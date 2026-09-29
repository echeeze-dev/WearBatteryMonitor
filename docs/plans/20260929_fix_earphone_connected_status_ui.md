# リアルタイム Bluetooth 接続状態に基づくデバイス表示への改訂計画

## 1. インプット（ユーザー様からのご指示・フィードバック一覧）

本不具合対応および修正計画は、以下の 2 点のユーザー様からのご指示・フィードバックをインプットとして作成いたしました。

### インプット 1（不具合報告および調査指示）
- **日時**: 2026年9月29日 22:38頃
- **ご指示原文**:
  > 「ワイヤレスイヤホンが接続されていないのにConnected表示がグラフの上に出ています。原因を調査してください。」

### インプット 2（修正 Plan への不承認および方針訂正指示）
- **日時**: 2026年9月29日 22:45頃
- **ご指示原文**:
  > 「そのplanは同意できません。バッテリー情報がとれないデバイスが接続される可能性があるからです。例えばカーナビなど。
  > 履歴の情報を参照してデバイス名やConnectedを表示しているなら、それはやめてください。あくまで現在のBluetoothデバイスとの接続状態をもとにこの表示はしてください。plan修正をお願いします。」

---

## 2. 目的と改訂方針

過去のデータベース履歴からのデバイス名復元処理（`latestEarphoneName` 等）を完全に撤去します。

バッテリー残量数値の有無にかかわらず、**「現在実際に OS 上で接続されているリアルタイムな Bluetooth デバイスの接続状態（`connectedDevices` / ACL 接続イベント）」のみを 100% 根拠として**、デバイス名および「Connected / Disconnected」の表示を行います。

---

## 3. 変更対象コンポーネント

### モバイルモジュール (`:mobile`)
- **`ui/MainViewModel.kt`** `[MODIFY]` : 
  `loadHistoryFromDb()` において、データベース履歴から過去のデバイス名を不必要に復元（`latestEarphoneName` 等）していた処理を**完全に削除**します。
- **`monitor/EarphoneBatteryMonitor.kt`** `[MODIFY]` : 
  現在実際に OS で接続されている Bluetooth デバイス（`connectedDevices` / ACL 接続イベント）のリアルタイム状態のみを検出し、接続デバイスが存在しない場合は直ちに `name = null` としてクリア通知します。
- **`ui/components/EarphoneSection.kt`** `[MODIFY]` : 
  現在接続中（`name != null`）の場合にデバイス名と `"$percent%"`（数値がない場合は `"Connected"`）を表示し、切断中（`name == null`）は直ちに `"Disconnected"` と非接続スタイルを表示します。

### ドキュメント類 (`docs/`)
- **`docs/plans/20260929_fix_earphone_connected_status_ui.md`** `[NEW]` : 本改訂計画書（本日日付: 2026年9月29日）
- **`docs/walkthroughs/20260929_fix_earphone_connected_status_ui.md`** `[NEW]` : 本改訂実績のウォークスルー報告書

---

## 4. 実装ステップ

### ステップ 1: DB 履歴からの過去デバイス名復元処理の完全削除 (`MainViewModel.kt`)
- `loadHistoryFromDb()` から過去の `deviceName` 読み出しロジックを全削除し、アプリ起動時はリアルタイムな検出結果のみを待機します。

### ステップ 2: リアルタイム Bluetooth 接続状態検知の徹底 (`EarphoneBatteryMonitor.kt`)
- `updateFromProfile()` および BroadcastReceiver において、現在実際に接続されている Bluetooth デバイス情報のみを `onEarphoneChanged` で通知し、接続デバイスが存在しない場合は即座に `(percent = null, name = null)` を発火させます。

### ステップ 3: リアルタイム状態に基づく UI 表示 (`EarphoneSection.kt`)
- 残量数値の有無を問わず、リアルタイム接続中（`name != null`）であればデバイス名と残量%（または `"Connected"`）を表示し、未接続時は `"Disconnected"` を表示します。

### ステップ 4: 静的解析およびビルド検証
- `analyze_file` によりエラーや警告がないことを確認します。
- テスト自動実行は行わず、`gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。

### ステップ 5: ウォークスルー実績書の作成 (`docs/walkthroughs/`)
- 実績レポート `docs/walkthroughs/20260929_fix_earphone_connected_status_ui.md` を作成します。

---

## 5. 検証計画

- `analyze_file` で確認を行います。
- `gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。
