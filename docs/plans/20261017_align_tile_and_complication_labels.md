# コンプリケーションおよびタイルの設定ラベル名称の一貫性修復計画

## 1. インプット（ユーザー様からのご指示・フィードバック）

- **インプット日時**: 2026年10月17日 00:12頃
- **関連履歴 No**: **`#28`**, **`#29`**, **`#31`**
- **ユーザー様からのご指示原文**:
  > 「コンプリケーション名の一貫性がないのでスマホのバッテリー情報の」

---

## 2. 目的と統一命名設計

Wear OS の文字盤追加画面およびタイル追加画面において、スマホ用と Bluetooth デバイス用で不揃いになっていた設定表示ラベル（Manifest / strings.xml の `android:label`）を一貫性のある統一名称へ修復します。

---

## 3. 変更対象コンポーネント

### ウェアモジュール (`:wear`)
- **`res/values/strings.xml`** `[MODIFY]` : 
  `complication_label` を `"Phone Battery"` に、`tile_label` を `"Phone Battery Tile"` に修正・更新します。
- **`AndroidManifest.xml`** `[MODIFY]` : 
  各サービスの `android:label` 参照を統合・統一いたします。

### ドキュメント類 (`docs/`)
- **`docs/plans/20261017_align_tile_and_complication_labels.md`** `[NEW]` : 本修正の計画書（本日日付: 2026年10月17日）
- **`docs/walkthroughs/20261017_align_tile_and_complication_labels.md`** `[NEW]` : 本修正実績のウォークスルー報告書
