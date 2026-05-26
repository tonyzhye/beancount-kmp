rootProject.name = "beancount-jvm"

// Include all submodules from the modules directory
file("modules").listFiles()
    ?.filter { it.isDirectory && File(it, "build.gradle.kts").exists() }
    ?.forEach { dir ->
        include(":${dir.name}")
        project(":${dir.name}").projectDir = dir
    }
