plugins {
    alias(libs.plugins.forge.jvm.library)
    alias(libs.plugins.forge.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Plain JVM on purpose: the response rules are tested in milliseconds, with no Android runtime.
dependencies {
    api(libs.okhttp)
    api(libs.retrofit)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)

    testImplementation(projects.core.testing)
}
