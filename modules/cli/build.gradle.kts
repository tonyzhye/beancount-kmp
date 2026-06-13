import java.util.Properties

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.3.20"
    application
    id("org.graalvm.buildtools.native")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("io.github.tonyzhye.beancount.cli.MainKt")
    applicationName = "beancount"
}

tasks.named<Jar>("jar") {
    archiveFileName.set("beancount.jar")
}

// Load local.properties (gitignored) once for both GraalVM and VC config
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        val stream = file.inputStream()
        try {
            load(stream)
        } finally {
            stream.close()
        }
    }
}

val graalvmHome: String? = localProps.getProperty("graalvm.home")
    ?: providers.gradleProperty("graalvmHome").orNull
    ?: System.getenv("GRAALVM_HOME")

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

tasks.named<Jar>("jar") {
    archiveFileName.set("beancount.jar")
}

tasks.test {
    useJUnitPlatform()
}

// GraalVM Native Image configuration
graalvmNative {
    binaries {
        // Main beancount CLI (bean-check, bean-doctor, bean-format, etc.)
        named("main") {
            imageName.set("beancount")
            mainClass.set("io.github.tonyzhye.beancount.cli.MainKt")
            classpath.setFrom(sourceSets["main"].runtimeClasspath + sourceSets["main"].output)
            buildArgs.add("--no-fallback")
            buildArgs.add("-O2")
            buildArgs.add("--initialize-at-build-time=kotlin.DeprecationLevel")
        }

        // Beanquery CLI (BQL interactive and batch query tool)
        register("beanquery") {
            imageName.set("beanquery")
            mainClass.set("io.github.tonyzhye.beancount.cli.BeanQueryMainKt")
            classpath.setFrom(sourceSets["main"].runtimeClasspath + sourceSets["main"].output)
            buildArgs.add("--no-fallback")
            buildArgs.add("-O2")
            buildArgs.add("--initialize-at-build-time=kotlin.DeprecationLevel")
        }
    }
}

// On Windows, GraalVM Native Image needs Visual Studio C++ toolchain (vcvarsall.bat).
// The path can be provided via local.properties (vcvarsall.path) or environment variable
// NATIVE_IMAGE_VCVARSALL. For CI, the workflow handles MSVC setup automatically.
// Local Windows builds should either:
//   1. Set vcvarsall.path in local.properties
//   2. Run vcvarsall.bat before gradle (e.g. in a Developer Command Prompt)
