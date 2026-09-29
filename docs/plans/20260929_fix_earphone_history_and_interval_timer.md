# サービス履歴データの同期・保存および Update Interval タイマーの修正計画

## 1. インプット（ユーザー様からのご指示・要望）

- **インプット日時**: 2026年9月29日 22:15頃
- **ユーザー様からのご指摘原文**:
  > 「接続状態は表示されるようになりましたが、まだ履歴には残りませんね。また、Update Intervalも反映されていなさそうなのですが、、、」

本計画書は、上記のご指示日時・ご発言をインプットとして作成いたしました。

---

## 2. 目的と原因分析

1. **「履歴に残らない」原因**:
   バックグラウンド監視サービス動作時、サービス側で記録された最新履歴を画面（`MainActivity` / `MainViewModel`）へ同期・読み出すコード（`syncFromService()`）が抜け落ちていたため、画面側の履歴リストやダイアログに新しいログが反映されていませんでした。
2. **「Update Interval が反映されない」原因**:
   サービス停止時に画面単体で動作する更新タイマーが存在せず、スライダーを変更しても定期記録が走っていませんでした。

---

## 3. 変更対象コンポーネント

### モバイルモジュール (`:mobile`)
- **`MainActivity.kt`** `[MODIFY]` : 
  - `syncFromService()` 内で、サービスが保持する最新の履歴リスト（`phoneHistory`, `watchHistory`, `earphoneHistory`）を `MainViewModel` の画面状態へ正しく同期する処理を復元します。
  - サービス停止時にも Update Interval スライダーの秒数に応じた定期更新タイマーを画面側で動作させ、定期履歴（`PERIODIC_INTERVAL`）をデータベースへ保存します。
- **`ui/MainViewModel.kt`** `[MODIFY]` : サービスから同期された履歴リストを一括更新するメソッドを追加します。

### ドキュメント類 (`docs/`)
- **`docs/plans/20260929_fix_earphone_history_and_interval_timer.md`** `[NEW]` : 本修正の改訂計画書（本日日付: 2026年9月29日）
- **`docs/walkthroughs/20260929_fix_earphone_history_and_interval_timer.md`** `[NEW]` : 本修正実績のウォークスルー報告書

---

## 4. 実装ステップ

### ステップ 1: サービスから画面への履歴リスト同期処理の復元
- `MainActivity.kt` の `syncFromService()` 内において、`service.phoneHistory`, `service.watchHistory`, `service.earphoneHistory` の最新リストを `MainViewModel` へ渡し、画面上の履歴リストおよびダイアログに最新ログが即座に反映されるようにします。

### ステップ 2: サービス停止時における Update Interval 定期タイマーの追加
- バックグラウンド監視が OFF の場合でも、スライダーで設定した Update Interval 秒数ごとに定期的に現在のバッテリー状態を取得し、定期履歴（`PERIODIC_INTERVAL`）としてデータベースへ保存・画面更新を行います。

### ステップ 3: ビルド検証とウォークスルー報告書の作成 (`docs/walkthroughs/`)
- テストの自動実行は行わず、`:mobile:assembleDebug` のビルド成功を確認後、実績レポート `docs/walkthroughs/20260929_fix_earphone_history_and_interval_timer.md` を作成します。

---

## 5. 検証計画

- `gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。
