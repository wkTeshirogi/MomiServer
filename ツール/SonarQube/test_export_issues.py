"""Offline tests: python -X utf8 -m unittest discover -v."""

from copy import deepcopy
from io import BytesIO, StringIO
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urlsplit
from urllib.request import Request

import export_issues as export


def issue(index=1, **overrides):
    return {"key": f"issue-{index}", "project": "momiServer", "rule": "java:S116",
            "component": "momiServer:src/Test.java", "line": index, "message": "命名を修正してください。",
            "severity": "MAJOR", "status": "OPEN", "issueStatus": "OPEN", **overrides}


def page(issues, total=None, index=1, size=2):
    return {"paging": {"pageIndex": index, "pageSize": size,
                       "total": len(issues) if total is None else total},
            "issues": issues, "components": [{"key": "momiServer:src/Test.java", "path": "src/Test.java"}]}


def quality_gate(status="ERROR"):
    return {"projectStatus": {"status": status, "ignoredConditions": False, "caycStatus": "compliant",
                              "conditions": [
                                  {"status": "ERROR" if status == "ERROR" else "OK", "metricKey": "new_coverage",
                                   "comparator": "LT", "errorThreshold": "80", "actualValue": "72.5"},
                                  {"status": "OK", "metricKey": "new_duplicated_lines_density",
                                   "comparator": "GT", "errorThreshold": "3", "actualValue": "1.2"}],
                              "period": {"mode": "previous_version", "date": "2026-10-20T00:00:00+0000"}}}


def coverage_file(index=1, measures=None):
    path = "src/Test.java" if index == 1 else f"src/Test{index}.java"
    return {"key": "momiServer:" + path, "path": path, "qualifier": "FIL", "language": "java",
            "measures": [{"metric": "coverage", "value": "72.5"},
                         {"metric": "line_coverage", "value": "75"},
                         {"metric": "branch_coverage", "value": "60"},
                         {"metric": "lines_to_cover", "value": "100"},
                         {"metric": "uncovered_lines", "value": "25"},
                         {"metric": "conditions_to_cover", "value": "20"},
                         {"metric": "uncovered_conditions", "value": "8"},
                         {"metric": "new_coverage", "period": {"value": "0"}},
                         {"metric": "new_lines_to_cover", "period": {"value": "4"}},
                         {"metric": "new_uncovered_lines", "period": {"value": "4"}}] if measures is None else measures}


def coverage_page(components, total=None, index=1, size=500):
    return {"paging": {"pageIndex": index, "pageSize": size, "total": len(components) if total is None else total},
            "baseComponent": {"key": "momiServer", "qualifier": "TRK", "measures": coverage_file()["measures"]},
            "components": components, "period": {"mode": "previous_version", "date": "2026-10-20T00:00:00+0000"}}


def command_response(issues):
    def response(endpoint, params):
        if endpoint == "issues/search":
            return page(issues, size=500)
        if endpoint == "qualitygates/project_status":
            return quality_gate()
        if endpoint == "measures/component_tree":
            return coverage_page([coverage_file()])
        raise AssertionError(f"Unexpected API: {endpoint}")
    return response


