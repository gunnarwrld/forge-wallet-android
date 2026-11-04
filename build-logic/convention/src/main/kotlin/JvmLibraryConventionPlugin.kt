import dev.ledgerforge.wallet.buildlogic.configureKotlinJvm
import dev.ledgerforge.wallet.buildlogic.configureQuality
import dev.ledgerforge.wallet.buildlogic.libs
import dev.ledgerforge.wallet.buildlogic.library
import dev.ledgerforge.wallet.buildlogic.registerUnitTestTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Pure Kotlin modules (money, domain): no Android, fast tests, coverage measured with Kover. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("org.jetbrains.kotlinx.kover")
            configureKotlinJvm()
            configureQuality()
            registerUnitTestTask("test")

            dependencies {
                add("testImplementation", libs.library("kotlin-test-junit"))
                add("testImplementation", libs.library("junit4"))
            }
        }
    }
}
