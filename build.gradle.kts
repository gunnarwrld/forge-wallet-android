// Third-party plugins are declared here once, without applying them, so every module and
// every build-logic convention plugin resolves the same versions from one classloader.
// AGP 9 compiles Kotlin itself; declaring kotlin-jvm puts KGP 2.4 on the classpath for it.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.room) apply false
}
