# デバイス名（ウォッチ名・イヤホン名）表示漏れ防止と多重フォールバック補強計画

## 1. 目的と原因分析

スマホアプリ画面において、スマートウォッチや接続イヤホンのデバイス名（端末名）が表示されない問題を根絶します。

### 原因分析
1. **表示フォールバックの不足**:
   `uiState.watchName` や `uiState.earphoneName` が一時的に null の際、`phoneHistory` / `watchHistory` / `earphoneHistory` などの履歴エントリーに保存されている最新の `entry.deviceName` を画面表示用タイトルとして参照・フォールバックしていませんでした。
2. **Bluetooth デバイス名の初期スキャン漏れ**:
   `EarphoneBatteryMonitor` において、接続済みヘッドセットプロファイルの検索時だけでなく、`BluetoothAdapter.bondedDevices` 等から現在アクティブな Bluetooth デバイスの名称取得が不十分なケースがありました。
3. **ノード検索名（`displayName`）の UI への確実な反映**:
   `Wearable.getNodeClient().connectedNodes` から取得したペアリングウォッチの表示名（例: "Galaxy Watch" 等）の `viewModel` への反映タイマー・コールバックが不十分でした。

---

## 2. 変更対象コンポーネント

### モバイルモジュール (`:mobile`)
- **`ui/components/BatterySection.kt`** `[MODIFY]` : タイトル表示時に `history` 内の最新 `deviceName` をフォールバック参照するロジックの追加
- **`ui/components/EarphoneSection.kt`** `[MODIFY]` : デバイス名決定時に `history` 内の最新 `deviceName` をフォールバック参照するロジックの追加
- **`monitor/EarphoneBatteryMonitor.kt`** `[MODIFY]` : ペアリング・接続済みデバイスからの名前取得ロジックの強化
- **`MainActivity.kt`** `[MODIFY]` : ノード検索結果および履歴からの表示名の即時反映・同期処理の補強

### ドキュメント類 (`docs/`)
- **`docs/plans/20260926_fix_device_name_fallback_and_detection.md`** `[NEW]` : 本修正の計画書
- **`docs/walkthroughs/20260926_fix_device_name_fallback_and_detection.md`** `[NEW]` : 本修正実績のウォークスルー報告書

---

## 3. 実装ステップ

### ステップ 1: UI コンポーネントへの多重フォールバックロジック追加 (`ui/components/`)
- `BatterySection.kt` および `EarphoneSection.kt` において、`title` / `name` が直接指定されない場合、`history` から最新の `deviceName` を自動検索して端末名として表示します。

### ステップ 2: `EarphoneBatteryMonitor.kt` の接続デバイス名取得強化
- `updateFromProfile()` 内で `connectedDevices` からのデバイス名抽出処理を改善し、権限がある場合に確実にデバイス名を取得して通知します。

### ステップ 3: `MainActivity.kt` でのノード検索・同期補強
- `onStart()` および `onResume()` にて `connectedNodes` の取得を確実に行い、検出されたウォッチ名を `viewModel.updateWatchName(name)` で即座に状態へ反映します。

### ステップ 4: テスト・ビルド検証とウォークスルー報告書の作成
- 単体テスト（`:mobile:testDebugUnitTest`）およびビルド（`:mobile:assembleDebug`）を実行し、エラーなく成功することを確認します。
- 実績レポート `docs/walkthroughs/20260926_fix_device_name_fallback_and_detection.md` を作成します。

---

## 4. 検証計画

- `gradle_build(":mobile:testDebugUnitTest")` で全テストが合格することを確認します。
- `gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。
