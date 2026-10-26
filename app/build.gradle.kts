plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.parallelview"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.parallelview"
        // miuix-blur 0.9.4（液态玻璃 highlight / sensor API）要求 Android 13。
        minSdk = 33
        targetSdk = 36
        versionCode = 9
        versionName = "1.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

android {
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    // Xposed API: compile only, provided at runtime by LSPosed
    compileOnly(files("stubs.jar"))
    // Full Material Symbols set; R8 keeps only the icons actually referenced
    implementation(files("material-icons-extended.jar"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.blur)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
