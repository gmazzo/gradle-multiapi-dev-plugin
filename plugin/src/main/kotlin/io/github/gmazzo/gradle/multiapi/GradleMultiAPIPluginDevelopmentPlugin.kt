@file:Suppress("UnstableApiUsage")

package io.github.gmazzo.gradle.multiapi

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationPublications
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.attributes.Usage.JAVA_API
import org.gradle.api.attributes.Usage.JAVA_RUNTIME
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE
import org.gradle.api.attributes.java.TargetJvmEnvironment.STANDARD_JVM
import org.gradle.api.attributes.java.TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE
import org.gradle.api.attributes.plugin.GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE
import org.gradle.api.internal.artifacts.configurations.DefaultConfigurationPublications
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaPlugin.TEST_TASK_NAME
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.plugin.devel.tasks.PluginUnderTestMetadata
import org.gradle.util.GradleVersion

public class GradleMultiAPIPluginDevelopmentPlugin : Plugin<Project> {

    internal companion object {
        val MIN_GRADLE_VERSION: GradleVersion = GradleVersion.version("7.0")
    }

    override fun apply(target: Project): Unit = with(target) {
        check(GradleVersion.current() >= MIN_GRADLE_VERSION) {
            "This plugin requires Gradle $MIN_GRADLE_VERSION or newer"
        }

        apply(plugin = "java-gradle-plugin")

        val extension = createExtension()

        val sourceSets = the<SourceSetContainer>()

        val main by sourceSets
        val test by sourceSets

        val java = the<JavaPluginExtension>()
        java.withSourcesJar()
        java.withJavadocJar()

        removeRunningGradleAPIFromMain(main, test)
        addMinGradleAPIToMain(main, test, extension)
        configureMainAsCommon(main)

        val commonFeature = (dependencies.create(project) as ProjectDependency).apply {
            capabilities { requireCapability(provider { "${project.group}:${project.name}-common" }) }
        }

        val testClasspathTask = tasks.named<PluginUnderTestMetadata>("pluginUnderTestMetadata") {
            delete(outputDirectory)
            enabled = false // we'll produce one per variant
        }

        afterEvaluate {
            // if none are declared, adds the default one to allow the project to sync
            if (extension.targetAPIs.isEmpty()) {
                extension.targetAPIs.maybeCreate(GradleVersion.current().version)
            }
        }

        extension.targetAPIs.all {

            java.registerFeature(featureName) {
                usingSourceSet(sourceSet)

                withSourcesJar()
                withJavadocJar()
            }

            configureVariantWithDefaultCapability(sourceSet)

            with(configurations) {
                configureEach config@{
                    if (this@config.name.startsWith(sourceSet.name) && (isCanBeResolved || isCanBeConsumed)) {
                        attributes {
                            attribute(GRADLE_PLUGIN_API_VERSION_ATTRIBUTE, objects.named(gradleVersion.version))
                        }
                    }
                }

                fun Configuration.standardJVM(usage: String) = attributes {
                    attribute(USAGE_ATTRIBUTE, objects.named(usage))
                    attribute(TARGET_JVM_ENVIRONMENT_ATTRIBUTE, objects.named(STANDARD_JVM))
                }
                getByName(sourceSet.apiElementsConfigurationName).standardJVM(JAVA_API)
                getByName(sourceSet.runtimeElementsConfigurationName).standardJVM(JAVA_RUNTIME)

                getByName(sourceSet.apiConfigurationName).extendsFrom(getByName(main.apiConfigurationName))
                getByName(sourceSet.implementationConfigurationName).extendsFrom(getByName(main.implementationConfigurationName))
                getByName(sourceSet.runtimeOnlyConfigurationName).extendsFrom(getByName(main.runtimeOnlyConfigurationName))
                getByName(sourceSet.annotationProcessorConfigurationName).extendsFrom(getByName(main.annotationProcessorConfigurationName))

                getByName(testSuite.sources.implementationConfigurationName).extendsFrom(getByName(sourceSet.implementationConfigurationName))
                getByName(testSuite.sources.implementationConfigurationName).extendsFrom(getByName(test.implementationConfigurationName))
                getByName(testSuite.sources.runtimeOnlyConfigurationName).extendsFrom(getByName(sourceSet.runtimeOnlyConfigurationName))
                getByName(testSuite.sources.runtimeOnlyConfigurationName).extendsFrom(getByName(test.runtimeOnlyConfigurationName))
                getByName(testSuite.sources.annotationProcessorConfigurationName).extendsFrom(getByName(test.annotationProcessorConfigurationName))
            }

            val variantTestClasspathTask =
                tasks.register<PluginUnderTestMetadata>("pluginUnderTestMetadata${featureName.replaceFirstChar { it.uppercase() }}") {
                    outputDirectory.set(layout.buildDirectory.dir(name))
                    pluginClasspath
                        .from(testClasspathTask.map { it.pluginClasspath })
                        .from(sourceSet.output)
                }

            dependencies {
                sourceSet.compileOnlyConfigurationName(gradleApi)
                sourceSet.apiConfigurationName(commonFeature)

                testSuite.sources.implementationConfigurationName(gradleTestKit)
                testSuite.sources.implementationConfigurationName(project)
                testSuite.sources.implementationConfigurationName(test.output)
                testSuite.sources.runtimeOnlyConfigurationName(files(variantTestClasspathTask))

                plugins.withId("kotlin") {
                    sourceSet.compileOnlyConfigurationName(gradleKotlinDsl)
                    testSuite.sources.implementationConfigurationName(gradleKotlinDsl)
                }
            }

            testSuite.targets.all {
                testTask.configure {
                    testClassesDirs += test.output.classesDirs
                }
            }

            tasks.named(TEST_TASK_NAME) {
                dependsOn(testSuite)
            }
        }

        tasks.named(TEST_TASK_NAME) {
            enabled = false
        }

        plugins.withId("maven-publish") {
            // we already know this multi variant approach is not Maven-compatible
            the<PublishingExtension>().publications.withType<MavenPublication>().configureEach {
                suppressAllPomMetadataWarnings()
            }
        }
    }

