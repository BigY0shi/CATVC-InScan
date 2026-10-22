// Pure Kotlin rules shared by the app and the shop server, so the two can never disagree.
// No Android imports: everything here is tested on the JVM, in CI and in the cloud session.
plugins {
  alias(libs.plugins.kotlin.jvm)
}

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
  compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
}

dependencies {
  testImplementation(libs.kotlin.test)
}
