# 端末シャットダウンおよびバッテリー切れ要因の記録機能追加計画

## 1. インプット（ユーザー様からのご指示・フィードバック一覧）

本不具合対応および機能追加は、以下の 2 点のユーザー様からのご指示・フィードバックをインプットとして作成いたしました。

### インプット 1（ご相談および機能リクエスト）
- **日時**: 2026年10月3日 09:40頃
- **ご指示原文**:
  > 「・スマホのバッテリーが切れてシャットダウンする場合、その情報も保持できませんか？バックグラウンドサービスが動作していてシャットダウンするとき、その要因などが分かれば一緒に保持してほしいです。可能ならほかのデバイスも同様にしたいですが、Bluetooth接続デバイスがバッテリー切れかどうかは、さすがに分からないですかね？」

### インプット 2（範囲絞り込みおよび方針指示）
- **日時**: 2026年10月3日 09:48頃
- **ご指示原文**:
  > 「Bluetoothイヤホンの対応はしなくていいです。無理やり条件を作ってしまうとよくわからなくなってしまうので。
  > その他の対応について進めてください。」

---

## 2. 目的と技術的アプローチ

スマートフォン本体およびスマートウォッチのバッテリー切れ・電源オフによるシャットダウン発生時において、その確定要因（`SYSTEM_SHUTDOWN` / `WATCH_SHUTDOWN`）をバッテリー履歴データベース（`battery_history`）へ永続保存する仕組みを追加します。

※ Bluetooth イヤホンについては、プロファイル上の確実なインテントが存在しないため無理な条件付けは行わず、対象から除外いたします。

### 技術的アプローチ
1. **スマホ本体のシャットダウン検知**:
   Android システムが電源オフ/バッテリー切れの直前に送信する確定システムインテント **`Intent.ACTION_SHUTDOWN`** および **`Intent.ACTION_BATTERY_LOW`** を `PhoneBatteryMonitor` で検知し、即座に `recordReason = "SYSTEM_SHUTDOWN"`（または `"BATTERY_LOW"`）としてデータベースへ同期保存します。
2. **スマートウォッチのシャットダウン検知 (Wear OS)**:
   Wear OS 側で `Intent.ACTION_SHUTDOWN` を検知した際、スマホ側へ緊急メッセージパケットを送信し、`recordReason = "WATCH_SHUTDOWN"` としてスマホのデータベースへ記録します。

---

## 3. 変更対象コンポーネント

### モバイルモジュール (`:mobile`)
- **`monitor/PhoneBatteryMonitor.kt`** `[MODIFY]` : 
  `Intent.ACTION_SHUTDOWN` および `Intent.ACTION_BATTERY_LOW` インテントの受信登録と解析処理を追加します。
- **`BatteryMonitorService.kt` & `MainActivity.kt`** `[MODIFY]` : 
  シャットダウン要因を受け取り、データベースへ保存するロジックを接続します。
- **`ui/components/HistoryDetailDialog.kt`** `[MODIFY]` : 
  履歴詳細ダイアログにて `SYSTEM_SHUTDOWN` (🛑 Shutdown) や `BATTERY_LOW` (⚠️ Low Battery) などのアイコン・タイトルを綺麗に表示できるようにダイアログ表記を拡張します。

### ウェアモジュール (`:wear`)
- **`presentation/MainActivity.kt`** `[MODIFY]` : 
  Wear OS 端末自身が `Intent.ACTION_SHUTDOWN` を受信した際、スマホ側へ `recordReason = "WATCH_SHUTDOWN"` のパケットを即時送信する処理を追加します。

### ドキュメント類 (`docs/`)
- **`docs/plans/20261003_add_shutdown_reason_logging.md`** `[NEW]` : 本機能追加の計画書（本日日付: 2026年10月3日）
- **`docs/walkthroughs/20261003_add_shutdown_reason_logging.md`** `[NEW]` : 本機能追加実績のウォークスルー報告書

---

## 4. 実装ステップ

### ステップ 1: `PhoneBatteryMonitor.kt` でのシャットダウンインテント検知の実装
- `IntentFilter` に `Intent.ACTION_SHUTDOWN` および `Intent.ACTION_BATTERY_LOW` を追加し、受信時に `onBatteryChanged` または専用コールバック経由で要因（`SYSTEM_SHUTDOWN` / `BATTERY_LOW`）を通知します。

### ステップ 2: ウェア側 `WearMainActivity.kt` でのシャットダウン通知の実装
- Wear OS 側で `ACTION_SHUTDOWN` を受信する BroadcastReceiver を追加し、`sendWatchBatteryToPhone("WATCH_SHUTDOWN")` を緊急送信します。

### ステップ 3: 履歴詳細ダイアログ (`HistoryDetailDialog.kt`) の表示表記拡張
- シャットダウンやバッテリー切れ警告のログエントリーに対して、分かりやすいアイコンとテキスト（例: `🛑 Shutdown`, `⚠️ Low Battery`）を表示するように拡張します。

### ステップ 4: 静的解析およびビルド検証
- `analyze_file` によりエラーや不要な警告がないことを確認します。
- テスト自動実行は行わず、`gradle_build(":mobile:assembleDebug")` および `:wear:assembleDebug` のビルド成功を確認します。

### ステップ 5: ウォークスルー実績書の作成 (`docs/walkthroughs/`)
- 実績レポート `docs/walkthroughs/20261003_add_shutdown_reason_logging.md` を作成します。

---

## 5. 検証計画

- `analyze_file` で確認を行います。
- `gradle_build(":mobile:assembleDebug")` および `:wear:assembleDebug` が正常に成功することを確認します。
