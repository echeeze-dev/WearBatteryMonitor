# ウォッチタイルのグラフ描画ロジックの抜本的修正計画

タイルのグラフにおいて、背景色の塗りつぶしが過去のデータを覆い隠してしまう問題を解消し、複数の棒が正しく横に並ぶように修正します。

## 修正内容

### [UIレイヤー - Wear OS タイル]

#### [MODIFY] [PhoneBatteryTileService.kt](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/wear/src/main/java/com/echeeze/wearbatterymonitor/tile/PhoneBatteryTileService.kt)
- `barChart` 関数内の各データポイント（ステータスラインおよびバッテリー残量の棒）の描画ロジックを修正します。
- **変更前**: 1つの `Box` に対して「左余白（位置）」と「背景色」を同時に設定。
- **変更後**:
    1. 外側に位置調整用の透明な `Box` を作成し、そこに `Padding`（左余白）を設定します。
    2. その内側に描画用の `Box` を配置し、そこに `Background`（色）と実際の幅を設定します。
- これにより、背景色の適用範囲が指定した幅のみに限定され、複数のデータが重ならずに正しく並んで表示されるようになります。

## 検証計画

### 自動テスト
- `gradle_assemble_all` を実行し、ビルドエラーがないことを確認します。

### 手動確認
- Wear OS デバイスまたはエミュレータにデプロイします。
- 「Phone Battery Monitor」タイルのグラフに、複数のデータ（棒）が横軸に沿って正しく並んでいることを確認します。
- スマホアプリ側で「Graph Range」を切り替えた際、タイル側の時間軸も正しく変化することを確認します。
