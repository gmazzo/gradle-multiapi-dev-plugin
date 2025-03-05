![GitHub](https://img.shields.io/github/license/gmazzo/gradle-multiapi-dev-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.gmazzo.gradle.multiapi)](https://plugins.gradle.org/plugin/io.github.gmazzo.gradle.multiapi)
[![Build Status](https://github.com/gmazzo/gradle-multiapi-dev-plugin/actions/workflows/build.yaml/badge.svg)](https://github.com/gmazzo/gradle-multiapi-dev-plugin/actions/workflows/build.yaml)
[![Coverage](https://codecov.io/gh/gmazzo/gradle-multiapi-dev-plugin/branch/main/graph/badge.svg?token=D5cDiPWvcS)](https://codecov.io/gh/gmazzo/gradle-multiapi-dev-plugin)
[![Users](https://img.shields.io/badge/users_by-Sourcegraph-purple)](https://sourcegraph.com/search?q=content:io.github.gmazzo.gradle.multiapi+-repo:github.com/gmazzo/gradle-multiapi-dev-plugin)

# gradle-multiapi-dev-plugin
A Gradle plugin allows targeting multiple `gradleApi()`s versions by leveraging [Java Plugin's Variant Features](https://docs.gradle.org/current/userguide/feature_variants.html).
 
# Usage
Apply the plugin at your `java-gradle-plugin` project:
```kotlin
plugins {
    `java-gradle-plugin`
    id("io.github.gmazzo.gradle.multiapi") version "<latest>" 
}

gradlePlugin {
    apiTargets("7.0", "8.1", "8.13")

    plugins {
        create("myPlugin") {
            // configure your plugin here
        }
    }
}
```
For each declared, a dedicated `gradle<version>` source will be created (with a companion `gradle<version>Test` test suite)

![Project Structure](README-structure.png)

> [!NOTE]
> `main` and `test` source sets will act as common shared code for all variants.
