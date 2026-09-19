# アプリ名の刷新：BattSync への改名計画

アプリ名を現在の「WearBatteryMonitor」から、より洗練された「BattSync」へ、英語・日本語共通で変更します。「tt」を含めることで Battery の意味をより明確に表現します。

## 修正内容

### [モバイルアプリ (:mobile)]
#### [MODIFY] [strings.xml](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/res/values/strings.xml)
- `app_name` を `WearBatteryMonitor` から `BattSync` に変更します。

### [ウェアアプリ (:wear)]
#### [MODIFY] [strings.xml](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/wear/src/main/res/values/strings.xml)
- `app_name` を `WearBatteryMonitor` から `BattSync` に変更します。
- 必要に応じて `tile_label` や `complication_label` も BattSync ブランドに合わせて微調整します（例: `BattSync Tile`, `BattSync Complication`）。

## 期待される効果
- アプリ名が短く印象的になり、ブランドイメージが向上します。
- ホーム画面や設定画面での視認性が向上します。

## 検証計画
### 自動テスト
- `gradle_assemble_all` でビルドが通ることを確認します。

### 手動確認
- スマホおよびウォッチのホーム画面でアプリ名が「BattSync」になっていることを確認します。
- ウォッチフェイスの設定画面で、コンプリケーション名に新しい名前が反映されていることを確認します。
