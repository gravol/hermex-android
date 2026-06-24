plugins {
    id("com.android.application")
}

android {
    namespace = "com.hermes.chat"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hermes.chat"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core:1.12.0")

    // Force consistent Kotlin stdlib to avoid duplicate class conflicts
    constraints {
        implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22") {
            because("appcompat + core pull in conflicting Kotlin stdlib versions")
        }
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22") {
            because("duplicate with kotlin-stdlib")
        }
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.22") {
            because("duplicate with kotlin-stdlib")
        }
    }
}
