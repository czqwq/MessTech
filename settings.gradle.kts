pluginManagement {
    repositories {
        maven {
            // RetroFuturaGradle
            name = "GTNH Maven"
            url = uri("https://nexus.gtnewhorizons.com/repository/public/")
            mavenContent {
                includeGroup("com.gtnewhorizons")
                includeGroupByRegex("com\\.gtnewhorizons\\..+")
            }
        }
        maven {
            name = "reposiliteRepositoryGtnhPublic"
            url = uri("https://maven.gaytnh.com/gtnh-public")
        }
        gradlePluginPortal()
        mavenCentral()
        maven {
            name = "JitPack"
            url = uri("https://jitpack.io")
            mavenContent {
                includeGroupByRegex("com\\.github\\..+")
            }
        }
        mavenLocal()
    }
}

plugins {
    id("com.gtnewhorizons.gtnhsettingsconvention") version("2.0.29")
}
