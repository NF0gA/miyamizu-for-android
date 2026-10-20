# Miyamizu Android 移植 - フェーズC 残作業まとめ

## フェーズC: ビルド・テスト・最適化・リリース準備

---

### C-1. NDKビルドテスト

**目的**: 全ネイティブコードがarm64-v8a / x86_64両アーキテクチャでビルドを通す

**確認項目**:
- [ ] `scripts/fetch_native_libs.sh` を実行して各ライブラリを取得
- [ ] Android StudioでNDKビルドが通るか確認
- [ ] r8brain-free-srcのincludeパス調整（`CDSPRealFFT.h`等のヘッダー依存）
- [ ] soxrのAndroidクロスコンパイル確認（`CMakeLists.txt.android`の動作）
- [ ] arm64-v8a / x86_64 両アーキテクチャでのビルド確認
- [ ] CMakeLists.txt統合の確認（libs/各ディレクトリのCMakeLists.txtが正しくincludeされるか）

**既知の課題**:
- FLACデコーダーが`init_file`を使用（`init_stream`への切り替え推奨）
- USB出力のalt setting自動選択が簡易実装（ネイティブ側結果をJNIで返すAPI追加が理想）
- リサンプラーの出力バッファサイズが推定値（動的リサイズが必要な場合あり）

---

### C-2. FLACカバーアートJNI C++側実装

**目的**: Kotlin側で宣言した`extractFlacPictureBlock()`と`extractVorbisCommentPicture()`のC++実装

**実装内容**:
- `miyamizu_jni_bridge.cpp`に以下を追加:
  - `Java_com_miyamizu_player_playback_NativeEngine_extractFlacPictureBlock()`
    - libFLAC++の`FLAC__metadata_iterator`でMETADATA_BLOCK_PICTUREを検索
    - PICTUREブロックの生バイト列をjbyteArrayとして返却
  - `Java_com_miyamizu_player_playback_NativeEngine_extractVorbisCommentPicture()`
    - Vorbis Commentブロックを検索
    - `METADATA_BLOCK_PICTURE`フィールドをBase64デコード
    - デコード済みバイト列をjbyteArrayとして返却

**参考**: Kotlin側の`NativeEngine.getCoverArt()`と`parsePictureBlock()`は実装済み

---

### C-3. 実機テスト（USB DAC各種）

**目的**: 実際のUSB DACでビットパーフェクト出力・テレメトリ・UAC2記述子パースを検証

**テスト対象**:
- [ ] XMOSベースDAC（Topping、iFi等）
- [ ] AmaneroベースDAC
- [ ] TI/Burr-BrownベースDAC
- [ ] Sony USB DAC
- [ ] FiiOポータブルDAC

**検証項目**:
- USB接続・パーミッション要求・インターフェースclaim
- UAC2記述子パース（alt setting自動選択）
- Isochronous転送での再生
- テレメトリ（jitter / xrunカウント）の妥当性
- ビットパーフェクト判定の精度

---

### C-4. テレメトリ精度向上

**目的**: jitter RMSとxrun検出の精度を向上させる

**改善点**:
- AAudio: `AAudioStream_getTimestamp()`の活用強化
- USB: フレーム間隔の統計計算を改善（移動平均→標準偏差）
- xrun検出: 欠落フレーム数の正確なカウント
- テレメトリ更新間隔を500ms→200msに短縮（オプション）

---

### C-5. ビットパーフェクト判定精度向上

**目的**: ビットパーフェクト出力の判定をより正確にする

**判定条件の改善**:
- AAudio: MMAPモード + ボリューム1.0 + エフェクト無効 + サンプルレート一致
- USB: リサンプラー無効 + フォマット一致（16/24/32bit） + チャンネル数一致
- リサンプラー有効時は自動的に非ビットパーフェクトと判定
- システムオーディオエフェクト（Equalizer等）の検出

---

### C-6. パフォーマンス最適化

**目的**: CPU使用率・メモリ使用量・バッテリー消費を最適化

**最適化項目**:
- [ ] ネイティブコードの`-O3`最適化確認
- [ ] FLACデコードのバッファサイズ調整
- [ ] リサンプラーの出力バッファ動的リサイズ
- [ ] RAM再生キューのメモリ上限設定（デバイスRAMに応じた調整）
- [ ] USBリングバッファサイズの最適化（デバイスごとにチューニング）
- [ ] コルーチンのディスパッチャー最適化
- [ ] Glideのカバーアートキャッシュ設定

---

### C-7. リリースビルド設定（ProGuard/R8）

**目的**: リリース用APKの最適化と難読化

**設定内容**:
- [ ] `app/proguard-rules.pro`の作成
  - JNIネイティブメソッドのkeepルール
  - Room Entityのkeepルール
  - MediaSession関連クラスkeepルール
- [ ] `isMinifyEnabled = true`の動作確認
- [ ] `isShrinkResources = true`の動作確認
- [ ] リリースビルドでのクラッシュ検証

---

### C-8. ライブラリ画面本実装

**目的**: フェーズBで仮実装（Placeholder）だったLibrary画面を本実装

**実装内容**:
- `ui/library/LibraryScreen.kt` - アルバム一覧（グリッド表示）
- `ui/library/LibraryViewModel.kt` - MusicLibrary連携
- `ui/library/AlbumDetailScreen.kt` - アルバム詳細（トラック一覧）
- アルバムカバーアート表示（ディレクトリ内cover.jpg/folder.jpg）
- トラックタップでPlayer画面に遷移＋キュー設定

---

## 優先順位提案

| 優先度 | 作業 | 理由 |
|--------|------|------|
| P0 | C-1 NDKビルドテスト | ビルドが通らないと他の作業が進まない |
| P0 | C-7 ProGuard設定 | リリースビルドの前提条件 |
| P1 | C-2 FLACカバーアートJNI | UIの完成度に直結 |
| P1 | C-8 ライブラリ画面 | アプリの基本機能 |
| P2 | C-3 実機テスト | 品質保証（複数デバイス必要） |
| P2 | C-4 テレメトリ精度 | オーディオ品質の信頼性向上 |
| P2 | C-5 ビットパーフェクト判定 | コア機能の精度向上 |
| P3 | C-6 パフォーマンス最適化 | リリース前の最終調整 |

---

## フェーズC完了後のリリース準備

- Google Play Console設定
- アプリアイコン・スクリーンショット作成
- プライバシーポリシー作成（USBパーミッション・ストレージアクセス）
- オープンソースライセンス表記（libFLAC, libsoxr, r8brain-free-src, AAudio）
