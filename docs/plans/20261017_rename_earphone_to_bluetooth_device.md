# タイル・コンプリケーション表示名称の Bluetooth Device への変更計画

## 1. インプット（ユーザー様からのご指示・フィードバック）

- **インプット日時**: 2026年10月17日 00:10頃
- **関連履歴 No**: **`#29`**, **`#30`**
- **ユーザー様からのご指示原文**:
  > 「名称をイヤホンにするのはやめてください。」

---

## 2. 目的と表記変更設計

Wear OS 側の新設タイルおよびコンプリケーションにおいて、イヤホン以外の各種 Bluetooth 機器（カーナビ、スピーカー、ヘッドセット等）も汎用的にカバーできるよう、表示名称から特定の「イヤホン (Earphone)」という表記を撤去し、**「Bluetooth Device」（または「Bluetooth」）へと名称表示を汎用化** します。

---

## 3. 変更対象コンポーネント

### ウェアモジュール (`:wear`)
- **`AndroidManifest.xml`** `[MODIFY]` : 
  タイルおよびコンプリケーションのサービスラベル (`android:label`) を「Bluetooth Battery」系へと修正します。
- **`tile/EarphoneBatteryTileService.kt`** `[MODIFY]` : 
  デフォルト表記名を `"Bluetooth Device"` へ更新します。
- **`complication/EarphoneComplicationService.kt`** `[MODIFY]` : 
  表示用コンテンツ説明文を `"Bluetooth Battery"` へ更新します。

### ドキュメント類 (`docs/`)
- **`docs/plans/20261017_rename_earphone_to_bluetooth_device.md`** `[NEW]` : 本修正の計画書（本日日付: 2026年10月17日）
- **`docs/walkthroughs/20261017_rename_earphone_to_bluetooth_device.md`** `[NEW]` : 本修正実績のウォークスルー報告書
