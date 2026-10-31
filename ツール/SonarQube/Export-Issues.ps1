# Windows entry point: forward every argument to the Python CLI.
$ErrorActionPreference = 'Stop'
$taskPython = Get-Command python -ErrorAction SilentlyContinue
if ($taskPython) {
    & $taskPython.Source -X utf8 (Join-Path $PSScriptRoot 'export_issues.py') @args
} else {
    $taskLauncher = Get-Command py -ErrorAction SilentlyContinue
    if (-not $taskLauncher) {
        throw 'Install Python 3.10 or later.'
    }
    & $taskLauncher.Source -3 -X utf8 (Join-Path $PSScriptRoot 'export_issues.py') @args
}
exit $LASTEXITCODE
