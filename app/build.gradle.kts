plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Khoá ký lấy từ biến môi trường (GitHub Secrets), không commit vào repo
val keystorePath: String? = System.getenv("KEYSTORE_PATH")
// CI truyền số lần chạy làm versionCode → tag release v<versionCode>
val buildNumber = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1

android {
    namespace = "vn.guildmaster.game"
    compileSdk = 34

    defaultConfig {
        applicationId = "vn.guildmaster.game"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isDebuggable = false
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(project(":core"))
}
