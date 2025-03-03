package org.test

import org.gradle.kotlin.dsl.apply
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class MyPluginTest {

    @Test
    fun `apply plugin`(): Unit = with(ProjectBuilder.builder().build()) {
        apply(plugin = "org.test.myPlugin")
    }

}
