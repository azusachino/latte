plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val latteVersion = "0.2.2"

val pixivOAuthClientId = System.getenv("LATTE_PIXIV_CLIENT_ID").orEmpty()
val pixivOAuthClientSecret = System.getenv("LATTE_PIXIV_CLIENT_SECRET").orEmpty()

android {
    namespace = "com.azusachino.latte"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.azusachino.latte"
        minSdk = 29
        targetSdk = 35
        versionCode = 10
        versionName = latteVersion

        buildConfigField("String", "PIXIV_OAUTH_CLIENT_ID", pixivOAuthClientId.asBuildConfigString())
        buildConfigField("String", "PIXIV_OAUTH_CLIENT_SECRET", pixivOAuthClientSecret.asBuildConfigString())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("LATTE_KEYSTORE_PATH")
                ?: project.findProperty("LATTE_KEYSTORE_PATH") as? String
            val storeFileObj = storeFilePath?.let { file(it) }

            if (storeFileObj != null && storeFileObj.exists()) {
                storeFile = storeFileObj
                storePassword = System.getenv("LATTE_KEYSTORE_PASSWORD")
                    ?: project.findProperty("LATTE_KEYSTORE_PASSWORD") as? String
                keyAlias = System.getenv("LATTE_KEY_ALIAS")
                    ?: project.findProperty("LATTE_KEY_ALIAS") as? String
                keyPassword = System.getenv("LATTE_KEY_PASSWORD")
                    ?: project.findProperty("LATTE_KEY_PASSWORD") as? String
            } else {
                val debugConfig = getByName("debug")
                storeFile = debugConfig.storeFile
                storePassword = debugConfig.storePassword
                keyAlias = debugConfig.keyAlias
                keyPassword = debugConfig.keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
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

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")

    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")

    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")

    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.browser:browser:1.8.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
