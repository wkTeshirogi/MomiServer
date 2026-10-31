# Git認証・認可基本設計

## 概要

Git通信はHTTPSのHTTP BasicとGit専用PATを使用する。
Web画面のJWT、Agent資格情報、在庫連携用の外部APIキーとは別の認証境界とする。
本書は新規Git機能の設計であり、既存SecurityConfigの実装変更ではない。

## 目的

Gitクライアントの資格情報を用途限定し、グループ・repoの権限、ref保護、失効を各要求と確定時に適用する。

## 対象範囲・構成要素

| 境界 | 設計 |
| --- | --- |
| Git通信 | `/git/<グループパス>/<repo>.git`配下のSmart HTTP専用領域を新設予定 |
| 管理API | `/api/git/**`を候補とする。既存Web JWTで認証し、Git管理権限を追加判定 |
| PAT管理 | 本人の発行・一覧・失効。システムADMINは他人のPATを失効可能だが原文取得は不可 |
| Git認証フィルター | Git領域を先に選択する専用SecurityFilterChain。既存のJWT等を代用しない |
| Git認可サービス | ユーザー状態、PAT、グループ継承、repo ACL、サービス状態を一元判定 |

パスは基本設計上の候補であり、API一覧・画面要件を作成してから固定する。
Gitチェーンの対象外は既存チェーンへ渡し、既存APIの許可範囲を広げない。
Servlet Filterの二重登録防止も既存設計に合わせる。

### ネットワーク公開境界

VPNなしの外部公開はWeb画面と、その操作に必要なAPI・WebSocketに限定する。
Git Smart HTTPのclone・fetch・pull・pushはLAN／VPN内に限定し、有効なPATでも公開入口からは拒否する。
GETのdiscoveryとPOSTの両方を対象とし、WebのSPAフォールバックや別API経由でGit通信を迂回できないようにする。
Web用repo管理APIは既存JWTとGit管理権限で判定し、Gitプロトコルのネットワーク許可とは区別する。
転送ヘッダーの自己申告をLAN判定の根拠にせず、信頼する入口・プロキシ・接続元の境界を詳細設計で固定する。
公開範囲・検索非掲載の正本は[本番運用設計](../../07_OperationsDesign/0005_production_acceptance.md)とする。

## データ方針

| 論理データ | 方針 |
| --- | --- |
| Git PAT | ID、識別用プレフィックス、ハッシュ、名称、所有user ID、scope、期限、発行・利用・失効日時 |
| Gitアクセス許可 | 対象group/repo ID、user ID、read/write/admin、付与者・日時、更新版 |
| ref保護 | repo ID、対象ref、直接更新・force・削除・上書き規則、版 |
| 例外許可 | repo ID、対象ref、目的、許可者・実行者、期待old/new OID、有効期限、消費状態 |
| 監査 | PAT ID・user ID・repo ID・操作ID・判定結果。PAT原文やAuthorizationは記録しない |

識別は不変のuser IDを使い、重複可能な表示名usernameを認証キーにしない。
Basicのユーザー名には既存ログイン識別子のemailを用い、PAT所有者との一致を確認する。
emailをURLへ埋め込まず、Git credential helperで資格情報を保持する運用とする。

### PATの発行・状態

- 暗号学的乱数による十分なエントロピーを持つSecretを生成し、原文は発行成功時の一度だけ表示する。
- 既存のTokenHashesによるSHA-256保存・定時間比較を再利用する。人のパスワードへこの方式を適用しない。
- 公開識別子で候補を絞って照合する。未知識別子でも固定長ダミーハッシュと比較し、応答理由を統一する。
- 有効期限を必須とし、初期値・最大期間は発行日時から暦1年とする。短縮は可能だが無期限は許可しない。
- 閏日の1年後は翌年2月末とする。画面の初期入力とBE上限検証は同じ算出規則を使う。
- `active / revoked`を永続化し、`expired`は現在時刻が期限以上かで判定する。
- 任意のscope文字列、期限切れ発行、他人へのなりすまし発行は拒否する。
- `git:read`は取得、`git:write`はpushを許可する。writeはreadを包含する。
- PATは所有者の現在の権限を超えない。発行後にACLが減れば即座に利用範囲も減る。
- PATで管理APIを操作できない。`admin`はrepo権限であり、Git管理用PAT scopeを新設する意味ではない。
- 失効済みPATは再有効化しない。交換は新規発行、切替、旧PAT失効の順とする。
- 日時の比較はBE共通Clockを利用し、DB・APIの日時契約を揃える。表示は既存の日本時間方針に従う。

