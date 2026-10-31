# Agent登録・専用認証API

## 基本情報

| 操作 | メソッド・パス | 認証・権限 | 正常応答 |
| --- | --- | --- | --- |
| 登録状態 | GET `/api/master/device/{deviceId}/agent` | 管理者 | 200 |
| コード発行・再発行 | POST `/api/master/device/{deviceId}/agent/registration-code` | 管理者 | 200 |
| 無効化・再登録開始 | POST `/api/master/device/{deviceId}/agent/status` | 管理者 | 200 |
| nonce取得 | POST `/api/v1/agent/auth/challenge` | 初回資格情報不要、要求回数制限 | 200 |
| 初回登録・応答復旧 | POST `/api/v1/agent/register` | 登録コード＋端末鍵の保有証明 | 200 |
| 資格情報更新・応答復旧 | POST `/api/v1/agent/auth/refresh` | 更新トークン＋端末鍵の保有証明 | 200 |
| 自分の登録状態・認証確認 | GET `/api/v1/agent/me` | Agent専用Bearer | 200 |

## 概要

2026年9月23日実装。初回登録のURLはHTTPSのベースURLに `/api/v1/agent/register` を付加する。
コードをURLへ含めずJSON本文で送信する。管理者のコード発行応答はregistrationCode、agentId、expiresAtを返す。
期限・状態遷移・保存方式は[専用認証設計](../../02_SystemDesign/認証/0005_agent_authentication.md)を正とする。

## 処理概要

1. 端末マスタを保存し、管理者が登録コードを発行する。
2. Agentが鍵ペアとregistrationRequestIdを保存する。
3. challenge APIからnonceとexpiresAtを取得する。
4. nonceと要求情報に署名し、registerへ送信する。
5. Agentが返された資格情報を保存し、meで認証確認する。

## パスパラメータ

管理者APIのdeviceIdは端末マスタの数値ID。Agentはコードから紐付けられるため、初回登録でdeviceIdを指定しない。
クエリパラメータは使用しない。

## リクエストボディ

### 登録

| 項目 | 型 | 必須 | 制約 | 説明 |
| --- | --- | --- | --- | --- |
| registrationCode | string | 必須 | 最大100文字 | 発行された43文字コード |
| registrationRequestId | UUID | 必須 | 再試行中は固定 | 登録前にローカル保存 |
| publicKey | string | 必須 | 最大128文字 | Ed25519公開鍵のX.509 SubjectPublicKeyInfo DERを標準Base64化 |
| proof | string | 必須 | 最大2048文字 | EdDSAのJWS Compact Serialization |
| hostname | string | 必須 | 最大255文字 | 表示用。認証根拠にしない |
| os | string | 必須 | 最大40文字 | CLIではwindows、linux、darwin |
| agentVersion | string | 必須 | 最大40文字 | Agentのバージョン |

### 保有証明

JWS保護ヘッダーのalgはEdDSA。CLIはtyp=JWTも設定する。
署名対象はBase64URLヘッダー、ドット、Base64URLペイロードを連結した標準JWS署名入力。

| claim | 登録時 | 更新時 |
| --- | --- | --- |
| aud | momiserver-agent-register | momiserver-agent-refresh |
| nonce | challenge応答のnonce | challenge応答のnonce |
| requestId | registrationRequestIdのUUID文字列 | refreshRequestIdのUUID文字列 |
| credentialHash | 登録コードのUTF-8をSHA-256化した小文字16進64桁 | 更新トークンの同形式SHA-256 |

再試行では要求IDと鍵を維持し、nonceとproofを毎回取得・生成し直す。成功済みproof自体の再使用は拒否する。

### 更新

refreshToken（最大100文字）、refreshRequestId（UUID）、proof（最大2048文字）を必須とする。
refreshRequestIdを通信前に保存し、応答保存成功まで変更しない。

### 管理者による状態変更

statusはdisabledまたはpending。reasonは空白以外を含む最大500文字。
pendingは旧資格情報を失効させる再登録操作であり、単純な有効化ではない。

## レスポンス

| 項目 | 型 | 説明 |
| --- | --- | --- |
| agentId | UUID | Agent外部ID |
| deviceId | number | 紐付いた端末マスタID |
| generation | number | 登録世代 |
| tokenType | string | Bearer |
| accessToken / accessExpiresAt | string / ISO 8601日時 | Agent APIの短期認証情報 |
| refreshToken / refreshExpiresAt | string / ISO 8601日時 | 鍵の保有証明と併用する更新情報 |
| mqtt | object | enabled=false、status=not_configured、clientIdのみ。接続秘密情報なし |

管理者の状態APIとmeはagentId、deviceId、generation、status、mqttClientId、codeExpiresAt、registeredAt、lastSeenAt、mqttStatusを返す。
未登録端末ではagentIdがnull、status=unregistered。登録コード平文・公開鍵・トークンは返さない。

## 主なエラー

| HTTP | code | 説明 |
| --- | --- | --- |
| 400 | VALIDATION_ERROR | 入力項目・長さ・形式の不備 |
| 401 | AGENT_AUTHENTICATION_FAILED | コード・更新トークン・署名・nonceの不一致や期限切れ。詳細を区別して漏らさない |
| 401 | UNAUTHORIZED | 通常Agent APIの認証情報が未指定・期限切れ・失効済み |
| 403 | ACCESS_DENIED | 管理者操作への権限不足、管理画面ユーザーによる通常Agent API利用 |
| 404 | AGENT_EXECUTION_NOT_FOUND | 自端末・同世代の実行要求でない、または未存在 |
| 409 | DEVICE_DISABLED / AGENT_REREGISTRATION_REQUIRED | 発行対象が無効、または明示的な再登録開始が必要 |
| 409 | AGENT_RECOVERY_UNAVAILABLE | 応答復旧期限切れ、復旧データを復号できない等 |
| 429 | AGENT_RATE_LIMITED | 要求回数超過。Retry-After: 60 |
| 503 | AGENT_AUTH_NOT_CONFIGURED | サーバーの認証専用鍵・期限設定が不適切 |

## 備考

nonce・コード・資格情報を返す応答はno-store。登録・更新には管理ユーザーのJWTを送信しない。
独自のAgentヘッダーや外部APIキーは使用しない。未実装APIバージョンは拒否する。
