plugins {
    alias(libs.plugins.forge.android.library)
    alias(libs.plugins.forge.hilt)
}

android {
    namespace = "dev.ledgerforge.wallet.core.data"
}

dependencies {
    api(projects.core.domain)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime)
    implementation(projects.core.database)
    implementation(projects.core.network)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
}
