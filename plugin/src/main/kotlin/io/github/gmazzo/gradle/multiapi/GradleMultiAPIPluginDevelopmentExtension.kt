package io.github.gmazzo.gradle.multiapi

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider

public interface GradleMultiAPIPluginDevelopmentExtension {

    /**
     * The different Gradle versions to test the plugin against.
     */
    public val targetAPIs: NamedDomainObjectContainer<GradleAPITarget>

    /**
     * The minimum Gradle version to support for the plugin, used to configure the test suites.
     */
    public val minGradleAPI: Provider<GradleAPITarget>

    /**
     * The Gradle user home directory to use for downloading (and caching) the different Gradle versions.
     */
    public val gradleUserHomeForCache: DirectoryProperty

    /**
     * Configures the Gradle version cache to be shared with the main Gradle user home directory.
     */
    public fun sharedCache()

    /**
     * Configures the Gradle version cache to be stored in the project's build directory.
     */
    public fun projectCache()

    /**
     * Disables the Gradle version cache, causing each Gradle version to be downloaded on each build.
     */
    public fun disableCache()

    public operator fun invoke(gradleVersion: String): GradleAPITarget

    public operator fun invoke(vararg gradleVersions: String): List<GradleAPITarget>

    public operator fun invoke(gradleVersions: Iterable<String>): List<GradleAPITarget>

}
