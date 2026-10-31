# JGit Git Smart HTTP技術検証

## 概要

2026/10/29に、Git基盤Phase 0の技術検証としてJGitと標準GitクライアントのHTTPS通信を実測した。
Windowsと一時Linuxコンテナの各17件、計34件が成功し、失敗・エラー・スキップは各0件だった。
JGitを採用候補として次の基本設計へ進める見通しは得られたが、本番Git機能の完成・正式採用は意味しない。

共通テスト設計テンプレート`090_test_design.md`を基に、結果と制約を整理した。

## 対象

| 項目 | 内容 |
| --- | --- |
| 関連要件 | FR-0054 リポジトリ管理、FR-0055 Git操作受付、FR-0056 Git基盤状態・監査・復旧管理 |
| 検証コード | BEのtools/jgit-smart-http-spike。アプリ本体と別のGradleビルド |
| BE基準 | origin/mainの89404afを基準にjgit-smart-http-spikeを作成 |
| JGit | 7.8.0.202609011348-r |
| Spring Boot／Tomcat | 3.5.0／10.1.61 |
| Servlet | JGitのJakarta Servlet依存と、BootのTomcatServletWebServerFactoryへの登録を実際に確認 |
| 通信 | ループバックの動的ポート上のHTTPS。標準Gitの実プロセスから接続 |
| 保存領域 | 試験ごとの一時bare repositoryと合成worktree。既存GitLab・業務DBとは分離 |

検証用認証はHTTP Basicのパスワード欄へランダムトークンを渡し、メモリ上のハッシュと期限・失効状態で照合する。
Cookieや事前のGET認証だけへ依存せず、POSTも認証・認可する。
権限・監査・保護規則は試験用fixtureであり、実DBのPAT管理や既存SecurityConfigの回帰試験ではない。

### バージョン選定

公式配布の安定版7.8.0でJakarta Servletへの依存を確認し、現在のServletコンテナとの互換性を実行確認した。
将来版・snapshotを選ばず、JGitとHTTP Serverモジュールの版を揃えた。
TomcatとJacksonはBEの既存gradle.propertiesに合わせ、本体の依存関係は変更しない。

