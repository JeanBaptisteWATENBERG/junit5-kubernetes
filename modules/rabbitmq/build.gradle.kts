dependencies {
    api(project(":core"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.11.0")

    testImplementation("com.rabbitmq:amqp-client:5.7.0")
}