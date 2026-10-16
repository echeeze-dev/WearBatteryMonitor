# イヤホン用コンプリケーション/タイルのデータ更新要求漏れ修復計画

## 1. インプット（ユーザー様からのご指示・フィードバック）

- **インプット日時**: 2026年10月17日 00:15頃
- **関連履歴 No**: **`#29`**, **`#30`**, **`#31`**
- **ユーザー様からのご指示原文**:
  > 「コンプリケーションのBluetoothデバイスのバッテリー情報について、ワイヤレスイヤホンを接続している状態でスマホにバッテリー情報が出ていてもコンプリケーションにパーセンテージが表示されず-%表示のままです。原因を調査してください。」

---

## 2. 目的と原因分析

ワイヤレスイヤホンが接続されてスマホ画面にバッテリー情報が表示されている際、Wear OS の文字盤コンプリケーションおよびタイル画面にリアルタイムで残量数値 (%) が反映されず `--%` のまま止まってしまう不具合を修正します。

### 原因分析
Wear OS 側のデータ更新受信用サービス (`TileDataUpdateService.kt`) において、スマホからの同期データマップ (`/battery_status`) の更新を検知した際、スマホ用のコンプリケーション (`MainComplicationService`) とスマホ用タイル (`PhoneBatteryTileService`) にしか再描画リクエストを出していませんでした。
新設したイヤホン用コンプリケーション (`EarphoneComplicationService`) およびイヤホン用タイル (`EarphoneBatteryTileService`) への再更新リクエスト (`ComplicationDataSourceUpdateRequester` / `TileService.getUpdater`) の呼び出しが漏れていたため、文字盤上の数値が同期更新されず `--%` のままになっていました。

---

## 3. 変更対象コンポーネント

### ウェアモジュール (`:wear`)
- **`tile/TileDataUpdateService.kt`** `[MODIFY]` : 
  `/battery_status` の更新受信時、`EarphoneComplicationService` および `EarphoneBatteryTileService` に対する画面再描画リクエスト（`requestUpdateAll` / `requestUpdate`）の呼び出しを追加します。

### ドキュメント類 (`docs/`)
- **`docs/plans/20261017_fix_earphone_complication_update_trigger.md`** `[NEW]` : 本修正の計画書（本日日付: 2026年10月17日）
- **`docs/walkthroughs/20261017_fix_earphone_complication_update_trigger.md`** `[NEW]` : 本修正実績のウォークスルー報告書

---

## 4. 実装ステップ

### ステップ 1: `task_history.md` への事前新規登録 (`#33`)
- ルール `[R11]` に従い、`docs/task_history.md` にタスク `#33`（イヤホン用コンプリケーションデータ更新要求の修復）を `[IN_PROGRESS]` 状態で事前登録します。

### ステップ 2: `TileDataUpdateService.kt` での更新トリガー呼び出しの追加
- `onDataChanged()` 内において、`EarphoneComplicationService` への `ComplicationDataSourceUpdateRequester.create(...).requestUpdateAll()` および `EarphoneBatteryTileService` への `TileService.getUpdater(...).requestUpdate(...)` を追加します。

### ステップ 3: 静的解析およびビルド検証
- `analyze_file` によりエラーや不要な警告がないことを確認します。
- テスト自動実行は行わず、`gradle_build(":wear:assembleDebug")` が正常に成功することを確認します。

### ステップ 4: ウォークスルー実績書の作成 (`docs/walkthroughs/`)
- 実績レポート `docs/walkthroughs/20261017_fix_earphone_complication_update_trigger.md` を作成します。

---

## 5. 検証計画

- `analyze_file` で確認を行います。
- `gradle_build(":wear:assembleDebug")` が正常に成功することを確認します。
