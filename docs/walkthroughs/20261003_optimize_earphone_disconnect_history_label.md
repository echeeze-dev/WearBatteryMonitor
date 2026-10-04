# ワイヤレスイヤホン切断時の履歴リスト表記最適化実績

本ウォークスルーは、ワイヤレスイヤホン切断イベント発生時に切断されたデバイス名（例: "LinkBuds"）を保持し、履歴リスト上に "Bluetooth LinkBuds - Disconnected" の形式で正しく記録・表記されるように改修した実績レポートです。

---

## インプット（ユーザー様からのご指示）

- **インプット日時**: 2026年10月3日 10:42頃
- **ユーザー様からのご指示原文**:
  > 「・(恐らく)ワイヤレスイヤホンの切断時のリスト上の情報が"BT Battery LinkBuds - Connected と表記されているのですが、切断時も接続時と同様に"Bluetooth LinkBuds - Disconnected"とリスト情報に残したいです。可能でしょうか？」

---

## 1. 実施内容サマリー

1. **`EarphoneBatteryMonitor` における切断デバイス名の保持**:
   [EarphoneBatteryMonitor.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/monitor/EarphoneBatteryMonitor.kt) の `handleDisconnection` において、直前まで接続されていたデバイス名（`device.name`）を取得・保持したまま `reason = "BT_DISCONNECT"` として通知するように修正いたしました。

2. **履歴詳細ダイアログ (`HistoryDetailDialog.kt`) での切断表記の補正**:
   [HistoryDetailDialog.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/ui/components/HistoryDetailDialog.kt) において、`recordReason == "BT_DISCONNECT"` の場合は表題ラベルを `🔗 Bluetooth` とし、詳細行を `[デバイス名] • Disconnected`（例: `LinkBuds • Disconnected`）と表示されるよう表示ロジックを修復いたしました。

---

## 2. 変更されたコードの Diff 比較 (Before / After)

### ① [EarphoneBatteryMonitor.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/monitor/EarphoneBatteryMonitor.kt)
```diff
      private fun handleDisconnection(intent: Intent) {
          val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
          } else {
              @Suppress("DEPRECATION")
              intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
          }
          if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
-             if (device != null) {
-                 onEarphoneChanged(null, null, "BT_CONNECTION")
-             }
+             val deviceName = device?.name
+             onEarphoneChanged(null, deviceName, "BT_DISCONNECT")
+         } else {
+             onEarphoneChanged(null, null, "BT_DISCONNECT")
          }
      }
```

### ② [HistoryDetailDialog.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/ui/components/HistoryDetailDialog.kt)
```diff
                                      val reasonTitle = when (entry.recordReason) {
                                          "BATTERY_CHANGED" -> "⚡ Battery"
                                          "PERIODIC_INTERVAL" -> "⏱ Periodic" + (entry.intervalSeconds?.let { " (${formatInterval(it)})" } ?: "")
                                          "INITIAL_FETCH" -> "🚀 Initial"
                                          "WEAR_REQUEST" -> "⌚ Wear Req"
                                          "BT_BATTERY_CHANGED", "BT_UPDATE" -> "🎧 BT Battery"
-                                         "BT_CONNECTION" -> "🔗 Bluetooth"
+                                         "BT_CONNECTION", "BT_DISCONNECT" -> "🔗 Bluetooth"
                                          "DATA_SYNC" -> "🔄 Sync"
                                          "SYSTEM_SHUTDOWN" -> "🛑 Shutdown"
                                          "BATTERY_LOW" -> "⚠️ Low Battery"
                                          "WATCH_SHUTDOWN" -> "⌚ Watch Shutdown"
                                          else -> if (entry.percent == -1) "Disconnected" else "Record"
                                      }

                                      val details = mutableListOf<String>()
                                      if (entry.deviceName != null) {
                                          details.add(entry.deviceName)
                                      }
                                      if (entry.isCharging) {
                                          details.add("Charging")
                                      }
                                      if (entry.percent == -1) {
-                                         details.add(if (entry.deviceName != null) "Connected" else "Disconnected")
+                                         if (entry.recordReason == "BT_DISCONNECT") {
+                                             details.add("Disconnected")
+                                         } else {
+                                             details.add(if (entry.deviceName != null) "Connected" else "Disconnected")
+                                         }
                                      }
```

---

## 3. 検証結果

- **静的解析 (`analyze_file`)**: エラーおよび不要な警告がないことを確認いたしました。
- **ビルド実行**: モバイルアプリのビルド（`:mobile:assembleDebug`）が正常に成功することを確認いたしました。
