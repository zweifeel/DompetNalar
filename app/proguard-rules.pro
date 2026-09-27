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

# Google AdMob SDK
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }

# Navigation & Jetpack Compose
-keep class androidx.navigation.** { *; }
-keepclassmembers class * extends androidx.navigation.NavType { *; }
