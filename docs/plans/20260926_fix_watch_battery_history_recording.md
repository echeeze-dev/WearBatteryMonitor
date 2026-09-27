# ウォッチバッテリー履歴が DB に記録されない不具合の修正計画

## 1. 目的と原因分析

スマホアプリおよび常駐サービスにおいて、スマートウォッチのバッテリー情報が履歴データベース（`battery_history`）へ正常に記録されない不具合を修正します。

### 原因分析
1. **スマホからウォッチへの定期更新要求 (`requestWatchBatteryUpdate`) の消失**:
   `BatteryMonitorService` の定期更新タイマーにおいて、スマホからウォッチ側ノードへ更新要求メッセージ（`/request_battery_update`）を送信する処理が抜け落ちていました。このため、ウォッチのバッテリー残量が大きく変動しない限りスマホ側に最新データが送られていませんでした。
2. **バックグラウンド受信用サービス (`MobileWearableListenerService`) での保存処理漏れ**:
   スマホアプリが非アクティブな状態でウォッチ側から `/watch_battery` データが届いた際、`MobileWearableListenerService` でログ出力のみを行い、データベース（`BatteryRepository`）への保存を実行していませんでした。

---

## 2. 変更対象コンポーネント

### モバイルモジュール (`:mobile`)
- **`BatteryMonitorService.kt`** `[MODIFY]` : `requestWatchBatteryUpdate()` メソッドを復元し、定期タイマー実行時および初期起動時にウォッチノードへ更新要求を送信するように修正
- **`MobileWearableListenerService.kt`** `[MODIFY]` : データ受信時に `BatteryRepository` を呼び出してデータベースへ受領データを永続化する処理を追加

### ドキュメント類 (`docs/`)
- **`docs/plans/20260926_fix_watch_battery_history_recording.md`** `[NEW]` : 本修正の計画書
- **`docs/walkthroughs/20260926_fix_watch_battery_history_recording.md`** `[NEW]` : 本修正実績のウォークスルー報告書

---

## 3. 実装ステップ

### ステップ 1: `BatteryMonitorService.kt` への `requestWatchBatteryUpdate()` の復元
- `requestWatchBatteryUpdate()` を定義し、`Wearable.getNodeClient(this).connectedNodes` から接続中のすべてのウォッチノードへ `/request_battery_update` メッセージを送信します。
- 定期更新タイマー（`startWatchUpdateTimer`）実行時および初期起動時に呼び出します。

### ステップ 2: `MobileWearableListenerService.kt` での DB 永続化の追加
- `/watch_battery` パスの `TYPE_CHANGED` イベント受信時、`BatteryRepositoryImpl` を使用して `BatteryEntry` インスタンスを生成し、`repository.insertEntry()` でデータベースへ確実に保存します。

### ステップ 3: テスト実行・ビルド検証とウォークスルー報告書の作成
- 単体テスト（`:mobile:testDebugUnitTest`）およびビルド（`:mobile:assembleDebug`）を実行し、成功を確認します。
- 実績レポート `docs/walkthroughs/20260926_fix_watch_battery_history_recording.md` を作成します。

---

## 4. 検証計画

- `gradle_build(":mobile:testDebugUnitTest")` で全テストが合格することを確認します。
- `gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。
