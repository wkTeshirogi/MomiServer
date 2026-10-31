# 品質チェック

## Checkstyle

| 項目 | 内容 |
| --- | --- |
| 利用ツール | Checkstyle |
| レポート | XML、HTML |
| 失敗時の扱い | ビルド失敗にはしない |

## テスト

| 項目 | 内容 |
| --- | --- |
| テストランナー | JUnit Platform |
| Spring Test | 利用 |
| Spring Security Test | 利用 |
| Spring Batch Test | 利用 |
| Testcontainers | PostgreSQLで利用 |

## 静的解析

SonarQubeに連携可能な構成とする。トークンは環境変数で管理する。
