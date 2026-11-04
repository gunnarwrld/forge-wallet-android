import dev.ledgerforge.wallet.buildlogic.libs
import dev.ledgerforge.wallet.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Hilt through KSP. Android modules get the Gradle plugin; JVM modules only need hilt-core. */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            dependencies {
                add("ksp", libs.library("hilt-compiler"))
            }

            pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
                dependencies {
                    add("implementation", libs.library("hilt-core"))
                }
            }
            listOf("com.android.application", "com.android.library").forEach { androidPlugin ->
                pluginManager.withPlugin(androidPlugin) {
                    pluginManager.apply("com.google.dagger.hilt.android")
                    dependencies {
                        add("implementation", libs.library("hilt-android"))
                    }
                }
            }
        }
    }
}
