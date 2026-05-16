plugins {
    kotlin("jvm") version "1.9.23"
    application
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-client-core:2.3.9")
    implementation("io.ktor:ktor-client-cio:2.3.9")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.9")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.9")
    implementation("io.ktor:ktor-client-logging:2.3.9")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("MainKt")
}

tasks.register<JavaExec>("runTelegram") {
    group = "application"
    dependsOn(tasks.compileKotlin)
    classpath = project.extensions.getByType<org.gradle.api.tasks.SourceSetContainer>()["main"].runtimeClasspath
    mainClass.set("TelegramReceiptWorkflowKt")
    workingDir = projectDir
}
