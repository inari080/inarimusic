# musicstreamer (音楽プレイヤーmod スケルトン)

## 今回作った部分(第2段: GUI)
- `gui/MusicPlayerScreen.java` — メイン画面。URL入力→Load→プレイリスト取得→クリックで再生、シークバー、ボリューム、再生/一時停止
- `gui/SeekBarWidget.java` / `gui/VolumeSliderWidget.java` — ドラッグ対応の自作ウィジェット(`AbstractWidget`継承)
- `gui/PlaylistWidget.java` — スクロール対応のトラック一覧
- `gui/VisualizerPanel.java` + `util/SimpleFFT.java` — 波形バー(上半分)とFFTスペクトラムバー(下半分)描画
- `gui/ThemeColors.java` — 配色定義(現状デフォルト固定、themedguimod未接続)
- `MusicStreamerMod.java` — Mキーで`MusicPlayerScreen`を開くキーバインドを追加(Fabric APIの`KeyMappingHelper`/`ClientTickEvents`使用)

### themedguimod連携について
現時点では`ThemeColors`はデフォルト配色のみで、themedguimodの`UiPalette`とは未接続です。
理由: `UiPalette`の実際のフィールド名をこちらで把握していないため、当て推量で接続すると壊れやすいです。
本格的に接続する場合は次のいずれかで進めるのがおすすめ:
1. `UiPalette.java`(または該当クラス)のソースを教えてもらい、`ThemeColors`から直接フィールドを読むように書き換える
2. `themedguimod`を`compileOnly`依存として`build.gradle`に追加し、IDEの補完で正しいAPIを確認しながら接続する

### 26.1.2固有の注意(GUI部分)
- `GuiGraphics`のメソッド名(`fill`/`hLine`/`vLine`/`drawString`/`enableScissor`等)は現行Fabricドキュメント記載のものを使用
- `AbstractWidget`の`renderWidget`/`updateWidgetNarration`は現行APIの想定シグネチャ。26.1系はGUIレンダリングパイプラインが刷新中(`GuiRenderState`導入)のため、マイナー更新でシグネチャが変わる可能性がある。ビルド時にコンパイルエラーが出たら、IDEのdecompileソースで正確なシグネチャを確認して調整すること
- `KeyMappingHelper`は26.1で`KeyBindingHelper`から改名されたAPI(`net.fabricmc.fabric.api.client.keymapping.v1`)

## 動作確認の手順(想定)
1. `yt-dlp`/`ffmpeg`をPATHに入れる
2. Fabric実行環境でmodを起動
3. ゲーム内で`M`キー → プレイヤー画面が開く
4. YouTubeプレイリストURLを入力して`Load`
5. 一覧からトラックをクリックして再生、シークバー/ボリュームを操作


## 前提: 外部ツールのインストール
このmodは以下がPATH上に必要です(または各クラスのコンストラクタにフルパスを渡す):

- [yt-dlp](https://github.com/yt-dlp/yt-dlp) — YouTubeの動画/プレイリスト情報取得・音声ストリームURL解決
- [ffmpeg](https://ffmpeg.org/) — 音声デコード(webm/opus, m4a等 → 生PCM)

Windows環境ならどちらも公式ビルドをダウンロードしてPATHに追加するか、
`YtDlpResolver` / `AudioStreamEngine` のコンストラクタに `C:\\tools\\yt-dlp.exe` のような絶対パスを渡してください。

## 動作確認のイメージ(GUI無しでのテストコード例)
``` java
YtDlpResolver resolver = new YtDlpResolver();
AudioStreamEngine engine = new AudioStreamEngine(resolver);

List<TrackInfo> tracks = resolver.resolvePlaylist("https://www.youtube.com/playlist?list=XXXX");
engine.play(tracks.get(0));

// 数秒後
engine.pause();
engine.resume();
engine.seekTo(30.0);
engine.setVolume(0.5f);
```

## 次にやること(未実装)
1. **themedguimod本接続**: `ThemeColors`をUiPaletteの実データに接続する
2. **プレイリストのJSON永続化**: 読み込んだURLや前回再生位置を`OverlayPositionScreen`と同じ方式で保存
3. **エラー表示のUI改善**: yt-dlp/ffmpeg未検出時のメッセージをGUIパネル内に分かりやすく表示
4. **Mod Menu連携**: 設定画面からもプレイヤーを開けるようにする(任意)

## Minecraft 26.1.2 に関する注意
26.1系は非obfuscated(Yarnマッピング廃止・Mojang公式名そのまま)なため、通常のFabric向けbuild.gradleと勝手が違います。

- Loomプラグインは `net.fabricmc.fabric-loom` (旧 `fabric-loom` ではない)を使う
- `mappings` の指定行は不要
- `modImplementation` / `modCompileOnly` ではなく通常の `implementation` / `compileOnly` を使う
- Gradle JVMにはJava 25以上が必要 (IntelliJ IDEAも2025.3以上を推奨)
- `themedguimod`側が1.21.11以前を対象にしている場合、26.1.2用には**別途ポーティングが必要**(1.21.11向けのjarはそのままでは26.1.2で動かない)。今回の`musicstreamer`単体は今のところMinecraftのクラスを直接参照していないため影響なし。GUI実装時に`themedguimod`のAPIを呼ぶ段になったら、themedguimod側も26.1.2対応済みかどうかを確認すること。
- gradle.propertiesの`loader_version`/`fabric_version`は目安値。最新は https://fabricmc.net/develop で確認すること。

## 注意事項
- YouTube利用規約に留意し、個人的なローカル再生の範囲での利用を前提としています。ダウンロード物の保存・再配布は行わない設計です(ストリームURLは都度取得し、ファイルに書き出していません)。
- `gradle.properties` のバージョン番号は仮値です。実際にビルドする前に、既存のThemedGuiMod等のプロジェクトと値を揃えてください。