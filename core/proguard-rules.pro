# MinisTermux ProGuard Rules

# Keep Termux native libraries
-keep class com.ministermux.** { *; }
-keep class com.termux.** { *; }

# Keep Kotlinx serialization
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# Keep Ktor client
-keep class io.ktor.** { *; }

# Keep OkHttp
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Keep AndroidX
-keep class androidx.** { *; }

# Keep JSON serialization
-keep class kotlinx.serialization.json.** { *; }

# Keep WebSocket
-keep class io.ktor.client.plugins.websocket.** { *; }

# Keep Coroutines
-keep class kotlinx.coroutines.** { *; }

# Keep MinisBridgeService and related
-keep class com.ministermux.bridge.** { *; }
-keep class com.ministermux.util.** { *; }
-keep class com.ministermux.boot.** { *; }

# Keep Termux API classes
-keep class com.termux.api.** { *; }

# Keep native method names
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep JNI classes
-keep class * {
    native <methods>;
}

# Don't obfuscate resource IDs
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Keep parcelable
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod