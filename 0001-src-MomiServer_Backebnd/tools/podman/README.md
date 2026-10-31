# Docker to Podman Migration Report

作成日: 2026-08-17

## 1. 棚卸し結果

### 実行環境

| 項目 | 結果 |
|---|---|
| Docker CLI | 29.4.3 |
| Docker Server | Docker Desktop 4.74.0 / Engine 29.4.3 |
| Docker context | `desktop-linux` |
| Podman 接続 | Docker 互換 API context `podman-machine-default` |
| Podman Server | 6.0.2 |
| Podman OS | Fedora on WSL2 |
| Podman CLI | Windows PATH では未検出 |

### Docker Compose

現在の Docker context では稼働中の Compose プロジェクトはありません。

### Dockerfile / CI/CD / systemd

この作業フォルダには移行対象リポジトリのファイルが存在しませんでした。そのため、Dockerfile、Compose ファイル、CI/CD 定義、systemd サービス定義のソース修正は未実施です。

### Docker コンテナ

| 名前 | イメージ | 状態 | ポート | ボリューム | ネットワーク |
|---|---|---|---|---|---|
| `zunda-db` | `zunda-postgres` | 停止済み | `5432:5432` | `zunda-pgdata:/var/lib/postgresql/data` | `bridge` |

### Docker イメージ

移行対象として `zunda-postgres:latest` を確認しました。イメージ manifest は OCI 形式です。

### Docker ボリューム

| 名前 | 用途 |
|---|---|
| `zunda-pgdata` | PostgreSQL データディレクトリ |

### Docker ネットワーク

Docker 側は標準ネットワークのみです。

| 名前 | Driver |
|---|---|
| `bridge` | bridge |
| `host` | host |
| `none` | null |

## 2. 移行方針

1. Docker 側の既存コンテナ・イメージ・ボリュームは削除しない。
2. DB ボリュームを停止済み状態で tar バックアップする。
3. Docker イメージを `docker save` で退避し、Podman 側へ `load` する。
4. Podman 側に同名ボリュームを作成し、バックアップを復元する。
5. Podman 側で起動し、DB readiness、SQL 接続、外部ポート、ログ、再起動、永続化を検証する。
6. 本番 Linux 環境では Docker Compose ではなく Quadlet で systemd 管理する。
7. 開発環境では Podman + `podman compose` または `podman-compose` を利用する。
8. Dockerfile は OCI 互換を維持し、Docker 固有機能が見つかった場合のみ最小修正する。

## 3. 実施内容

### バックアップ

| 対象 | 出力 |
|---|---|
| Docker volume `zunda-pgdata` | `zunda-pgdata-backup-20260817.tar.gz` |
| Docker image `zunda-postgres:latest` | `zunda-postgres-image-20260817.tar` |

### Podman 側への移行

| 項目 | 結果 |
|---|---|
| イメージ load | 成功 |
| ボリューム作成 | `zunda-pgdata` 作成 |
| ボリューム復元 | 成功 |
| コンテナ作成/起動 | `zunda-db` 起動 |

## 4. 注意点

### Podman bridge ネットワーク

Podman machine の通常 bridge ネットワークでは、netavark/nftables エラーによりコンテナ起動が失敗しました。

今回の Windows + Podman machine 環境では `--network host` で起動し、`127.0.0.1:5432` への疎通を確認しました。本番 Linux 環境では bridge ネットワークまたは Quadlet の `PublishPort=5432:5432` を基本とし、同様の netavark エラーが出る場合は Podman machine / WSL2 / nftables 側の修復が必要です。

### SELinux ラベル

Docker 側のマウント mode は `z` でした。今回の Podman machine は Fedora on WSL2 で SELinux ラベルの実運用検証は対象外です。本番 Linux で bind mount を使う場合は、専有利用なら `:Z`、共有利用なら `:z` を付けてください。named volume の場合は通常ラベル指定は不要です。

### rootless / UID / GID

今回の Podman API は rootful storage `/var/lib/containers/storage` を利用していました。本番を rootless にする場合は、PostgreSQL の実行 UID/GID と volume 所有権を事前確認してください。

## 5. 検証結果

| 検証 | 結果 |
|---|---|
| Podman コンテナ起動 | 成功 |
| `pg_isready` | 成功 |
| SQL 接続 | 成功 |
| Windows 側 `127.0.0.1:5432` 疎通 | 成功 |
| コンテナ再起動 | 成功 |
| 再起動後の DB 接続 | 成功 |
| ログ確認 | PostgreSQL ready を確認 |
| 自動起動 | 現コンテナに restart policy `always` を設定 |

## 6. 未完了 / 次アクション

1. 移行対象リポジトリがある場合は、その場所で Dockerfile、Compose、CI/CD、systemd 定義を確認して Podman 互換修正を行う。
2. 本番ホストに Quadlet ファイルを配置し、`systemctl --user` または system systemd で有効化する。
3. Podman machine の bridge ネットワークを利用したい場合は、netavark/nftables エラーを解消して `PublishPort=5432:5432` 構成で再検証する。
4. DB パスワードなどの値は Quadlet へ直接書かず、運用環境では secret または env file に移す。

