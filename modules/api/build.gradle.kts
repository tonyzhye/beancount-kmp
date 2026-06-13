plugins {
    kotlin("multiplatform")
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    coordinates(
        groupId = "io.github.tonyzhye.beancount",
        artifactId = "api",
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
                api(project(":core"))
                api(project(":parser"))
                api(project(":loader"))
                api(project(":query"))
                api(project(":plugin-api"))
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
            }
        }
        jvmTest {
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter:5.10.0")
                implementation("org.junit.jupiter:junit-jupiter-params:5.10.0")
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
