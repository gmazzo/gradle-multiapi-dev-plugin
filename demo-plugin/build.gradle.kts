plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.samReceiver)
    alias(libs.plugins.gradle.pluginPublish)
    id("io.github.gmazzo.gradle.multiapi")
}

group = "org.test"
version = "0.1.0"

samWithReceiver.annotation(HasImplicitReceiver::class.java.name)

gradlePlugin {
    website = "https://example.org"
    vcsUrl = "https://example.org/repo"

    apiTargets("7.0", "8.1", "8.13")
    apiTargets.projectCache()

    plugins {
        create("myPlugin") {
            id = "org.test.myPlugin"
            displayName = "My Plugin"
            description = "My Plugin Description"
            implementationClass = "org.test.MyPlugin"
            tags = listOf("tag1", "tag2")
        }
    }
}

testing.suites.withType<JvmTestSuite>().configureEach {
    useJUnitJupiter()
}

tasks.publishPlugins {
    validateOnly = true
}
