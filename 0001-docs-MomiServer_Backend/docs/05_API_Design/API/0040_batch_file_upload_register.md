# バッチ定義配布ファイル アップロード登録API

## V25実装契約（以下の旧案より優先）

管理APIはADMIN権限を要求する。ファイルは不変として保存し、解除はステータス更新とする。

| API | 入出力 |
| --- | --- |
| GET /api/master/batch/{definitionId}/files | files配列とexceeds_limitsを返す |
| POST /api/master/batch/{definitionId}/files | multipart: file、upload_request_id(UUID)、destination_path、file_category(script/input/config)、任意replaces_id。正常200で添付情報 |
| POST /api/master/batch/{definitionId}/files/{fileId}/status?status=deleted | 添付解除。正常200 |
| GET /api/v1/agent/batch/executions/{id}/attachments | 当該実行の確定済み添付一覧 |
| GET /api/v1/agent/batch/executions/{id}/attachments/{fileId} | application/octet-streamで本体。リダイレクトなし |

添付情報はid、file_name、destination_path、file_category、file_size、checksum_sha256、created_at。保存パスは返さない。
Agent APIは専用認証と実行の端末・登録世代を照合し、確定一覧に含まれる添付だけ取得できる。旧版への添付解除後も既存要求からの取得は可能。
上限超過・パス不正・同一要求IDの内容不一致は400、管理権限不足は403。保存先の未設定は400。I/O障害は保存失敗として返す。
共通設定APIの追加項目はattachment_max_file_mib、attachment_max_total_mib、attachment_max_count、work_directory_retention_days（整数）。省略時は既存値を維持する。

## 旧案の基本情報

| 項目 | 内容 |
| --- | --- |
| API ID | API-0040（案） |
| 分類 | バッチ定義配布ファイル管理 |
| メソッド | POST |
| パス | `/api/master/batch/{batchDefinitionId}/files`（案） |
| 認証 | 既存の管理画面向け認証を必須とする |
| 権限 | ADMINを初期案とし、対象定義を更新する権限も確認する |
| Content-Type | multipart/form-data |
| 正常ステータス | 新規登録201、同一要求の受理済み結果返却200（案） |

## 概要

2026年9月16日作成。管理画面からスクリプト・入力・設定ファイルの本体を受信し、
バッチ定義に紐づく配布ファイルとして保存するAPI案。パス・入力・応答・制約は未確定で、実装は行っていない。

既存の[実行要求へのファイル登録API](./0025_batch_execution_file_register.md)は、
サーバ上に存在するファイルのパスを登録する処理であり、本体アップロードではない。
本案は既存APIを置き換えず、管理画面のバッチ定義編集から利用する新規APIとして分離する。
新規バッチ定義は先に保存してIDを取得してからアップロードする。
既存設計の「バッチ定義登録・更新処理の一部」は画面の操作フローとして維持し、
HTTP要求とDBトランザクションは分ける案とする。

Agentからのログ・成果物送信は[Agentファイルアップロード旧案](./0031_agent_file_upload.md)の対象で、本案とは別扱い。
この旧案のパスは未実装であり、Agent専用認証を前提として再設計が必要である。

## 処理概要

1. 管理者認証、対象バッチ定義の存在・更新権限、要求の入力を確認する。
2. 登録要求IDの状態を確認する。受理済み要求でも以下の内容検証を行い、一致した場合のみ同じファイル登録結果を返す。
3. ファイルを専用領域の一時ファイルへ保存する。受信しながらサイズ上限を検査し、SHA-256を計算する。
4. 任意の送信元チェックサムと比較し、不一致なら登録しない。
5. サーバが採番した保存キーでファイルを確定保存し、メタ情報をm_batch_filesへ登録する。
6. 本体とメタ情報の保存が完了してから登録結果を返す。保存途中のファイルは配布対象にしない。

登録に失敗しても、先に保存したバッチ定義を自動的に取り消さない。画面でファイル登録を再試行できるようにする。
本体保存とDB更新は単一トランザクションではないため、中断時の孤立ファイル回収・整合確認を実装する。
削除対象はサーバが管理する一時ファイル等に限定し、利用者が指定したパスを削除しない。

## パスパラメータ

| 項目 | 型 | 必須 | 制約 | 説明 |
| --- | --- | --- | --- | --- |
| batchDefinitionId | integer(int64) | 必須 | 正のID | 保存済みバッチ定義のID |

## クエリパラメータ

なし。

## リクエストボディ

1リクエストにつき1ファイルをmultipart/form-dataで送る。

| 項目 | 型 | 必須 | 制約 | 説明 |
| --- | --- | --- | --- | --- |
| upload_request_id | UUID文字列 | 必須 | 初回送信前にクライアントで生成 | 応答消失後の重複登録防止。API識別子であり認証情報ではない |
| file | binary | 必須 | サイズ上限は設定化、初期値未確定 | ファイル本体 |
| file_name | string | 必須 | 1〜255文字、パス区切り・制御文字・ドットのみの名前を禁止 | 配布先でのファイル名。OSの予約名等も検証する |
| file_category | string | 必須 | input / script / config | 初期対象。agent_updateは自己更新機能とともに別途扱う |
| description | string | 任意 | 最大1000文字 | 説明 |
| checksum_sha256 | string | 任意 | 64桁の16進文字列 | 送信元が算出したハッシュ。不指定でもサーバ側では算出する |

