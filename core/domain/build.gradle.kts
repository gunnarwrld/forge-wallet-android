plugins {
    alias(libs.plugins.forge.jvm.library)
    alias(libs.plugins.forge.hilt)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(projects.core.money)

    testImplementation(projects.core.testing)
}
