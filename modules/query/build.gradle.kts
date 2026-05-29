plugins {
    kotlin("multiplatform")
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
                implementation(project(":loader"))
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
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Benchmark task
val benchmark by tasks.registering(JavaExec::class) {
    description = "Run query engine performance benchmark"
    group = "benchmark"
    classpath = sourceSets["jvmMain"].runtimeClasspath
    mainClass.set("io.github.tonyzhye.beancount.query.QueryBenchmarkKt")
    workingDir = rootDir
}