- [Eclipse JGitリリース一覧](https://projects.eclipse.org/projects/technology.jgit/governance)
- [HTTP Server 7.8.0の公式配布POM](https://repo.maven.apache.org/maven2/org/eclipse/jgit/org.eclipse.jgit.http.server/7.8.0.202609011348-r/org.eclipse.jgit.http.server-7.8.0.202609011348-r.pom)

## 環境・実行結果

| 項目 | Windows | Linux |
| --- | --- | --- |
| OS | 開発端末のWindows | Ubuntu 26.04.1 LTS、一時Podmanコンテナ |
| Java | Microsoft 25.0.4.1 | Eclipse Temurin 25.0.4 |
| Git | 2.46.0.windows.1 | 2.53.0 |
| Gradle | 9.4.1 | 9.4.1 |
| JUnit結果 | 17件成功、失敗0、エラー0、スキップ0 | 17件成功、失敗0、エラー0、スキップ0 |
| git fsck --full | 全17ケースで終了コード0 | 全17ケースで終了コード0 |
| atomic push | 対応を実測 | 対応を実測 |

Linuxイメージは既存のeclipse-temurin:25-jdkを使用し、その一時コンテナ内だけで公式パッケージのGitを導入した。
イメージdigestは`sha256:6fc323be479ffa5874d1d686f4fc49aa0e11d415c79d2257592ff3d6937221eb`。
CentOS Stream 9の実機、SELinux、本番の保存領域・ユーザー・リバースプロキシでは実施していない。

## テストケース

全ケースで既存の到達可能なGitオブジェクト・refを保全し、ケース終了後のfsckも確認した。

| ID | 内容 | 入力・試験条件 | 結果 |
| --- | --- | --- | --- |
| JG-01 | bare作成・正常通信 | HEADをmainへ設定し、標準GitでHTTPS clone・fetch・pull・push | 取得内容とSHAが一致。upload-pack／receive-packの実要求を確認 |
| JG-02 | TLS検証 | 一時証明書を信頼しない場合と、クライアントだけで信頼する場合 | 未信頼時は拒否、信頼時は成功。TLS検証の無効化なし |
| JG-03 | 認証拒否 | トークン未指定・不正・無効・期限切れ・失効のfixture | HTTP 401。標準Gitの不正認証も失敗 |
| JG-04 | 権限拒否 | read権限でpush、対象repository権限なしで取得、POST直接送信 | HTTP 403またはGit失敗。ref変更なし |
| JG-05 | 要求ごとの認証 | discovery成功後に失効しreceive-packをPOST、未認証POST | HTTP 401。discovery成功を認証の代替にしない |
| JG-06 | main保護 | 直接push、force付き更新、削除 | ref更新前に拒否。mainのSHAを維持 |
| JG-07 | 履歴・tag保護 | non-fast-forwardと既存tagの上書き | 拒否。元のbranch・tagのSHAを維持 |
| JG-08 | 複数ref拒否 | 保護refと通常refを同一pushへ含め、atomicオプションなしで送信 | fixtureのpre-receiveで全更新を拒否。一部更新なし |
| JG-09 | atomic対応 | --atomicで2branch作成、その後保護refを含めて更新 | 2branch作成成功。拒否する更新は一部も変更しない |
| JG-10 | 同時push | 同じ旧SHAを基に2要求をpre-receiveで同期させて競合 | 一方成功・一方拒否。refはどちらかの正しいSHAに一致 |
| JG-11 | 通信中断 | ランダム2MiBのpackを送信中、32KiB以上受信した箇所で停止しクライアントを切断 | サーバー処理終了を待って比較。ref変更なし、監査更新なし |
| JG-12 | 更新後の応答異常 | ref確定後のGit成功応答を破棄し、HTTP 502へ置換 | クライアントは失敗だがrefは更新済み。再pushはup-to-dateで監査fixtureも重複しない |
| JG-13 | サイズ上限 | 128KiB上限に対して512KiBのランダムオブジェクトをpush | 拒否。ref変更なし |
| JG-14 | 不正pack | 有効形式のref更新要求に未存在OIDと不正なpack versionを含める | Gitのunpack errorで拒否。HTTP 200でもGitプロトコル上の失敗として確認 |
| JG-15 | 名前・パス保護 | 未登録repo、encoded traversal、dumb HTTPのHEAD・config取得 | HTTP 4xx。内部パスを応答しない |
| JG-16 | シンボリックリンク | 管理storeの外のbare repoへaliasを作成しURLから取得 | 名前から内部IDへの限定マッピングにより404 |
| JG-17 | バックアップ・復元 | 書込みのない状態でmirror取得し、別領域へmirror復元 | 全refのOIDとHEAD一致。復元先のfsckも0 |

JG-12は実ネットワークを任意時刻で切断する試験ではなく、ref確定後の応答障害を注入する試験である。
JG-11では実Gitの専用プロセスツリーを停止し、TLSソケットの切断を発生させた。
JG-16は未登録aliasを解決しないことの確認であり、登録済み保存先を後から改ざんする攻撃の網羅試験ではない。

## 正常系・異常系からの判断

- Jakarta Servlet型のGitServletを現在のBoot／Tomcatへ登録して、標準Gitから操作できる。
- pre-receiveで保護規則を適用し、post-receiveから成功したref・旧新OID・実行者を取得できる。
- post-receiveの記録先はメモリであり、GitとDBを同一トランザクションにしたことにはならない。
- 更新後にクライアントが失敗と判断する場合がある。再送時はrefを再照合し、Git更新を巻き戻さない設計が必要。
- 同時更新は旧OIDとの比較で競合を検出できる。ただし高負荷・多数クライアント・GC競合の保証ではない。
- バックアップはref更新のない状態での取得を確認した。ライブコピー・並行GC・業務メタデータの復元は未確認。

### 復元で照合したref

以下は今回のJG-17で、取得元と復元先の両方が一致したOID。合成コミットのため再実行時には変わる。

| ref | Windows | Linux |
| --- | --- | --- |
| refs/heads/main | 1be8a177f6e07f01cbcaf37651de452147289191 | 0f4d81a5f77f6742c262c2b62581c7ceb47c90ee |
| refs/heads/topic | 5cd73ab7c810112b8631b1945842560f32ab03e8 | 3f39bdba0886b9a0f3a8e7169c6d40c6d96534da |
| refs/tags/backup | 5cd73ab7c810112b8631b1945842560f32ab03e8 | 3f39bdba0886b9a0f3a8e7169c6d40c6d96534da |

## 再実行・証跡

BEルートで、Java 25と標準Gitを用意して独立ビルドだけを実行する。

```powershell
.\gradlew.bat --no-daemon -p tools/jgit-smart-http-spike test
```

Windowsの一時ソケット設定とLinuxでの実行方法は、同ディレクトリのREADMEを参照する。
証明書はkeytoolで短期の自己署名証明書を毎回生成し、テスト用クライアントだけが信頼する。
実環境のCAストア、Gitのユーザー設定、資格情報helper、リモートURL、既存アプリ設定は変更しない。

| 証跡 | BEルートからのパス |
| --- | --- |
| Windows JUnit XML | tools/jgit-smart-http-spike/build/test-results/test/ |
| Windows HTML | tools/jgit-smart-http-spike/build/reports/tests/test/ |
| Windows ケース別JSON | tools/jgit-smart-http-spike/build/reports/spike/ |
| Linux JUnit XML | build/jgit-linux-results-final/test-results/test/ |
| Linux HTML・ケース別JSON | build/jgit-linux-results-final/reports/ |
| Linux OS情報 | build/jgit-linux-results-final/os-release |

JSONにはref、HTTP要求のメソッド・パス、監査fixture、fsck結果、使用版を記録する。
成功判定はJUnit XMLと併用する。JSONだけで成功を判定しない。
生成レポート・鍵・キャッシュはGitへ登録しない。追跡するのは検証コードと本記録である。

## 制約・残課題

- 本番CentOS、SELinux、専用OSユーザー、永続領域、TLSリバースプロキシでの再検証。
- 既存Spring Security／Gateway／APIログとのフィルター順序、Git応答・CSRF境界の結合設計・試験。
- 実DBによるPAT発行・期限・失効・連続試行制限、権限階層、秘密情報のマスキング。
- Git確定後のDB監査保存失敗、Outbox、冪等な補完・再照合。今回はメモリ記録のみ。
- ディスク空き容量・repo上限・pack全体の制限・通信時間制限。今回は単一オブジェクト上限のみ。
- DBメタデータとGitを組にしたバックアップ・復元、論理削除、名前変更、GC・repackとの排他。
- 実運用規模の負荷、複数プロセス／複数ノード、NFS等の保存先、partial clone、LFS、SSHは今回対象外。

技術検証タスクは完了とするが、Git基盤Phase 0全体やFR-0054からFR-0056の受入完了へは加算しない。
基本設計・安全な保存先・認証認可・監査・復旧方式を揃えてから、Git基盤MVPの着手可否を判断する。

## 確認結果

| 確認項目 | 結果 | 補足 |
| --- | --- | --- |
| Windows自動試験 | 成功 | 最終17件、失敗・エラー・スキップ0 |
| Linux自動試験 | 成功 | 最終17件、失敗・エラー・スキップ0 |
| 既存BEの変更確認 | 成功 | origin/mainと比べ、ルートbuild.gradle・gradle.properties・src・CIの差分なし |
| 既存BE全JUnit | 未実施 | 本体を変更せず、独立した検証ビルドだけを実行 |
| 本番受入・実DB連携 | 未実施 | 本番Gitサーバーを起動せず、実DB・GitLabへ書込みなし |
| ブラウザー・画面試験 | 対象外 | UI追加ではなくGitプロトコルの検証。標準Gitの実クライアントを使用 |

## 関連

- [Git基盤の判断記録](../90_MeetingMinutesAndNotes/git_platform_and_notification_decisions_20261017.md)
- [FR-0054](../01_RequirementsDefinition/FR/0054.md)・[FR-0055](../01_RequirementsDefinition/FR/0055.md)・[FR-0056](../01_RequirementsDefinition/FR/0056.md)
- [作業記録](../90_MeetingMinutesAndNotes/weekly_document_progress_20261029.md)
