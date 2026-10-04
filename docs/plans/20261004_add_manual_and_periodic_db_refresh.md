# データベース情報の画面への手動更新ボタンおよび定期自動更新機能の追加計画

## 1. インプット（ユーザー様からのご指示）

- **インプット日時**: 2026年10月4日 11:45頃
- **ユーザー様からのご指示原文**:
  > 「・データベースの情報の画面への反映がされないです。更新ボタンなどをつけるか、一定期間で更新するようにできませんか？」

ご指示いただいた 5 件のご要望の最後の項目である **「5 点目：データベース情報の画面への反映（手動更新ボタンおよび定期自動更新）」** に着手いたします。

---

## 2. 目的と実装アプローチ

データベース（`battery_history`）に新たに記録された最新のバッテリー履歴および状態が、アプリ画面を開いたままにしている間も即座に画面上のグラフや履歴ダイアログへ反映されるよう、「手動更新ボタン」および「一定周期での画面自動更新（Periodic Auto-Refresh）」機能を実装します。

### 実装アプローチ
1. **手動更新 (Refresh) ボタンの追加**:
   画面上部（Graph Range 切り替えチップの右横など）に「🔄 更新」ボタンを配置し、タップされた際に即座にデータベースからの再読み込み（`loadHistoryFromDb()`）およびノード接続確認を実行し、画面を一瞬で最新化します。
2. **一定期間ごとの画面自動更新 (Periodic Auto-Refresh)**:
   画面が開いている間（`onStart` 〜 `onStop`）、ユーザーが選択した Update Interval 秒数（例: 60秒ごと）ごとにバックグラウンドで自動的に `loadHistoryFromDb()` を実行し、放置していても画面の履歴やグラフが最新状態へ自動更新されるようにします。

---

## 3. 変更対象コンポーネント

### モバイルモジュール (`:mobile`)
- **`MainActivity.kt`** `[MODIFY]` : 
  - 画面開出し中（`onStart`〜`onStop`）、`updateIntervalSeconds` ごとに `loadHistoryFromDb()` を実行するコルーチンタイマーを追加します。
  - UI 上部に手動で最新化できる「🔄 更新」ボタンを追加します。
- **`ui/MainViewModel.kt`** `[MODIFY]` : 
  手動/自動更新のトリガーを受け取る `refreshData()` メソッドを追加します。

### ドキュメント類 (`docs/`)
- **`docs/plans/20261004_add_manual_and_periodic_db_refresh.md`** `[NEW]` : 本機能追加の計画書（本日日付: 2026年10月4日）
- **`docs/walkthroughs/20261004_add_manual_and_periodic_db_refresh.md`** `[NEW]` : 本機能追加実績のウォークスルー報告書

---

## 4. 実装ステップ

### ステップ 1: `MainViewModel.kt` への手動/定期リフレッシュ処理の追加
- `refreshData()` メソッドを追加し、`loadHistoryFromDb()` とリアルタイムデータ更新を統合実行可能にします。

### ステップ 2: 画面 UI 上部への手動「🔄 更新」ボタンの配置 (`MainActivity.kt`)
- `GraphRangeSelector` の横に「🔄 更新」アイコンボタンを追加し、タップ時に `viewModel.refreshData()` を呼び出すようにレイアウトを更新します。

### ステップ 3: 画面開出し中の定期自動更新タイマーの追加 (`MainActivity.kt`)
- 画面が開いている間、設定された更新秒数ごとに `viewModel.refreshData()` を呼び出すコルーチンタイマーを起動します。

### ステップ 4: 静的解析およびビルド検証
- `analyze_file` によりエラーや不要な警告がないことを確認します。
- テスト自動実行は行わず、`gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。

### ステップ 5: ウォークスルー実績書の作成 (`docs/walkthroughs/`)
- 実績レポート `docs/walkthroughs/20261004_add_manual_and_periodic_db_refresh.md` を作成します。

---

## 5. 検証計画

- `analyze_file` で確認を行います。
- `gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。
