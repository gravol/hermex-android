plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hermes.chat"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hermes.chat"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables { useSupportLibrary = true }

        // ── Network hardening ─────────────────────────────────
        // Production Hermes endpoint. Override by setting HERMES_BASE_URL env var.
        buildConfigField("String", "PRODUCTION_BASE_URL",
            "\"https://hermes.internal.example.com/v1/chat/completions\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug { isDebuggable = true }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use debug keystore for development releases
            signingConfig = signingConfigs["debug"]
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }

    // ── Unit tests ──────────────────────────────────────────────
    testOptions {
        unitTests.isReturnDefaultValues = false
        unitTests.all { it.useJUnitPlatform() }
    }

    // ── Lint ────────────────────────────────────────────────────
    lint {
        // lint.xml is the single source of truth for severities
        checkReleaseBuilds = true
        abortOnError = false
        htmlReport = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ── Test ────────────────────────────────────────────────────
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.22")
}
