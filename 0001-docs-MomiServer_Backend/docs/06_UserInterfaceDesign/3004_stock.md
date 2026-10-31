# 在庫管理画面設計書

## 概要

品目マスタと実在庫を組み合わせて、現在数量、保管場所、補充推奨数量、在庫ステータスを管理する画面。
在庫管理一覧、詳細確認、登録、更新を行う。

## 対象ルート

| 画面 | パス | コンポーネント |
| --- | --- | --- |
| 一覧 | `/stock` | `StockList` |
| 詳細 | `/stock/:itemId` | `StockDetail` |
| 新規登録 | `/stock/new` | `StockDetail` |
| 互換ルート | `/Stock/List` | `StockList` |
| 互換ルート | `/Stock/Detail/:itemId` | `StockDetail` |

## 一覧画面

### 一覧データ取得

| 処理 | API |
| --- | --- |
| 在庫品目一覧取得 | `POST /api/master/item/list` |
| 実在庫一覧取得 | `POST /api/stock/list` |

### 表示項目

| 項目 | 説明 |
| --- | --- |
| manufacturer | 品目マスタのメーカー |
| item_name | 品目マスタの品目名 |
| model_number | 品目マスタの型番 |
| category | 品目マスタのカテゴリ |
| quantity / min_quantity | 実在庫の現在数量と品目マスタの標準最低数量 |
| inventory_status | 実在庫ステータス。`available`、`low`、`out_of_stock`、`deleted` を表示名へ変換する |
| master.status | 品目管理ステータス。`active`、`disabled`、`deleted` を表示名へ変換する |

バックエンド実装では、実在庫ステータスを `InventoryStatus`、在庫数量操作種別を `InventoryStockChangeType` 列挙型として扱う。

API入出力値とDB保存値は従来通り `available`、`low`、`out_of_stock`、`deleted` および `inbound`、`outbound`、`stocktake`、`discard`、`delete` の文字列とする。

### 画面内検索

一覧取得後、画面側で入力された検索語を小文字化し、以下の項目に部分一致する行を表示する。

| 対象項目 | 備考 |
| --- | --- |
| manufacturer | メーカー |
| item_name | 品目名 |
| model_number | 型番 |
| category | カテゴリ |
| unit | 単位 |
| master.status | 品目管理ステータス値 |
| master.status 表示名 | 有効、無効、削除 |
| storage_location | 保管場所 |
| inventory_status | 在庫ステータス値 |
| inventory_status 表示名 | 在庫あり、在庫注意、在庫なし、削除 |
| quantity | 現在数量を文字列化して比較 |
| min_quantity | 標準最低数量を文字列化して比較 |
| inventory_item_id | 品目 ID を文字列化して比較 |

## 詳細・登録画面

### 詳細データ取得

| 条件 | 処理 |
| --- | --- |
| `itemId` が `new` | 品目マスタ一覧を取得し、空の入力フォームを表示する |
| `itemId` が数値 | 品目マスタ一覧と `POST /api/stock/detail?id={itemId}` を並列取得する |
| `itemId` が数値以外 | 在庫ID不正エラーを表示する |

### 入力項目

| 項目 | 入力種別 | 必須 | 説明 |
| --- | --- | --- | --- |
| inventory_item_id | select | 必須 | 品目マスタから選択する |
| storage_location | text | 必須 | 保管場所 |
| quantity | number | 必須 | 現在数量。0以上に補正する |
| min_quantity | readonly | - | 選択した品目マスタの標準最低数量を表示する |
| replenish_quantity | number | 任意 | 補充時の推奨数量。空欄可 |
| inventory_status | readonly | - | 現在数量と品目マスタの最低数量から自動算出した表示名を表示する |
| expiration_date | date | 任意 | 期限日 |
| memo | textarea | 任意 | 在庫に関するメモ |

### 初期値

| 項目 | 初期値 |
| --- | --- |
| inventory_item_id | 0 |
| storage_location | 空文字 |
| quantity | 0 |
| replenish_quantity | 未設定 |
| inventory_status | 新規表示時は `available`。保存時は数量と最低数量から再算出する |
| expiration_date | 未設定 |
| memo | 空文字 |

### 入力チェック

| 項目 | 条件 |
| --- | --- |
| inventory_item_id | 1以上 |
| storage_location | 空白のみ不可 |
| quantity | 数値かつ 0 以上 |
| replenish_quantity | 入力時は数値かつ 0 以上 |

### 在庫ステータスの自動算出

| 条件 | ステータス |
| --- | --- |
| 既存ステータスが `deleted` | `deleted` を維持する |
| 現在数量が 0 | `out_of_stock` |
| 現在数量が1以上かつ最低数量以下 | `low` |
| 現在数量が最低数量を上回る | `available` |

在庫ステータスは利用者が直接選択しない。保存時に画面側で算出した値を送信し、バックエンドでも品目マスタの最低数量を参照して整合する値へ補正する。

## 操作フロー

| 操作 | 挙動 |
| --- | --- |
| 新規登録 | `/stock/new` へ遷移し、空フォームを表示する |
| 詳細 | `/stock/{id}` へ遷移し、詳細情報を取得する |
| 保存 | 入力チェック後、`POST /api/stock/save` を実行する |
| 新規保存成功 | 保存後の ID を使用して `/stock/{id}` へ置換遷移する |
| 更新保存成功 | 詳細情報を再取得し、保存完了メッセージを表示する |
| 一覧に戻る | `/stock` へ遷移する |

## エラーハンドリング

| 条件 | 表示内容 | 挙動 |
| --- | --- | --- |
| 一覧取得失敗 | 在庫一覧の取得に失敗しました。 | 一覧上部にエラーを表示する |
| 詳細取得失敗 | 在庫詳細の取得に失敗しました。 | 詳細画面上部にエラーを表示する |
| 対象なし | 在庫情報が見つかりません。 | 詳細画面上部にエラーを表示する |
| 保存失敗 | 在庫情報の保存に失敗しました。 | フォーム上部にエラーを表示する |
| 入力チェックエラー | 入力内容を確認してください。 | ページ上部と対象項目の下に表示し、APIは呼び出さない |

## 実装ファイル

| 種別 | パス |
| --- | --- |
| ルーティング | `src/App.tsx` |
| サイドバー | `src/components/layout/Sidebar.tsx` |
| 一覧画面 | `src/pages/Stock/List/page.tsx` |
| 一覧サービス | `src/pages/Stock/List/service.tsx` |
| 詳細画面 | `src/pages/Stock/Detail/page.tsx` |
| 詳細サービス | `src/pages/Stock/Detail/service.tsx` |

## 関連仕様

- 実在庫画面では品目マスタを別途取得し、実在庫の `inventory_item_id` と突合する。
- 現行の画面実装では、数量操作 API と履歴取得 API は直接利用していない。
- 詳細画面の保存は `POST /api/stock/save` を使用するため、保存操作だけでは数量変更履歴を作成しない。
