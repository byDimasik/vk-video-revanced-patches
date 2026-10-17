pluginManagement {
    repositories {
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2") }
        google()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2") }
        google()
        maven { url = uri("https://repo.clojars.org") }
        maven {
            url = uri("https://maven.pkg.github.com/revanced/revanced-patcher")
            credentials {
                username = System.getenv("GITHUB_ACTOR")?.takeIf { it.isNotBlank() } ?: "user"
                password = System.getenv("GITHUB_TOKEN")?.takeIf { it.isNotBlank() } ?: ""
            }
        }
    }
}

rootProject.name = "vk-video-revanced-patches"
include(":patches")
