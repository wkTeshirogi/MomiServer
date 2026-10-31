import { type BatchGlobalSettingValues } from './service'

// Fixed definitions scoped to pages/Master/CommonSettings/Batch.
export type FieldName = keyof BatchGlobalSettingValues

export const sections: { title: string; fields: { name: FieldName; label: string; min: number; max?: number }[] }[] = [
  { title: '添付・作業ディレクトリ', fields: [
    { name: 'attachment_max_file_mib', label: '1ファイル最大サイズ（MiB）', min: 1, max: 10240 },
    { name: 'attachment_max_total_mib', label: '定義内の添付合計上限（MiB）', min: 1, max: 102400 },
    { name: 'attachment_max_count', label: '最大添付数', min: 1, max: 1000 },
    { name: 'work_directory_retention_days', label: '作業ディレクトリ保持期間（日）', min: 1, max: 3650 },
  ] },

  {
    title: '実行制御',
    fields: [
      { name: 'max_concurrent_executions', label: '同時実行数（エージェント1台あたり）', min: 1 },
      { name: 'default_timeout_seconds', label: '標準実行タイムアウト（秒）', min: 1 },
      { name: 'start_wait_timeout_seconds', label: '開始待ち期限（秒）', min: 1 },
      { name: 'cancel_grace_seconds', label: 'キャンセル停止猶予（秒）', min: 1 },
    ],
  },
  {
    title: '結果送信・確認',
    fields: [
      { name: 'result_retry_interval_seconds', label: '結果再送間隔（秒）', min: 1 },
      { name: 'result_retry_count', label: '結果再送回数（初回を除く）', min: 0 },
      { name: 'unsent_result_retention_days', label: '未送信結果保持期間（日）', min: 1 },
      { name: 'result_wait_timeout_seconds', label: '結果待ち期限（秒）', min: 1 },
    ],
  },
]