class CoverageTest(unittest.TestCase):
    def fetch(self, responses, size=500, filters=None):
        client = Mock()
        client.get.side_effect = responses
        with patch("sys.stderr", new=StringIO()):
            raw = export.fetch_coverage(client, "momiServer", filters or {}, size)
        return raw, client

    def test_all_file_pages_are_fetched(self):
        responses = [coverage_page([coverage_file(i) for i in range(start, min(start + 100, 548))],
                                   total=547, index=index, size=100)
                     for index, start in enumerate(range(1, 548, 100), 1)]
        raw, client = self.fetch(responses, size=100)
        self.assertEqual(547, raw["total_files"])
        self.assertEqual(6, client.get.call_count)
        self.assertEqual(6, client.get.call_args.args[1]["p"])
        self.assertEqual("72.5", raw["baseComponent"]["measures"][0]["value"])

    def test_scope_matches_without_forwarding_issue_filters(self):
        for scope in ({}, {"branch": "main"}, {"pullRequest": "123"}):
            raw, client = self.fetch([coverage_page([])], filters={**scope, "issueStatuses": "OPEN", "createdAfter": "2026-01-01"})
            params = client.get.call_args.args[1]
            self.assertEqual("momiServer", params["component"])
            self.assertEqual("FIL", params["qualifiers"])
            self.assertEqual("leaves", params["strategy"])
            self.assertEqual(set(export.COVERAGE_METRICS), set(params["metricKeys"].split(",")))
            self.assertNotIn("issueStatuses", params)
            self.assertNotIn("createdAfter", params)
            for key, value in scope.items():
                self.assertEqual(value, params[key])
            self.assertEqual(0, raw["total_files"])

    def test_zero_percent_is_not_missing_and_covered_counts_are_derived(self):
        metrics = export.coverage_measures(coverage_file())
        self.assertEqual(72.5, metrics["coverage"])
        self.assertEqual(0.0, metrics["new_coverage"])
        self.assertIsNone(metrics["new_branch_coverage"])
        self.assertEqual(75, metrics["covered_lines"])
        self.assertEqual(12, metrics["covered_conditions"])
        self.assertEqual(0, metrics["new_covered_lines"])
        self.assertIsNone(metrics["new_covered_conditions"])

    def test_no_measures_are_not_assumed_zero_or_fully_covered(self):
        metrics = export.coverage_measures(coverage_file(measures=[]))
        self.assertTrue(all(value is None for value in metrics.values()))

    def test_period_zero_is_preferred_over_top_level_value_for_new_code(self):
        metrics = export.coverage_measures(coverage_file(measures=[
            {"metric": "new_coverage", "value": "99", "period": {"value": 0}},
            {"metric": "coverage", "value": "50", "period": {"value": "5"}}]))
        self.assertEqual(0, metrics["new_coverage"])
        self.assertEqual(50, metrics["coverage"])

    def test_new_code_top_level_values_are_supported(self):
        metrics = export.coverage_measures(coverage_file(measures=[{"metric": "new_coverage", "value": "85.5"}]))
        self.assertEqual(85.5, metrics["new_coverage"])

    def test_bad_values_duplicate_metrics_and_impossible_counts_are_rejected(self):
        invalid = [[{"metric": "coverage", "value": value}] for value in ("NaN", "Infinity", "bad", "-1", "101", True)]
        invalid += [[{"metric": "uncovered_lines", "value": "1.5"}],
                    [{"metric": "coverage", "value": "50"}, {"metric": "coverage", "value": "60"}],
                    [{"metric": "lines_to_cover", "value": "1"}, {"metric": "uncovered_lines", "value": "2"}],
                    [{"metric": "new_coverage", "period": "invalid"}]]
        for measures in invalid:
            with self.subTest(measures=measures), self.assertRaises(export.ExportError):
                export.coverage_measures(coverage_file(measures=measures))

    def test_duplicate_short_changed_and_invalid_pages_fail(self):
        changed = coverage_page([coverage_file(3)], total=3, index=2, size=2)
        changed["baseComponent"]["measures"][0]["value"] = "80"
        invalid_sequences = [
            [coverage_page([coverage_file()], total=2, size=2)],
            [coverage_page([coverage_file(), coverage_file(2)], total=3, size=2),
             coverage_page([coverage_file()], total=3, index=2, size=2)],
            [coverage_page([coverage_file(), coverage_file(2)], total=3, size=2),
             coverage_page([coverage_file(3)], total=4, index=2, size=2)],
            [coverage_page([coverage_file(), coverage_file(2)], total=3, size=2), changed],
            [{"paging": {}, "components": [], "baseComponent": {}}]]
        for responses in invalid_sequences:
            with self.subTest(responses=responses), self.assertRaises(export.ExportError):
                self.fetch(responses, size=2)

    def test_other_project_and_non_file_components_are_rejected(self):
        other_project = coverage_page([])
        other_project["baseComponent"]["key"] = "other-project"
        directory = coverage_file()
        directory["qualifier"] = "DIR"
        for response in (other_project, coverage_page([directory])):
            with self.subTest(response=response), self.assertRaises(export.ExportError):
                self.fetch([response])


