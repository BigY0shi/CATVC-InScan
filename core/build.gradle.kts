// Pure Kotlin rules shared by the app and the shop server, so the two can never disagree.
// No Android imports: everything here is tested on the JVM, in CI and in the cloud session.
plugins {
  alias(libs.plugins.kotlin.jvm)
  // Report and filter shapes (UnitFilter, ReportDto) are @Serializable so the server and app read them the same way.
  alias(libs.plugins.kotlin.serialization)
}

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
  compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
}

dependencies {
  // QR encoding for printed tags (and decoding, so tests prove a label reads back exactly).
  api(libs.zxing.core)
  api(libs.kotlinx.serialization.core)
  testImplementation(libs.kotlin.test)
}
