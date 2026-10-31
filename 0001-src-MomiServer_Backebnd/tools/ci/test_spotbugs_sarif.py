"""Offline regression tests for the MR SARIF handoff."""

import copy
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from spotbugs_sarif import normalize_report


class SpotBugsSarifTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.source = self.root / "src/main/java/example/Example.java"
        self.source.parent.mkdir(parents=True)
        self.source.write_text("class Example {}\n", encoding="utf-8")
        self.report = {
            "version": "2.1.0",
            "runs": [{
                "tool": {"driver": {"name": "SpotBugs"}},
                "invocations": [{"executionSuccessful": True, "exitCode": 1}],
                "originalUriBaseIds": {"SOURCE": {"uri": self.source.parent.parent.as_uri() + "/"}},
                "results": [{
                    "ruleId": "NP_ALWAYS_NULL",
                    "message": {"text": "Possible null dereference"},
                    "locations": [{"physicalLocation": {
                        "artifactLocation": {"uri": "example/Example.java", "uriBaseId": "SOURCE"},
                        "region": {"startLine": 1},
                    }}],
                }],
            }],
        }

    def artifact(self, report=None):
        return (report or self.report)["runs"][0]["results"][0]["locations"][0]["physicalLocation"]["artifactLocation"]

    def test_findings_are_preserved_with_repository_relative_paths(self):
        self.assertEqual(1, normalize_report(self.report, self.root))
        self.assertEqual({"uri": "src/main/java/example/Example.java"}, self.artifact())
        self.assertEqual("NP_ALWAYS_NULL", self.report["runs"][0]["results"][0]["ruleId"])

    def test_clean_report_is_not_skipped(self):
        run = self.report["runs"][0]
        run["results"] = []
        run["invocations"][0]["exitCode"] = 0
        self.assertEqual(0, normalize_report(self.report, self.root))

    def test_absolute_source_uri(self):
        self.artifact().clear()
        self.artifact().update(uri=self.source.as_uri())
        normalize_report(self.report, self.root)
        self.assertEqual("src/main/java/example/Example.java", self.artifact()["uri"])

    def test_repository_relative_uri(self):
        self.artifact().clear()
        self.artifact().update(uri="src/main/java/example/Example.java")
        normalize_report(self.report, self.root)

    def test_nested_uri_base(self):
        self.report["runs"][0]["originalUriBaseIds"] = {
            "ROOT": {"uri": self.root.as_uri() + "/"},
            "SOURCE": {"uri": "src/main/java/", "uriBaseId": "ROOT"},
        }
        normalize_report(self.report, self.root)

    def test_analysis_errors_and_missing_classes_are_rejected(self):
        for code in (2, 3, 4, 5, 6, 7, -1):
            with self.subTest(code=code):
                report = copy.deepcopy(self.report)
                report["runs"][0]["invocations"][0]["exitCode"] = code
                with self.assertRaises(ValueError):
                    normalize_report(report, self.root)

    def test_unsuccessful_or_missing_invocation_is_rejected(self):
        for invocations in ([], [{"executionSuccessful": False, "exitCode": 0}]):
            with self.subTest(invocations=invocations):
                report = copy.deepcopy(self.report)
                report["runs"][0]["invocations"] = invocations
                with self.assertRaises(ValueError):
                    normalize_report(report, self.root)

    def test_error_notification_is_rejected(self):
        self.report["runs"][0]["invocations"][0]["toolExecutionNotifications"] = [{"level": "error"}]
        with self.assertRaises(ValueError):
            normalize_report(self.report, self.root)

    def test_unknown_cyclic_remote_and_outside_paths_are_rejected(self):
        for artifact in (
            {"uri": "example/Example.java", "uriBaseId": "UNKNOWN"},
            {"uri": "https://invalid.example/Example.java"},
            {"uri": "../outside.java"},
            {"uri": "%2e%2e/outside.java"},
            {"uri": "src/main/java/example/Missing.java"},
            {"uri": "build/generated/jooq/Example.java"},
        ):
            with self.subTest(artifact=artifact):
                report = copy.deepcopy(self.report)
                self.artifact(report).clear()
                self.artifact(report).update(artifact)
                with self.assertRaises(ValueError):
                    normalize_report(report, self.root)
        self.report["runs"][0]["originalUriBaseIds"]["SOURCE"]["uriBaseId"] = "SOURCE"
        with self.assertRaises(ValueError):
            normalize_report(self.report, self.root)

    def test_wrong_version_or_empty_runs_are_rejected(self):
        for report in ({"version": "2.0.0", "runs": [{}]}, {"version": "2.1.0", "runs": []}):
            with self.assertRaises(ValueError):
                normalize_report(report, self.root)

    def test_unlocated_findings_are_rejected(self):
        self.report["runs"][0]["results"][0]["locations"] = []
        with self.assertRaises(ValueError):
            normalize_report(self.report, self.root)

    def test_cli_removes_stale_output_on_failure(self):
        raw = self.root / "raw.sarif"
        output = self.root / "main.sarif"
        raw.write_text("{}", encoding="utf-8")
        output.write_text("stale", encoding="utf-8")
        result = subprocess.run([
            sys.executable, "-B", str(Path(__file__).with_name("spotbugs_sarif.py")),
            str(raw), str(output), "--root", str(self.root),
        ], capture_output=True, text=True)
        self.assertEqual(1, result.returncode)
        self.assertFalse(output.exists())

    def test_cli_writes_valid_report(self):
        raw = self.root / "raw.sarif"
        output = self.root / "main.sarif"
        raw.write_text(json.dumps(self.report), encoding="utf-8")
        result = subprocess.run([
            sys.executable, "-B", str(Path(__file__).with_name("spotbugs_sarif.py")),
            str(raw), str(output), "--root", str(self.root),
        ], capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("src/main/java/example/Example.java", self.artifact(json.loads(output.read_text()))["uri"])


if __name__ == "__main__":
    unittest.main()
