rootProject.name = "cmakebuild"

include(
    ":cmakebuild",
)

project(":cmakebuild").projectDir = file("plugin")

pluginManagement {
    repositories {
        mavenLocal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
        maven(url = uri("https://repo1.maven.org/maven2/"))
        maven(url = uri("https://clojars.org/repo/"))
        maven(url = uri("https://oss.sonatype.org/content/repositories/snapshots/"))
        maven(url = uri("https://jitpack.io"))
        maven(url = uri("https://oss.jfrog.org/libs-snapshot/"))
        maven(url = uri("https://oss.jfrog.org/artifactory/oss-snapshot-local/"))
    }

}

