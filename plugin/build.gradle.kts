plugins {
    id("java-gradle-plugin")
    id("kotlin")
    id("maven-publish")
    id("signing")
}

group = "com.edwardstock"
version = "0.2.2"

gradlePlugin {
    plugins {
        create("cmakebuild") {
            id = "${group}.cmakebuild"
            implementationClass = "${group}.cmakebuild.CMakeBuild"
        }
    }
}

dependencies {
    implementation(gradleApi())
}

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components.getByName("java"))
            groupId = project.group as String
            artifactId = project.name
            version = project.version as String

            pom {
                name.set(project.name)
                url.set("https://github.com/edwardstock/gradle-cmakebuild")
                inceptionYear.set("2021")
                description.set("Gradle plugin helps to build CMake project")
                scm {
                    connection.set("scm:git:${pom.url}.git")
                    developerConnection.set(connection)
                    url.set(pom.url)
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
    }
    repositories {
        mavenLocal()
        maven(url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")) {
            credentials.username = findProperty("ossrhUsername") as String?
            credentials.password = findProperty("ossrhPassword") as String?
        }
    }
}

project.tasks.withType<PublishToMavenLocal> {
    dependsOn("publishAllPublicationsToMavenLocalRepository")
}

signing {
    useGpgCmd()
    sign(publishing.publications)
}