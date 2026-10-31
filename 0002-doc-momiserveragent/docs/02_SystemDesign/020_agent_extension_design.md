# 拡張設計

## 基本方針

もみサーバエージェントは、実行できる処理や稼働端末の特徴を管理できるようにします。

バッチ実行要求は、Capability、Label、タグをもとに実行可能なエージェントへ割り当てます。

モバイル端末は通常エージェントとは別のCapabilityとして扱い、ジョブ実行よりも端末情報送信、通知、軽量タスクを中心に扱います。

## Capability

Capabilityは、エージェントが実行できる機能を表します。

| Capability | 内容 | Phase |
| --- | --- | --- |
| BatchExecution | バックエンドから通知されたバッチ実行要求を処理できます。 | Phase1 |
| DeviceStatusRecord | CPU温度、CPU使用率、Memory、HDD/SSD容量などを記録できます。 | Phase1 |
| DeviceStatusSend | 記録した端末状態をもみサーバへ送信できます。 | Phase1 |
| MobileSync | モバイル端末情報と状態を同期できます。 | Phase2 |
| MobileNotify | モバイル端末へ通知できます。 | Phase2 |
| Java | Javaジョブを実行できます。 | Phase2 |
| Python | Pythonジョブを実行できます。 | Phase2 |
| Shell | Shellジョブを実行できます。 | Phase2 |
| Docker | Dockerジョブを実行できます。 | Phase2 |
| PowerShell | PowerShellジョブを実行できます。 | Phase2 |
| Node.js | Node.jsジョブを実行できます。 | Phase2 |
| Playwright | Playwrightジョブを実行できます。 | Phase2 |
| MCP | MCP連携ジョブを実行できます。 | Phase2 |
| AI | AIを利用するジョブを実行できます。 | Phase2 |

## Label

Labelは、ジョブ実行先の条件指定に使用します。

| Label例 | 内容 |
| --- | --- |
| Location=Home | 自宅環境に配置された端末であることを表します。 |
| GPU=True | GPUを搭載している端末であることを表します。 |
| Windows=True | Windows端末であることを表します。 |
| Linux=True | Linux端末であることを表します。 |
| MacOS=True | macOS端末であることを表します。 |
| Mobile=True | モバイル端末であることを表します。 |
| Android=True | Android端末であることを表します。 |
| iOS=True | iOS端末であることを表します。 |
| Docker=True | Dockerを利用できる端末であることを表します。 |
| SSD=True | SSDを搭載している端末であることを表します。 |

## タグ

タグは、検索、分類、運用管理のために使用します。

| タグ例 | 用途 |
| --- | --- |
| Garage | 設置場所で分類します。 |
| MainServer | 主要端末として分類します。 |
| Backup | バックアップ用途の端末として分類します。 |
| NAS | ストレージ用途の端末として分類します。 |
| GPU | GPU搭載端末として分類します。 |
| Development | 開発用途の端末として分類します。 |
| Mobile | モバイル端末として分類します。 |

## Plugin構成

Pluginは、エージェント本体を変更せずに実行方式や管理対象を追加するために使用します。

```text
Agent
|
+-- Batch Execution Plugin
+-- Device Status Plugin
+-- Mobile Plugin
+-- Java Plugin
+-- Python Plugin
+-- Shell Plugin
+-- Docker Plugin
+-- Playwright Plugin
+-- MCP Plugin
+-- UniFi Plugin
+-- Minecraft Plugin
+-- AI Plugin
```

## Pluginの責務

| 責務 | 内容 |
| --- | --- |
| 実行可否判定 | バッチ実行要求、OS、Capability、Labelをもとに実行できるか判定します。 |
| バッチ実行 | Pluginが担当するバッチ処理を実行します。 |
| ログ返却 | stdout、stderr、ExitCode、実行時間を共通形式で返却します。 |
| メタ情報提供 | Plugin名、バージョン、対応Capabilityを提供します。 |

## バッチ実行割当方針

| 条件 | 判定内容 |
| --- | --- |
| Capability | バッチ実行要求が要求する実行機能をエージェントが持つこと。 |
| Label | バッチ実行要求が指定する条件にエージェントが一致すること。 |
| 接続状態 | エージェントがOnlineであること。 |
| 実行余力 | 最大同時実行数、CPU、Memoryの制限内であること。 |
| タグ | 管理者が指定した分類条件に一致すること。 |

## モバイル割当方針

| 条件 | 判定内容 |
| --- | --- |
| MobileSync | 端末情報や接続状態の同期を許可します。 |
| MobileNotify | 通知配信を許可します。 |
| Mobile=True | モバイル端末向け処理だけを割り当てます。 |
| Android=True | Android対応時にAndroid向け処理を割り当てます。 |
| iOS=True | iOS対応時にiOS向け処理を割り当てます。 |
