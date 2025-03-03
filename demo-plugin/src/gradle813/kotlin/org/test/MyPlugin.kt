package org.test

import org.gradle.api.Project
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.base.TestingExtension

class MyPlugin : MyPluginBase("8.13") {

    override fun Project.doSpecificStuff() {
        objects.fileCollection()

        the<TestingExtension>().suites.withType<JvmTestSuite>().configureEach {
            println("Test suite: $name")
        }
    }

}
