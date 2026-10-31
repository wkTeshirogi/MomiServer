# Agent常駐・実行照合API

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0042 |
| 分類 | Agent専用、HTTP Bearer認証必須 |
| 権限 | AgentPrincipalの端末ID・登録世代で認可。管理ユーザーJWT・外部APIキーは使用不可 |
| 正常ステータス | HTTP 200、機密応答はno-store |

## API一覧

| メソッド・パス | 入力 | 応答・用途 |
| --- | --- | --- |
| GET /api/v1/agent/mqtt/settings | なし | enabled/status。readyの場合のみbrokerUrl/clientId/username/password/commandTopic/cancelTopic |
| GET /api/v1/agent/runtime/environment | なし | shell/shell_args/working_directory。登録時OSに応じた初期値 |
| GET /api/v1/agent/runtime/sync | なし | 自分の世代の未完了execution_ids、server_time、time_zone。予定時刻到来済みを要求日時順で最大1000件 |
| GET /api/v1/agent/batch/executions/{id}/command | UUIDの実行ID | agent_id/generation/server_time/time_zone/execution。認可後に保存済み実行内容を返す |
| POST /api/v1/agent/heartbeat | mqtt_connected（boolean） | server_time。受信時刻をheartbeat_atへ保存 |
| GET /api/v1/agent/status/targets | なし | 認証Agentの端末に割り当てられたservice IDとOSサービス名 |
| POST /api/v1/agent/status/snapshot | 端末・ストレージ・サービスの実測状態 | 本文なし。認証Agentの最新状態として保存 |
| POST /api/v1/agent/batch/executions/{id}/unconfirmed | reason（500文字以内） | 本文なし。既存実行状態を維持し確認状態だけを更新 |

## 処理概要

MQTT通知は実行IDの通知であり、実行コマンドを信頼する入口にしない。command APIで端末・世代を照合する。sync APIは再接続時・定期照合に使用する。1000件を超える未完了要求がある場合のページングは今後の拡張対象。
環境設定の配布初期値はWindowsがcmd.exe /D /S /C、その他が/bin/sh -c。実行OSはWindows/Linux/macOSを対象とする。

状態収集はハートビートと分離する。Agentは監視対象を毎回バックエンドから取得し、
WindowsではService Control Manager、Linuxではsystemdからサービス状態とPIDを取得する。
端末CPU・メモリ・ストレージ・稼働時間、サービスプロセスCPU・メモリ・稼働時間を1分間隔で送信する。
温度センサーがOSから取得できない場合はCPU・GPU温度を省略する。

状態登録ではリクエスト内のAgent IDや端末IDを信頼せず、Bearer認証の`AgentPrincipal`を使用する。
サービスIDが認証Agentの端末に割り当てられていない場合はHTTP 403とし、登録世代が変わった状態は保存しない。
`collected_at`はサーバー時刻から5分以内、割合は0から100、サービス状態は`online`、`offline`、`unknown`に限定する。

## 既存APIの拡張

- complete APIにstdout、stderr（各65536文字以内）、output_truncatedを追加。Agentは各64KiBで収集を打ち切る。実行要求詳細で取得できる。同一終端結果の再送で出力を上書きしない。
- 実行要求詳細にparametersを追加。V24で要求作成時のパラメータ値をJSONB保存する。固定値とcurrent_date/current_timeを対応対象とし、コマンドには文字列展開しない。
- meおよび端末マスタのAgent状態にheartbeatAt、connectionStatus、mqttConnectedを追加。ハートビート未受信はunknown、直近3分以内はonline、それ以外offline。mqttConnectedは最終受信時の報告であり現在接続の保証ではない。
- 管理画面のmqttStatusはnot_configured / pending / ready / revocation_pending / revoked。readyは接続確認ではなくブローカー設定反映済みを意味する。
- 開始APIは本文なしの場合、キャンセル要求済み・開始期限超過を拒否する。受理後の応答消失は再起動時に未確認として照合する。
- 保守中、開始APIは同じ実行IDの未開始状態`queued`または`dispatched`をHTTP 200で返し、runningへ更新しない。新Agentはプロセスを起動せず、開始意図を解除して次周期に再照合する。通信失敗・識別子不一致とは区別する。
- complete APIはDB保存後の実行IDと終端状態を返す。Agentは同じID・結果状態の受理を確認するまで本文を保持する。HTTP 2xxだけでは受理とみなさない。
- 旧Agentは保守の未開始応答を結果不明として扱うため、保守制御の導入は新Agentを先に更新する。

## 主なエラー

| HTTP | 意味 |
| --- | --- |
| 400 | 入力・状態遷移不正 |
| 401 / 403 | 認証不可・認証主体不正 |
| 404 | 未存在または他端末・旧世代の実行 |

状態登録では、担当外サービスを403、収集日時・値域・重複キーの不正を400として扱う。

## 関連設計

- [常駐・MQTT運用](../../07_OperationsDesign/0004_agent_batch_runtime.md)
- [Agent認証](../../02_SystemDesign/認証/0005_agent_authentication.md)
- [MQTT資格情報テーブル](../../04_DatabaseDesign/Tables/t_agent_mqtt_credentials.md)
- [Agent端末状態](../../04_DatabaseDesign/Tables/t_agent_machine_status.md)
- [Agentストレージ状態](../../04_DatabaseDesign/Tables/t_agent_storage_status.md)
- [Agentサービス状態](../../04_DatabaseDesign/Tables/t_agent_service_status.md)
