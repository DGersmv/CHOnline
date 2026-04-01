import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// Публичный origin как у Nginx (443), не :8788 — снаружи открыты только 80/443.
val apiBaseUrl: String =
    (localProperties.getProperty("api.base.url") ?: "https://messenger.227.info").trim()
// Email администратора (доступ к /admin/*), как VITE_ADMIN_EMAIL на вебе.
val adminEmail: String =
    (localProperties.getProperty("admin.email") ?: "admin@227.info").trim()
val rustorePushProjectId: String =
    (localProperties.getProperty("rustore.push.project.id") ?: "").trim()

// coturn: переопределение в local.properties; пароль не храним в репозитории — только в local.properties или ICE_TURN_PASSWORD.
val defaultIceTurnUrls =
    "turn:83.166.246.116:3478?transport=udp,turn:83.166.246.116:3478?transport=tcp"
val defaultIceTurnUsername = "webrtc"
val iceTurnUrls: String =
    (localProperties.getProperty("ice.turn.urls") ?: defaultIceTurnUrls).trim()
val iceTurnUsername: String =
    (localProperties.getProperty("ice.turn.username") ?: defaultIceTurnUsername).trim()
val iceTurnPassword: String =
    (
        localProperties.getProperty("ice.turn.password")
            ?: System.getenv("ICE_TURN_PASSWORD")
            ?: ""
    ).trim()

fun escBuildConfig(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.example.chonline"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.chonline"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Переопределение: local.properties → api.base.url=... (без /api/v1). Локально: http://10.0.2.2:8788
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "ADMIN_EMAIL", "\"${adminEmail.replace("\"", "\\\"")}\"")
        buildConfigField("String", "RUSTORE_PUSH_PROJECT_ID", "\"${rustorePushProjectId.replace("\"", "\\\"")}\"")
        buildConfigField("String", "ICE_TURN_URLS", "\"${escBuildConfig(iceTurnUrls)}\"")
        buildConfigField("String", "ICE_TURN_USERNAME", "\"${escBuildConfig(iceTurnUsername)}\"")
        buildConfigField("String", "ICE_TURN_PASSWORD", "\"${escBuildConfig(iceTurnPassword)}\"")
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
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.security.crypto)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.socket.io.client)
    implementation(libs.coil.compose)
    implementation(libs.rustore.pushclient)
    implementation(libs.google.webrtc)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
