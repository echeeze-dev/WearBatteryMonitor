# スマートウォッチ最新バッテリー情報のリアルタイム表示修復計画

## 1. インプット（ユーザー様からのご指示・フィードバック一覧）

本不具合対応および修正計画は、以下のユーザー様からのご指示をインプットとして作成いたしました。

### インプット（ご指摘および 1 点目の修正指示）
- **日時**: 2026年10月10日 10:55頃
- **関連履歴 No**: **`#10`**, **`#11`**
- **ご指示原文**:
  > 「では本題です。
  > スマホアプリに表示するウォッチのバッテリー情報についていくつか気になる点があります。
  > まずは原因調査をお願いします。
  > 1. ウォッチの現状のバッテリー残量がーー表示になっている。バッテリー情報の履歴追加時には表示されるようですが、履歴追加に無関係に最新の情報を表示してほしいです。」

いただいた 4 件のご要望のうち、まずは **「1 点目：スマートウォッチ最新バッテリー情報のリアルタイム表示修復」** に集中して対応いたします。

---

## 2. 目的と原因分析

スマートウォッチが接続中である際、履歴追加のタイミングに関わらず、Wear OS から受信した最新のバッテリー残量（%）および充電状態が画面上にリアルタイム表示されるように修正します。

### 原因分析
1. **`BatteryMonitorService` での最新残量保持の欠落**:
   常駐サービス（`BatteryMonitorService`）において、`currentPhonePercent` や `currentEarphonePercent` はリアルタイム変数として保持されていたのに対し、スマートウォッチ用の `currentWatchPercent` および `currentWatchIsCharging` 変数が存在していませんでした。
2. **サービスからのリアルタイム同期漏れ**:
   Wear OS から残量データが届いた際、サービス側で変数として保持されていなかったため、`syncFromService()` 実行時に ViewModel へ最新の残量がリアルタイム反映されず、DB に新しい履歴が保存されて再ロードされるまで画面上が `---`（`null`）のままになってしまっていました。

---

## 3. 変更対象コンポーネント

### モバイルモジュール (`:mobile`)
- **`BatteryMonitorService.kt`** `[MODIFY]` : 
  - `currentWatchPercent: Int?` および `currentWatchIsCharging: Boolean` 変数を新設します。
  - Wear OS からデータを受領した際（`onWatchBatteryReceived`）、最新の残量と充電状態を即座に変数は保持し、`syncAllData()` および画面へのリアルタイム通知を実行します。
- **`MainActivity.kt`** `[MODIFY]` : 
  `syncFromService()` 内において、サービスが保持する最新の `currentWatchPercent` および `currentWatchIsCharging` を `viewModel.updateWatchBattery()` 経由で画面（`MainUiState`）へ即座に更新・反映します。

### ドキュメント類 (`docs/`)
- **`docs/plans/20261010_fix_watch_battery_realtime_display.md`** `[NEW]` : 本修正の計画書（本日日付: 2026年10月10日）
- **`docs/walkthroughs/20261010_fix_watch_battery_realtime_display.md`** `[NEW]` : 本修正実績のウォークスルー報告書

---

## 4. 実装ステップ

### ステップ 1: タスク対応履歴 (`task_history.md`) への事前新規登録
- ルール `[R11]` に従い、本タスクを `#19` として `docs/task_history.md` へ `[IN_PROGRESS]` 状態で事前新規登録します。

### ステップ 2: `BatteryMonitorService.kt` でのウォッチ最新状態保持の実装
- `currentWatchPercent` および `currentWatchIsCharging` 変数を追加し、`onWatchBatteryReceived` コールバック内で最新値を保持するように対応します。

### ステップ 3: `MainActivity.kt` でのリアルタイム同期の追加
- `syncFromService()` 内にて、サービスの最新ウォッチ残量を `viewModel.updateWatchBattery()` へ渡し、履歴追加の有無に関わらず常に最新のバッテリー数値（%）が画面へ反映されるように修復します。

### ステップ 4: 静的解析およびビルド検証
- `analyze_file` によりエラーや不要な警告がないことを確認します。
- テスト自動実行は行わず、`gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。

### ステップ 5: ウォークスルー実績書の作成 (`docs/walkthroughs/`)
- 実績レポート `docs/walkthroughs/20261010_fix_watch_battery_realtime_display.md` を作成します。

---

## 5. 検証計画

- `analyze_file` で確認を行います。
- `gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。
