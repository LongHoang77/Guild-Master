plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

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

    // Khoá cố định commit trong repo → mọi bản build cùng chữ ký, cài đè được
    signingConfigs {
        create("release") {
            storeFile = file("signing.p12")
            storeType = "PKCS12"
            storePassword = "guildmaster"
            keyAlias = "guildmaster"
            keyPassword = "guildmaster"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
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
