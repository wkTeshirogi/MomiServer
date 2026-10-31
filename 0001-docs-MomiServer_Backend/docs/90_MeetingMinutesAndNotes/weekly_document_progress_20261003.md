# 週次ドキュメント更新メモ 2026/10/03

## 概要

FR-0069のPhase 1管理範囲と完了条件を確定し、Gatewayのバージョン状態判定・OpenAPI認証表示・公開制御の実装に合わせて文書を更新した。本メモは汎用Markdownの作業ログテンプレートに基づく。前日までの進捗メモは当時の記録として保持する。

後続作業でロール単位の認可方針とGateway完了条件を整理し、隔離したPostgreSQL 17・Java 25で統合テストを完了した。

## 作業内容

| 対象 | 変更内容 | 結果 |
| --- | --- | --- |
| 要件 | API経路は実装から生成し、Phase 1はAgent・IoTの `v1` 公開状態に限定 | FR-0069に対象・除外・完了条件を記載 |
| 基本設計 | 未実装版、active、deprecated、retired、DB障害の応答を分類 | `m_api_versions` と認証境界の関係を記載 |
| API・運用 | OpenAPI公開設定、操作ごとの認証表示、状態変更手順 | API索引・設定管理へ反映 |
| 進捗 | 旧「管理API未確認」の説明をPhase 1の範囲へ修正 | 管理画面・編集APIは今回の対象外 |
| 認可方針 | Webの利用可否はロール単位とし、ユーザー個人単位の許可設定を対象外化 | 画面・API追加時に認可とテストを更新 |
| 回帰テスト | 実JWT・Agent Bearer・IoTキーの境界、DB公開状態、OpenAPI公開設定を検証 | Gateway20件・OpenAPI2件成功 |
| 不明経路 | Spring MVCの未存在例外が500に変換される不具合を修正 | 共通形式の404で応答 |
| 完了判定 | FR-0066～FR-0069を現行Phase 1範囲で再判定 | Gateway関連を実装済みへ更新 |

## 変更したもの

- [API統合管理要件](../01_RequirementsDefinition/FR/0069.md)
- [Gateway基本設計](../02_SystemDesign/API%20Gateway/0001_gateway_basic_design.md)
- [API索引](../05_API_Design/000_index.md)
- [設定管理](../07_OperationsDesign/0002_configuration_management.md)
- [Phase 1作成物一覧](../00_ProjectOverview/010_phase1_deliverables.md)
- [Gatewayルーティング要件](../01_RequirementsDefinition/FR/0066.md)
- [Gateway認証要件](../01_RequirementsDefinition/FR/0067.md)
- [レート制限要件](../01_RequirementsDefinition/FR/0068.md)
- [Gatewayテスト設計](../08_TestDesign/0005_gateway_test.md)
- [認可設計](../11_SecurityDesign/0002_authorization.md)
- [議事録・メモ索引](000_index.md)

## 確認したこと

- Gateway・OpenAPI関連の単体テスト16件は成功した。
- 変更Javaコードと統合テストの直接コンパイルは成功した。
- MkDocs strictビルドは成功した。
- Gradleはテスト開始前にloopback接続エラーで停止した。
- DBを使う統合テストはPostgreSQL `localhost:5432` に接続できず、Spring Context起動前に停止した。

上記は初回検証の記録。後続作業ではWSL経由の専用コンテナで次の結果を確認した。

- 空の検証DBに26マイグレーションを適用し、jOOQ生成・Javaコンパイルが成功した。
- 全JUnitは199件中198件成功、失敗0件、Mosquitto連携1件スキップだった。
- Gateway統合テスト20件、OpenAPI公開・非公開の統合テスト各1件が成功した。
- Checkstyleは追加・更新したテストの違反0件。全体には既存コード・生成コードの違反が残る。
- 変更MarkdownのlintとMkDocs strictビルドが成功した。全体lintでは対象外の既存違反12件を検出した。
- 検証結果はバックエンドの `build/reports/tests/test/index.html` と `build/gateway-final-verification.log` に保存した。

## 発生した問題

