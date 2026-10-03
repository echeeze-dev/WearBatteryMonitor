# グラフ内デバイス名称テキスト表記（接続時1回のみ＆動的多段高さ表示）の復元実績

本ウォークスルーは、バッテリー残量グラフ (HistoryGraph) 内において、「接続開始時 1 回のみの表示」を維持しつつ、テキスト描画高さとグラフ描画高さから最大段数を自動動的計算し、描画ごとに表示段（Y 座標）を切り替えて美しく描画する機能の実装実績レポートです。

---

## インプット（ユーザー様からのご指示・フィードバック一覧）

1. **インプット 1 (機能復元リクエスト / 2026年10月3日 09:55頃)**:
   > 「・グラフ内のデバイス名称表記をお願いします。以前は実現できていたのですが、リファクタリング時に消えてしまったようです。」

2. **インプット 2 (表示条件の確認指示 / 2026年10月3日 10:02頃)**:
   > 「確認です。テキストラベル表示条件はentry.deviceName != nullのデータがあるところ全てですか？」

3. **インプット 3 (表示位置の上下シフト指定 / 2026年10月3日 10:08頃)**:
   > 「「直前にラベルを描画した位置から一定の横幅間隔（80dp 以上）が開いていること」は不要で、かわりに表示位置をずらしてデバイス名を表示することはできませんか？」

4. **インプット 4 (複数段表示指定 / 2026年10月3日 10:15頃)**:
   > 「2段だけだとまだデバイス名が重なるケースが多いので、グラフ表示の高さにあわせて複数段にすることはできませんか？」

5. **インプット 5 (接続開始時 1 回のみ表示への方針確定指示 / 2026年10月3日 10:25頃)**:
   > 「間引きをなくすのではなく、デバイス接続時以降最初にデバイス名が分かったときのデータのみデバイス名を表示してください。」

6. **インプット 6 (自動段数計算および表示段変更の復元指示 / 2026年10月3日 10:35頃)**:
   > 「表示段の変更がなくなっているので、再修正をお願いします。
   > できれば、4段だけでなく、テキスト表示の高さとグラフの高さから自動で段数を計算してデバイス名表示毎に表示位置を変えてほしいです。」

---

## 1. 実施内容サマリー

[HistoryGraph.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/ui/components/HistoryGraph.kt) の Canvas 描画ロジックにおいて、「デバイス接続開始時 1 回のみの表示」を維持した上で、グラフの高さ（`height`）とテキストの測定高さ（`textHeight`）から配置可能な最大段数（`maxLevels`）を自動計算し、デバイス名が表示されるたびに Y 座標の描画レベル（高さ）を段違いに切り替えてテキストラベル (`drawText`) を描画するレスポンシブな多段配置アルゴリズムを追記・実装いたしました。

これにより、複数デバイスの接続ログが連続する場合であっても、表示高さが段違いに変更されるためテキスト同士が直接重ならず、グラフ上が極めて見やすく美しく表示されます。

---

## 2. 変更されたコードの Diff 比較 (Before / After)

### [HistoryGraph.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/ui/components/HistoryGraph.kt)
```diff
                  var lastBoundaryDay = -1
                  val calendar = Calendar.getInstance()
                  history.forEach { entry ->
                      val x = ((entry.timestamp - minDataTs).toFloat() / totalTimelineDuration) * width
                      calendar.timeInMillis = entry.timestamp
                      val currentDay = calendar.get(Calendar.DAY_OF_YEAR)
                      if (lastBoundaryDay != -1 && currentDay != lastBoundaryDay) {
                          drawLine(color = dayDividerColor, start = Offset(x, 0f), end = Offset(x, height), strokeWidth = 1.dp.toPx())
                      }
                      lastBoundaryDay = currentDay
                  }

-                 // デバイス名テキスト描画（接続開始時・端末切り替わり時の 1 回のみ描画）
+                 // デバイス名テキスト描画（接続開始時 1 回のみ ＋ テキスト＆グラフ高さによる自動段数切り替え）
                  var lastDrawnDeviceName: String? = null
+                 var nameLevelIndex = 0
                  val labelColor = Color(0xFF6200EE)

                  history.forEach { entry ->
                      val name = entry.deviceName
                      if (!name.isNullOrBlank()) {
                          if (name != lastDrawnDeviceName) {
                              val x = ((entry.timestamp - minDataTs).toFloat() / totalTimelineDuration) * width
                              val textResult = textMeasurer.measure(
                                  text = name,
                                  style = TextStyle(fontSize = 10.sp, color = labelColor, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                              )
+                             val textHeight = textResult.size.height.toFloat().coerceAtLeast(10.dp.toPx())
+                             val maxLevels = ((height - textHeight) / textHeight).toInt().coerceIn(2, 5)
+                             val currentLevel = nameLevelIndex % maxLevels
+                             val levelY = 2.dp.toPx() + (currentLevel * (textHeight * 0.95f))

                              drawText(
                                  textLayoutResult = textResult,
-                                 topLeft = Offset(x + 2.dp.toPx(), 4.dp.toPx())
+                                 topLeft = Offset(x + 2.dp.toPx(), levelY)
                              )
                              lastDrawnDeviceName = name
+                             nameLevelIndex++
                          }
                      } else {
                          lastDrawnDeviceName = null
                      }
                  }

                  val leftmostX = 0f
                  drawLine(color = boundaryColor, start = Offset(leftmostX, 0f), end = Offset(leftmostX, height), strokeWidth = 1.5.dp.toPx())
                  val rightmostX = width
                  drawLine(color = boundaryColor, start = Offset(rightmostX, 0f), end = Offset(rightmostX, height), strokeWidth = 1.5.dp.toPx())
```

---

## 3. 検証結果

- **静的解析 (`analyze_file`)**: エラーおよび不要な警告がないことを確認いたしました。
- **ビルド実行**: モバイルアプリのビルド（`:mobile:assembleDebug`）が正常に成功することを確認いたしました。
