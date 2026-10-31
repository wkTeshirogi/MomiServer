"""Publish a bounded, secret-free summary to one managed GitLab Issue."""

import argparse
from collections import Counter
from dataclasses import dataclass
import html
import json
import os
from pathlib import Path
import re
import ssl
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode, urlsplit
from urllib.request import HTTPRedirectHandler, HTTPSHandler, Request, build_opener

import dependency_check_issue_constants as constants


class ReportError(Exception):
    """A sanitized reporting failure; never include tokens or HTTP bodies."""


def positive_id(value):
    text = str(value)
    if not re.fullmatch(r"[1-9][0-9]*", text):
        raise ReportError("Invalid GitLab numeric identifier.")
    return text


@dataclass(frozen=True)
class Context:
    api: str
    project_id: str
    pipeline_id: str
    project_url: str

    @classmethod
    def from_environment(cls, environment):
        server = environment.get("CI_SERVER_URL", "").rstrip("/")
        parsed = urlsplit(server)
        if (parsed.scheme != "https" or not parsed.hostname or parsed.username
                or parsed.password or parsed.query or parsed.fragment
                or any(part in (".", "..") for part in parsed.path.split("/"))):
            raise ReportError("CI_SERVER_URL must be an HTTPS GitLab URL without credentials.")
        api = environment.get("CI_API_V4_URL", "").rstrip("/")
        if api != server + "/api/v4":
            raise ReportError("CI_API_V4_URL must belong to the current GitLab server.")
        path = environment.get("CI_PROJECT_PATH", "")
        if len(path.split("/")) < 2 or any(
                not part or part in (".", "..") for part in path.split("/")):
            raise ReportError("Invalid GitLab project path.")
        return cls(api, positive_id(environment.get("CI_PROJECT_ID", "")),
                   positive_id(environment.get("CI_PIPELINE_ID", "")),
                   server + "/" + quote(path, safe="/"))

    def project_api(self, suffix):
        return f"{self.api}/projects/{self.project_id}/{suffix}"


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, new_url):
        return None


class GitLabApi:
    def __init__(self, context, token, ca_file=None):
        if not token or any(ord(character) < 33 or ord(character) > 126 for character in token):
            raise ReportError("Set GITLAB_ISSUE_API_TOKEN in the dependency-check-issue environment.")
        trust = ssl.create_default_context()
        if ca_file:
            trust.load_verify_locations(cafile=ca_file)
        self.context = context
        self.token = token
        self.opener = build_opener(NoRedirect(), HTTPSHandler(context=trust))

    def request(self, method, suffix, payload=None, query=None):
        url = self.context.project_api(suffix)
        if query:
            url += "?" + urlencode(query)
        request = Request(url, method=method,
                          data=None if payload is None else json.dumps(payload).encode("utf-8"),
                          headers={"PRIVATE-TOKEN": self.token, "Accept": "application/json",
                                   "Content-Type": "application/json"})
        try:
            with self.opener.open(request, timeout=constants.HTTP_TIMEOUT_SECONDS) as response:
                raw = response.read(constants.MAX_API_BYTES + 1)
                next_page = response.headers.get("X-Next-Page", "")
        except HTTPError as error:
            code = error.code
            error.close()
            raise ReportError(f"GitLab API returned HTTP {code}; no response body was logged.") from None
        except (URLError, OSError, ValueError):
            raise ReportError("GitLab API connection failed; check TLS, connectivity and token permissions.") from None
        if len(raw) > constants.MAX_API_BYTES:
            raise ReportError("GitLab API response exceeded the size limit.")
        try:
            return json.loads(raw), next_page
        except (ValueError, UnicodeError):
            raise ReportError("GitLab API returned invalid JSON.") from None

    def items(self, suffix, query=None):
        page = "1"
        for _ in range(constants.MAX_PAGES):
            result, next_page = self.request("GET", suffix,
                                             query={**(query or {}), "per_page": constants.PAGE_SIZE, "page": page})
            if not isinstance(result, list) or any(not isinstance(item, dict) for item in result):
                raise ReportError("GitLab API returned an invalid list.")
            yield from result
            # Some installations omit X-Next-Page: a full page still requires a probe.
            if not next_page and len(result) < constants.PAGE_SIZE:
                return
            next_page = next_page or str(int(page) + 1)
            if not next_page.isascii() or not next_page.isdecimal() or int(next_page) <= int(page):
                raise ReportError("GitLab API returned invalid pagination.")
            page = next_page
        raise ReportError("GitLab pagination limit reached; refusing to create duplicate records.")


def find_analysis_job(api, context):
    jobs = [job for job in api.items(f"pipelines/{context.pipeline_id}/jobs",
                                    {"include_retried": "false"})
            if job.get("name") == "dependency_check"]
    if not jobs:
        raise ReportError("The pipeline has no Dependency-Check job to report.")
    return max(jobs, key=lambda job: int(positive_id(job.get("id", ""))))


