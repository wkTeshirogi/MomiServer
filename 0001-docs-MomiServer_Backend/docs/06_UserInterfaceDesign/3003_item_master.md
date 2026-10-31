# 在庫品目マスタ画面設計書

## 概要

備品・消耗品として管理する在庫品目マスタの一覧表示、詳細確認、登録、更新を行う画面。
品目名、カテゴリ、単位、最低数量、メーカー、型番、JANコード、購入URL、品目管理ステータスを管理する。

## 対象ルート

| 画面 | パス | コンポーネント |
| --- | --- | --- |
| 一覧 | `/master/items` | `ItemMasterList` |
| 詳細 | `/master/items/:itemId` | `ItemMasterDetail` |
| 新規登録 | `/master/items/new` | `ItemMasterDetail` |

## 一覧画面

### 一覧データ取得

| 処理 | API |
| --- | --- |
| 在庫品目一覧取得 | `POST /api/master/item/list` |

### 表示項目

| 項目 | 説明 |
| --- | --- |
| item_name | 品目名 |
| category | カテゴリ |
| min_quantity / unit | 最低数量と単位 |
| manufacturer | メーカー |
| model_number | 型番 |
| jan_code | JANコード |
| status | 品目管理ステータス。`active`、`disabled`、`deleted` を表示名へ変換する |

### 画面内検索

一覧取得後、画面側で入力された検索語を小文字化し、以下の項目に部分一致する行を表示する。

| 対象項目 | 備考 |
| --- | --- |
| item_name | 品目名 |
| category | カテゴリ |
| unit | 単位 |
| min_quantity | 数値を文字列化して比較 |
| manufacturer | メーカー |
| model_number | 型番 |
| jan_code | JANコード |
| status | ステータス値 |
| status 表示名 | 有効、無効、削除 |

## 詳細・登録画面

### 詳細データ取得

| 条件 | 処理 |
| --- | --- |
| `itemId` が `new` | 空の入力フォームを表示する |
| `itemId` が数値 | `POST /api/master/item/detail?id={itemId}` で詳細を取得する |
| `itemId` が数値以外 | 品目ID不正エラーを表示する |

### 入力項目

| 項目 | 入力種別 | 必須 | 説明 |
| --- | --- | --- | --- |
| item_name | text | 必須 | 品目名 |
| category | text | 必須 | カテゴリ |
| unit | text | 必須 | 単位 |
| min_quantity | number | 必須 | 最低数量。0以上に補正する |
| manufacturer | text | 任意 | メーカー |
| model_number | text | 任意 | 型番 |
| jan_code | text | 任意 | 数字のみ、13桁以内に整形する |
| status | select | 必須 | 有効、無効、削除から選択する |
| purchase_url | text | 任意 | 購入URL |
| memo | textarea | 任意 | 品目に関するメモ |

### 初期値

| 項目 | 初期値 |
| --- | --- |
| item_name | 空文字 |
| category | 空文字 |
| unit | 空文字 |
| min_quantity | 0 |
| manufacturer | 空文字 |
| model_number | 空文字 |
| jan_code | 空文字 |
| purchase_url | 空文字 |
| status | `active` |
| memo | 空文字 |

### 入力チェック

| 項目 | 条件 |
| --- | --- |
| item_name | 空白のみ不可 |
| category | 空白のみ不可 |
| unit | 空白のみ不可 |
| min_quantity | 数値かつ 0 以上 |
| jan_code | 13桁以内 |
| status | 空白のみ不可 |

## 操作フロー

| 操作 | 挙動 |
| --- | --- |
| 新規登録 | `/master/items/new` へ遷移し、空フォームを表示する |
| 詳細 | `/master/items/{id}` へ遷移し、詳細情報を取得する |
| 保存 | 入力チェック後、`POST /api/master/item/register` を実行する |
| 新規保存成功 | 保存後の ID を使用して `/master/items/{id}` へ置換遷移する |
| 更新保存成功 | 詳細情報を再取得し、保存完了メッセージを表示する |
| 一覧に戻る | `/master/items` へ遷移する |

## エラーハンドリング

| 条件 | 表示内容 | 挙動 |
| --- | --- | --- |
| 一覧取得失敗 | 在庫品目マスタ一覧の取得に失敗しました。 | 一覧上部にエラーを表示する |
| 詳細取得失敗 | 在庫品目マスタ詳細の取得に失敗しました。 | 詳細画面上部にエラーを表示する |
| 対象なし | 在庫品目マスタが見つかりません。 | 詳細画面上部にエラーを表示する |
| 保存失敗 | 在庫品目マスタの保存に失敗しました。 | フォーム上部にエラーを表示する |
| 入力チェックエラー | 項目ごとのエラー文言 | 対象項目の下に表示し、APIは呼び出さない |

## 実装ファイル

| 種別 | パス |
| --- | --- |
| ルーティング | `src/App.tsx` |
| マスタメニュー | `src/pages/Master/MasterList/page.tsx` |
| 一覧画面 | `src/pages/Master/Items/List/page.tsx` |
| 一覧サービス | `src/pages/Master/Items/List/service.tsx` |
| 詳細画面 | `src/pages/Master/Items/Detail/page.tsx` |
| 詳細サービス | `src/pages/Master/Items/Detail/service.tsx` |

## 関連仕様

- 在庫品目の物理削除操作は提供しない。
- 削除扱いにする場合は、品目管理ステータスとして `deleted` を保存する。
- 現在数量、保管場所、在庫ステータス、期限日は在庫品目マスタではなく実在庫側の管理対象とする。
