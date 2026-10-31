# スケジュール完了ログ取得API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0044 |
| 分類 | バッチスケジュール |
| メソッド | GET |
| パス | `/api/master/batch/schedule/executions/logs` |
| 認証 | 管理ユーザーJWT |
| 権限 | 管理者（AdminOnly） |
| 正常ステータス | HTTP 200 |

## 概要

指定スケジュールの予定日・時間帯に対応する完了ログを取得する。
対象状態はsuccess・failed・timeoutのみ。実行中、cancelled、expired等は含めない。

## 処理概要

1. 検索条件を検証する。
2. 予定日の0時以上から翌日0時未満を検索する。hour指定時は当該1時間に限定する。
3. 予定日時降順、内部ID降順で要求件数より1件多く取得する。
4. 要求件数分、残件有無、検索条件に一致する総件数を返す。

## クエリパラメータ

| 項目 | 型 | 必須 | 制約 | 説明 |
| --- | --- | --- | --- | --- |
| scheduleId | integer | 必須 | 1以上 | スケジュールID |
| date | date | 必須 | yyyy-MM-dd | 予定日 |
| hour | integer | 任意 | 0～23 | 省略時は終日 |
| offset | integer | 任意 | 0以上、既定0 | 読み飛ばす件数 |
| limit | integer | 任意 | 20、50、100。既定20 | 取得件数 |

## レスポンス

| 項目 | 型 | 説明 |
| --- | --- | --- |
| logs | array | 最大limit件。対象なしは空配列 |
| has_more | boolean | 次のページが存在するか |
| total_count | long | 検索条件に一致する総件数 |
| logs[].execution_id | string(UUID) | 実行ID |
| logs[].scheduled_fire_time | string(datetime) | 予定日時 |
| logs[].execution_status | string | success・failed・timeout |
| logs[].started_at / finished_at | string(datetime) / null | 開始・終了日時 |
| logs[].exit_code | integer / null | 終了コード |
| logs[].message | string / null | 結果メッセージ |
| logs[].stdout / stderr | string / null | 保存済み出力 |
| logs[].output_truncated | boolean / null | 保存時の出力省略有無 |

## 主なエラー

| HTTPステータス | code | 説明 |
| --- | --- | --- |
| 400 | INVALID_REQUEST | ID・時間帯・offset・limitの範囲違反 |
| 401 | UNAUTHORIZED | 未認証 |
| 403 | ACCESS_DENIED | 管理者権限なし |

## 備考

日時はyyyy-MM-dd'T'HH:mm:ss形式で、タイムゾーンのオフセットを含まない。
存在しないスケジュールも検索結果0件として扱う。パラメータ・添付一覧は返さない。
根拠: BatchScheduleMasterController、BatchScheduleService、BatchScheduleExecutionRepository、各DTO。
