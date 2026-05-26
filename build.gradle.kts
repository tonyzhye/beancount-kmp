plugins {
    kotlin("multiplatform") version "2.3.20" apply false
}

allprojects {
    group = "io.github.tonyzhye.beancount"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}
