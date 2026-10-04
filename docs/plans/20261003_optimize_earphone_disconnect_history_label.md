# ワイヤレスイヤホン切断時の履歴リスト表記最適化計画

## 1. インプット（ユーザー様からのご指示）

- **インプット日時**: 2026年10月3日 10:42頃
- **ユーザー様からのご指示原文**:
  > 「・(恐らく)ワイヤレスイヤホンの切断時のリスト上の情報が"BT Battery LinkBuds - Connected と表記されているのですが、切断時も接続時と同様に"Bluetooth LinkBuds - Disconnected"とリスト情報に残したいです。可能でしょうか？」

ご指示いただいた 5 件のご要望のうち、**「4 点目：ワイヤレスイヤホン切断時の履歴リスト表記最適化」** に対応いたします。

---

## 2. 目的と原因分析

ワイヤレスイヤホンの切断イベント時、履歴リストにおいて切断されたデバイス名（例: "LinkBuds"）を保持しつつ、状態を「Disconnected」として正しく分かりやすく表記できるように不具合を修復します。

### 原因分析
`HistoryDetailDialog.kt` における `details` 行の判定ロジックにおいて、`if (entry.percent == -1)` のとき `if (entry.deviceName != null) "Connected" else "Disconnected"` と判定していました。そのため、切断ログであっても `deviceName != null`（デバイス名が存在する）場合は無条件に `"Connected"` と表記されてしまっていました。

---

## 3. 変更対象コンポーネント

### モバイルモジュール (`:mobile`)
- **`monitor/EarphoneBatteryMonitor.kt`** `[MODIFY]` : 
  Bluetooth イヤホンの切断検知時（`handleDisconnection`）、直前まで接続されていたデバイス名を保持して `reason = "BT_DISCONNECT"` として通知するように修正します。
- **`ui/components/HistoryDetailDialog.kt`** `[MODIFY]` : 
  `recordReason == "BT_DISCONNECT"` 時の表示ロジックを修正し、切断ログに対してデバイス名と `"Disconnected"` の組み合わせ（例: `LinkBuds • Disconnected`）で正しくラベル表記されるように改修します。

### ドキュメント類 (`docs/`)
- **`docs/plans/20261003_optimize_earphone_disconnect_history_label.md`** `[NEW]` : 本修正の計画書（本日日付: 2026年10月3日）
- **`docs/walkthroughs/20261003_optimize_earphone_disconnect_history_label.md`** `[NEW]` : 本修正実績のウォークスルー報告書

---

## 4. 実装ステップ

### ステップ 1: `EarphoneBatteryMonitor.kt` での切断デバイス名と `BT_DISCONNECT` 理由の保持
- イヤホン切断検知時 (`handleDisconnection`)、切断されたデバイスの `device.name` を保持して `reason = "BT_DISCONNECT"` として `onEarphoneChanged` を発火させます。

### ステップ 2: `HistoryDetailDialog.kt` での接続/切断表記の補正
- `recordReason == "BT_DISCONNECT"` の場合は表記タイトルを `🔗 Bluetooth` とし、詳細行に `[デバイス名] • Disconnected`（例: `LinkBuds • Disconnected`）と明確に表示します。

### ステップ 3: 静的解析およびビルド検証
- `analyze_file` によりエラーや不要な警告がないことを確認します。
- テスト自動実行は行わず、`gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。

### ステップ 4: ウォークスルー実績書の作成 (`docs/walkthroughs/`)
- 実績レポート `docs/walkthroughs/20261003_optimize_earphone_disconnect_history_label.md` を作成します。

---

## 5. 検証計画

- `analyze_file` で確認を行います。
- `gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。
