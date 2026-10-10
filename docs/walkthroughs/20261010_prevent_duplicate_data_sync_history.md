# データ同期 (DATA_SYNC) 発生時における履歴 2 重重複保存の防止実績

本ウォークスルーは、Wear OS からのデータ同期（`/watch_battery`）発生時、常駐監視サービス内の `WearDataSyncer` とシステムバックグラウンドの `MobileWearableListenerService` の双方で同一イベントが重複検知されて履歴エントリーが 2 回連続インサートされていた二重受領バグを根本排除した実績レポートです。

---

## インプット（ユーザー様からのご指示・フィードバック）

1. **インプット 1 (不具合報告および改善指示 / 2026年10月4日 10:55頃)**:
   > 「3. 履歴情報の Sync 時、毎回2回連続データが残っています。１度のみにできませんか？」

2. **インプット 2 (重なり除外判定不採用および根本原因排除指示 / 2026年10月10日 11:30頃)**:
   > 「#25に進んでください。ただし、重なり除外判定は不具合が見えなくなる可能性があるため、今は不要です。」

---

## 1. 実施内容サマリー

1. **`BatteryMonitorService` への起動フラグ (`isRunning`) の定義**:
   [BatteryMonitorService.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/BatteryMonitorService.kt) において、常駐監視サービスが起動・アクティブ動作中であることを示す `BatteryMonitorService.isRunning` フラグを管理・状態保持いたしました。

2. **`MobileWearableListenerService` での二重受領インサートの根本排除**:
   [MobileWearableListenerService.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/MobileWearableListenerService.kt) の `onDataChanged` において、`BatteryMonitorService.isRunning == true`（＝常駐監視サービスが起動しており `WearDataSyncer` が受信と DB 保存を担当中）である場合は、バックグラウンド受信サービス側での重複インサート処理をスキップするように改修いたしました。
   これにより、時間間引き等のガード処理を挟むことなく、同一データパケットの二重受領による重複インサートが根本から排除されました。

---

## 2. 変更されたコードの Diff 比較 (Before / After)

- **変更対象モジュール**: **モバイルアプリ (`:mobile`) のみ** （※ウォッチアプリ `:wear` のコード変更なし）

### ① [BatteryMonitorService.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/BatteryMonitorService.kt)
```diff
  class BatteryMonitorService : Service() {
+     companion object {
+         @Volatile
+         var isRunning: Boolean = false
+     }

      override fun onCreate() {
          super.onCreate()
+         isRunning = true
          ...
      }

      override fun onDestroy() {
          super.onDestroy()
+         isRunning = false
          ...
      }
```

### ② [MobileWearableListenerService.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/MobileWearableListenerService.kt)
```diff
      override fun onDataChanged(dataEvents: DataEventBuffer) {
          Log.i("BatteryMonitorListener", "onDataChanged received ${dataEvents.count} events")
+         // 常駐監視サービスが起動中で WearDataSyncer 側が直接受領・保存を行っている場合は二重インサートを防止する
+         if (BatteryMonitorService.isRunning) {
+             Log.d("BatteryMonitorListener", "Skipping duplicate insert in listener service because BatteryMonitorService is running")
+             return
+         }

          val database = AppDatabase.getDatabase(this)
```

---

## 3. 検証結果

- **静的解析 (`analyze_file`)**: エラーおよび不要な警告がないことを確認いたしました。
- **ビルド実行**: モバイルアプリのビルド（`:mobile:assembleDebug`）が正常に成功することを確認いたしました。
