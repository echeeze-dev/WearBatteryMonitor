# 応答なし (No Response) ログ発生時におけるグラフ内デバイス名重複描画抑止実績

本ウォークスルーは、スマートウォッチの応答なし（`No Response`）ログがデータベースへ記録された際、同じ端末名であるにもかかわらずグラフ描画 (HistoryGraph) 内でデバイス名テキストが高頻度で重複描画されてしまっていた不具合の修正実績レポートです。

---

## インプット（ユーザー様からのご指示・フィードバック）

- **インプット日時**: 2026年10月4日 10:55頃
- **関連履歴 No**: **`#13`**, **`#15`**
- **ユーザー様からのご指示原文**:
  > 「2. グラフ中のデバイス名表示が頻繁に表示されている。履歴情報を比較すると、Watch Google Pixel Watch 2 - No Responseのデータがあるときに毎回表示してしまっているようです。デバイスが変わったとき・新たに接続したときにのみ表示してほしいです。」

---

## 1. 実施内容サマリー

[HistoryGraph.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/ui/components/HistoryGraph.kt) の描画ループにおいて、これまで `percent == -1` のエントリー通過時に一律 `lastDrawnDeviceName = null` とクリアされていたロジックを修正いたしました。

明示的な切断ログ（`BT_DISCONNECT` / `WATCH_DISCONNECT`）が発生した場合にのみ `lastDrawnDeviceName = null` とクリアし、同一端末の通信応答なし（`WATCH_NO_RESPONSE`）や未受領ログが継続している間は前回のデバイス名を記憶・維持するように補正いたしました。これにより、同一デバイス接続中の無用なテキスト重複描画が完全に防がれます。

---

## 2. 変更されたコードの Diff 比較 (Before / After)

- **変更対象モジュール**: **モバイルアプリ (`:mobile`) のみ** （※ウォッチアプリ `:wear` のコード変更なし）

### [HistoryGraph.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/ui/components/HistoryGraph.kt)
```diff
                              drawText(
                                  textLayoutResult = textResult,
                                  topLeft = Offset(x + 2.dp.toPx(), levelY)
                              )
                              lastDrawnDeviceName = name
                              nameLevelIndex++
                          }
                      } else {
-                         lastDrawnDeviceName = null
+                         // 明示的な切断ログ時のみクリアし、単なる応答なし (No Response) ログ継続時は前回描画デバイス名を維持
+                         if (entry.recordReason == "BT_DISCONNECT" || entry.recordReason == "WATCH_DISCONNECT") {
+                             lastDrawnDeviceName = null
+                         }
                      }
                  }
```

---

## 3. 検証結果

- **静的解析 (`analyze_file`)**: エラーおよび不要な警告がないことを確認いたしました。
- **ビルド実行**: モバイルアプリのビルド（`:mobile:assembleDebug`）が正常に成功することを確認いたしました。
