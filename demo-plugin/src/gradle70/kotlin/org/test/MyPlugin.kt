package org.test

import org.gradle.api.Project

class MyPlugin : MyPluginBase("7.0") {

    override fun Project.doSpecificStuff() {
        gradle.buildFinished { println("Build finished") }
    }

}
