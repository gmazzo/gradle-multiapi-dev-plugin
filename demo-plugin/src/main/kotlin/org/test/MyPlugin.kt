package org.test

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import java.util.*

class MyPlugin : Plugin<Project> {

    val service: MyPluginService = ServiceLoader.load(MyPluginService::class.java).single()

    override fun apply(target: Project) = with(service) {
        println("Target Gradle version: ${service.targetGradleVersion}")
        println("Running Gradle version: ${target.gradle.gradleVersion}")

        target.apply(plugin = "java")
        target.onBuildFinished()
    }

}
