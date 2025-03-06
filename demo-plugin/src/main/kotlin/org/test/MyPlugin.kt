package org.test

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.internal.service.ServiceLocator
import org.gradle.kotlin.dsl.apply
import javax.inject.Inject

class MyPlugin @Inject constructor(
    serviceLocator: ServiceLocator,
) : Plugin<Project> {

    val service: MyPluginService = serviceLocator.get(MyPluginService::class.java)

    override fun apply(target: Project) = with(service) {
        println("Target Gradle version: ${service.targetGradleVersion}")
        println("Running Gradle version: ${target.gradle.gradleVersion}")

        target.apply(plugin = "java")
        target.onBuildFinished()
    }

}