class QualityGateTest(unittest.TestCase):
    def setUp(self):
        self.metadata = {"project": "momiServer", "server_url": "https://sonar.example"}

    def test_same_scope_is_used_without_issue_filters(self):
        for scope in ({}, {"branch": "feature/gate"}, {"pullRequest": "123"}):
            client = Mock()
            export.fetch_quality_gate(client, "momiServer", {
                **scope, "issueStatuses": "OPEN,CONFIRMED", "createdAfter": "2026-10-01"})
            client.get.assert_called_once_with("qualitygates/project_status", {"projectKey": "momiServer", **scope})

    def test_failed_conditions_preserve_values_and_correct_comparator_direction(self):
        raw = quality_gate()
        original = deepcopy(raw)
        raw["projectStatus"]["conditions"][1]["status"] = "ERROR"
        gate = export.normalize_quality_gate(raw, self.metadata)
        self.assertEqual("ERROR", gate["status"])
        self.assertEqual(2, gate["failed_count"])
        coverage, duplication = gate["failed_conditions"]
        self.assertEqual("72.5", coverage["actual_value"])
        self.assertEqual({"operator": "<", "threshold": "80"}, coverage["failure_when"])
        self.assertEqual({"operator": ">=", "threshold": "80"}, coverage["required_when"])
        self.assertEqual({"operator": ">", "threshold": "3"}, duplication["failure_when"])
        self.assertEqual({"operator": "<=", "threshold": "3"}, duplication["required_when"])
        self.assertNotIn("file", coverage)
        self.assertEqual(original["projectStatus"]["period"], gate["period"])
        self.assertEqual("compliant", gate["cayc_status"])
        self.assertEqual("ERROR", raw["projectStatus"]["conditions"][1]["status"])

    def test_passing_gate_has_no_failed_conditions(self):
        raw = quality_gate("OK")
        original = deepcopy(raw)
        gate = export.normalize_quality_gate(raw, self.metadata)
        self.assertEqual("OK", gate["status"])
        self.assertEqual([], gate["failed_conditions"])
        self.assertEqual(0, gate["failed_count"])
        self.assertEqual(original, raw)

    def test_none_is_retained_as_not_evaluated(self):
        gate = export.normalize_quality_gate({"projectStatus": {"status": "NONE"}}, self.metadata)
        self.assertEqual("NONE", gate["status"])
        self.assertEqual([], gate["conditions"])
        self.assertIsNone(gate["ignored_conditions"])

    def test_warning_is_not_misclassified_as_failure(self):
        raw = quality_gate("WARN")
        raw["projectStatus"]["conditions"][0]["status"] = "WARN"
        gate = export.normalize_quality_gate(raw, self.metadata)
        self.assertEqual(0, gate["failed_count"])
        self.assertEqual(1, gate["warning_count"])
        self.assertTrue(gate["warning_conditions"][0]["requires_attention"])

    def test_ignored_conditions_and_cayc_status_are_not_reinterpreted_as_failures(self):
        raw = quality_gate("OK")
        raw["projectStatus"].update(ignoredConditions=True, caycStatus="non-compliant")
        gate = export.normalize_quality_gate(raw, self.metadata)
        self.assertTrue(gate["ignored_conditions"])
        self.assertEqual("non-compliant", gate["cayc_status"])
        self.assertEqual("OK", gate["status"])

    def test_missing_threshold_or_actual_value_is_not_fabricated(self):
        raw = quality_gate()
        del raw["projectStatus"]["conditions"][0]["errorThreshold"]
        del raw["projectStatus"]["conditions"][0]["actualValue"]
        condition = export.normalize_quality_gate(raw, self.metadata)["failed_conditions"][0]
        self.assertIsNone(condition["actual_value"])
        self.assertIsNone(condition["required_when"])
        self.assertIsNone(condition["error_threshold"])

    def test_unknown_comparator_does_not_produce_a_guessed_target(self):
        raw = quality_gate()
        raw["projectStatus"]["conditions"][0]["comparator"] = "FUTURE_OPERATOR"
        condition = export.normalize_quality_gate(raw, self.metadata)["failed_conditions"][0]
        self.assertEqual("FUTURE_OPERATOR", condition["comparator"])
        self.assertIsNone(condition["required_when"])

    def test_invalid_responses_are_not_reported_as_passes(self):
        invalid = [{}, {"projectStatus": []}, {"projectStatus": {"status": "UNKNOWN", "conditions": []}},
                   {"projectStatus": {"status": [], "conditions": []}},
                   {"projectStatus": {"status": "ERROR"}},
                   {"projectStatus": {"status": "OK", "conditions": None}},
                   {"projectStatus": {"status": "OK", "conditions": [], "ignoredConditions": "true"}},
                   {"projectStatus": {"status": "OK", "conditions": [None]}},
                   {"projectStatus": {"status": "ERROR", "conditions": [{"status": "ERROR", "metricKey": ""}]}},
                   {"projectStatus": {"status": "ERROR", "conditions": [{"status": [], "metricKey": "coverage"}]}},
                   {"projectStatus": {"status": "ERROR", "conditions": [{"status": "ERROR", "metricKey": "coverage", "comparator": {}}]}}]
        for response in invalid:
            with self.subTest(response=response), self.assertRaises(export.ExportError):
                export.normalize_quality_gate(response, self.metadata)

    def test_dashboard_link_preserves_branch_and_pr(self):
        for scope, expected in (({"branch": "feature/gate"}, "branch=feature%2Fgate"),
                                ({"pull_request": "123"}, "pullRequest=123")):
            gate = export.normalize_quality_gate(quality_gate(), {**self.metadata, **scope})
            self.assertIn(expected, gate["sonarqube_url"])


