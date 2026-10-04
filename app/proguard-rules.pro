# FinSmart Security Suite ProGuard Rules

# Preserve line numbers and source file names for crash reporting
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# Room Database & SQLite
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keepclassmembers class * {
    @androidx.room.* *;
}

# Kotlinx Serialization
-keepattributes *Annotation*,ElementValuePairs
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit & OkHttp
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers enum * { *; }
-keepclassmembers class * {
    @retrofit2.http.* <fields>;
    @retrofit2.http.* <methods>;
}

# Gemini API & Data Models
-keep class com.example.data.model.** { *; }
-keep class com.example.data.local.** { *; }
-keep interface com.example.data.ai.GeminiApiService { *; }
-keep class com.example.data.ai.** { *; }

# Moshi JSON Library & Codegen Adapters
-keepclasseswithmembers class * {
    @com.squareup.moshi.JsonClass <init>(...);
}
-keep class * extends com.squareup.moshi.JsonAdapter
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**

# WorkManager Reflection Instantiation
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# App Version Metadata (GoogleDriveUpdateManager reflection serialization)
-keep class com.example.util.AppVersionMetadata { *; }

# MainActivity target for dynamic launcher aliases
-keep class com.example.MainActivity { *; }

# Google AdMob SDK
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }

# Navigation & Jetpack Compose
-keep class androidx.navigation.** { *; }
-keepclassmembers class * extends androidx.navigation.NavType { *; }

# Strip debug and verbose logs in release build
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
