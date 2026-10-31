# セキュリティ・エラーテスト

## 認証・認可

| No | 条件 | 期待結果 |
| --- | --- | --- |
| 1 | 認証なしでマスタAPIへアクセス | 401 |
| 2 | 管理者権限なしでマスタAPIへアクセス | 403 |
| 3 | 不正なBearerトークンでアクセス | 401 |
| 4 | 公開APIへ認証なしでアクセス | 認証要求なし |

## エラー応答

| No | 条件 | code |
| --- | --- | --- |
| 1 | 入力値不正 | VALIDATION_ERROR |
| 2 | パラメータ型不正 | INVALID_PARAMETER |
| 3 | JSON不正 | MALFORMED_REQUEST |
| 4 | リソースなし | RESOURCE_NOT_FOUND |
| 5 | DB例外 | DATABASE_ERROR |
