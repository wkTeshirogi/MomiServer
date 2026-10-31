# API Gateway 基本設計

## 位置付け

現行のGatewayは独立したプロキシではなく、Spring Bootアプリケーション内のFilterとSecurityFilterChainによる入口制御である。URLとHTTPメソッドからアプリケーション内のControllerへ振り分ける。マイクロサービス間の転送機能は提供していない。

外部連携APIはAgent・IoT・専用外部APIの3経路に分離する。Phase 1の実装対象は`v1`のみで、
DBへ別バージョンを登録しただけでは公開されない。

## 現行の認証境界

| 利用元 | 現行の認証・認可 | Gatewayの制限 |
| --- | --- | --- |
| Web画面・管理API | ユーザーJWTと各APIの権限判定 | Gateway APIキー・レート制限の対象外 |
| Agent登録・challenge・トークン更新 | 専用の登録・署名検証手順。接続元IPの認証要求回数をDBで制限 | Gateway APIキー・レート制限の対象外 |
| その他の `/api/v1/agent/**` | Agent専用Bearer、AgentPrincipal、対象端末・登録世代の認可 | Gateway APIキー・レート制限の対象外 |
| `/api/v*/iot/**` | Gateway有効かつAPIキー認証有効時はIoT用 `X-API-Key` | Gateway有効かつレート制限有効時に適用 |
| `/api/v1/external/**` | DB管理の専用APIキーとスコープ。Web・Agent権限は継承しない | Gateway設定と独立して認証・DB共有上限を必須適用 |
| 未実装のAgent・IoTバージョン | `ApiVersionStatusFilter` で404 | APIキー・Bearerの有無によらず開放しない |

Agentの詳細は[Agent専用認証設計](../認証/0005_agent_authentication.md)を参照する。
管理ユーザーJWTとIoT用APIキーはAgent認証の代わりにならない。
`GatewayApiKeyAuthFilter` はAgent経路を後続へ委譲し、Agent用APIキー検証もGatewayレート制限も行わない。

Web API・画面の利用可否は既存のロール単位で管理し、ユーザー個人単位の許可設定は設けない。
画面やAPIを追加・変更する際は、利用可能ロールとバックエンド・フロントエンドの認可、対応する回帰テストを更新する。
通知の宛先・閲覧範囲とAgentの端末・登録世代境界は引き続き個別の業務条件として判定する。

## IoT向けGateway制御

Gateway有効時、IoT経路では次の順序で処理する。

1. APIキー認証が有効なら、設定済みIoTキーと `X-API-Key` を照合する。不一致・未指定はHTTP 401とする。
2. レート制限が有効なら、APIキー認証有効時はキー単位、無効時は送信元IP単位で1分の固定ウィンドウを消費する。超過時はHTTP 429とする。
3. 後続のSecurityFilterChainとControllerへ渡す。Gatewayが無効なら上記のキー検証・レート制限は行わない。

制限状態はアプリケーション内メモリに保持するため、複数インスタンス間では共有されない。
制限判定時には `X-RateLimit-Limit` と `X-RateLimit-Remaining` を付与し、制限超過時には `Retry-After` も付与する。
Gatewayのレート制限はJWTユーザーやAgent APIには適用しない。

## 設定

| 環境変数 | 現行の用途 |
| --- | --- |
| `GATEWAY_ENABLED` | IoT向けGateway制御の有効化 |
| `GATEWAY_API_KEY_ENABLED` | IoT APIキー検証の有効化 |
| `GATEWAY_API_KEY_HEADER_NAME` | キーを受け取るヘッダー名。既定は `X-API-Key` |
| `GATEWAY_IOT_API_KEYS` | IoT向け許可キー |
| `GATEWAY_RATE_LIMIT_ENABLED` | IoT向けレート制限の有効化 |
| `GATEWAY_RATE_LIMIT_PER_MINUTE` | IoT向け分間上限 |
| `GATEWAY_OPENAPI_PUBLIC_ENABLED` | `true` は公開、`false` はADMIN限定。`/v3/api-docs` とSwagger UIに適用 |
| `GATEWAY_DEFAULT_API_VERSION` | 設定上の既定バージョン。DB上の状態判定や経路切替には使用しない |

