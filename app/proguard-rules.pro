# Hermes Chat — ProGuard / R8 rules
# Add project-specific keep rules below.
-keepattributes *Annotation*

# security-crypto: suppress warnings about error_prone annotations
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-keep class com.google.errorprone.** { *; }
