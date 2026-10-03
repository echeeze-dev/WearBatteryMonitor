# リアルタイム Wearable 接続状態に基づくスマートウォッチ表示への改訂実績

本ウォークスルーは、データベースの過去ログからの判定・復元に依存せず、Android Wearable API (`getNodeClient().connectedNodes`) による「リアルタイムなスマートウォッチ接続状態」の検索結果を 100% 根拠とした表示制御への改訂実績レポートです。

---

## インプット（ユーザー様からのご指示・フィードバック）

1. **インプット 1 (不具合報告・調査指示 / 2026年10月3日 09:15頃)**:
   > 「・ウォッチの接続情報がおかしいと思います。スマホアプリのウォッチの情報がDisconnectedと表示されることがあります。もちろん接続されている状態です。表示条件など確認をお願いします。」

2. **インプット 2 (Plan 不承認・リアルタイム表示指示 / 2026年10月3日 09:22頃)**:
   > 「日時はOKです。ただ、修正内容は同意できません。
   > グラフ上部にあるウォッチ情報とバッテリー情報 or Disconnected はデータベース上の情報ではなく最新の情報であるべきです。修正内容を再検討してください。」

---

## 1. 実施内容サマリー

1. **`MainUiState` へのリアルタイム接続フラグ `isWatchConnected` の追加**:
   [MainUiState.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/ui/MainUiState.kt) に `isWatchConnected: Boolean` プロパティを追加し、スマートウォッチが物理的に現在到達・接続可能かどうかのリアルタイム状態を一元保持できるようにいたしました。

2. **Wearable Node API によるリアルタイム接続判定の実装**:
   [MainActivity.kt (Mobile)](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/MainActivity.kt) の `onStart()` および `syncFromService()` において、`Wearable.getNodeClient(this).connectedNodes` を呼び出し、実際に接続中のスマートウォッチが存在するかを非同期検索する処理 (`checkWatchNodeConnection()`) を実装いたしました。
   - **接続ノードが存在する場合 (`nodes.isNotEmpty()`)**:
     `isWatchConnected = true` および `watchName = node.displayName` をリアルタイム反映します。
   - **接続ノードが存在しない場合 (`nodes.isEmpty()`)**:
     `isWatchConnected = false` とし、画面に即座に `"Disconnected"` を描画します。

3. **画面表示ロジックの判定修復**:
   [MainActivity.kt (Mobile)](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/MainActivity.kt) の `BatterySection` (Watch) における `isDisconnected` の指定を `!uiState.isWatchConnected` に変更し、DB 過去ログや起動直後の `watchPercent == null` による誤判定を完全に排除いたしました。

---

## 2. 変更されたコードの Diff 比較 (Before / After)

### ① [MainUiState.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/ui/MainUiState.kt)
```diff
  data class MainUiState(
      val isLoading: Boolean = true,
      val phonePercent: Int = 0,
      val phoneIsCharging: Boolean = false,
      val phoneHistory: List<BatteryEntry> = emptyList(),
      val watchPercent: Int? = null,
      val watchIsCharging: Boolean = false,
      val watchHistory: List<BatteryEntry> = emptyList(),
      val earphonePercent: Int? = null,
      val earphoneName: String? = null,
      val earphoneHistory: List<BatteryEntry> = emptyList(),
      val isServiceRunning: Boolean = false,
      val graphRangeHours: Int = 3,
      val updateIntervalSeconds: Int = 60,
-     val watchName: String? = null
+     val watchName: String? = null,
+     val isWatchConnected: Boolean = false
  )
```

### ② [MainViewModel.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/ui/MainViewModel.kt)
```diff
+     /**
+      * ウォッチのリアルタイム接続状態を更新します。
+      *
+      * @param isConnected リアルタイム接続中フラグ
+      * @param watchName ウォッチのデバイス名
+      */
+     fun updateWatchConnected(isConnected: Boolean, watchName: String? = null) {
+         _uiState.update { current ->
+             current.copy(
+                 isWatchConnected = isConnected,
+                 watchName = watchName ?: current.watchName
+             )
+         }
+     }
```

### ③ [MainActivity.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/MainActivity.kt)
```diff
      override fun onStart() {
          super.onStart()
          val (percent, isCharging) = phoneMonitor.startMonitoring()
          viewModel.updatePhoneBattery(percent, isCharging)
          earphoneMonitor.startMonitoring()
          wearDataSyncer.startSyncing()

-         Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
-             nodes.firstOrNull()?.displayName?.let { name ->
-                 viewModel.updateWatchName(name)
-             }
-         }
+         checkWatchNodeConnection()
      }

+     private fun checkWatchNodeConnection() {
+         Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
+             val isConnected = nodes.isNotEmpty()
+             val name = nodes.firstOrNull()?.displayName
+             viewModel.updateWatchConnected(isConnected, name)
+             if (batteryMonitorService != null && name != null) {
+                 batteryMonitorService?.currentWatchName = name
+             }
+         }.addOnFailureListener {
+             viewModel.updateWatchConnected(false)
+         }
+     }
```

```diff
          BatterySection(
              icon = Icons.Default.Watch,
              title = uiState.watchName ?: "Smartwatch",
              percent = uiState.watchPercent ?: 0,
              isCharging = uiState.watchIsCharging,
              history = uiState.watchHistory,
-             isDisconnected = uiState.watchPercent == null,
+             isDisconnected = !uiState.isWatchConnected,
              rangeHours = uiState.graphRangeHours,
              onGraphClick = { detailDialogData = (uiState.watchName ?: "Smartwatch") to uiState.watchHistory }
          )
```

---

## 3. 検証結果

- **静的解析 (`analyze_file`)**: エラーおよび不要な警告がないことを確認いたしました。
- **ビルド実行**: `:mobile:assembleDebug` のビルドタスクがエラーなしで正常に成功（SUCCESS）することを確認いたしました。
