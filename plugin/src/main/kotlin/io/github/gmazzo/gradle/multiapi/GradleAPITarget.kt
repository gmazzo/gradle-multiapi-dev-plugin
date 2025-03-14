@file:Suppress("UnstableApiUsage")

package io.github.gmazzo.gradle.multiapi

import org.gradle.api.Named
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.tasks.SourceSet
import org.gradle.util.GradleVersion

interface GradleAPITarget : Named {

    val featureName: String

    val gradleVersion: GradleVersion

    val gradleApi: FileCollection

    val gradleTestKit: FileCollection

    val gradleKotlinDsl: FileCollection

    val sourceSet: SourceSet

    val testSuite: JvmTestSuite

}
