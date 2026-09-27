# Graph Range（グラフ表示範囲）の Wear OS 端末への同期修正計画

## 1. 目的と原因分析

スマホアプリ側でユーザーが変更した Graph Range（グラフ表示時間範囲: 3h / 6h / 12h / 24h / ALL）の設定値が、Wear OS スマートウォッチ側のグラフ表示範囲へ正常に同期・反映されない問題を修正します。

### 原因分析
1. **設定値送信呼び出しの漏れ**:
   スマホアプリ側（`MainActivity.kt`）のリファクタリング時、`updateGraphRange(hours)` 実行時に Wear OS 端末へ設定値を送信する処理が漏れていました。
2. **設定値管理の分離リスク**:
   バッテリー状態データ（`/battery_status`）とグラフ範囲設定（`/settings/graph_range`）が分離されたパスで管理されていたため、同期タイミングにズレが生じていました。

---

## 2. 変更対象コンポーネント

### モバイルモジュール (`:mobile`)
- **`data/BatteryRepository.kt` & `BatteryRepositoryImpl.kt`** `[MODIFY]` : `syncStatusToWear` の送信データマップ内に `rangeHours` 属性を追加
- **`MainActivity.kt`** `[MODIFY]` : `updateGraphRange(hours)` 実行時に `syncStatusToWear()` を呼び出して最新の範囲設定を Wear へ即時同期

### ウェアモジュール (`:wear`)
- **`presentation/MainActivity.kt`** `[MODIFY]` : `/battery_status` 受信時にデータマップ内の `range_hours` を取得し、`graphRangeHours` を自動更新

### ドキュメント類 (`docs/`)
- **`docs/plans/20260926_sync_graph_range_to_wear.md`** `[NEW]` : 本修正の計画書
- **`docs/walkthroughs/20260926_sync_graph_range_to_wear.md`** `[NEW]` : 本修正実績のウォークスルー報告書

---

## 3. 実装ステップ

### ステップ 1: リポジトリ層での `rangeHours` 送信の追加 (`:mobile`)
- `BatteryRepository.kt` および `BatteryRepositoryImpl.kt` の `syncStatusToWear` に `rangeHours: Int = 3` を追加し、`dataMap.putInt("range_hours", rangeHours)` を送信データに含めます。

### ステップ 2: スマホ側 `MainActivity.kt` での変更時即時同期
- `updateGraphRange(hours)` 呼び出し時、`syncStatusToWear()` を実行して新しい表示範囲を Wear OS へ即座に送出します。

### ステップ 3: ウェア側 `WearMainActivity.kt` での受信・反映処理
- `updateStateFromDataMap` において、`dataMap.containsKey("range_hours")` が存在する場合は `graphRangeHours` を自動で更新します。

### ステップ 4: テスト・ビルド検証とウォークスルー報告書の作成
- 単体テスト（`:mobile:testDebugUnitTest`）および全モジュールビルド（`:mobile:assembleDebug`, `:wear:assembleDebug`）を実行し、成功を確認します。
- 実績レポート `docs/walkthroughs/20260926_sync_graph_range_to_wear.md` を作成します。

---

## 4. 検証計画

- `gradle_build(":mobile:testDebugUnitTest")` で全テストが合格することを確認します。
- `gradle_build(":mobile:assembleDebug")` および `:wear:assembleDebug` が正常に成功することを確認します。
