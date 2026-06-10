plugins {
    kotlin("multiplatform") version "2.3.20" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.1" apply false
    id("org.graalvm.buildtools.native") version "0.10.4" apply false
}

subprojects {
    apply(plugin = "org.jetbrains.kotlinx.kover")

    // Auto-configure maven-publish and signing for all KMP subprojects except cli
    afterEvaluate {
        val hasMultiplatform = plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")
        val isCli = name == "cli"
        if (hasMultiplatform && !isCli) {
            apply(plugin = "maven-publish")
            apply(plugin = "signing")

            configure<SigningExtension> {
                val signingKeyId = findProperty("signing.keyId") as String?
                val signingPassword = findProperty("signing.password") as String?
                val signingSecretKeyRingFile = findProperty("signing.secretKeyRingFile") as String?
                if (!signingKeyId.isNullOrBlank() && !signingPassword.isNullOrBlank()) {
                    useInMemoryPgpKeys(signingKeyId, signingSecretKeyRingFile?.let { java.io.File(it).readText() }, signingPassword)
                    sign(the<PublishingExtension>().publications)
                }
            }

            configure<PublishingExtension> {
                publications.withType<MavenPublication>().configureEach {
                    pom {
                        name.set("beancount-kmp-${project.name}")
                        description.set("Beancount JVM - A JVM implementation of Beancount")
                        url.set("https://github.com/tonyzhye/beancount-kmp")
                        licenses {
                            license {
                                name.set("GNU General Public License v2.0")
                                url.set("https://www.gnu.org/licenses/old-licenses/gpl-2.0.txt")
                            }
                        }
                        developers {
                            developer {
                                id.set("tonyzhye")
                                name.set("Tony Zhang")
                            }
                        }
                        scm {
                            connection.set("scm:git:git://github.com/tonyzhye/beancount-kmp.git")
                            developerConnection.set("scm:git:ssh://github.com:tonyzhye/beancount-kmp.git")
                            url.set("https://github.com/tonyzhye/beancount-kmp")
                        }
                    }
                }
                repositories {
                    maven {
                        name = "mavenCentral"
                        val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                        val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
                        url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
                        credentials {
                            username = findProperty("mavenCentralUsername") as String? ?: System.getenv("MAVEN_CENTRAL_USERNAME") ?: ""
                            password = findProperty("mavenCentralPassword") as String? ?: System.getenv("MAVEN_CENTRAL_PASSWORD") ?: ""
                        }
                    }
                }
            }
        }
    }
}

allprojects {
    group = "io.github.tonyzhye.beancount"
    // Version can be overridden via -Pversion=xxx (used by CI from git tag)
    version = findProperty("version") as String? ?: "3.2.3-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}
