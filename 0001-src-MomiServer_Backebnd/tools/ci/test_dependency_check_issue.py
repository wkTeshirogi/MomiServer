"""Offline tests; use fake GitLab responses and synthetic reports only."""

import copy
from contextlib import redirect_stderr, redirect_stdout
from io import BytesIO, StringIO
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import MagicMock, Mock, patch
from urllib.error import HTTPError, URLError

import dependency_check_issue as reporting
import dependency_check_issue_constants as constants


def environment():
    return {"CI_SERVER_URL": "https://gitlab.example.invalid",
            "CI_API_V4_URL": "https://gitlab.example.invalid/api/v4",
            "CI_PROJECT_PATH": "group/backend", "CI_PROJECT_ID": "13", "CI_PIPELINE_ID": "100",
            "CI_PIPELINE_SOURCE": "schedule", "CI_COMMIT_BRANCH": "main",
            "CI_COMMIT_REF_PROTECTED": "true", "RUN_DEPENDENCY_CHECK": "true",
            "GITLAB_ISSUE_API_TOKEN": "synthetic-token-not-a-real-credential"}


class SummaryTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.context = reporting.Context.from_environment(environment())
        self.job = {"id": 200, "name": "dependency_check", "status": "success",
                    "started_at": "2026-10-23T00:00:00Z"}
        self.report = {"scanInfo": {"engineVersion": "13.0.0"},
                       "dependencies": [{"fileName": "sample.jar"}]}
        self.diagnostic = self.root / "dependency-check-ci"
        self.diagnostic.mkdir()
        (self.diagnostic / "analysis-job-id.txt").write_text("200\n", encoding="utf-8")
        (self.diagnostic / "analysis-pipeline-id.txt").write_text("100\n", encoding="utf-8")
        (self.diagnostic / "analysis-exit-code.txt").write_text("0\n", encoding="utf-8")
        self.path = self.root / "dependency-check/dependency-check-report.json"
        self.path.parent.mkdir()

    def summary(self):
        self.path.write_text(json.dumps(self.report), encoding="utf-8")
        return reporting.render_summary(self.context, self.job, self.root)

    def test_success_with_no_findings(self):
        body = self.summary()
        self.assertIn("解析成功・指摘なし", body)
        self.assertIn("解析対象: 1依存", body)
        self.assertIn("/-/jobs/200/artifacts/browse/", body)
        self.assertIn("実行日時", body)

    def test_unique_severity_counts_and_dependency_rows(self):
        self.report["dependencies"][0]["vulnerabilities"] = [
            {"name": "CVE-2099-0001", "severity": "HIGH"}]
        duplicate = copy.deepcopy(self.report["dependencies"][0])
        duplicate["fileName"] = "another.jar"
        duplicate["vulnerabilities"][0]["severity"] = "CRITICAL"
        self.report["dependencies"].append(duplicate)
        body = self.summary()
        self.assertIn("解析成功・指摘あり", body)
        self.assertIn("未抑制の脆弱性: 1件", body)
        self.assertIn("| CRITICAL | 1 |", body)
        self.assertIn("another.jar", body)

    def test_missing_or_corrupt_report_is_unknown_not_zero(self):
        for content in (None, "broken", "{}", '{"scanInfo":{},"dependencies":[]}'):
            with self.subTest(content=content):
                if content is not None:
                    self.path.write_text(content, encoding="utf-8")
                body = reporting.render_summary(self.context, self.job, self.root)
                self.assertIn("件数は不明", body)
                self.assertNotIn("解析成功", body)
                self.assertNotIn("未抑制の脆弱性: 0件", body)

    def test_missing_exit_code_is_not_success(self):
        (self.diagnostic / "analysis-exit-code.txt").unlink()
        self.assertIn("正常完了を確認できません", self.summary())

    def test_timeout_has_progress_but_no_raw_log_or_secrets(self):
        self.job["status"] = "failed"
        (self.diagnostic / "analysis-exit-code.txt").write_text("124", encoding="utf-8")
        (self.diagnostic / "analysis.log").write_text(
            "secret API key and response body\nDownloaded 40,000/413,131 (10%)\n", encoding="utf-8")
        body = self.summary()
        self.assertIn("タイムアウト（検査未完了）", body)
        self.assertIn("40,000/413,131 (10%)", body)
        self.assertNotIn("secret API", body)
        self.assertNotIn("解析成功", body)

    def test_runner_timeout_without_artifacts(self):
        self.job.update(status="failed", failure_reason="job_execution_timeout")
        body = reporting.render_summary(self.context, self.job, Path("nonexistent-fixture-directory"))
        self.assertIn("タイムアウト", body)
        self.assertIn("件数は不明", body)

    def test_invalid_or_unbounded_progress_is_not_copied(self):
        for text in ("Downloaded 20/10 (200%)", "Downloaded " + "9" * 1000 + "/1000 (10%)"):
            with self.subTest(size=len(text)):
                (self.diagnostic / "analysis.log").write_text(text, encoding="utf-8")
                self.assertEqual("取得進捗は記録されていません。", reporting.last_progress(self.diagnostic / "analysis.log"))

    def test_failed_job_with_report_is_reference_only(self):
        self.job["status"] = "failed"
        (self.diagnostic / "analysis-exit-code.txt").write_text("1", encoding="utf-8")
        body = self.summary()
        self.assertIn("参考値・正常完了未確認", body)
        self.assertIn("厳格モード", body)
        self.assertNotIn("解析成功", body)

    def test_stale_pipeline_or_job_artifacts_are_rejected(self):
        self.summary()
        for name in ("analysis-pipeline-id.txt", "analysis-job-id.txt"):
            with self.subTest(name=name):
                path = self.diagnostic / name
                original = path.read_text()
                path.write_text("999", encoding="utf-8")
                body = reporting.render_summary(self.context, self.job, self.root)
                self.assertIn("過去のレポートは使用しません", body)
                self.assertNotIn("解析対象:", body)
                path.write_text(original, encoding="utf-8")

    def test_exceptions_in_report_prevent_success(self):
        for target in (self.report["scanInfo"], self.report["dependencies"][0]):
            target["analysisExceptions"] = [{"message": "synthetic private detail"}]
            self.assertNotIn("解析成功", self.summary())

    def test_malformed_dependency_or_vulnerability_is_unknown(self):
        for dependency in (None, {}, {"vulnerabilities": None}, {"vulnerabilities": [None]},
                           {"fileName": "sample", "vulnerabilities": [{"name": None}]}):
            with self.subTest(dependency=dependency):
                self.report["dependencies"] = [dependency]
                body = self.summary()
                self.assertNotIn("解析成功", body)

    def test_markdown_and_mentions_are_in_code_and_html_is_escaped(self):
        self.report["dependencies"] = [{"fileName": "@all | <img src=x> `\n/close",
                                        "vulnerabilities": [{"name": "[bad](https://evil.invalid)",
                                                             "severity": "HIGH"}]}]
        body = self.summary()
        self.assertNotIn("<img", body)
        self.assertNotIn("\n/close", body)
        self.assertIn("<code>@all &#124; &lt;img", body)
        self.assertIn("<code>[bad]", body)

    def test_detail_rows_are_bounded_but_counts_are_complete(self):
        self.report["dependencies"][0]["vulnerabilities"] = [
            {"name": f"CVE-2099-{index:04}", "severity": "HIGH"}
            for index in range(constants.MAX_DETAIL_ROWS + 1)]
        body = self.summary()
        self.assertIn("未抑制の脆弱性: 51件", body)
        self.assertIn("先頭50行", body)


