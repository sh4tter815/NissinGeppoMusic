# アプリ名変更、アイコンリセット、およびホーム画面右側シークバーディスクの実装計画

## 概要
1. アプリ名を「日進月歩ミュージック」に変更。
2. アプリのアイコンをまっさらにリセット（シンプルでクリーンなダークミニマリストアイコンに変更）。
3. ホーム画面（page == 1）の右側の円を、従来の「お気に入りアプリ一覧」から、**再生中の楽曲の進行状況を表すシークバーディスク（MusicSeekBarDisc）**に変更。
   - 曲の進行に伴い少しずつ回転。
   - 円の外側に秒数が目盛りのように描かれ、**一番左に来た秒数が現在の再生秒数**となる。
   - 右側を指でスクロール（ドラッグ）すると、リスト操作のように滑らかにシーク可能。

## 変更予定ファイル
- **[MODIFY] [strings.xml](file:///C:/Users/harum/AndroidStudioProjects/NisshinGeppoMusic/app/src/main/res/values/strings.xml)**: アプリ名を「日進月歩ミュージック」に変更。
- **[MODIFY] [ic_launcher.xml](file:///C:/Users/harum/AndroidStudioProjects/NisshinGeppoMusic/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml)** / **[ic_launcher_background.xml](file:///C:/Users/harum/AndroidStudioProjects/NisshinGeppoMusic/app/src/main/res/drawable/ic_launcher_background.xml)**: アイコンをクリーンなダーク単色背景にリセット。
- **[MODIFY] [MusicPlayerManager.kt](file:///C:/Users/harum/AndroidStudioProjects/NisshinGeppoMusic/app/src/main/java/com/example/nissingeppomusic/data/MusicPlayerManager.kt)**: 現在の再生位置 (`currentPosition`)、総再生時間 (`duration`)、および `seekTo(positionMs)` を追加。
- **[NEW] [MusicSeekBarDisc.kt](file:///C:/Users/harum/AndroidStudioProjects/NisshinGeppoMusic/app/src/main/java/com/example/nissingeppomusic/ui/components/MusicSeekBarDisc.kt)**: 秒数目盛りの描画、再生位置に応じた回転、およびタッチドラッグによるシーク操作を実装したコンポーネント。
- **[MODIFY] [MainLauncherScreen.kt](file:///C:/Users/harum/AndroidStudioProjects/NisshinGeppoMusic/app/src/main/java/com/example/nissingeppomusic/ui/MainLauncherScreen.kt)**: ホーム画面（page == 1）の右側に `MusicSeekBarDisc` を配置。
