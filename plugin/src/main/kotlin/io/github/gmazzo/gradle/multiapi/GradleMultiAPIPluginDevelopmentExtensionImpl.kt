package io.github.gmazzo.gradle.multiapi

import io.github.gmazzo.gradle.multiapi.GradleMultiAPIPluginDevelopmentPlugin.Companion.MIN_GRADLE_VERSION
import javax.inject.Inject
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.setProperty
import org.gradle.util.GradleVersion

internal abstract class GradleMultiAPIPluginDevelopmentExtensionImpl @Inject constructor(
    project: Project,
    private val objects: ObjectFactory,
) : GradleMultiAPIPluginDevelopmentExtension {

    private val maxGradleVersion = GradleVersion.current()

    private val logger = project.logger

    override val targetAPIs: NamedDomainObjectContainer<GradleAPITarget> =
        objects.domainObjectContainer(GradleAPITarget::class.java, ::createProvider)

    abstract override val minGradleAPI: Property<GradleAPITarget>

    init {
        val allLockable = objects.setProperty<GradleAPITarget>()

        targetAPIs.all {
            check(gradleVersion >= MIN_GRADLE_VERSION && gradleVersion <= maxGradleVersion) {
                "Target Gradle API $gradleVersion must be in between $MIN_GRADLE_VERSION and $maxGradleVersion"
            }
            allLockable.add(this)
        }

        allLockable.finalizeValueOnRead()
        minGradleAPI.value(allLockable.map(::computeMin))
        minGradleAPI.finalizeValueOnRead()
    }

    private fun createProvider(version: String) =
        objects.newInstance<GradleAPIClasspathProvider>(version)

    private fun computeMin(all: Set<GradleAPITarget>): GradleAPITarget {
        if (all.size < 2) {
            logger.error("At at least 2 target Gradle API versions must be declared at `gradlePlugins.apiTargets`")
        }
        return all.minBy { it.gradleVersion }
    }

    override operator fun invoke(gradleVersion: String): GradleAPITarget =
        targetAPIs.maybeCreate(gradleVersion)

    override operator fun invoke(vararg gradleVersions: String) =
        invoke(gradleVersions.asList())

    override operator fun invoke(gradleVersions: Iterable<String>) =
        gradleVersions.map(::invoke)


    // Groovy DSL support
    fun call(targetAPI: String) = invoke(targetAPI)
    fun call(vararg targetAPIs: String) = invoke(*targetAPIs)
    fun call(targetAPIs: Iterable<String>) = invoke(targetAPIs)

}
