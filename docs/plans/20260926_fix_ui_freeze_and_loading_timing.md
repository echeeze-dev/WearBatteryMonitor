# データ読み出し時の GUI ブロック防止およびローディング解除タイミング最適化計画

## 1. 目的と原因分析

スマホアプリ起動時、ローディングインジケーターのアニメーションが固まる現象、およびインジケーター解除直後に一瞬不完全なグラフが表示される問題が発生していました。

### 原因分析
1. **GUIスレッドのブロック**:
   `phoneHistory.clear(); phoneHistory.addAll(phoneData)` のように Compose の `SnapshotStateList` に対する全件一括更新処理がメインスレッド上で実行され、大量の重い再構成（Recomposition）計算によりローディングインジケーターのアニメーション描画フレームが一時的にフリーズ（コマ落ち）していました。
2. **データ未確定状態でのフライング描画**:
   ステート更新の直後に `isLoading = false` を設定していたため、Compose の新しいデータによるレイアウト計算・フレーム描画が完了する前に画面切替（`Crossfade`）がスタートし、描画完了前の空/未確定な状態が一瞬画面に表示されてしまっていました。

---

## 2. 変更対象コンポーネント

### モバイルモジュール (`:mobile`)
- **`MainActivity.kt`** `[MODIFY]` : データ更新時の不要な再構成の抑制、最新バッテリー状態の取得関数の分離、およびフレーム確定用の待機制御（`delay(200)`）の追加

### ドキュメント類 (`docs/`)
- **`docs/plans/20260926_fix_ui_freeze_and_loading_timing.md`** `[NEW]` : 本修正計画書
- **`docs/walkthroughs/20260926_fix_ui_freeze_and_loading_timing.md`** `[NEW]` : 本修正実績のウォークスルー

---

## 3. 実装ステップ

### ステップ 1: 原因分析と修正計画のドキュメント化 (`docs/plans/`)
- 本計画書を作成し、原因分析と改善手順を明確化します。

### ステップ 2: メインスレッドブロックの解消とステート更新最適化
- `MainActivity.kt` において、`phoneHistory` などのリスト変更時に変更の有無をチェック（`if (phoneHistory != phoneData)`）し、無駄な全件一括再構成による GUI ブロックを回避します。

### ステップ 3: 描画フレーム確定後のローディング解除制御
- DB からのデータロードおよびバッテリー最新状態の確定（`fetchCurrentBatteryState()`）を実行後、`delay(200)` で Compose の画面描画フレームが完全に確定するまで待機してから `isLoading = false` を解除します。

### ステップ 4: テスト・ビルド検証とウォークスルー実績書の作成 (`docs/walkthroughs/`)
- 単体テスト（`:mobile:testDebugUnitTest`）およびビルド（`:mobile:assembleDebug`）を実行し、成功を確認後、ウォークスルー報告書を作成します。

---

## 4. 検証計画

### 自動テスト & ビルド検証
- `gradle_build("mobile:testDebugUnitTest")`
- `gradle_build("mobile:assembleDebug")`
がエラーなく正常に完了することを確認します。
