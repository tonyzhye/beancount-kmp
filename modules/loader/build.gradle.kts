plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "2.3.20"
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    coordinates(
        groupId = "io.github.tonyzhye.beancount",
        artifactId = "loader",
        version = project.version.toString()
    )
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core"))
                implementation(project(":parser"))
                implementation(project(":plugin-api"))
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }
        jvmTest {
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter:5.10.0")
                implementation("org.junit.jupiter:junit-jupiter-params:5.10.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation(project(":query"))
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
