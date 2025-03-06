package io.github.gmazzo.gradle.multiapi

import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.the
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class GradleMultiAPIPluginDevelopmentPluginTest {

    @Test
    fun `plugin can be configured`(): Unit = with(ProjectBuilder.builder().build()) {
        apply(plugin = "io.github.gmazzo.gradle.multiapi")

        configure<GradlePluginDevelopmentExtension> {
            val apiTargets = (this as ExtensionAware).the<GradleMultiAPIPluginDevelopmentExtension>()

            apiTargets("7.0", "8.1", "8.13")

            plugins {
                create("myPlugin") {
                    id = "org.test.myPlugin"
                    implementationClass = "org.test.MyPlugin"
                }
            }
        }
    }

}
