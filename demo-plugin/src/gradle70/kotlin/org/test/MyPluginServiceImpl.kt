package org.test

import org.gradle.api.Project

class MyPluginServiceImpl : MyPluginService {

    override val targetGradleVersion = "7.0"

    override fun Project.onBuildFinished() {
        gradle.buildFinished { println("Build finished") }
    }

}
