@file:Suppress("UnstableApiUsage")

package org.test

import org.gradle.api.Project
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.base.TestingExtension

class MyPluginServiceImpl : MyPluginService {

    override fun Project.onBuildFinished() {
        objects.fileCollection()

        the<TestingExtension>().suites.withType<JvmTestSuite>().configureEach {
            println("Test suite: $name")
        }
    }

    override val targetGradleVersion = "8.13"

}
