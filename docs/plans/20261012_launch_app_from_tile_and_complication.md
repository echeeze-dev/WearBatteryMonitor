# タイルおよびコンプリケーションタップ時のアプリ自動起動対応計画

## 1. インプット（ユーザー様からのご指示）

- **インプット日時**: 2026年10月12日 18:37頃
- **ユーザー様からのご指示原文**:
  > 「新機能を追加してください。
  > ウォッチアプリのタイルとコンプリケーションをタッチしたときに、BattSyncアプリを起動するようにしたいです。」

---

## 2. 目的と技術的設計

Wear OS スマートウォッチ側の「タイル (Tile)」および「コンプリケーション (Complication)」要素をタップ（タッチ）した際、ウォッチ用 BattSync メイン画面 (`com.echeeze.wearbatterymonitor.presentation.MainActivity`) を自動起動するタップアクションを追加します。

### 技術的設計
1. **タイル (`PhoneBatteryTileService.kt`)**:
   ProtoLayout の `ActionBuilders.LaunchAction` および `ModifiersBuilders.Clickable` を用いて、タイルのレイアウト全体（または主要要素）に `MainActivity` 起動アクション（`setAndroidActivity`）を設定します。
2. **コンプリケーション (`MainComplicationService.kt`)**:
   `ShortTextComplicationData.Builder` に対し、`PendingIntent.getActivity()` で生成した `MainActivity` 起動インテントを `.setTapAction(pendingIntent)` として登録・設定します。

---

## 3. 変更対象コンポーネント

### ウェアモジュール (`:wear`)
- **`tile/PhoneBatteryTileService.kt`** `[MODIFY]` : 
  タイルのレイアウト要素に `LaunchAction` Clickable モディファイアを追加します。
- **`complication/MainComplicationService.kt`** `[MODIFY]` : 
  `ShortTextComplicationData` に `PendingIntent` タップアクション（`setTapAction`）を追加します。

### ドキュメント類 (`docs/`)
- **`docs/plans/20261012_launch_app_from_tile_and_complication.md`** `[NEW]` : 本機能追加の計画書（本日日付: 2026年10月12日）
- **`docs/walkthroughs/20261012_launch_app_from_tile_and_complication.md`** `[NEW]` : 本機能追加実績のウォークスルー報告書

---

## 4. 実装ステップ

### ステップ 1: `task_history.md` への事前新規登録 (`#28`)
- ルール `[R11]` に従い、`docs/task_history.md` にタスク `#28`（タイル/コンプリケーションタップ時のアプリ起動対応）を `[IN_PROGRESS]` 状態で事前登録します。

### ステップ 2: `PhoneBatteryTileService.kt` へのタップ起動アクションの組み込み
- ProtoLayout の `ActionBuilders` を用いて `MainActivity` 起動アクションを生成し、タイルのルートモディファイアに `setClickable` として設定します。

### ステップ 3: `MainComplicationService.kt` へのタップ起動インテントの組み込み
- `PendingIntent.getActivity()` を用いて `MainActivity` 起動用 `PendingIntent` を生成し、`ShortTextComplicationData.Builder` へ `.setTapAction()` として組み込みます。

### ステップ 4: 静的解析およびビルド検証
- `analyze_file` によりエラーや不要な警告がないことを確認します。
- テスト自動実行は行わず、`gradle_build(":wear:assembleDebug")` が正常に成功することを確認します。

### ステップ 5: ウォークスルー実績書の作成 (`docs/walkthroughs/`)
- 実績レポート `docs/walkthroughs/20261012_launch_app_from_tile_and_complication.md` を作成します。

---

## 5. 検証計画

- `analyze_file` で確認を行います。
- `gradle_build(":wear:assembleDebug")` が正常に成功することを確認します。
