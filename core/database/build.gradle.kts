plugins {
    alias(libs.plugins.forge.android.library)
    alias(libs.plugins.forge.android.room)
    alias(libs.plugins.forge.hilt)
}

android {
    namespace = "dev.ledgerforge.wallet.core.database"
}

dependencies {
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
}
