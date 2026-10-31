# ===================================================
# PostgreSQL 開発・テスト用 Dockerfile
# 用途: ローカル開発環境 / CI テスト
# 本番環境には使用しないこと
# ===================================================
FROM postgres:17-alpine

# ロケール・タイムゾーン
ENV LANG=ja_JP.utf8
ENV TZ=Asia/Tokyo

# 接続情報（開発用固定値 / 本番では環境変数で上書き）
ENV POSTGRES_DB=zunda
ENV POSTGRES_USER=zunda_user
ENV POSTGRES_PASSWORD=zunda_pass

# 初期化スクリプト置き場
# このディレクトリに置いた .sql / .sh は初回起動時に自動実行される
COPY ./init/ /docker-entrypoint-initdb.d/

EXPOSE 5432
