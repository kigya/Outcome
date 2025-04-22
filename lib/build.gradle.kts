import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

    iosX64()
    iosArm64()
    iosSimulatorArm64()

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
    compileSdk = 35
    namespace = "dev.kigya.outcome"
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

group = "dev.kigya.outcome"
version = "0.1.3"

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifactId = when (name) {
            "kotlinMultiplatform" -> "core"
            "iosArm64" -> "core-iosarm64"
            "iosX64" -> "core-iosx64"
            "iosSimulatorArm64" -> "core-iossimulatorarm64"
            else -> name  // если будут другие таргеты
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
        mavenLocal()
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
