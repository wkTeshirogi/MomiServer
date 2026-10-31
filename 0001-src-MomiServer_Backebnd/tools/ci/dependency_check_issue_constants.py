"""Constants scoped to Dependency-Check's GitLab Issue reporting."""

from pathlib import Path

ISSUE_TITLE = "Dependency-Check定期実行結果"
ISSUE_MARKER = "<!-- momi:dependency-check-results:v1 -->"
RUN_MARKER_PREFIX = "<!-- momi:dependency-check-run:"
ISSUE_DESCRIPTION = (
    ISSUE_MARKER + "\n\n"
    "Dependency-Checkの定期実行結果を記録します。実行ごとにコメントを追加し、"
    "同一パイプラインの再投稿は既存コメントを更新します。\n\n"
    "取得・解析失敗は脆弱性なしを意味しません。詳細レポートは各ジョブの"
    "アーティファクトを参照してください。保存期限は1週間です。\n\n"
    "このIssueは履歴用です。自動クローズ・再オープンは行いません。"
)
REPORTS_ROOT = Path("build/reports")
HTTP_TIMEOUT_SECONDS = 30
MAX_API_BYTES = 8 * 1024 * 1024
MAX_REPORT_BYTES = 64 * 1024 * 1024
MAX_LOG_TAIL_BYTES = 2 * 1024 * 1024
MAX_PAGES = 1000
PAGE_SIZE = 100
MAX_DETAIL_ROWS = 50
MAX_CELL_CHARACTERS = 240
PROGRESS_PATTERN = r"Downloaded ([0-9][0-9,]{0,19})/([0-9][0-9,]{0,19}) \(([0-9]{1,3})%\)"
SEVERITIES = ("CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO", "UNKNOWN")
TIMEOUT_REASONS = frozenset({
    "job_execution_timeout", "stuck_or_timeout_failure", "server_timeout_running",
    "server_timeout_canceling",
})
