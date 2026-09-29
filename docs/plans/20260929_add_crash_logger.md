# 未キャッチ例外（クラッシュ）の自動ファイル保存およびログ表示機能追加計画

## 1. 目的と概要

アプリが万が一予期せぬエラー（未キャッチ例外）でクラッシュ（強制終了）した際、エラーの発生日時、例外の種別、スタックトレース（発生クラス名および行番号）を端末内部ストレージ (`crash_log.txt`) へ自動保存する **`CrashLogger`** 機能を導入します。

さらに、保存されたクラッシュログを PC 接続時（Device File Explorer）だけでなく、スマホアプリの画面（履歴ダイアログ等）からも直接確認・コピーできるようにし、数日間動作させた後の不具合調査を極めて容易にします。

---

## 2. 変更・新設対象コンポーネント

### モバイルモジュール (`:mobile`)

#### 【新設ファイル】
- **`util/CrashLogger.kt`** `[NEW]` :
  `Thread.setDefaultUncaughtExceptionHandler` を利用し、クラッシュ時のスタックトレースを内部ストレージ `crash_log.txt` へ自動書き出し・追記するログ管理クラス

#### 【既存ファイルの改修】
- **`MainActivity.kt`** `[MODIFY]` :
  アプリ起動時 (`onCreate`) にて `CrashLogger.init(this)` を実行して監視を開始
- **`ui/components/HistoryDetailDialog.kt`** `[MODIFY]` :
  クラッシュログが存在する場合に「クラッシュログ表示」ボタンを配置し、ダイアログ内でログ内容を確認・消去できるようにUIを拡張

### ドキュメント類 (`docs/`)
- **`docs/plans/20260929_add_crash_logger.md`** `[NEW]` : 本機能追加の計画書（本日日付: 2026年9月29日）
- **`docs/walkthroughs/20260929_add_crash_logger.md`** `[NEW]` : 本機能追加実績のウォークスルー報告書

---

## 3. 実装ステップ

### ステップ 1: 原因分析と本計画書の提示 (`docs/plans/`)
- 本計画書を作成し、チャット上で確認・承認を待機します。

### ステップ 2: `CrashLogger.kt` の作成 (`util/`)
- 未キャッチ例外のハンドラーを設定し、クラッシュ時に以下のフォーマットで `crash_log.txt` に記録します。
  - 発生日時（例: `2026/09/29 22:30:00`）
  - 例外名およびメッセージ（例: `java.lang.NullPointerException: ...`）
  - スタックトレース（発生行・メソッド呼び出し履歴）
- クラッシュログの読み込み (`getCrashLog()`) および削除 (`clearCrashLog()`) メソッドを実装します。

### ステップ 3: `MainActivity.kt` でのログ機能初期化
- `onCreate()` にて `CrashLogger.init(this)` を呼出し、アプリ全域でのクラッシュ監視を有効化します。

### ステップ 4: UI 上でのクラッシュログ確認画面の追加 (`HistoryDetailDialog.kt`)
- ログファイルが存在する場合に「クラッシュログ表示」ボタンを表示し、ダイアログ内でエラー理由の閲覧およびクリア（消去）を可能にします。

### ステップ 5: ビルド検証とウォークスルー報告書の作成 (`docs/walkthroughs/`)
- 自動テストの実行は行わず、`:mobile:assembleDebug` のビルド成功を確認後、実績レポート `docs/walkthroughs/20260929_add_crash_logger.md` を作成します。

---

## 4. 検証計画

- `gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。
- 新設・改修コードすべてに KDoc コメントが記述されていることを確認します。
