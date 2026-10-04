# クラッシュログのクリップボードコピー機能追加計画

## 1. インプット（ユーザー様からのご指示・フィードバック）

- **インプット日時**: 2026年10月4日 12:30頃
- **関連履歴 No**: **`#07`**（未キャッチ例外の自動ファイル保存＆表示機能追加）
- **ユーザー様からのご指示原文**:
  > 「クラッシュログについて。出たんだけどお願いがあります。
  > ログを共有する方法がスクリーンショットくらいしかなく非効率です。クリップボードにコピーする機能を追加してもらえませんか？」

---

## 2. 目的と UI UX 改善設計

タスク `#07` にて導入したクラッシュログ表示ダイアログ (`HistoryDetailDialog`) 内において、長いログテキストをスクショすることなくワンタップで端末のクリップボードへ直接コピーできる **「📋 クリップボードにコピー (Copy Log)」ボタン** を追加します。

### 機能改善内容
- クラッシュログ表示画面の下部に「📋 Copy Log」ボタンを配置します。
- ボタンタップ時、Compose の `LocalClipboardManager` を利用してログテキスト全体をクリップボードへ保存し、`Toast` にてコピー完了をフィードバック通知します。

---

## 3. 変更対象コンポーネント

### モバイルモジュール (`:mobile`)
- **`ui/components/HistoryDetailDialog.kt`** `[MODIFY]` : 
  クラッシュログ表示画面下部に「📋 Copy Log」ボタンを追加し、クリップボードコピーおよび Toast 通知処理を実装します。

### ドキュメント類 (`docs/`)
- **`docs/plans/20261004_add_crash_log_copy_to_clipboard.md`** `[NEW]` : 本機能改善の計画書（本日日付: 2026年10月4日）
- **`docs/walkthroughs/20261004_add_crash_log_copy_to_clipboard.md`** `[NEW]` : 本機能改善実績のウォークスルー報告書

---

## 4. 実装ステップ

### ステップ 1: `HistoryDetailDialog.kt` へのクリップボードコピーボタンの配置
- `LocalClipboardManager.current` を取得し、クラッシュログ表示領域の下部に「📋 Copy Log」ボタンを配置します。
- タップ時に `clipboardManager.setText(AnnotatedString(crashLogText))` を実行し、Toast 通知を出力します。

### ステップ 2: 静的解析およびビルド検証
- `analyze_file` によりエラーや不要な警告がないことを確認します。
- テスト自動実行は行わず、`gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。

### ステップ 3: ウォークスルー実績書の作成 (`docs/walkthroughs/`)
- 実績レポート `docs/walkthroughs/20261004_add_crash_log_copy_to_clipboard.md` を作成します。

---

## 5. 検証計画

- `analyze_file` で確認を行います。
- `gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。
