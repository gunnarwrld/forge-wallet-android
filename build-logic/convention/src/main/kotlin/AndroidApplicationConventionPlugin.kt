import com.android.build.api.dsl.ApplicationExtension
import dev.ledgerforge.wallet.buildlogic.configureKotlinAndroid
import dev.ledgerforge.wallet.buildlogic.configureQuality
import dev.ledgerforge.wallet.buildlogic.libs
import dev.ledgerforge.wallet.buildlogic.registerUnitTestTask
import dev.ledgerforge.wallet.buildlogic.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = libs.version("targetSdk").toInt()
                defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                testOptions.animationsDisabled = true
            }
            configureQuality()
            registerUnitTestTask("testDebugUnitTest")
        }
    }
}
