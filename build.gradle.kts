buildscript {
    repositories {
        mavenLocal()
        google()
        maven(url = uri("https://plugins.gradle.org/m2/"))
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.10")
    }
}

