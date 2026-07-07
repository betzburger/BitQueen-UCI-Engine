plugins {
    kotlin("multiplatform") version "2.2.10"
}

group = "de.peterbetz.bitqueen"
version = "2.2"

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    macosArm64 {
        binaries {
            executable {
                baseName = "BitQueen-UCI"
                entryPoint = "de.peterbetz.bitqueen.uci.main"
            }
        }
    }
    linuxX64 {
        binaries {
            executable {
                baseName = "BitQueen-UCI"
                entryPoint = "de.peterbetz.bitqueen.uci.main"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            }
        }
        val jvmMain by getting
        val macosArm64Main by getting
    }
}

tasks.named<Jar>("jvmJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "de.peterbetz.bitqueen.uci.BitQueenUCIKt"
    }
    val runtimeClasspath = configurations.getByName("jvmRuntimeClasspath")
    from(runtimeClasspath.map { if (it.isDirectory) it else zipTree(it) })
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
