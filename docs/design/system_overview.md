# BattSync (WearBatteryMonitor) システム仕様書 & アーキテクチャ解説

本書は、**BattSync (旧 WearBatteryMonitor)** プロジェクトの概要、機能一覧、必要なシステムリソース、および設計アーキテクチャ・主要機能と Android SDK / Google ライブラリクラスとの対応関係についてまとめた技術ドキュメントです。

---

## 1. プロジェクト概要

**BattSync** は、Android スマートフォン（Mobile）と Wear OS スマートウォッチ（Wear）、および接続された Bluetooth イヤホン等のバッテリー残量・充電状態をリアルタイムで相互監視・可視化・永続化するクロスデバイスバッテリーモニターアプリです。

### 主な目的
- **マルチデバイス対応**: スマホ・スマートウォッチ・イヤホンのバッテリー状態を1つの画面でまとめて確認できます。
- **データ同期**: Google Wearable Data Layer API を利用し、スマホとウォッチ間での低遅延・バックグラウンド自動同期を実現します。
- **履歴と可視化**: バッテリーの残量変化を Room DB に保存し、グラフ描画（Jetpack Compose / Wear Compose）によって時間経過による消費傾向を可視化します。
- **Wear OS プラットフォーム統合**: ウォッチのタイル（Tile）やコンプリケーション（Complication）機能を通じて、文字盤上で即座にスマホのバッテリー状態を確認できます。

---

## 2. 機能一覧

以下は、本システムが提供する機能の一覧です（※具体的な技術・ライブラリなどの実現方法は後述の「5. 主な機能の実現方法」に記載しています）。

| カテゴリ | 機能名 | 機能の概要（ユーザーに提供する機能仕様） |
| :--- | :--- | :--- |
| **共通 / データ** | バッテリー履歴の自動記録 | バッテリー残量・充電状態の変化や定期的インターバルに応じて、デバイスのバッテリー履歴を記録します。 |
| | データ永続化 & トリミング | 過去のバッテリー履歴を端末内に保存し、保存上限（最大件数）を超えた古いデータを自動的に削除してデータを整理します。 |
| **モバイル (:mobile)** | スマホバッテリー監視 | スマートフォン本体のバッテリー残量や充電状態の変化を常時検知し、最新状態を更新します。 |
| | イヤホンバッテリー検出 | 接続された Bluetooth イヤホン等のデバイスのバッテリー残量およびデバイス名を取得します。 |
| | バックグラウンド常駐監視 | アプリが非表示やバックグラウンドにある状態でもバッテリーやデバイスの接続変化を継続的に監視し、通知領域に残量を表示します。 |
| | ウェアアプリへのデータ配信 | スマホの最新バッテリー状態、接続イヤホン情報、および過去の履歴データを接続されたスマートウォッチへ送信します。 |
| | グラフ & 履歴リスト表示 | 直近のバッテリー残量推移を折れ線グラフで描画し、過去の記録ログをリスト形式で確認できるように表示します。データ未取得時は「データなし」を美しく表示します。 |
| | 起動時ローディング表示 | アプリ起動時の DB ロード中にプログレスインジケータを表示し、読み込み完了後にスムーズにメイン画面へ切り替えます。 |
| **ウェア (:wear)** | ウォッチバッテリー監視 | スマートウォッチ自身のバッテリー残量や充電状態を検知し、スマホ側へ送信します。 |
| | スマホとの接続状態判定 | スマホ側アプリとの通信可否やペアリング接続状況を判別し、画面上に接続ステータスを表示します。 |
| | タイル表示 (Tile) | Wear OS のタイル画面上に、スマホのバッテリー残量や残量推移グラフをコンパクトに表示します。 |
| | コンプリケーション表示 | ウォッチフェイス（文字盤）上に、スマホのバッテリー残量アイコンやテキストを表示します。 |

---

## 3. 必要なリソース & パーミッション

アプリが正常に動作するために要求するパーミッションおよび依存ライブラリの概要です。

### 必須パーミッション (`AndroidManifest.xml`)
- **Bluetooth 関連**:
  - `android.permission.BLUETOOTH_CONNECT` (Android 12+ / API 31+) : 接続されている Bluetooth イヤホンの名称およびバッテリーレベル取得に必要となります。
