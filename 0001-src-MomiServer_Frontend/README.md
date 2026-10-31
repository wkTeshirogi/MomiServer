# 0001-src-SmartHomeWebServerFrontend



## Getting started

To make it easy for you to get started with GitLab, here's a list of recommended next steps.

Already a pro? Just edit this README.md and make it your own. Want to make it easy? [Use the template at the bottom](#editing-this-readme)!

## Add your files

* [Create](https://docs.gitlab.com/user/project/repository/web_editor/#create-a-file) or [upload](https://docs.gitlab.com/user/project/repository/web_editor/#upload-a-file) files
* [Add files using the command line](https://docs.gitlab.com/topics/git/add_files/#add-files-to-a-git-repository) or push an existing Git repository with the following command:

```
cd existing_repo
git remote add origin https://gitlab.zundamochi.local/xokikurumi/0001-src-smarthomewebserverfrontend.git
git branch -M main
git push -uf origin main
```

## Integrate with your tools

* [Set up project integrations](https://gitlab.zundamochi.local/xokikurumi/0001-src-smarthomewebserverfrontend/-/settings/integrations)

## Collaborate with your team

* [Invite team members and collaborators](https://docs.gitlab.com/user/project/members/)
* [Create a new merge request](https://docs.gitlab.com/user/project/merge_requests/creating_merge_requests/)
* [Automatically close issues from merge requests](https://docs.gitlab.com/user/project/issues/managing_issues/#closing-issues-automatically)
* [Enable merge request approvals](https://docs.gitlab.com/user/project/merge_requests/approvals/)
* [Set auto-merge](https://docs.gitlab.com/user/project/merge_requests/auto_merge/)

## Test and Deploy

Use the built-in continuous integration in GitLab.

* [Get started with GitLab CI/CD](https://docs.gitlab.com/ci/quick_start/)
* [Analyze your code for known vulnerabilities with Static Application Security Testing (SAST)](https://docs.gitlab.com/user/application_security/sast/)
* [Deploy to Kubernetes, Amazon EC2, or Amazon ECS using Auto Deploy](https://docs.gitlab.com/topics/autodevops/requirements/)
* [Use pull-based deployments for improved Kubernetes management](https://docs.gitlab.com/user/clusters/agent/)
* [Set up protected environments](https://docs.gitlab.com/ci/environments/protected_environments/)

***

# Editing this README

When you're ready to make this README your own, just edit this file and use the handy template below (or feel free to structure it however you want - this is just a starting point!). Thanks to [makeareadme.com](https://www.makeareadme.com/) for this template.

## Suggestions for a good README

Every project is different, so consider which of these sections apply to yours. The sections used in the template are suggestions for most open source projects. Also keep in mind that while a README can be too long and detailed, too long is better than too short. If you think your README is too long, consider utilizing another form of documentation rather than cutting out information.

## Name
Choose a self-explaining name for your project.

## Description
Let people know what your project can do specifically. Provide context and add a link to any reference visitors might be unfamiliar with. A list of Features or a Background subsection can also be added here. If there are alternatives to your project, this is a good place to list differentiating factors.

## Badges
On some READMEs, you may see small images that convey metadata, such as whether or not all the tests are passing for the project. You can use Shields to add some to your README. Many services also have instructions for adding a badge.

## Visuals
Depending on what you are making, it can be a good idea to include screenshots or even a video (you'll frequently see GIFs rather than actual videos). Tools like ttygif can help, but check out Asciinema for a more sophisticated method.

## Installation
Within a particular ecosystem, there may be a common way of installing things, such as using Yarn, NuGet, or Homebrew. However, consider the possibility that whoever is reading your README is a novice and would like more guidance. Listing specific steps helps remove ambiguity and gets people to using your project as quickly as possible. If it only runs in a specific context like a particular programming language version or operating system or has dependencies that have to be installed manually, also add a Requirements subsection.

## Usage
Use examples liberally, and show the expected output if you can. It's helpful to have inline the smallest example of usage that you can demonstrate, while providing links to more sophisticated examples if they are too long to reasonably include in the README.

## Support
Tell people where they can go to for help. It can be any combination of an issue tracker, a chat room, an email address, etc.

## Roadmap
If you have ideas for releases in the future, it is a good idea to list them in the README.

## Contributing
State if you are open to contributions and what your requirements are for accepting them.

For people who want to make changes to your project, it's helpful to have some documentation on how to get started. Perhaps there is a script that they should run or some environment variables that they need to set. Make these steps explicit. These instructions could also be useful to your future self.

You can also document commands to lint the code or run tests. These steps help to ensure high code quality and reduce the likelihood that the changes inadvertently break something. Having instructions for running tests is especially helpful if it requires external setup, such as starting a Selenium server for testing in a browser.

## Authors and acknowledgment
Show your appreciation to those who have contributed to the project.

## License
For open source projects, say how it is licensed.

## Project status
If you have run out of energy or time for your project, put a note at the top of the README saying that development has slowed down or stopped completely. Someone may choose to fork your project or volunteer to step in as a maintainer or owner, allowing your project to keep going. You can also make an explicit request for maintainers.


## 画面の共通通知

画面の操作結果・取得失敗・警告は `src/components/ui/notification-banner.tsx` のNotificationBannerに統一する。ページのタイトル・ヘッダー直下を基本位置とし、ログイン画面はフォーム上部に置く。添付上限の警告は添付欄に表示する。

- typeはerror / success / warning / info。表示色、余白、ダークモード、右端の「×」は共通部品で管理する。
- メッセージはchildrenへ文字列で渡す。エラー・警告にはalert、それ以外にはstatusの読み上げ属性を付ける。
- 閉じるボタンは「通知を閉じる」という操作名を持ち、キーボードでも利用可能。フォーム送信を起こさず、自動消去もしない。
- 書き換え可能な通知状態にはonCloseを渡して通知状態を空にする。同じ操作を再実行したとき、同じ文面でも再表示できる。
- 取得エラーなど読み取り専用の通知は部品内で閉じた状態を保持する。親の通常再描画では復活しない。内容・種別が変わるか、一度通知が取り除かれてから再表示された場合は新しい通知になる。取得処理は再取得開始時にエラーを解除する。
- 通知へスクロールする必要がある場合はautoScrollを指定する。既存画面のスクロール処理との重複は避ける。
- 閉じても入力値・項目別エラー・保存処理・利用制限は変えない。項目直下の入力検証やCronの検証プレビュー、通知一覧の業務データはページ通知とは別に維持する。

2026年9月23日: ログイン、トップ、アラート、在庫、ユーザー・端末・サービス・在庫品目・通知経路・バッチ定義・スケジュールの各既存通知、およびバッチ/通知共通設定を共通部品へ移行した。


## フォーム同期と画面読み込み

バッチ定義・スケジュール・品目・端末の編集フォームは、取得データの参照が変わった場合に限り編集状態を同期する。Effect経由の同期的な状態更新は使用せず、取得元との比較で更新を制限する。通常の再描画で編集中の値を初期化しない。スケジュール新規作成時は候補一覧取得後に先頭の定義・端末を初期選択する。

Appの画面コンポーネントはReact.lazyで個別に読み込み、Suspenseで読み込み中表示を行う。画面追加時もページ集約モジュールからの一括読み込みを避け、対象画面モジュールを直接指定する。チャンクサイズ警告のしきい値を緩和して隠す設定は行っていない。

2026年9月23日: 上記4フォームのESLint指摘を解消。変更対象5ファイルのESLintは0件、本番ビルドは警告なし。最大JavaScriptチャンクは約725kBから約378kBへ縮小。本番プレビューと模擬APIで4フォームの取得値反映・編集保持・ID変更、およびスケジュール新規作成の初期選択を確認した。

## フロントエンドの検証

```shell
npm ci
npx playwright install chromium
npm run test:unit
npm run lint
npm run build
npm run test:e2e -- --workers=2
```

`test:e2e` はテストコードの型検査後にChromiumで画面の回帰テストを実行する。
バックエンドとエージェントの起動、実ユーザーの認証情報は不要。
HTTP APIを模擬応答に置き換え、未定義のAPI呼び出しとブラウザの未捕捉例外はテスト失敗として扱う。
実際のバックエンドとの結合試験やCORS設定の検証とは区別する。

* テスト用Viteは `http://localhost:5175` で起動し、終了時に停止する。既存の5173番サーバーは使用しない。
* 5175番が使用中の場合は起動エラーにする。別プロセスへの接続や別ポートへの自動変更は行わない。
* 画面の基本操作、ログイン失敗時の通知・再試行、取得失敗、編集保持、権限制御、期限切れを確認する。
* WebSocketは模擬接続で再接続と明示的切断を検証する。
* HTMLレポートとスクリーンショットは `output/playwright/` 配下に保存する。

ESLintは手書きコードを対象とし、生成クライアントと検証成果物は除外する。
Contextとフックを同居させる既存モジュールなどはFast Refreshの単一公開ルールのみ対象外とする。
それらのモジュールを変更した際はViteのモジュール再読み込みが発生する場合がある。
React Hooksの規則は引き続き検証する。外部から使用されていないUIバリアント定数は公開しない。

初回のAPI読み込みは既存のタイマー方式に揃え、Effect終了時に未実行の予約を解除する。
WebSocketは重複接続を防ぎ、明示的切断・アンマウント後の再接続と古い接続からの状態更新を抑止する。

## 定数定義

共通方針 `08_定数定義方針.md` に従い、固定値は使用範囲に合う専用ファイルで管理する。

- 認証ストレージキー、API接続先、共通ページング、品目マスタの状態選択肢は `src/constants/` に置く。
- 複数のマスタ画面が使う通知設定は `src/pages/Master/notification.constants.ts`、ユーザーロール表示は `src/pages/Master/Users/user.constants.ts` に置く。
- 機能・部品固有の選択肢、表示ラベル、初期値は対象階層の `*.constants.ts` または `*.constants.tsx` に置く。JSXを含む定義にはtsxを使用する。
- 同じ意味の定義を再作成せず、使用範囲が広がった場合は共通の親階層へ移す。通知設定の業務種別と画面通知の表示種別は混同しない。
- APIクライアント、React Context、フック、状態更新処理は定数ファイルに移さない。生成クライアントは直接編集しない。
- 接続先や保存キー、状態コード、選択肢の順序を変える場合は、既存の認証・表示・ページングの回帰テストも確認する。

## 共通日時表示・APIエラー処理

- API日時の表示は`src/lib/dateTime.ts`の`formatJapanDateTime`を使用する。既存の`src/lib/utils.ts`からの再公開も維持する。
- オフセット付き日時は日本時間へ変換し、オフセットなしのAPI日時はBEと同じ日本時間として解釈する。ブラウザのタイムゾーンには依存しない。
- 既定はミリ秒付き。ログ・APIキー・Agent等は`milliseconds: false`で秒まで表示できる。未設定・不正日時は既定で`-`、必要なら`emptyValue`で置き換える。
- 日付のみの値はそのまま保持する。車検満了日、カレンダー計算、datetime-local入力・送信変換を表示用関数に置き換えない。
- APIエラーは`src/lib/apiError.ts`の`getApiErrorMessage`・`getApiErrorStatus`・`getApiFieldErrors`で取得する。Axiosのresponse.dataと生成SDKが直接返すエラー本文を吸収する。
- 画面固有の代替文言、400・409等の業務分岐、項目名の対応付けは各画面に残す。ログインや再認証の制御は変更しない。
- 生のError.message・stack・request・config・rejectedValueを表示しない。空白文言・不正な応答・HTML応答は画面指定の代替文言へ戻す。
- 通知の配置・入力保持・モーダル内外の使い分けは既存のNotificationBannerの方針を維持する。

共通関数は追加ライブラリなしでNode.js 24の組み込みテストにより確認する。
`npm run test:unit`はCIの`frontend_unit_test`でも実行し、既存のLint・ビルド・Playwright回帰テストと併用する。

## 共通の非同期取得

- 通常のHTTP取得は`useAsyncResource`を使い、データ・読み込み・エラー・再取得を共用する。loadはモジュール関数またはuseCallbackで参照を安定させる。
- データと通知の反映に固有処理があるフォーム・APIキー管理は`useAsyncRequest`を使う。異なる取得対象には別の取得レーンを用意する。
- 新しい取得開始、条件変更、アンマウントで以前の取得を中止する。AbortSignal非対応の処理でも古い結果・エラー・終了通知は反映しない。
- 対応するHTTPクライアントにはAbortSignalも渡す。取得用のキャンセルを保存・入出庫などの更新処理の中断へ流用しない。
- APIログ・実行ログの検索では行・次ページ有無・総件数を取得開始時にクリアする。通常一覧の保持、実行状態取得失敗時のクリア等は各用途の指定を維持する。
- API固有エラーを表示するか、画面の固定文言にするかは呼出元で指定する。
- 認証更新、通知ポーリングと既読処理の連動、WebSocket、原本プレビュー、複数段の業務手順を持つ取得は固有制御を維持する。単純な共通フックへ機械的に置き換えない。
- 純粋な取得レーンの競合・キャンセル処理はNode単体テスト、画面への適用は既存のPlaywright回帰テストで確認する。
