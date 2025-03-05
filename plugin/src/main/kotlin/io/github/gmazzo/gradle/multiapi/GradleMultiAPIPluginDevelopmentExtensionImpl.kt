package io.github.gmazzo.gradle.multiapi

import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.newInstance
import org.gradle.util.GradleVersion
import javax.inject.Inject

internal abstract class GradleMultiAPIPluginDevelopmentExtensionImpl @Inject constructor(
    private val objects: ObjectFactory,
    project: Project,
    private val minGradleVersion: GradleVersion,
) : GradleMultiAPIPluginDevelopmentExtension() {

    private val maxGradleVersion = GradleVersion.current()

    private val logger = project.logger

    abstract val providers: MapProperty<GradleVersion, GradleAPIClasspathProvider>

    abstract val minGradleAPI: Property<GradleAPIClasspathProvider>

    init {
        targetAPIs.all {
            check(this >= minGradleVersion && this <= maxGradleVersion) {
                "Target Gradle API $this must be in between $minGradleVersion and $maxGradleVersion"
            }

            providers.put(this, objects.newInstance<GradleAPIClasspathProvider>(this))
        }
        providers.finalizeValueOnRead()

        minGradleAPI.value(providers.map { it.computeMin() })
        minGradleAPI.finalizeValueOnRead()
    }

    private fun Map<GradleVersion, GradleAPIClasspathProvider>.computeMin(): GradleAPIClasspathProvider {
        if (size < 2) {
            logger.error("At at least 2 target Gradle API versions must be declared at `gradlePlugins.apiTargets`")
        }
        return minByOrNull { it.key }?.value ?: objects.newInstance(GradleVersion.current())
    }

    // for Groovy DSL
    fun call(vararg args: String) = invoke(*args)

    // for Groovy DSL
    fun call(vararg args: GradleVersion) = invoke(*args)

}
