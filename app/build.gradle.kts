plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jjz.assist"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jjz.assist"
        minSdk = 31
        targetSdk = 35
        versionCode = 11
        versionName = "1.10"
    }

    signingConfigs {
        // keystore 不在仓库里（只推文本）。文件在（本地或 CI 拼出来）才签名，
        // 否则出 unsigned 包——保证无签名材料时 CI 仍能编译验证。
        if (rootProject.file("jjz-release.keystore").exists()) {
            create("release") {
                storeFile = rootProject.file("jjz-release.keystore")
                storePassword = "jjz2026assist"
                keyAlias = "jjz"
                keyPassword = "jjz2026assist"
            }
        }
    }

    buildTypes {
        release {
            // R8 混淆 + 资源压缩：debug 包 5.7MB 的 dex 主要是没优化的 ML Kit 全量类
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // 按 ABI 拆包：ML Kit 的 libmlkit_google_ocr_pipeline.so 四个架构各 6~11MB，
    // 合在一起就是 36.6MB。拆开后每台机器只装自己那一份。
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/{AL2.0,LGPL2.1}",
                "DebugProbesKt.bin"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")
}
