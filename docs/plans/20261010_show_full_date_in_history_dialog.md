# 履歴リスト詳細ダイアログにおける年月日（yyyy/MM/dd）表記拡張計画

## 1. インプット（ユーザー様からのご指示）

- **インプット日時**: 2026年10月10日 12:00頃
- **ユーザー様からのご指示原文**:
  > 「リストの時間情報に日付が乗っていません。年月日を表示するようにできませんか？」

---

## 2. 目的と原因分析

履歴詳細表示ダイアログ (`HistoryDetailDialog.kt`) のログリストにおいて、時刻のみ（`HH:mm:ss`）表示されていた箇所の描画を拡張し、いつの記録であるかが一目で判別できるよう **年月日を含めたフル日時表記 (`yyyy/MM/dd HH:mm:ss`)** を表示するように改修します。

### 原因分析
`HistoryDetailDialog.kt` の描画コードにおいて、`sdf.format(Date(entry.timestamp)).substringAfter(" ")` により日付（年月日）文字列が故意に切り落とされて時刻部分のみ抽出されていました。また、タイムスタンプ表示用のテキスト幅も `70.dp` と狭く制限されていました。

---

## 3. 変更対象コンポーネント

### モバイルモジュール (`:mobile`)
- **`ui/components/HistoryDetailDialog.kt`** `[MODIFY]` : 
  `substringAfter(" ")` による日付切り落としを削除し、`yyyy/MM/dd HH:mm:ss`（例: `2026/10/10 10:26:58`）の全日時を表示するように修正します。あわせて、年月日が収まるようタイムスタンプ列の幅 (`width`) を拡張します。

### ドキュメント類 (`docs/`)
- **`docs/plans/20261010_show_full_date_in_history_dialog.md`** `[NEW]` : 本修正の計画書（本日日付: 2026年10月10日）
- **`docs/walkthroughs/20261010_show_full_date_in_history_dialog.md`** `[NEW]` : 本修正実績のウォークスルー報告書

---

## 4. 実装ステップ

### ステップ 1: `task_history.md` への事前新規登録 (`#27`)
- ルール `[R11]` に従い、`docs/task_history.md` にタスク `#27`（履歴ダイアログでの年月日表示拡張）を `[IN_PROGRESS]` 状態で事前登録します。

### ステップ 2: `HistoryDetailDialog.kt` での年月日表示と幅の拡張
- `substringAfter(" ")` 処理を撤去し、`sdf.format(Date(entry.timestamp))` をそのまま全表示します。
- レイアウト崩れを起こさないよう、タイムスタンプ列の幅を `70.dp` ➔ `125.dp` 付近へ拡張調整します。

### ステップ 3: 静的解析およびビルド検証
- `analyze_file` によりエラーや不要な警告がないことを確認します。
- テスト自動実行は行わず、`gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。

### ステップ 4: ウォークスルー実績書の作成 (`docs/walkthroughs/`)
- 実績レポート `docs/walkthroughs/20261010_show_full_date_in_history_dialog.md` を作成します。

---

## 5. 検証計画

- `analyze_file` で確認を行います。
- `gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。
