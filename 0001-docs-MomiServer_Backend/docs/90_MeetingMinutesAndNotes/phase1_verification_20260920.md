# Phase 1 優先作業1〜3の検証 2026/09/20

## サマリ

[9月月次まとめ](./monthly_progress_202609.md)に集約した9月18日の進捗を受け、
起動・通知設定と認証境界の整合、既存テスト・ビルド、認証・通知の結合確認を実施した。
バックエンドは取得時のorigin/main `22112bc`、フロントエンドは `19d0d33` を基準に `phase1-verification` ブランチで修正した。
実運用DB・既存zunda-dbには変更を加えず、専用PostgreSQL 17コンテナを使用した。外部通知は送信していない。

## 修正内容

| 対象 | 確認した問題 | 対応 |
| --- | --- | --- |
| Discord設定 | ソース内に固定Webhookがあり、通知が既定で有効 | 固定値を除去し、通知の既定値をfalseへ変更 |
| 起動時Job | 自動実行しないコメントとtrueが矛盾 | BATCH_JOB_ENABLEDの既定値をfalseへ変更 |
| Agent認証 | APIキー方針と実装が不一致。一般ユーザーJWTと端末認証の境界も未完成 | GatewayのAgent APIキー検証を除外。専用認証・端末認可が整うまでAgent APIは拒否する暫定措置 |
| ユーザー設定API | 任意ユーザーのパスワード・権限を変更するAPIが公開対象 | /api/auth/settingsをURL認可とAdminOnlyで管理者限定にする |
| 通知一覧 | 通知が存在するとTimestampからLocalDateTimeへのキャストで500 | jOOQの型変換を明示。未読・通常一覧の実DBテストで確認 |
| フロントエンドAPI URL | VITE_API_BASE_URLを指定すると手書きAPIの/apiが欠落 | 設定を生成クライアントと同じサーバールートとして扱い、/apiを付加 |
| トークン更新URL | 手書きクライアントに旧/v1/authパスが残存 | 現行の/api/auth/login・refreshへ統一 |
| Checkstyle設定 | XMLコメント内の連続ハイフンで設定を読み込めない | コメントのみ修正。検査ルールは変更しない |

起動時のSpring Batch Job定義はソース内に確認されなかった。一方、BatchExecutionSchedulerはApplicationReadyEventで当日・翌日の実行要求を展開する。
BATCH_JOB_ENABLED=falseはこの展開や定期再送を停止する設定ではない。

## 検証環境・方法

- WindowsのJava25/Gradleはloopback接続エラーで起動できず、Java25 Linuxコンテナへ切り替えた。
- Podmanのbridge公開はnftablesエラーとなったため、専用DBをVM内の127.0.0.1:55432で起動した。
- Flywayの21マイグレーション、generateJooq、test、bootJarの順に検証した。
- 画面確認はViteとPlaywright CLIを使用。APIはWSL内部IPの専用ポート18080へ接続し、CORSは検証画面のオリジンに限定した。
- Phase1IntegrationTestは実際のSecurityFilterChain・Controller・Service・Repository・PostgreSQLを使用し、テストユーザーをトランザクション内で作成・ロールバックする。

## 確認結果

| 確認項目 | 結果 | 範囲 |
| --- | --- | --- |
| マイグレーション・jOOQ生成 | 成功 | 専用DBへの21件適用 |
| バックエンドテスト | 成功 | 最終122件、失敗0・スキップ0 |
| バックエンドbootJar | 成功 | Java25 Linux環境 |
| フロントエンドbuild | 成功 | 修正後のTypeScript/Viteビルド。500KB超のchunk警告あり |
| フロントエンド全体lint | 失敗 | 修正前から44エラー・3警告。React Hooks、Fast Refresh、型・import等。無関係な一括修正はしない |
| フロントエンド変更ファイルlint | 成功 | src/api/client.ts、src/api/authApi.ts |
| Checkstyle | 実行可能・違反あり | XML設定修正後に実行。mainは6998件（生成jOOQを含む）。既存ignoreFailures=trueのため終了コード0でも規約適合とは扱わない。追加テストの指摘は修正済み |
| ログイン・更新・ログアウト | 成功 | 誤パスワード拒否、JWT発行、refreshローテーション、旧refresh拒否、logout後のaccess/refresh拒否を実DBで確認 |
| 通知のユーザー分離 | 成功 | 別ユーザーは一覧に表示されず、read/dismiss/delivered更新は404 |
| 通知状態の保存 | 成功 | 未読一覧・件数、既読更新、DB保存、非表示を実DBで確認 |
| 管理者認可 | 成功 | 共通バッチ設定とユーザー・端末・サービス・品目・バッチ・スケジュールの一覧。一般ユーザー403、管理者200 |
| ユーザー設定認可 | 成功 | 未認証401、一般ユーザー403、管理者の権限更新成功 |
| Agent境界 | 成功 | APIキーのみ401、管理者JWTでも403。IoTキー認証の既存テストは維持 |
| ブラウザ結合 | 成功 | ログイン→通知表示→詳細表示・既読化→未読バッジ消去→ログアウト |
| ブラウザの自動refresh | 成功 | 検証用accessを無効値に置換して401を発生させ、実APIでrefresh後に元の通知取得が200。access置換とrefreshローテーションを確認 |

ローカルテストの成功であり、リモートCI・本番環境の成功を示すものではない。
ブラウザでアクセストークンの自然失効まで待つ長時間試験は実施していない。トークン更新のサーバ側経路は実DB結合テストで確認した。
各マスタの全更新操作・全ロールの総当たりは今回の結合範囲外。バッチ定義別権限・在庫操作権限は既存テストと注釈を併せて照合した。

## 残課題・次の作業

1. Agent専用認証、端末との紐付け、MQTT認可を実装し、Agent APIの暫定拒否を解除する。現在は開始・完了通知やファイル取得も利用できない。
2. 旧Webhookが有効なら運用者がDiscordで失効・再発行する。ソースからの除去だけではGit履歴の値は無効にならない。保存済み通知共通設定も別途確認する。
3. フロントエンド全体lintの既存問題を別作業で整理する。
   Checkstyleも生成コードの扱いと既存違反を整理する。実行成功と違反0を区別する。
4. サービス単位の権限を、一覧以外の操作とOPERATOR・BAN_USERも含めた全操作表に展開する。

## 成果物

- backend: application.yaml、SecurityConfig、GatewayApiKeyAuthFilter、AuthController、NotificationRepository、Checkstyle設定、Gatewayフィルタテスト、Phase1IntegrationTest。
- frontend: src/api/client.ts、src/api/authApi.ts。
- docs: 本メモ、起動・停止設計、設定管理設計、認可設計、メモ索引。
- バックエンドの実行結果は build/test-results/test と build/reports/tests/test、作業ログは phase1-*.log に保存。ログはGitへ追加しない。
- フロントエンドのブラウザ記録は .playwright-cli 配下。トークン等を含み得る記録はGitへ追加しない。
- 既存の未コミット文書とoutput/siteを保持し、commit・pushは行わない。
