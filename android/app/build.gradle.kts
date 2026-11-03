plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// 디버그 빌드의 API 주소. 에뮬레이터 기본값은 호스트의 8080 포트다.
// 실제 기기는 adb reverse tcp:8080 tcp:8080 후 -Pnurseflow.apiBaseUrl=http://127.0.0.1:8080 으로 빌드한다.
// 릴리스 빌드는 주소가 없어 서버 연결을 하지 않는다.
val debugApiBaseUrl = (project.findProperty("nurseflow.apiBaseUrl") as String?) ?: "http://10.0.2.2:8080"

android {
    namespace = "com.nurseflow.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nurseflow.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"\"")
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
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

tasks.withType<Test>().configureEach {
    // 로컬 통합 검증(LiveOrganizationApiTest)에 필요한 값만 전달한다. 없으면 해당 테스트는 건너뛴다.
    listOf("NURSEFLOW_LIVE_BASE_URL", "NURSEFLOW_LIVE_ISSUER", "NURSEFLOW_LIVE_HOSPITAL_ADMIN").forEach { key ->
        System.getenv(key)?.let { environment(key, it) }
    }
}
