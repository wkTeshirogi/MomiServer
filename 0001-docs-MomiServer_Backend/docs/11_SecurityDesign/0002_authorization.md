# 認可設計

## 公開API

| No | パス | 用途 |
| --- | --- | --- |
| 1 | `/api/auth/login`、`/api/auth/refresh`、`/api/auth/logout` | 認証API |
| 2 | `/v3/api-docs`、`/v3/api-docs.yaml`、`/v3/api-docs/**` | OpenAPI。公開設定がtrueの場合のみ匿名利用可 |
| 3 | `/swagger-ui`、`/swagger-ui/**` | Swagger UI。公開設定がtrueの場合のみ匿名利用可 |
| 4 | `/swagger-ui.html` | Swagger UI。公開設定がtrueの場合のみ匿名利用可 |
| 5 | `/error` | エラー |

## 認証必須API

公開API以外のAPIは認証を必須とする。

`GATEWAY_OPENAPI_PUBLIC_ENABLED=false` の場合、OpenAPIとSwagger UIは `ADMIN` のみ利用可能とする。
Web API・画面の利用可否はロール単位で管理し、ユーザー個人単位の許可設定は設けない。
画面・APIの追加や操作変更時は、対象ロール、バックエンドの認可、フロントエンドの経路制御、回帰テストを更新する。
通知の宛先・閲覧範囲とAgentの端末・登録世代に基づく認可は維持する。

将来の内蔵Gitはこの一般Web画面の制御とは分離し、Git領域だけgroup・repo単位の個別アクセス許可を設ける。
システムADMIN、Git PAT、継承権限、main保護の基本設計は
[Git認証・認可](../02_SystemDesign/Git基盤/0002_authentication_authorization.md)を正本とする。
2026/10/29時点では設計のみであり、既存SecurityConfig・APIの実装を変更したものではない。

`/api/auth/settings` は任意ユーザーのパスワード・権限を更新するため管理者限定とする。
2026/09/20にURL制御と `@AdminOnly` で保護した。個人設定専用APIではない。

## Webユーザーのロール階層

| ロール | 権限順位 | 上位ロールが権限を包含する対象 |
| --- | --- | --- |
| `ADMIN` | 999 | `OPERATOR`、`USER` の操作 |
| `OPERATOR` | 100 | `USER` の操作 |
| `USER` | 10 | 一般利用操作 |
| `BAN_USER` | 0 | なし。常に拒否 |

未知または未設定のロールは拒否する。`BAN_USER` はログインと更新を拒否し、
ロール変更時に有効セッションを失効する。既存アクセストークンはリクエストごとに
現在のDBロールを確認し、認証情報をSecurityContextへ設定せず401として扱う。

## 画面・操作権限表

| 対象 | URL・操作 | ADMIN | OPERATOR | USER | 備考 |
| --- | --- | --- | --- | --- | --- |
| トップ | `/index` | 可 | 可 | 可 | 認証済みユーザーの初期画面 |
| マシンステータス | `/machine/status`、`/machine/status/:serverId` | 可 | 可 | 不可 | 設定ボタンはADMINのみ |
| サービスステータス | `/service/status`、`/service/status/:serverId` | 可 | 可 | 可 | 設定ボタンはADMINのみ |
| アラート | `/alerts` | 可 | 可 | 可 | 本人宛てかつロールで閲覧可能な通知のみ |
| APIログ | `/logs/api` | 可 | 可 | 不可 | 内部API構成、IP、操作主体を含むため運用ロール以上に限定 |
| 在庫管理 | `/stock`、`/stock/:itemId`、`/Stock/Detail` | 可 | 可 | 可 | 一覧、詳細、登録、更新、実在庫操作、履歴を含む |
| マスタ管理 | `/master/**` | 可 | 不可 | 不可 | 共通設定、ユーザー、サービス、マシン、品目、バッチ、通知ルートを含む |
| バッチ手動実行 | 定義の実行操作 | 条件付き可 | 条件付き可 | 条件付き可 | `required_role` 以上。ADMINは上書き可 |
| バッチ実行詳細・ログ | 実行IDに紐づく参照 | 条件付き可 | 条件付き可 | 条件付き可 | 実行要求の定義に設定された `required_role` 以上 |
| バッチ取消 | 実行取消操作 | 可 | 不可 | 不可 | 実行要求者による例外は設けない |

マシン・サービスステータスはHTTP初期取得とWebSocket配信を実装済みとする。
マシンは `ADMIN`・`OPERATOR`、サービスは全通常ロールで保護する。
WebSocket接続には、JWT認証済みHTTP APIで発行する30秒有効・一回限りのチャネル別チケットを使用する。
JWT自体はWebSocket URLへ含めない。詳細は
[マシン・サービス状態取得／リアルタイム配信](../05_API_Design/API/0045_status_streaming.md)を参照する。

