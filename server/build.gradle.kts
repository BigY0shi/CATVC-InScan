// The shop server: one system of record for every phone. Ktor + SQLite, run as a single JVM process.
plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  application
}

kotlin { jvmToolchain(21) }

application {
  mainClass.set("com.countryatv.server.MainKt")
}

dependencies {
  implementation(project(":core"))
  implementation(libs.ktor.server.core)
  implementation(libs.ktor.server.cio)
  implementation(libs.ktor.server.content.negotiation)
  implementation(libs.ktor.server.status.pages)
  implementation(libs.ktor.serialization.kotlinx.json)
  implementation(libs.sqlite.jdbc)
  implementation(libs.logback.classic)
  testImplementation(libs.ktor.server.test.host)
  testImplementation(libs.ktor.client.content.negotiation)
  testImplementation(libs.kotlin.test)
}
