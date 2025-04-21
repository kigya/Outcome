import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    `maven-publish`
}

kotlin {
    explicitApi()

    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_1_8)
                }
            }
        }
    }

    jvm("desktop") {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_1_8)
                }
            }
        }
    }

    val xcf = XCFramework()
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "lib"
            xcf.add(this)
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "dev.kigya.outcome"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

version = "0.1.3"

publishing {
    publications.withType<MavenPublication>().configureEach {
        groupId = "dev.kigya.outcome"
        version = project.version.toString()
        artifactId = when (name) {
            "kotlinMultiplatform"    -> "core"
            "desktop"                -> "core-desktop"
            "androidRelease"         -> "core-android"
            "iosX64"                 -> "core-iosx64"
            "iosArm64"               -> "core-iosarm64"
            "iosSimulatorArm64"      -> "core-iossimulatorarm64"
            else                     -> "core-${name}"
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/kigya/Outcome")
            credentials {
                username = findProperty("gpr.user") as String?
                    ?: System.getenv("GITHUB_ACTOR")
                password = findProperty("gpr.key") as String?
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
        // mavenLocal()
    }
}

tasks.register<Delete>("cleanMavenLocal") {
    val groupIdPath = "dev/kigya"
    val artifactId = "outcome"

    delete(
        file("${System.getProperty("user.home")}/.m2/repository/$groupIdPath/$artifactId")
    )
}

tasks.named("publishToMavenLocal") {
    dependsOn(tasks.named("cleanMavenLocal"))
}
