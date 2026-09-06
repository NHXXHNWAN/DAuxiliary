plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dauxiliary"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dauxiliary"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
// Use the ARM64 AAPT2 artifact only on ARM64 hosts. GitHub Actions uses x86_64.
if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "com.android.tools.build" && requested.name == "aapt2") {
                useTarget("com.android.tools.build:aapt2:${requested.version}:linux-aarch64")
            }
        }
    }
}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))

    // Miuix UI + blur/effect components (HyperOS style)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)

    // Xposed: compile-only, provided by LSPosed at runtime
    compileOnly(libs.xposed.api)
}