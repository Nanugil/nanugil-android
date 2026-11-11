import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// API 키는 커밋하지 않는 local.properties(또는 CI 환경변수)에서 읽는다.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun secret(name: String): String = localProperties.getProperty(name) ?: System.getenv(name) ?: ""

android {
    namespace = "com.nanugil.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nanugil.app"
        // Pleos Connect 차량 보안 요구사항(서명 키 로테이션) 때문에 API 28 이상 필수
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "NAVER_MAP_CLIENT_ID", "\"${secret("NAVER_MAP_CLIENT_ID")}\"")
        buildConfigField("String", "OPINET_API_KEY", "\"${secret("OPINET_API_KEY")}\"")
        // TODO(서버): 경로선(Directions 5) 테스트용. Secret은 출시 전에 서버 프록시로 옮긴다 (ADR-002).
        buildConfigField("String", "NAVER_MAP_CLIENT_SECRET", "\"${secret("NAVER_MAP_CLIENT_SECRET")}\"")
        // 선택: NCP 스타일 에디터로 만든 지도 스타일 ID (Figma 시안의 회색 블록 지도). 없으면 기본 내비 지도를 다듬어 쓴다.
        buildConfigField("String", "NAVER_MAP_STYLE_ID", "\"${secret("NAVER_MAP_STYLE_ID")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.vehicle.sdk)
    implementation(libs.navihelper.sdk)
    implementation(libs.naver.map.sdk)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.ui.tooling)
}
