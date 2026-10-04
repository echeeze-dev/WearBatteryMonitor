# スマートウォッチ切断および応答なし時の履歴表記最適化計画

## 1. インプット（ユーザー様からのご指示・フィードバック一覧）

本最適化計画は、以下の 2 点のユーザー様からのご指示・フィードバックをインプットとして作成いたしました。

### インプット 1（スマートウォッチ切断時の表示最適化リクエスト）
- **日時**: 2026年10月4日 11:15頃
- **ご指示原文**:
  > 「追加対応お願いできますか？」
- **ご相談背景・コンテキスト**:
  ワイヤレスイヤホン切断時の履歴表示最適化（"LinkBuds • Disconnected"）の完了報告後、「スマートウォッチが切断された際の履歴表示」について確認された際、ワイヤレスイヤホンと同様に `⌚ Watch` 表記や `Galaxy Watch • Disconnected` の形式へ分かりやすく表示拡張してほしい、というご依頼を受けたものです。

### インプット 2（非接続と応答なしの区分け指示）
- **日時**: 2026年10月4日 11:28頃
- **ご指示原文**:
  > 「非接続と応答なしは判別可能ですか？可能なら分かるようにしておいてほしいです。」

---

## 2. 目的と表記設計

スマートウォッチの接続切断ログにおいて、物理的な「非接続（`Disconnected`）」と、接続中だがデータが返ってこない「応答なし（`No Response`）」を技術的に明確に判別し、履歴リストダイアログで判別表示できるように最適化します。

### 区分け表記設計
1. **非接続 (`WATCH_DISCONNECT`)**:
   - 条件: `connectedNodes.isEmpty()`（物理的な Bluetooth/Wearable ネットワーク切断）
   - **表題**: `⌚ Watch`
   - **詳細**: `[ウォッチ端末名] • Disconnected`
2. **応答なし (`WATCH_NO_RESPONSE`)**:
   - 条件: `connectedNodes` は存在するが、定期監視時に一定時間以上ウォッチからバッテリーデータが返ってこない場合
   - **表題**: `⌚ Watch`
   - **詳細**: `[ウォッチ端末名] • No Response`

---

## 3. 変更対象コンポーネント

### モバイルモジュール (`:mobile`)
- **`BatteryMonitorService.kt`** `[MODIFY]` : 
  Wearable API の `connectedNodes` ノード存在有無と前回のデータ受領時刻から、「非接続 (`WATCH_DISCONNECT`)」と「応答なし (`WATCH_NO_RESPONSE`)」を明確に判別してデータベースへ保存するように修正します。
- **`ui/components/HistoryDetailDialog.kt`** `[MODIFY]` : 
  `WATCH_DISCONNECT`（`Disconnected`）と `WATCH_NO_RESPONSE`（`No Response`）のそれぞれについて、分かりやすいラベル詳細を表示できるように拡張します。

### ドキュメント類 (`docs/`)
- **`docs/plans/20261004_optimize_watch_disconnect_history_label.md`** `[NEW]` : 本修正の計画書（本日日付: 2026年10月4日）
- **`docs/walkthroughs/20261004_optimize_watch_disconnect_history_label.md`** `[NEW]` : 本修正実績のウォークスルー報告書

---

## 4. 実装ステップ

### ステップ 1: `BatteryMonitorService.kt` での非接続 / 応答なし判別ロジックの実装
- 定期タイマー実行時、`connectedNodes` を検索します。
  - `nodes.isEmpty()` ➔ `recordReason = "WATCH_DISCONNECT"`（非接続）として記録
  - `nodes.isNotEmpty()` かつ一定時間データ未受領 ➔ `recordReason = "WATCH_NO_RESPONSE"`（応答なし）として記録

### ステップ 2: `HistoryDetailDialog.kt` での区分け表記追加
- `WATCH_DISCONNECT` ➔ `[ウォッチ名] • Disconnected`
- `WATCH_NO_RESPONSE` ➔ `[ウォッチ名] • No Response`

### ステップ 3: 静的解析およびビルド検証
- `analyze_file` によりエラーや不要な警告がないことを確認します。
- テスト自動実行は行わず、`gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。

### ステップ 4: ウォークスルー実績書の作成 (`docs/walkthroughs/`)
- 実績レポート `docs/walkthroughs/20261004_optimize_watch_disconnect_history_label.md` を作成します。

---

## 5. 検証計画

- `analyze_file` で確認を行います。
- `gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。
