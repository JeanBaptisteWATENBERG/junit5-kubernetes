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
    }

    configure<JavaPluginConvention> {
        sourceCompatibility = JavaVersion.VERSION_1_8
    }
}



