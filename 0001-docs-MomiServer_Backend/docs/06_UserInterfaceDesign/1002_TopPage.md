# Top Page (ダッシュボード) 画面設計書

## 概要

システム全体の概要を一目で把握できるダッシュボード画面。通知一覧とサーバー状況のサマリーを表示。

---

## 画面構成

### レイアウト構造

```plantuml
@startuml
skinparam componentStyle rectangle
skinparam backgroundColor #FFFFFF

package "Top Page (Dashboard)" {
  [Header Area: 通知一覧] as Header
  [Main Content: サーバー状況] as Main
  [Footer Area: 予備領域] as Footer
}

Header --> Main
Main --> Footer

package "Header Area" {
  [Section Title: 通知一覧] as HTitle
  [Notification Table] as NTable
}

package "Main Content (4 Columns Grid)" {
  [Section Title: 各種サーバー状況] as MTitle
  [Server Card Grid] as SGrid
}

package "Footer Area (Reserved)" {
  [Section Title: ○○○状況] as FTitle
  [Empty Grid] as EGrid
}
@enduml
```

---

## コンポーネント詳細

### 1. 通知一覧セクション

```plantuml
@startuml
skinparam componentStyle rectangle
package "通知一覧 Section" {
  [Section Title] as ST
  [Table Container] as TC
  [Sticky Header] as SH
  [Scrollable Body\n(max-h-150px)] as SB
  [Row x4] as R
}

ST --> TC
TC --> SH
TC --> SB
SB --> R

package "Columns" {
  [時刻\nw-40] as C1
  [緊急度\nw-24] as C2
  [内容\nflex-1] as C3
  [アクション] as C4
}
SH --> C1
SH --> C2
SH --> C3
SH --> C4
@enduml
```

| 要素 | 仕様 |
|------|------|
| セクションタイトル | "通知一覧" (text-xl, font-semibold) |
| テーブル形式 | 固定ヘッダー + 縦スクロール (max-h-150px) |
| カラム | 時刻 / 緊急度 / 内容 / アクション |
| 行ホバー | 背景色変化 (zinc-100/dark:zinc-800) |
| アクション | SendButton "詳細" → InfoModal 表示 |

#### 通知データ (noticeList)

| フィールド | 型 | 例 |
|------------|-----|-----|
| id | number | 1 |
| date | string | "yyyy/mm/dd hh:mm:ss" |
| urgency | string | "緊急" |
| title | string | "タイトルなのだ." |
| content | string | "お兄ちゃんﾌﾞﾁｨ!!" |

#### モーダル連携

```plantuml
@startuml
[SendButton "詳細"] --> [openModal(INFO_MODAL_ID)]
[openModal] --> [InfoModal]
InfoModal : title = data.title + ' (' + data.urgency + ')'
InfoModal : message = data.content
InfoModal : size = 'xl'
@enduml
```

---

### 2. 各種サーバー状況セクション

```plantuml
@startuml
skinparam componentStyle rectangle
package "各種サーバー状況 Section" {
  [Section Title] as ST
  [Responsive Grid\n1/2/4 Columns] as RG
  [Server Card x4] as SC
}

ST --> RG
RG --> SC

package "Server Card Structure" {
  [Card Header\nService Name] as CH
  [Grid 2-Column Metrics] as GM
  [Detail Button] as DB
}

SC --> CH
SC --> GM
SC --> DB

package "Metrics (Key/Value Pairs)" {
  [Status] as M1
  [CPU] as M2
  [Mem] as M3
  [稼働時間] as M4
  [Version] as M5
}
GM --> M1
GM --> M2
GM --> M3
GM --> M4
GM --> M5
@enduml
```

| 要素 | 仕様 |
|------|------|
| セクションタイトル | "各種サーバー状況" |
| レイアウト | レスポンシブグリッド (1col <sm, 2col sm-xl, 4col ≥xl) |
| カード | Card コンポーネント (p-3, border) |

#### サーバーカード構成

| 領域 | 内容 |
|------|------|
| ヘッダー | サービス名 (h3, border-b) |
| メトリクス (2カラムグリッド) | Status / CPU / Mem / 稼働時間 / Version |
| フッター | "詳細" ボタン → `/status/{id}` 遷移 |

#### メトリクス詳細

