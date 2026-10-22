package com.countryatv.server

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlin.system.exitProcess

// Usage (run on the server box):
//   inscan-server serve                          start the API (INSCAN_DB, INSCAN_HOST, INSCAN_PORT)
//   inscan-server add-employee NAME [admin]      add an employee; the PIN is typed, never passed as an argument
//   inscan-server setup-code                     print a one-time code to enroll a phone
fun main(args: Array<String>) {
    val dbPath = System.getenv("INSCAN_DB") ?: "inscan.db"
    when (args.firstOrNull()) {
        "serve", null -> {
            val db = Database(dbPath)
            val host = System.getenv("INSCAN_HOST") ?: "127.0.0.1"
            val port = System.getenv("INSCAN_PORT")?.toIntOrNull() ?: 8080
            embeddedServer(CIO, port = port, host = host) { inscanServer(db) }.start(wait = true)
        }
        "add-employee" -> {
            val name = args.getOrNull(1) ?: fail("Give the employee's name: add-employee NAME [admin]")
            val role = if (args.getOrNull(2) == "admin") "admin" else "employee"
            val pin = readPin("PIN for $name (4 to 8 digits): ")
            if (readPin("Type the PIN again: ") != pin) fail("The two PINs didn't match. Nothing was saved.")
            Database(dbPath).use { db ->
                runCatching { Auth(db).createEmployee(name, role, pin) }
                    .onFailure { fail(it.message ?: "Couldn't add the employee.") }
            }
            println("Added $name ($role).")
        }
        "setup-code" -> {
            Database(dbPath).use { db -> println("Setup code (works once, for 24 hours): ${Auth(db).newSetupCode("console")}") }
        }
        else -> fail("Unknown command. Use: serve | add-employee NAME [admin] | setup-code")
    }
}

private fun readPin(prompt: String): String {
    val console = System.console()
    return if (console != null) String(console.readPassword(prompt)) else {
        print(prompt)
        readlnOrNull().orEmpty()
    }
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}
