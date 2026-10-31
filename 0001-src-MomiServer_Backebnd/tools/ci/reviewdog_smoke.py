"""Opt-in smoke tests using real reviewdog and a loopback-only fake GitLab API.

Run with REVIEWDOG_BINARY=/path/to/reviewdog python3 -B tools/ci/reviewdog_smoke.py.
No real GitLab credentials or real-project repository/API writes are used.
"""

import json
import os
from pathlib import Path
import subprocess
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, unquote, urlsplit


class FakeGitLab(BaseHTTPRequestHandler):
    def log_message(self, *_args):
        pass

    def respond(self, status, payload):
        body = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def dispatch(self):
        state = self.server.state
        if self.headers.get("PRIVATE-TOKEN") != "smoke-test-only-token":
            self.respond(401, {"message": "Test token required"})
            return
        path = unquote(urlsplit(self.path).path)
        prefix = "/api/v4/projects/example/project/merge_requests/1"
        if self.command == "GET" and path == prefix + "/discussions":
            self.respond(200, state["discussions"])
        elif self.command == "GET" and path == prefix:
            self.respond(200, {"iid": 1, "target_project_id": 1, "target_branch": "main"})
        elif self.command == "GET" and path == "/api/v4/projects/1/repository/branches/main":
            self.respond(200, {"name": "main", "commit": {"id": state["base"]}})
        elif self.command == "POST" and path == prefix + "/discussions":
            if state["reject_posts"]:
                self.respond(403, {"message": "Test rejection"})
                return
            raw = self.rfile.read(int(self.headers.get("Content-Length", "0")))
            payload = json.loads(raw)
            discussion = {"id": str(len(state["discussions"]) + 1), "notes": [{
                "id": len(state["discussions"]) + 1, "body": payload["body"],
                "position": payload["position"], "resolvable": True, "resolved": False,
            }]}
            state["discussions"].append(discussion)
            self.respond(201, discussion)
        elif self.command == "PUT" and path.startswith(prefix + "/discussions/"):
            raw = self.rfile.read(int(self.headers.get("Content-Length", "0")))
            payload = (json.loads(raw) if "application/json" in self.headers.get("Content-Type", "")
                       else {key: value[0] for key, value in parse_qs(raw.decode()).items()})
            if payload.get("resolved") not in (True, "true"):
                self.respond(400, {"message": "Expected resolved=true"})
                return
            discussion = next(item for item in state["discussions"] if item["id"] == path.rsplit("/", 1)[1])
            for note in discussion["notes"]:
                note["resolved"] = True
            self.respond(200, discussion)
        else:
            state["unexpected"].append((self.command, path))
            self.respond(404, {"message": "Unexpected test endpoint"})

    do_GET = dispatch
    do_POST = dispatch
    do_PUT = dispatch


