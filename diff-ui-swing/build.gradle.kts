dependencies {
    api(project(":diff-model"))
    implementation(libs.kotlinx.coroutines.swing)
    api(libs.flatlaf.core)
    implementation(libs.flatlaf.extras)
    api(libs.rsyntaxtextarea)
}
