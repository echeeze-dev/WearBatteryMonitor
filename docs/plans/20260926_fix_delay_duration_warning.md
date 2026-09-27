# MainActivity における `delay(200)` コンパイラ警告の修正計画

## 1. 目的と概要

`MainActivity.kt` (295行目) にて出力される `Legacy Long overload can be converted to Duration` というコンパイラ警告を解消します。

Kotlin Coroutines 1.6+ における最新の Duration API（`kotlin.time.Duration.Companion.milliseconds`）に対応させ、コードの品質とモダンな Kotlin 規格への適合度を向上させます。

---

## 2. 変更対象コンポーネント

### モバイルモジュール (`:mobile`)
- **`MainActivity.kt`** `[MODIFY]` : `import kotlin.time.Duration.Companion.milliseconds` の追加および `delay(200.milliseconds)` への書き換え

### ドキュメント類 (`docs/`)
- **`docs/plans/20260926_fix_delay_duration_warning.md`** `[NEW]` : 本修正計画書
- **`docs/walkthroughs/20260926_fix_delay_duration_warning.md`** `[NEW]` : 本修正実績のウォークスルー報告書

---

## 3. 実装ステップ

### ステップ 1: `MainActivity.kt` の警告箇所の修正
- `kotlin.time.Duration.Companion.milliseconds` をインポートし、`delay(200)` を `delay(200.milliseconds)` へ修正します。

### ステップ 2: 静的解析およびビルド・テスト検証
- `analyze_file` により `MainActivity.kt` から該当警告が消去されていることを確認します。
- `gradle_build("mobile:testDebugUnitTest")` および `gradle_build("mobile:assembleDebug")` が正常に成功することを確認します。

### ステップ 3: ウォークスルー実績書の作成
- 修正結果および検証ログをまとめた `docs/walkthroughs/20260926_fix_delay_duration_warning.md` を作成します。

---

## 4. 検証計画

- `analyze_file` で警告が解消されていることを確認します。
- 単体テストおよびビルドが正常に成功することを確認します。
