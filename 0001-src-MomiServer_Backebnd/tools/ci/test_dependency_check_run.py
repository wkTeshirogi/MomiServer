"""Linux/Bash lifecycle tests with short-lived local commands, never NVD."""

import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import unittest


@unittest.skipUnless(sys.platform == "linux" and shutil.which("bash"), "Requires Linux/Bash CI process groups.")
class DependencyCheckRunTest(unittest.TestCase):
    def setUp(self):
        self.script = Path(__file__).with_name("dependency_check_run.sh").resolve()

    def command(self, source, interval="1", grace="1"):
        return ["bash", str(self.script), interval, grace, sys.executable, "-c", source]

    def test_bash_syntax(self):
        subprocess.run(["bash", "-n", str(self.script)], check=True)

    def test_success_and_streamed_output(self):
        result = subprocess.run(self.command("print('Downloaded 1/1 (100%)')"),
                                capture_output=True, text=True, timeout=5)
        self.assertEqual(0, result.returncode)
        self.assertIn("Downloaded 1/1 (100%)", result.stdout)

    def test_command_failure_exit_code_is_preserved(self):
        result = subprocess.run(self.command("raise SystemExit(23)"), capture_output=True, timeout=5)
        self.assertEqual(23, result.returncode)

    def test_command_not_found_is_failure(self):
        result = subprocess.run(["bash", str(self.script), "1", "1", "/missing/ci-command"],
                                capture_output=True, timeout=5)
        self.assertEqual(127, result.returncode)

    def test_heartbeat_does_not_terminate_a_silent_command_or_print_arguments(self):
        result = subprocess.run(self.command("import time; secret='synthetic-only'; time.sleep(2.3)"),
                                capture_output=True, text=True, timeout=6)
        self.assertEqual(0, result.returncode)
        self.assertGreaterEqual(result.stdout.count("Dependency-Check heartbeat:"), 2)
        self.assertIn("not download progress", result.stdout)
        self.assertNotIn("synthetic-only", result.stdout + result.stderr)

    def test_long_heartbeat_sleep_does_not_delay_completion(self):
        started = time.monotonic()
        result = subprocess.run(self.command("pass", interval="300"), capture_output=True, timeout=5)
        self.assertEqual(0, result.returncode)
        self.assertLess(time.monotonic() - started, 3)

    def test_invalid_parameters_fail_before_starting_command(self):
        for interval, grace in (("0", "1"), ("-1", "1"), ("oops", "1"), ("1", "0"), ("1", "oops")):
            with self.subTest(interval=interval, grace=grace):
                result = subprocess.run(self.command("print('must-not-run')", interval, grace),
                                        capture_output=True, text=True, timeout=5)
                self.assertEqual(2, result.returncode)
                self.assertNotIn("must-not-run", result.stdout + result.stderr)

    def test_missing_command_is_rejected(self):
        result = subprocess.run(["bash", str(self.script), "1", "1"], capture_output=True, timeout=5)
        self.assertEqual(2, result.returncode)

    def assert_stopped(self, pid):
        path = Path(f"/proc/{pid}/stat")
        if path.exists():
            self.assertEqual("Z", path.read_text().split(") ", 1)[1][0], f"Process {pid} is still active")

    def check_signal(self, signum, expected, ignore_term=False):
        with tempfile.TemporaryDirectory() as directory:
            state = Path(directory) / "state.json"
            source = (
                "import json,os,signal,subprocess,sys,time; "
                + "secret='synthetic-only-secret'; "
                + ("signal.signal(signal.SIGTERM, signal.SIG_IGN); " if ignore_term else "")
                + "child=subprocess.Popen([sys.executable,'-c','import time; time.sleep(60)']); "
                + f"open({str(state)!r},'w').write(json.dumps([os.getpid(),child.pid])); "
                + "time.sleep(60)"
            )
            process = subprocess.Popen(self.command(source), stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
            pids = []
            try:
                deadline = time.monotonic() + 5
                while time.monotonic() < deadline:
                    if state.exists() and state.stat().st_size:
                        pids = json.loads(state.read_text())
                        break
                    time.sleep(0.02)
                self.assertEqual(2, len(pids))
                self.assertEqual(pids[0], os.getpgid(pids[0]))
                self.assertEqual(pids[0], os.getpgid(pids[1]))
                self.assertNotEqual(os.getpgrp(), os.getpgid(pids[0]))
                process.send_signal(signum)
                stdout, stderr = process.communicate(timeout=7)
                self.assertEqual(expected, process.returncode)
                self.assertNotIn("synthetic-only-secret", stdout + stderr)
                for pid in pids:
                    self.assert_stopped(pid)
            finally:
                if process.poll() is None:
                    process.kill()
                    process.communicate(timeout=3)
                for pid in pids:
                    try:
                        os.kill(pid, signal.SIGKILL)
                    except ProcessLookupError:
                        pass

    def test_term_stops_analysis_descendants_and_heartbeat(self):
        self.check_signal(signal.SIGTERM, 143)

    def test_int_stops_analysis_descendants_and_heartbeat(self):
        self.check_signal(signal.SIGINT, 130)

    def test_shutdown_escalates_only_after_explicit_cancel(self):
        self.check_signal(signal.SIGTERM, 143, ignore_term=True)


if __name__ == "__main__":
    unittest.main()
