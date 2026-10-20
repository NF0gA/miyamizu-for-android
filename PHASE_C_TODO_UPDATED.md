# Miyamizu Android 移植 - フェーズC 残作業まとめ（更新版）

## 完了した作業（このターンで実施）

| # | 作業 | 状態 |
|---|------|------|
| C-2 | FLACカバーアートJNI C++側実装 | 完了 - `miyamizu_jni_bridge.cpp`に`extractFlacPictureBlock()`と`extractVorbisCommentPicture()`を追加。libFLAC++のmetadata APIでPICTUREブロック/Vorbis Commentを検索。Base64デコーダー実装済み |
| C-7 | ProGuard/R8設定 | 完了 - `app/proguard-rules.pro`作成。JNIメソッド・Room Entity・DataStore・MediaSession・Compose Navigation・サードパーティライブラリのkeepルール定義済み |
| C-8 | ライブラリ画面本実装 | 完了 - `LibraryScreen.kt`（アルバムグリッド・検索・再スキャン）、`LibraryViewModel.kt`（アルバム一覧・カバーアートキャッシュ・検索）、`AlbumDetailScreen.kt`（カバー表示・トラック一覧・Play/Shuffleボタン） |

---

## 残作業（ユーザーが実施する項目）

### C-1. NDKビルドテスト（P0）

**目的**: 全ネイティブコーがarm64-v8a / x86_64両アーキテクチャでビルドを通す

**あなたがやるべきこと**:
1. `scripts/fetch_native_libs.sh` を実行して各ライブラリを取得
2. Android Studioでプロジェクトを開き、NDKビルドを実行
3. 以下を確認:
   - r8brain-free-srcのincludeパス（`CDSPRealFFT.h`等のヘッダー依存）
   - soxrのAndroidクロスコンパイル（`CMakeLists.txt.android`の動作）
   - arm64-v8a / x86_64 両アーキテクチャでのビルド
   - CMakeLists.txt統合（libs/各ディレクトリのCMakeLists.txtが正しくincludeされるか）

**既知の課題**:
- FLACデコーダーが`init_file`を使用（`init_stream`への切り替え推奨）
- USB出力のalt setting自動選択が簡易実装（ネイティブ側結果をJNIで返すAPI追加が理想）
- リサンプラーの出力バッファサイズが推定値（動的リサイズが必要な場合あり）

---

## 残作業（今後の拡張・任意）

### C-3. 実機テスト（P2）

**目的**: 実際のUSB DACでビットパーフェクト出力・テレメトリ・UAC2記述子パースを証

**テスト対象**:
- XMOSベースDAC（Topping、iFi等）
- AmaneroベースDAC
- TI/Burr-BrownベースDAC
- Sony USB DAC
- FiiOポータブルDAC

**検証項目**:
- USB接続・パーミッション要求・インターフェースclaim
- UAC2記述子パース（alt setting自動選択）
- Isochronous転送での再生
- テレメトリ（jitter / xrunカウント）の妥当性
- ビットパーフェクト判定の精度

### C-4. テレメトリ精度向上（P2）

**改善点**:
- AAudio: `AAudioStream_getTimestamp()`の活用強化
- USB: フレーム間隔の統計計算を改善（移動平均→標準偏差）
- xrun検出: 欠落フレーム数の正確なカウント
- テレメトリ更新間隔を500ms→200msに短縮（オプション）

### C-5. ビットパーフェクト判定精度向上（P2）

**判定条件の改善**:
- AAudio: MMAPモード + ボリューム1.0 + エフェクト無効 + サンプルレート一致
- USB: リサンプラー無効 + フォーマット一致（16/24/32bit） + チャンネル数一致
- リサンプラー有効時は自動的に非ビットパーフェクトと判定
- システムオーディオエフェクト（Equalizer等）の検出

### C-6. パフォーマンス最適化（P3）

**最適化項目**:
- ネイティブコードの`-O3`最適化確認
- FLACデコードのバッファサイズ調整
- リサンプラーの出力バッファ動的リサイズ
- RAM再生キューのメモリ上限設定（デバイスRAMに応じた調整）
- USBリングバッファサイズの最適化（デバイスごとにチューニング）
- コーチンのディスパッチャー最適化
- Glideのカバーアートキャッシュ設定

---

## フェーズC完了後のリリース準備

- Google Play Console設定
- アプリアイコン・スクリーンショット作成
- プライバシーポリシー作成（USBパーミッション・ストレージアクセス）
- オープンソースライセンス表記（libFLAC, libsoxr, r8brain-free-src, AAudio）
