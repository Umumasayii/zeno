plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Debug-only feasibility probe. Separate applicationId so it can be installed beside Zeno and
// uninstalled afterwards without touching the real app's permissions or data.
android {
    namespace = "com.zeno.spike"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.zeno.spike"
        minSdk = 34
        targetSdk = 34
        versionCode = 1
        versionName = "spike-0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
