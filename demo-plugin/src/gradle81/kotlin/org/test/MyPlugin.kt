package org.test

import org.gradle.api.Project
import org.gradle.api.flow.FlowAction
import org.gradle.api.flow.FlowParameters
import org.gradle.api.flow.FlowScope
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.kotlin.dsl.always
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.base.TestingExtension
import javax.inject.Inject

class MyPlugin @Inject constructor(
    private val flowScope: FlowScope,
): MyPluginBase("8.1") {

    override fun Project.doSpecificStuff() {
        flowScope.always(DummyAction::class) {}

        the<TestingExtension>().suites.withType<JvmTestSuite>().configureEach {
            println("Test suite type: ${testType.get()}")
        }
    }

    interface DummyAction : FlowAction<FlowParameters.None>

}
