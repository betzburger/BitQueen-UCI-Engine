plugins {
    kotlin("multiplatform") version "2.2.10"
}

group = "de.peterbetz.bitqueen"
version = "4.2"

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    
    val nativeTargets = listOf(
        macosArm64(),
        macosX64(),
        mingwX64(),
        linuxX64(),
        linuxArm64()
    )
    
    nativeTargets.forEach { target ->
        target.binaries {
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
