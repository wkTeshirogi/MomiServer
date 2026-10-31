"""Run a report-only ZAP baseline against an explicitly approved test origin."""

import json
import os
from pathlib import Path
import subprocess
import sys
from urllib.parse import urlsplit


def origin(url):
    """Reject credentials and ambiguous URLs before starting the crawler."""
    if not url or any(character.isspace() or ord(character) < 32 for character in url):
        raise ValueError("A nonempty HTTP(S) URL without whitespace is required.")
    parsed = urlsplit(url)
    if (parsed.scheme not in ("http", "https") or not parsed.hostname
            or parsed.username is not None or parsed.password is not None
            or parsed.query or parsed.fragment or "\\" in url):
        raise ValueError("Use an HTTP(S) URL without credentials, query, or fragment.")
    return parsed.scheme, parsed.hostname.lower(), parsed.port or (443 if parsed.scheme == "https" else 80)


def validate_target(environment):
    if environment.get("ZAP_TEST_ENVIRONMENT") != "true":
        raise ValueError("ZAP_TEST_ENVIRONMENT=true is required for a disposable test environment.")
    target = environment.get("ZAP_TARGET_URL", "")
    allowed = environment.get("ZAP_ALLOWED_ORIGIN", "")
    if urlsplit(allowed).path not in ("", "/"):
        raise ValueError("ZAP_ALLOWED_ORIGIN must contain only scheme, hostname, and optional port.")
    if origin(target) != origin(allowed):
        raise ValueError("ZAP_TARGET_URL does not match the approved ZAP_ALLOWED_ORIGIN.")
    return target


def run_scan(target, output):
    output.mkdir(parents=True, exist_ok=True)
    reports = [output / f"baseline.{extension}" for extension in ("html", "json", "xml")]
    for report in reports:
        report.unlink(missing_ok=True)
    status_file = output / "exit-code.txt"
    status_file.unlink(missing_ok=True)
    # The packaged script requires this directory even with absolute report paths.
    Path("/zap/wrk").mkdir(parents=True, exist_ok=True)
    command = [
        "zap-baseline.py", "-t", target, "-m", "1", "-T", "5", "--autooff",
        "-r", str(reports[0]), "-J", str(reports[1]), "-x", str(reports[2]),
    ]
    result = subprocess.run(command, check=False, timeout=600, cwd=output)
    status_file.write_text(f"{result.returncode}\n", encoding="utf-8")
    if result.returncode not in (0, 2):
        raise ValueError(f"ZAP failed (exit {result.returncode}); inspect the job log.")
    if any(not report.is_file() or report.stat().st_size == 0 for report in reports):
        raise ValueError("ZAP did not generate all expected reports.")
    data = json.loads(reports[1].read_text(encoding="utf-8"))
    sites = data.get("site", []) if isinstance(data, dict) else []
    _, host, port = origin(target)
    if (not isinstance(sites, list) or not sites
            or not any(isinstance(site, dict) and int(site.get("@port", 0)) == port
                       and site.get("@host", "").lower() == host for site in sites)):
        raise ValueError("The ZAP report contains no results for the approved target.")
    print(f"ZAP baseline complete (exit {result.returncode}); warnings are report-only.")


def main():
    try:
        target = validate_target(os.environ)
        output = Path("build/reports/zap").resolve()
        run_scan(target, output)
    except (OSError, ValueError, TypeError, subprocess.SubprocessError) as error:
        print(f"ZAP baseline error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
