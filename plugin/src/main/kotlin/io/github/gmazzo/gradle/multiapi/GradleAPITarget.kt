@file:Suppress("UnstableApiUsage")

package io.github.gmazzo.gradle.multiapi

import org.gradle.api.Named
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.tasks.SourceSet
import org.gradle.util.GradleVersion

public interface GradleAPITarget : Named {

    public val featureName: String

    public val gradleVersion: GradleVersion

    public val gradleApi: FileCollection

    public val gradleTestKit: FileCollection

    public val gradleKotlinDsl: FileCollection

    public val sourceSet: SourceSet

    public val testSuite: JvmTestSuite

}
