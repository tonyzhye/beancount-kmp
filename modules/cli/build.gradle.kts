plugins {
    kotlin("jvm")
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

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.0")
}

tasks.test {
    useJUnitPlatform()
}
