pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "intellij-diff"

include(
    ":diff-core",
    ":diff-model",
    ":diff-ui-swing",
    ":diff-ui-compose",
    ":app"
)
