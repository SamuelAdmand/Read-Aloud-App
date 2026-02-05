plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// --- Dynamic Build Configuration ---
val baseNamespace = "com.samuel.readaloud"
val baseAppName = "Read Aloud"

// --- Make changes to following code before commiting anything -----
val myAppVersionCode = 2
val appVersion = "1.0.0"
// --- End of the code ----

android {
    namespace = baseNamespace
    compileSdk = 36

    defaultConfig {
        applicationId = baseNamespace
        minSdk = 30
        targetSdk = 36
        versionCode = myAppVersionCode
        versionName = appVersion

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // These will be filled by the GitHub robot
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val abi = output.getFilter(com.android.build.OutputFile.ABI) ?: "universal"
            output.outputFileName = "ReadAloud-${versionName}-${abi}.apk"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "RA Debug")
        }
        release {
            resValue("string", "app_name", baseAppName)
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("debug") {
            java.srcDir("build/generated/ksp/debug/java")
            java.srcDir("build/generated/ksp/debug/kotlin")
        }
        getByName("release") {
            java.srcDir("build/generated/ksp/release/java")
            java.srcDir("build/generated/ksp/release/kotlin")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // The Material 3 library with Expressive APIs
    implementation(libs.androidx.material3)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Extended Icons (for Home, Settings, etc.)
    implementation(libs.material.icons.extended.android)
    // Media (for MediaSession and Notification MediaStyle)
    implementation("androidx.media:media:1.7.1")
    // Markdown Renderer
    implementation("com.mikepenz:multiplatform-markdown-renderer-android:0.38.1")
    // Room Database
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.11.0")
    // OkHttp for network requests
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    // Gson
    implementation("com.google.code.gson:gson:2.13.2")

    // Native Content Extraction
    implementation("net.dankito.readability4j:readability4j:1.0.8")
    implementation("org.jsoup:jsoup:1.18.3")
}