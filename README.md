# gradle-cmakebuild

## Gradle plugin helps to build CMake project

### Usage

1. Add to root build.gradle

```kotlin
buildscript {
    repositories {
        //todo
    }
    dependencies {
        classpath("com.edwardstock:cmakebuild:0.1.0")
    }
}
```

2. Apply plugin

```kotlin
plugins {
    id("com.edwardstock.cmakebuild")
}
```

3. Configure

```kotlin
plugins {
    `java-library`
    kotlin("jvm")
    id("maven-publish")
    id("com.edwardstock.cmakebuild")
}

// Simple configuration requires only path to cmake project directory
// Plugin adds a task named buildCMake
cmakeBuild {
    // you can switch off native build by pass boolean
    enable = project.property("enable_native_build") == "1"
    path = rootProject.file("my-cmake-project-dir")
    // cmake's CMAKE_BUILD_TYPE
    buildType = "Debug"
    // common cmake arguments
    arguments += listOf(
        "-DMY_OPTION=1"
    )
    // also, you can specify cmake configure time definitions by special variable
    definitions["MY_OPTION"] = "1"
    // also you can override previously setup
    definitions["CMAKE_BUILD_TYPE"] = "Debug"


    allOS {
        // configure:
        // cFlags, cppFlags and arguments for all OS
    }

    // also you can specify configuration for:
    windows {}
    macos {}
    linux {}
}

// Set Jar-generation tasks depends on cmake project to make ability attach libs to jar
tasks.withType<Jar> {
    // exclude adding generated libs to javadoc and sources jar files
    if (archiveClassifier.get() != "sources" && archiveClassifier.get() != "javadoc") {
        dependsOn("buildCMake")
        // ${project.buildDir}/.cxx is a default location for cmake artifacts (archive, library and runtime)
        from(file("${project.buildDir}/.cxx"))
    }
}

// Also, to run local test with JNI-bindings, use Test task
tasks.withType<Test> {
    val arch = when (System.getProperty("os.arch")) {
        "x86_64",
        "amd64" -> "x86_64"
        else -> System.getProperty("os.arch")
    }
    allJvmArgs = if(cmakeBuild.isWindows) {
        allJvmArgs + listOf(
            // windows MSVC puts artifacts to configuration-specific dir
            "-Djava.library.path=${project.buildDir}/.cxx/${arch}/${cmakeBuild.buildType}"
        )
    } else {
        allJvmArgs + listOf(
            "-Djava.library.path=${project.buildDir}/.cxx/${arch}"
        )
    }
}
```
