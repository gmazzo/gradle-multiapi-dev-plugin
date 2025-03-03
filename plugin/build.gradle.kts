plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.samReceiver)
    alias(libs.plugins.gradle.pluginPublish)
    alias(libs.plugins.publicationsReport)
    jacoco
}

group = "io.github.gmazzo.gradle.multiapi"
description = "Gradle Multi API Development Plugin"
version = providers
    .exec { commandLine("git", "describe", "--tags", "--always") }
    .standardOutput.asText.get().trim().removePrefix("v")

java.toolchain.languageVersion.set(JavaLanguageVersion.of(libs.versions.java.get()))
samWithReceiver.annotation(HasImplicitReceiver::class.qualifiedName!!)

gradlePlugin {
    website.set("https://github.com/gmazzo/gradle-multiapi-dev-plugin")
    vcsUrl.set("https://github.com/gmazzo/gradle-multiapi-dev-plugin")

    plugins {
        create("java-gradle-multiapi-plugin") {
            id = "io.github.gmazzo.gradle.multiapi"
            displayName = name
            implementationClass = "io.github.gmazzo.gradle.multiapi.JavaGradleMultiAPIPlugin"
            description = "Enables targeting multiple Gradle APIs in a Gradle Plugin"
            tags.addAll("gradle-api", "multiple", "plugin-development")
        }
    }
}

dependencies {
    compileOnly(gradleKotlinDsl())

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.params)

    testImplementation(gradleKotlinDsl())
    testImplementation(gradleTestKit())
}

testing.suites.withType<JvmTestSuite> {
    useJUnitJupiter()
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports.xml.required = true
}

tasks.publish {
    dependsOn(tasks.publishPlugins)
}
