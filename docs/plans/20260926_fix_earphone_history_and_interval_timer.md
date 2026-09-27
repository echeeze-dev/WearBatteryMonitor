# イヤホン履歴表示および Update Interval 定期タイマーの反映修正計画

## 1. 目的と原因分析

イヤホンの接続履歴ログが画面およびグラフに反映されない問題、および Update Interval（更新間隔スライダー）が正しく画面や定期記録に反映されない不具合を修正します。

### 原因分析
1. **イヤホン接続ログ (`percent = -1`) のグラフ描画除外**:
   残量数値のない接続ログ（`percent = -1`）のみが存在する場合、`HistoryGraph.kt` 内の `validBatteryData = filteredData.filter { it.percent >= 0 }` 判定により「データなし」と判定され、グラフおよびタップダイアログ領域が表示されなくなっていました。
2. **サービス非起動時の定期タイマー欠落**:
   バックグラウンドサービス停止時（`isServiceRunning == false`）に、Update Interval スライダーに基づく定期タイマーが動作していませんでした。
3. **サービス起動時のタイマー実行データの UI 未同期**:
   サービス起動時、`startWatchUpdateTimer` の定期実行ログが `BatteryMonitorService` に追加された後、`MainActivity` / `MainViewModel` へのリアルタイム状態同期が行われていませんでした。

---

## 2. 変更対象コンポーネント

### モバイルモジュール (`:mobile`)
- **`ui/components/HistoryGraph.kt`** `[MODIFY]` : `percent = -1` (接続中・未数値) のエントリーが存在する場合でも、ステータスライン（下部接続表示）および履歴件数を正常に描画・タップ可能にする修正
- **`ui/MainViewModel.kt`** `[MODIFY]` : サービス停止時にも Update Interval タイマーを起動し、定期記録を DB および画面に保持する処理を追加
- **`BatteryMonitorService.kt`** `[MODIFY]` : 定期タイマー（`startWatchUpdateTimer`）発火時にサービスから登録されているリスナー/Activity へ最新履歴を同期・通知する処理の追加

### ドキュメント類 (`docs/`)
- **`docs/plans/20260926_fix_earphone_history_and_interval_timer.md`** `[NEW]` : 本修正の計画書
- **`docs/walkthroughs/20260926_fix_earphone_history_and_interval_timer.md`** `[NEW]` : 本修正実績のウォークスルー報告書

---

## 3. 実装ステップ

### ステップ 1: 原因分析と修正計画書の作成 (`docs/plans/`)
- 本修正計画書を作成し、イヤホン履歴表示および定期タイマー反映の修正方針を明確化します。

### ステップ 2: `HistoryGraph.kt` での接続ログ (`percent = -1`) 描画サポート
- 残量数値がない接続エントリー（`percent = -1`）であっても、下部の接続状態ステータスラインを描画し、タップしてダイアログを表示できるように判定ロジックを緩和します。

### ステップ 3: `MainViewModel.kt` および `BatteryMonitorService.kt` での Update Interval 定期タイマーの反映
- サービス停止時・起動時問わず、設定された Update Interval 秒数ごとに定期記録（`PERIODIC_INTERVAL`）が正しく発火・DB 保存され、画面 UI にリアルタイム反映されるように補強します。

### ステップ 4: ビルド検証とウォークスルー報告書の作成 (`docs/walkthroughs/`)
- テストの自動実行は行わず、`:mobile:assembleDebug` のビルド成功を確認後、実績レポート `docs/walkthroughs/20260926_fix_earphone_history_and_interval_timer.md` を作成します。

---

## 4. 検証計画

- `gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。
