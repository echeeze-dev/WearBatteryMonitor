# リポジトリパターンの導入およびアーキテクチャ・リファクタリング計画

## 1. 目的と概要

現在のコード（特に `MainActivity` や `BatteryMonitorService`）では、画面描画、サービス管理、Room データベースへのアクセス、Google Wearable Data Layer 通信、BroadcastReceiver の受信処理が1つのクラスに混在しています。

本リファクタリングでは、**リポジトリパターン (Repository Pattern)** を導入し、データアクセス（Room DB、Wearable API、OS バッテリー情報）を単一のデータ層へカプセル化します。これにより、今後予定している「バッテリー残り時間推測機能」や「長期傾向集計機能」を安全かつスムーズに追加できる設計へ刷新します。

また、リファクタリング前後で既存動作やデータの入出力が変わっていないことを保証するため、**自動テストの作成とコードカバレッジの確認** を行います。あわせて、設計ドキュメントやクラス図をタイムリーに更新し、実装とドキュメントの整合性を維持します。

---

## 2. 変更対象コンポーネント

### モバイルモジュール (`:mobile`)
- **`data/BatteryRepository.kt`** `[NEW]` : データアクセスのインターフェース定義
- **`data/BatteryRepositoryImpl.kt`** `[NEW]` : Room DB や Wearable API との連携を担う実装クラス
- **`BatteryMonitorService.kt`** `[MODIFY]` : Repository を経由したデータ保存・同期処理への修正
- **`MainActivity.kt`** `[MODIFY]` : 直参照のデータ操作を Repository / 状態管理へ委譲する修正
- **`src/androidTest/java/com/echeeze/wearbatterymonitor/data/BatteryDaoTest.kt`** `[NEW]` : Room DB 挙動検証用テスト
- **`src/test/java/com/echeeze/wearbatterymonitor/data/BatteryRepositoryTest.kt`** `[NEW]` : リポジトリ層単体テスト

### ウェアモジュール (`:wear`)
- **`data/WearBatteryRepository.kt`** `[NEW]` (必要に応じて) : ウォッチ側のデータ通信・状態取得処理のカプセル化
- **`presentation/MainActivity.kt`** `[MODIFY]` : 通信・バッテリー計測処理の整理

### ドキュメント類 (`docs/`)
- **`docs/design/class_diagram.puml`** `[MODIFY]` : リポジトリ層を追加したクラス図への改訂
- **`docs/design/system_overview.md`** `[MODIFY]` : システム仕様書および機能・対応クラス一覧表の更新
- **`docs/walkthroughs/20260922_refactor_repository_pattern.md`** `[NEW]` : リファクタリング実績、テスト結果、カバレッジ報告をまとめたウォークスルー作成

---

## 3. 実装ステップ

### ステップ 0: リファクタリング前の自動テスト作成とカバレッジ確認
1. **`BatteryDaoTest.kt` の作成**:
   - Room のメモリ内データベース (`Room.inMemoryDatabaseBuilder`) を使用し、データ挿入 (`insert`)、履歴取得 (`getHistoryByType`)、およびトリミング (`trimHistory`) の挙動を検証するテストを作成します。
2. **事前カバレッジ測定**:
   - Android Studio の "Run with Coverage" を実行し、既存データアクセスロジックのカバレッジ（テストカバー率）を記録・確保します。

### ステップ 1: `BatteryRepository` インターフェースおよび実装クラスの作成 (`:mobile`)
1. **インターフェース定義 (`BatteryRepository.kt`)**:
   - モバイル、ウォッチ、イヤホンのバッテリー履歴取得（`Flow<List<BatteryEntry>>` または `List<BatteryEntry>`）
   - バッテリー履歴の追加（`addEntry`）
   - 履歴データの自動トリミング（`trimHistory`）
   - Wear OS 側へのデータ同期（`syncToWear`）
2. **実装クラス作成 (`BatteryRepositoryImpl.kt`)**:
   - `BatteryDao` (Room DB) および `DataClient` (Wearable API) を保持し、非同期コルーチン（`Dispatchers.IO`）上でデータ操作を一括管理します。
