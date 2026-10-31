# セキュリティエラー応答設計

## 未認証

| 項目 | 内容 |
| --- | --- |
| HTTPステータス | 401 |
| code | UNAUTHORIZED |
| message | 認証が必要です。 |

## 権限不足

| 項目 | 内容 |
| --- | --- |
| HTTPステータス | 403 |
| code | ACCESS_DENIED |
| message | この操作を行う権限がありません。 |

## トークン不正

| 項目 | 内容 |
| --- | --- |
| HTTPステータス | 401 |
| code | INVALID_TOKEN |
| message | トークンが無効または期限切れです。 |
