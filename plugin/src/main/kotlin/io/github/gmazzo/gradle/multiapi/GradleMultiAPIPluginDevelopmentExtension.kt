package io.github.gmazzo.gradle.multiapi

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.provider.Provider

interface GradleMultiAPIPluginDevelopmentExtension {

    val targetAPIs: NamedDomainObjectContainer<GradleAPITarget>

    val minGradleAPI: Provider<GradleAPITarget>

    operator fun invoke(gradleVersion: String): GradleAPITarget

    operator fun invoke(vararg gradleVersions: String): List<GradleAPITarget>

    operator fun invoke(gradleVersions: Iterable<String>): List<GradleAPITarget>

}
