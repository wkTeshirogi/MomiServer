# バックエンドCI検証設計

## 対象

`090_test_design.md`テンプレートを使用した。
2026/10/22時点のバックエンド `.gitlab-ci.yml`（`c68d104`）を対象とする。
以下は定義上の仕様であり、実際のパイプライン成功を示すものではない。

| No | 対象 | 観点 |
| --- | --- | --- |
| 1 | 単体テスト・DB | マイグレーション、生成コード、テスト、カバレッジ |
| 2 | 静的解析 | MRのSpotBugs、reviewdog、mainのSonarQube |
| 3 | セキュリティ検査 | 依存関係検査と明示起動のZAP |

## 確認項目

ステージ順は `test`、`review`、`analysis`、`security` とする。

| ジョブ | 実行条件 | 主な処理・成果物 |
| --- | --- | --- |
| unit_test | 原則実行。ただしmainのscheduleでRUN_DEPENDENCY_CHECK=trueかつRUN_ZAP_BASELINEがtrue以外なら省略 | [DBテスト](./0004_database_test.md)、JUnit、JaCoCo、クラス・生成コード |
| spotbugs_analysis | merge_request_event | DB準備、CI補助スクリプトのPythonテスト、spotbugsMr、SARIF整形 |
| reviewdog_spotbugs | merge_request_event | 前段のSARIFを検証し、追加行の指摘をMR discussionへ投稿 |
| sonarqube_analysis | mainへのpush | unit_test成果物を利用し、品質ゲート結果を最大300秒待つ |
| dependency_check | mainのscheduleでRUN_DEPENDENCY_CHECK=true | NVD接続診断、依存関係解析、HTML・JSON・SARIFレポート |
| zap_baseline | RUN_ZAP_BASELINE=true、保護されたmain、webまたはschedule | unit_testで作成したbootJarを利用し、ZAP補助スクリプトで検査 |

mainへのpushではunit_test内で `spotbugsSonar` も実行する。
ZAPの起動条件を満たす場合にunit_test内で `bootJar` を実行する。
SonarQubeはJUnit、JaCoCo XML、SpotBugs XML、クラスの存在を確認してから解析する。

## テストケース

| No | テスト内容 | 前提条件 | 入力 | 期待結果 |
| --- | --- | --- | --- | --- |
| 1 | MR解析 | MRパイプライン | コード変更 | SpotBugs解析とreviewdog投稿が分離して実行される |
| 2 | main解析 | mainへのpush | テスト成果物 | SonarQube解析と品質ゲート待機が行われる |
| 3 | 依存関係専用実行 | mainのschedule | RUN_DEPENDENCY_CHECK=true、ZAP無効 | 単体テストを省略して依存関係検査を実行する |
| 4 | ZAP起動 | 保護されたmainのwebまたはschedule | RUN_ZAP_BASELINE=true | JARを作成し、ZAPジョブへ引き渡す |
| 5 | 同時指定 | 保護されたmainのschedule | 両RUN変数=true | 単体テストを省略せず、依存関係検査とZAPを実行する |

## 正常系

| No | 観点 | 期待結果 |
| --- | --- | --- |
| 1 | 認証情報の分離 | SonarQubeはsonarqube-main、reviewdogはreviewdog-mrの環境スコープを使用する |
| 2 | 成果物保存 | 定義されたテスト・解析・ZAPレポートは成否にかかわらず1週間保持する |
| 3 | 依存関係検査の既定値 | DEPENDENCY_CHECK_ENFORCE=falseではreport-onlyプロパティを渡す |

## 異常系

| No | 条件 | 期待結果 |
| --- | --- | --- |
| 1 | 必須成果物の欠落 | SonarQube解析前の存在確認で失敗する |
| 2 | reviewdog配布物の不一致 | アーカイブ・実行ファイルのチェックサム検証で停止する |
| 3 | ENFORCEがtrue/false以外 | 依存関係検査ジョブを失敗にする |
| 4 | NVD事前診断の失敗 | 診断結果を記録し、本体の依存関係解析は試行する |
| 5 | 依存関係レポートが空・欠落 | HTML・JSON・SARIFの存在確認で失敗する |

## 確認結果

| 確認項目 | 結果 | 補足 |
| --- | --- | --- |
| ジョブ条件・成果物 | 定義確認済み | バックエンドCI設定の静的確認 |
| GitLab側の変数・保護設定 | 未確認 | リポジトリの定義だけでは実設定を判断できない |
| 実パイプライン・品質ゲート | 未実行 | 文書更新では外部投稿・スキャンを起動しない |

## 備考

- API機能のテスト設計は[テスト設計一覧](./000_index.md)を参照する。
- 今回の文書検証結果は[週次メモ](../90_MeetingMinutesAndNotes/weekly_document_progress_20261022.md)へ記録する。
