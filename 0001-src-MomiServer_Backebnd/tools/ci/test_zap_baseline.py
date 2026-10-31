"""Offline safety and failure-mode tests; never contact a scan target."""

import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from zap_baseline import run_scan, validate_target


class ZapBaselineTest(unittest.TestCase):
    def setUp(self):
        self.environment = {
            "ZAP_TEST_ENVIRONMENT": "true",
            "ZAP_TARGET_URL": "https://app.test/login",
            "ZAP_ALLOWED_ORIGIN": "https://app.test",
        }
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.output = Path(directory.name) / "reports"
        self.output.mkdir()

    def test_explicit_test_environment_is_required(self):
        for value in ("", "false", "TRUE"):
            with self.subTest(value=value):
                self.environment["ZAP_TEST_ENVIRONMENT"] = value
                with self.assertRaises(ValueError):
                    validate_target(self.environment)

    def test_approved_origin_with_path_is_accepted(self):
        self.assertEqual("https://app.test/login", validate_target(self.environment))

    def test_default_port_and_hostname_case_are_normalized(self):
        self.environment["ZAP_ALLOWED_ORIGIN"] = "https://APP.TEST:443/"
        self.assertEqual("https://app.test/login", validate_target(self.environment))

    def test_invalid_or_unapproved_targets_are_rejected(self):
        for target in ("", "https://production.test", "http://app.test", "https://app.test:8443",
                       "https://user:secret@app.test", "https://app.test?token=secret",
                       "https://app.test/#token", "file:///etc/passwd", "https://app.test:invalid",
                       "https://app.test\\@other.test", "https://app.test/\n"):
            with self.subTest(target=target):
                self.environment["ZAP_TARGET_URL"] = target
                with self.assertRaises(ValueError):
                    validate_target(self.environment)

    def test_allowed_origin_cannot_contain_a_path(self):
        self.environment["ZAP_ALLOWED_ORIGIN"] = "https://app.test/login"
        with self.assertRaises(ValueError):
            validate_target(self.environment)

    def write_reports(self, code, sites=None):
        (self.output / "baseline.html").write_text("<html>Report</html>", encoding="utf-8")
        (self.output / "baseline.xml").write_text("<report/>", encoding="utf-8")
        data = {"site": sites if sites is not None else [{"@host": "app.test", "@port": "443"}]}
        (self.output / "baseline.json").write_text(json.dumps(data), encoding="utf-8")
        return subprocess.CompletedProcess([], code)

    @patch("zap_baseline.Path.mkdir")
    @patch("zap_baseline.subprocess.run")
    def test_clean_and_warning_scans_keep_reports(self, run, _mkdir):
        for code in (0, 2):
            with self.subTest(code=code):
                run.side_effect = lambda *args, **kwargs: self.write_reports(code)
                run_scan("https://app.test/login", self.output)
                self.assertEqual(f"{code}\n", (self.output / "exit-code.txt").read_text())
                command = run.call_args.args[0]
                self.assertIn("--autooff", command)
                self.assertNotIn("-I", command)
                self.assertEqual(600, run.call_args.kwargs["timeout"])
                self.assertEqual(self.output, run.call_args.kwargs["cwd"])

    @patch("zap_baseline.Path.mkdir")
    @patch("zap_baseline.subprocess.run")
    def test_tool_errors_and_configured_failures_are_not_ignored(self, run, _mkdir):
        for code in (1, 3, 4, -9):
            with self.subTest(code=code):
                run.return_value = subprocess.CompletedProcess([], code)
                with self.assertRaises(ValueError):
                    run_scan("https://app.test", self.output)

    @patch("zap_baseline.Path.mkdir")
    @patch("zap_baseline.subprocess.run")
    def test_missing_reports_cannot_reuse_stale_results(self, run, _mkdir):
        self.write_reports(0)
        run.return_value = subprocess.CompletedProcess([], 0)
        with self.assertRaises(ValueError):
            run_scan("https://app.test", self.output)
        self.assertFalse((self.output / "baseline.json").exists())

    @patch("zap_baseline.Path.mkdir")
    @patch("zap_baseline.subprocess.run")
    def test_report_must_contain_the_target(self, run, _mkdir):
        run.side_effect = lambda *args, **kwargs: self.write_reports(0, [])
        with self.assertRaises(ValueError):
            run_scan("https://app.test", self.output)

    @patch("zap_baseline.Path.mkdir")
    @patch("zap_baseline.subprocess.run")
    def test_timeout_does_not_look_like_success(self, run, _mkdir):
        run.side_effect = subprocess.TimeoutExpired("zap-baseline.py", 600)
        with self.assertRaises(subprocess.TimeoutExpired):
            run_scan("https://app.test", self.output)

    @patch("zap_baseline.subprocess.run")
    def test_validation_precedes_process_start(self, run):
        self.environment["ZAP_TARGET_URL"] = "https://other.test"
        with self.assertRaises(ValueError):
            validate_target(self.environment)
        run.assert_not_called()


if __name__ == "__main__":
    unittest.main()
