# タイルおよびコンプリケーションタップ時のアプリ自動起動対応計画

## 1. インプット（ユーザー様からのご指示）

- **インプット日時**: 2026年10月17日 00:01頃
- **ユーザー様からのご指示原文**:
  > 「新機能を追加してください。
  > ウォッチアプリのタイルとコンプリケーションをタッチしたときに、BattSyncアプリを起動するようにしたいです。」

---

## 2. 目的と技術的設計

Wear OS スマートウォッチ側の「タイル (Tile)」および「コンプリケーション (Complication)」要素をタップ（タッチ）した際、ウォッチ用 BattSync メイン画面 (`com.echeeze.wearbatterymonitor.presentation.MainActivity`) を自動起動するタップアクションを追加します。

---

## 3. 変更対象コンポーネント

### ウェアモジュール (`:wear`)
- **`tile/PhoneBatteryTileService.kt`** `[MODIFY]` : 
  タイルのレイアウト要素に `LaunchAction` Clickable モディファイアを追加します。
- **`complication/MainComplicationService.kt`** `[MODIFY]` : 
  `ShortTextComplicationData` に `PendingIntent` タップアクション（`setTapAction`）を追加します。

### ドキュメント類 (`docs/`)
- **`docs/plans/20261017_launch_app_from_tile_and_complication.md`** `[NEW]` : 本機能追加の計画書（本日日付: 2026年10月17日）
- **`docs/walkthroughs/20261017_launch_app_from_tile_and_complication.md`** `[NEW]` : 本機能追加実績のウォークスルー報告書