Content-Typeはfileパートの情報等から取得し、ファイル内容の安全性を示す根拠にはしない。
storage_path、file_size、登録ユーザー、管理ステータスはクライアントから指定させない。
保存先はサーバ側で決め、受信ファイルを登録処理中に実行・自動展開しない。
保存ルートは設定ファイルで指定し、実体名にはバックエンド生成のUUID v4を使用する。
設定・DBの相対パス・保存先変更手順は [ファイル転送詳細設計](../../02_SystemDesign/バッチ管理/Details/0005_batch_file_transfer_detail_design.md) に従う。

### 重複登録と差替え

同じ認証ユーザー・バッチ定義・upload_request_idを一つの登録操作として扱う。
同じ内容の再試行は同じファイルIDを返し、内容が異なる場合は409とする。
一致判定にはカテゴリ・配布ファイル名・説明・実測サイズ・サーバ計算ハッシュを使用する。
同時処理中の同一要求は409として、既存処理と重複した登録を行わない案とする。

別要求で同名のactiveファイルがある場合は409とし、暗黙に上書きしない。
ファイルの差替え・無効化は別の状態更新契約で具体化し、削除専用APIは設けない。
要求IDと受理結果は [アップロード要求管理テーブル案](../../04_DatabaseDesign/Tables/t_batch_file_upload_requests.md) に保存する。
状態・処理所有権・保存失敗時の復旧・保持方針は同定義を参照する。
同名判定用キーとDBの一意制約は [配布ファイルテーブル案](../../04_DatabaseDesign/Tables/m_batch_files.md) を参照する。

## レスポンス

| 項目 | 型 | 説明 |
| --- | --- | --- |
| batch_file_id | integer(int64) | 登録した配布ファイルID |
| batch_definition_id | integer(int64) | 対象バッチ定義 |
| upload_request_id | UUID文字列 | 登録要求の識別子 |
| file_name | string | 配布ファイル名 |
| file_category | string | ファイルカテゴリ |
| content_type | string | 保存したContent-Type |
| file_size | integer(int64) | サーバが計測したバイト数 |
| checksum_sha256 | string | サーバが計算したSHA-256 |
| status | string | 登録成功時はactive |
| created_at | string | 登録日時。日時形式は共通API規約に合わせて確定 |

内部のstorage_pathや認証情報は返さない。
登録成功はAgentへの配布完了を意味せず、この時点ではdownload_uuidを返さない。

## 実行要求・配布との関係

実行要求作成時にactiveの親ファイルから実行要求単位の転送情報を作成し、download_uuid等をAgentへ連携する。
本案の登録対象はm_batch_files、配布対象はt_batch_transfer_filesで分ける。

作成済みの実行要求が参照するファイル内容・ハッシュは、親データの差替えで変えない案とする。
参照中の本体を削除しない保持・回収方針が必要となる。
現行の実行ID・fileIdによるダウンロード実装と、設計上のdownload_uuid方式の接続・移行は別途実装する。
本APIだけで配布フローが完成したとは扱わない。

## 主なエラー

以下は提案コード。既存の共通エラー形式に合わせて確定する。

| HTTPステータス | code | 説明 |
| --- | --- | --- |
| 400 | VALIDATION_ERROR | 必須項目、カテゴリ、ファイル名、ハッシュ形式が不正 |
| 401 | UNAUTHORIZED | 管理画面向け認証が無効 |
| 403 | ACCESS_DENIED | 対象定義の更新権限なし |
| 404 | BATCH_DEFINITION_NOT_FOUND | 対象定義なし |
| 409 | UPLOAD_CONFLICT | 同名競合、同一要求の内容不一致・処理中等 |
| 413 | FILE_TOO_LARGE | 設定されたアップロード上限超過 |
| 422 | CHECKSUM_MISMATCH | 指定ハッシュと実測値が不一致 |
| 503 | FILE_STORAGE_UNAVAILABLE | 容量不足等で保存できない |
| 500 | FILE_REGISTRATION_FAILED | その他の保存・DB更新失敗 |

応答消失や一時障害では同じ登録要求IDで再試行する。入力修正や別ファイルの登録は新しい要求IDとする。

## 備考

- 未確定: 上限サイズ、拡張子制限、空ファイル可否、容量制限、要求ID保持期間、同名判定の大文字小文字規則。
- 未確定: 配布ファイル一覧・メタ情報更新・状態変更API、ファイル差替えの操作手順、実行要求作成との競合制御。
- 検証対象: 本体保存、権限、ハッシュ不一致、容量不足、同時再送、応答消失、DB保存失敗、実行中のファイル差替え。
- [バッチファイル転送基本設計](../../02_SystemDesign/バッチ管理/0007_batch_file_transfer_basic_design.md)
- [配布ファイルテーブル設計](../../04_DatabaseDesign/Tables/m_batch_files.md)

## 追加合意との整合

添付対象、配置先の重複禁止、バッチ共通設定の上限と引き下げ時の扱いは[ファイル転送詳細設計の合意仕様](../../02_SystemDesign/バッチ管理/Details/0005_batch_file_transfer_detail_design.md)を優先する。
受信時にバックエンドが上限を検証し、相対配置先を管理できるよう本API案を具体化する。入力・応答・DB項目の追加仕様は未確定であり、本追記で実装済みAPIの契約を変更するものではない。
