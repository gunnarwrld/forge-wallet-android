plugins {
    alias(libs.plugins.forge.jvm.library)
}

// Plain JVM on purpose: Android test classpaths can use it, and it never depends on the app.
dependencies {
    api(libs.junit4)
    api(libs.kotlinx.coroutines.test)
    api(libs.okhttp.mockwebserver)
    api(projects.core.domain)
    implementation(libs.kotlinx.serialization.json)
}
