# 履歴リストの時刻表示に年月日を追加した実績

本ウォークスルーでは、履歴一覧に表示される時間だけを省略していた不備を修正し、年月日を含む日時が一覧の各行に表示されるように改善した実績を記録します。

---

## インプット（ユーザー様からのご指示・フィードバック）

- **インプット日時**: 2026年10月10日 17:03頃
- **関連履歴 No**: **`#26`**
- **ユーザー様からのご指示原文**:
  > 「リストの時間情報に日付が乗っていません。年月日を表示するようにできませんか？」

---

## 1. 実施内容サマリー

1. **履歴一覧の表示形式の修正**:
   `HistoryDetailDialog.kt` において、日時文字列を生成した後に `substringAfter(" ")` により日付を落としていた処理を削除し、年月日と時刻を同じ行に表示するように修正しました。

2. **レイアウト幅の調整**:
   表示内容が長くなるため、時間列の横幅を `70.dp` から `120.dp` に拡張し、年月日 + 時刻が見切れないように調整しました。

---

## 2. 変更されたコードの Diff 比較 (Before / After)

- **変更対象モジュール**: **モバイルアプリ (`:mobile`) のみ**

### ① [HistoryDetailDialog.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/ui/components/HistoryDetailDialog.kt)
```diff
-                               Text(
-                                   text = sdf.format(Date(entry.timestamp)).substringAfter(" "),
-                                   style = MaterialTheme.typography.bodySmall,
-                                   modifier = Modifier.width(70.dp)
-                               )
+                               Text(
+                                   text = sdf.format(Date(entry.timestamp)),
+                                   style = MaterialTheme.typography.bodySmall,
+                                   modifier = Modifier.width(120.dp),
+                                   maxLines = 1
+                               )
```

---

## 3. 技術的な理由

- 日付と時刻を合わせて表示することで、どの日に発生したイベントなのかを一目で把握できます。
- 以前の `substringAfter(" ")` による落とし込みは、履歴の直感的な把握を妨げていたためです。
- 表示幅を広めに確保することで、年月日と時刻が折り返さずに表示され、一覧の読みやすさを維持しています。

---

## 4. 検証結果

- **静的確認**: 変更箇所に不整合やコンパイル上の問題がないことを確認しました。
- **ビルド実行**: `./gradlew --quiet :mobile:compileDebugKotlin` を実行し、成功を確認しました。
