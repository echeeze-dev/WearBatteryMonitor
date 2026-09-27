# スマホアプリ側からの Wear 同期データ不完全性の修正計画（:mobile のみ修正）

## 1. 目的と原因分析

ウォッチアプリ（`:wear`）のコードを一切変更することなく、スマホアプリ（`:mobile`）側のデータ送信ロジックを修正することで、ウォッチアプリ上のグラフ表示をスマホアプリ側のグラフと完全に一致させます。

### 原因分析
1. **一時メモリリストの送信**:
   スマホアプリ側（`BatteryRepositoryImpl` / `BatteryMonitorService`）から Wear へデータを送信する際、データベース内の完全な履歴データではなく、メモリ内に一時保持されている不完全なリストが送信されるケースがありました。
2. **送信時のデータサンプリング歪み**:
   `BatteryRepositoryImpl` の `downsampleHistory`（データ間引処理）において、データ件数が少ない場合や特定の時間区間においてタイムスタンプの整合性が崩れ、Wear 側で正常な時間軸として復元できなくなっていました。

---

## 2. 変更対象コンポーネント

### モバイルモジュール (`:mobile`) のみ修正
- **`data/BatteryRepositoryImpl.kt`** `[MODIFY]` : Wear へのデータ同期処理 (`syncStatusToWear`) において、データベースから取得した正確で完全な履歴データを送信し、ダウンサンプリング時のタイムスタンプ保持精度を向上
- **`BatteryMonitorService.kt`** `[MODIFY]` : データ同期時に DB からロードした完全な履歴リストを確実にリポジトリ経由で Wear へ同期するよう修正

### ドキュメント類 (`docs/`)
- **`docs/plans/20260926_fix_wear_sync_data_from_mobile.md`** `[NEW]` : 本修正の計画書（スマホアプリ側のみ修正）
- **`docs/walkthroughs/20260926_fix_wear_sync_data_from_mobile.md`** `[NEW]` : 本修正実績のウォークスルー報告書

---

## 3. 実装ステップ

### ステップ 1: スマホアプリ側 `BatteryRepositoryImpl.kt` の送信データ生成の改修
- `syncStatusToWear()` 実行時、DB から `getHistoryByType()` で最新かつ完全な履歴リストを取得して DataMap へパッキングします。
- `downsampleHistory()` における先頭（最古）データおよび末尾（最新）データのタイムスタンプ保持ロジックを厳格化し、時系列の歪みを防止します。

### ステップ 2: `BatteryMonitorService.kt` での送信データの完全性保証
- `syncAllData()` 実行時、メモリ内不完全リストではなく DB ロード完了後の履歴データを確実にリポジトリへ引き渡すように修正します。

### ステップ 3: テスト・ビルド検証とウォークスルー報告書の作成
- 単体テスト（`:mobile:testDebugUnitTest`）およびビルド（`:mobile:assembleDebug`）を実行し、成功を確認します。
- ウォッチアプリ（`:wear:assembleDebug`）のビルドが一切変更なしで成功することを確認します。
- 実績レポート `docs/walkthroughs/20260926_fix_wear_sync_data_from_mobile.md` を作成します。

---

## 4. 検証計画

- `gradle_build(":mobile:testDebugUnitTest")` で全テストが合格することを確認します。
- `gradle_build(":mobile:assembleDebug")` および `:wear:assembleDebug` が正常に成功することを確認します。