## 権限解決

### システムロールとGit権限

1. ユーザー不在、利用不可状態、BAN_USER、未知ロールは、Git ACLの有無に関係なく拒否する。
2. 利用可能なシステムADMINには全repoのadminを与える。ただしPAT scope、ref保護、保守・障害は迂回しない。
3. 他の通常ロールは、対象repo、所属グループ、すべての祖先グループで本人に付与された権限を集約する。
4. `admin > write > read > 未付与`の最大値を有効権限とする。未付与はアクセス不可とする。

初期は許可の加算型継承とし、明示deny、継承停止、システムロールからの自動付与は設けない。
例えば親groupがwrite、repoがreadならwriteとなる。権限を下げる場合は元の親group付与を変更する。
GitHub設定の「近い設定が上書きする」継承とは別規則である。
groupへの権限は、その配下のrepoに対する付与であり、既存ユーザーロール自体の変更権限ではない。

### 操作権限表

| 操作 | 必要なGit権限 | 追加条件 |
| --- | --- | --- |
| clone・fetch・pull、branch/tag/commit参照 | read以上 | Git通信はgit:read以上のPAT |
| 通常branchへのpush、新規tag | write以上 | git:write、ref保護、状態・容量を確認 |
| repo設定・ACL変更、論理削除・復元 | admin | Web JWTで認証。repoのadminだけでは親group ACLを変更不可 |
| repo作成 | 対象groupのadminまたはシステムADMIN | 個人が任意のgroupへ作成できる扱いにはしない |
| group作成・階層変更・group ACL | 親groupのadminまたはシステムADMIN | ルートgroup作成はシステムADMIN。移動元・先の管理権限を確認 |
| 物理削除、復旧公開、保守設定 | システムADMIN | バックアップ・猶予・検証条件を満たす |
| 自分のPAT発行・失効 | 利用可能な本人 | PAT発行だけではrepoの権限を取得しない |

公開範囲は初期privateのみとし、匿名clone、全ログインユーザーへの自動公開は追加しない。
追加する場合は別途権限表・取得範囲・監査の要件を定義する。

## 主要処理

### HTTP要求とCSRF境界

| 要求 | 処理 |
| --- | --- |
| GET info/refs | serviceを限定し、Basic/PATとreadまたはwriteを判定してdiscoveryを返す |
| POST git-upload-pack | GET済みと仮定せず再認証し、readとscopeを確認 |
| POST git-receive-pack | 再認証・write確認後に受信。ref確定直前にも再判定 |
| HEAD/config等のdumb HTTP、未知service | 配信しない。Git以外の静的ファイルを公開しない |
| その他のmethod・OPTIONS | Git操作として許可しない。Web全体のOPTIONS許可をGit領域へ流用しない |

TLSと証明書検証を必須とし、HTTPへの認証ヘッダー送信・URL内PAT・クエリPATは認めない。
プロキシがAuthorizationを転送し、ログに残さないことを確認する。
JGitの形式に合うContent-Typeとservice・methodだけを許可し、form、JSON、任意バイナリを拒否する。
Origin付きGit POSTは拒否し、Git領域へブラウザー用CORSを許可しない。
ブラウザーのクロスサイトを示すFetch Metadataも拒否する。

HTTP Basicはブラウザーが資格情報を自動送信する可能性があり、statelessだけではCSRF安全とはしない。
標準GitがCSRFトークンを送れないため、CSRF除外は厳密に上記Git要求へ限定して設計・回帰試験する。
Cookie/JWTによるGit認証、GETによる更新、全体のCSRF設定の追加緩和は認めない。
既存SecurityConfigでhttpBasicが無効な点を踏まえ、Git専用チェーンだけでPAT Basicを有効にする。

### 権限変更・失効と処理中push

認可状態の変更とref確定を、単一Backend内の共通認可確定ゲートで直列化する。
PAT失効、ユーザー利用停止・ロール変更、group/repo ACL変更、保護規則変更はこの境界を共用する。

