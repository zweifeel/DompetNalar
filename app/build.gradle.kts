import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.secrets)
    alias(libs.plugins.google.services)
}

// Ensure .env contains GEMINI_API_KEY from environment variable if provided in CI/CD (GitHub Actions)
val envGeminiKey = System.getenv("GEMINI_API_KEY") ?: (project.findProperty("GEMINI_API_KEY") as? String) ?: ""
if (envGeminiKey.isNotBlank() && envGeminiKey != "MY_GEMINI_API_KEY") {
    val envFile = rootProject.file(".env")
    if (!envFile.exists()) {
        envFile.writeText("GEMINI_API_KEY=$envGeminiKey\n")
    } else {
        val currentContent = envFile.readText()
        if (!currentContent.contains("GEMINI_API_KEY=") || currentContent.contains("GEMINI_API_KEY=MY_GEMINI_API_KEY")) {
            val updated = currentContent.lines()
                .filterNot { it.startsWith("GEMINI_API_KEY=") }
                .plus("GEMINI_API_KEY=$envGeminiKey")
                .joinToString("\n")
            envFile.writeText(updated)
        }
    }
}

android {
    namespace = "com.example"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aistudio.finsmart.kptxvw"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            isCrunchPngs = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile?.exists() == true && !releaseSigning.storePassword.isNullOrBlank()) {
                signingConfig = releaseSigning
            } else {
                signingConfig = null
            }
        }
        debug {
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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
    implementation(libs.firebase.ai)
    // Uncomment to use Firestore:
    // implementation(libs.firebase.firestore)

    // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google Sign-In via Credential Manager:
    // implementation(libs.firebase.auth)
    // implementation(libs.androidx.credentials)
    // implementation(libs.androidx.credentials.play.services)
    // implementation(libs.googleid)
    implementation(libs.firebase.appcheck.recaptcha)
    implementation(libs.firebase.appcheck.debug)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation(libs.play.services.ads)
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
