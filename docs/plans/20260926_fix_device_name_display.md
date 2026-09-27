# デバイス名（ウォッチ名・イヤホン名）表示不具合の修正計画

## 1. 目的と原因分析

スマホアプリ画面および履歴において、スマートウォッチや接続イヤホンの実際のデバイス名（端末名）が表示されない問題を解消します。

### 原因分析
1. **ウォッチデバイス名の不伝達**:
   `WearDataSyncer` の `onWatchBatteryReceived` コールバック受信時に、受信した `deviceName` を `MainViewModel.updateWatchName()` に反映させる処理が漏れていました。
2. **ノード検索時の更新漏れ**:
   `onResume()` などで `Wearable.getNodeClient().connectedNodes` から検出したウォッチの表示名（`displayName`）を ViewModel の `uiState` に設定する処理が削除されていました。
3. **DB 復元時の端末名未復元**:
   `MainViewModel.loadHistoryFromDb()` でデータベースから履歴を復元した際、履歴内に記録された最新の `deviceName` を `watchName` や `earphoneName` にセットしていませんでした。

---

## 2. 変更対象コンポーネント

### モバイルモジュール (`:mobile`)
- **`ui/MainViewModel.kt`** `[MODIFY]` : DB からの履歴ロード時に `watchName` および `earphoneName` を自動復元する処理を追加
- **`MainActivity.kt`** `[MODIFY]` : `onWatchBatteryReceived` 受信時および `onResume()` のノード検索時に `watchName` を更新する処理を追加

### ドキュメント類 (`docs/`)
- **`docs/plans/20260926_fix_device_name_display.md`** `[NEW]` : 本修正の計画書
- **`docs/walkthroughs/20260926_fix_device_name_display.md`** `[NEW]` : 本修正実績のウォークスルー報告書

---

## 3. 実装ステップ

### ステップ 1: `MainViewModel.kt` でのデバイス名復元処理追加
- `loadHistoryFromDb()` において、DB から取得した `watchData` および `earphoneData` から最新の `deviceName` を抽出し、`watchName` と `earphoneName` にセットします。

### ステップ 2: `MainActivity.kt` でのデバイス名更新の補強
- `WearDataSyncer` の `onWatchBatteryReceived` コールバックで `deviceName` が届いた際、`viewModel.updateWatchName(deviceName)` を呼び出します。
- `onResume()` 内で `Wearable.getNodeClient(this).connectedNodes` から接続されているウォッチの `displayName` を取得し、`viewModel.updateWatchName(name)` を呼び出します。

### ステップ 3: テスト実行・ビルド検証とウォークスルー報告書の作成
- 単体テスト（`:mobile:testDebugUnitTest`）およびビルド（`:mobile:assembleDebug`）を実行し、成功を確認します。
- 実績レポート `docs/walkthroughs/20260926_fix_device_name_display.md` を作成します。

---

## 4. 検証計画

- `gradle_build(":mobile:testDebugUnitTest")` で全テストが合格することを確認します。
- `gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。
