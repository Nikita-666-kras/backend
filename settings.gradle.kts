pluginManagement {
    repositories {
        mavenCentral()
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        gradlePluginPortal()
    }
}

rootProject.name = "blog-platform"

include(
    "common-library",
    "post-service",
    "parts-service",
    "sso-service",
    "api-gateway",
    "admin-service",
    "proposal-service",
    "logging-service",
    "integrations-service"
)

project(":post-service").projectDir = file("article-service")