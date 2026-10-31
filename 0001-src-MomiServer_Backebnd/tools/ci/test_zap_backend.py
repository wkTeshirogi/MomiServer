"""Backend lifecycle checks without a real database, Java process, or scanner."""

import io
import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

from zap_backend import backend_command, backend_process, run_backend_scan, wait_for_backend, wait_for_database


class ZapBackendTest(unittest.TestCase):
    def test_runtime_options_pin_test_database_and_disable_background_work(self):
        command = backend_command(Path("java"), Path("backend.jar"), "/tmp/test", "postgres", 5432, 18080)
        for option in (
            "--spring.datasource.url=jdbc:postgresql://postgres:5432/zunda_zap",
            "--app.scheduling.enabled=false", "--spring.batch.job.enabled=false",
            "--mqtt.enabled=false", "--app.agent-mqtt.enabled=false",
            "--notification.discord.enabled=false", "--notification.discord.security-events.enabled=false",
            "--app.vehicle-inspection-notification.enabled=false", "--server.address=127.0.0.1",
        ):
            with self.subTest(option=option):
                self.assertIn(option, command)
        self.assertFalse(any("permitAll" in argument or "security.enabled=false" in argument for argument in command))

    @patch("zap_backend.subprocess.Popen")
    def test_process_is_stopped_after_success(self, popen):
        process = popen.return_value
        process.poll.return_value = None
        with backend_process(["java"], {}, io.StringIO()) as actual:
            self.assertIs(process, actual)
        process.terminate.assert_called_once()
        process.wait.assert_called_once_with(timeout=30)

    @patch("zap_backend.subprocess.Popen")
    def test_process_is_stopped_after_scanner_failure(self, popen):
        process = popen.return_value
        process.poll.return_value = None
        with self.assertRaises(ValueError):
            with backend_process(["java"], {}, io.StringIO()):
                raise ValueError("Simulated scanner failure")
        process.terminate.assert_called_once()

    @patch("zap_backend.subprocess.Popen")
    def test_process_is_stopped_on_pipeline_cancellation(self, popen):
        process = popen.return_value
        process.poll.return_value = None
        with self.assertRaises(SystemExit):
            with backend_process(["java"], {}, io.StringIO()):
                raise SystemExit(143)
        process.terminate.assert_called_once()

    @patch("zap_backend.subprocess.Popen")
    def test_shutdown_timeout_forces_process_exit(self, popen):
        process = popen.return_value
        process.poll.return_value = None
        process.wait.side_effect = [subprocess.TimeoutExpired("java", 30), 0]
        with backend_process(["java"], {}, io.StringIO()):
            pass
        process.kill.assert_called_once()
        self.assertEqual(2, process.wait.call_count)

    @patch("zap_backend.subprocess.Popen")
    def test_already_exited_process_is_not_signalled(self, popen):
        process = popen.return_value
        process.poll.return_value = 1
        with backend_process(["java"], {}, io.StringIO()):
            pass
        process.terminate.assert_not_called()

    def test_database_origin_is_restricted(self):
        with self.assertRaises(ValueError):
            wait_for_database("production.example", 5432)

    @patch("zap_backend.time.sleep")
    @patch("zap_backend.socket.create_connection")
    def test_database_waits_for_postgres_startup_protocol(self, connect, _sleep):
        connection = connect.return_value.__enter__.return_value
        connection.recv.side_effect = [b"E", b"R"]
        wait_for_database("postgres", 5432)
        self.assertEqual(2, connect.call_count)
        self.assertIn(b"zunda_zap", connection.sendall.call_args.args[0])

    def test_database_timeout_fails(self):
        with self.assertRaises(TimeoutError):
            wait_for_database("postgres", 5432, timeout=0)

    @patch("zap_backend.build_opener")
    def test_dead_backend_does_not_pass_readiness(self, opener):
        process = Mock()
        process.poll.return_value = 1
        with self.assertRaises(RuntimeError):
            wait_for_backend(process, "http://127.0.0.1:18080")
        opener.return_value.open.assert_not_called()

    @patch("zap_backend.build_opener")
    def test_openapi_readiness_requires_a_nonempty_document(self, opener):
        process = Mock()
        process.poll.return_value = None
        response = opener.return_value.open.return_value.__enter__.return_value
        response.status = 200
        response.read.return_value = b'{"openapi":"3.1.0","paths":{"/api/auth/login":{}}}'
        wait_for_backend(process, "http://127.0.0.1:18080")
        opener.return_value.open.assert_called_once_with("http://127.0.0.1:18080/v3/api-docs", timeout=2)

    def test_backend_readiness_timeout_fails(self):
        with self.assertRaises(TimeoutError):
            wait_for_backend(Mock(), "http://127.0.0.1:18080", timeout=0)

    def test_orchestration_ignores_external_target_and_production_environment(self):
        with tempfile.TemporaryDirectory() as directory:
            previous = Path.cwd()
            try:
                os.chdir(directory)
                Path("build/libs").mkdir(parents=True)
                Path("build/libs/backend.jar").write_bytes(b"fixture")
                java = Path(__file__).resolve()
                with patch("zap_backend.subprocess.run") as run, \
                        patch("zap_backend.subprocess.Popen") as popen, \
                        patch("zap_backend.wait_for_database"), patch("zap_backend.wait_for_backend"), \
                        patch("zap_backend.run_scan") as scan, patch("zap_backend.socket.socket") as socket, \
                        patch.dict(os.environ, {"ZAP_TARGET_URL": "https://production.example",
                                                "CI_JOB_TOKEN": "test-only", "JAVA_TOOL_OPTIONS": "-Dunsafe=true",
                                                "SPRING_DATASOURCE_URL": "jdbc:postgresql://production/db"}):
                    run.return_value = subprocess.CompletedProcess([], 0, "", 'openjdk version "25.0.4"')
                    socket.return_value.__enter__.return_value.getsockname.return_value = ("127.0.0.1", 18080)
                    process = popen.return_value
                    process.poll.return_value = None
                    run_backend_scan(java)
                    scan.assert_called_once_with("http://127.0.0.1:18080/swagger-ui/index.html",
                                                 Path("build/reports/zap").resolve())
                    child_environment = popen.call_args.kwargs["env"]
                    for name in ("CI_JOB_TOKEN", "JAVA_TOOL_OPTIONS", "SPRING_DATASOURCE_URL", "ZAP_TARGET_URL"):
                        self.assertNotIn(name, child_environment)
                    self.assertTrue(child_environment["JWT_SECRET"])
                    process.terminate.assert_called_once()
            finally:
                os.chdir(previous)

    @patch("zap_backend.subprocess.Popen")
    def test_missing_jar_is_rejected_before_backend_start(self, popen):
        with self.assertRaises(ValueError):
            run_backend_scan(Path("missing-java"))
        popen.assert_not_called()


if __name__ == "__main__":
    unittest.main()
