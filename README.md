# gradle-cmakebuild

## Gradle plugin helps to build CMake project

### Usage

1. Version catalogs

```toml
[plugins]
cmake = { id = "com.edwardstock.cmakebuild", version = "0.3.0" } 
```

2. Root build.gradle.kts
```kotlin
plugins {
    alias(libs.plugins.cmake) apply false
}
```

3. Configure module build.gradle.kts

```kotlin
plugins {
    `java-library`
    kotlin("jvm")
    id("maven-publish")
    alias(libs.plugins.cmake) // apply cmake plugin
}

// Simple configuration requires only path to cmake project directory
// Plugin adds a task named buildCMake
cmakeBuild {
    // you can switch off native build by pass boolean
    enable = project.property("enable_native_build") == "1"
    path = rootProject.file("my-cmake-project-dir")

    /* set specific cmake build directory, by default it uses ${project}/build/cmake
    stagingPath = project.buildDir
     */

    // cmake's --config Debug
    buildType = "Debug"
    // common cmake arguments
    arguments += listOf(
        "-DMY_OPTION=1"
    )
    // specify cmake configure time definitions by special variable
    definitions["MY_OPTION"] = "1"
    definitions["CMAKE_BUILD_TYPE"] = "Debug"


    // set configuration for all OS:
    allOS {
        // configure:
        // cFlags, cppFlags, arguments or definitions for all OS
    }

    // set configuration for specific OS:
    windows {}
    macos {}
    linux {}
}
```

### Main Usage: Integration with SciJava

A primary use case for this plugin is to build native libraries for use with the SciJava `native-lib-loader`. To enable this, set the
`useScijavaLoaderTemplate` property to `true`. This will configure the build to output the native libraries in the directory structure expected by the
SciJava loader.

Here is a complete example of how to configure your build for SciJava:

```kotlin
import com.edwardstock.cmakebuild.getCMakeBuildPathHostSpecific
import org.jetbrains.kotlin.gradle.tasks.ProcessResources

plugins {
    `java-library`
    kotlin("jvm")
    alias(libs.plugins.cmake)
}

cmakeBuild {
    // Enable SciJava native-lib-loader integration
    useScijavaLoaderTemplate = true
    path = rootProject.file("native")
    // ... other configurations
}

/**
 * Include the CMake build output (native libs) into the JVM resources,
 * so they get packaged into the JAR and are available at runtime.
 */
run {
    val cmakeOutDir = providers.provider {
        getCMakeBuildPathHostSpecific(cmakeBuild).parentFile.parentFile
    }

    // Add to jvmMain resources
    kotlin.sourceSets.named("jvmMain") {
        resources.srcDir(cmakeOutDir)
    }

    tasks.named<ProcessResources>("jvmProcessResources").configure {
        dependsOn("buildCMake")
        inputs.dir(cmakeOutDir)
    }

    tasks.matching { it.name in setOf("jvmJar", "jar") }.configureEach {
        dependsOn("jvmProcessResources")
    }
}
```
