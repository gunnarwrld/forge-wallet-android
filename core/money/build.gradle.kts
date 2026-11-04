plugins {
    alias(libs.plugins.forge.jvm.library)
}

dependencies {
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotlinx.coroutines.test)
}
