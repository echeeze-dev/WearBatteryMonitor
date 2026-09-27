# Bluetooth イヤホン履歴記録不具合の修正計画（受信フラグ修正）

## 1. 目的と原因分析

Bluetooth イヤホン接続時および残量変更時に、ワイヤレスイヤホンの履歴データがデータベース（`battery_history`）および画面に記録・更新されなくなっていた不具合を修正します。

### 原因分析
`EarphoneBatteryMonitor.kt` において、Bluetooth ブロードキャスト受信機（`bluetoothReceiver`）を登録する際、誤って `Context.RECEIVER_NOT_EXPORTED`（アプリ外部からの受信拒否）を指定していました。このため、Android OS システムから発火される Bluetooth イベント（`ACTION_ACL_CONNECTED`, `BATTERY_LEVEL_CHANGED` 等）がセキュリティ制限によってアプリ側で一切受信できなくなっていました。

---

## 2. 変更対象コンポーネント

### モバイルモジュール (`:mobile`)
- **`monitor/EarphoneBatteryMonitor.kt`** `[MODIFY]` : `registerReceiver` の第 3 引数を `Context.RECEIVER_EXPORTED`（システムブロードキャスト受信許可）へ変更し、OS からの Bluetooth イベント受信を復元
- **`MainActivity.kt`** `[MODIFY]` : `syncFromService()` 内で、サービス側の最新履歴リストを画面の ViewModel へ正しく同期・更新する処理を追加

### ドキュメント類 (`docs/`)
- **`docs/plans/20260926_fix_earphone_history_recording.md`** `[NEW]` : 本修正の改訂計画書
- **`docs/walkthroughs/20260926_fix_earphone_history_recording.md`** `[NEW]` : 本修正実績のウォークスルー報告書

---

## 3. 実装ステップ

### ステップ 1: 原因分析と修正計画書の作成 (`docs/plans/`)
- 本修正計画書を作成し、`EarphoneBatteryMonitor.kt` の受信フラグ修正方針を明確化します。

### ステップ 2: `EarphoneBatteryMonitor.kt` の受信フラグ修正
- `registerReceiver` のフラグを `Context.RECEIVER_EXPORTED` に変更し、OS システムからの Bluetooth 接続・残量イベント受信を有効化します。

### ステップ 3: `MainActivity.kt` でのサービス履歴同期の追加
- `syncFromService()` 内にて、サービスで蓄積された最新の履歴データが画面の ViewModel にも反映されるよう同期処理を追加します。

### ステップ 4: ビルド検証とウォークスルー報告書の作成 (`docs/walkthroughs/`)
- `:mobile:assembleDebug` のビルド成功を確認後、実績レポート `docs/walkthroughs/20260926_fix_earphone_history_recording.md` を作成します。

---

## 4. 検証計画

- `gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。
