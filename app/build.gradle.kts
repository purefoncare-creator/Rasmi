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
    // تم التخفيض إلى 35 لضمان استقرار البناء ولتجنب أخطاء حزم المطورين
    compileSdk = 35

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }

    defaultConfig {
        applicationId = "com.rasmi.purevon"
        minSdk = 26
        targetSdk = 35
        versionCode = 27
        versionName = "1.2.7"

        testInstrumentationRunner = "com.rasmi.purevon.HiltTestRunner"
        
        vectorDrawables {
            useSupportLibrary = true
        }
        
        // تم نقل ksp من هنا إلى الأسفل كجذر منفصل
        
        ndk {
            // التصحيح: استخدام += بدلاً من addAll ليتوافق مع Kotlin DSL
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        // التصحيح: استخدام += بدلاً من addAll
        resourceConfigurations += listOf(
            "en", "ar", "fr", "es", "de", "pt", "tr", "hi", "ur", "fa", 
            "id", "ms", "ru", "ja", "ko", "zh-rCN", "zh-rTW", "it", "nl", 
            "pl", "uk", "bn", "sw", "vi", "th", "fil", "el", "he", "sv", 
            "no", "da", "fi", "cs", "hu", "ro", "sk", "bg", "hr", "sr", 
            "sl", "et", "lv", "lt", "ca", "is", "sq", "hy", "ka", "az", 
            "kk", "uz"
        )
    }

    // Configure signing for release builds
    signingConfigs {
        create("release") {
            // Read from keystore.properties file (create it from keystore.properties.example)
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            
            when {
                keystorePropertiesFile.exists() -> {
                    val keystoreProperties = Properties()
                    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
                    
                    val keystorePath = requireNotNull(keystoreProperties["storeFile"]?.toString()) {
                        "keystore.properties must define storeFile for release builds"
                    }
                    require(file(keystorePath).exists()) {
                        "Release keystore not found at: $keystorePath"
                    }
                    storeFile = file(keystorePath)
                    storePassword = requireNotNull(keystoreProperties["storePassword"]?.toString())
                    keyAlias = requireNotNull(keystoreProperties["keyAlias"]?.toString())
                    keyPassword = requireNotNull(keystoreProperties["keyPassword"]?.toString())
                }
                
                System.getenv("KEYSTORE_FILE") != null -> {
                    // For CI/CD: use environment variables
                    val keystorePath = System.getenv("KEYSTORE_FILE")
                    storeFile = file(keystorePath)
                    storePassword = requireNotNull(System.getenv("STORE_PASSWORD"))
                    keyAlias = requireNotNull(System.getenv("KEY_ALIAS"))
                    keyPassword = requireNotNull(System.getenv("KEY_PASSWORD"))
                }
                
                else -> {
                    throw GradleException(
                        "Release signing is not configured. Provide keystore.properties or KEYSTORE_FILE/STORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD."
                    )
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
            initWith(getByName("release"))
            
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-STAGING"
            
            // Staging mirrors release security profile
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            
            buildConfigField("String", "API_BASE_URL", "\"https://staging-api.purevon.com\"")
            buildConfigField("boolean", "ENABLE_LOGGING", "false")
            
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

// ✅ التصحيح: وضع ksp ككتلة مستقلة في المستوى الجذري
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.exifinterface)

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
    implementation(libs.androidx.security.crypto)

    // Media3 (ExoPlayer) for video playback
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    // HTML Parser for link previews
    implementation(libs.jsoup)

    // SMS/MMS Library - Global APN database
    // ❌ التصحيح: تم تعليق هذه المكتبة لتجنب انهيار البناء بالخطأ 25.0.4.
    // ابحث عن بدائل حديثة متوافقة مع AndroidX إذا كنت بحاجة لها.
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
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
    
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.androidx.test.runner)
    kspAndroidTest(libs.hilt.android.compiler)
    
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
