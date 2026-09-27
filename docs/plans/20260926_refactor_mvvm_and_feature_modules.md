# MVVM アーキテクチャ導入および機能別パッケージ分割計画

## 1. 目的と概要

現在の `MainActivity.kt`（約800行）および `BatteryMonitorService.kt` には、UI描画、状態管理、Bluetooth検出、Notification処理、Wearable通信が混在しており、大量の import 文が存在する「神クラス」状態となっています。

本リファクタリングでは、仕様書（`system_overview.md`）の機能一覧と一対一対応する **機能別パッケージ構造（`monitor/`, `sync/`, `ui/`）** へ分割し、**MVVM (Model-View-ViewModel) アーキテクチャ** を適用します。これにより、保守性、テスト容易性、およびコードの見通しを大幅に向上させます。

また、新設・リファクタリングするすべての Kotlin ファイルにおいて、Kotlin 標準のドキュメンテーション規格である **KDoc フォーマット（`/** ... */`）** を適用し、クラス・プロパティ・関数の役割・引数・戻り値を日本語で分かりやすくコメント記述します。

---

## 2. 変更・新設対象コンポーネント

### モバイルモジュール (`:mobile`)

#### 【新設パッケージおよびファイル】
- **`ui/MainUiState.kt`** `[NEW]` : 画面状態（`isLoading`, `phonePercent`, 履歴等）の一元保持データクラス
- **`ui/MainViewModel.kt`** `[NEW]` : 画面状態制御・操作イベント受配用の ViewModel
- **`ui/components/BatterySection.kt`** `[NEW]` : スマホ/ウォッチ用カード UI コンポーネント
- **`ui/components/EarphoneSection.kt`** `[NEW]` : イヤホン用カード UI コンポーネント
- **`ui/components/HistoryGraph.kt`** `[NEW]` : 折れ線グラフ描画 (Canvas) コンポーネント
- **`ui/components/HistoryDetailDialog.kt`** `[NEW]` : 履歴詳細ダイアログコンポーネント
- **`monitor/PhoneBatteryMonitor.kt`** `[NEW]` : スマホバッテリー残量・充電状態の監視クラス
- **`monitor/EarphoneBatteryMonitor.kt`** `[NEW]` : Bluetooth イヤホン残量・接続検知クラス
- **`monitor/NotificationHelper.kt`** `[NEW]` : フォアグラウンド常駐通知の生成・更新クラス
- **`sync/WearDataSyncer.kt`** `[NEW]` : Wear OS との Data Layer 通信・受信管理クラス

#### 【既存ファイルの改修・スリム化】
- **`MainActivity.kt`** `[MODIFY]` : `MainViewModel` と Compose 画面のバインドに限定し、数百行から数十行へ劇的にスリム化
- **`BatteryMonitorService.kt`** `[MODIFY]` : 各 Monitor と Syncer のライフサイクル管理に限定しスリム化

### ドキュメント類 (`docs/`)
- **`docs/design/class_diagram.puml`** `[MODIFY]` : MVVM & 機能別パッケージ構造を反映したクラス図への改訂
- **`docs/design/system_overview.md`** `[MODIFY]` : アーキテクチャ構成および対応クラス表の更新
- **`docs/plans/20260926_refactor_mvvm_and_feature_modules.md`** `[NEW]` : 本改修計画書
- **`docs/walkthroughs/20260926_refactor_mvvm_and_feature_modules.md`** `[NEW]` : 本改修実績のウォークスルー報告書

---

## 3. 実装ステップ

### ステップ 0: 事前テスト・動作検証
- 既存の単体テスト（`BatteryRepositoryTest`）およびインストゥルメンテッドテスト（`BatteryDaoTest`）を実行し、すべて成功することを確認します。

### ステップ 1: 機能別データ収集・監視クラスの作成 (`monitor/`)
- `PhoneBatteryMonitor.kt`, `EarphoneBatteryMonitor.kt`, `NotificationHelper.kt` を作成し、OS インテントおよび Bluetooth からのデータ取得処理を単一責任のクラスへ抽出します。
- 各クラス・メソッド・プロパティに KDoc フォーマットのドキュメントコメント（`@param`, `@return`, `@property` 等）を記述します。

### ステップ 2: 機能別通信クラスの作成 (`sync/`)
- `WearDataSyncer.kt` を作成し、Google Wearable Data Layer API へのデータ送信・受信ロジックを分離します。
- KDoc コメントによる詳細な仕様説明を追記します。

### ステップ 3: UI コンポーネントおよび ViewModel の新設 (`ui/`)
- `MainUiState.kt` および `MainViewModel.kt` を作成し、画面状態を一元管理します。
- `MainActivity.kt` 内に存在していた `BatterySection`, `EarphoneSection`, `HistoryGraph`, `HistoryDetailDialog` を独立した Kotlin ファイルへ分割抽出します。
- UI コンポーネントおよび ViewModel に対し、KDoc フォーマットによるコメントを追加します。

### ステップ 4: `MainActivity.kt` および `BatteryMonitorService.kt` のスリム化
- 分割作成した各クラスおよび `MainViewModel` を組み合わせ、`MainActivity.kt` および `BatteryMonitorService.kt` を最小限のコード量へスリム化します。

### ステップ 5: 自動テスト実行・ビルド・動作検証
- 既存単体テストの全件再実行（4/4 passed）および `:mobile:assembleDebug`, `:wear:assembleDebug` ビルドの正常成功を確認します。

### ステップ 6: ドキュメント類の同期更新とウォークスルー作成
- [class_diagram.puml](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/docs/design/class_diagram.puml) および [system_overview.md](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/docs/design/system_overview.md) を新アーキテクチャに更新します。
- 実績レポート `docs/walkthroughs/20260926_refactor_mvvm_and_feature_modules.md` を作成します。

---

## 4. 検証計画

- **自動テスト検証**: `gradle_build(":mobile:testDebugUnitTest")` で全テストが合格することを確認します。
- **ビルド検証**: `:mobile:assembleDebug` および `:wear:assembleDebug` が成功することを確認します。
- **コード品質・可読性確認**: `MainActivity.kt` 内の不要な import 文および行数が大幅に改善され、新設・改修コードすべてに KDoc コメントが正しく記述されていることを確認します。