class PaginationTest(unittest.TestCase):
    def fetch(self, responses, size=2):
        client = Mock()
        client.get.side_effect = responses
        with patch("sys.stderr", new=StringIO()):
            result = export.fetch_issues(client, "momiServer", {"issueStatuses": "OPEN,CONFIRMED"}, size)
        return result, client

    def test_collects_all_547_issues_instead_of_only_first_100(self):
        responses = [page([issue(i) for i in range(start, min(start + 100, 548))],
                          total=547, index=index, size=100)
                     for index, start in enumerate(range(1, 548, 100), 1)]
        result, client = self.fetch(responses, size=100)
        self.assertEqual(547, result["total"])
        self.assertEqual(6, client.get.call_count)
        self.assertEqual(1, len(result["components"]))
        self.assertEqual(6, client.get.call_args.args[1]["p"])
        self.assertEqual("momiServer", client.get.call_args.args[1]["componentKeys"])
        self.assertEqual("OPEN,CONFIRMED", client.get.call_args.args[1]["issueStatuses"])

    def test_empty_project_is_a_complete_export(self):
        result, client = self.fetch([page([])])
        self.assertEqual(0, result["total"])
        client.get.assert_called_once()

    def test_exact_page_boundary_does_not_request_extra_page(self):
        result, client = self.fetch([page([issue(1), issue(2)])])
        self.assertEqual(2, result["total"])
        client.get.assert_called_once()

    def test_duplicate_issue_refuses_success(self):
        with self.assertRaisesRegex(export.ExportError, "重複"):
            self.fetch([page([issue(1), issue(2)], total=3), page([issue(2)], total=3, index=2)])

    def test_changed_total_refuses_success(self):
        with self.assertRaisesRegex(export.ExportError, "総件数が変化"):
            self.fetch([page([issue(1), issue(2)], total=3), page([issue(3)], total=4, index=2)])

    def test_short_page_refuses_partial_success(self):
        with self.assertRaisesRegex(export.ExportError, "途切れ"):
            self.fetch([page([issue(1)], total=3)])

    def test_more_than_10000_results_refuses_truncation(self):
        with self.assertRaisesRegex(export.ExportError, "10,000"):
            self.fetch([page([], total=10001)])

    def test_foreign_project_refuses_silently_ignored_filter(self):
        with self.assertRaisesRegex(export.ExportError, "別プロジェクト"):
            self.fetch([page([issue(project="other-project")])])

    def test_ignored_status_filter_cannot_export_fixed_issues_as_open(self):
        with self.assertRaisesRegex(export.ExportError, "指定した状態"):
            self.fetch([page([issue(issueStatus="FIXED")])])

    def test_missing_rule_or_invalid_components_are_rejected(self):
        broken_component = page([issue()])
        broken_component["components"] = [None]
        for response in (page([issue(rule="")]), broken_component):
            with self.subTest(response=response), self.assertRaises(export.ExportError):
                self.fetch([response])

    def test_invalid_paging_is_rejected(self):
        for bad in ({"issues": []}, page([], total=-1), page([], index=2), page([], size=0)):
            with self.subTest(response=bad), self.assertRaises(export.ExportError):
                self.fetch([bad])

    def test_reduced_server_page_size_is_followed(self):
        result, client = self.fetch([page([issue(1)], total=2, size=1),
                                     page([issue(2)], total=2, index=2, size=1)], size=2)
        self.assertEqual(2, result["total"])
        self.assertEqual(1, client.get.call_args.args[1]["ps"])


