dependencies {
    // Coroutines & Flow
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Security (EncryptedPrefs)
    implementation("androidx.security:security-crypto-ktx:1.1.0-alpha06")

    // Serialization (Kotlinx Serialization)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    
    // Hilt (Optional, for dependency injection)
    // implementation("com.google.dagger:hilt-android:2.50")
}