- pack受信中は認可ゲートを保持しない。受信中に失効したPATはref確定直前の判定で拒否する。
- 確定処理は現行DB状態を再読込し、認可ゲートを保持した短い区間でref更新を完了する。
- 失効操作が完了してから、そのPATによる新たなref更新が確定する状態を作らない。
- すでに確定境界に入ったpushが先に完了する場合、失効操作はその終了後に完了する。
- ロック順は保守ゲート、必要なrepoリース、認可ゲートの順とし、逆順取得を禁止する。
- DB認可情報を確認できなければ拒否し、期限付きキャッシュだけで失効済みPATを通さない。

既存ユーザー管理の変更経路もゲートへ接続する必要があり、Gitチェーンを追加するだけでは完成としない。
すでに認可済みのダウンロードで送った内容は取り消せない。失効の保証対象は後続要求とref確定であり、
既存ストリームの即時切断を保証する仕様ではない。

### ref保護と例外

| 対象 | 既定方針 |
| --- | --- |
| main | 通常の直接push、force、削除を禁止。ADMINでも通常pushは不可 |
| 通常branch | fast-forward更新と新規作成のみ許可。force・削除は初期禁止 |
| tag | 新規作成のみ許可。上書き・削除は初期禁止 |
| 内部用ref | クライアントからの作成・変更を拒否。許可namespaceはheadsとtagsに限定 |
| 複数ref | 全件事前判定に加えてatomic必須。例外対象外refが混ざれば全件拒否 |

初回main作成・障害復旧だけ、Git管理者がWeb認証で目的と期待old/new OIDを明示して例外を承認する。
例外は短期・一回・単一repo/ref・本人・PAT IDに限定し、設定全体の「保護OFF」で代用しない。
初回作成はoldがゼロOIDの場合のみ。復旧は必要な更新種別を指定し、main削除の例外は初期対応しない。
許可の消費を操作予定へ結び付け、通信再送で別の更新へ使い回せないようにする。
確定失敗・結果不明時も自動再利用せず、照合後に必要なら再承認する。

マージ機能完成前は通常のmain更新経路が存在しない。障害復旧の例外を日常マージの代用にしない。
将来のサーバー側マージも同じ認可・排他・監査を通す設計とし、DBやbareへの直接書込で迂回しない。

## エラー・監査方針

| 事象 | 外部応答・内部処理 |
| --- | --- |
| 未認証・PAT不正・期限切れ・失効 | 同じ401とBasicの認証チャレンジ。細かな失敗原因は外へ出さない |
| 有効な本人だが権限・scope不足 | 403。存在しないrepoは404。非公開名の探索は試行制限・監査対象 |
| 連続した認証試行 | 429等で制限。Webログイン用失敗回数を加算して第三者が本人をロックできる構成にしない |
| DB障害・全体保守 | 503。古い許可情報で認証を継続しない |
| ref保護拒否 | Git開始前は403、開始後はref拒否結果を返し、HTTP成功だけで判定しない |

試行制限は送信元とPAT識別子等を組み合わせ、識別子を変えた大量試行も全体上限で抑制する。
最終利用日時の更新頻度と監査量は分離し、Secret、本文、commit内容、Basic値を監査へ含めない。
不正利用とGit管理操作を通常APIログから追跡可能なIDで関連付ける。
Git操作の監査も現行の1年保持方針に合わせるが、未補完の操作予定は清掃から除外する。

## 実装時の受入条件

| ID | 確認内容 |
| --- | --- |
| GA-01 | JWT・Agent・外部キー・CookieではGit認証不可。PATで管理APIを操作できない |
| GA-02 | ADMIN、通常ロール、BAN_USER、親group継承、未付与、移動時の増減を表どおり判定 |
| GA-03 | discovery後・pack受信中の失効とACL変更、同時失効・ref確定を順序付きで検証 |
| GA-04 | main直接push拒否、初回・復旧例外の期限・本人・OID・一回性、再送を検証 |
| GA-05 | Content-Type、Origin、CORS、method、URL、TLS、秘匿ログと外部入口のGit拒否を実プロキシ込みで確認 |
| GA-06 | PATの発行時のみ表示、定時間比較、期限境界、失効後再起動、バックアップ復元後失効を確認 |

## 関連ドキュメント

- [基本設計全体](./000_index.md)
- [既存Web認可](../../11_SecurityDesign/0002_authorization.md)
- [ストレージ・操作整合性](./0001_storage.md)
- [バックアップ・復元](./0003_backup_restore.md)
- [Spring SecurityのCSRFとstateless認証の注意点](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
