pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Pleos Playground SDK (Vehicle, NaviHelper 등)
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/")
        // 네이버 지도 SDK (ADR-001)
        maven("https://repository.map.naver.com/archive/maven")
    }
}

rootProject.name = "Nanugil"
include(":app")
