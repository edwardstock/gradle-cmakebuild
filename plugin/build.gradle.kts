import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java-gradle-plugin")
    alias(libs.plugins.kotlin.jvm)
    id("signing")
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "com.edwardstock"
version = "0.3.0"

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

gradlePlugin {
    plugins {
        create("cmakebuild") {
            id = "${group}.cmakebuild"
            implementationClass = "${id}.CMakeBuild"
        }
    }
}

dependencies {
    implementation(gradleApi())
}

signing {
    useGpgCmd()
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    pom {
        val thisPom = this
        name.set(project.name)
        url.set("https://github.com/edwardstock/gradle-cmakebuild")
        inceptionYear.set("2021")
        description.set("Gradle plugin helps to build CMake project")
        scm {
            connection.set("scm:git:${thisPom.url.get()}.git")
            developerConnection.set(connection)
            url.set(thisPom.url)
        }
        licenses {
            license {
                name.set("The MIT License")
                url.set("https://github.com/edwardstock/gradle-cmakebuild/blob/master/LICENSE")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("edwardstock")
                name.set("Eduard Maximovich")
                email.set("edward.vstock@gmail.com")
                roles.add("owner")
                timezone.set("Europe/Moscow")
            }
        }
    }
}