- **サービス・通知関連**:
  - `android.permission.FOREGROUND_SERVICE` : バックグラウンドでのバッテリー監視サービス起動に必要となります。
  - `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE` (Android 14+ / API 34+) : 接続デバイス（Bluetooth/Wearable）監視サービスの属性として使用します。
  - `android.permission.POST_NOTIFICATIONS` (Android 13+ / API 33+) : 常駐通知の表示に必要となります。
- **Wearable 同期**:
  - Google Play Services Wearable API (`com.google.android.gms:play-services-wearable`) : スマホ・ウォッチ間の通信基盤として利用します。

---

## 4. システムアーキテクチャ & クラス図

システム全体は [class_diagram.puml](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/docs/design/class_diagram.puml) にて定型化されています。

```
                       ┌──────────────────────────────────────────┐
                       │   Android SDK / Google Play Services     │
                       └────────────────────┬─────────────────────┘
                                            │ (継承・API利用)
                  ┌─────────────────────────┴─────────────────────────┐
                  ▼                                                   ▼
┌────────────────────────────────────┐             ┌────────────────────────────────────┐
│      Mobile Module (:mobile)       │             │       Wear Module (:wear)          │
├────────────────────────────────────┤             ├────────────────────────────────────┤
│ • MobileMainActivity (UI)          │             │ • WearMainActivity (UI)            │
│ • BatteryMonitorService (Service)  │◄───────────►│ • WearBatteryRepository / Impl[NEW]│
│ • BatteryRepository / Impl [NEW]   │  DataClient │ • PhoneBatteryTileService (Tile)   │
│ • MobileWearableListenerService    │ MessageClient│ • MainComplicationService (Comp)   │
│ • AppDatabase / BatteryDao (Room)  │             │ • BatteryEntry (Data)              │
└────────────────────────────────────┘             └────────────────────────────────────┘
```

### クラス図の主要構成要素

1. **Android SDK / Platform APIs**:
   - `ComponentActivity`: UI コンポーネント（Jetpack Compose / Wear Compose）のエントリーポイントとして機能します。
   - `Service`: バックグラウンドで長期的にバッテリーや Bluetooth イベントを監視します。
   - `BroadcastReceiver`: OS から発火されるブロードキャストインテントを受信します。
   - `TileService` / `SuspendingComplicationDataSourceService`: Wear OS プラットフォーム拡張機能の基底クラスとして利用します。

2. **Google Play Services (Wearable Data Layer API)**:
   - `DataClient`: ノード間でデータ (`DataMap`) を同期します。
   - `MessageClient`: ノード間で即時メッセージを送受信します。
   - `CapabilityClient`: 相手ノードの到達可能性・アプリ存在有無を確認します。

3. **リポジトリ層 (Repository Layer)**:
   - `BatteryRepository` / `BatteryRepositoryImpl`: UI やサービス層からのデータ要求を受け取り、Room DB (SQLite) や Wearable API (DataClient) へのアクセスをカプセル化・一元管理します。

4. **Room Database**:
   - `AppDatabase` / `BatteryDao` / `BatteryEntry`: 履歴データの定義と CRUD 操作を担当します。

---

## 5. 主な機能の実現方法と Android SDK / ライブラリクラスとの対応関係

機能一覧で定義した各機能について、**どのような Android SDK / ライブラリ技術を用いて、アプリ内のどのクラスで実現しているか** の詳細対応一覧です。

