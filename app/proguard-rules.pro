# ProGuard & R8 Configuration for Latte

# Preserve Kotlin Reflection / Serialization attributes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Kotlinx Serialization
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keep class com.azusachino.latte.data.model.** { *; }
-keep class com.azusachino.latte.data.network.** { *; }
-keep class com.azusachino.latte.data.update.** { *; }
-keep class com.azusachino.latte.plugin.PlatformId { *; }
-keepclassmembers enum * { *; }
-dontwarn kotlinx.serialization.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Coil 3
-keep class coil3.** { *; }
-dontwarn coil3.**

# AndroidX Browser / Custom Tabs
-keep class androidx.browser.** { *; }
-dontwarn androidx.browser.**

# AndroidX Security Crypto & Google Tink
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**
-keep class com.google.crypto.tink.** { *; }
-keepclassmembers class * extends com.google.crypto.tink.KeyManager { *; }
-keepclassmembers class * extends com.google.crypto.tink.PrimitiveWrapper { *; }
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.crypto.tink.**

# AndroidX WorkManager
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# Datastore Preferences
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**
