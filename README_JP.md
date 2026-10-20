# miyamizu-for-android ビルド修正パッチ

## 今回のActions失敗の直接原因

ログでは `configureCMakeDebug[arm64-v8a]` のCMake設定中に、次のエラーで止まっています。

```text
The source directory .../libs/soxr does not contain a CMakeLists.txt file.
```

`libs/soxr` ディレクトリ自体はリポジトリ内にありましたが、中身はプレースホルダ（`CMakeLists.txt.android`）だけでした。既存の `scripts/fetch_native_libs.sh` は「ディレクトリがあるか」だけで依存取得をスキップするため、soxr本体が取られないままCMakeに渡っていました。

## パッチに含む変更

- `scripts/fetch_native_libs.sh`：ディレクトリではなく必要なソースファイルを確認して取得。soxrは不完全なプレースホルダを上流ソースに置換し、FLAC/Ogg/r8brainはAndroid用CMakeラッパーを保持してソースを補充。
- `app/src/main/cpp/CMakeLists.txt`：soxrの不要なテスト・例・任意SIMD等を無効化。arm64に不適切な `-mfpu=neon` は付けない。
- `libs/r8brain-free-src/CMakeLists.txt`：上流に存在しない `.cpp` をビルド対象にしていた設定を、ヘッダーオンリーのINTERFACEライブラリに変更。
- `libs/flac/CMakeLists.txt`：Android向けに移植可能な標準Cソースだけをビルドし、x86/VSX向けソースを除外。

## GitHub Web UI での反映

zip内の各ファイルを展開し、同じパスのリポジトリ内ファイルを置き換えてコミットしてください。少なくとも次の4ファイルです。

1. `scripts/fetch_native_libs.sh`
2. `app/src/main/cpp/CMakeLists.txt`
3. `libs/r8brain-free-src/CMakeLists.txt`
4. `libs/flac/CMakeLists.txt`

反映後に `Actions` から失敗したワークフローを再実行します。

## 検証状況

このzipは今回提示されたCMakeの直接原因と、CMake設定上確認できた後続のネイティブ設定問題を修正するパッチです。Android SDK/NDKを使ったAPKビルドをこの場で実行できていないため、**ビルド成功を確認済みとは表記していません**。Actionsを再実行し、次に出たエラーを確認して続けて修正する前提です。
