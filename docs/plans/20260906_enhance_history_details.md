# 履歴情報の詳細化とBluetooth接続記録の改善計画

スマホアプリの履歴リストにおいて、ウォッチのデバイス名と正確な理由を表示できるようにし、さらにカーナビ等のバッテリー情報を持たないBluetoothデバイスの接続ログも残せるように改善します。

## 修正内容

### 1. ウォッチデータの拡張（Wear側 修正）
- **[MODIFY]** `MainActivity.kt (wear)`:
    - スマホへのデータ送信時（`/watch_battery`）に、以下の情報を追加で送信します。
        - `device_name`: ウォッチのモデル名（`Build.MODEL`）。
        - `record_reason`: 送信のきっかけ（バッテリー変化、定期更新、要求応答など）。

### 2. Bluetooth接続ログの改善（Mobile側 修正）
- **[MODIFY]** `BatteryMonitorService.kt`:
    - **接続記録の強化**: Bluetoothデバイスが接続された際（ACL_CONNECTED）、バッテリー情報が取得できなくても「名前」を伴って履歴に1件記録します。これにより、カーナビ等の接続履歴も残るようになります。
    - **表記の分かりやすさ向上**: 内部定数 `BT_BATTERY_CHANGED` を `BT_UPDATE` に改名し、UI上の表示を「🎧 BT Change」から **「🎧 BT Battery」** 等のより直感的な表現に変更します。

### 3. スマホ側でのデータ受け入れ（Mobile側 修正）
- **[MODIFY]** `BatteryMonitorService.kt` / `MainActivity.kt`:
    - ウォッチから受信した `device_name` と `record_reason` を、履歴データベース（Room）の `deviceName` および `recordReason` フィールドに正しく保存するようにします。

### 4. UIの微調整（Mobile側 修正）
- **[MODIFY]** `MainActivity.kt`:
    - 履歴リストの理由表示を更新。
        - `BT_UPDATE`: 「🎧 BT Battery」
        - `BT_CONNECTION`: 「🔗 BT Connect」
        - ウォッチから届いた理由をアイコン付きで適切にマッピング。
    - バッテリー不明（-1%）でもデバイス名がある場合は、右側の残量を「—」としつつ、詳細欄に名前を表示して「接続されていたこと」を表現します。

## 検証計画

### 自動テスト
- `gradle_assemble_all` を実行し、ビルドエラーがないことを確認します。

### 手動確認
- **ウォッチ履歴**: スマホの履歴リストに、ウォッチのモデル名（例：Pixel Watch）と具体的な理由が表示されることを確認します。
- **カーナビ/BT接続**: バッテリー非対応のBTデバイスを接続した際、リストにその名前が記録され、残量が「—」となっていることを確認します。
- **表記**: 「BT Change」がより分かりやすい表記に変わっていることを確認します。
