import org.gradle.jvm.tasks.Jar
import org.gradle.api.tasks.Sync

plugins {
    application
}

dependencies {
    implementation(project(":diff-ui-swing"))
    implementation(project(":diff-ui-compose"))
    implementation(libs.clikt)
    implementation(libs.kotlinx.coroutines.swing)
}

application {
    mainClass.set("org.ijdiff.app.MainKt")
    applicationName = "ijdiff"
    applicationDefaultJvmArgs = listOf(
        "-Dapple.laf.useScreenMenuBar=true",
        "-Dapple.awt.application.name=ijdiff",
        "-Dapple.awt.application.appearance=system"
    )
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
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
}

val stageJpackageInput by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Stages the standalone fat-JAR into build/jpackage-input for native installer creation via jpackage."
    dependsOn(fatJar)
    from(fatJar.map { it.archiveFile })
    into(layout.buildDirectory.dir("jpackage-input"))
    rename { "ijdiff-standalone.jar" }
}
