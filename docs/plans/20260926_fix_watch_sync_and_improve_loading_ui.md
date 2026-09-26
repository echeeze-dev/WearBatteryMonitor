# ウォッチ起動時同期の最適化および初期ロード UI 品質向上計画

## 1. 目的と概要

アプリの動作品位向上および同期処理の最適化のため、以下の問題に対応します。
1. ウォッチアプリ起動時にスマホ側へ過剰に強制同期リクエスト（`/request_battery_update`）が送信されていたため、無駄な通信を削減して自動同期処理を整理・修正します。
2. データ未取得時やデータ件数が少ない際に不完全なバーグラフが画面に描画されていた問題を改修し、「データなし」メッセージを画面領域に美しく表示します。
3. スマホアプリ起動直後、データベースから履歴が読み込まれるまでのわずかな時間（0〜0.3秒）に 0% や未完成な画面が一瞬チラつく問題を改修し、`isLoading` 状態と `Crossfade` による上品なローディング画面を表示します。

---

## 2. 変更対象コンポーネント

### モバイルモジュール (`:mobile`)
- **`MainActivity.kt`** `[MODIFY]` : `isLoading` 状態管理の追加、`Crossfade` アニメーションでの画面切替、および `HistoryGraph` の空データ表示改善

### ウェアモジュール (`:wear`)
- **`presentation/MainActivity.kt`** `[MODIFY]` : `checkStatus()` 内での自動同期メッセージ送信の削除、`HistoryGraphFixed` での「データなし」状態の描画制御改善

### 設計・ドキュメント (`docs/`)
- **`docs/design/system_overview.md`** `[MODIFY]` : 初期表示状態および同期動作仕様の追記・更新
- **`docs/walkthroughs/20260926_fix_watch_sync_and_improve_loading_ui.md`** `[NEW]` : 修正実績と検証結果の作成

---

## 3. 実装ステップ

### ステップ 1: ウォッチ起動時自動同期リクエストの削除
- `WearMainActivity` 内の `checkStatus()` で発火していた `/request_battery_update` メッセージの即時自動送信処理を削除します。

### ステップ 2: データ未取得時のグラフ描画制御の改善（モバイル & ウェア）
- 履歴データが存在しない場合、あるいは指定時間範囲内に有効なデータ（`percent >= 0`）が存在しない場合に、中途半端なバーを描画せず「データなし」テキストを表示するように修正します。

### ステップ 3: スマホアプリ初期ロード時 UI の品位向上
- `MainActivity` (Mobile) に `isLoading` 状態を追加し、`loadHistoryFromDb()` のデータ復元完了後に解除します。
- `setContent` 内で `Crossfade` アニメーションを使い、読み込み中は `CircularProgressIndicator` とメッセージを表示し、完了後にスムーズにメイン画面へ遷移させます。

### ステップ 4: ドキュメントの同期更新とウォークスルー作成
- `system_overview.md` を更新し、対応実績をまとめたウォークスルーレポートを作成します。

---

## 4. 検証計画

### ビルド & テスト検証
- `gradle_build("mobile:testDebugUnitTest")`
- `gradle_build("mobile:assembleDebug")`
- `gradle_build("wear:assembleDebug")`
がすべて正常に成功することを確認します。
