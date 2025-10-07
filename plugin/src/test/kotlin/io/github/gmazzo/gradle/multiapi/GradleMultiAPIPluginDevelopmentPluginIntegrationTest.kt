package io.github.gmazzo.gradle.multiapi

import io.github.gmazzo.gradle.multiapi.GradleMultiAPIPluginDevelopmentPlugin.Companion.MIN_GRADLE_VERSION
import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GradleMultiAPIPluginDevelopmentPluginIntegrationTest {

    private val tempDir = File(System.getenv("TEMP_DIR"))

    private val projectDir by lazy {
        tempDir.resolve("project").apply {
            deleteRecursively()
            mkdirs()

            File("../gradle/libs.versions.toml").copyTo(resolve("gradle/libs.versions.toml"))
            File("../demo-plugin").copyRecursively(this)

            resolve("settings.gradle.kts").writeText(
                """
                dependencyResolutionManagement {
                    repositories {
                        mavenCentral()
                    }
                }

                rootProject.name = "myPlugin"

                """.trimIndent()
            )
        }
    }

    @Test
    fun `demo project produces expected jars`() {
        val jarsContentDir = projectDir.resolve("build/jars-content")

        projectDir.resolve("settings.gradle.kts").apply {
            writeText(
                """
                plugins {
                    id("jacoco-testkit-coverage")
                }

                """.trimIndent() + readText()
            )
        }

        projectDir.resolve("build.gradle.kts").appendText(
            """
            val jars = copySpec()
            tasks.withType<Jar> task@{
                jars.from(zipTree(this@task.archiveFile)) {
                    into(this@task.name)
                }
            }
            tasks.register<Copy>("collectJarsContent") {
                with(jars)
                exclude("**/META-INF/MANIFEST.MF", "**/META-INF/*.kotlin_module")
                into("$jarsContentDir")
            }

            """.trimIndent()
        )

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("collectJarsContent", "-s")
            .forwardOutput()
            .build()

        val jarsContent = jarsContentDir.walkTopDown()
            .filter { it.isFile }
            .map { it.toRelativeString(jarsContentDir) }
            .toSortedSet()

        assertEquals(
            sortedSetOf(
                "gradle70Jar/gradle70res.txt",
                "gradle70Jar/org/test/Gradle70Helper.class",
                "gradle70Jar/org/test/MyPluginServiceImpl.class",

                "gradle70SourcesJar/gradle70res.txt",
                "gradle70SourcesJar/org/test/Gradle70Helper.kt",
                "gradle70SourcesJar/org/test/MyPluginServiceImpl.kt",

                "gradle813Jar/gradle813res.txt",
                "gradle813Jar/org/test/Gradle813Helper.class",
                "gradle813Jar/org/test/MyPluginServiceImpl\$onBuildFinished$\$inlined\$the$1.class",
                "gradle813Jar/org/test/MyPluginServiceImpl\$onBuildFinished$\$inlined\$the$2.class",
                "gradle813Jar/org/test/MyPluginServiceImpl.class",

                "gradle813SourcesJar/gradle813res.txt",
                "gradle813SourcesJar/org/test/Gradle813Helper.kt",
                "gradle813SourcesJar/org/test/MyPluginServiceImpl.kt",

                "gradle81Jar/gradle81res.txt",
                "gradle81Jar/org/test/Gradle81Helper.class",
                "gradle81Jar/org/test/MyPluginServiceImpl\$DummyAction.class",
                "gradle81Jar/org/test/MyPluginServiceImpl\$onBuildFinished$\$inlined\$the$1.class",
                "gradle81Jar/org/test/MyPluginServiceImpl.class",

                "gradle81SourcesJar/gradle81res.txt",
                "gradle81SourcesJar/org/test/Gradle81Helper.kt",
                "gradle81SourcesJar/org/test/MyPluginServiceImpl.kt",

                "jar/META-INF/gradle-plugins/org.test.myPlugin.properties",
                "jar/META-INF/services/org.test.MyPluginService",
                "jar/org/test/MyPlugin.class",
                "jar/org/test/MyPluginService.class",

                "sourcesJar/META-INF/services/org.test.MyPluginService",
                "sourcesJar/org/test/MyPlugin.kt",
                "sourcesJar/org/test/MyPluginService.kt",
            ),
            jarsContent
        )
    }

    fun gradleVersions() = projectDir
        .resolve("src")
        .listFiles { it.isDirectory }
        .mapNotNull { "gradle(\\d)(\\d+)".toRegex().matchEntire(it.name)?.groupValues }
        .map { (_, major, minor) -> "$major.$minor" }
        .union(listOf(MIN_GRADLE_VERSION.version, GradleVersion.current().version))

    @ParameterizedTest
    @MethodSource("gradleVersions")
    fun `demo project can be consumed by multiple Gradle`(version: String) {
        val localRepoDir = publishToLocalRepo()
        val projectDir = projectDir.resolve("consumer-project").apply { deleteRecursively(); mkdirs() }

        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    maven { url = uri("$localRepoDir") }
                }
            }

            rootProject.name = "consumer-project"
            """.trimIndent()
        )

        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("java-gradle-plugin")
                id("org.test.myPlugin") version "+"
            }
            """.trimIndent()
        )

        GradleRunner.create()
            .withGradleVersion(version)
            .withProjectDir(projectDir)
            .withArguments("build", "-s")
            .forwardOutput()
            .build()
    }

    private fun publishToLocalRepo() = tempDir.resolve("local-repo").also { localRepoDir ->
        localRepoDir.deleteRecursively()

        projectDir.resolve("build.gradle.kts").appendText(
            """
            apply(plugin = "maven-publish")

            publishing.repositories {
                maven {
                    name = "local"
                    url = uri(file("$localRepoDir"))
                }
            }

            """.trimIndent()
        )

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("publishAllPublicationsToLocalRepository", "-s")
            .forwardOutput()
            .build()
    }

}