def cell(value):
    text = " ".join(str(value).split())[:constants.MAX_CELL_CHARACTERS]
    return "<code>" + html.escape(text).replace("|", "&#124;") + "</code>"


def small_file(path):
    try:
        if path.stat().st_size > 128:
            return ""
        return path.read_text(encoding="utf-8").strip()
    except (OSError, UnicodeError):
        return ""


def read_inventory(path):
    try:
        if path.stat().st_size > constants.MAX_REPORT_BYTES:
            raise ValueError("report too large")
        report = json.loads(path.read_bytes())
        dependencies = report["dependencies"]
        if (not isinstance(report["scanInfo"], dict) or not report["scanInfo"].get("engineVersion")
                or not isinstance(dependencies, list) or not dependencies):
            raise ValueError("missing inventory")
        errors = bool(report["scanInfo"].get("analysisExceptions"))
        rows = set()
        severities = {}
        for dependency in dependencies:
            filename = dependency["fileName"]
            if not isinstance(filename, str) or not filename:
                raise ValueError("invalid dependency")
            errors |= bool(dependency.get("analysisExceptions"))
            vulnerabilities = dependency.get("vulnerabilities", [])
            if not isinstance(vulnerabilities, list):
                raise ValueError("invalid vulnerabilities")
            for vulnerability in vulnerabilities:
                name = vulnerability["name"]
                if not isinstance(name, str) or not name or not isinstance(filename, str):
                    raise ValueError("invalid finding")
                severity = str(vulnerability.get("severity", "UNKNOWN")).upper()
                if severity not in constants.SEVERITIES:
                    severity = "UNKNOWN"
                identity = (str(vulnerability.get("source", "NVD")), name)
                previous = severities.get(identity, "UNKNOWN")
                severities[identity] = min((severity, previous), key=constants.SEVERITIES.index)
                rows.add((filename, name, severity))
        return len(dependencies), Counter(severities.values()), sorted(rows), errors
    except (OSError, ValueError, KeyError, TypeError, AttributeError, UnicodeError):
        return None


def last_progress(path):
    try:
        with path.open("rb") as stream:
            stream.seek(max(0, path.stat().st_size - constants.MAX_LOG_TAIL_BYTES))
            tail = stream.read().decode("utf-8", errors="replace")
    except OSError:
        return "取得進捗は記録されていません。"
    matches = re.findall(constants.PROGRESS_PATTERN, tail)
    if matches:
        downloaded, total, percentage = matches[-1]
        downloaded = int(downloaded.replace(",", ""))
        total = int(total.replace(",", ""))
        if 0 <= downloaded <= total and total > 0 and 0 <= int(percentage) <= 100:
            return f"最終取得進捗: {downloaded:,}/{total:,} ({int(percentage)}%)。"
    return "取得進捗は記録されていません。"


def render_summary(context, job, reports_root=constants.REPORTS_ROOT):
    job_id = positive_id(job.get("id", ""))
    directory = reports_root / "dependency-check-ci"
    matched = (small_file(directory / "analysis-pipeline-id.txt") == context.pipeline_id
               and small_file(directory / "analysis-job-id.txt") == job_id)
    exit_code = small_file(directory / "analysis-exit-code.txt") if matched else ""
    inventory = read_inventory(reports_root / "dependency-check/dependency-check-report.json") if matched else None
    complete = job.get("status") == "success" and exit_code == "0" and inventory is not None and not inventory[3]
    if exit_code == "124" or job.get("failure_reason") in constants.TIMEOUT_REASONS:
        status = "タイムアウト（検査未完了）"
    elif complete:
        status = "解析成功・指摘あり" if inventory[1] else "解析成功・指摘なし"
    else:
        status = "検査未完了・正常完了を確認できません"
    marker = f"{constants.RUN_MARKER_PREFIX}{context.project_id}:{context.pipeline_id} -->"
    job_url = f"{context.project_url}/-/jobs/{job_id}"
    lines = [marker, f"## Dependency-Check 実行結果: {status}", "",
             f"- 実行日時: {cell(job.get('started_at') or '不明')}（UTC）",
             f"- GitLabジョブ状態: {cell(job.get('status', 'unknown'))}",
             f"- 終了コード: {cell(exit_code or '未記録')}",
             f"- [パイプライン #{context.pipeline_id}]({context.project_url}/-/pipelines/{context.pipeline_id})",
             f"- [解析ジョブ #{job_id}]({job_url})",
             f"- [レポート]({job_url}/artifacts/browse/build/reports/dependency-check/)",
             f"- [診断・実行ログ]({job_url}/artifacts/browse/build/reports/dependency-check-ci/)", ""]
    if job.get("failure_reason"):
        lines.append(f"GitLabの失敗理由: {cell(job['failure_reason'])}")
    if not complete:
        lines.append("**脆弱性なしとは判定しません。** 厳格モードの脆弱性判定による失敗も含め、ジョブログを確認してください。")
    if not matched:
        lines.append("この解析ジョブの成果物が取得できないか、実行識別子が一致しません。過去のレポートは使用しません。")
    else:
        lines.append(last_progress(directory / "analysis.log"))
    if inventory is None:
        lines.append("有効な解析レポートがないため、対象数・脆弱性件数は不明です。")
    else:
        count, severities, rows, errors = inventory
        lines.extend(["", "### 検出概要" + ("（参考値・正常完了未確認）" if not complete else ""),
                      f"解析対象: {count}依存。未抑制の脆弱性: {sum(severities.values())}件（情報源・識別子で重複排除）。",
                      "", "| 深刻度 | 件数 |", "| --- | ---: |"])
        lines.extend(f"| {severity} | {severities[severity]} |" for severity in constants.SEVERITIES)
        if errors:
            lines.append("レポートに解析例外が含まれます。件数は参考値です。")
        if rows:
            lines.extend(["", "| ライブラリ | 脆弱性 | 深刻度 |", "| --- | --- | --- |"])
            lines.extend("| " + " | ".join(cell(value) for value in row) + " |"
                         for row in rows[:constants.MAX_DETAIL_ROWS])
            if len(rows) > constants.MAX_DETAIL_ROWS:
                lines.append(f"先頭{constants.MAX_DETAIL_ROWS}行のみ表示します。全件はレポートを参照してください。")
    lines.extend(["", "アーティファクト保存期限は1週間です。ログ本文・原本レポート・認証情報はこのコメントに転載しません。"])
    return "\n".join(lines) + "\n"


