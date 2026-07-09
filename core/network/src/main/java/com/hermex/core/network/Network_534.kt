// Retrofit & Gson/Kotlinx Serialization
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

// OkHttp
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

// SSE (Server-Sent Events)
// Retrofit doesn't natively support SSE, we use OkHttp's EventSource
implementation("com.squareup.okhttp3:okhttp:4.12.0")