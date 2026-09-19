# BattSync アプリアイコンの作成計画

新名称「BattSync」のブランドイメージを確立するため、スマートフォン、スマートウォッチ、および充電（同期）を象徴する新しいアプリアイコンを作成し、適用します。

## デザインコンセプト

- **前景 (Foreground)**:
    - スマートフォンとスマートウォッチのシルエットを組み合わせたデザイン。
    - 中央に力強い「雷マーク（ボルト）」を配置し、バッテリーと同期（Sync）のアイデンティティを表現します。
    - アダプティブアイコンのセーフゾーン（中央72dp）に収まるよう配置し、視認性を高めます。
- **背景 (Background)**:
    - アプリのテーマカラーである紫（Purple）を基調とした、モダンなグラデーションを採用します。

## 修正内容

### [リソース - スマホ・ウォッチ共通]

#### [MODIFY] [ic_launcher_foreground.xml](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/res/drawable/ic_launcher_foreground.xml)
- デフォルトの Android ロボットを廃止し、BattSync 専用の「デバイス＋ボルト」デザインのベクトルデータに差し替えます。

#### [MODIFY] [ic_launcher_background.xml](file:///C:/Users/uitti/AndroidStudioProjects/WearBatteryMonitor/mobile/src/main/res/drawable/ic_launcher_background.xml)
- テーマに合わせたグラデーション背景（#6200EE 〜 #3700B3）に変更します。

※ ウォッチアプリ（:wear）側のリソースも同様に更新、またはモバイル側のリソースをコピーして同期させます。

## 期待される効果
- ホーム画面でアプリが目立ち、機能（スマホとウォッチのバッテリー連携）が直感的に伝わるようになります。
- デフォルトアイコンからの脱却により、アプリの完成度と信頼性が向上します。

## 検証計画
### 自動テスト
- `gradle_assemble_all` でリソースのコンパイルエラーがないことを確認します。

### 手動確認
- エミュレータまたは実機にインストールし、ランチャー（ホーム画面）でアイコンが正しく表示されることを確認します。
- アイコンの形状（丸、四角など）が変わっても、主要な要素（ボルト）が欠けないことを確認します。
