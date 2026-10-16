# Bluetooth デバイス (イヤホン) 用 Wear OS タイル & コンプリケーション新設計画

## 1. インプット（ユーザー様からのご指示）

- **インプット日時**: 2026年10月17日 00:05頃
- **ユーザー様からのご指示原文**:
  > 「さらに追加機能です。
  > ウォッチでもスマホでもない、主にワイヤレスイヤホンを想定した、Bluetoothデバイスのバッテリー情報を表示するコンプリケーションとタイルを追加したいです。」

---

## 2. 目的と技術的設計

Wear OS スマートウォッチにおいて、スマホに接続された Bluetooth デバイス（主にワイヤレスイヤホン等）のバッテリー残量およびデバイス名を表示する **専用タイル (`EarphoneBatteryTileService`)** および **専用コンプリケーション (`EarphoneComplicationService`)** を新設・追加します。

---

## 3. 変更対象コンポーネント

### ウェアモジュール (`:wear`)
- **`data/WearBatteryRepository.kt` & `WearBatteryRepositoryImpl.kt`** `[MODIFY]` : 
  イヤホンのバッテリーデータおよび履歴抽出メソッドを追加・拡張します。
- **`tile/EarphoneBatteryTileService.kt`** `[NEW]` : 
  Bluetooth デバイス（イヤホン）のバッテリー情報を表示し、タップでアプリ起動する専用タイルサービスを新設します。
- **`complication/EarphoneComplicationService.kt`** `[NEW]` : 
  Bluetooth デバイスのバッテリー数値を表示する専用コンプリケーションサービスを新設します。
- **`AndroidManifest.xml`** `[MODIFY]` : 
  新設したタイルサービスおよびコンプリケーションサービスを登録宣言します。

### ドキュメント類 (`docs/`)
- **`docs/plans/20261017_add_earphone_tile_and_complication.md`** `[NEW]` : 本機能追加の計画書（本日日付: 2026年10月17日）
- **`docs/walkthroughs/20261017_add_earphone_tile_and_complication.md`** `[NEW]` : 本機能追加実績のウォークスルー報告書