## 外部システム・サイドバー権限表

| 導線 | ADMIN | OPERATOR | USER | 備考 |
| --- | --- | --- | --- | --- |
| GitLab | 表示 | 表示 | 非表示 | 外部システム内の認証は対象外 |
| Grafana | 表示 | 表示 | 非表示 | 外部システム内の認証は対象外 |
| Prometheus | 表示 | 表示 | 非表示 | 外部システム内の認証は対象外 |
| Swagger UI | 表示 | 非表示 | 非表示 | 直接アクセス可否はOpenAPI公開設定に従う |
| ダッシュボード・ドキュメント・APIリファレンス | 非表示 | 非表示 | 非表示 | 対応画面が未実装のため導線を表示しない |

## Agent専用認証

2026年9月23日、v1のAgent APIを専用Bearerと端末・登録世代の認可で保護した。
POST register / auth/challenge / auth/refreshは初回Bearer不要だが、コード・端末鍵・nonceまたは更新資格情報を検証する。
管理ユーザーJWTや外部APIキーでは通常Agent APIを利用できない。Agentトークンは管理APIで認証しない。
未実装バージョンは拒否する。IoT向けGatewayのAPIキー検証は維持する。
詳細は[Agent専用認証設計](../02_SystemDesign/認証/0005_agent_authentication.md)を参照する。

## 管理者権限

マスタAPIは管理者権限を要求する。

| API分類 | 必要権限 |
| --- | --- |
| ユーザーマスタ | 管理者 |
| デバイスマスタ | 管理者 |
| サービスマスタ | 管理者 |

管理者権限の判定は、Controllerへ `@PreAuthorize` を直接記載せず、専用アノテーション `@AdminOnly` へ集約する。

## バッチ実行権限

手動バッチ実行APIは、専用アノテーション `@CanExecuteBatch` を利用してバッチ定義単位に実行可否を判定する。

| 判定対象 | 内容 |
| --- | --- |
| 認証状態 | 未認証ユーザーは拒否する。 |
| 管理者 | `ADMIN` はすべてのバッチ定義を実行可能とする。 |
| バッチ定義 | 対象バッチ定義の `required_role` を取得する。 |
| ロール階層 | ユーザーのロールが `required_role` 以上の場合に許可する。 |
| 不正な設定 | `required_role` が未設定または未知の場合は拒否する。 |

手動実行と実行詳細・ログ参照は同じ階層判定を利用する。取消は `ADMIN` のみに限定する。

実装方法の詳細は [認可アノテーション実装設計](./0005_authorization_annotation.md) を参照する。

## フロントエンド制御

### 通知の閲覧範囲

一覧・未読一覧・未読件数は本人宛ての通知を対象とし、同じロール別表示条件を適用する。

| 対象 | ADMIN | OPERATOR | USER |
| --- | --- | --- | --- |
| 通常通知 | 閲覧対象 | 閲覧対象 | 閲覧対象 |
| リンクが/machine/statusで始まる通知 | 閲覧対象 | 閲覧対象 | 除外 |
| リンクが/masterで始まる通知 | 閲覧対象 | 除外 | 除外 |
| source_typeがbatch_execution | 閲覧対象 | 除外 | 除外 |

リンクは小文字化して前方一致で判定する。リンク先APIの認可は別途必要となる。
既読・配信済み・非表示更新は本人宛て通知IDに限定し、更新対象なしは404を返す。
表示の絞り込みと状態更新の宛先確認は別処理である。
根拠はNotificationService・NotificationRepository。
検証観点は[通知テスト設計](../08_TestDesign/0006_notification_test.md)を参照する。

### 画面の制御

フロントエンドでは、同じロールモデルを利用して管理画面のRoute、サイドバー、操作部品の表示を制御する。

フロントエンド制御は利用者体験を整えるための補助であり、セキュリティの正本はバックエンドの認可判定とする。

詳細は [フロントエンド権限制御設計](../06_UserInterfaceDesign/3005_frontend_authorization.md) を参照する。

## 不正アクセス監査

認証失敗、認可拒否、APIキー不正、レート制限超過などのセキュリティイベントは、通常ログとは分けて専用の監査イベントとして記録する。

監査イベントの記録先、検索、保持期間、Discord通知、フロントエンド通知の詳細は [不正アクセス監査イベント設計](./0006_security_event_audit.md) を参照する。
