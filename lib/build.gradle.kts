plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinMultiplatform)
    `maven-publish`
}

group = "dev.kigya.outcome"
version = "0.2.0"

kotlin {
    explicitApi()

    androidTarget {
        publishLibraryVariants("release")
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    jvm("desktop") {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
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
    compileSdk = 35
    namespace = "dev.kigya.outcome"
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

publishing {
    publications {
        withType<MavenPublication>()
            .named("kotlinMultiplatform") {
                groupId = "dev.kigya.outcome"
                artifactId = "core"
                version = project.version.toString()
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
