# t_batch_file_upload_requests（案）

## 概要

バッチ定義画面からの配布ファイル登録について、要求の重複防止、処理状態、登録結果を保持する。
2026年9月17日作成の設計案。マイグレーション・プログラム実装は行っていない。

ファイルメタ情報の正本は [m_batch_files](./m_batch_files.md)、本体はストレージに保存する。
本テーブルは [アップロード登録API](../../05_API_Design/API/0040_batch_file_upload_register.md) 専用で、Agentのログ・成果物アップロードや配布完了の管理には使わない。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | id | BIGSERIAL | 不可 | 自動採番 | 主キー | 内部管理ID |
| 2 | upload_request_id | UUID | 不可 | なし | 複合一意 | クライアントが初回送信前に生成する要求ID |
| 3 | batch_definition_id | BIGINT | 不可 | なし | 外部キー | 登録対象のバッチ定義 |
| 4 | requested_by | BIGINT | 不可 | なし | 外部キー | 認証済みユーザーID |
| 5 | request_fingerprint | VARCHAR(64) | 不可 | なし | CHECK | 登録内容の一致判定用SHA-256 |
| 6 | fingerprint_version | SMALLINT | 不可 | 1 | CHECK | 一致判定の正規化・連結規則の版 |
| 7 | status | VARCHAR(20) | 不可 | processing | CHECK | processing / completed / failed |
| 8 | batch_file_id | BIGINT | 可 | なし | 外部キー・一意 | 登録成功した配布ファイルID |
| 9 | attempt_token | UUID | 不可 | なし | なし | サーバ生成の処理所有者識別子。再試行時に再生成 |
| 10 | lease_expires_dt | timestamp(3) | 可 | なし | 状態制約 | 処理所有権の期限 |
| 11 | attempt_storage_path | VARCHAR(1000) | 不可 | なし | 一意 | 保存ルートからの相対パス。現在の試行専用のバックエンド生成UUID v4を使用 |
| 12 | response_body | JSONB | 可 | なし | 状態制約 | 完了時のAPI応答項目のスナップショット。内部パスを含めない |
| 13 | last_error_code | VARCHAR(100) | 可 | なし | なし | 失敗時の英語コード。認証情報・スタックトレースは保存しない |
| 14 | attempt_count | INTEGER | 不可 | 1 | CHECK | 処理所有権を取得した回数 |
| 15 | completed_dt | timestamp(3) | 可 | なし | 状態制約 | 登録がDBで確定した日時 |
| 16 | created_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 初回の要求記録日時 |
| 17 | updated_dt | timestamp(3) | 不可 | CURRENT_TIMESTAMP | なし | 更新日時 |

## インデックス

| No | インデックス名 | カラム | 一意 | 用途 |
| --- | --- | --- | --- | --- |
| 1 | uq_t_batch_file_upload_requests_request | requested_by, batch_definition_id, upload_request_id | はい | 同一登録操作をDB制約で一意にする |
| 2 | uq_t_batch_file_upload_requests_file | batch_file_id | はい | 1つの登録結果が複数要求に紐づくことを防ぐ。未完了のNULLは複数許可 |
| 3 | idx_t_batch_file_upload_requests_recovery | status, lease_expires_dt | いいえ | 中断した処理の検出 |
| 4 | idx_t_batch_file_upload_requests_definition | batch_definition_id, created_dt | いいえ | 定義単位の調査 |
| 5 | uq_t_batch_file_upload_requests_storage | attempt_storage_path | はい | 試行間の保存先の分離 |

## 外部キー

| No | カラム | 参照先 | 削除時 | 更新時 |
| --- | --- | --- | --- | --- |
| 1 | batch_definition_id | m_batch_definitions.id | RESTRICT | NO ACTION |
| 2 | requested_by | m_users.id | RESTRICT | NO ACTION |
| 3 | batch_file_id | m_batch_files.id | RESTRICT | NO ACTION |

成功ファイルが要求と同じバッチ定義に属することをトランザクション内で検証する。
マスタの通常削除はステータス更新のため、本テーブルを連鎖削除しない。

## 制約・一致判定

