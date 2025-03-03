package io.github.gmazzo.gradle.multiapi

import org.gradle.api.DomainObjectSet
import org.gradle.util.GradleVersion

abstract class GradleMultiAPIPluginDevelopmentExtension {

    abstract val targetAPIs: DomainObjectSet<GradleVersion>

    operator fun invoke(vararg targetAPIs: String) =
        this(targetAPIs.toList())

    operator fun invoke(targetAPIs: Iterable<String>) =
        this(targetAPIs.map(GradleVersion::version))

    operator fun invoke(vararg targetAPIs: GradleVersion) =
        this(targetAPIs.toList())

    @JvmName("invokeGradleVersion")
    operator fun invoke(targetAPIs: Iterable<GradleVersion>) {
        this.targetAPIs.addAll(targetAPIs)
    }

    // for Groovy DSL
    fun call(vararg args: String) = invoke(*args)

    // for Groovy DSL
    fun call(vararg args: GradleVersion) = invoke(*args)

}