旧 `GATEWAY_AGENT_API_KEYS` は現行のAgent認証に使用せず、設定項目から除外した。Agentの認証情報は専用の登録・更新手順で管理する。
`GATEWAY_ENABLED=false` または `GATEWAY_API_KEY_ENABLED=false` では、IoT経路のGateway APIキー検証を行わない。IoT経路を公開する環境では、この無効化設定を運用上の認証不要状態として扱う必要がある。

## APIバージョンと公開状態

`ApiVersionStatusFilter`はAgent・IoT・専用外部APIの経路に対してGateway設定とは独立して動作し、
各認証より前に`m_api_versions`を要求ごとに参照して、以下の状態を判定する。

| 状態 | 応答 |
| --- | --- |
| `active` | 認証・Controllerへ続行 |
| `deprecated` | `Deprecation: @<Unix秒>` を付与して続行。`deprecated_dt` が未設定なら `updated_dt` を使用 |
| `retired` | HTTP 410、`API_VERSION_RETIRED` |
| DBに該当行なし・`v1` 以外 | HTTP 404、`API_VERSION_NOT_FOUND` |
| DB参照不可・不正な状態値 | HTTP 503、`API_VERSION_UNAVAILABLE` |

日時は既存のタイムゾーン設定に合わせてAsia/Tokyoのローカル日時からUnix秒へ変換する。
ヘッダー形式は[RFC 9745](https://www.rfc-editor.org/rfc/rfc9745.html)に従う。
バージョン変更はマイグレーション等で行い、管理画面・編集APIは設けない。
`m_external_clients` は現行Agent認証・IoTキー検証に接続しておらず、クライアント別バージョン許可は将来対応とする。

OpenAPIはControllerの実装から生成する。Web JWT、Agent Bearer、IoTキー、専用外部キー、認証不要を区別する。
IoTキー認証が無効な設定では、IoT操作も認証不要として表示する。
`GATEWAY_OPENAPI_PUBLIC_ENABLED=false` では、OpenAPI JSONとSwagger UIの閲覧にADMIN権限が必要となる。

## 旧仕様・残対応の分類

| 項目 | 状態 | 扱い |
| --- | --- | --- |
| Agent向けGateway APIキー | 廃止した旧構想 | Agent専用Bearerに置き換え済み。キー設定でAgentアクセスを許可しない |
| Agent向けGatewayメモリ内レート制限 | 廃止した旧構想 | 登録・challenge・更新の要求回数は専用のDB管理を使用する |
| マイクロサービスへのプロキシ転送 | 未実装 | 現行構成はアプリケーション内Controllerへの振り分け |
| ユーザー個人単位のアクセス許可・レート制限 | 対象外 | Webの利用可否はロール単位で管理する |
| エンドポイント・ロール別のGatewayレート上限 | Phase 1対象外 | 必要性が生じた時点で[FR-0068](../../01_RequirementsDefinition/FR/0068.md)を更新する |
| DBによるAPIバージョン状態の実行時判定 | Phase 1実装 | `m_api_versions` の状態を判定。クライアント別許可は将来対応 |
| API-0031～0034の旧ファイル転送経路 | 未実装の設計案 | 現行API一覧から分離。再設計時はAgent専用認証と対象端末・登録世代の認可を前提にする |

ファイル取得など実装済みの経路は[API一覧](../../05_API_Design/000_index.md)を参照する。旧設計案のパスは、Agent認証方式を読み替えただけで利用できるものではない。

## 関連ドキュメント

- [外部APIキー・在庫連携設計](./0002_external_api_keys.md)

- [FR-0066 リクエストルーティング](../../01_RequirementsDefinition/FR/0066.md)
- [FR-0067 認証処理](../../01_RequirementsDefinition/FR/0067.md)
- [FR-0068 レート制限](../../01_RequirementsDefinition/FR/0068.md)
- [FR-0069 API統合管理](../../01_RequirementsDefinition/FR/0069.md)
- [不正アクセス監査イベント設計](../../11_SecurityDesign/0006_security_event_audit.md)
- [Gatewayテスト設計](../../08_TestDesign/0005_gateway_test.md)
