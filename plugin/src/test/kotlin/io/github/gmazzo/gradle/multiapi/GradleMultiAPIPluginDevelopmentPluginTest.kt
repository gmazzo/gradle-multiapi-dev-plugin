package io.github.gmazzo.gradle.multiapi

import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.the
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GradleMultiAPIPluginDevelopmentPluginTest {

    private val tempDir = File(System.getenv("TEMP_DIR"), "project")

    @Test
    fun `plugin can be configured`(): Unit = with(ProjectBuilder.builder().build()) {
        apply(plugin = "io.github.gmazzo.gradle.multiapi")

        configure<GradlePluginDevelopmentExtension> {
            val apiTargets = (this as ExtensionAware).the<GradleMultiAPIPluginDevelopmentExtension>()

            apiTargets("7.0", "8.1", "8.13")

            plugins {
                create("myPlugin") {
                    id = "org.test.myPlugin"
                    implementationClass = "org.test.MyPlugin"
                }
            }
        }
    }

    fun gradleVersions() = listOf(
        "8.0",
        GradleVersion.current().version,
    )

    @ParameterizedTest
    @MethodSource("gradleVersions")
    fun `demo project works`(gradleVersion: String) {
        val projectDir = tempDir.resolve(gradleVersion).apply { deleteRecursively(); mkdirs() }

        File("../gradle/libs.versions.toml").copyTo(projectDir.resolve("gradle/libs.versions.toml"))
        File("../demo-plugin").copyRecursively(projectDir)

        projectDir.resolve("settings.gradle").writeText("""
            plugins {
                id("jacoco-testkit-coverage")
            }
            
            dependencyResolutionManagement {
                repositories {
                    mavenCentral()
                }
            }
        """.trimIndent())

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withGradleVersion(gradleVersion)
            .withPluginClasspath()
            .withArguments("build", "publishToMavenLocal", "-s")
            .forwardOutput()
            .build()
    }

}
