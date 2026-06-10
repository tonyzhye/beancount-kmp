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

// Detect Visual Studio vcvarsall.bat for Windows Native Image builds.
// Priority: local.properties > vswhere auto-detection > null
val detectedVsVarsPath: File? = run {
    val fromProps = localProps.getProperty("vcvarsall.path")?.let { File(it) }
    if (fromProps?.exists() == true) return@run fromProps

    // Auto-detect via vswhere on Windows
    val osName = System.getProperty("os.name") ?: ""
    if (osName.contains("Windows", ignoreCase = true)) {
        val vswherePaths = listOf(
            File("C:/Program Files (x86)/Microsoft Visual Studio/Installer/vswhere.exe"),
            File("C:/Program Files/Microsoft Visual Studio/Installer/vswhere.exe")
        )
        for (vswhere in vswherePaths) {
            if (!vswhere.exists()) continue
            try {
                val process = ProcessBuilder(
                    vswhere.absolutePath,
                    "-products", "*",
                    "-requires", "Microsoft.VisualStudio.Component.VC.Tools.x86.x64",
                    "-property", "installationPath"
                ).redirectOutput(ProcessBuilder.Redirect.PIPE).start()
                val output = process.inputStream.bufferedReader().readText().trim()
                process.waitFor()
                if (output.isNotBlank()) {
                    val candidate = File(output, "VC/Auxiliary/Build/vcvarsall.bat")
                    if (candidate.exists()) return@run candidate
                }
            } catch (_: Exception) { /* ignore */ }
        }
    }
    null
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
// If auto-detected above, inject the NATIVE_IMAGE_VCVARSALL env var into the task.
if (detectedVsVarsPath != null) {
    tasks.withType<org.gradle.api.tasks.Exec>().configureEach {
        if (name.contains("native", ignoreCase = true) && name.contains("Compile", ignoreCase = true)) {
            environment("NATIVE_IMAGE_VCVARSALL", detectedVsVarsPath.absolutePath)
        }
    }
}
