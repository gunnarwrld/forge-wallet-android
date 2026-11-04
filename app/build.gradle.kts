plugins {
    alias(libs.plugins.forge.android.application)
    alias(libs.plugins.forge.android.application.compose)
    alias(libs.plugins.forge.hilt)
}

android {
    namespace = "dev.ledgerforge.wallet"

    defaultConfig {
        applicationId = "dev.ledgerforge.wallet"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
