# データ同期 (DATA_SYNC) 発生時における履歴 2 重重複保存の防止計画

## 1. インプット（ユーザー様からのご指示・フィードバック）

- **インプット日時**: 2026年10月4日 10:55頃
- **関連履歴 No**: **`#04`**
- **ユーザー様からのご指示原文**:
  > 「3. 履歴情報の Sync 時、毎回2回連続データが残っています。１度のみにできませんか？」

---

## 2. 目的と原因分析

Wear OS とのデータ同期（`DATA_SYNC`）発生時、データベース（`battery_history`）へ全く同じタイムスタンプ・残量のエントリーが 2 回連続で重複保存されてしまう問題を修復します。

### 原因分析
`WearDataSyncer` でデータ更新イベントを受領した際、`onWatchBatteryReceived` コールバックが発火して `addHistory` が実行された直後に、コールバック内で `syncAllData()` が呼び出されて再度 `addHistory` または通知同期が重複して走っていました。
また、履歴追加関数（`addHistory`）において、直前（例: 10秒以内）に同一デバイス・同一残量・同一理由のエントリーが保存済みの場合は挿入をスキップする重なり除外判定が欠落していました。

---

## 3. 変更対象コンポーネント

### モバイルモジュール (`:mobile`)
- **`BatteryMonitorService.kt` & `MainActivity.kt`** `[MODIFY]` : 
  `addHistory` 内において、リストの末尾エントリー（`list.lastOrNull()`）のタイムスタンプと内容を確認し、同一内容で 10秒以内に連続記録されようとした場合はデプ duplica (重複) として追加・DB 挿入をガード・スキップする判定を追加します。

### ドキュメント類 (`docs/`)
- **`docs/plans/20261010_prevent_duplicate_data_sync_history.md`** `[NEW]` : 本修正の計画書（本日日付: 2026年10月10日）
- **`docs/walkthroughs/20261010_prevent_duplicate_data_sync_history.md`** `[NEW]` : 本修正実績のウォークスルー報告書

---

## 4. 実装ステップ

### ステップ 1: `addHistory` での連続重複ガードの実装
- 追加しようとする `BatteryEntry` が直前（10秒以内）の同一デバイス種別・残量・理由のエントリーと重複している場合、無駄な DB 重複挿入をスキップします。

### ステップ 2: 静的解析およびビルド検証
- `analyze_file` によりエラーや不要な警告がないことを確認します。
- テスト自動実行は行わず、`gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。

### ステップ 3: ウォークスルー実績書の作成 (`docs/walkthroughs/`)
- 実績レポート `docs/walkthroughs/20261010_prevent_duplicate_data_sync_history.md` を作成します。

---

## 5. 検証計画

- `analyze_file` で確認を行います。
- `gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。
