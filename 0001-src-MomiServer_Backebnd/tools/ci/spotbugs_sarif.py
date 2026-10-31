"""Validate SpotBugs SARIF and resolve source URIs before handing it to reviewdog."""

import argparse
import json
from pathlib import Path
import sys
from urllib.parse import quote, urljoin, urlsplit
from urllib.request import url2pathname


def normalize_report(report, root):
    """Reject incomplete analysis and map every source location into the checkout."""
    root = root.resolve()
    root_uri = root.as_uri() + "/"
    if report.get("version") != "2.1.0" or not report.get("runs"):
        raise ValueError("A nonempty SARIF 2.1.0 report is required.")
    count = 0
    for run in report["runs"]:
        if run.get("tool", {}).get("driver", {}).get("name") != "SpotBugs":
            raise ValueError("Expected a SpotBugs run.")
        invocations = run.get("invocations", [])
        if not invocations:
            raise ValueError("SpotBugs invocation status is missing.")
        for invocation in invocations:
            if invocation.get("executionSuccessful") is not True or invocation.get("exitCode") not in (0, 1):
                raise ValueError("SpotBugs analysis failed or has missing classes.")
            for key in ("toolExecutionNotifications", "toolConfigurationNotifications"):
                if any(note.get("level") == "error" for note in invocation.get(key, [])):
                    raise ValueError("SpotBugs reported an execution/configuration error.")

        bases = run.get("originalUriBaseIds", {})

        def base_uri(base_id, seen=()):
            if base_id in seen or base_id not in bases:
                raise ValueError("Unknown or cyclic SARIF URI base.")
            base = bases[base_id]
            parent = base_uri(base["uriBaseId"], seen + (base_id,)) if "uriBaseId" in base else root_uri
            return urljoin(parent, base.get("uri", ""))

        def normalize_location(location):
            uri = location.get("uri")
            if not isinstance(uri, str) or not uri:
                raise ValueError("A source artifact URI is required.")
            base = base_uri(location["uriBaseId"]) if "uriBaseId" in location else root_uri
            parsed = urlsplit(urljoin(base, uri))
            if parsed.scheme != "file" or parsed.netloc not in ("", "localhost") or parsed.query or parsed.fragment:
                raise ValueError("Source artifacts must be local files in the checkout.")
            path = Path(url2pathname(parsed.path)).resolve()
            relative = path.relative_to(root)
            if relative.parts[:3] != ("src", "main", "java") or not path.is_file():
                raise ValueError("Source artifact is not an existing production Java file.")
            # Decode before validating; preserve URI escaping for SARIF consumers.
            location["uri"] = "/".join(quote(part) for part in relative.parts)
            location.pop("uriBaseId", None)
            location.pop("index", None)

        def walk(value):
            if isinstance(value, dict):
                for key, item in value.items():
                    if key == "artifactLocation":
                        normalize_location(item)
                    else:
                        walk(item)
            elif isinstance(value, list):
                for item in value:
                    walk(item)

        for result in run.get("results", []):
            locations = result.get("locations", [])
            if not locations:
                raise ValueError("A SpotBugs finding has no source location.")
            for location in locations:
                physical = location.get("physicalLocation", {})
                line = physical.get("region", {}).get("startLine")
                if "artifactLocation" not in physical or not isinstance(line, int) or line < 1:
                    raise ValueError("A finding must have a source file and positive line number.")
            count += 1
        walk(run)
        for artifact in run.get("artifacts", []):
            normalize_location(artifact["location"])
        run.pop("originalUriBaseIds", None)
    return count


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    args = parser.parse_args()
    # A failed validation must not leave a previous successful normalized report.
    args.output.unlink(missing_ok=True)
    try:
        report = json.loads(args.input.read_text(encoding="utf-8"))
        count = normalize_report(report, args.root)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(f"Invalid SpotBugs SARIF: {error}", file=sys.stderr)
        return 1
    print(f"Validated SARIF 2.1.0: {count} findings, checkout-relative source paths.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
