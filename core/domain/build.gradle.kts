import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM: the domain layer must not depend on Android or UI frameworks
// (docs/02_SYSTEM_ARCHITECTURE.md, "no dependency from business logic onto UI/framework details").
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    // Fixture tests read the documentation package's machine-readable specs directly,
    // so the specs and the code cannot silently drift apart.
    systemProperty("zeno.specsDir", rootProject.file("specs").absolutePath)
}
