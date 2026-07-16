rootProject.name = "blog-platform"

include(
    "common-library",
    "post-service",
    "sso-service",
    "api-gateway",
    "admin-service"
)

project(":post-service").projectDir = file("article-service")
