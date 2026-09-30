# ProGuard & R8 Configuration for Latte

# Preserve Kotlin Reflection / Serialization attributes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Kotlinx Serialization
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
    @kotlinx.serialization.Serializable <fields>;
}
-dontwarn kotlinx.serialization.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Coil 3
-keep class io.coil_kt.** { *; }
-dontwarn io.coil_kt.**

# AndroidX Security Crypto & Keystore
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.crypto.tink.**

# AndroidX WorkManager
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# Datastore Preferences
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**