| 機能名 | 利用している Android SDK / ライブラリのクラス & API | アプリ実装クラス (アプリ側コード) | 技術的な実現方法・処理内容の詳細 |
| :--- | :--- | :--- | :--- |
| **バッテリー履歴の自動記録** | • `android.content.BroadcastReceiver`<br>• `java.util.Timer`<br>• `java.util.TimerTask` | • [BatteryRepository.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/data/BatteryRepository.kt)<br>• [BatteryMonitorService.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/BatteryMonitorService.kt)<br>• [MainActivity.kt (Wear)](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/wear/src/main/java/com/echeeze/wearbatterymonitor/presentation/MainActivity.kt) | バッテリーイベント発生時や一定時間経過時に、現在の残量・充電中フラグ・記録理由を設定した `BatteryEntry` インスタンスを生成し、`BatteryRepository` を経由してデータベースへ保存します。 |
| **データ永続化 & トリミング** | • `androidx.room.Room`<br>• `androidx.room.RoomDatabase`<br>• `androidx.room.Dao`<br>• `androidx.room.Entity`<br>• `androidx.sqlite.db.SupportSQLiteDatabase` | • [BatteryRepositoryImpl.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/data/BatteryRepositoryImpl.kt)<br>• [AppDatabase.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/data/AppDatabase.kt)<br>• [BatteryDao.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/data/BatteryDao.kt)<br>• [BatteryEntry.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/data/BatteryEntry.kt) | `@Entity` で定義された `battery_history` テーブルに対し、`BatteryRepositoryImpl` 内の `insertEntry()` から `batteryDao.insert()` を呼び出して履歴を追加します。指定上限数（例: `MAX_HISTORY_SIZE`）を超える古データは `DELETE FROM ... WHERE timestamp NOT IN (...)` クエリを発行して自動削除します。スキーマ変更は `Migration` クラスで対応します。 |
| **スマホバッテリー監視** | • `android.content.BroadcastReceiver`<br>• `android.os.BatteryManager`<br>• `android.content.Context` (`registerReceiver`) | • [PhoneBatteryMonitor.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/monitor/PhoneBatteryMonitor.kt)<br>• [BatteryMonitorService.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/BatteryMonitorService.kt)<br>• [MainActivity.kt (Mobile)](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/MainActivity.kt) | OS から通知される `Intent.ACTION_BATTERY_CHANGED` インテントを受信します。`BatteryManager.EXTRA_LEVEL` と `EXTRA_SCALE` からバッテリー残量 (%) を計算し、`EXTRA_STATUS` から充電状態 (`BATTERY_STATUS_CHARGING` / `BATTERY_STATUS_FULL`) を判定します。 |
| **イヤホンバッテリー検出** | • `android.bluetooth.BluetoothManager`<br>• `android.bluetooth.BluetoothProfile`<br>• `android.bluetooth.BluetoothHeadset`<br>• `android.bluetooth.BluetoothDevice`<br>• `android.content.BroadcastReceiver` | • [EarphoneBatteryMonitor.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/monitor/EarphoneBatteryMonitor.kt)<br>• [BatteryMonitorService.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/BatteryMonitorService.kt) | `BluetoothManager` から `BluetoothProfile.HEADSET` プロキシを取得します。ACL 接続イベント (`ACTION_ACL_CONNECTED`) やバッテリーレベル変更アクション (`"android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"`) を受信し、インテント内の `EXTRA_BATTERY_LEVEL` および `BluetoothDevice.getName()` からイヤホン情報を抽出します。 |
| **バックグラウンド常駐監視** | • `android.app.Service`<br>• `android.app.NotificationManager`<br>• `android.app.NotificationChannel`<br>• `androidx.core.app.NotificationCompat.Builder` | • [NotificationHelper.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/monitor/NotificationHelper.kt)<br>• [BatteryMonitorService.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/BatteryMonitorService.kt) | アプリが非表示の際も OS によるプロセス破棄を防ぐため、`startForeground()` を実行して通知チャネルに常駐通知を表示します。サービス内で Monitor クラスを保持し続け、常に最新状態を維持します。 |
| **ウェアアプリへのデータ配信** | • `com.google.android.gms.wearable.Wearable`<br>• `com.google.android.gms.wearable.DataClient`<br>• `com.google.android.gms.wearable.PutDataMapRequest`<br>• `com.google.android.gms.wearable.WearableListenerService` | • [WearDataSyncer.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/sync/WearDataSyncer.kt)<br>• [BatteryRepositoryImpl.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/data/BatteryRepositoryImpl.kt)<br>• [BatteryMonitorService.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/BatteryMonitorService.kt) | `BatteryRepository.syncStatusToWear()` から `PutDataMapRequest.create("/battery_status")` を作成し、スマホの残量・充電フラグ・イヤホン情報・履歴アレイを格納して `DataClient.putDataItem()` を呼び出します。Wear 側からの即時リクエスト（`/request_battery_update`）は `WearDataSyncer` の `onMessageReceived` で捕捉し、最新状態を応答します。 |
| **グラフ & 履歴リスト表示** | • `androidx.compose.foundation.Canvas`<br>• `androidx.compose.foundation.lazy.LazyColumn`<br>• `androidx.compose.material3.*` | • [MainViewModel.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/ui/MainViewModel.kt)<br>• [HistoryGraph.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/ui/components/HistoryGraph.kt)<br>• [MainActivity.kt (Mobile)](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/java/com/echeeze/wearbatterymonitor/MainActivity.kt) | Jetpack Compose の `Canvas` コンポーネントを使用して、時系列の残量変化をグリッド補助線・破線とともに折れ線グラフとしてレンダリングします。履歴ログは `LazyColumn` を使用してスクロール可能なリストとして表示します。 |
| **ウォッチバッテリー監視** | • `android.content.BroadcastReceiver`<br>• `android.os.BatteryManager`<br>• `com.google.android.gms.wearable.DataClient` | • [WearBatteryRepository.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/wear/src/main/java/com/echeeze/wearbatterymonitor/data/WearBatteryRepository.kt)<br>• [MainActivity.kt (Wear)](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/wear/src/main/java/com/echeeze/wearbatterymonitor/presentation/MainActivity.kt) | ウォッチ自身の `ACTION_BATTERY_CHANGED` インテントから残量を取得し、`WearBatteryRepository` を経由して `PutDataMapRequest.create("/watch_battery")` でスマホ側へ同期送信します。 |
| **スマホとの接続状態判定** | • `com.google.android.gms.wearable.CapabilityClient`<br>• `com.google.android.gms.wearable.NodeClient` | • [WearBatteryRepository.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/wear/src/main/java/com/echeeze/wearbatterymonitor/data/WearBatteryRepository.kt)<br>• [MainActivity.kt (Wear)](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/wear/src/main/java/com/echeeze/wearbatterymonitor/presentation/MainActivity.kt) | `CapabilityClient.getCapability("battery_monitor_phone_app")` を呼び出し、スマホ側アプリが起動可能・通信圏内にあるかを判定します。接続状況に応じて `ConnectionState`（Active / Offline / AppNotInstalled など）を切り替えます。 |
| **タイル表示 (Tile)** | • `androidx.wear.tiles.TileService`<br>• `androidx.wear.protolayout.LayoutElementBuilders`<br>• `androidx.wear.protolayout.material3` | • [WearBatteryRepository.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/wear/src/main/java/com/echeeze/wearbatterymonitor/data/WearBatteryRepository.kt)<br>• [PhoneBatteryTileService.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/wear/src/main/java/com/echeeze/wearbatterymonitor/tile/PhoneBatteryTileService.kt) | `TileService` を継承します。`onTileRequest()` 内で `WearBatteryRepository.getPhoneBatteryStatus()` を呼び出し、最新のスマホ残量・グラフ履歴を取得して ProtoLayout 形式でタイル UI をレンダリングします。 |
| **コンプリケーション表示** | • `androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService`<br>• `androidx.wear.watchface.complications.data.ShortTextComplicationData`<br>• `androidx.wear.watchface.complications.data.MonochromaticImage` | • [WearBatteryRepository.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/wear/src/main/java/com/echeeze/wearbatterymonitor/data/WearBatteryRepository.kt)<br>• [MainComplicationService.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/wear/src/main/java/com/echeeze/wearbatterymonitor/complication/MainComplicationService.kt) | `SuspendingComplicationDataSourceService` を継承します。`onComplicationRequest()` で `WearBatteryRepository.getPhoneBatteryStatus()` を経由して同期データを取得し、充電状態に応じたアイコンと残量テキスト（例: `"85%"`）をセットした `ShortTextComplicationData` を生成して Watch Face へ給送します。 |

---

## 6. リファクタリングおよび今後の拡張計画への考慮事項

現状の設計を元に今後予定されている主な拡張・リファクタリングのポイントです。

1. **ロジックの肥大化と責務分離 (Refactoring)**:
   - 本リファクタリングにて `BatteryRepository` を導入し、UI・サービス層からの DB アクセスや Data Layer 通信処理を分離しました。
   - 次のステップとして `ViewModel` を導入し、画面状態（State）の管理と描画処理をさらに疎結合にする設計が可能です。
2. **バッテリー推測機能（Estimator Engine）の追加**:
   - `BatteryEntry` に画面輝度 (`screenBrightness`) や画面 ON/OFF (`isScreenOn`) 属性を追加します。
   - `BatteryRepository` の履歴データをもとに残り稼働時間を算出する `BatteryPredictor` モジュールを追加します。
3. **長期傾向集計機能 (Daily Stats Aggregator)**:
   - 毎日の生データから日次統計データを抽出する `DailyBatteryStat` エンティティと `WorkManager` バッチ処理を構築します。