class ClientTest(unittest.TestCase):
    def client(self, token="test-private-token"):
        client = export.SonarClient("https://sonar.example/sonarqube", token)
        client.opener = Mock()
        response = Mock()
        response.read.return_value = b'{"issues": []}'
        client.opener.open.return_value.__enter__ = Mock(return_value=response)
        client.opener.open.return_value.__exit__ = Mock(return_value=False)
        return client

    def test_token_is_header_only_and_query_is_encoded(self):
        client = self.client()
        self.assertEqual({"issues": []}, client.get("issues/search", {"componentKeys": "project:a b"}))
        request = client.opener.open.call_args.args[0]
        self.assertEqual("Bearer test-private-token", request.get_header("Authorization"))
        self.assertNotIn("test-private-token", request.full_url)
        self.assertEqual({"componentKeys": ["project:a b"]}, parse_qs(urlsplit(request.full_url).query))
        self.assertIn("/sonarqube/api/issues/search", request.full_url)

    def test_basic_auth_is_supported_explicitly(self):
        client = export.SonarClient("https://sonar.example", "token", auth="basic")
        self.assertEqual("Basic dG9rZW46", client.headers["Authorization"])

    def test_401_has_actionable_message_without_error_body_or_token(self):
        client = self.client()
        client.opener.open.side_effect = HTTPError("https://sonar.example", 401, "Unauthorized", {},
                                                  BytesIO(b'test-private-token'))
        with self.assertRaisesRegex(export.ExportError, "HTTP 401") as error:
            client.get("issues/search")
        self.assertNotIn("test-private-token", str(error.exception))

    @patch("export_issues.time.sleep")
    def test_transient_errors_retry_with_bounded_backoff(self, sleep):
        client = self.client()
        success = client.opener.open.return_value
        client.opener.open.side_effect = [HTTPError("url", 429, "limited", {"Retry-After": "999"}, None), success]
        self.assertEqual({"issues": []}, client.get("issues/search"))
        sleep.assert_called_once_with(30)

    @patch("export_issues.time.sleep")
    def test_retry_count_is_bounded(self, sleep):
        client = self.client()
        client.opener.open.side_effect = HTTPError("url", 503, "Unavailable", {}, None)
        with self.assertRaisesRegex(export.ExportError, "HTTP 503"):
            client.get("issues/search")
        self.assertEqual(3, client.opener.open.call_count)

    def test_invalid_json_and_network_failure_are_readable(self):
        client = self.client()
        client.opener.open.side_effect = URLError("test-private-token")
        with self.assertRaisesRegex(export.ExportError, "通信できません") as error:
            client.get("issues/search")
        self.assertNotIn("test-private-token", str(error.exception))
        client = self.client()
        client.opener.open.return_value.__enter__.return_value.read.return_value = b'<html>login</html>'
        with self.assertRaisesRegex(export.ExportError, "JSON"):
            client.get("issues/search")

    def test_cross_origin_redirect_and_tls_downgrade_are_blocked(self):
        handler = export.SafeRedirectHandler()
        for old, new in (("https://sonar.example/api/issues/search", "https://other.example/api/issues/search"),
                         ("https://sonar.example/api/issues/search", "http://sonar.example/api/issues/search")):
            with self.subTest(new=new), self.assertRaises(export.ExportError):
                handler.redirect_request(Request(old), None, 302, "Found", {}, new)

    def test_same_origin_and_https_upgrade_redirects_are_allowed(self):
        handler = export.SafeRedirectHandler()
        for old, new in (("https://sonar.example/api/issues/search", "https://sonar.example/api/issues/search/"),
                         ("http://sonar.example/api/issues/search", "https://sonar.example/api/issues/search")):
            redirected = handler.redirect_request(Request(old, headers={"Authorization": "Bearer token"}),
                                                  None, 302, "Found", {}, new)
            self.assertEqual(new, redirected.full_url)

    def test_credentials_in_server_url_are_rejected(self):
        for url in ("file:///etc/passwd", "https://token@sonar.example", "https://sonar.example?token=secret"):
            with self.subTest(url=url), self.assertRaises(export.ExportError):
                export.SonarClient(url)


class OutputTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        (self.root / "src").mkdir()
        (self.root / "src" / "Test.java").touch()
        self.meta = {"project": "momiServer", "server_url": "https://sonar.example",
                     "exported_at": "2026-10-20T12:00:00+00:00", "complete": True,
                     "filters": {"issueStatuses": "OPEN,CONFIRMED"}}
        self.raw = {"total": 2, "components": page([])["components"], "quality_gate": quality_gate(),
                    "coverage": coverage_page([coverage_file()]),
                    "issues": [issue(2), issue(1, severity="CRITICAL", textRange={"startLine": 10, "endLine": 11,
                                "startOffset": 3, "endOffset": 9}, flows=[{"locations": [{"component": "momiServer:src/Other.java",
                                "textRange": {"startLine": 20}, "msg": "関連箇所"}]}])]}

    def test_ai_output_preserves_locations_links_order_and_file_index(self):
        original = deepcopy(self.raw)
        report = export.normalize(self.raw, self.meta, self.root, {})
        first = report["issues"][0]
        self.assertEqual("issue-1", first["key"])
        self.assertEqual(10, first["line"])
        self.assertTrue(first["file_exists"])
        self.assertEqual(str(self.root / "src/Test.java"), first["absolute_path"])
        self.assertEqual(20, first["related_locations"][0]["line"])
        self.assertFalse(first["related_locations"][0]["file_exists"])
        self.assertEqual(["issue-2", "issue-1"], report["by_file"]["src/Test.java"])
        self.assertIn("open=issue-1", first["sonarqube_url"])
        self.assertEqual(original, self.raw)

    def test_path_traversal_and_absolute_server_paths_are_not_mapped_locally(self):
        for value in ("../secret", "/etc/passwd", "C:/secret", "..\\secret"):
            location = export.issue_location("component", None, None, {"component": value}, "momiServer", self.root)
            self.assertIsNone(location["absolute_path"])

    def test_project_level_issue_does_not_invent_a_source_path(self):
        location = export.issue_location("momiServer", None, None, {}, "momiServer", self.root)
        self.assertIsNone(location["file"])
        self.assertIsNone(location["line"])

    def test_run_directories_preserve_history_and_utf8_outputs(self):
        report = export.normalize(self.raw, self.meta, self.root, {})
        first = export.write_results(self.root / "result", self.raw, report)
        second = export.write_results(self.root / "result", self.raw, report)
        self.assertNotEqual(first, second)
        self.assertEqual({"issues.json", "issues.raw.json", "issues.jsonl", "quality_gate.json",
                          "coverage.json", "coverage.jsonl", "summary.md"},
                         {path.name for path in first.iterdir()})
        self.assertEqual(2, len(json.loads((first / "issues.json").read_text(encoding="utf-8"))["issues"]))
        records = [json.loads(line) for line in (first / "issues.jsonl").read_text(encoding="utf-8").splitlines()]
        self.assertEqual(2, len(records))
        self.assertIn("命名を修正", records[0]["message"])
        self.assertIn("全ページ取得済み", (first / "summary.md").read_text(encoding="utf-8"))
        self.assertFalse(list((self.root / "result").glob(".sonar-export-*")))

    def test_quality_gate_is_included_in_json_raw_and_summary(self):
        report = export.normalize(self.raw, self.meta, self.root, {})
        run = export.write_results(self.root / "result", self.raw, report)
        gate = json.loads((run / "quality_gate.json").read_text(encoding="utf-8"))
        combined = json.loads((run / "issues.json").read_text(encoding="utf-8"))
        raw = json.loads((run / "issues.raw.json").read_text(encoding="utf-8"))
        summary = (run / "summary.md").read_text(encoding="utf-8")
        self.assertEqual(gate, combined["quality_gate"])
        self.assertEqual(self.raw["quality_gate"], raw["quality_gate"])
        self.assertEqual("new_coverage", gate["failed_conditions"][0]["metric_key"])
        self.assertIn("判定: **ERROR**", summary)
        self.assertIn("不合格条件: 1件", summary)
        self.assertIn("| new_coverage | ERROR | 72.5 | < 80 | >= 80 |", summary)

    def test_coverage_json_jsonl_raw_and_summary_share_the_same_data(self):
        report = export.normalize(self.raw, self.meta, self.root, {})
        run = export.write_results(self.root / "result", self.raw, report)
        coverage = json.loads((run / "coverage.json").read_text(encoding="utf-8"))
        combined = json.loads((run / "issues.json").read_text(encoding="utf-8"))
        raw = json.loads((run / "issues.raw.json").read_text(encoding="utf-8"))
        records = [json.loads(line) for line in (run / "coverage.jsonl").read_text(encoding="utf-8").splitlines()]
        self.assertEqual(coverage, combined["coverage"])
        self.assertEqual(self.raw["coverage"], raw["coverage"])
        self.assertEqual(coverage["files"], records)
        self.assertEqual(str(self.root / "src/Test.java"), records[0]["absolute_path"])
        self.assertEqual(["issue-2", "issue-1"], records[0]["related_issue_keys"])
        summary = (run / "summary.md").read_text(encoding="utf-8")
        self.assertIn("| coverage | 72.5 | 0.0 |", summary)
        self.assertIn("| branch_coverage | 60.0 | — |", summary)
        self.assertIn("| src/Test.java | 72.5 | 0.0 | 25 | 4 | 8 |", summary)

    def test_files_are_sorted_by_uncovered_new_lines_then_total_lines(self):
        extra = coverage_file(2, [{"metric": "new_uncovered_lines", "period": {"value": "10"}}])
        self.raw["coverage"]["components"].append(extra)
        coverage = export.normalize(self.raw, self.meta, self.root, {})["coverage"]
        self.assertEqual("src/Test2.java", coverage["files"][0]["file"])
        self.assertFalse(coverage["files"][0]["file_exists"])
        self.assertEqual([], coverage["files"][0]["related_issue_keys"])

    def test_summary_explains_none_and_ignored_conditions(self):
        for raw_gate, expected in (({"projectStatus": {"status": "NONE"}}, "合格を意味しません"),
                                   (quality_gate("OK"), "一部の条件が無視")):
            if raw_gate["projectStatus"]["status"] == "OK":
                raw_gate["projectStatus"]["ignoredConditions"] = True
            self.raw["quality_gate"] = raw_gate
            report = export.normalize(self.raw, self.meta, self.root, {})
            self.assertIn(expected, export.markdown_summary(report))

    def test_error_without_condition_details_is_not_reported_as_no_problem(self):
        self.raw["quality_gate"] = {"projectStatus": {"status": "ERROR", "conditions": []}}
        report = export.normalize(self.raw, self.meta, self.root, {})
        self.assertIn("不合格条件の詳細がありません", export.markdown_summary(report))

    def test_failed_write_does_not_publish_partial_run(self):
        report = export.normalize(self.raw, self.meta, self.root, {})
        with patch.object(Path, "write_text", side_effect=OSError("disk full")), self.assertRaises(OSError):
            export.write_results(self.root / "result", self.raw, report)
        self.assertEqual([], list((self.root / "result").iterdir()))

    def test_rule_descriptions_are_fetched_once_per_unique_rule(self):
        client = Mock()
        client.get.return_value = {"rule": {"key": "java:S116", "name": "Naming", "htmlDesc": "<p>説明</p><pre>x &lt; y</pre>"}}
        rules = export.rule_details(client, self.raw["issues"])
        client.get.assert_called_once_with("rules/show", {"key": "java:S116"})
        self.assertIn("説明", rules["java:S116"]["description"])
        self.assertIn("x < y", rules["java:S116"]["description"])


