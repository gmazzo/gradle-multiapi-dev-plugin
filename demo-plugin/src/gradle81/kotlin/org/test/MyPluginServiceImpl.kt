@file:Suppress("UnstableApiUsage")

package org.test

import org.gradle.api.Project
import org.gradle.api.flow.FlowAction
import org.gradle.api.flow.FlowParameters
import org.gradle.api.flow.FlowScope
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.kotlin.dsl.always
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.base.TestingExtension

class MyPluginServiceImpl : MyPluginService {

    override val targetGradleVersion = "8.1"

    override fun Project.onBuildFinished() {
        serviceOf<FlowScope>().always(DummyAction::class) {}

        the<TestingExtension>().suites.withType<JvmTestSuite>().configureEach {
            println("Test suite type: ${testType.get()}")
        }
    }

    abstract class DummyAction : FlowAction<FlowParameters.None> {

        override fun execute(parameters: FlowParameters.None) {
        }

    }

}
