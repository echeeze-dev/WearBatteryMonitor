# ウォッチ名・イヤホン名の同調漏れ解消およびサービス連携修正計画

## 1. 目的と原因分析

スマホアプリ画面において、接続されているスマートウォッチおよび Bluetooth イヤホンのデバイス名（端末名）が表示されない問題を解決します。

### 原因分析
1. **サービス層 (`BatteryMonitorService`) での `watchName` 保持漏れ**:
   フォアグラウンドサービス実行中、`BatteryMonitorService` がウォッチからの通信データを受信しているにもかかわらず、`currentWatchName` プロパティが存在せず、受信した端末名を保持していませんでした。
2. **サービスから Activity (`syncFromService`) へのウォッチ名不伝達**:
   `MainActivity` がサービスに接続された際（`syncFromService()`）、スマホやイヤホンの情報のみを同期し、ウォッチの端末名 (`currentWatchName`) を `MainViewModel` へ同期していませんでした。
3. **イヤホンの起動時初期自動検出の不足**:
   アプリ起動時にすでに接続されている Bluetooth イヤホンが存在する場合、`EarphoneBatteryMonitor` からの初期通知が遅れるか反映されない状態になっていました。

---

## 2. 変更対象コンポーネント

### モバイルモジュール (`:mobile`)
- **`BatteryMonitorService.kt`** `[MODIFY]` : `currentWatchName` プロパティの追加、データ受信時の保持、およびノード接続時の端末名保存処理の追加
- **`MainActivity.kt`** `[MODIFY]` : `syncFromService()` にて `currentWatchName` を `MainViewModel` へ同期する処理の追加
- **`monitor/EarphoneBatteryMonitor.kt`** `[MODIFY]` : `startMonitoring()` 実行時に接続済みイヤホン情報 (`updateFromProfile()`) を即時検出・更新する処理の追加

### ドキュメント類 (`docs/`)
- **`docs/plans/20260926_fix_watch_and_earphone_device_name_sync.md`** `[NEW]` : 本不具合の対応計画書
- **`docs/walkthroughs/20260926_fix_watch_and_earphone_device_name_sync.md`** `[NEW]` : 本不具合対応実績のウォークスルー報告書

---

## 3. 実装ステップ

### ステップ 1: `BatteryMonitorService.kt` への `currentWatchName` 追加とデータ同期
- `var currentWatchName: String? = null` プロパティを追加します。
- `WearDataSyncer` の `onWatchBatteryReceived` 受信時、`deviceName` が存在すれば `currentWatchName = deviceName` へ保存します。

### ステップ 2: `MainActivity.kt` でのサービス同期強化
- `syncFromService()` 内において、サービスが保持する `currentWatchName` を `viewModel.updateWatchName(...)` で UI 状態に正しく更新します。

### ステップ 3: `EarphoneBatteryMonitor.kt` の初期接続イヤホン即時検出
- `startMonitoring()` 呼び出し時に `updateFromProfile()` を即座に実行し、すでにペアリング・接続されているイヤホンのデバイス名およびバッテリーレベルを画面に即時通知します。

### ステップ 4: テスト・ビルド検証およびウォークスルー報告書の作成
- 単体テスト（`:mobile:testDebugUnitTest`）およびビルド（`:mobile:assembleDebug`）を実行し、エラーなく成功することを確認します。
- 実績レポート `docs/walkthroughs/20260926_fix_watch_and_earphone_device_name_sync.md` を作成します。

---

## 4. 検証計画

- `gradle_build(":mobile:testDebugUnitTest")` で全テストが合格することを確認します。
- `gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。
