# データ同期 (DATA_SYNC) 発生時における履歴 2 重重複保存の防止計画

## 1. インプット（ユーザー様からのご指示・フィードバック一覧）

本修正計画は、以下の 2 点のユーザー様からのご指示・フィードバックをインプットとして作成いたしました。

### インプット 1（不具合報告および改善指示）
- **日時**: 2026年10月4日 10:55頃
- **関連履歴 No**: **`#04`**
- **ご指示原文**:
  > 「3. 履歴情報の Sync 時、毎回2回連続データが残っています。１度のみにできませんか？」

### インプット 2（重なり除外判定不採用および根本原因排除指示）
- **日時**: 2026年10月10日 11:30頃
- **ご指示原文**:
  > 「#25に進んでください。ただし、重なり除外判定は不具合が見えなくなる可能性があるため、今は不要です。」

---

## 2. 目的と根本原因の排除方針

時間間引き（10秒ガード）などの推測ガード処理は一切入れず、Wear OS からのデータ同期時に 2 回連続して履歴エントリーが重複保存されていた**根本原因（通信受領の構造的二重化）**を排除します。

### 根本原因の構造分析
Wear OS から `/watch_battery` データパケットが届いた際、以下の 2 つのサービスが独立して同時に同じパケットをイベント検知していました：
1. **経路 A (`WearDataSyncer`)**: `BatteryMonitorService` 内で起動されているリアルタイム同期リスナー。受領時に `addHistory`（1 回目の DB 保存）を実行。
2. **経路 B (`MobileWearableListenerService`)**: Android システムが起動するバックグラウンド受信用サービス。同じイベントを受け取り独自に `repository.insertEntry`（2 回目の DB 保存）を実行。

アプリ/サービス起動中において、経路 A と経路 B の双方が同時に同じパケットを非同期保存していたことが 2 重重複の根本原因です。

---

## 3. 変更対象コンポーネント

### モバイルモジュール (`:mobile`)
- **`MobileWearableListenerService.kt`** `[MODIFY]` : 
  常駐サービス（`BatteryMonitorService`）がアクティブで `WearDataSyncer` が受信処理を担当している間は、バックグラウンド受信機での重複 DB 挿入をスキップし、受信経路の一元化を行います。

### ドキュメント類 (`docs/`)
- **`docs/plans/20261010_prevent_duplicate_data_sync_history.md`** `[NEW]` : 本改訂計画書（本日日付: 2026年10月10日）
- **`docs/walkthroughs/20261010_prevent_duplicate_data_sync_history.md`** `[NEW]` : 本改訂実績のウォークスルー報告書

---

## 4. 実装ステップ

### ステップ 1: `task_history.md` の `#25` を `[IN_PROGRESS]` に更新
- ルール `[R11]` に従い、`docs/task_history.md` のタスク `#25` を進行中状態へ更新します。

### ステップ 2: `MobileWearableListenerService.kt` での二重受領・保存の排除
- 常駐監視サービス動作時における `MobileWearableListenerService` 側の重複 DB インサート処理を整理し、`WearDataSyncer` への一元化によって 2 重保存を根本排除します。

### ステップ 3: 静的解析およびビルド検証
- `analyze_file` によりエラーや不要な警告がないことを確認します。
- テスト自動実行は行わず、`gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。

### ステップ 4: ウォークスルー実績書の作成 (`docs/walkthroughs/`)
- 実績レポート `docs/walkthroughs/20261010_prevent_duplicate_data_sync_history.md` を作成します。

---

## 5. 検証計画

- `analyze_file` で確認を行います。
- `gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。
