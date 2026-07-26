import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.rasmi.purevon"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rasmi.purevon"
        minSdk = 26
        targetSdk = 36
        versionCode = 18
        versionName = "1.1.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        vectorDrawables {
            useSupportLibrary = true
        }
        
        // Room schema export directory
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
        
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
        }

        // App supports English, Arabic, and all 50 target translation languages — strip other locales pulled in by libraries.
        resourceConfigurations.addAll(listOf(
            "en", "ar", "fr", "es", "de", "pt", "tr", "hi", "ur", "fa", 
            "id", "ms", "ru", "ja", "ko", "zh-rCN", "zh-rTW", "it", "nl", 
            "pl", "uk", "bn", "sw", "vi", "th", "fil", "el", "he", "sv", 
            "no", "da", "fi", "cs", "hu", "ro", "sk", "bg", "hr", "sr", 
            "sl", "et", "lv", "lt", "ca", "is", "sq", "hy", "ka", "az", 
            "kk", "uz"
        ))
    }

    // Configure signing for release builds
    signingConfigs {
        create("release") {
            // Read from keystore.properties file (create it from keystore.properties.example)
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            val encryptedKeystoreFile = rootProject.file("keystore.properties.enc")
            
            // Auto-decrypt if only encrypted file exists
            if (!keystorePropertiesFile.exists() && encryptedKeystoreFile.exists()) {
                logger.warn("⚠️ keystore.properties not found but .enc exists — run ./decrypt_keystore.sh to decrypt")
            }
            
            when {
                keystorePropertiesFile.exists() -> {
                    val keystoreProperties = Properties()
                    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
                    
                    val keystorePath = keystoreProperties["storeFile"]?.toString()
                    if (keystorePath != null && file(keystorePath).exists()) {
                        storeFile = file(keystorePath)
                        storePassword = keystoreProperties["storePassword"]?.toString() ?: ""
                        keyAlias = keystoreProperties["keyAlias"]?.toString() ?: ""
                        keyPassword = keystoreProperties["keyPassword"]?.toString() ?: ""
                    } else {
                        logger.warn("⚠️ Keystore file not found at: $keystorePath - Release builds will use debug signing")
                    }
                }
                
                System.getenv("KEYSTORE_FILE") != null -> {
                    // For CI/CD: use environment variables
                    val keystorePath = System.getenv("KEYSTORE_FILE")
                    storeFile = file(keystorePath)
                    storePassword = System.getenv("STORE_PASSWORD") ?: ""
                    keyAlias = System.getenv("KEY_ALIAS") ?: ""
                    keyPassword = System.getenv("KEY_PASSWORD") ?: ""
                }
                
                else -> {
                    logger.warn("⚠️ No keystore configuration found - Release builds will use debug signing")
                    logger.warn("   Create keystore.properties from keystore.properties.example for production builds")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            
            // Disable debugging for release
            isDebuggable = false
            
            // Build config fields
            buildConfigField("String", "API_BASE_URL", "\"https://api.purevon.com\"")
            buildConfigField("boolean", "ENABLE_LOGGING", "false")
        }
        
        create("staging") {
            initWith(getByName("debug"))
            
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-STAGING"
            
            // Enable minification but keep debuggable
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = true
            
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // Use release signing for staging (if available)
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            
            // Build config fields for staging
            buildConfigField("String", "API_BASE_URL", "\"https://staging-api.purevon.com\"")
            buildConfigField("boolean", "ENABLE_LOGGING", "true")
            
            // Different app name for staging
            resValue("string", "app_name", "Purevon Staging")
        }
        
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = true
            
            // Build config fields for debug
            buildConfigField("String", "API_BASE_URL", "\"https://dev-api.purevon.com\"")
            buildConfigField("boolean", "ENABLE_LOGGING", "true")
            
            // Different app name for debug
            resValue("string", "app_name", "Purevon Debug")
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xjvm-default=all"
        )
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.core.splashscreen)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Database Encryption (SQLCipher)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // Paging
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Image Loading
    implementation(libs.coil.compose)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Security
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)

    // Media3 (ExoPlayer) for video playback
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    // HTML Parser for link previews
    implementation(libs.jsoup)

    // SMS/MMS Library - Global APN database
    implementation(libs.klinker.android.smsmms)

    // Memory Leak Detection (Debug only)
    debugImplementation(libs.leakcanary)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.room.testing)
    
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
