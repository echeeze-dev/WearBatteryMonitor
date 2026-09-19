# コンプリケーションの充電表示をテーマカラーに同期させる計画

現在、充電中に表示される雷マーク（⚡）は絵文字であるため、ウォッチフェイスのテーマカラーに関わらず常に黄色で表示されてしまいます。これを解決するため、雷マークをベクトルアイコンに変更し、システムが自動的にテーマカラーで着色できるように修正します。

## 修正内容

### [リソース - Wearアプリ]

#### [NEW] [ic_phone_charging.xml](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/wear/src/main/res/drawable/ic_phone_charging.xml)
- 既存のスマートフォンアイコンに、充電中を示す雷マークを組み合わせた新しいベクトルアイコンを作成します。

### [UIレイヤー - Wearアプリ]

#### [MODIFY] [MainComplicationService.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/wear/src/main/java/com/echeeze/wearbatterymonitor/complication/MainComplicationService.kt)
- **アイコンの動的切り替え**:
    - 充電中かどうかに応じて、`ic_phone_battery`（通常時）と `ic_phone_charging`（充電中）を切り替えて表示するように変更します。
- **絵文字の削除**:
    - `setTitle` に設定していた絵文字の雷マーク（⚡）を削除します。
- **テーマカラーへの対応**:
    - `MonochromaticImage` としてアイコンを提供することで、Wear OS システムがウォッチフェイスのテーマカラーに合わせて自動的にアイコン全体（スマホ＋雷マーク）を着色するようになります。

## 期待される効果
- 充電中であっても、ウォッチフェイス全体の色の調和を乱すことなく、統一感のあるデザインでバッテリー状態を確認できるようになります。
- 雷マークだけが浮いて見える問題が解消されます。

## 検証計画

### 自動テスト
- `gradle_assemble_all` を実行し、リソースの参照エラーがないことを確認します。

### 手動確認
- スマートフォンの充電を開始/停止し、ウォッチフェイス上のアイコンが「通常のスマホ」と「雷マーク付きのスマホ」に切り替わることを確認します。
- ウォッチフェイスのテーマカラーを変更し、雷マーク部分もその色に染まることを確認します。
