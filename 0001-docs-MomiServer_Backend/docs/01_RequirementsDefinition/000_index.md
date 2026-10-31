# 要件定義

## 各フェーズ

| フェーズ | 内容 |
| --- | --- |
| Phase 1 | REST API・認証基盤・基本業務機能 (Spring Boot + React) |
| Phase 2 | IoT / MQTT・監視・通知基盤 |
| Phase 3 | Python AI・AIエージェント連携 (FastAPI / MCP) |
| Phase 4 | 履歴データ・ログ・時系列DB導入 (InfluxDB / TimescaleDB) |
| Phase 5 | Gitサーバ・CI/CD基盤 |

## 機能要件

| id | 内容 | 対応フェーズ | link |
| :-- | :-- | --- | --- |
| FR-0001 | (ユーザー管理) ユーザーの作成・更新・削除 | Phase 1 | [Link](./FR/0001.md) |
| FR-0002 | (ユーザー管理) ユーザー情報の参照 | Phase 1 | [Link](./FR/0002.md) |
| FR-0003 | (ユーザー管理) ログイン / ログアウト | Phase 1 | [Link](./FR/0003.md) |
| FR-0004 | (ユーザー管理) JWTトークン発行 | Phase 1 | [Link](./FR/0004.md) |
| FR-0005 | (ユーザー管理) セッション管理 | Phase 1 | [Link](./FR/0005.md) |
| FR-0006 | (ユーザー管理) ロールベースアクセス制御（RBAC） | Phase 1 | [Link](./FR/0006.md) |
| FR-0007 | (ユーザー管理) サービス単位のアクセス制御 | Phase 1 | [Link](./FR/0007.md) |
| FR-0008 | (ユーザー管理) 外部向けAPIキー発行 | Phase 1 | [Link](./FR/0008.md) |
| FR-0009 | (ユーザー管理) APIキーの無効化 | Phase 1 | [Link](./FR/0009.md) |
| FR-0010 | (ユーザー管理) スコープ設定（アクセス範囲制御） | Phase 1 | [Link](./FR/0010.md) |
| FR-0011 | (IoT管理サービス)IoT機器の登録・削除・更新 | Phase 2  |[Link](./FR/0011.md) |
| FR-0012 | (IoT管理サービス)デバイス一覧取得 | Phase 2  |[Link](./FR/0012.md) |
| FR-0013 | (IoT管理サービス)デバイス状態管理 | Phase 2  |[Link](./FR/0013.md) |
| FR-0014 | (MQTT通信)MQTTブローカーとの接続 | Phase 2 | [Link](./FR/0014.md) |
| FR-0015 | (MQTT通信)メッセージ受信・送信 | Phase 2 | [Link](./FR/0015.md) |
| FR-0016 | (MQTT通信)トピック管理| Phase 2 | [Link](./FR/0016.md) |
| FR-0017 | (センサーデータ管理)データ受信・保存| Phase 2, Phase 4 | [Link](./FR/0017.md) |
| FR-0018 | (センサーデータ管理)デバイス別履歴管理| Phase 4 | [Link](./FR/0018.md) |
| FR-0019 | (タスク管理)タスク作成・更新・削除 | Phase 1 | [Link](./FR/0019.md) |
| FR-0020 | (タスク管理)タスク状態管理（未着手 / 進行中 / 完了） | Phase 1 | [Link](./FR/0020.md) |
| FR-0021 | (スケジュール管理)定期実行スケジュール設定 | Phase 1 | [Link](./FR/0021.md) |
| FR-0022 | (スケジュール管理)Cronベースジョブ管理 | Phase 1 | [Link](./FR/0022.md) |
| FR-0023 | (トリガー制御)イベントトリガーによるタスク起動 | Phase 2 | [Link](./FR/0023.md) |
| FR-0024 | (トリガー制御)IoT・AI連携トリガー対応 | Phase 3 | [Link](./FR/0024.md) |
| FR-0025 | (在庫管理)在庫登録・更新・削除 | Phase 1  |[Link](./FR/0025.md) |
| FR-0026 | (在庫管理)在庫数量管理 | Phase 1 |[Link](./FR/0026.md) |
| FR-0027 | (在庫管理)在庫一覧表示 | Phase 1 |[Link](./FR/0027.md) |
| FR-0028 | (車庫管理)車両情報管理 | Phase 1 |[Link](./FR/0028.md) |
| FR-0029 | (車庫管理)駐車状態管理 | Phase 2 |[Link](./FR/0029.md) |
| FR-0030 | (車庫管理)車庫状態可視化 | Phase 2 |[Link](./FR/0030.md) |
| FR-0031 | (車庫管理)在庫検知AI連携 | Phase 3 |[Link](./FR/0031.md) |
| FR-0032 | (車庫管理)車庫検知AI連携 | Phase 3 |[Link](./FR/0032.md) |
| FR-0033 | (サービス監視)稼働状況監視 | Phase 2 |[Link](./FR/0033.md) |
| FR-0034 | (サービス監視)ダウン検知 | Phase 2 |[Link](./FR/0034.md) |
| FR-0035 | (リソース監視)CPU / メモリ / ディスク監視 | Phase 2 |[Link](./FR/0035.md) |
| FR-0036 | (ヘルスチェック)ヘルスチェックAPI提供 | Phase 1 | [Link](./FR/0036.md) |
| FR-0037 | (ヘルスチェック)定期死活監視 | Phase 2 | [Link](./FR/0037.md) |
| FR-0038 | アプリケーションログ表示 | Phase 4 | [Link](./FR/0038.md) |
| FR-0039 | IoTログ表示 | Phase 2, Phase 4 | [Link](./FR/0039.md) |
| FR-0040 | APIログ表示 | Phase 1, Phase 4 | [Link](./FR/0040.md) |
| FR-0041 | (監視設定UI)監視ルール設定 | Phase 2 | [Link](./FR/0041.md) |
| FR-0042 | (監視設定UI)通知条件設定 | Phase 2 | [Link](./FR/0042.md) |
| FR-0043 | (通知機能)Discord通知送信 | Phase 2 | [Link](./FR/0043.md) |
| FR-0044 | (通知機能)イベントベース通知 | Phase 2 | [Link](./FR/0044.md) |
| FR-0045 | (通知機能)通知チャンネル管理 | Phase 2 | [Link](./FR/0045.md) |
| FR-0046 | (通知ルール)重要度別通知制御 | Phase 2 | [Link](./FR/0046.md) |
| FR-0047 | (通知ルール)条件ベース通知（閾値など） | Phase 2 | [Link](./FR/0047.md) |
| FR-0048 | 各種予測処理（在庫・温度・負荷） | Phase 3 | [Link](./FR/0048.md) |
| FR-0049 | API経由で推論実行 | Phase 3 | [Link](./FR/0049.md) |
| FR-0050 | (モデル管理)学習モデルのバージョン管理 | Phase 3 | [Link](./FR/0050.md) |
| FR-0051 | (モデル管理)モデル切り替え | Phase 3 | [Link](./FR/0051.md) |
| FR-0052 | (AIエージェント連携)MCP / AI Agentインターフェース提供 | Phase 3 | [Link](./FR/0052.md) |
| FR-0053 | (AIエージェント連携)外部ツール呼び出し対応 | Phase 3 | [Link](./FR/0053.md) |
| FR-0054 | (Gitサーバ)リポジトリ管理 | Phase 5 | [Link](./FR/0054.md) |
| FR-0055 | (Gitサーバ)Git操作受付 | Phase 5 | [Link](./FR/0055.md) |
| FR-0056 | (Gitサーバ)Git基盤状態・監査・復旧管理 | Phase 5 | [Link](./FR/0056.md) |
| FR-0057 | (気象情報連携)気象データ取得 | Phase 2 | [Link](./FR/0057.md) |
| FR-0058 | (気象情報連携)予報情報保存 | Phase 4 | [Link](./FR/0058.md) |
| FR-0059 | (外部情報連携)Minecraft情報取得 | Phase 2 | [Link](./FR/0059.md) |
| FR-0060 | (外部情報連携)Discord bot情報取得 | Phase 2 | [Link](./FR/0060.md) |
| FR-0061 | (UI / ダッシュボードサービス)IoTデバイス一覧表示 | Phase 2 | [Link](./FR/0061.md) |
| FR-0062 | (UI / ダッシュボードサービス)在庫管理画面 | Phase 1 | [Link](./FR/0062.md) |
| FR-0063 | (UI / ダッシュボードサービス)車庫管理画面 | Phase 2 | [Link](./FR/0063.md) |
| FR-0064 | (UI / ダッシュボードサービス)監視ダッシュボード | Phase 2, Phase 4 | [Link](./FR/0064.md) |
| FR-0065 | (UI / ダッシュボードサービス)監視設定UI | Phase 2 | [Link](./FR/0065.md) |
| FR-0066 | (API Gateway)リクエストルーティング | Phase 1 | [Link](./FR/0066.md) |
| FR-0067 | (API Gateway)認証処理 | Phase 1 | [Link](./FR/0067.md) |
| FR-0068 | (API Gateway)レート制限 | Phase 1 | [Link](./FR/0068.md) |
| FR-0069 | (API Gateway)API統合管理 | Phase 1 | [Link](./FR/0069.md) |
| FR-0070 | (FR-0033,FR-0034,FR-0035)監視エージェント  使用予定言語: Go | Phase 2 | Link |




