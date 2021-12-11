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
// Plugin adding task named buildCMake
cmakeBuild {
    path = rootProject.file("my-cmake-project-dir")
}

// Set Jar-generation tasls depends on cmake project to make ability attach libs to jar
tasks.withType<Jar> {
    // exclude adding generated libs to javadoc and sources jar files
    if (archiveClassifier.get() != "sources" && archiveClassifier.get() != "javadoc") {
        dependsOn("buildCMake")
        // ${project.buildDir}/.cxx is a default location for cmake artifacts (archive, library and runtime)
        from(file("${project.buildDir}/.cxx"))
    }
}

// Also to run local test with JNI-bindings, use Test task
tasks.withType<Test> {
    val arch = when (System.getProperty("os.arch")) {
        "x86_64",
        "amd64" -> "x86_64"
        else -> System.getProperty("os.arch")
    }
    allJvmArgs = allJvmArgs + listOf(
        "-Djava.library.path=${project.buildDir}/.cxx/${arch}"
    )
}
```