class ReviewdogSmokeTest(unittest.TestCase):
    def setUp(self):
        binary = os.environ.get("REVIEWDOG_BINARY")
        if not binary:
            self.skipTest("Set REVIEWDOG_BINARY to the verified reviewdog 0.21.1 binary.")
        self.binary = str(Path(binary).resolve())
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.source = self.root / "src/main/java/Example.java"
        self.source.parent.mkdir(parents=True)
        self.report_path = self.root / "build/reports/spotbugs/main.sarif"
        self.report_path.parent.mkdir(parents=True)
        self.config = self.root / "spotbugs-reviewdog.yml"
        self.config.write_text("runner:\n  SpotBugs:\n    cmd: cat build/reports/spotbugs/main.sarif\n    format: sarif\n")
        # Synthetic commits exist only in this disposable fixture repository.
        self.git("init", "--quiet")
        self.git("config", "user.name", "Reviewdog Smoke Test")
        self.git("config", "user.email", "smoke@example.invalid")
        self.source.write_text("class Example {\n    int existing = 1;\n}\n")
        self.git("add", ".")
        self.commit("base")
        base = self.git("rev-parse", "HEAD").strip()
        self.source.write_text("class Example {\n    int existing = 1;\n    int added = 2;\n}\n")
        self.git("add", ".")
        self.commit("head")
        head = self.git("rev-parse", "HEAD").strip()
        self.state = {"base": base, "discussions": [], "reject_posts": False, "unexpected": []}
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), FakeGitLab)
        self.server.state = self.state
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.addCleanup(self.stop_server)
        self.env = dict(os.environ, CI="true", GITLAB_CI="true", CI_PROJECT_PATH="example/project",
                        CI_PROJECT_NAMESPACE="example", CI_PROJECT_NAME="project",
                        CI_MERGE_REQUEST_IID="1", CI_COMMIT_SHA=head,
                        CI_MERGE_REQUEST_DIFF_BASE_SHA=base,
                        CI_API_V4_URL=f"http://127.0.0.1:{self.server.server_port}/api/v4",
                        REVIEWDOG_GITLAB_API_TOKEN="smoke-test-only-token")
        self.env["GITLAB_API"] = self.env["CI_API_V4_URL"]

    def stop_server(self):
        self.server.shutdown()
        self.thread.join(timeout=5)
        self.server.server_close()

    def git(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.root, text=True, stderr=subprocess.STDOUT)

    def commit(self, message):
        self.git("-c", "core.hooksPath=/dev/null", "-c", "commit.gpgsign=false",
                 "commit", "--quiet", "-m", message)

    def report(self, line=3, message="Smoke-test diagnostic"):
        result = {"ruleId": "SMOKE_RULE", "level": "warning", "message": {"text": message},
                  "locations": [{"physicalLocation": {
                      "artifactLocation": {"uri": "src/main/java/Example.java"},
                      "region": {"startLine": line},
                  }}]}
        return {"version": "2.1.0", "runs": [{"tool": {"driver": {"name": "SpotBugs"}},
                                                 "results": [result] if line is not None else []}]}

    def review(self, report, reporter="gitlab-mr-discussion"):
        self.report_path.write_text(json.dumps(report))
        args = [self.binary, f"-reporter={reporter}", "-filter-mode=added", "-fail-on-error"]
        if reporter == "local":
            args += ["-f=sarif", "-name=SpotBugs",
                     f"-diff=git diff --no-ext-diff --unified=0 {self.state['base']} {self.env['CI_COMMIT_SHA']} --"]
        else:
            args += [f"-conf={self.config}", "-runners=SpotBugs"]
        result = subprocess.run(args,
                                input=json.dumps(report), text=True, capture_output=True,
                                cwd=self.root, env=self.env, timeout=30)
        self.assertEqual([], self.state["unexpected"], result.stdout + result.stderr)
        return result

    def test_posts_deduplicates_and_resolves_after_fix(self):
        for _ in range(2):
            result = self.review(self.report())
            self.assertEqual(1, result.returncode, result.stdout + result.stderr)
            self.assertEqual(1, len(self.state["discussions"]))
            note = self.state["discussions"][0]["notes"][0]
            self.assertFalse(note["resolved"])
            self.assertEqual(3, note["position"]["new_line"])
        result = self.review(self.report(line=None))
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertTrue(self.state["discussions"][0]["notes"][0]["resolved"])

    def test_filters_unchanged_lines(self):
        result = self.review(self.report(line=2))
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual([], self.state["discussions"])

    def test_failed_post_does_not_resolve_previous_discussion(self):
        self.assertEqual(1, self.review(self.report()).returncode)
        self.state["reject_posts"] = True
        result = self.review(self.report(message="Another diagnostic"))
        self.assertNotEqual(0, result.returncode)
        self.assertFalse(self.state["discussions"][0]["notes"][0]["resolved"])

    def test_empty_report_succeeds(self):
        result = self.review(self.report(line=None))
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_local_reporter_reads_sarif_without_api_calls(self):
        result = self.review(self.report(), reporter="local")
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn("Smoke-test diagnostic", result.stdout)
        self.assertEqual([], self.state["discussions"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
