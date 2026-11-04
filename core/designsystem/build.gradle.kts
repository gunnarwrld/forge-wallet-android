plugins {
    alias(libs.plugins.forge.android.library)
    alias(libs.plugins.forge.android.library.compose)
}

android {
    namespace = "dev.ledgerforge.wallet.core.designsystem"
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    api(projects.core.money)

    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
