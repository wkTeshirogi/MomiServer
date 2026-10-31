# ビルド・依存管理

## ビルド

| 項目 | 内容 |
| --- | --- |
| ビルドツール | Gradle |
| Java Toolchain | Java 25 |
| 依存管理 | Spring Dependency Management |
| リポジトリ | Maven Central |

## 主要プラグイン

| 分類 | 用途 |
| --- | --- |
| Spring Boot | アプリケーション起動・パッケージング |
| Checkstyle | コードスタイル確認 |
| SonarQube | 静的解析 |
| jOOQ | DBスキーマからのコード生成 |

## jOOQ

- PostgreSQLスキーマを元にコード生成する。
- 生成結果はビルド出力配下へ配置する。
- スキーマ変更後はコード生成を実行する。
