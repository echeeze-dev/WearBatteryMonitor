# 下に引っ張って更新 (Pull-to-Refresh) UI UX 改善計画

## 1. インプット（ユーザー様からのご指示・フィードバック）

- **インプット日時**: 2026年10月4日 12:10頃
- **関連履歴 No**: **`#16`**（画面への手動更新ボタンおよび一定期間ごとの自動更新機能追加）
- **ユーザー様からのご指示原文**:
  > 「手動更新ボタンの追加は見た目がいまいちなので、見直してほしいです。
  > 画面右上に縦に"Reflesh"と出ています。例えば、下に引っ張って更新のような機能は難しいでしょうか？」

---

## 2. 目的と UI UX 改善設計

タスク `#16` にて導入した画面上部の常示テキストボタン（見た目が崩れていた不格好な「🔄 Refresh」ボタン）を撤去し、Android / Compose 標準の直感的な **「下に引っ張って更新 (Pull-to-Refresh / SwipeRefresh)」** 機能へ変更・置換します。

### UI UX 改善内容
- 不格好な常示ボタンをヘッダーから撤去し、画面領域をすっきりと整理します。
- 画面の一番上から指で下方向にスワイプ（引っ張る）ことで、標準の円形インジケータが表示され、直感的にデータベース再ロード（`viewModel.refreshData()`）およびノード再検索（`checkWatchNodeConnection()`）が実行されるように改修します。

---

## 3. 変更対象コンポーネント

### モバイルモジュール (`:mobile`)
- **`MainActivity.kt`** `[MODIFY]` : 
  - 常示「🔄 Refresh」ボタンをレイアウトから削除します。
  - Jetpack Compose Material 3 の `PullToRefreshBox`（または `rememberPullToRefreshState`）を組み込み、下にスワイプした際に `onRefresh` アクション（`refreshData()` & `checkWatchNodeConnection()`）を発火させるようにレイアウト構造を改修します。

### ドキュメント類 (`docs/`)
- **`docs/plans/20261004_implement_pull_to_refresh_ui.md`** `[NEW]` : 本 UI 改善の計画書（本日日付: 2026年10月4日）
- **`docs/walkthroughs/20261004_implement_pull_to_refresh_ui.md`** `[NEW]` : 本 UI 改善実績のウォークスルー報告書

---

## 4. 実装ステップ

### ステップ 1: `MainActivity.kt` での常示ボタン撤去と `PullToRefreshBox` の組み込み
- `BatteryMonitorScreen` コンポーネント全体を Compose M3 の `PullToRefreshBox`（または PullToRefresh 状態管理）でラップします。
- 下に引っ張った動作（SwipeDown）時に `isRefreshing` 状態を保持し、非同期でリフレッシュ処理を実行後、インジケータを自動解除します。

### ステップ 2: 静的解析およびビルド検証
- `analyze_file` によりエラーや不要な警告がないことを確認します。
- テスト自動実行は行わず、`gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。

### ステップ 3: ウォークスルー実績書の作成 (`docs/walkthroughs/`)
- 実績レポート `docs/walkthroughs/20261004_implement_pull_to_refresh_ui.md` を作成します。

---

## 5. 検証計画

- `analyze_file` で確認を行います。
- `gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。
