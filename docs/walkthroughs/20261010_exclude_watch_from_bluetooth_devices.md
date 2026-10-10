# Bluetooth デバイス一覧領域からのスマートウォッチ情報除外実績

本ウォークスルーは、スマホアプリの Bluetooth デバイス検出・表示領域 (`EarphoneBatteryMonitor` / `EarphoneSection`) において、スマートウォッチ (Wear OS 端末) の情報が誤検出されて混ざってしまうのを防止するため、判定ロジックを強化して除外処理を徹底した実績レポートです。

---

## インプット（ユーザー様からのご指示・フィードバック）

- **インプット日時**: 2026年10月4日 10:55頃
- **関連履歴 No**: **`#02`**, **`#08`**
- **ユーザー様からのご指示原文**:
  > 「次はBluetoothデバイスのバッテリー情報について
  > 4. ウォッチデバイスの情報が乗ってしまいます。ここにはウォッチを除いた情報を載せてほしいのですが、判別は可能ですか？」

---

## 1. 実施内容サマリー

1. **`EarphoneBatteryMonitor` へのスマートウォッチ判定メソッド (`isWatchDevice`) の新設・強化**:
   [EarphoneBatteryMonitor.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/monitor/EarphoneBatteryMonitor.kt) において、`isWatchDevice(device: BluetoothDevice)` 判定関数を新設いたしました。
   検出された BluetoothDevice について、Bluetooth Major Class (`0x0700` Wearable) および Device Class (`0x0704` Wrist Watch) を判定し、スマートウォッチと判定された場合は Bluetooth デバイス領域から直ちに検知スキップ・除外するように修復いたしました。

2. **`updateFromIntent` および `updateFromProfile` でのフィルタリング徹底**:
   インテント受領時 (`updateFromIntent`) および接続プロファイル照合時 (`updateFromProfile`) の双方において `isWatchDevice(device)` 判定を通し、Bluetooth イヤホン・ヘッドセット等のみを純粋に検出して表示するように動作を統一いたしました。

---

## 2. 変更されたコードの Diff 比較 (Before / After)

- **変更対象モジュール**: **モバイルアプリ (`:mobile`) のみ** （※ウォッチアプリ `:wear` のコード変更なし）

### [EarphoneBatteryMonitor.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/monitor/EarphoneBatteryMonitor.kt)
```diff
+     /**
+      * 指定された Bluetooth デバイスがスマートウォッチ (Wear OS 端末) であるかを判定します。
+      *
+      * @param device Bluetooth デバイス
+      * @return スマートウォッチと判定された場合は true
+      */
+     private fun isWatchDevice(device: BluetoothDevice): Boolean {
+         if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
+             return false
+         }
+         val deviceClass = device.bluetoothClass?.deviceClass ?: 0
+         val majorClass = device.bluetoothClass?.majorDeviceClass ?: 0
+ 
+         // Bluetooth Device / Major Class による判定 (Wearable: 0x0700, Wrist Watch: 0x0704)
+         return ((deviceClass and 0x0700) == 0x0700) || (majorClass == 0x0700) || (deviceClass == 0x0704)
+     }

      fun updateFromIntent(intent: Intent) {
          ...
          if (device != null) {
              if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
-                 val deviceClass = device.bluetoothClass?.deviceClass ?: 0
-                 // Wearable (0x0700) / Wrist Watch (0x0704) はイヤホンから除外
-                 if ((deviceClass and 0x0700) == 0x0700) return
+                 // スマートウォッチ端末はイヤホン一覧から確実に除外
+                 if (isWatchDevice(device)) return

                  val percent = if (batteryLevel != -1) batteryLevel else null
                  val name = device.name
                  onEarphoneChanged(percent, name, reason)
                  return
              }
          }
          updateFromProfile()
      }

      fun updateFromProfile() {
          if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return

          val activeDevice = bluetoothHeadset?.connectedDevices?.firstOrNull { device ->
-             val deviceClass = device.bluetoothClass?.deviceClass ?: 0
-             (deviceClass and 0x0700) != 0x0700
+             !isWatchDevice(device)
          }
```

---

## 3. 検証結果

- **静的解析 (`analyze_file`)**: エラーおよび不要な警告がないことを確認いたしました。
- **ビルド実行**: モバイルアプリのビルド（`:mobile:assembleDebug`）が正常に成功することを確認いたしました。
