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

tasks.register<Javadoc>("pluginJavadoc") {
    group = "publishing"
    isFailOnError = false
    source = sourceSets["main"].allSource
    classpath += sourceSets["main"].compileClasspath
}

tasks.register<Jar>("sourcesJar") {
    dependsOn("classes")
    archiveClassifier.set("sources")
    group = "publishing"
    from(sourceSets.getByName("main").allSource)
}

tasks.register<Jar>("javadocJar") {
    dependsOn("pluginJavadoc")
    archiveClassifier.set("javadoc")
    group = "publishing"
    from((tasks.findByName("javadoc") as Javadoc).destinationDir)
}

artifacts {
    archives(tasks.getByName("sourcesJar"))
    archives(tasks.getByName("javadocJar"))
}

dependencies {
    implementation(gradleApi())
}


publishing {
    publications {
        create<MavenPublication>("release") {
            afterEvaluate {
//                from(components["java"])
                from(components["kotlin"])
            }
            artifact(tasks.getByName("sourcesJar"))
            artifact(tasks.getByName("javadocJar"))
            groupId = project.group as String
            artifactId = project.name
            version = project.version as String
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

afterEvaluate {
    publishing.publications.forEach {
        val pub = it as MavenPublication
        pub.pom {
            name.set(project.name)
            url.set("https://github.com/edwardstock/gradle-cmakebuild")
            inceptionYear.set("2021")
            description.set("Gradle plugin helps to build CMake project")
            scm {
                connection.set("scm:git:${pub.pom.url.get()}.git")
                developerConnection.set(connection)
                url.set(pub.pom.url)
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

project.tasks.withType<PublishToMavenLocal> {
    dependsOn("publishAllPublicationsToMavenLocalRepository")
}

signing {
    useGpgCmd()
    sign(publishing.publications)
}