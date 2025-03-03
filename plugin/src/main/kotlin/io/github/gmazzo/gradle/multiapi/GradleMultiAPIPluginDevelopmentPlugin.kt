@file:Suppress("UnstableApiUsage")

package io.github.gmazzo.gradle.multiapi

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.attributes.java.TargetJvmEnvironment.STANDARD_JVM
import org.gradle.api.attributes.java.TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE
import org.gradle.api.attributes.plugin.GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaPlugin.JAR_TASK_NAME
import org.gradle.api.plugins.JavaPlugin.PROCESS_RESOURCES_TASK_NAME
import org.gradle.api.plugins.JavaPlugin.TEST_TASK_NAME
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.internal.buildoption.InternalFlag
import org.gradle.internal.buildoption.InternalOptions
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.creating
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.project
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.the
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.testing.base.TestingExtension
import org.gradle.util.GradleVersion
import javax.inject.Inject

class GradleMultiAPIPluginDevelopmentPlugin @Inject constructor(
    private val options: InternalOptions,
) : Plugin<Project> {

    override fun apply(target: Project): Unit = with(target) {
        apply(plugin = "java-gradle-plugin")

        val extension = (the<GradlePluginDevelopmentExtension>() as ExtensionAware).extensions
            .create<GradleMultiAPIPluginDevelopmentExtension>("apiTargets")

        val java = the<JavaPluginExtension>()
        val sourceSets = the<SourceSetContainer>()
        val testing = the<TestingExtension>()

        val main by sourceSets
        val test by sourceSets

        removeRunningGradleAPIFromMain(main)
        addMinGradleAPIToMain(main, test, extension)
        disableMainPublication(main)

        extension.targetAPIs.all gradleVersion@{
            val featureName = "gradle${this@gradleVersion.version.replace("\\W".toRegex(), "")}"
            val sourceSet = sourceSets.maybeCreate(featureName)
            val testSuite = testing.suites.create<JvmTestSuite>("${featureName}Test")

            java.registerFeature(featureName) {
                usingSourceSet(sourceSet)

                afterEvaluate {
                    capability("${project.group}", project.name, "${project.version}")

                    if (configurations.names.contains(main.sourcesElementsConfigurationName)) {
                        withSourcesJar()
                    }
                    if (configurations.names.contains(main.javadocElementsConfigurationName)) {
                        withJavadocJar()
                    }
                }
            }

            with(configurations) {
                configureEach config@{
                    if (this@config.name.startsWith(sourceSet.name) && (isCanBeResolved || isCanBeConsumed)) {
                        attributes {
                            attribute(GRADLE_PLUGIN_API_VERSION_ATTRIBUTE, objects.named(this@gradleVersion.version))
                        }
                    }
                }

                fun Configuration.standardJVM() = attributes {
                    attribute(TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(STANDARD_JVM))
                }
                getByName(sourceSet.apiElementsConfigurationName).standardJVM()
                getByName(sourceSet.runtimeElementsConfigurationName).standardJVM()

                getByName(sourceSet.apiConfigurationName).extendsFrom(getByName(main.apiConfigurationName))
                getByName(sourceSet.implementationConfigurationName).extendsFrom(getByName(main.implementationConfigurationName))
                getByName(sourceSet.runtimeOnlyConfigurationName).extendsFrom(getByName(main.runtimeOnlyConfigurationName))

                getByName(testSuite.sources.implementationConfigurationName).extendsFrom(getByName(test.implementationConfigurationName))
                getByName(testSuite.sources.runtimeOnlyConfigurationName).extendsFrom(getByName(test.runtimeOnlyConfigurationName))
            }

            dependencies {

                // makes tests to depends on main variant
                testSuite.sources.implementationConfigurationName(provider {
                    dependencies.project(path).also { dep ->
                        dep.capabilities {
                            requireCapability("${dep.group}:${dep.name}-$featureName:${dep.version}")
                        }
                    }
                })

                // configures gradle API dependencies for the variant
                val apiProvider = objects.newInstance<GradleAPIClasspathProvider>(this@gradleVersion)

                sourceSet.apiConfigurationName(apiProvider.api)
                testSuite.sources.implementationConfigurationName(apiProvider.testKit)

                plugins.withId("kotlin") {
                    sourceSet.compileOnlyConfigurationName(apiProvider.kotlinDSL)
                    testSuite.sources.implementationConfigurationName(apiProvider.kotlinDSL)
                }
            }

            fun SourceSet.from(other: SourceSet) {
                this@from.java.srcDir(other.java)
                this@from.resources.srcDir(other.resources)

                plugins.withId("groovy") { this@from.groovy.srcDir(other.groovy) }
                plugins.withId("kotlin") { this@from.kotlin.srcDir(other.kotlin) }

            }
            sourceSet.from(main)
            testSuite.sources.from(test)

            tasks.named<Copy>(sourceSet.processResourcesTaskName) {
                with(tasks.getByName<Copy>(PROCESS_RESOURCES_TASK_NAME))
            }

            tasks.named(TEST_TASK_NAME) {
                dependsOn(testSuite)
            }
        }

        tasks.named(JAR_TASK_NAME) {
            enabled = false
        }
        tasks.named(TEST_TASK_NAME) {
            enabled = false
        }
    }

    private fun Project.removeRunningGradleAPIFromMain(main: SourceSet) = afterEvaluate {
        configurations.getByName(main.apiConfigurationName)
            .dependencies.remove(dependencies.gradleApi())

        the<GradlePluginDevelopmentExtension>().testSourceSets.forEach {
            configurations.getByName(it.implementationConfigurationName)
                .dependencies.remove(dependencies.gradleTestKit())
        }
    }

    private fun Project.addMinGradleAPIToMain(
        main: SourceSet,
        test: SourceSet,
        extension: GradleMultiAPIPluginDevelopmentExtension,
    ) {
        val minimumGradleAPI by configurations.creating
        val minimumTestGradleAPI by configurations.creating { extendsFrom(minimumGradleAPI) }

        configurations.getByName(main.compileClasspathConfigurationName).extendsFrom(minimumGradleAPI)
        configurations.getByName(test.compileClasspathConfigurationName).extendsFrom(minimumTestGradleAPI)

        val minAPIProvider = objects.property<GradleAPIClasspathProvider>()
            .value(provider {
                check(extension.targetAPIs.size >= 2) {
                    "At at least 2 target Gradle API versions must be declared at `gradlePlugins.apiTargets`"
                }

                val minVersion = extension.targetAPIs.min()

                objects.newInstance<GradleAPIClasspathProvider>(minVersion)
            })
            .apply { finalizeValueOnRead() }

        dependencies {
            minimumGradleAPI(minAPIProvider.map { it.api })
            minimumTestGradleAPI(minAPIProvider.map { it.testKit })

            plugins.withId("kotlin") {
                minimumGradleAPI(minAPIProvider.map { it.kotlinDSL })
            }
        }
    }

    private fun Project.disableMainPublication(main: SourceSet) {
        components.named<AdhocComponentWithVariants>("java") {
            listOfNotNull(
                configurations.getByName(main.apiElementsConfigurationName),
                configurations.getByName(main.runtimeElementsConfigurationName),
                configurations.findByName(main.sourcesElementsConfigurationName),
                configurations.findByName(main.javadocElementsConfigurationName),
            ).forEach { withVariantsFromConfiguration(it) { skip() } }
        }
    }

    private val SourceSet.groovy get() = extensions.getByName<SourceDirectorySet>("groovy")
    private val SourceSet.kotlin get() = extensions.getByName<SourceDirectorySet>("kotlin")

}