class PublishTest(unittest.TestCase):
    def setUp(self):
        self.api = Mock()
        self.issue = {"iid": 7, "title": constants.ISSUE_TITLE,
                      "description": constants.ISSUE_DESCRIPTION, "confidential": True, "state": "closed"}
        self.body = "<!-- momi:dependency-check-run:13:100 -->\nsummary\n"

    def test_first_run_creates_confidential_issue_and_comment(self):
        self.api.items.side_effect = [[], []]
        self.api.request.side_effect = [(self.issue, ""), ({"id": 8}, "")]
        self.assertEqual("7", reporting.publish(self.api, self.body))
        self.assertEqual("POST", self.api.request.call_args_list[0].args[0])
        self.assertTrue(self.api.request.call_args_list[0].args[2]["confidential"])
        self.assertEqual("issues/7/notes", self.api.request.call_args_list[1].args[1])

    def test_existing_closed_issue_gets_new_comment_without_reopening(self):
        self.api.items.side_effect = [[self.issue], []]
        reporting.publish(self.api, self.body)
        self.api.request.assert_called_once_with("POST", "issues/7/notes", {"body": self.body})

    def test_retry_does_not_duplicate_comment(self):
        self.api.items.side_effect = [[self.issue], [{"id": 8, "body": self.body}]]
        reporting.publish(self.api, self.body)
        self.api.request.assert_not_called()

    def test_retry_updates_existing_comment(self):
        self.api.items.side_effect = [[self.issue], [{"id": 8, "body": self.body + "old"}]]
        reporting.publish(self.api, self.body)
        self.api.request.assert_called_once_with("PUT", "issues/7/notes/8", {"body": self.body})

    def test_unrelated_issue_is_never_modified(self):
        unrelated = {**self.issue, "description": "User-authored issue with the same title"}
        self.api.items.side_effect = [[unrelated], []]
        self.api.request.side_effect = [(self.issue, ""), ({"id": 8}, "")]
        reporting.publish(self.api, self.body)
        self.assertEqual("issues", self.api.request.call_args_list[0].args[1])

    def test_public_issue_or_duplicate_issues_fail_without_posting(self):
        for issues in ([{**self.issue, "confidential": False}], [self.issue, self.issue]):
            with self.subTest(issues=issues):
                self.api.reset_mock()
                self.api.items.side_effect = [issues]
                with self.assertRaises(reporting.ReportError):
                    reporting.publish(self.api, self.body)
                self.api.request.assert_not_called()

    def test_duplicate_pipeline_comments_fail_without_posting(self):
        self.api.items.side_effect = [[self.issue], [{"id": 8, "body": self.body}] * 2]
        with self.assertRaises(reporting.ReportError):
            reporting.publish(self.api, self.body)
        self.api.request.assert_not_called()

    def test_lookup_error_is_not_treated_as_missing_issue(self):
        self.api.items.side_effect = reporting.ReportError("GitLab API returned HTTP 403.")
        with self.assertRaises(reporting.ReportError):
            reporting.publish(self.api, self.body)
        self.api.request.assert_not_called()


