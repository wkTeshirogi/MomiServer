# Gatewayテスト設計

## 対象

| No | 対象 | 観点 |
| --- | --- | --- |
| 1 | FR-0066 リクエストルーティング | 現行Controllerへの振り分け・不明経路の拒否 |
| 2 | FR-0067 認証処理 | Web JWT・Agent専用Bearer・IoT APIキーの境界 |
| 3 | FR-0068 レート制限 | IoTのキー・IP単位の制限と対象外経路 |
| 4 | FR-0069 API統合管理 | DB公開状態・OpenAPI認証表示・公開設定 |

## 確認項目

- 本書は `090_test_design.md` テンプレートに基づく。
- Webの利用可否はロール単位とし、ユーザー個人単位の許可・レート制限は対象外とする。
- 実PostgreSQLへFlywayマイグレーションを適用し、実際のSecurityFilterChainとRepositoryを使用する。
- Webユーザーはテスト用データを登録してログインAPIからJWTを取得する。
- Agentは検証用端末・登録世代・有効セッションをDBへ登録し、実認証フィルターで照合する。
- IoT業務Controllerは未実装のため、テスト専用Controllerで入口制御とOpenAPI表示を検証する。
- テスト内のDB変更は原則トランザクション終了時にロールバックする。

## テストケース

| No | テスト内容 | 前提条件 | 入力 | 期待結果 |
| --- | --- | --- | --- | --- |
| 1 | Web認証 | USER・OPERATOR・ADMINでログイン | 各JWTで通知APIを取得 | 200 |
| 2 | WebからAgentへの境界 | 有効なWeb JWT | Agent APIを取得 | 403 |
| 3 | WebからIoTへの境界 | IoTキー認証有効 | JWTのみでIoT APIを取得 | 401 |
| 4 | Agent認証 | 有効な端末・登録世代・セッション | Agent BearerでAgent APIを取得 | 200 |
| 5 | AgentからWeb・IoTへの境界 | 有効なAgent Bearer | Web・IoT APIを取得 | 401 |
| 6 | IoT認証 | 登録済みIoTキー | テスト専用IoT APIを取得 | 200 |
| 7 | IoTキーの流用拒否 | 登録済みIoTキー | Web・Agent API・非公開OpenAPIを取得 | 401 |
| 8 | レート制限 | IoT上限2回の設定 | 同じキーで3回要求 | 2回許可、3回目429・残数0・Retry-After |
| 9 | 制限対象の境界 | IoTキーの制限枠を消費済み | Web JWT・Agent Bearerで各APIを取得 | 200、Gateway制限ヘッダーなし |
| 10 | DB未登録版 | Agent・IoTのv1行を削除 | 対象APIを取得 | 404・API_VERSION_NOT_FOUND |
| 11 | 未実装版 | v2をDBへactiveで登録 | v2経路を取得 | 404・API_VERSION_NOT_FOUND |
| 12 | 非推奨版 | v1をdeprecated、日時設定 | 認証あり・なしで取得 | 認証要件を維持し、正確なDeprecation日時を付与 |
| 13 | 廃止版 | v1をretiredに変更 | 認証なしで取得 | 410・API_VERSION_RETIRED |
| 14 | DB参照不可 | トランザクション内で公開状態テーブルを一時改名 | 対象APIを取得 | 503・API_VERSION_UNAVAILABLE、処理を続行しない |
| 15 | 不明なController | ADMINのJWT | 未定義Web経路を取得 | 404 |
| 16 | OpenAPI非公開 | 公開設定false | 匿名・USER・OPERATOR・ADMINで取得 | 匿名401、USER・OPERATOR403、ADMIN200 |
| 17 | OpenAPI公開 | 公開設定true | 匿名でJSON・YAML・Swagger UI・設定を取得 | 200、Swagger入口はリダイレクト |
| 18 | 認証スキーム表示 | 実Controllerとテスト用IoT Controller | OpenAPI JSONを取得 | Web JWT、Agent Bearer、IoTキー、認証不要の操作が一致 |

## 正常系

| No | 観点 | 期待結果 |
| --- | --- | --- |
| 1 | activeとdeprecated | 本来の認証・認可を維持して処理する |
| 2 | 公開設定 | JSON・YAML・Swagger UIに同一の閲覧条件を適用する |
| 3 | ロール | 実ログインのJWTでUSER・OPERATOR・ADMINを判定する |

## 異常系

| No | 条件 | 期待結果 |
| --- | --- | --- |
| 1 | 認証情報の流用・不正トークン | 401または403で拒否する |
| 2 | DB未登録・未実装・廃止 | 後続認証より前に404または410で拒否する |
| 3 | 公開状態をDBから取得できない | 503で拒否し、公開判定を迂回しない |
| 4 | IoTの上限超過 | Controllerへ到達せず429を返す |

## 確認結果

| 確認項目 | 結果 | 補足 |
| --- | --- | --- |
| Gateway・OpenAPI統合テスト | 成功 | Gateway20件、公開・非公開OpenAPI各1件。Java 25・専用PostgreSQL 17で実行 |
| 全JUnit | 成功 | 199件中198件成功、失敗0件、MQTT連携1件スキップ |
| Flyway・jOOQ・コンパイル | 成功 | 空DBへ26マイグレーションを適用して確認 |
| 静的解析 | 追加テストは違反0件 | Checkstyle全体は既存コード・生成コードの違反あり。ignoreFailures設定でビルドは成功 |
| Markdown・MkDocs | 成功 | 変更文書のlintとstrictビルドを確認。全体lintには対象外の既存違反12件あり |

## 備考

- GatewayIntegrationTest、OpenApiAccessIntegrationTest、OpenApiPublicAccessIntegrationTestが入口制御の統合確認を担う。
- AgentAuthenticationIntegrationTestは登録・署名・更新・失効・端末と世代の境界を補完する。
- GatewayRateLimiterTest、GatewayApiKeyAuthFilterTest、ApiVersionStatusFilterTest、SpringDocConfigTestで設定分岐を補完する。
- CIの既存JUnitジョブで実行する。マイクロサービス転送、管理画面、クライアント別バージョン許可は対象外とする。
- 実行記録は[2026/10/03の作業メモ](../90_MeetingMinutesAndNotes/weekly_document_progress_20261003.md)を参照する。
