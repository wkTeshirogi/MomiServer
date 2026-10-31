# t_agent_auth_rate_limits

## 概要

バックエンド複数プロセス間で共有する要求回数。
V22マイグレーションで追加。認証契約は[専用認証設計](../../02_SystemDesign/認証/0005_agent_authentication.md)を参照する。

## カラム定義

| No | カラム | 型 | NULL | デフォルト | 制約 | 説明 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | bucket_key | VARCHAR(64) | 不可 | なし | 複合主キー | 接続元IPのSHA-256 |
| 2 | window_minute | BIGINT | 不可 | なし | 複合主キー | UTC epochの分単位窓 |
| 3 | attempts | INTEGER | 不可 | なし | なし | 窓内の要求回数 |

## インデックス

(bucket_key, window_minute)の複合主キー。

## 外部キー

なし。

## 更新制御

UPSERTで原子的に加算。登録処理のトランザクション開始前に記録し、認証失敗でも回数を保持する。1時間より古い窓は清掃する。