class ApiTest(unittest.TestCase):
    def setUp(self):
        self.context = reporting.Context.from_environment(environment())

    def client(self):
        with patch.object(reporting, "build_opener") as factory:
            client = reporting.GitLabApi(self.context, "synthetic-only")
        client.opener = MagicMock()
        return client

    def test_https_current_server_only(self):
        for key, value in (("CI_SERVER_URL", "http://gitlab.example.invalid"),
                           ("CI_SERVER_URL", "https://name:secret@gitlab.example.invalid"),
                           ("CI_API_V4_URL", "https://evil.invalid/api/v4"),
                           ("CI_PROJECT_ID", "../2"), ("CI_PROJECT_PATH", "group/../other")):
            with self.subTest(key=key):
                with self.assertRaises(reporting.ReportError):
                    reporting.Context.from_environment({**environment(), key: value})

    def test_subpath_gitlab_and_encoded_project_path(self):
        context = reporting.Context.from_environment({**environment(),
            "CI_SERVER_URL": "https://gitlab.example.invalid/gitlab",
            "CI_API_V4_URL": "https://gitlab.example.invalid/gitlab/api/v4"})
        self.assertEqual("https://gitlab.example.invalid/gitlab/group/backend", context.project_url)

    def test_token_only_in_header_and_tls_is_verified(self):
        client = self.client()
        response = Mock()
        response.read.return_value = b'{}'
        response.headers = {}
        client.opener.open.return_value.__enter__.return_value = response
        client.request("POST", "issues", {"title": "example"})
        request = client.opener.open.call_args.args[0]
        self.assertEqual("synthetic-only", request.get_header("Private-token"))
        self.assertNotIn("synthetic-only", request.full_url)
        self.assertNotIn(b"synthetic-only", request.data)
        self.assertEqual(30, client.opener.open.call_args.kwargs["timeout"])
        with patch.object(reporting.ssl, "create_default_context") as trust:
            reporting.GitLabApi(self.context, "synthetic-only", "ca.pem")
            trust.return_value.load_verify_locations.assert_called_once_with(cafile="ca.pem")

    def test_redirect_is_not_followed(self):
        self.assertIsNone(reporting.NoRedirect().redirect_request(None, None, 302, "", {}, "https://evil.invalid"))

    def test_missing_or_invalid_token_is_rejected(self):
        for token in (None, "", "secret\nvalue", "secret value"):
            with self.subTest(token=bool(token)), self.assertRaises(reporting.ReportError):
                reporting.GitLabApi(self.context, token)

    def test_invalid_json_or_oversized_response_is_rejected(self):
        for raw in (b"not json", b"x" * 1025):
            with self.subTest(size=len(raw)), patch.object(constants, "MAX_API_BYTES", 1024):
                client = self.client()
                response = client.opener.open.return_value.__enter__.return_value
                response.read.return_value = raw
                response.headers = {}
                with self.assertRaises(reporting.ReportError):
                    client.request("GET", "issues")

    def test_http_and_connection_errors_are_sanitized_without_post_retry(self):
        for failure in (HTTPError("https://synthetic-only.invalid", 403, "synthetic-only", {},
                                  BytesIO(b'synthetic-only response body')),
                        URLError("synthetic-only")):
            with self.subTest(failure=type(failure)):
                client = self.client()
                client.opener.open.side_effect = failure
                with self.assertRaises(reporting.ReportError) as raised:
                    client.request("POST", "issues", {})
                self.assertNotIn("synthetic-only", str(raised.exception))
                self.assertEqual(1, client.opener.open.call_count)

    def test_pagination_and_missing_pagination_header(self):
        client = self.client()
        client.request = Mock(side_effect=[([{"id": index} for index in range(100)], ""),
                                           ([{"id": 101}], "")])
        self.assertEqual(101, len(list(client.items("issues"))))
        self.assertEqual("2", client.request.call_args.kwargs["query"]["page"])

    def test_invalid_pagination_or_response_is_rejected(self):
        for response in (([], "1"), ({}, ""), ([], "https://evil.invalid")):
            with self.subTest(response=response):
                client = self.client()
                client.request = Mock(return_value=response)
                with self.assertRaises(reporting.ReportError):
                    list(client.items("issues"))

    def test_page_limit_fails_instead_of_assuming_no_issue_exists(self):
        client = self.client()
        client.request = Mock(return_value=([{}] * 100, "2"))
        with patch.object(constants, "MAX_PAGES", 1), self.assertRaises(reporting.ReportError):
            list(client.items("issues"))

    def test_latest_analysis_job_is_selected(self):
        api = Mock()
        api.items.return_value = [{"id": 201, "name": "dependency_check_issue"},
                                 {"id": 200, "name": "dependency_check"},
                                 {"id": 150, "name": "dependency_check"}]
        self.assertEqual(200, reporting.find_analysis_job(api, self.context)["id"])

    def test_missing_analysis_job_is_an_error(self):
        api = Mock()
        api.items.return_value = []
        with self.assertRaises(reporting.ReportError):
            reporting.find_analysis_job(api, self.context)

    def test_main_redacts_token_from_summary_and_does_not_log_content(self):
        output = StringIO()
        with patch.dict(reporting.os.environ, environment(), clear=True), \
                patch.object(reporting, "GitLabApi") as api, \
                patch.object(reporting, "find_analysis_job", return_value={"id": 200}), \
                patch.object(reporting, "render_summary", return_value="synthetic-token-not-a-real-credential"), \
                patch.object(reporting, "publish", return_value="7") as publish, redirect_stdout(output):
            self.assertEqual(0, reporting.main([]))
            publish.assert_called_once_with(api.return_value, "[REDACTED]")
            self.assertNotIn("synthetic-token", output.getvalue())

    def test_main_api_failure_has_status_without_server_content(self):
        output = StringIO()
        with patch.dict(reporting.os.environ, environment(), clear=True), \
                patch.object(reporting, "GitLabApi") as api, redirect_stderr(output):
            api.return_value.items.side_effect = reporting.ReportError("GitLab API returned HTTP 403.")
            self.assertEqual(1, reporting.main([]))
            self.assertIn("HTTP 403", output.getvalue())
            self.assertNotIn("synthetic-token", output.getvalue())

    def test_wrong_pipeline_context_never_contacts_api(self):
        for key, value in (("CI_PIPELINE_SOURCE", "merge_request_event"),
                           ("CI_COMMIT_BRANCH", "feature"), ("CI_COMMIT_REF_PROTECTED", "false")):
            with self.subTest(key=key), patch.dict(reporting.os.environ, {**environment(), key: value}, clear=True), \
                    patch.object(reporting, "GitLabApi") as api, redirect_stderr(StringIO()):
                self.assertEqual(1, reporting.main([]))
                api.assert_not_called()

    def test_dry_run_does_not_contact_api_or_require_token(self):
        values = environment()
        values.pop("GITLAB_ISSUE_API_TOKEN")
        with patch.dict(reporting.os.environ, values, clear=True), \
                patch.object(reporting, "GitLabApi") as api, redirect_stdout(StringIO()):
            self.assertEqual(0, reporting.main(["--dry-run"]))
            api.assert_not_called()


if __name__ == "__main__":
    unittest.main()
