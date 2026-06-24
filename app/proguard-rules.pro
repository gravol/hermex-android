# Hermes Chat WebView APK - ProGuard rules
-keepattributes *Annotation*

# Keep WebView class
-keep class android.webkit.** { *; }

# Keep appcompat
-keep class androidx.appcompat.** { *; }

# Keep our activity
-keep class com.hermes.chat.** { *; }
