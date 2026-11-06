import org.gradle.jvm.tasks.Jar

plugins {
    application
}

dependencies {
    implementation(project(":diff-ui-swing"))
    implementation(libs.clikt)
    implementation(libs.kotlinx.coroutines.swing)
}

application {
    mainClass.set("org.ijdiff.app.MainKt")
    applicationName = "ijdiff"
}

val fatJar by tasks.registering(Jar::class) {
    group = "distribution"
    description = "Assembles a standalone executable fat-JAR containing ijdiff and all runtime dependencies."
    archiveBaseName.set("ijdiff-standalone")
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "org.ijdiff.app.MainKt"
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    })
}
