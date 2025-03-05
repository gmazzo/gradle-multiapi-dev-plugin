package io.github.gmazzo.gradle.multiapi

import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.the
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
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

    @Test
    fun `demo project produces expected jars`() {
        val projectDir = tempDir.apply { deleteRecursively(); mkdirs() }

        File("../gradle/libs.versions.toml").copyTo(projectDir.resolve("gradle/libs.versions.toml"))
        File("../demo-plugin").copyRecursively(projectDir)

        projectDir.resolve("settings.gradle").writeText(
            """
            plugins {
                id("jacoco-testkit-coverage")
            }
            
            dependencyResolutionManagement {
                repositories {
                    mavenCentral()
                }
            }
        """.trimIndent()
        )

        val jarsContentDir = projectDir.resolve("build/jars-content")

        projectDir.resolve("build.gradle").appendText(
            """
            def jars = copySpec()
            tasks.withType(Jar) { task ->
                jars.from(zipTree(task.archiveFile)) {
                    into(task.name) 
                }
            }
            tasks.register("collectJarsContent", Copy) {
                with(jars)
                exclude("**/META-INF/MANIFEST.MF", "**/META-INF/*.kotlin_module")
                into("$jarsContentDir")
            }
        """.trimIndent()
        )

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("build", "publishToMavenLocal", "collectJarsContent", "-s")
            .forwardOutput()
            .build()

        val jarsContent = jarsContentDir.walkTopDown()
            .filter { it.isFile }
            .map { it.toRelativeString(jarsContentDir) }
            .toSortedSet()

        assertEquals(
            sortedSetOf(
                "gradle70Jar/org/test/Gradle70Helper.class",
                "gradle70Jar/org/test/MyPlugin.class",
                "gradle70Jar/org/test/MyPluginBase.class",
                "gradle70Jar/META-INF/gradle-plugins/org.test.myPlugin.properties",
                "gradle70Jar/gradle70res.txt",

                "gradle81Jar/org/test/Gradle81Helper.class",
                "gradle81Jar/org/test/MyPlugin\$DummyAction.class",
                "gradle81Jar/org/test/MyPlugin\$doSpecificStuff$\$inlined\$the$1.class",
                "gradle81Jar/org/test/MyPlugin.class",
                "gradle81Jar/org/test/MyPluginBase.class",
                "gradle81Jar/META-INF/gradle-plugins/org.test.myPlugin.properties",
                "gradle81Jar/gradle81res.txt",

                "gradle813Jar/org/test/Gradle813Helper.class",
                "gradle813Jar/org/test/MyPlugin\$doSpecificStuff$\$inlined\$the$1.class",
                "gradle813Jar/org/test/MyPlugin\$doSpecificStuff$\$inlined\$the$2.class",
                "gradle813Jar/org/test/MyPlugin.class",
                "gradle813Jar/org/test/MyPluginBase.class",
                "gradle813Jar/META-INF/gradle-plugins/org.test.myPlugin.properties",
                "gradle813Jar/gradle813res.txt",

                "jar/META-INF/gradle-plugins/org.test.myPlugin.properties",
                "jar/org/test/MyPluginBase.class",
            ),
            jarsContent
        )
    }

}
