# タイルおよびコンプリケーションタップ時のアプリ自動起動対応実績

本ウォークスルーは、Wear OS スマートウォッチ側の「タイル (Tile)」および「コンプリケーション (Complication)」画面をタッチ（タップ）した際、ウォッチ用 BattSync アプリ (`com.echeeze.wearbatterymonitor.presentation.MainActivity`) が即座に自動起動するタップアクションを組み込んだ実績レポートです。

---

## インプット（ユーザー様からのご指示・フィードバック）

- **インプット日時**: 2026年10月17日 00:01頃
- **関連タスク No**: **`#28`**
- **ユーザー様からのご指示原文**:
  > 「新機能を追加してください。
  > ウォッチアプリのタイルとコンプリケーションをタッチしたときに、BattSyncアプリを起動するようにしたいです。」

---

## 1. 実施内容サマリー

1. **タイル (`PhoneBatteryTileService.kt`) へのタップ起動モディファイアの追加**:
   [PhoneBatteryTileService.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/wear/src/main/java/com/echeeze/wearbatterymonitor/tile/PhoneBatteryTileService.kt) において、ProtoLayout の `ActionBuilders.LaunchAction` および `ModifiersBuilders.Clickable` を用いて、タイル表示領域タップ時にウォッチ用 `MainActivity` を起動するモディファイアをアタッチいたしました。

2. **コンプリケーション (`MainComplicationService.kt`) へのタップ起動 PendingIntent の追加**:
   [MainComplicationService.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/wear/src/main/java/com/echeeze/wearbatterymonitor/complication/MainComplicationService.kt) において、`PendingIntent.getActivity()` で生成した `MainActivity` 起動インテントを `ShortTextComplicationData.Builder` へ `.setTapAction(pendingIntent)` として登録いたしました。

---

## 2. 変更されたコードの Diff 比較 (Before / After)

- **変更対象モジュール**: **ウォッチアプリ (`:wear`) のみ** （※スマホアプリ `:mobile` のコード変更なし）

### ① [PhoneBatteryTileService.kt (Wear)](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/wear/src/main/java/com/echeeze/wearbatterymonitor/tile/PhoneBatteryTileService.kt)
```diff
  private fun tile(
      requestParams: RequestBuilders.TileRequest,
      context: Context,
      percent: Int,
      isCharging: Boolean,
      timestamp: Long,
      history: List<Triple<Int, Boolean, Long>>,
      rangeHours: Int
  ): TileBuilders.Tile {
      val timeStr = if (timestamp > 0) {
          SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
      } else "--:--"

+     val launchAction = androidx.wear.protolayout.ActionBuilders.LaunchAction.Builder()
+         .setAndroidActivity(
+             androidx.wear.protolayout.ActionBuilders.AndroidActivity.Builder()
+                 .setPackageName(context.packageName)
+                 .setClassName("com.echeeze.wearbatterymonitor.presentation.MainActivity")
+                 .build()
+         )
+         .build()

+     val clickableModifier = ModifiersBuilders.Modifiers.Builder()
+         .setClickable(
+             ModifiersBuilders.Clickable.Builder()
+                 .setId("launch_main_activity")
+                 .setOnClick(launchAction)
+                 .build()
+         )
+         .build()

      return TileBuilders.Tile.Builder()
          .setResourcesVersion(RESOURCES_VERSION)
          .setTileTimeline(
              TimelineBuilders.Timeline.fromLayoutElement(
                  materialScope(context, requestParams.deviceConfiguration) {
                      primaryLayout(
                          mainSlot = {
                              Column.Builder()
+                                 .setModifiers(clickableModifier)
                                  .addContent(
                                      text(
                                          "Phone".layoutString,
```

### ② [MainComplicationService.kt (Wear)](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/wear/src/main/java/com/echeeze/wearbatterymonitor/complication/MainComplicationService.kt)
```diff
-     private fun createComplicationData(text: String, contentDescription: String, isCharging: Boolean) =
-         ShortTextComplicationData.Builder(
-             text = PlainComplicationText.Builder(text).build(),
-             contentDescription = PlainComplicationText.Builder(contentDescription).build()
-         ).setMonochromaticImage(
-             MonochromaticImage.Builder(
-                 Icon.createWithResource(
-                     this,
-                     if (isCharging) com.echeeze.wearbatterymonitor.R.drawable.ic_phone_charging
-                     else com.echeeze.wearbatterymonitor.R.drawable.ic_phone_battery
-                 )
-             ).build()
-         ).build()
+     private fun createComplicationData(text: String, contentDescription: String, isCharging: Boolean): ShortTextComplicationData {
+         val intent = android.content.Intent(this, com.echeeze.wearbatterymonitor.presentation.MainActivity::class.java).apply {
+             flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
+         }
+         val pendingIntent = android.app.PendingIntent.getActivity(
+             this,
+             0,
+             intent,
+             android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
+         )
+ 
+         return ShortTextComplicationData.Builder(
+             text = PlainComplicationText.Builder(text).build(),
+             contentDescription = PlainComplicationText.Builder(contentDescription).build()
+         ).setMonochromaticImage(
+             MonochromaticImage.Builder(
+                 Icon.createWithResource(
+                     this,
+                     if (isCharging) com.echeeze.wearbatterymonitor.R.drawable.ic_phone_charging
+                     else com.echeeze.wearbatterymonitor.R.drawable.ic_phone_battery
+                 )
+             ).build()
+         ).setTapAction(pendingIntent).build()
+     }
```

---

## 3. 検証結果

- **静的解析 (`analyze_file`)**: 対象ファイルにコンパイルエラーがないことを確認いたしました。
- **ビルド実行**: ウェアアプリのビルド（`:wear:assembleDebug`）がエラーなしで正常に成功（SUCCESS）することを確認いたしました。
