import java.lang.System;

plugins {
    java
    `maven-publish`
    `signing`
}

subprojects {
    group = "com.github.jeanbaptistewatenberg.junit5kubernetes"
    version = "2.0.0"

    repositories {
        mavenCentral()
    }

    apply {
        plugin("java")
        plugin("java-library")
        plugin("maven-publish")
        plugin("signing")
    }

    java {
        withJavadocJar()
        withSourcesJar()
    }

    dependencies {
        testImplementation("org.assertj:assertj-core:3.11.1")
        testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")
    }

    publishing {
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/JeanBaptisteWATENBERG/junit5-kubernetes")
                credentials {
                    username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
                    password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
                }
            }
            maven {
                name = "MavenCentral"
                val releasesRepoUrl = "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
                val snapshotsRepoUrl = "https://oss.sonatype.org/content/repositories/snapshots/"
                url = uri(if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
                credentials {
                    username = System.getenv("ORG_GRADLE_PROJECT_SONATYPE_NEXUS_USERNAME")
                    password = System.getenv("ORG_GRADLE_PROJECT_SONATYPE_NEXUS_PASSWORD")
                }
            }
        }

        publications {
            create<MavenPublication>("default") {
                from(components["java"])
                pom {
                    name.set("junit5-kubernetes")
                    description.set("Use pod and other kubernetes object right form your junit5 tests.")
                    url.set("https://github.com/JeanBaptisteWATENBERG/junit5-kubernetes")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("JeanBaptisteWATENBERG")
                            name.set("Jean-Baptiste WATENBERG")
                            email.set("jeanbaptiste.watenberg@gmail.com")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/JeanBaptisteWATENBERG/junit5-kubernetes.git")
                        developerConnection.set("scm:git:ssh://github.com/JeanBaptisteWATENBERG/junit5-kubernetes.git")
                        url.set("https://github.com/JeanBaptisteWATENBERG/junit5-kubernetes")
                    }
                }
            }

        }
    }

    signing {
        val signingKey: String? by project
        val signingPassword: String? by project
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["default"])
    }

    tasks.test {
        useJUnitPlatform()
        testLogging {

            // set to false to disable detailed failure logs
            showExceptions = true

            // set to false to hide stack traces
            showStackTraces = true

            // set to false to hide exception causes
            showCauses = true

            // enable to see standard out and error streams inline with the test results
            showStandardStreams = true

            events("passed", "skipped", "failed")
        }
        systemProperties = System.getProperties().map { e -> Pair(e.key as String, e.value) }.toMap()
    }

    configure<JavaPluginConvention> {
        sourceCompatibility = JavaVersion.VERSION_1_8
    }
}



