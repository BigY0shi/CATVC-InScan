package com.countryatv.server

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlin.system.exitProcess

// Usage (run on the server box):
//   inscan-server serve                          start the API (INSCAN_DB, INSCAN_HOST, INSCAN_PORT)
//   inscan-server add-employee NAME [ROLE]       add a person with a role (Employee by default; Management,
//                                                Admin, or any role's name); the PIN is typed, never passed
//   inscan-server setup-code [HOURS]             print a one-time code to enroll a phone; it lasts 24
//                                                hours, or HOURS (1 to 720) for a code sent ahead
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
            val name = args.getOrNull(1) ?: fail("Give the person's name: add-employee NAME [ROLE]")
            val role = args.drop(2).joinToString(" ").ifBlank { "Employee" }
            val pin = readPin("PIN for $name (4 to 8 digits): ")
            if (readPin("Type the PIN again: ") != pin) fail("The two PINs didn't match. Nothing was saved.")
            Database(dbPath).use { db ->
                runCatching { Auth(db).createEmployee(name, role, pin) }
                    .onFailure { fail(it.message ?: "Couldn't add the employee.") }
            }
            println("Added $name as $role.")
        }
        "setup-code" -> {
            val hours = setupCodeHours(args.drop(1))
                ?: fail("Give how many hours the code should last, from 1 to $MAX_SETUP_CODE_HOURS (a week is 168): setup-code [HOURS]")
            Database(dbPath).use { db -> println("Setup code (works once, for ${hoursInWords(hours)}): ${Auth(db).newSetupCode("console", hours)}") }
        }
        else -> fail("Unknown command. Use: serve | add-employee NAME [ROLE] | setup-code [HOURS]")
    }
}

// The hours asked for after "setup-code": none means the default; anything that isn't one whole
// number from 1 to MAX_SETUP_CODE_HOURS is refused (null) rather than guessed at.
internal fun setupCodeHours(rest: List<String>): Int? = when (rest.size) {
    0 -> DEFAULT_SETUP_CODE_HOURS
    1 -> rest[0].toIntOrNull()?.takeIf { it in 1..MAX_SETUP_CODE_HOURS }
    else -> null
}

// "24 hours", "1 hour", "168 hours (7 days)".
internal fun hoursInWords(hours: Int): String {
    val base = if (hours == 1) "1 hour" else "$hours hours"
    return if (hours > 24 && hours % 24 == 0) "$base (${hours / 24} days)" else base
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
