import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Fake adapters shared by unit tests. Never a dependency of production code.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api(project(":core:domain"))
    implementation(libs.kotlinx.coroutines.core)
}