def publish(api, body):
    issues = [issue for issue in api.items("issues", {"scope": "all", "state": "all",
                                                    "search": constants.ISSUE_TITLE, "in": "title"})
              if issue.get("title") == constants.ISSUE_TITLE
              and constants.ISSUE_MARKER in (issue.get("description") or "")]
    if len(issues) > 1:
        raise ReportError("Multiple managed Issues found; resolve the duplicate before reporting.")
    if issues:
        issue = issues[0]
    else:
        issue, _ = api.request("POST", "issues", {"title": constants.ISSUE_TITLE,
                                                "description": constants.ISSUE_DESCRIPTION,
                                                "confidential": True})
    if not isinstance(issue, dict):
        raise ReportError("GitLab API returned an invalid Issue.")
    iid = positive_id(issue.get("iid", ""))
    if issue.get("confidential") is not True:
        raise ReportError("The managed Issue must be confidential before posting security results.")
    marker = body.splitlines()[0]
    notes = [note for note in api.items(f"issues/{iid}/notes")
             if not note.get("system") and (note.get("body") or "").startswith(marker + "\n")]
    if len(notes) > 1:
        raise ReportError("Multiple comments found for this pipeline; refusing an ambiguous update.")
    if notes:
        if notes[0]["body"] != body:
            api.request("PUT", f"issues/{iid}/notes/{positive_id(notes[0].get('id', ''))}", {"body": body})
    else:
        api.request("POST", f"issues/{iid}/notes", {"body": body})
    return iid


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true", help="Render locally without GitLab API requests.")
    arguments = parser.parse_args(argv)
    try:
        context = Context.from_environment(os.environ)
        if arguments.dry_run:
            job_id = small_file(constants.REPORTS_ROOT / "dependency-check-ci/analysis-job-id.txt")
            job = {"id": job_id or "1", "status": "unknown"}
        else:
            if (os.environ.get("CI_PIPELINE_SOURCE") != "schedule"
                    or os.environ.get("CI_COMMIT_BRANCH") != "main"
                    or os.environ.get("CI_COMMIT_REF_PROTECTED") != "true"
                    or os.environ.get("RUN_DEPENDENCY_CHECK") != "true"):
                raise ReportError("Issue reporting is restricted to protected main Dependency-Check schedules.")
            api = GitLabApi(context, os.environ.get("GITLAB_ISSUE_API_TOKEN"),
                            os.environ.get("CI_SERVER_TLS_CA_FILE"))
            job = find_analysis_job(api, context)
        body = render_summary(context, job)
        token = os.environ.get("GITLAB_ISSUE_API_TOKEN")
        if token:
            body = body.replace(token, "[REDACTED]")
        if arguments.dry_run:
            print(body)
        else:
            iid = publish(api, body)
            print(f"Dependency-Check results recorded in Issue #{iid}.")
        return 0
    except ReportError as error:
        print(f"Dependency-Check Issue reporting failed: {error}", file=sys.stderr)
        return 1
    except (OSError, ValueError, TypeError, KeyError, AttributeError):
        # Do not print untrusted server messages, file content, URLs, or credentials.
        print("Dependency-Check Issue reporting failed. Check token scope/environment, CA trust, "
              "API connectivity, and managed Issue confidentiality/duplicates.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
