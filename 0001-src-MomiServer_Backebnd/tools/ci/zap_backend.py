"""Start an isolated backend, scan it with ZAP, and always stop the process."""

import argparse
from contextlib import contextmanager
import json
import os
from pathlib import Path
import secrets
import signal
import socket
import struct
import subprocess
import sys
import tempfile
import time
from urllib.error import URLError
from urllib.request import ProxyHandler, build_opener

from zap_baseline import run_scan


def wait_for_database(host, port, timeout=120):
    """Wait for PostgreSQL to accept startup, not merely an open TCP port."""
    if host not in ("postgres", "127.0.0.1", "localhost") or not 1 <= port <= 65535:
        raise ValueError("Use the CI PostgreSQL service or a dedicated loopback test DB.")
    deadline = time.monotonic() + timeout
    startup = struct.pack("!I", 196608) + b"user\0zunda_user\0database\0zunda_zap\0\0"
    while time.monotonic() < deadline:
        try:
            with socket.create_connection((host, port), timeout=2) as connection:
                connection.sendall(struct.pack("!I", len(startup) + 4) + startup)
                if connection.recv(1) == b"R":
                    return
        except OSError:
            pass
        time.sleep(1)
    raise TimeoutError("CI PostgreSQL did not become ready.")


def backend_command(java, jar, directory, host, database_port, http_port):
    """Pin the data source and disable outbound/background work for this run."""
    return [
        str(java), f"-Djava.io.tmpdir={directory}", "-jar", str(jar),
        "--spring.profiles.active=ci-zap", "--server.address=127.0.0.1", f"--server.port={http_port}",
        f"--spring.datasource.url=jdbc:postgresql://{host}:{database_port}/zunda_zap",
        "--spring.datasource.username=zunda_user", "--spring.datasource.password=zunda_pass",
        "--app.scheduling.enabled=false", "--spring.batch.job.enabled=false",
        "--mqtt.enabled=false", "--app.agent-mqtt.enabled=false",
        "--notification.discord.enabled=false", "--notification.discord.security-events.enabled=false",
        "--app.vehicle-inspection-notification.enabled=false",
        f"--app.vehicle-import.root={directory}/vehicle-imports",
        f"--app.batch.file-storage.root-directory={directory}/batch-files",
        "--logging.level.jp.xoki.web.zunda=INFO", "--logging.level.org.springframework.security=INFO",
    ]


def wait_for_backend(process, origin, timeout=180):
    deadline = time.monotonic() + timeout
    opener = build_opener(ProxyHandler({}))
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError("Backend exited before readiness; inspect backend.log.")
        try:
            with opener.open(origin + "/v3/api-docs", timeout=2) as response:
                document = json.load(response)
                if response.status == 200 and document.get("openapi") and document.get("paths"):
                    return
        except (OSError, ValueError, URLError):
            pass
        time.sleep(1)
    raise TimeoutError("Backend did not expose a valid OpenAPI document within the wait limit.")


@contextmanager
def backend_process(command, environment, log):
    process = subprocess.Popen(command, env=environment, stdout=log, stderr=subprocess.STDOUT)
    try:
        yield process
    finally:
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=30)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)


def run_backend_scan(java, host="postgres", database_port=5432):
    java = java.resolve()
    jars = sorted(path.resolve() for path in Path("build/libs").glob("*.jar")
                  if not path.name.endswith("-plain.jar"))
    if not java.is_file() or len(jars) != 1:
        raise ValueError("An explicit Java 25 executable and exactly one Spring Boot JAR are required.")
    version = subprocess.run([str(java), "-version"], capture_output=True, text=True, check=True)
    if 'version "25' not in version.stderr + version.stdout:
        raise ValueError("The backend requires a Java 25 runtime.")
    output = Path("build/reports/zap").resolve()
    output.mkdir(parents=True, exist_ok=True)
    for path in output.glob("baseline.*"):
        path.unlink()
    (output / "exit-code.txt").unlink(missing_ok=True)
    wait_for_database(host, database_port)
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        http_port = listener.getsockname()[1]
    # Do not inherit CI tokens, production connection strings, or JVM option injection.
    environment = {name: os.environ[name] for name in ("PATH", "HOME", "LANG", "TZ") if name in os.environ}
    environment["JWT_SECRET"] = secrets.token_urlsafe(64)
    origin = f"http://127.0.0.1:{http_port}"
    with tempfile.TemporaryDirectory(prefix="zap-backend-") as directory, \
            (output / "backend.log").open("w", encoding="utf-8") as log:
        command = backend_command(java, jars[0], directory, host, database_port, http_port)
        with backend_process(command, environment, log) as process:
            wait_for_backend(process, origin)
            print(f"Isolated backend ready: {origin}; scanning public Swagger UI.", flush=True)
            run_scan(origin + "/swagger-ui/index.html", output)
            if process.poll() is not None:
                raise RuntimeError("Backend exited while ZAP was scanning.")
    print("Isolated backend stopped; reports and backend.log are preserved.", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", type=Path, required=True)
    parser.add_argument("--database-host", default="postgres")
    parser.add_argument("--database-port", type=int, default=5432)
    args = parser.parse_args()
    signal.signal(signal.SIGTERM, lambda *_: sys.exit(143))
    try:
        run_backend_scan(args.java, args.database_host, args.database_port)
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        print(f"Backend ZAP error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
