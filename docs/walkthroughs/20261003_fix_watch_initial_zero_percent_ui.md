# スマホ起動直後のウォッチバッテリー 0% 誤表示の修復実績

本ウォークスルーは、スマホアプリ起動直後にスマートウォッチの残量パケット未受領時 (`null`) に「0%」と誤表示されていた問題を「---」（バー表示）へ修復した実績レポートです。

---

## インプット（ユーザー様からのご指示・ご質問）

- **インプット日時**: 2026年10月3日 09:30頃
- **ユーザー様からのご質問・ご指示原文**:
  > 「スマホアプリ起動直後、ウォッチのバッテリー情報が0%と出たけど、原因は分かりますか？
  > 取得できていない状態ならバー表示などにしてほしいのですが。」

---

## 1. 実施内容サマリー

1. **`BatterySection` コンポーネントの `percent` 引数の型拡張**:
   [BatterySection.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/ui/components/BatterySection.kt) の `percent` 引数を `Int?` (nullable) に拡張し、数値未受領（`null` または `-1`）の際は「0%」ではなく「---」とバー表記するように改修いたしました。

2. **レイアウト呼び出し時のフォールバック値 `?: 0` の撤去**:
   [MainActivity.kt (Mobile)](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/MainActivity.kt) における `BatterySection` 呼び出し時の `percent = uiState.watchPercent ?: 0` から `?: 0` を削除し、`uiState.watchPercent` の状態を直接渡すように修復いたしました。

---

## 2. 変更されたコードの Diff 比較 (Before / After)

### ① [BatterySection.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/ui/components/BatterySection.kt)
```diff
  @Composable
  fun BatterySection(
      icon: ImageVector,
      title: String,
-     percent: Int,
+     percent: Int?,
      isCharging: Boolean,
      history: List<BatteryEntry>,
      isDisconnected: Boolean = false,
      rangeHours: Int,
      onGraphClick: () -> Unit
  ) {
      val historyDeviceName = history.lastOrNull { it.deviceName != null }?.deviceName
      val displayTitle = if (!isDisconnected && (title == "Smartwatch" || title.isBlank()) && historyDeviceName != null) {
          historyDeviceName
      } else {
          title
      }

+     val displayPercent = when {
+         isDisconnected -> "Disconnected"
+         percent != null && percent != -1 -> "$percent%"
+         else -> "---"
+     }

      Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
              Spacer(modifier = Modifier.width(8.dp))
              Text(text = displayTitle, style = MaterialTheme.typography.headlineSmall)
          }
          Text(
-             text = if (isDisconnected) "Disconnected" else "$percent%",
+             text = displayPercent,
              style = MaterialTheme.typography.displayLarge,
              color = if (isDisconnected) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
          )
```

### ② [MainActivity.kt (Mobile)](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/MainActivity.kt)
```diff
          BatterySection(
              icon = Icons.Default.Watch,
              title = uiState.watchName ?: "Smartwatch",
-             percent = uiState.watchPercent ?: 0,
+             percent = uiState.watchPercent,
              isCharging = uiState.watchIsCharging,
              history = uiState.watchHistory,
              isDisconnected = !uiState.isWatchConnected,
              rangeHours = uiState.graphRangeHours,
              onGraphClick = { detailDialogData = (uiState.watchName ?: "Smartwatch") to uiState.watchHistory }
          )
```

---

## 3. 検証結果

- **静的解析 (`analyze_file`)**: エラーおよび不要な警告がないことを確認いたしました。
- **ビルド実行**: `:mobile:assembleDebug` のビルドタスクがエラーなしで正常に成功（SUCCESS）することを確認いたしました。
