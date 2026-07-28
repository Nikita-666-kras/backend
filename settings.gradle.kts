rootProject.name = "blog-platform"

include(
    "common-library",
    "post-service",
    "parts-service",
    "sso-service",
    "api-gateway",
    "admin-service",
    "proposal-service"
)

project(":post-service").projectDir = file("article-service")
