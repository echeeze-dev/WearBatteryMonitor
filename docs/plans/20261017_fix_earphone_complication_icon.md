# イヤホン用コンプリケーションのアイコン表示修復計画

## 1. インプット（ユーザー様からのご指示・フィードバック）

- **インプット日時**: 2026年10月17日 00:08頃
- **関連履歴 No**: **`#29`**
- **ユーザー様からのご指示原文**:
  > 「コンプリケーションに表示されているアイコンがスマホになっていませんか？」

---

## 2. 目的と原因分析

イヤホン用コンプリケーション (`EarphoneComplicationService.kt`) において、アイコン画像がスマホ用 (`ic_phone_battery`) になってしまっていた表示不具合を修正し、Bluetooth イヤホンに適した専用アイコン (`ic_bluetooth_battery` 等) に切り替えて表示するように改善します。

---

## 3. 変更対象コンポーネント

### ウェアモジュール (`:wear`)
- **`res/drawable/ic_bluetooth_battery.xml`** `[NEW]` : 
  イヤホン・Bluetooth 機器用のベクトルドローアブルアイコンリソースを新設します。
- **`complication/EarphoneComplicationService.kt`** `[MODIFY]` : 
  `Icon.createWithResource` で指定しているアイコンを `R.drawable.ic_bluetooth_battery` へ差し替えます。

### ドキュメント類 (`docs/`)
- **`docs/plans/20261017_fix_earphone_complication_icon.md`** `[NEW]` : 本修正の計画書（本日日付: 2026年10月17日）
- **`docs/walkthroughs/20261017_fix_earphone_complication_icon.md`** `[NEW]` : 本修正実績のウォークスルー報告書
