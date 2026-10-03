# スマホ起動直後のウォッチバッテリー 0% 誤表示の修復計画

## 1. インプット（ユーザー様からのご指示・ご質問）

- **インプット日時**: 2026年10月3日 09:30頃
- **ユーザー様からのご質問・ご指示原文**:
  > 「スマホアプリ起動直後、ウォッチのバッテリー情報が0%と出たけど、原因は分かりますか？
  > 取得できていない状態ならバー表示などにしてほしいのですが。」

---

## 2. 目的と原因分析

スマホアプリ起動直後、スマートウォッチが接続中（`isWatchConnected = true`）であるものの、ウォッチからの最新バッテリー情報パケットをまだ 1 回も受領していない一瞬の間、画面上に「0%」と誤表示されてしまう不具合を修正します。

未取得・受領前の状態では「0%」ではなく「---」とバー（未取得）表示するように UI 描画を改善します。

### 原因分析
`MainActivity.kt` の Composable レイアウト呼び出しにおいて、`percent = uiState.watchPercent ?: 0` とエルビス演算子で非受領時 (`null`) に強制的に `0` を補填して `BatterySection` へ渡していました。
また、`BatterySection.kt` 側の `percent` 引数が `Int` (non-null) 型で定義されていたため、`null`（数値未取得）を扱えなくなっていました。

---

## 3. 変更対象コンポーネント

### モバイルモジュール (`:mobile`)
- **`ui/components/BatterySection.kt`** `[MODIFY]` : 
  `percent` 引数の型を `Int?` (nullable) に拡張し、数値が未取得（`null` または `-1`）の場合は「0%」ではなく「---」とバー表示するように描画ロジックを改修します。
- **`MainActivity.kt`** `[MODIFY]` : 
  `BatterySection` (Watch) 呼び出し時のエルビス演算子 `?: 0` を削除し、`uiState.watchPercent` (nullable) をそのまま安全に渡すように修正します。

### ドキュメント類 (`docs/`)
- **`docs/plans/20261003_fix_watch_initial_zero_percent_ui.md`** `[NEW]` : 本修正の計画書（本日日付: 2026年10月3日）
- **`docs/walkthroughs/20261003_fix_watch_initial_zero_percent_ui.md`** `[NEW]` : 本修正実績のウォークスルー報告書

---

## 4. 実装ステップ

### ステップ 1: `BatterySection.kt` の `percent` 引数拡張と未取得テキスト表示
- `percent` の型を `Int?` に変更し、非接続時は `"Disconnected"`、接続中で残量受領前の場合は `"---"`、残量受領後は `"$percent%"` と描画されるように修復します。

### ステップ 2: `MainActivity.kt` での `watchPercent` の直接渡しの適用
- `BatterySection` 呼び出し時の `percent = uiState.watchPercent ?: 0` から `?: 0` を除去し、`percent = uiState.watchPercent` を直接渡します。

### ステップ 3: 静的解析およびビルド検証
- `analyze_file` によりエラーや不要な警告がないことを確認します。
- テスト自動実行は行わず、`gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。

### ステップ 4: ウォークスルー実績書の作成 (`docs/walkthroughs/`)
- 実績レポート `docs/walkthroughs/20261003_fix_watch_initial_zero_percent_ui.md` を作成します。

---

## 5. 検証計画

- `analyze_file` で確認を行います。
- `gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。