| 問題 | 原因 | 対応 |
| --- | --- | --- |
| 初回GradleでJUnitを実行できない | 実行環境でloopback接続を確立できない | 後続作業のJava 25コンテナでGradle実行が成功 |
| 初回DB統合テストを完了できない | ローカルPostgreSQLが待ち受けていない | 専用PostgreSQL 17コンテナで統合テストが成功 |
| 未定義経路が500になる | 共通のExceptionハンドラーがSpring MVCの未存在例外を捕捉 | 未存在例外用ハンドラーを追加し404の回帰確認 |

## 残課題

- GitLab上のCI結果は未取得。既存のJUnitジョブで今回の統合テストを継続実行する。
- リポジトリ全体の既存Checkstyle違反と対象外Markdown違反12件の整理は別作業とする。
- `m_external_clients` のクライアント別許可バージョンは将来対応とし、Agent専用認証と整合する識別モデルを別途設計する。
- ADMIN限定のSwagger UIをブラウザから利用する場合は、JWTを付与できる導線が必要となる。

## 次のアクション

- GatewayのPhase 1は自動テスト成功に基づき完了判定を更新した。画面・API追加時に仕様・認可・回帰テストを見直す。
- 実際のGitLab CIでも今回の検証結果を確認する。

## フロントエンドのエラー対応

後続の現状確認で検出したESLintエラー47件・警告5件と画面テスト4件の失敗を修正した。
作業ブランチと既存の未コミット変更は維持し、バックエンド・エージェントの実装は変更していない。

| 対象 | 原因 | 対応 |
| --- | --- | --- |
| ESLint | 生成コード・検証成果物の検査、既存の混在公開、同期状態更新・依存関係 | 検査範囲を整理し、初回取得の予約解除・依存関係・UI定数公開を修正 |
| WebSocket | 接続関数の自己参照と切断後の再接続 | 名前付き接続関数、重複接続防止、古い接続の無視、切断時の予約解除 |
| ログイン通知 | 生成クライアントのエラーをAxios形式として判別 | HTTP応答から400・401・その他のエラーを判別 |
| バッチ・端末取得 | 取得失敗を空データとして扱う経路が存在 | 生成クライアントの例外化を指定し、既存の画面通知へ反映 |
| トップの詳細リンク | 旧URLを参照 | サービスステータス詳細の現行URLへ変更 |
| 画面テスト | Windowsの起動方法、5173・5175の混在、実認証への依存 | 専用Viteと模擬APIへ統一し、現行UIに合わせて検証 |

### 検証範囲

- フロントエンドの全体Lintと本番ビルド。
- 既存の画面テスト4件と、エラー表示・再試行・通知取得・編集保持・WebSocket・ロール・期限切れの回帰確認。
- デスクトップと390px幅のモバイルでログインエラーの表示を確認する。
- テストAPIは模擬応答のみを使用し、稼働中のDBと通知既読状態は変更しない。
- 実際のバックエンド・エージェントとの結合試験とCORS設定は今回の検証対象外。

### 注意事項

- Context等の既存の混在公開モジュールはFast Refreshの単一公開ルールのみ対象外。Hooks規則は維持する。
- テスト起動時に専用ポート5175が使用中の場合は停止し、他プロセスへ接続しない。
- Windowsの制限付き実行環境ではテスト用サーバーの終了処理が阻害された。専用プロセスだけを確認して終了した。
- バックエンド全体の既存Checkstyle違反と対象外Markdown違反は上記の別作業方針を維持する。

### 検証結果

- `npm run lint`: エラー0件・警告0件。
- `npm run build`: TypeScript検査とVite本番ビルド成功。
- `npm run test:e2e -- --workers=2 --max-failures=1`: テストコード型検査成功、14件成功・失敗0件、20.1秒。
- デスクトップ・モバイルのスクリーンショットで通知の重なりと画面外へのはみ出しがないことを確認した。
- 終了可能な実行環境での再試験では、テスト専用Viteも自動終了した。
- 本メモのMarkdownlintとMkDocs strictビルドは成功。
- フロントエンドREADMEの追記部分にlint違反なし。既存テンプレート・既存記述の書式違反は対象外として保持。
- バックエンド・エージェントは前回試験から実装変更なし。今回の再試験はフロントエンドに限定した。

検証手順はフロントエンドのREADME、画面試験のレポートは `output/playwright/report/index.html` に記録した。
