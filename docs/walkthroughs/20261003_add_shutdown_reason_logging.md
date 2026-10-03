# 端末シャットダウンおよびバッテリー切れ要因の記録機能追加実績

本ウォークスルーは、スマートフォン本体およびスマートウォッチのバッテリー切れ・電源オフによるシャットダウン発生時に、その要因（`SYSTEM_SHUTDOWN` / `WATCH_SHUTDOWN`）をデータベース（`battery_history`）へ永続保存する機能の追加実績レポートです。

---

## インプット（ユーザー様からのご指示・フィードバック）

1. **インプット 1 (機能リクエスト / 2026年10月3日 09:40頃)**:
   > 「・スマホのバッテリーが切れてシャットダウンする場合、その情報も保持できませんか？バックグラウンドサービスが動作していてシャットダウンするとき、その要因などが分かれば一緒に保持してほしいです。可能ならほかのデバイスも同様にしたいですが、Bluetooth接続デバイスがバッテリー切れかどうかは、さすがに分からないですかね？」

2. **インプット 2 (範囲限定指示 / 2026年10月3日 09:48頃)**:
   > 「Bluetoothイヤホンの対応はしなくていいです。無理やり条件を作ってしまうとよくわからなくなってしまうので。
   > その他の対応について進めてください。」

---

## 1. 実施内容サマリー

1. **スマホ本体のシャットダウンおよび低バッテリー検知の実装**:
   [PhoneBatteryMonitor.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/monitor/PhoneBatteryMonitor.kt) において、`Intent.ACTION_SHUTDOWN`（電源オフ/バッテリー切れシャットダウン）および `Intent.ACTION_BATTERY_LOW`（バッテリー残量警告）のシステムインテント受信を追加いたしました。
   シャットダウン検知時は即座に `recordReason = "SYSTEM_SHUTDOWN"` として最新残量とともにデータベースへ保存いたします。

2. **スマートウォッチのシャットダウン緊急パケット送信の実装**:
   [MainActivity.kt (Wear)](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/wear/src/main/java/com/echeeze/wearbatterymonitor/presentation/MainActivity.kt) において、Wear OS 端末自身が `Intent.ACTION_SHUTDOWN` を受信した際、スマホ側へ `recordReason = "WATCH_SHUTDOWN"` パケットを自動緊急送信するレシーバーを追加いたしました。

3. **履歴詳細ダイアログの表記拡張**:
   [HistoryDetailDialog.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/ui/components/HistoryDetailDialog.kt) において、シャットダウンログに対して `🛑 Shutdown`, `⚠️ Low Battery`, `⌚ Watch Shutdown` などの専用アイコン・タイトルを表示するように表示を拡張いたしました。

---

## 2. 変更されたコードの Diff 比較 (Before / After)

### ① [PhoneBatteryMonitor.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/monitor/PhoneBatteryMonitor.kt)
```diff
  class PhoneBatteryMonitor(
      private val context: Context,
-     private val onBatteryChanged: (percent: Int, isCharging: Boolean) -> Unit
+     private val onBatteryChanged: (percent: Int, isCharging: Boolean, reason: String) -> Unit
  ) {

      private val batteryReceiver = object : BroadcastReceiver() {
          override fun onReceive(context: Context, intent: Intent) {
-             if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
-                 val (percent, isCharging) = parseBatteryIntent(intent)
-                 onBatteryChanged(percent, isCharging)
-             }
+             val reason = when (intent.action) {
+                 Intent.ACTION_SHUTDOWN -> "SYSTEM_SHUTDOWN"
+                 Intent.ACTION_BATTERY_LOW -> "BATTERY_LOW"
+                 else -> "BATTERY_CHANGED"
+             }
+             val (percent, isCharging) = parseBatteryIntent(intent)
+             onBatteryChanged(percent, isCharging, reason)
          }
      }

      fun startMonitoring(): Pair<Int, Boolean> {
-         val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
+         val filter = IntentFilter().apply {
+             addAction(Intent.ACTION_BATTERY_CHANGED)
+             addAction(Intent.ACTION_SHUTDOWN)
+             addAction(Intent.ACTION_BATTERY_LOW)
+         }
          val initialIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
-             context.registerReceiver(batteryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
+             context.registerReceiver(batteryReceiver, filter, Context.RECEIVER_EXPORTED)
          } else {
              context.registerReceiver(batteryReceiver, filter)
          }
          return parseBatteryIntent(initialIntent)
      }
```

### ② [MainActivity.kt (Wear)](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/wear/src/main/java/com/echeeze/wearbatterymonitor/presentation/MainActivity.kt)
```diff
      private val watchBatteryReceiver = object : BroadcastReceiver() {
-         override fun onReceive(context: Context, intent: Intent) { updateWatchBattery() }
+         override fun onReceive(context: Context, intent: Intent) {
+             if (intent.action == Intent.ACTION_SHUTDOWN) {
+                 sendWatchBatteryToPhone("WATCH_SHUTDOWN")
+             } else {
+                 updateWatchBattery()
+             }
+         }
      }

      override fun onResume() {
          super.onResume()
          Wearable.getDataClient(this).addListener(this)
          Wearable.getCapabilityClient(this).addListener(this, "battery_monitor_phone_app")
-         val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
+         val filter = IntentFilter().apply {
+             addAction(Intent.ACTION_BATTERY_CHANGED)
+             addAction(Intent.ACTION_SHUTDOWN)
+         }
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
-             registerReceiver(watchBatteryReceiver, filter, RECEIVER_NOT_EXPORTED)
+             registerReceiver(watchBatteryReceiver, filter, RECEIVER_EXPORTED)
          } else {
              registerReceiver(watchBatteryReceiver, filter)
          }
```

### ③ [HistoryDetailDialog.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/ui/components/HistoryDetailDialog.kt)
```diff
  val reasonTitle = when (entry.recordReason) {
      "BATTERY_CHANGED" -> "⚡ Battery"
      "PERIODIC_INTERVAL" -> "⏱ Periodic" + (entry.intervalSeconds?.let { " (${formatInterval(it)})" } ?: "")
      "INITIAL_FETCH" -> "🚀 Initial"
      "WEAR_REQUEST" -> "⌚ Wear Req"
      "BT_BATTERY_CHANGED", "BT_UPDATE" -> "🎧 BT Battery"
      "BT_CONNECTION" -> "🔗 Bluetooth"
      "DATA_SYNC" -> "🔄 Sync"
+     "SYSTEM_SHUTDOWN" -> "🛑 Shutdown"
+     "BATTERY_LOW" -> "⚠️ Low Battery"
+     "WATCH_SHUTDOWN" -> "⌚ Watch Shutdown"
      else -> if (entry.percent == -1) "Disconnected" else "Record"
  }
```

---

## 3. 検証結果

- **静的解析 (`analyze_file`)**: 対象全ファイルにおいてエラーや不必要な警告がないことを確認いたしました。
- **ビルド実行**: `:mobile:assembleDebug` および `:wear:assembleDebug` の両タスクがエラーなしで正常に成功（SUCCESS）することを確認いたしました。
