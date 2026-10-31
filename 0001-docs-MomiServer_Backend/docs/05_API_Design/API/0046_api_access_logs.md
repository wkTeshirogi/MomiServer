# APIアクセスログ取得

## 基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0046 |
| 分類 | ログ管理 |
| メソッド | GET |
| パス | `/api/logs/access`、`/api/logs/access/{requestId}` |
| 認証 | 必須 |
| 権限 | ADMIN・OPERATOR |
| 正常ステータス | HTTP 200 |

## 概要

保存済みのAPIアクセスログを条件検索し、一覧またはリクエスト単位の詳細を取得する。

## 処理概要

1. JWT認証とロールを検証する。
2. 一覧取得では検索条件を検証し、発生日時の降順で取得する。
3. 詳細取得ではリクエストIDに一致する1件を取得する。
4. APIへのアクセス自体も、レスポンス完了後にAPIアクセスログへ記録する。

## パスパラメータ

| 項目 | 型 | 必須 | 制約 | 説明 |
| --- | --- | --- | --- | --- |
| requestId | UUID | 詳細のみ必須 | UUID形式 | 外部参照用リクエストID |

## クエリパラメータ

| 項目 | 型 | 必須 | 制約 | 説明 |
| --- | --- | --- | --- | --- |
| from | datetime | 任意 | `to`以前 | 開始日時。未指定時は終了日時の24時間前 |
| to | datetime | 任意 | 検索期間31日以内 | 終了日時。未指定時は現在日時 |
| http_method | string | 任意 | GET、POST、PUT、PATCH、DELETE、HEAD | HTTPメソッド |
| request_path | string | 任意 | 部分一致 | リクエストパスまたは解決済みエンドポイント |
| status_category | string | 任意 | success、client_error、server_error | HTTPステータス区分 |
| status_code | integer | 任意 | 100～599 | HTTPステータス。区分より優先 |
| source_type | string | 任意 | 定義済み発生元分類 | 発生元 |
| principal | string | 任意 | 部分一致 | 操作主体 |
| ip_address | string | 任意 | 完全一致 | 送信元IP |
| min_duration_ms | long | 任意 | 0以上 | 最小処理時間 |
| offset | integer | 任意 | 0以上、既定0 | 取得開始位置 |
| limit | integer | 任意 | 1～200、既定50 | 取得件数 |

## レスポンス

検索条件の前後空白は除去し、空文字は未指定として扱う。
HTTPメソッドは大文字、ステータス区分と発生元は小文字へ正規化してから許可値を検証する。
発生元の許可値はweb・agent・iot・gateway・anonymous。
検索期間の31日は日時の差として判定し、暦日を両端含みで数える方式ではない。
offsetとlimitの範囲違反は自動補正せず400を返す。

一覧は`logs`配列、次ページ有無`has_more`、検索条件に一致する総件数`total_count`を返す。
詳細は一覧要素と同一形式の1件を返す。

各ログには、リクエストID、発生日時、HTTPメソッド、パス、エンドポイント、ステータス、処理時間、発生元、操作主体、IP、User-Agent、エラーコードを含む。

## 主なエラー

| HTTPステータス | code | 説明 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 検索条件または検索期間が不正 |
| 401 | `UNAUTHORIZED` | 未認証 |
| 403 | `ACCESS_DENIED` | ADMIN・OPERATOR以外 |
| 404 | `RESOURCE_NOT_FOUND` | 詳細対象のログが存在しない |

## 備考

- APIログを画面から更新・削除するAPIは提供しない。
- 一覧取得中のリクエストはレスポンス完了後に記録されるため、そのレスポンス自身には含まれない。
