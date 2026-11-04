package dev.ledgerforge.wallet.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String = findVersion(alias).get().requiredVersion

internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> = findLibrary(alias).get()

/**
 * One task name, `unitTest`, runs host unit tests in every module: `test` for JVM modules,
 * `testDebugUnitTest` for Android modules (the release variant adds nothing but time).
 */
internal fun Project.registerUnitTestTask(testTaskName: String) {
    tasks.register("unitTest") {
        group = "verification"
        description = "Runs this module's host unit tests."
        dependsOn(testTaskName)
    }
}
