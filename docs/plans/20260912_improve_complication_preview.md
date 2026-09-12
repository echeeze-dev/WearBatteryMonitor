# コンプリケーションの選択・追加時の視認性向上計画

コンプリケーションの選択リストや追加直後の編集画面において、汎用的な「＋」アイコンではなく、専用のアイコンとプレビューが表示されるように改善します。

## 修正内容

### [リソース - Wearアプリ]

#### [NEW] [ic_phone_battery.xml](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/wear/src/main/res/drawable/ic_phone_battery.xml)
- スマートフォンの形をしたシンプルなベクトルアイコン（単色）を作成します。これはコンプリケーションの標準的なアイコンとして使用されます。

### [UIレイヤー - Wearアプリ]

#### [MODIFY] [MainComplicationService.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/wear/src/main/java/com/echeeze/wearbatterymonitor/complication/MainComplicationService.kt)
- `createComplicationData` メソッドを修正し、`setMonochromaticImage` を使用して上記アイコンをデータに含めます。
- これにより、数値の横にアイコンが表示されるようになります。

### [設定 - Wearアプリ]

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/wear/src/main/AndroidManifest.xml)
- `<service>` タグに `android:icon` 属性を追加し、`ic_phone_battery.xml` を指定します。
- これにより、ウォッチフェイスの編集画面（コンプリケーションのリスト）で、アプリ固有のアイコンが表示されるようになります。

## 期待される効果
- コンプリケーション選択時に、一目で「スマホのバッテリー」であることがわかるようになります。
- 追加直後の「＋」表示がなくなり、プロフェッショナルな外観になります。

## 検証計画

### 自動テスト
- `gradle_assemble_all` を実行し、リソースの参照エラーがないことを確認します。

### 手動確認
- ウォッチフェイスの編集画面を開き、コンプリケーションの選択リストで作成したアイコンが表示されていることを確認します。
- 配置直後のプレビュー状態で、数値とアイコンが表示されることを確認します。
