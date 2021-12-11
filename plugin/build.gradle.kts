plugins {
    id("java-gradle-plugin")
    id("kotlin")
    id("maven-publish")

}

group = "com.edwardstock"
version = "0.1.0"

gradlePlugin {
    plugins {
        create("cmakebuild") {
            id = "${group}.cmakebuild"
            implementationClass = "${group}.cmakebuild.CMakeBuild"
        }
    }
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
                licenses {
                    license {
                        name.set("MIT")
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
    }
}