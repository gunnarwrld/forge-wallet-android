import com.android.build.api.dsl.LibraryExtension
import dev.ledgerforge.wallet.buildlogic.configureKotlinAndroid
import dev.ledgerforge.wallet.buildlogic.configureQuality
import dev.ledgerforge.wallet.buildlogic.libs
import dev.ledgerforge.wallet.buildlogic.library
import dev.ledgerforge.wallet.buildlogic.registerUnitTestTask
import dev.ledgerforge.wallet.buildlogic.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                testOptions.targetSdk = libs.version("targetSdk").toInt()
                lint.targetSdk = libs.version("targetSdk").toInt()
                testOptions.animationsDisabled = true
            }
            configureQuality()
            registerUnitTestTask("testDebugUnitTest")

            dependencies {
                add("testImplementation", libs.library("kotlin-test-junit"))
                add("testImplementation", libs.library("junit4"))
                add("testImplementation", project(":core:testing"))
            }
        }
    }
}
