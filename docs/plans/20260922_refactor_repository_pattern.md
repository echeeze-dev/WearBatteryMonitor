# リポジトリパターンの導入およびアーキテクチャ・リファクタリング計画

## 1. 目的と概要

現在のコード（特に `MainActivity` や `BatteryMonitorService`）では、画面描画、サービス管理、Room データベースへのアクセス、Google Wearable Data Layer 通信、BroadcastReceiver の受信処理が1つのクラスに混在しています。

本リファクタリングでは、**リポジトリパターン (Repository Pattern)** を導入し、データアクセス（Room DB、Wearable API、OS バッテリー情報）を単一のデータ層へカプセル化します。これにより、今後予定している「バッテリー残り時間推測機能」や「長期傾向集計機能」を安全かつスムーズに追加できる設計へ刷新します。

---

## 2. 変更対象コンポーネント

### モバイルモジュール (`:mobile`)
- **`data/BatteryRepository.kt`** `[NEW]` : データアクセスのインターフェース定義
- **`data/BatteryRepositoryImpl.kt`** `[NEW]` : Room DB や Wearable API との連携を担う実装クラス
- **`BatteryMonitorService.kt`** `[MODIFY]` : Repository を経由したデータ保存・同期処理への修正
- **`MainActivity.kt`** `[MODIFY]` : 直参照のデータ操作を Repository / 状態管理へ委譲する修正

### ウェアモジュール (`:wear`)
- **`data/WearBatteryRepository.kt`** `[NEW]` (必要に応じて) : ウォッチ側のデータ通信・状態取得処理のカプセル化
- **`presentation/MainActivity.kt`** `[MODIFY]` : 通信・バッテリー計測処理の整理

---

## 3. 実装ステップ

### ステップ 1: `BatteryRepository` インターフェースおよび実装クラスの作成 (`:mobile`)
1. **インターフェース定義 (`BatteryRepository.kt`)**:
   - モバイル、ウォッチ、イヤホンのバッテリー履歴取得（`Flow<List<BatteryEntry>>` または `List<BatteryEntry>`）
   - バッテリー履歴の追加（`addEntry`）
   - 履歴データの自動トリミング（`trimHistory`）
   - Wear OS 側へのデータ同期（`syncToWear`）
2. **実装クラス作成 (`BatteryRepositoryImpl.kt`)**:
   - `BatteryDao` (Room DB) および `DataClient` (Wearable API) を保持し、非同期コルーチン（`Dispatchers.IO`）上でデータ操作を一括管理します。

### ステップ 2: `BatteryMonitorService` のリファクタリング
1. `BatteryMonitorService` 内で直接行っていた `batteryDao.insert()` や `trimHistory()` などの DB 直接アクセスを、`BatteryRepository` 呼び出しへ置き換えます。
2. BroadcastReceiver（`ACTION_BATTERY_CHANGED` や `BATTERY_LEVEL_CHANGED`）で取得したデータを `BatteryRepository` へ渡し、データ保存と Wear 送信処理を分離します。

### ステップ 3: モバイル側 `MainActivity` のリファクタリング
1. `MainActivity` 内で重複して行っていた DB ロード処理やデータ保存処理を `BatteryRepository` 経由に統一します。
2. サービス（`BatteryMonitorService`）との状態同期を整理し、UI 表示に特化させます。

### ステップ 4: ウェア側 (`:wear`) の通信・データアクセス整理
1. `WearMainActivity` や `PhoneBatteryTileService` で直接行われていた `Wearable.getDataClient()` のデータ送受信処理を整理し、保守性を向上させます。

---

## 4. 期待される効果

1. **関心の分離（SoC）**: UI、サービス、データベース、通信の役割が明確になり、コードの可読性が大幅に向上します。
2. **新機能の組み込み容易性**: 今後開発する「バッテリー残り時間推測エンジン（`BatteryPredictor`）」を `BatteryRepository` のデータと容易に連携させることができるようになります。
3. **テスト容易性の向上**: リポジトリのモック（Mock/Fake）を作成することで、画面やサービスの単体テストが可能になります。

---

## 5. 検証計画

### ビルド検証
- `gradle_build("mobile:assembleDebug")` および `gradle_build("wear:assembleDebug")` が正常に成功することを確認します。

### 機能動作検証
1. **モバイルアプリ**:
   - アプリ起動時にデータベースから履歴が正常に読み出されることを確認します。
   - バッテリー残量変更時およびイヤホン接続時に履歴が保存されることを確認します。
2. **Wear OS アプリ & タイル**:
   - スマホのバッテリー状態および履歴が Wear OS アプリ、タイル、コンプリケーションに正しく同期・表示されることを確認します。
