plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.3.20"
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("io.github.tonyzhye.beancount.cli.MainKt")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":parser"))
    implementation(project(":loader"))
    implementation(project(":query"))
    implementation(project(":plugin-api"))

    // CLI framework
    implementation("com.github.ajalt.clikt:clikt:4.4.0")

    // kotlinx-datetime (needed because core uses it as implementation-only)
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")

    // JSON serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.0")
    testImplementation("org.junit.platform:junit-platform-launcher:1.10.0")
}

// Create beanquery run task
tasks.register<JavaExec>("runBeanquery") {
    group = "application"
    description = "Run the beanquery CLI"
    classpath = configurations.runtimeClasspath.get() + sourceSets["main"].output
    mainClass.set("io.github.tonyzhye.beancount.cli.BeanQueryMainKt")
    args = project.findProperty("beanqueryArgs")?.toString()?.split(" ") ?: emptyList()
}

tasks.test {
    useJUnitPlatform()
}
