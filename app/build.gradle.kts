plugins {
    alias(libs.plugins.forge.android.application)
    alias(libs.plugins.forge.android.application.compose)
    alias(libs.plugins.forge.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.ledgerforge.wallet"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "dev.ledgerforge.wallet"
        versionCode = 1
        versionName = "0.1.0"

        // The emulator's route to a Ledger Forge on this machine. For a deployed one, pass
        // -PledgerBaseUrl=https://example.com/ (it must end with a slash).
        val ledgerBaseUrl = providers.gradleProperty("ledgerBaseUrl").getOrElse("http://10.0.2.2:8080/")
        buildConfigField("String", "LEDGER_BASE_URL", "\"$ledgerBaseUrl\"")
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
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(projects.core.network)
    implementation(projects.feature.activity)
    implementation(projects.feature.home)
    implementation(projects.feature.send)
    ksp(libs.androidx.hilt.compiler)
}
