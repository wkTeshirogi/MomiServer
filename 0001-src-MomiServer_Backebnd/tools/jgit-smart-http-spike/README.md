# JGit Smart HTTP技術検証

## 概要

本番Git機能ではなく、JGitを既存のSpring Boot／Tomcat世代へ組み込めるかを確認する独立した検証ビルド。
アプリ本体のGradle設定を適用せず、DB、GitLab、GitHub、既存の認証情報へ接続しない。
通常のアプリビルド・テスト・CIへ自動追加しない。

## 対象

| 項目 | 内容 |
| --- | --- |
| Java | 25 |
| Gradle | BEのwrapper（検証時9.4.1）を使用 |
| Spring Boot | 3.5.0 |
| Tomcat・Jackson | BEのgradle.propertiesのバージョンに合わせる |
| JGit | 7.8.0.202609011348-r。Jakarta Servlet依存を確認した公開安定版 |
| Gitクライアント | PATH上の標準git。テストデータ用プロセスだけで設定・資格情報を渡す |
| 接続先 | 127.0.0.1の動的HTTPSポートと一時bare repositoryのみ |

## 実行手順

Java 25のJAVA_HOMEとGitを用意し、バックエンドのルートから実行する。

```powershell
.\gradlew.bat --no-daemon -p tools/jgit-smart-http-spike test
```

WindowsでJava NIOの`Unable to establish loopback connection`が発生した場合、
今回の環境では専用の一時ソケットディレクトリ指定で解消した。
既存アプリやOS全体の設定は変えず、検証コマンドのプロセスだけに適用する。
以下のパスは必要に応じて、利用環境の短いローカルパスへ変更する。

```powershell
$sockets = 'D:/6000.PROJECTS/MomiServer/.jgit-sockets'
New-Item -ItemType Directory -Path $sockets -Force | Out-Null
$previous = $env:JAVA_TOOL_OPTIONS
try {
    $env:JAVA_TOOL_OPTIONS = "$previous -Djdk.net.unixdomain.tmpdir=$sockets".Trim()
    .\gradlew.bat --no-daemon -p tools/jgit-smart-http-spike test
} finally {
    $env:JAVA_TOOL_OPTIONS = $previous
}
```

Linuxでは、Java 25・Git・CA証明書を用意して同じwrapperを使用する。

```bash
sh gradlew --no-daemon -p tools/jgit-smart-http-spike test
```

依存物未取得の場合はMaven CentralとGradle配布元への接続、または事前キャッシュが必要。
ネットワーク、ストレージ、サービスを本番環境へ切り替えて実行しない。

## 安全性・検証範囲

- keytoolで短期の自己署名証明書を毎回生成し、テスト用クライアントだけで信頼する。TLS検証は無効化しない。
- テストユーザー・トークンを毎回生成し、URL・ソース・レポートへ原文を記録しない。
- ローカル資格情報helper・Gitのユーザー設定・HTTPプロキシ・トレースをテスト用プロセスから分離する。
- 一時worktreeで合成コミット・ローカルpushを行うが、開発リポジトリのcommitやリモートpushは行わない。
- 中断試験では、その試験で起動したGitプロセスと子プロセスだけを停止する。
- 各ケース終了後にサーバーを停止してgit fsckを行い、JUnitの一時領域を回収する。
- 認証・認可・監査は検証用のメモリ実装。MomiServer本体のSecurityConfig・DB・PAT管理は使用しない。
- シンボリックリンク作成不可の環境では該当試験がスキップされる。未検証として区別して報告する。

## 出力と確認結果

| 出力 | 用途 |
| --- | --- |
| build/test-results/test/ | JUnit XML。成功・失敗・スキップ判定の正本 |
| build/reports/tests/test/ | HTML試験結果 |
| build/reports/spike/ | ケース別のref、監査fixture、HTTP要求、環境版、fsck結果 |

上記パスはこの独立プロジェクトの配下。JSONだけで成功と判断せず、JUnit結果と併せて確認する。
ケース別JSONは毎回作り直す。生成物、証明書、秘密鍵、依存キャッシュはGit登録しない。

2026/10/29にWindowsで17件、Ubuntuの一時Linuxコンテナで17件成功した。両環境で失敗・スキップ0件。
詳細な実施結果、対象外範囲、採用判断は、設計書リポジトリの
`docs/08_TestDesign/0017_jgit_smart_http_spike.md`を参照する。

## 備考

運用テンプレート`100_operations_design.md`の対象・手順・確認結果の構成を基に調整した。
Gitサービスの正式実装や本番採用の完了を示すものではない。