    private fun Project.createExtension() =
        (the<GradlePluginDevelopmentExtension>() as ExtensionAware).extensions.create(
            GradleMultiAPIPluginDevelopmentExtension::class,
            "apiTargets",
            GradleMultiAPIPluginDevelopmentExtensionImpl::class,
        )

    private fun Project.removeRunningGradleAPIFromMain(main: SourceSet, test: SourceSet) = afterEvaluate {
        configurations.getByName(main.apiConfigurationName)
            .dependencies.remove(dependencies.gradleApi())
        configurations.getByName(test.implementationConfigurationName)
            .dependencies.remove(dependencies.gradleTestKit())
    }

    private fun Project.addMinGradleAPIToMain(
        main: SourceSet,
        test: SourceSet,
        extension: GradleMultiAPIPluginDevelopmentExtension,
    ) {
        val gradleApi = configurations.create("${main.compileOnlyConfigurationName}GradleApi")
        val gradleTestApi = configurations.create("${test.compileOnlyConfigurationName}GradleApi") {
            extendsFrom(gradleApi)
        }

        configurations.getByName(main.compileClasspathConfigurationName).extendsFrom(gradleApi)
        configurations.getByName(test.compileClasspathConfigurationName).extendsFrom(gradleTestApi)

        plugins.withId("java-test-fixtures") {
            val testFixtures by the<SourceSetContainer>()

            configurations.getByName(testFixtures.compileOnlyConfigurationName).extendsFrom(gradleTestApi)
        }

        dependencies {
            gradleApi(extension.minGradleAPI.map { it.gradleApi })
            gradleTestApi(extension.minGradleAPI.map { it.gradleTestKit })

            plugins.withId("kotlin") {
                gradleApi(extension.minGradleAPI.map { it.gradleKotlinDsl })
            }
        }
    }

    private fun Project.configureMainAsCommon(main: SourceSet) = with(configurations) {
        sequenceOf(
            main.apiElementsConfigurationName,
            main.runtimeElementsConfigurationName,
            main.sourcesElementsConfigurationName,
            main.javadocElementsConfigurationName,
        ).forEach { getByName(it).outgoing.capability(provider { "$group:$name-common:$version" }) }
    }

    private fun Project.configureVariantWithDefaultCapability(sourceSet: SourceSet) = with(configurations) {
        sequenceOf(
            sourceSet.apiElementsConfigurationName,
            sourceSet.runtimeElementsConfigurationName,
            sourceSet.sourcesElementsConfigurationName,
            sourceSet.javadocElementsConfigurationName,
        ).forEach { getByName(it).outgoing.clearCapabilities() }
    }

    private fun ConfigurationPublications.clearCapabilities() {
        DefaultConfigurationPublications::class.java.getDeclaredField("capabilities")
            .apply { isAccessible = true }
            .set(this, null)
    }

}
