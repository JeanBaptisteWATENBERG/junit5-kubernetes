rootProject.name = "junit5-kubernetes"

include("core")
file("modules").listFiles().forEach {
    include(it.name)
    project(":${it.name}").projectDir = it
}

