# 実行環境設計

## 基本構成

| 項目 | 内容 |
| --- | --- |
| アプリケーション種別 | Spring Boot Webアプリケーション |
| 既定ポート | 8080 |
| ビルドツール | Gradle |
| Java | Java 25 toolchain |
| 主要フレームワーク | Spring Boot 3.5.0 |
| API形式 | REST API |
| JSON処理 | Jackson |
| DBアクセス | jOOQ |
| DBマイグレーション | Flyway |

## 主要依存

| 分類 | 内容 |
| --- | --- |
| Web | Spring MVC |
| Security | Spring Security |
| Validation | Bean Validation |
| DB | PostgreSQL JDBC、jOOQ |
| Migration | Flyway |
| APIドキュメント | SpringDoc OpenAPI |
| Batch | Spring Batch |
| Monitoring | Spring Actuator |
| WebSocket | Spring WebSocket |
| JWT | JJWT |

## 起動条件

- PostgreSQLへ接続できること
- DBマイグレーションを実行できること
- JWT署名用秘密鍵が設定されていること
- フロントエンドからのアクセス元がCORS許可オリジンに含まれていること
