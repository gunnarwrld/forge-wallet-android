plugins {
    alias(libs.plugins.forge.android.library)
    alias(libs.plugins.forge.android.library.compose)
}

android {
    namespace = "dev.ledgerforge.wallet.core.ui"
}

// UI that knows the domain: shared by features, which can't depend on each other.
dependencies {
    api(projects.core.designsystem)
    api(projects.core.domain)
}
