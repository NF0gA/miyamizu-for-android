# Miyamizu ProGuard/R8 Rules
# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# =============================================================================
# JNI Native Methods
# =============================================================================
# NativeEngineのexternalメソッドはJNI経由で呼ばれるため、名前を保持する必要がある
-keepclasseswithmembernames class * {
    native <methods>;
}

# MiyamizuPlayerのMediaSession互換メソッドも保持
-keep class com.miyamizu.player.playback.MiyamizuPlayer {
    public *;
}

-keep class com.miyamizu.player.playback.NativeEngine {
    public *;
}

-keep class com.miyamizu.player.playback.PlaybackEngine {
    public *;
}

-keep class com.miyamizu.player.playback.PlaybackEngine$* {
    *;
}

-keep class com.miyamizu.player.playback.PlaybackEngine$State { *; }
-keep class com.miyamizu.player.playback.PlaybackEngine$ResamplerConfig { *; }
-keep class com.miyamizu.player.playback.PlaybackEngine$UsbOutputConfig { *; }
-keep class com.miyamizu.player.playback.PlaybackEngine$Telemetry { *; }
-keep class com.miyamizu.player.playback.PlaybackEngine$ResamplerEngine { *; }

-keep class com.miyamizu.player.playback.FlacInfoData {
    *;
}

# =============================================================================
# Room Database
# =============================================================================
# Room Entityクラス
-keep class com.miyamizu.player.db.*Entity {
    <init>(...);
    @androidx.room.* <fields>;
}

# Room DAOインターフェース
-keep interface com.miyamizu.player.db.*Dao {
    *;
}

# Room Database
-keep class com.miyamizu.player.db.AppDatabase {
    *;
}

# Room TypeConverter
-keep class com.miyamizu.player.db.Converters {
    *;
}

# Roomの@ColumnInfo等のアノテーション
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# Roomが使用するjavax.annotation.Generated
-dontwarn javax.annotation.**

# =============================================================================
# DataStore Preferences
# =============================================================================
-keepclassmembers class androidx.datastore.* {
    *;
}

# =============================================================================
# Media3 / MediaSession
# =============================================================================
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# =============================================================================
# Compose
# =============================================================================
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ComposeのPreviewはリリースビルドで不要
-dontwarn androidx.compose.ui.tooling.preview.Preview

# =============================================================================
# Coroutines
# =============================================================================
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# =============================================================================
# External Libraries
# =============================================================================
# libFLAC / libsoxr / r8brain はネイティブライブラリなのでJava側のkeep不要
# ただしJNI呼び出し部分は上記の native <methods> でカバー済み

# Glide
-keep public class com.bumptech.glide.** { *; }
-dontwarn com.bumptech.glide.**

# =============================================================================
# Kotlin Serialization (将来使用する場合)
# =============================================================================
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# =============================================================================
# General Android
# =============================================================================
-keep public class android.net.http.SslError
-keep public class android.webkit.**{ *; }
-dontwarn android.webkit.**

# =============================================================================
# Logging (リリース時はLog.d/Log.vを削除、Log.e/Log.wは保持)
# =============================================================================
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
    public static int i(...);
}
