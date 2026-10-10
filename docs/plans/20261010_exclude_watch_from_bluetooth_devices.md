# Bluetooth デバイス一覧領域からのスマートウォッチ情報除外計画

## 1. インプット（ユーザー様からのご指示・フィードバック）

- **インプット日時**: 2026年10月4日 10:55頃
- **関連履歴 No**: **`#02`**, **`#08`**
- **ユーザー様からのご指示原文**:
  > 「次はBluetoothデバイスのバッテリー情報について
  > 4. ウォッチデバイスの情報が乗ってしまいます。ここにはウォッチを除いた情報を載せてほしいのですが、判別は可能ですか？」

---

## 2. 目的と判定技術の適用

スマホアプリの Bluetooth デバイス表示領域 (`EarphoneSection` / `EarphoneBatteryMonitor`) において、スマートウォッチ (Wear OS 端末) の情報が混ざって表示されるのを防止するため、スマートウォッチ端末を技術的に 100% 確実に判定・除外します。

### 判定技術アプローチ
Bluetooth デバイス検出時 (`EarphoneBatteryMonitor.kt`) において、以下の 2 重の判定・除外フィルターを適用します：
1. **Bluetooth Device Class 判定**:
   `device.bluetoothClass.majorDeviceClass` が Wearable / Wrist Watch (クラス `0x0700` 付近) である場合はイヤホン領域から即座に除外します。
2. **Wearable API 接続ノード (`connectedNodes`) との照合**:
   `Wearable.getNodeClient().connectedNodes` から取得したスマートウォッチのノード名・Bluetooth アドレスと一致するデバイスを、イヤホン検出対象から明確にフィルタリング・除外します。

---

## 3. 変更対象コンポーネント

### モバイルモジュール (`:mobile`)
- **`monitor/EarphoneBatteryMonitor.kt`** `[MODIFY]` : 
  デバイス検出時に `BluetoothClass` の判定強化および `connectedNodes`（ウォッチノード名）との除外リスト照合処理を組み込みます。

### ドキュメント類 (`docs/`)
- **`docs/plans/20261010_exclude_watch_from_bluetooth_devices.md`** `[NEW]` : 本修正の計画書（本日日付: 2026年10月10日）
- **`docs/walkthroughs/20261010_exclude_watch_from_bluetooth_devices.md`** `[NEW]` : 本修正実績のウォークスルー報告書

---

## 4. 実装ステップ

### ステップ 1: `EarphoneBatteryMonitor.kt` でのスマートウォッチ除外判定の強化
- 検出された BluetoothDevice について、Device Class 判定と Wearable ノードリストとの一致確認を行い、スマートウォッチを検出対象から除外します。

### ステップ 2: 静的解析およびビルド検証
- `analyze_file` によりエラーや不要な警告がないことを確認します。
- テスト自動実行は行わず、`gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。

### ステップ 3: ウォークスルー実績書の作成 (`docs/walkthroughs/`)
- 実績レポート `docs/walkthroughs/20261010_exclude_watch_from_bluetooth_devices.md` を作成します。

---

## 5. 検証計画

- `analyze_file` で確認を行います。
- `gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。