| ラベル | 値 | 備考 |
|--------|-----|------|
| Status | `server.status` | "● online" 形式 |
| CPU | `server.cpu` | "100%" 形式 |
| Mem | `server.mem` | "100%" 形式 |
| 稼働時間 | `server.worktime` | "dd day hh:mm" |
| Version | `server.version` | "Alpha Zundamochi" 等 |

#### サーバーデータ (servers)

```plantuml
@startuml
class ServerSummary {
  + id: number
  + name: string
  + status: string  // "● online"
  + cpu: string     // "100%"
  + mem: string     // "100%"
  + worktime: string
  + version: string
}

object "7Days To Die" as S1
object "Minecraft JE" as S2
object "CoreKeeper" as S3
object "もっちもち" as S4

S1 : id = 1
S1 : version = "Alpha Zundamochi"
S2 : id = 2
S2 : version = "26.2.1"
S3 : id = 3
S3 : version = "10.10.10"
S4 : id = 4
S4 : version = "MochiMochi"
@enduml
```

---

### 3. 予備セクション (○○○状況)

```plantuml
@startuml
skinparam componentStyle rectangle
package "予備セクション" {
  [Section Title: ○○○状況] as ST
  [Empty Grid\n4 Columns] as EG
}

ST --> EG
note right of EG
  現状は空のグリッド
  将来的な拡張用
end note
@enduml
```

| 要素 | 状態 |
|------|------|
| セクションタイトル | "○○○状況" (プレースホルダー) |
| グリッド | 空 (children なし) |
| 用途 | 将来的な機能追加用予約領域 |

---

## データモデル

### NoticeItem

```plantuml
@startuml
class NoticeItem {
  + id: number
  + date: string
  + urgency: string
  + title: string
  + content: string
}
@enduml
```

### ServerSummaryItem

```plantuml
@startuml
class ServerSummaryItem {
  + id: number
  + name: string
  + status: string
  + cpu: string
  + mem: string
  + worktime: string
  + version: string
}
@enduml
```

---

## 使用コンポーネント

| コンポーネント | 用途 | インポート元 |
|----------------|------|--------------|
| `Card` | サーバーカード, 通知テーブル容器 | `@/components/ui/card` |
| `Button` | 詳細ボタン | `@/components/ui/button` |
| `SendButton` | 通知詳細ボタン | `@/components/ui/send-button` |
| `useModalManager` | モーダル表示制御 | `@/contexts/ModalContext` |
| `INFO_MODAL_ID` | InfoModal 識別子 | `@/components/modals` |

---

## レスポンシブ対応

```plantuml
@startuml
skinparam componentStyle rectangle
package "Responsive Grid" {
  [Mobile < 640px\n1 Column] as M
  [Tablet 640-1280px\n2 Columns] as T
  [Desktop ≥ 1280px\n4 Columns] as D
}

note right of M
  grid-cols-1
end note
note right of T
  sm:grid-cols-2
end note
note right of D
  xl:grid-cols-4
end note
@enduml
```

| ブレークポイント | サーバーカードグリッド | 予備グリッド |
|------------------|------------------------|--------------|
| `< sm` (640px) | 1カラム | 1カラム |
| `sm` - `xl` | 2カラム | 2カラム |
| `≥ xl` (1280px) | 4カラム | 4カラム |

---

## 実装ファイル

```text
src/pages/Top/
└── TopPage.tsx  # 172行
```

### 主要定数

```plantuml
@startuml
package "TopPage Constants" {
  [servers: ServerSummaryItem[4]] as S
  [noticeList: NoticeItem[4]] as N
}
@enduml
```

| 定数 | 型 | 件数 | 用途 |
|------|-----|------|------|
| `servers` | `ServerSummaryItem[]` | 4 | サーバー状況カード |
| `noticeList` | `NoticeItem[]` | 4 | 通知一覧テーブル |

---

## 今後の拡張ポイント

1. **実データ連携**: 定数データ → API/WebSocket から取得
2. **予備セクション活用**: "○○○状況" を具体的な機能 (アラート、リソース、ネットワーク等) に展開
3. **通知フィルタリング**: 緊急度別フィルタ、既読/未読管理
4. **サーバー検索/ソート**: 名前順、ステータス順、負荷順
5. **リアルタイム更新**: WebSocket で通知・サーバー状況を自動更新
