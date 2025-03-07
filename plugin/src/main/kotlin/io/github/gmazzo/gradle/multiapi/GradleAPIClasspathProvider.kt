@file:Suppress("UnstableApiUsage")

package io.github.gmazzo.gradle.multiapi

import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.collections.MinimalFileSet
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.invocation.Gradle
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.maybeCreate
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.the
import org.gradle.testing.base.TestingExtension
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import java.io.File
import javax.inject.Inject

internal abstract class GradleAPIClasspathProvider @Inject constructor(
    name: String,
    objects: ObjectFactory,
    gradle: Gradle,
    project: Project,
    sourceSets: SourceSetContainer,
    private val fileCollectionFactory: FileCollectionFactory,
    private val temporaryFileProvider: TemporaryFileProvider,
) : GradleAPITarget, Named by objects.named(name) {

    private val logger = project.logger

    private val forceRebuild = gradle.startParameter.isRerunTasks

    private val gradleUserHomeDir = gradle.gradleUserHomeDir

    private val workDirs by lazy { extractAPIs() }

    override val featureName =
        "gradle${name.replace("\\W".toRegex(), "")}"

    override val gradleVersion: GradleVersion =
        GradleVersion.version(name)

    override val gradleApi =
        createCollection("Gradle ${gradleVersion.version} API files") { workDirs.first }

    override val gradleTestKit =
        createCollection("Gradle ${gradleVersion.version} TestKit files") { workDirs.second }

    override val gradleKotlinDsl =
        createCollection("Gradle ${gradleVersion.version} Kotlin DSL files") { workDirs.third }

    override val sourceSet: SourceSet =
        sourceSets.maybeCreate(featureName)

    override val testSuite =
        project.the<TestingExtension>().suites.maybeCreate<JvmTestSuite>("${featureName}Test")

    private fun extractAPIs(): Triple<File, File, File> {
        val workDir = temporaryFileProvider.newTemporaryDirectory("gradle-api-classpath")
        val apiFile = workDir.resolve("gradle-api-${gradleVersion.version}.txt")
        val testKitFile = workDir.resolve("gradle-test-kit-${gradleVersion.version}.txt")
        val kotlinDslFile = workDir.resolve("gradle-kotlin-dsl-${gradleVersion.version}.txt")
        val result = Triple(apiFile, testKitFile, kotlinDslFile)

        if (!forceRebuild &&
            apiFile.isValidClasspath &&
            testKitFile.isValidClasspath &&
            kotlinDslFile.isValidClasspath
        ) {
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
            .withTestKitDir(gradleUserHomeDir)
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
