package org.test

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply

abstract class MyPluginBase(private val version: String) : Plugin<Project> {

    override fun apply(target: Project) {
        println("Target Gradle version: $version")
        println("Running Gradle version: ${target.gradle.gradleVersion}")

        target.apply(plugin = "java")
        target.doSpecificStuff()
    }

    abstract fun Project.doSpecificStuff()

}
