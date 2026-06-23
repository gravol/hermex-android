plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hermes.chat"
    compileSdk = 34

    val ciRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
    val localVersionCode = 11
    val resolvedVersionCode = ciRunNumber ?: localVersionCode

    defaultConfig {
        applicationId = "com.hermes.chat"
        minSdk = 26
        targetSdk = 34
        versionCode = resolvedVersionCode
        versionName = "0.1.$resolvedVersionCode"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // ── Network defaults ──────────────────────────────────
        // Real-phone defaults for Jeff's Hermes host. Settings can still override them.
        val defaultTailscaleUrl = System.getenv("HERMES_DEFAULT_TAILSCALE_URL")
            ?: "http://100.80.204.66:8650/v1/chat/completions"
        val defaultLocalUrl = System.getenv("HERMES_DEFAULT_LOCAL_URL")
            ?: "http://192.168.68.105:8650/v1/chat/completions"
        buildConfigField("String", "DEFAULT_TAILSCALE_BASE_URL", "\"$defaultTailscaleUrl\"")
        buildConfigField("String", "DEFAULT_LOCAL_BASE_URL", "\"$defaultLocalUrl\"")

        // Production placeholder retained for future non-Jeff deployments.
        buildConfigField("String", "PRODUCTION_BASE_URL",
            "\"https://hermes.internal.example.com/v1/chat/completions\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            storeFile = project.rootProject.file("app/keystore/hermes-chat.jks")
            storePassword = System.getenv("HERMES_STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("HERMES_KEY_ALIAS") ?: ""
            keyPassword = System.getenv("HERMES_KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        debug { isDebuggable = true }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs["release"]
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
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ── Test ────────────────────────────────────────────────────
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.22")
    testImplementation("org.json:json:20240303")

    // ── Instrumentation test ────────────────────────────────────
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:rules:1.5.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
