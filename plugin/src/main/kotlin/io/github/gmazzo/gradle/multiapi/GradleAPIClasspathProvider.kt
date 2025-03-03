package io.github.gmazzo.gradle.multiapi

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.collections.MinimalFileSet
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import java.io.File
import javax.inject.Inject

abstract class GradleAPIClasspathProvider @Inject constructor(
    private val project: Project,
    private val fileCollectionFactory: FileCollectionFactory,
    private val temporaryFileProvider: TemporaryFileProvider,
    private val gradleVersion: GradleVersion,
) {

    private val workDirs by lazy { extractAPIs() }

    val api = createCollection("Gradle ${gradleVersion.version} API files") { workDirs.first }

    val testKit = createCollection("Gradle ${gradleVersion.version} TestKit files") { workDirs.second }

    val kotlinDSL = createCollection("Gradle ${gradleVersion.version} Kotlin DSL files") { workDirs.third }

    private fun extractAPIs(): Triple<File, File, File> {
        val workDir = temporaryFileProvider.newTemporaryDirectory("gradle-api-classpath")
        val apiFile = workDir.resolve("gradle-api-${gradleVersion.version}.txt")
        val testKitFile = workDir.resolve("gradle-test-kit-${gradleVersion.version}.txt")
        val kotlinDslFile = workDir.resolve("gradle-kotlin-dsl-${gradleVersion.version}.txt")
        val result = Triple(apiFile, testKitFile, kotlinDslFile)

        if (apiFile.isValidClasspath && testKitFile.isValidClasspath && kotlinDslFile.isValidClasspath) {
            return result
        }

        project.logger.lifecycle("Extracting Gradle ${gradleVersion.version} API")

        val projectDir = workDir.resolve("work${gradleVersion.version}")
        projectDir.deleteRecursively()
        projectDir.mkdirs()

        projectDir.resolve("settings.gradle.kts").createNewFile()
        projectDir.resolve("build.gradle.kts").writeText(
            """
            file("$apiFile").writeText(configurations.detachedConfiguration(dependencies.gradleApi()).files.joinToString("\n"))
            file("$testKitFile").writeText(configurations.detachedConfiguration(dependencies.gradleTestKit()).files.joinToString("\n"))
            file("$kotlinDslFile").writeText(configurations.detachedConfiguration(gradleKotlinDsl()).files.joinToString("\n"))
            """.trimIndent()
        )

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withTestKitDir(project.gradle.gradleUserHomeDir)
            .withGradleVersion(gradleVersion.version)
            .withArguments("-m")
            .build()

        projectDir.deleteRecursively()

        return result
    }

    private fun createCollection(displayName: String, classpathFile: () -> File): FileCollection =
        fileCollectionFactory.create(GradleClasspath(displayName, classpathFile))

    private val File.isValidClasspath
        get() = isFile && useLines { it.map(::File).all(File::isFile) }

    class GradleClasspath(
        private val displayName: String,
        private val classpathFile: () -> File,
    ) : MinimalFileSet {

        private val resolvedFiles by lazy {
            classpathFile().useLines { it.map(::File).toSet() }
        }

        override fun getDisplayName() = displayName

        override fun getFiles() = resolvedFiles

    }

}