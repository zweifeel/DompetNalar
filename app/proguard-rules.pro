# FinSmart Security Suite ProGuard Rules

# Preserve line numbers and source file names for crash reporting
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# Room Database & SQLite
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keepclassmembers class * {
    @androidx.room.* *;
}
-keep class * extends androidx.room.migration.Migration { *; }
-keep class * implements androidx.room.RoomOpenHelper$Delegate { *; }
-keep class * extends androidx.room.RoomOpenHelper$Delegate { *; }
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

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

# FinSmart Data Models, Local AI & On-Device ML Kit
-keep class com.example.data.model.** { *; }
-keep class com.example.data.local.** { *; }
-keep class com.example.data.ai.** { *; }
-keep class com.example.data.chatbot.** { *; }
-keep class com.example.data.ocr.** { *; }
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.tasks.** { *; }
-dontwarn com.google.android.gms.tasks.**
-keep class com.google.android.gms.vision.** { *; }
-dontwarn com.google.android.gms.vision.**

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

# Google Play Billing Library
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# AndroidX Media3 / ExoPlayer (Video Splash Screen)
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Google Credential Manager & Identity
-keep class androidx.credentials.** { *; }
-dontwarn androidx.credentials.**
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.android.libraries.identity.googleid.**

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
