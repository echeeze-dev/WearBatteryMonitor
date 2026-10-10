# リアルタイム Wearable 接続状態に基づくスマートウォッチ表示への改訂計画

> **過去提案のステータス**: `[CANCELLED]` （過去DB履歴からの数値復元案は取り下げ） — 2026/10/03 確定  
> **改訂後ステータス**: `[ACCEPTED]` （リアルタイム Wearable ノード検索案の採用）

## 1. 結論および不採用提案の理由 (`[CANCELLED]`)

当初 AI から提示された「過去のデータベース履歴 (`watchData`) から最新残量レベルを復元して `watchPercent` に代入する提案」は、過去の古い残量値を画面上に表示してしまう不不備があったため取り下げ・不採用（`[CANCELLED]`）となりました。

画面上のウォッチ表示はデータベース履歴ではなく **リアルタイムな到達・接続状態** であるべきとのご指示を受け、Android Wearable API (`Wearable.getNodeClient().connectedNodes`) によるリアルタイム検索判定方式へ修正・改訂されました。

---

## 2. インプット（ユーザー様からのご指示・フィードバック一覧）

本不具合対応および修正計画は、以下の 2 点のユーザー様からのご指示・フィードバックをインプットとして作成いたしました。

### インプット 1（不具合報告および調査指示）
- **日時**: 2026年10月3日 09:15頃
- **ご指示原文**:
  > 「・ウォッチの接続情報がおかしいと思います。スマホアプリのウォッチの情報がDisconnectedと表示されることがあります。もちろん接続されている状態です。表示条件など確認をお願いします。」

### インプット 2（修正 Plan への不承認および方針訂正指示）
- **日時**: 2026年10月3日 09:22頃
- **ご指示原文**:
  > 「日時はOKです。ただ、修正内容は同意できません。
  > グラフ上部にあるウォッチ情報とバッテリー情報 or Disconnected はデータベース上の情報ではなく最新の情報であるべきです。修正内容を再検討してください。」

---

## 3. 目的と改訂方針

過去のデータベース履歴からの数値復元処理案を破棄します。

データベースの古い履歴に依存せず、**Android Wearable API (`Wearable.getNodeClient().connectedNodes`) を用いた「現在（リアルタイム）のスマートウォッチ接続状態」の検索結果を 100% 唯一の根拠として**、スマートウォッチの表示制御を行います。

---

## 4. 変更対象コンポーネント

### モバイルモジュール (`:mobile`)
- **`MainActivity.kt`** `[MODIFY]` : 
  - `onStart()` / `onResume()` および定期同期時において、`Wearable.getNodeClient(context).connectedNodes` を実行してリアルタイムに接続中のスマートウォッチノードを検索します。
  - 接続ノードが存在する場合 (`nodes.isNotEmpty()`) ➔ 接続状態とし、`node.displayName` を端末名 (`watchName`) として反映します。
  - 接続ノードが存在しない場合 (`nodes.isEmpty()`) ➔ 即座に切断状態 (`isWatchDisconnected = true`) とし、`"Disconnected"` を表示します。
- **`ui/MainUiState.kt`** `[MODIFY]` : 
  スマートウォッチがリアルタイムに到達・接続可能かを示す明示的な状態フラグ `isWatchConnected: Boolean = false` を追加します。

### ドキュメント類 (`docs/`)
- **`docs/plans/20261003_fix_watch_disconnected_status.md`** `[NEW]` : 本改訂計画書（本日日付: 2026年10月3日）
- **`docs/walkthroughs/20261003_fix_watch_disconnected_status.md`** `[NEW]` : 本改訂実績のウォークスルー報告書

---

## 5. 実装ステップ

### ステップ 1: `MainUiState.kt` へのリアルタイム接続フラグ `isWatchConnected` の追加
- スマートウォッチの物理的な接続状態を保持する `isWatchConnected: Boolean` プロパティを追加します。

### ステップ 2: Wearable API によるリアルタイムノード検索処理の実装 (`MainActivity.kt`)
- `Wearable.getNodeClient(context).connectedNodes` の非同期レスポンスから、現在実際に接続されているスマートウォッチが存在するかをリアルタイム確認します。
- 接続ノードが存在すれば `isWatchConnected = true` および `watchName = node.displayName` に更新し、存在しなければ `isWatchConnected = false` に更新します。

### ステップ 3: リアルタイム状態に基づく UI レイアウト表示の制御 (`MainActivity.kt`)
- `BatterySection` (Watch) における `isDisconnected` の指定を、リアルタイム検索結果 `!uiState.isWatchConnected` に変更し、DB 過去ログからの誤判定を排除します。

### ステップ 4: 静的解析およびビルド検証
- `analyze_file` によりエラーや警告がないことを確認します。
- テスト自動実行は行わず、`gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。

### ステップ 5: ウォークスルー実績書の作成 (`docs/walkthroughs/`)
- 実績レポート `docs/walkthroughs/20261003_fix_watch_disconnected_status.md` を作成します。

---

## 6. 検証計画

- `analyze_file` で確認を行います。
- `gradle_build(":mobile:assembleDebug")` が正常に成功することを確認します。
