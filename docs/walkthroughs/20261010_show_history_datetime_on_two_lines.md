# 履歴リストの日付と時刻を2行表示した実績

本ウォークスルーでは、履歴詳細ダイアログの日付と時刻を別々の行に分け、日時列の幅を調整した実績を記録します。

---

## インプット（ユーザー様からのご指示・フィードバック）

- **インプット日時**: 2026年10月10日 17:24頃
- **関連履歴 No**: **`#27`**
- **ユーザー様からのご指示原文**:
  > 「表示を確認しましたが、年月日と時刻を2行表示にしたいです。」

---

## 1. 実施内容サマリー

- **変更対象モジュール**: モバイルアプリ (`:mobile`)
- `HistoryDetailDialog.kt` の日時表示を年月日（上段）と時刻（下段）の2行にしました。
- 日時欄の幅を `120.dp` から `80.dp` に変更し、2行表示を使って履歴の他の情報の表示領域を確保しました。

## 2. 変更されたコードの Diff 比較 (Before / After)

### `HistoryDetailDialog.kt`
```diff
-    val sdf = remember { SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()) }
+    val sdf = remember { SimpleDateFormat("yyyy/MM/dd\nHH:mm:ss", Locale.getDefault()) }

                                 Text(
                                     text = sdf.format(Date(entry.timestamp)),
                                     style = MaterialTheme.typography.bodySmall,
-                                    modifier = Modifier.width(120.dp),
-                                    maxLines = 1
+                                    modifier = Modifier.width(80.dp),
+                                    maxLines = 2
                                 )
```

## 3. 技術的な理由

日時を改行で分けて縦に並べることで、年月日を残したまま横幅を抑え、履歴理由や残量の領域を確保します。日付を上段、時刻を下段にするため、イベントの発生日と時刻をそれぞれ読み取りやすくなります。

## 4. 検証結果

- 変更箇所のコードを確認し、日時フォーマットに改行が含まれ、表示が最大2行となる設定を確認しました。
- プロジェクトルール `[R02]` に従い、自動テストや Gradle タスクは実行していません。
- 端末・エミュレーターでの今回の変更の表示確認は未実施です。