class CommandTest(unittest.TestCase):
    @patch.dict("os.environ", {}, clear=True)
    @patch("sys.stdin.isatty", return_value=False)
    def test_missing_token_in_noninteractive_session_is_actionable(self, isatty):
        with tempfile.TemporaryDirectory() as root, patch("sys.stderr", new=StringIO()) as stderr:
            self.assertEqual(1, export.main(["--source-root", root]))
            self.assertIn("SONAR_TOKEN", stderr.getvalue())

    @patch.dict("os.environ", {"SONAR_TOKEN": "private-test-token"})
    @patch("export_issues.SonarClient")
    def test_complete_command_exports_without_persisting_token(self, factory):
        factory.return_value.url = "https://sonar.example"
        factory.return_value.get.side_effect = command_response([issue(1)])
        with tempfile.TemporaryDirectory() as root, patch("sys.stdout", new=StringIO()), patch("sys.stderr", new=StringIO()):
            output = Path(root) / "result"
            result = export.main(["--url", "https://sonar.example", "--source-root", root,
                                  "--output", str(output), "--skip-rule-details", "--branch", "main"])
            self.assertEqual(0, result)
            run = next(output.iterdir())
            for file in run.iterdir():
                self.assertNotIn("private-test-token", file.read_text(encoding="utf-8"))
            params = factory.return_value.get.call_args.args[1]
            self.assertEqual("main", params["branch"])
            factory.return_value.get.assert_any_call("qualitygates/project_status", {"projectKey": "momiServer", "branch": "main"})
            gate = json.loads((run / "quality_gate.json").read_text(encoding="utf-8"))
            self.assertEqual("ERROR", gate["status"])

    def test_branch_and_pull_request_are_mutually_exclusive(self):
        with patch("sys.stderr", new=StringIO()), self.assertRaises(SystemExit):
            export.parse_args(["--branch", "main", "--pull-request", "1"])

    @patch("export_issues.SonarClient")
    def test_all_statuses_and_anonymous_are_explicit_options(self, factory):
        factory.return_value.url = "https://sonar.example"
        factory.return_value.get.side_effect = command_response([issue(issueStatus="FIXED")])
        with tempfile.TemporaryDirectory() as root, patch("sys.stdout", new=StringIO()), patch("sys.stderr", new=StringIO()):
            self.assertEqual(0, export.main(["--anonymous", "--all-statuses", "--source-root", root,
                                            "--output", str(Path(root) / "result"), "--skip-rule-details"]))
            issue_call = next(call for call in factory.return_value.get.call_args_list if call.args[0] == "issues/search")
            self.assertNotIn("issueStatuses", issue_call.args[1])
            self.assertEqual("", factory.call_args.args[1])

    @patch("export_issues.SonarClient")
    def test_zero_issues_still_exports_failing_quality_gate(self, factory):
        factory.return_value.url = "https://sonar.example"
        factory.return_value.get.side_effect = command_response([])
        with tempfile.TemporaryDirectory() as root, patch("sys.stdout", new=StringIO()), patch("sys.stderr", new=StringIO()):
            output = Path(root) / "result"
            self.assertEqual(0, export.main(["--anonymous", "--source-root", root, "--output", str(output),
                                            "--skip-rule-details", "--pull-request", "123"]))
            run = next(output.iterdir())
            report = json.loads((run / "issues.json").read_text(encoding="utf-8"))
            self.assertEqual([], report["issues"])
            self.assertEqual(1, report["quality_gate"]["failed_count"])
            self.assertTrue(report["metadata"]["quality_gate_included"])
            self.assertTrue(report["metadata"]["coverage_included"])
            factory.return_value.get.assert_any_call("qualitygates/project_status", {"projectKey": "momiServer", "pullRequest": "123"})

    @patch("export_issues.SonarClient")
    def test_quality_gate_api_failure_does_not_publish_partial_export(self, factory):
        def response(endpoint, params):
            if endpoint == "qualitygates/project_status":
                raise export.ExportError("qualitygates/project_status: HTTP 403")
            return page([issue(1)], size=500)
        factory.return_value.get.side_effect = response
        with tempfile.TemporaryDirectory() as root, patch("sys.stdout", new=StringIO()), patch("sys.stderr", new=StringIO()) as stderr:
            output = Path(root) / "result"
            self.assertEqual(1, export.main(["--anonymous", "--source-root", root, "--output", str(output), "--skip-rule-details"]))
            self.assertIn("qualitygates/project_status", stderr.getvalue())
            self.assertFalse(output.exists())

    @patch("export_issues.SonarClient")
    def test_malformed_quality_gate_does_not_publish_partial_export(self, factory):
        factory.return_value.url = "https://sonar.example"
        def response(endpoint, params):
            if endpoint == "qualitygates/project_status":
                return {}
            return command_response([])(endpoint, params)
        factory.return_value.get.side_effect = response
        with tempfile.TemporaryDirectory() as root, patch("sys.stdout", new=StringIO()), patch("sys.stderr", new=StringIO()) as stderr:
            output = Path(root) / "result"
            self.assertEqual(1, export.main(["--anonymous", "--source-root", root, "--output", str(output), "--skip-rule-details"]))
            self.assertIn("品質ゲート", stderr.getvalue())
            self.assertFalse(output.exists())

    @patch("export_issues.SonarClient")
    def test_coverage_api_failure_does_not_publish_partial_export(self, factory):
        factory.return_value.url = "https://sonar.example"
        def response(endpoint, params):
            if endpoint == "measures/component_tree":
                raise export.ExportError("measures/component_tree: HTTP 403")
            return command_response([issue(1)])(endpoint, params)
        factory.return_value.get.side_effect = response
        with tempfile.TemporaryDirectory() as root, patch("sys.stdout", new=StringIO()), patch("sys.stderr", new=StringIO()) as stderr:
            output = Path(root) / "result"
            self.assertEqual(1, export.main(["--anonymous", "--source-root", root, "--output", str(output), "--skip-rule-details"]))
            self.assertIn("measures/component_tree", stderr.getvalue())
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
