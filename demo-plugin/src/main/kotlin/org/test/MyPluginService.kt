package org.test

import org.gradle.api.Project

interface MyPluginService {

    val targetGradleVersion: String

    fun Project.onBuildFinished()

}
