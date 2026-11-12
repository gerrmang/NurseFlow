import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Developer-specific values live in local.properties (git-ignored). Never commit Kakao keys.
//   kakao.nativeAppKey=<Kakao native app key>
//   nurseflow.apiBaseUrl=http://10.0.2.2:8080/      (emulator → host machine; physical device: http://<PC IP>:8080/)
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val kakaoNativeAppKey: String = localProperties.getProperty("kakao.nativeAppKey") ?: System.getenv("KAKAO_NATIVE_APP_KEY") ?: ""
fun String.withTrailingSlash() = if (endsWith("/")) this else "$this/" // Retrofit requires it
val debugApiBaseUrl: String = (localProperties.getProperty("nurseflow.apiBaseUrl") ?: "http://10.0.2.2:8080/").withTrailingSlash()
val releaseApiBaseUrl: String = (localProperties.getProperty("nurseflow.releaseApiBaseUrl") ?: "https://api.nurseflow.invalid/").withTrailingSlash()

android {
    namespace = "com.nurseflow.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nurseflow.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoNativeAppKey\"")
        manifestPlaceholders["kakaoScheme"] = "kakao$kakaoNativeAppKey"
        testInstrumentationRunner = "com.nurseflow.app.test.IsolatedSessionTestRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
            // Fallback when the server omits inviteUrl (no NURSEFLOW_INVITE_LINK_BASE). Development only.
            buildConfigField("String", "INVITE_LINK_BASE", "\"nurseflow://invite\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"$releaseApiBaseUrl\"")
            // No production link format yet (HTTPS App Links are a follow-up): issuance is disabled.
            buildConfigField("String", "INVITE_LINK_BASE", "\"\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui:1.7.0")
    implementation("androidx.compose.foundation:foundation:1.7.0")
    implementation("androidx.compose.material3:material3:1.3.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.0")

    // Screens, state and navigation
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Server API (same contract as docs/openapi.yaml)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Session storage (refresh token encrypted with an Android Keystore key)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Kakao login (OpenID Connect ID token)
    implementation("com.kakao.sdk:v2-user:2.20.6")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    // Compose 1.7.0 brings Espresso 3.5.0, whose InputManager reflection fails on API 37.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.7.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
