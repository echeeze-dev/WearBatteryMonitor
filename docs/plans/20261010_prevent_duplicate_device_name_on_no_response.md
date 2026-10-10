# 応答なし (No Response) ログ発生時におけるグラフ内デバイス名重複描画抑止計画

## 1. インプット（ユーザー様からのご指示・フィードバック）

- **インプット日時**: 2026年10月4日 10:55頃
- **関連履歴 No**: **`#13`**, **`#15`**
- **ユーザー様からのご指示原文**:
  > 「2. グラフ中のデバイス名表示が頻繁に表示されている。履歴情報を比較すると、Watch Google Pixel Watch 2 - No Responseのデータがあるときに毎回表示してしまっているようです。デバイスが変わったとき・新たに接続したときにのみ表示してほしいです。」

---

## 2. 目的と原因分析

スマートウォッチの応答なし（`No Response`）ログ（`percent = -1`, `recordReason = "WATCH_NO_RESPONSE"`）がデータベースに定期記録された際、グラフ描画（`HistoryGraph.kt`）において、同じデバイス名であるにもかかわらず高頻度でデバイス名テキストが再描画されてしまう不具合を修復します。

### 原因分析
`HistoryGraph.kt` の描画ロジックにおいて、`percent == -1`（切断・応答なし）のエントリーを通過した際、`lastDrawnDeviceName = null` と内部変数がリセットされていました。このため、次の応答なしログエントリー（`deviceName = "Google Pixel Watch 2"`）を描画する際、同一端末であるにもかかわらず「新しいデバイスが接続された」と誤判定されてテキストが重複描画されてしまっていました。

---

## 3. 変更対象コンポーネント

### モバイルモジュール (`:mobile`)
- **`ui/components/HistoryGraph.kt`** `[MODIFY]` : 
  切断（`BT_DISCONNECT` / `WATCH_DISCONNECT`）の場合にのみ `lastDrawnDeviceName = null` をクリアし、単なる通信応答なし（`WATCH_NO_RESPONSE`）等で同じ端末名が継続している間はデバイス名テキストの無用な重複再描画を行わないよう描画判定を補正します。

### ドキュメント類 (`docs/`)
- **`docs/plans/20261010_prevent_duplicate_device_name_on_no_response.md`** `[NEW]` : 本修正の計画書（本日日付: 2026年10月10日）
- **`docs/walkthroughs/20261010_prevent_duplicate_device_name_on_no_response.md`** `[NEW]` : 本修正実績のウォークスルー報告書

---

## 4. 実装ステップ

### ステップ 1: `HistoryGraph.kt` での `lastDrawnDeviceName` クリア条件の厳格化
- `entry.percent == -1` かつ明示的な切断ログ（`BT_DISCONNECT` / `WATCH_DISCONNECT`）が発生した場合にのみ `lastDrawnDeviceName = null` とし、同端末の `No Response` ログ継続時はテキスト重複描画を抑止します。

### ステップ 2: 静的解析およびビルド検証
- `analyze_file` によりエラーや不要な警告がないことを確認します。
- テスト自動実行は行わず、`gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。

### ステップ 3: ウォークスルー実績書の作成 (`docs/walkthroughs/`)
- 実績レポート `docs/walkthroughs/20261010_prevent_duplicate_device_name_on_no_response.md` を作成します。

---

## 5. 検証計画

- `analyze_file` で確認を行います。
- `gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。
