package io.github.gmazzo.gradle.multiapi

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class GradleMultiAPIPluginDevelopmentPluginGroovyTest {

    @Test
    void "plugin can be configured in Groovy"() {
        ProjectBuilder.builder().build().with {
            apply(plugin: "io.github.gmazzo.gradle.multiapi")

            gradlePlugin {
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

}
