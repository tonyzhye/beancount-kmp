plugins {
    kotlin("multiplatform") version "2.3.20" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.1" apply false
}

subprojects {
    apply(plugin = "org.jetbrains.kotlinx.kover")
}

allprojects {
    group = "io.github.tonyzhye.beancount"
    version = "3.2.3"

    repositories {
        mavenCentral()
    }
}