## 非機能要件

| id | 内容 | 対応フェーズ | link |
| :-- | :-- | --- | --- |
| NFR-0001 | (ユーザー管理)パスワードはハッシュ化して保存（bcrypt等） | Phase 1 | [Link](./NFR/0001.md) |
| NFR-0002 | (ユーザー管理)JWTの有効期限管理 | Phase 1 | [Link](./NFR/0002.md) |
| NFR-0003 | (ユーザー管理)APIキーのレート制限対応 | Phase 1 | [Link](./NFR/0003.md) |
| NFR-0004 | (ユーザー管理)不正アクセスログの記録 | Phase 1 | [Link](./NFR/0004.md) |
| NFR-0005 | (MQTT通信)MQTT切断時の再接続処理 | Phase 2 | [Link](./NFR/0005.md) |
| NFR-0006 | (MQTT通信)オフラインデータの再送処理 | Phase 2 | [Link](./NFR/0006.md) |
| NFR-0007 | (MQTT通信)デバイスIDの一意性保証 | Phase 2 | [Link](./NFR/0007.md) |
| NFR-0008 | (MQTT通信)高頻度データ受信への耐性 | Phase 2, Phase 4 | [Link](./NFR/0008.md) |
| NFR-0009 | (タスク管理)Cronジョブの冪等性保証 | Phase 1 | [Link](./NFR/0009.md) |
| NFR-0011 | (タスク管理)実行失敗時のリトライ機構 | Phase 1 | [Link](./NFR/0011.md) |
| NFR-0012 | (タスク管理)同時実行制御（排他制御） | Phase 1 | [Link](./NFR/0012.md) |
| NFR-0013 | (AI連携)データ整合性保証（在庫数の正確性） | Phase 1, Phase 3 | [Link](./NFR/0013.md) |
| NFR-0014 | (AI連携)同時更新時の競合制御 | Phase 1 | [Link](./NFR/0014.md) |
| NFR-0015 | (AI連携)履歴データの保持 | Phase 4 | [Link](./NFR/0015.md) |
| NFR-0016 | (ログ管理)ログの高速検索性 | Phase 4 | [Link](./NFR/0016.md) |
| NFR-0017 | (ログ管理)ログ保持期間の管理 | Phase 4 | [Link](./NFR/0017.md) |
| NFR-0018 | (ログ管理)高頻度メトリクス収集対応 | Phase 2, Phase 4 | [Link](./NFR/0018.md) |
| NFR-0019 | (ログ管理)障害時の自己復旧不可を前提とした設計 | Phase 2 | [Link](./NFR/0019.md) |
| NFR-0020 | (通知ルール)通知重複防止（冪等性） | Phase 2 | [Link](./NFR/0020.md) |
| NFR-0021 | (通知ルール)配信失敗時のリトライ処理 | Phase 2 | [Link](./NFR/0021.md) |
| NFR-0022 | (通知ルール)非同期処理（キュー利用） | Phase 2 | [Link](./NFR/0022.md) |
| NFR-0023 | (AIエージェント連携)推論レスポンス時間制御 | Phase 3 | [Link](./NFR/0023.md) |
| NFR-0024 | (AIエージェント連携)モデル再学習機構 | Phase 3 | [Link](./NFR/0024.md) |
| NFR-0025 | (AIエージェント連携)GPU/CPUリソース分離 | Phase 3 | [Link](./NFR/0025.md) |
| NFR-0026 | (AIエージェント連携)モデルバージョン互換性維持 | Phase 3 | [Link](./NFR/0026.md) |
| NFR-0027 | (イベント基盤)高スループット対応 | Phase 2, Phase 4 | [Link](./NFR/0027.md) |
| NFR-0028 | (イベント基盤)メッセージロスト防止 | Phase 2 | [Link](./NFR/0028.md) |
| NFR-0029 | (イベント基盤)再送制御 | Phase 2 | [Link](./NFR/0029.md) |

## 未振分け

| id | 内容 | 対応フェーズ | link |
| :-- | :-- | --- | --- |
| 0001 | (FR-0033,FR-0034,FR-0035)監視エージェント  使用予定言語: Go | Phase 2 | Link |

## 実装追補

| id | 内容 | 対応フェーズ | link |
| :-- | :-- | --- | --- |
| FR-0071 | バッチ定義管理 | Phase 1 | [Link](./FR/0071.md) |
| FR-0072 | バッチスケジュール管理 | Phase 1 | [Link](./FR/0072.md) |
| FR-0073 | バッチ実行ファイルアップロード | Phase 1 | [Link](./FR/0073.md) |
| FR-0074 | バッチ実行ファイルダウンロード | Phase 1 | [Link](./FR/0074.md) |
| FR-0075 | バッチ定義配布ファイル管理 | Phase 1 | [Link](./FR/0075.md) |
| FR-0076 | 車検期限管理 | Phase 1 | [Link](./FR/0076.md) |
| NFR-0030 | 認証セッション管理 | Phase 1 | [Link](./NFR/0030.md) |
| BATCH-REQ-0001 | バッチ管理 要件定義 | Phase 1 | [Link](./Batch/0001_batch_management_requirements.md) |
