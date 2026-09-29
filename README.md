# BattSync (WearBatteryMonitor)

**BattSync** は、Android スマートフォン、Wear OS スマートウォッチ、および接続された Bluetooth イヤホン等のバッテリー残量・充電状態をリアルタイムで相互監視・可視化・永続化するクロスデバイスバッテリーモニターアプリです。

---

## 🌟 主な機能 (Key Features)

- **📱 マルチデバイス統合バッテリー監視**:
  スマートフォン本体、ペアリングされた Wear OS スマートウォッチ、および接続中の Bluetooth イヤホンのバッテリー残量と充電状態を 1 つの画面でまとめて確認できます。

- **🔄 低遅延クロスデバイス同期**:
  Google Wearable Data Layer API を利用し、スマホとウォッチ間でバックグラウンド自動データ同期を行います。

- **📊 バッテリー消費履歴の可視化**:
  Room データベースに永続化された時系列データをもとに、直近（3時間 / 6時間 / 12時間 / 24時間 / 全期間）の残量変化推移を Jetpack Compose カスタム Canvas による綺麗な折れ線グラフで描画します。

- **⌚ Wear OS プラットフォーム統合 (Tile & Complication)**:
  - **タイル (Tile)**: 端末をスワイプするだけで、スマホのバッテリー残量や推移グラフをサッと確認できます。
  - **コンプリケーション (Complication)**: Watch Face（文字盤）上にスマホのバッテリー残量テキストとアイコンを給送・表示します。

---

## 🛠️ 技術スタック (Tech Stack)

| カテゴリ | 使用技術・ライブラリ |
| :--- | :--- |
| **言語 / 環境** | Kotlin (100%), Modern Android Development (MAD) |
| **UI Framework** | Jetpack Compose (Mobile), Wear Compose (Material 3), ProtoLayout (Tile) |
| **アーキテクチャ** | Repository Pattern (関心の分離), Coroutines / Flow / StateFlow |
| **データベース** | Room Persistence Library (SQLite) |
| **デバイス間通信** | Google Play Services Wearable Data Layer API (`DataClient`, `MessageClient`, `CapabilityClient`) |
| **テスト** | JUnit, MockK (Unit Test), Room In-Memory Test (Instrumented Test) |

---

## 📁 プロジェクト構造 (Project Structure)

```
WearBatteryMonitor/
├── mobile/         # Android スマートフォン用アプリモジュール
│   ├── src/main/   # メイン画面 (Compose), 常駐サービス (BatteryMonitorService), Repository
│   └── src/test/   # データアクセス層の単体テスト (MockK)
├── wear/           # Wear OS スマートウォッチ用アプリモジュール
│   └── src/main/   # ウェア画面 (Wear Compose), タイル (TileService), コンプリケーション
└── docs/           # ドキュメント類
    ├── design/     # システム仕様書 (system_overview.md), クラス図 (class_diagram.puml)
    ├── plans/      # 機能開発・改修計画書
    └── walkthroughs/ # 開発・改修実績レポート
```

---

## 🚀 ビルド・実行手順 (Getting Started)

### 前提条件
- **Android Studio**: Ladybug (2024.2.1) 以降を推奨
- **JDK**: JDK 17 以上
- **対応 SDK**: Min SDK 30 (Android 11 / Wear OS 3.0) 〜 Target SDK 37

### ビルドタスク (Gradle)
プロジェクト直下で以下の Gradle コマンドを実行してビルドを行います。

```bash
# モバイルアプリのデバッグビルド
./gradlew :mobile:assembleDebug

# ウェアアプリのデバッグビルド
./gradlew :wear:assembleDebug

# ユニットテストの実行
./gradlew :mobile:testDebugUnitTest
```

---

## 📖 詳細ドキュメント (Documentation)

プロジェクトの内部設計やアーキテクチャの詳細については、`docs/` ディレクトリ配下のドキュメントをご参照ください。

- **[システム仕様書 & アーキテクチャ解説](docs/design/system_overview.md)**: アプリの全体構成、機能と Android SDK / ライブラリクラスとの対応関係表
- **[PlantUML クラス図](docs/design/class_diagram.puml)**: モジュール構造とリポジトリパターンの定型クラス図
- **[開発・運用ルールガイドライン](docs/development_rules.md)**: AI エージェント (Gemini 等) を利用する際の本プロジェクトの開発フロー、事前 Plan 承認、KDoc コメント、ルール変更履歴などの共同開発取り決め一覧
- **[プライバシーポリシー](docs/PRIVACY_POLICY.md)**: Google Play Console 登録用プライバシーポリシー文書

---

## 📄 ライセンス & プライバシー (License & Privacy)

- **ライセンス**: 本プロジェクトは [Apache License 2.0](LICENSE) のもとで公開・管理されています。
- **プライバシーポリシー**: アプリで取り扱うデータの詳細については [docs/PRIVACY_POLICY.md](docs/PRIVACY_POLICY.md) をご参照ください。
