@file:Suppress("UnstableApiUsage")

package io.github.gmazzo.gradle.multiapi

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Usage.JAVA_API
import org.gradle.api.attributes.Usage.JAVA_RUNTIME
import org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE
import org.gradle.api.attributes.java.TargetJvmEnvironment.STANDARD_JVM
import org.gradle.api.attributes.java.TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE
import org.gradle.api.attributes.plugin.GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE
import org.gradle.api.capabilities.Capability
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.FeatureSpec
import org.gradle.api.plugins.JavaPlugin.TEST_TASK_NAME
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.internal.DefaultJavaFeatureSpec
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.internal.component.external.model.ProjectDerivedCapability
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.the
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.plugin.devel.tasks.PluginUnderTestMetadata
import org.gradle.testing.base.TestingExtension
import org.gradle.util.GradleVersion

class GradleMultiAPIPluginDevelopmentPlugin : Plugin<Project> {

    private val minGradleVersion = GradleVersion.version("7.0")

    override fun apply(target: Project): Unit = with(target) {
        check(GradleVersion.current() >= minGradleVersion) {
            "This plugin requires Gradle $minGradleVersion or newer"
        }

        apply(plugin = "java-gradle-plugin")

        val extension = createExtension()

        val java = the<JavaPluginExtension>()
        val sourceSets = the<SourceSetContainer>()
        val testing = the<TestingExtension>()

        val main by sourceSets
        val test by sourceSets

        removeRunningGradleAPIFromMain(main, test)
        addMinGradleAPIToMain(main, test, extension)
        disableMainPublication(main)

        val testClasspathTask = tasks.named<PluginUnderTestMetadata>("pluginUnderTestMetadata") {
            delete(outputDirectory)
            enabled = false // we'll produce one per variant
        }

        extension.targetAPIs.all gradleVersion@{
            val featureName = "gradle${this@gradleVersion.version.replace("\\W".toRegex(), "")}"
            val sourceSet = sourceSets.maybeCreate(featureName)
            val testSuite = testing.suites.create<JvmTestSuite>("${featureName}Test")

            java.registerFeature(featureName) {
                usingSourceSet(sourceSet)

                projectDerivedCapability(this)

                if (configurations.names.contains(main.sourcesElementsConfigurationName)) {
                    withSourcesJar()
                }
                if (configurations.names.contains(main.javadocElementsConfigurationName)) {
                    withJavadocJar()
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

            dependencies {
                val apiProvider = extension.providers.map { it[this@gradleVersion] }

                sourceSet.apiConfigurationName(apiProvider.map { it.api })
                sourceSet.compileOnlyConfigurationName(main.output)

                testSuite.sources.implementationConfigurationName(apiProvider.map { it.testKit })
                testSuite.sources.implementationConfigurationName(sourceSet.output)
                testSuite.sources.implementationConfigurationName(test.output)

                plugins.withId("kotlin") {
                    sourceSet.compileOnlyConfigurationName(apiProvider.map { it.kotlinDSL })
                    testSuite.sources.implementationConfigurationName(apiProvider.map { it.kotlinDSL })
                }
            }

            // makes main/test classes to be included in the variant source set
            sourceSet.output.classesDirs(main.output.classesDirs)
            testSuite.sources.output.classesDirs(test.output.classesDirs)

            tasks.named<Copy>(sourceSet.processResourcesTaskName) {
                with(tasks.getByName<Copy>(main.processResourcesTaskName))
            }

            tasks.named<Copy>(testSuite.sources.processResourcesTaskName) {
                with(tasks.getByName<Copy>(test.processResourcesTaskName))
            }

            val variantTestClasspathTask = tasks.register<PluginUnderTestMetadata>("pluginUnderTestMetadata${featureName.replaceFirstChar { it.uppercase() }}") {
                outputDirectory.set(layout.buildDirectory.dir(name))
                pluginClasspath
                    .from(testClasspathTask.map { it.pluginClasspath })
                    .from(sourceSet.output)
            }
            dependencies.add(testSuite.sources.runtimeOnlyConfigurationName, files(variantTestClasspathTask))

            tasks.named(TEST_TASK_NAME) {
                dependsOn(testSuite)
            }
        }

        tasks.named(TEST_TASK_NAME) {
            enabled = false
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Project.projectDerivedCapability(spec: FeatureSpec, name: String? = null) {
        (DefaultJavaFeatureSpec::class.java.getDeclaredField("capabilities")
            .apply { isAccessible = true }
            .get(spec) as MutableSet<Capability>)
            .add(ProjectDerivedCapability(this, name))
    }

    private fun Project.createExtension() =
        (the<GradlePluginDevelopmentExtension>() as ExtensionAware).extensions.create(
            GradleMultiAPIPluginDevelopmentExtension::class,
            "apiTargets",
            GradleMultiAPIPluginDevelopmentExtensionImpl::class,
            minGradleVersion,
        ) as GradleMultiAPIPluginDevelopmentExtensionImpl

    private fun Project.removeRunningGradleAPIFromMain(main: SourceSet, test: SourceSet) = afterEvaluate {
        configurations.getByName(main.apiConfigurationName)
            .dependencies.remove(dependencies.gradleApi())
        configurations.getByName(test.implementationConfigurationName)
            .dependencies.remove(dependencies.gradleTestKit())
    }

    private fun Project.addMinGradleAPIToMain(
        main: SourceSet,
        test: SourceSet,
        extension: GradleMultiAPIPluginDevelopmentExtensionImpl,
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
            gradleApi(extension.minGradleAPI.map { it.api })
            gradleTestApi(extension.minGradleAPI.map { it.testKit })

            plugins.withId("kotlin") {
                gradleApi(extension.minGradleAPI.map { it.kotlinDSL })
            }
        }
    }

    private fun Project.disableMainPublication(main: SourceSet) = with(configurations) {
        val apiElements = getByName(main.apiElementsConfigurationName)
        val runtimeElements = getByName(main.runtimeElementsConfigurationName)

        apiElements.isCanBeConsumed = false
        runtimeElements.isCanBeConsumed = false

        components.named<AdhocComponentWithVariants>("java") {
            listOfNotNull(
                apiElements,
                runtimeElements,
                findByName(main.sourcesElementsConfigurationName),
                findByName(main.javadocElementsConfigurationName),
            ).forEach { withVariantsFromConfiguration(it) { skip() } }
        }

        plugins.withId("maven-publish") {
            afterEvaluate {
                // we already know this multi variant approach is not Maven-compatible
                the<PublishingExtension>().publications.named<MavenPublication>("pluginMaven") {
                    suppressAllPomMetadataWarnings()
                }
            }
        }
    }

    private fun SourceSetOutput.classesDirs(vararg from: Any) =
        (classesDirs as ConfigurableFileCollection).from(*from)

}
