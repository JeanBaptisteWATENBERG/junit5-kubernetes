import java.lang.System;

plugins {
    java
}

allprojects {
    group = "com.github.jeanbaptistewatenberg"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    apply {
        plugin("java")
    }

    dependencies {
        testImplementation("org.assertj:assertj-core:3.11.1")
        testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")
    }

    tasks.test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
        systemProperties = System.getProperties().map { e -> Pair(e.key as String, e.value) }.toMap()
    }

    configure<JavaPluginConvention> {
        sourceCompatibility = JavaVersion.VERSION_1_8
    }
}



