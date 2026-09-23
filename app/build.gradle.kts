import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// v1.2.1：release 签名配置。凭据读项目根 keystore.properties；
// 缺失或 keystore 不存在时回退 debug 密钥，保证 assembleRelease 始终可构建。
val keystoreProps = Properties().apply {
    val pf = rootProject.file("keystore.properties")
    if (pf.exists()) pf.inputStream().use { load(it) }
}
val releaseStoreFile = keystoreProps.getProperty("storeFile")
    ?.let { rootProject.file(it) }
    ?.takeIf { it.exists() }

android {
    namespace = "com.qinglong.panel"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.qinglong.panel"
        minSdk = 24
        targetSdk = 35
        versionCode = 12
        versionName = "1.2.3"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // v1.2.1：release 正式签名（用户要求不再打 debug 包）
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
        debug {
            // v1.2.2：去掉 .debug 后缀，与 release 统一 applicationId（com.qinglong.panel）。
            // 原因：applicationId 决定应用数据目录，debug/release 包名不同会导致
            // 两者数据隔离——用户从 debug 版换装 release 版后登录信息"丢失"（实际存在
            // 各自沙箱中但不可见）。统一后 debug/release 互为覆盖升级，登录态保留。
            // 签名同步用 release keystore（缺失时回退 debug 默认密钥），保证同包名可互相覆盖安装。
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
