# 共通Clockテスト

## 対象

| No | 対象 | 観点 |
| --- | --- | --- |
| 1 | 共通時計 | 日本時間、現在日時をキャッシュしないこと、固定時計での再現性 |
| 2 | 認証・個人設定 | 更新日時、JWTの発行・検証、セッション期限の保存・比較 |
| 3 | 既存機能 | バッチ、通知、Agent、在庫、車両、監視の回帰 |
| 4 | DB・実行環境 | UTCのJVM・DBでも業務時刻が日本時間で一致すること |

## 確認項目

- 共通ClockとCron・JDBCセッションのzoneが一致すること。
- Instantの絶対時刻とLocalDateの業務日付を混同しないこと。
- 取得方法の回帰防止テストがプロダクションソースを走査すること。
- 実行環境に依存する既存スキップを、Clock修正の成功扱いに含めないこと。

## テストケース

| No | テスト内容 | 前提条件 | 入力 | 期待結果 |
| --- | --- | --- | --- | --- |
| 1 | Factory・Bean | システム時計 | 生成したClock | zoneはAsia/Tokyo。取得時刻は呼出し前後の範囲 |
| 2 | 日付境界 | 固定Clock | 2026-10-24T15:00Z | 日本時間は25日0時、UTCは24日、Instantは同じ |
| 3 | 個人設定更新 | jOOQ MockConnection | 表示名・テーマ・パスワードの更新 | 全3操作で固定時計の同じ更新日時をバインド |
| 4 | エラー応答 | 固定Clock | 共通応答の生成 | 固定した日本時間と+09:00を返す |
| 5 | JWT | 2000年の固定Clock | 発行・解析、301秒進めたClock | 同一Clockでは有効、期限300秒を超えると無効 |
| 6 | DBセッション | UTCのJVM・PostgreSQLサーバー | Hikari経由のSHOW TimeZoneとlocaltimestamp | セッションはAsia/Tokyo、アプリ時刻との差は5秒以内 |
| 7 | 認証セッション | 専用DB・トランザクション | 未来のInstantで作成、失効 | 日本時間のexpires_atと一致し、有効から失効へ遷移 |
| 8 | 方針チェック | mainソース | ASTによる呼出し・Cron走査 | 引数なしnow、環境依存zone、個別時計生成、zone未指定Cronがない |

## 正常系

| No | 観点 | 期待結果 |
| --- | --- | --- |
| 1 | 全機能の既存試験 | Clockの依存変更後もコンパイル・JUnitが成功 |
| 2 | 状態判定 | 固定時刻のHeartbeatに基づきonline・offline等を判定 |
| 3 | SQL既定時刻 | アプリの日本時間とDBセッションのローカル日時が一致 |

## 異常系

| No | 条件 | 期待結果 |
| --- | --- | --- |
| 1 | JWT期限超過 | 有効と判定しない |
| 2 | セッション失効 | active判定はfalse |
| 3 | 方針違反の再追加 | 方針チェックのテストが失敗 |

## 確認結果

結果は[今回の更新記録](../90_MeetingMinutesAndNotes/weekly_document_progress_20261024.md)へ集約する。
JUnit・JaCoCo・SpotBugs成果物はBEのbuild/application-clock-verification-20261024へ保存する。

## 備考

- [共通Clock基本設計](../02_SystemDesign/共通基盤/0003_application_clock.md)に時刻・既存データ互換方針を定義する。
- 実DBは検証用の独立したPostgreSQLを使用し、利用中のDBデータは更新しない。
- SonarQubeサーバーでの再解析結果はローカル静的解析と区別する。
