import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.secrets)
    alias(libs.plugins.google.services)
}

// Local-first on-device AI architecture active - zero Gemini API key required.
android {
    namespace = "com.example"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aistudio.finsmart.kptxvw"
        minSdk = 24
        targetSdk = 34
        versionCode = 16
        versionName = "1.0.16"

        buildConfigField("boolean", "IS_PLAY_STORE_BUILD", "false")
        buildConfigField("String", "ADMOB_APP_ID", "\"ca-app-pub-3940256099942544~3347511713\"")
        buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/9214589741\"")
        buildConfigField("String", "ADMOB_REWARDED_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/5224354917\"")

        buildConfigField("String", "ADMOB_INTERSTITIAL_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        localeFilters += listOf("id", "en")
    }

    val localProperties = Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) {
            file.inputStream().use { load(it) }
        }
    }

    fun getReleaseSigningProperty(key: String): String? {
        return System.getenv(key)
            ?: (project.findProperty(key) as? String)
            ?: localProperties.getProperty(key)
    }

    signingConfigs {
        create("release") {
            val keystorePath = getReleaseSigningProperty("KEYSTORE_PATH")
                ?: getReleaseSigningProperty("RELEASE_KEYSTORE_PATH")
                ?: "${rootDir}/release-key.jks"
            val keystoreFile = file(keystorePath)

            val sPassword = getReleaseSigningProperty("RELEASE_KEYSTORE_PASSWORD")
                ?: getReleaseSigningProperty("STORE_PASSWORD")
            val kAlias = getReleaseSigningProperty("RELEASE_KEY_ALIAS")
                ?: getReleaseSigningProperty("KEY_ALIAS")
            val kPassword = getReleaseSigningProperty("RELEASE_KEY_PASSWORD")
                ?: getReleaseSigningProperty("KEY_PASSWORD")

            if (keystoreFile.exists() && !sPassword.isNullOrBlank() && !kAlias.isNullOrBlank() && !kPassword.isNullOrBlank()) {
                storeFile = keystoreFile
                storePassword = sPassword
                keyAlias = kAlias
                keyPassword = kPassword
            }
        }
        getByName("debug") {
            val debugKeystore = file("${rootDir}/debug.keystore")
            val userDebugKeystore = file("${System.getProperty("user.home")}/.android/debug.keystore")
            if (debugKeystore.exists()) {
                storeFile = debugKeystore
            } else if (userDebugKeystore.exists()) {
                storeFile = userDebugKeystore
            }
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            val isPlayTarget = (project.findProperty("playStoreBuild") as? String)?.toBoolean() ?: true
            buildConfigField("boolean", "IS_PLAY_STORE_BUILD", isPlayTarget.toString())
            val releaseAdmobAppId = getReleaseSigningProperty("ADMOB_APP_ID") ?: "ca-app-pub-2539100802088383~9012414109"
            val releaseAdmobBannerId = getReleaseSigningProperty("ADMOB_BANNER_AD_UNIT_ID") ?: "ca-app-pub-2539100802088383/9128798324"
            val releaseAdmobRewardedId = getReleaseSigningProperty("ADMOB_REWARDED_AD_UNIT_ID") ?: "ca-app-pub-2539100802088383/3501242135"
            val releaseAdmobInterstitialId = getReleaseSigningProperty("ADMOB_INTERSTITIAL_AD_UNIT_ID") ?: "ca-app-pub-2539100802088383/4814323801"
            buildConfigField("String", "ADMOB_APP_ID", "\"$releaseAdmobAppId\"")
            buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", "\"$releaseAdmobBannerId\"")
            buildConfigField("String", "ADMOB_REWARDED_AD_UNIT_ID", "\"$releaseAdmobRewardedId\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_AD_UNIT_ID", "\"$releaseAdmobInterstitialId\"")
            manifestPlaceholders["admobAppId"] = releaseAdmobAppId
            isCrunchPngs = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "FULL"
            }
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile?.exists() == true && !releaseSigning.storePassword.isNullOrBlank()) {
                signingConfig = releaseSigning
            } else {
                signingConfig = null
            }
        }
        debug {
            val isPlayDebug = (project.findProperty("playStoreBuild") as? String)?.toBoolean() ?: false
            buildConfigField("boolean", "IS_PLAY_STORE_BUILD", isPlayDebug.toString())
            buildConfigField("String", "ADMOB_APP_ID", "\"ca-app-pub-3940256099942544~3347511713\"")
            buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/9214589741\"")
            buildConfigField("String", "ADMOB_REWARDED_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/5224354917\"")

            buildConfigField("String", "ADMOB_INTERSTITIAL_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            applicationIdSuffix = ".debug"
            val debugKeystore = file("${rootDir}/debug.keystore")
            val userDebugKeystore = file("${System.getProperty("user.home")}/.android/debug.keystore")
            if (debugKeystore.exists() || userDebugKeystore.exists()) {
                signingConfig = signingConfigs.getByName("debug")
            } else {
                signingConfig = null
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests { isIncludeAndroidResources = true } }
    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
        getByName("test").assets.directories.add("$projectDir/schemas")
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }
}

// Configure Room Schema export directory for KSP
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
secrets {
    propertiesFileName = ".env"
    defaultPropertiesFileName = ".env.example"
    ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))
    // implementation(libs.accompanist.permissions)
    implementation(libs.androidx.activity.compose)
    // implementation(libs.androidx.camera.camera2)
    // implementation(libs.androidx.camera.core)
    // implementation(libs.androidx.camera.lifecycle)
    // implementation(libs.androidx.camera.view)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    // implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.coil.compose)
    implementation(libs.converter.moshi)

    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)

    // Google Sign-In via Credential Manager & Google Play Services Auth:
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    // Media3 ExoPlayer for video splash screen
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation(libs.googleid)
    implementation(libs.play.services.auth)
    implementation(libs.firebase.appcheck.recaptcha)
    implementation(libs.firebase.appcheck.debug)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation(libs.play.services.ads)
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    implementation(libs.play.billing)
    implementation(libs.logging.interceptor)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    // implementation(libs.play.services.location)
    implementation(libs.retrofit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation("androidx.work:work-testing:2.9.0")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    "ksp"(libs.androidx.room.compiler)
    "ksp"(libs.moshi.kotlin.codegen)
}
