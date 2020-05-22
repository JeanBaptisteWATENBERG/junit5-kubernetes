dependencies {
    api(project(":jdbc"))

    testImplementation("com.zaxxer:HikariCP:3.4.3")
    testImplementation("org.postgresql:postgresql:42.2.12")
}