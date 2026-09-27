# Git 追跡対象からの `.idea/` 設定ファイルの削除計画

## 1. 目的と概要

`.gitignore` 更新前に誤って Git リポジトリの追跡対象（インデックス）に追加されていた Android Studio 設定ファイル（`.idea/` フォルダ配下の XML ファイル群 13 件）を Git の追跡対象から除外します。

ローカル開発環境の `.idea/` 設定ファイルを保持したまま、Git のインデックス（Tracking）からのみ安全に削除し、GitHub リポジトリ上をクリーンな状態に整理します。

---

## 2. 変更対象コンポーネント

- **Git インデックス**: `.idea/` フォルダ配下の 13 ファイルの追跡解除
- **`docs/plans/20260926_remove_idea_from_git_tracking.md`** `[NEW]` : 本クリーニングの計画書
- **`docs/walkthroughs/20260926_remove_idea_from_git_tracking.md`** `[NEW]` : 本クリーニング実績のウォークスルー報告書

---

## 3. 実装ステップ

### ステップ 1: Git インデックスからの追跡解除
- コマンド `git rm -r --cached .idea` を実行し、物理ファイルを削除せずに Git 追跡のみを解除します。

### ステップ 2: 追跡解除の検証
- コマンド `git status` および `git ls-files` を実行し、`.idea/` 以下のファイルが削除予定状態（`deleted:`）としてインデックスに記録され、今後の新規作成・変更が追跡されないことを確認します。

### ステップ 3: ウォークスルー実績書の作成
- 実施結果と検証内容をまとめた `docs/walkthroughs/20260926_remove_idea_from_git_tracking.md` を作成します。

---

## 4. 検証計画

- `git status` および `git ls-files` により、`.idea/` が追跡リストから消失していることを確認します。