3. **`BatteryRepositoryTest.kt` の作成**:
   - リポジトリ層のユニットテストを作成し、正しいデータ操作・変換が行われるか検証します。

### ステップ 2: `BatteryMonitorService` のリファクタリング
1. `BatteryMonitorService` 内で直接行っていた `batteryDao.insert()` や `trimHistory()` などの DB 直接アクセスを、`BatteryRepository` 呼び出しへ置き換えます。
2. BroadcastReceiver（`ACTION_BATTERY_CHANGED` や `BATTERY_LEVEL_CHANGED`）で取得したデータを `BatteryRepository` へ渡し、データ保存と Wear 送信処理を分離します。

### ステップ 3: モバイル側 `MainActivity` のリファクタリング
1. `MainActivity` 内で重複して行っていた DB ロード処理やデータ保存処理を `BatteryRepository` 経由に統一します。
2. サービス（`BatteryMonitorService`）との状態同期を整理し、UI 表示に特化させます。

### ステップ 4: ウェア側 (`:wear`) の通信・データアクセス整理
1. `WearMainActivity` や `PhoneBatteryTileService` で直接行われていた `Wearable.getDataClient()` のデータ送受信処理を整理し、保守性を向上させます。

### ステップ 5: リファクタリング後のテスト再実行とカバレッジ検証
1. **自動テストの全件再実行**:
   - ステップ 0 および 1 で作成した自動テストを再実行し、すべて成功（パス）することを確認します。
2. **カバレッジの再確認**:
   - カバレッジ測定を実行し、リファクタリング前後でデグレが発生していないこと、データアクセスの主要ラインがテストでカバーされていることを検証します。

### ステップ 6: 設計ドキュメントの同期更新とウォークスルー作成
1. **クラス図の更新**: [class_diagram.puml](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/docs/design/class_diagram.puml) に `BatteryRepository` および `BatteryRepositoryImpl` を追加し、構成線を最新化します。
2. **システム仕様書の更新**: [system_overview.md](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/docs/design/system_overview.md) のアーキテクチャ図と第 5 章の対応表を最新実装に書き換えます。
3. **ウォークスルーの作成**: `docs/walkthroughs/20260922_refactor_repository_pattern.md` を作成し、実施したコード修正内容、テスト結果、カバレッジ測定結果をまとめます。

---

## 4. 期待される効果

1. **関心の分離（SoC）**: UI、サービス、データベース、通信の役割が明確になり、コードの可読性が大幅に向上します。
2. **品質・同一性の保証**: 自動テストとカバレッジ確認により、リファクタリングによる先祖返りや不具合の発生を防止できます。
3. **新機能の組み込み容易性**: 今後開発する「バッテリー残り時間推測エンジン（`BatteryPredictor`）」を `BatteryRepository` のデータと容易に連携させることができるようになります。
4. **ドキュメントとコードの一致**: 実装と設計書・クラス図が常に同期された状態を保つことができます。

---

## 5. 検証計画

### 自動テスト検証 & カバレッジ検証
- **インストゥルメンテッドテスト**: `mobile` モジュールにて `BatteryDaoTest` を実行し、全テストケースが成功することを確認します。
- **ユニットテスト**: `BatteryRepositoryTest` を実行し、全テストケースが成功することを確認します。
- **カバレッジ測定**: Android Studio の "Run with Coverage" を利用し、データアクセス層のカバレッジを確認・報告します。

### ビルド検証
- `gradle_build("mobile:assembleDebug")` および `gradle_build("wear:assembleDebug")` が正常に成功することを確認します。

### 機能動作検証
1. **モバイルアプリ**:
   - アプリ起動時にデータベースから履歴が正常に読み出されることを確認します。
   - バッテリー残量変更時およびイヤホン接続時に履歴が保存されることを確認します。
2. **Wear OS アプリ & タイル**:
   - スマホのバッテリー状態および履歴が Wear OS アプリ、タイル、コンプリケーションに正しく同期・表示されることを確認します。
