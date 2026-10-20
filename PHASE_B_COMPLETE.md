# Miyamizu Android 移植 - フェーズB完了報告

## 完了日: 2026-10-20

---

## フェーズB 全作業完了

### B-1. ギャップレス再生
- `playback/GaplessController.kt` - 次曲プリバッファ・PCMバッファ無縫継切り替え
- `playback/PlaybackEngine.kt` (更新) - `switchToPreloadedBuffer()`追加

### B-2. RAM再生
- `playback/RamPlaybackQueue.kt` - トラック事前デコード・RAMキュー管理（最大10トラック/同時2デコード）

### B-3. 設定画面
- `settings/SettingsRepository.kt` - DataStore Preferences永続化
- `settings/SettingsViewModel.kt` - UI用Flow開封設定適用
- `settings/SettingsScreen.kt` - Compose設定画面（リサンプラー/出力モード/RAM/テレメトリ/テーマ）

### B-4. プレイリスト・お気に入り・再生履歴（Room DB）
- `db/AppDatabase.kt` / `db/Converters.kt` / `db/Entity.kt` - Roomデータベース定義
- `db/PlaylistDao.kt` / `db/FavoriteDao.kt` / `db/PlaybackHistoryDao.kt` - DAO
- `db/PlaylistRepository.kt` - 統合リポジトリ（プレイリスト/お気に入り/履歴）
- `playlist/PlaylistScreen.kt` / `PlaylistViewModel.kt` - プレイリスト画面
- `favorites/FavoriteScreen.kt` / `FavoriteViewModel.kt` - お気に入り画面
- `history/HistoryScreen.kt` / `HistoryViewModel.kt` - 再生履歴画面

### B-5. Player画面・カバーアート統合
- `ui/player/PlayerScreen.kt` - カバーアート・テレメトリ・再生制御・お気に入りボタン
- `ui/player/PlayerViewModel.kt` - 位置更新・カバーアートロード・お気に入り状態
- `playback/NativeEngine.kt` (更新) - PICTUREブロック/Vorbis Comment抽出JNI
- `playback/MiyamizuPlayer.kt` (更新) - Gapless/RAM/履歴統合

### B-6. キュ画面・ナビゲーション統合
- `ui/queue/QueueScreen.kt` / `QueueViewModel.kt` - 再生キュー表示・順序変更・削除
- `MiyamizuApp.kt` (更新) - NavHost統合（Library/Playlists/Favorites/History/Player/Queue/Settings）

### B-7. DI（MiyamizuPlayerプロバイダー）
- `playback/MiyamizuPlayerProvider.kt` - シングルトン管理・コンポーネント組み立て・設定自動適用

### B-8. Room kapt -> ksp移行
- `app/build.gradle.kts` (更新) - `kapt`を削除、`ksp`プラグイ適用、`ksp("androidx.room:room-compiler")`に変更
- `build.gradle.kts` (プロジェクトルート新規) - kspプラグイン定義

---

## 合計ファイル数: 68ファイル

## 実装済み機能: 20項目

1. AAudio出力 (MMAP/SP_Shared両対応、テレメトリ収集)
2. USB Audio出力 (UAC2対応、SPSCリングバッファ、Isochronous)
3. FLACデコード (libFLAC++、CUE対応、RAM展開)
4. リサンプラー (soxr / r8brain-free-src 両対応)
5. TPDFディザ + ノイズシェイピング
6. UAC2記述子パーサー (alt setting自動選択)
7. USBデバイスマネージャー (fd管理、記述子取得)
8. JNIブリッジ (全ネイティブAPIのJava/Kotlinラッパー)
9. ギャップレス再生 (次曲プリバッファ・PCMバッファ切り替え)
10. RAM再生キュー (事前デコード・メモリ管理)
11. 設定画面 (リサンプラー/出力モード/RAM/テレメトリ/テーマ)
12. DataStore Preferences (設定永続化)
13. Room DB + ksp (プレイリスト/お気に入り/再生履歴)
14. プレイリスト画面 (作成/リネーム/削除/一覧)
15. お気に入り画面 (追加/削除/一覧)
16. 再生履歴画面 (最近の再生/クリア)
17. Player画面 (カバーアート・シーク・テレメトリ・お気に入り)
18. キュー画面 (順序変更・削除・再生位置ジャンプ)
19. ナビゲーション統合 (BottomNav + NavHost)
20. FLACカバーアート抽出 (PICTUREブロック/Vorbis Comment)

---

## 残作業（フェーズC）

1. NDKビルドテスト (arm64-v8a/x86_64)
2. FLACカバーアートJNI C++側実装 (extractFlacPictureBlock)
3. 実機テスト (USB DAC各種)
4. テレメトリ精度向上
5. ビットパーフェクト判定精度向上
6. パフォーマンス最適化
7. リリースビルド設定 (ProGuard/R8)
8. ライブラリ画面（アルバム一覧・トラック一覧）本実装
