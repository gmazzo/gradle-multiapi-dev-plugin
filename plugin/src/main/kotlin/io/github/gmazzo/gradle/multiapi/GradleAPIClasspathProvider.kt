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

internal abstract class GradleAPIClasspathProvider @Inject constructor(
    project: Project,
    private val fileCollectionFactory: FileCollectionFactory,
    private val temporaryFileProvider: TemporaryFileProvider,
    private val gradleVersion: GradleVersion,
) {

    private val gradle = project.gradle

    private val logger = project.logger

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

        if (!gradle.startParameter.isRerunTasks &&
            apiFile.isValidClasspath &&
            testKitFile.isValidClasspath &&
            kotlinDslFile.isValidClasspath) {
            return result
        }

        logger.lifecycle("Extracting Gradle ${gradleVersion.version} API")

        val projectDir = workDir.resolve("work${gradleVersion.version}")
        projectDir.deleteRecursively()
        projectDir.mkdirs()

        projectDir.resolve("settings.gradle.kts").createNewFile()
        projectDir.resolve("build.gradle.kts").writeText(
            """
            fun File.writeClasspath(source: Dependency) =
                writeText(configurations.detachedConfiguration(source).files.joinToString("\n"))
                
            file("$apiFile").writeClasspath(dependencies.gradleApi())
            file("$testKitFile").writeClasspath(dependencies.gradleTestKit())
            file("$kotlinDslFile").writeClasspath(gradleKotlinDsl())
            """.trimIndent()
        )

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withTestKitDir(gradle.gradleUserHomeDir)
            .withGradleVersion(gradleVersion.version)
            .withArguments("-m")
            .forwardStdOutput(projectDir.resolve("stdout.txt").writer())
            .forwardStdError(projectDir.resolve("stderr.txt").writer())
            .build()

        projectDir.deleteRecursively()

        return result
    }

    private fun createCollection(displayName: String, classpathFile: () -> File): FileCollection =
        fileCollectionFactory.create(GradleClasspath(displayName, classpathFile))

    private val File.isValidClasspath
        get() = isFile && useLines { lines -> lines.all { File(it).isFile } }

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