- `request_fingerprint` は小文字64桁の16進文字列、`fingerprint_version` と `attempt_count` は1以上。
- フィンガープリントの対象はAPI案に従い、カテゴリ・配布名・説明・実測サイズ・サーバ計算ハッシュとする。認証ユーザー・定義・要求IDは一意キーで区別する。
- 版1の案: 上記の順序のJSON配列を空白なし・UTF-8で直列化してSHA-256を計算する。配布名は保存するNFC表記、説明の未指定はnullに統一する。空文字とnullは区別する。実測サイズは整数、ハッシュは小文字とし、エスケープ規則と共通テスト値は実装前に固定する。
- Content-Typeは一致判定に含めず、再送時は初回に保存した値を返す。
- `processing` ではlease期限必須、結果ID・応答・完了日時はNULL。
- `completed` では結果ID・応答・完了日時必須、lease期限・エラーコードはNULL。
- `failed` ではエラーコード必須、lease期限・結果ID・応答・完了日時はNULL。
- 上記の状態整合はCHECK制約で保証する。結果の存在・所属は外部キーと登録処理で保証する。

## 更新制御

1. 認証・権限を確認し、ファイルを試行ごとの一時領域へ受信する。サイズ・ハッシュ・入力を検証し、フィンガープリントを計算する。検証に失敗した段階では本テーブルへ登録しない。
2. 複合一意キーで要求を予約する。初回は `processing` とし、試行トークン・専用保存先・lease期限を記録してコミットする。同時INSERT競合時は既存行を確認する。
3. 同一キーで内容が異なる場合は409。同じ内容で `completed` なら保存済み応答を200で返す。`processing` の有効なleaseがある場合は409。
4. 同じ内容で `failed`、または期限切れの `processing` の場合は、行ロックの下で所有権を取得する。トークンと専用保存先を更新し、回数を増加させる。同じパスを別試行で使い回さない。
5. 所有者は必要に応じてleaseを延長し、本体を確定保存する。その後、行ロックの下でトークンとleaseを再確認する。所有権を失った処理は結果を確定できない。
6. 同一DBトランザクションで `m_batch_files` のINSERT、結果ID・応答スナップショットの保存、`completed` への更新を行う。新規成功は201。処理中は長時間のDBトランザクションを保持しない。
7. 失敗時は自身のトークンが有効な場合にだけ `failed` とエラーコードを保存する。コミット成否が不明なら、新しい接続で要求行を確認してから再試行・回収を判断する。

同じ要求の再送でも内容検証のため本体を受信する。処理中要求の早期409判定は可能だが、完了済み要求をIDだけで成功扱いにはしない。
更新日時は既存の [更新日時制御](../0001_updated_at_trigger.md) に従う。lease判定はDB側の時刻に統一する。

## 障害復旧・保持

保存ルート設定・パス形式・UUIDの生成主体は [ファイル転送詳細設計](../../02_SystemDesign/バッチ管理/Details/0005_batch_file_transfer_detail_design.md) を参照する。

本体保存後、DBコミット前に停止した場合は未参照の本体が残り得る。再試行は新しい保存先を使い、旧本体を配布しない。
DBコミット後に応答が失われた場合は、要求の完了記録から同じファイルID・登録時の応答を返す。
後からファイルが無効化されても応答スナップショットは変更しない。応答のstatusは登録時点の値であり、現在状態は一覧・詳細APIで取得する。

回収対象は管理領域の一時ファイル・未参照本体に限定する。稼働中の試行、有効なlease、親ファイル・転送ファイルの参照を確認し、処理所有権の取得と競合しない手順で回収する。
DBに接続できず参照の確認ができない場合は削除しない。旧試行のパスはストレージの管理キーから走査できる構造とし、猶予期間と回収頻度は別途確定する。

初期案では要求記録を自動削除しない。保持期限を導入する場合は、期限後の要求ID再利用を拒否する方法も定義し、過去の再送を新規登録として扱わない。
エージェントの未送信結果の保持期間（1週間）は本テーブルには適用しない。

## 備考

- 未確定: lease期間・延長間隔、回収の猶予期間・頻度、要求記録の保持期間、フィンガープリントの直列化詳細。
- 本テーブルは処理管理用であり、マスタ用のactive/inactive/deletedや削除フラグは追加しない。
- 配布時の情報は [t_batch_transfer_files](./t_batch_transfer_files.md) を参照。現行の実行ファイルAPIとの移行設計は別途必要。
- 検証対象: 同一要求の同時送信、内容変更、同名競合、lease失効後の旧処理復帰、保存直後の停止、コミット結果不明、応答消失、参照中ファイルの回収防止。
