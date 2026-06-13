plugins {
    kotlin("multiplatform") version "2.3.20" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.1" apply false
    id("org.graalvm.buildtools.native") version "0.10.4" apply false
    id("com.vanniktech.maven.publish") version "0.29.0" apply false
}

subprojects {
    apply(plugin = "org.jetbrains.kotlinx.kover")
}

allprojects {
    group = "io.github.tonyzhye.beancount"
    // Version can be overridden via -Pversion=xxx (used by CI from git tag)
    version = findProperty("version") as String? ?: "3.2.3-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}
