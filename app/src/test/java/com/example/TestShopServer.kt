package com.example

import com.countryatv.server.Auth
import com.countryatv.server.Database
import com.countryatv.server.inscanServer
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.runBlocking
import java.io.File

// The real shop server, run inside a test on a free local port with its own temporary database.
// Tests check results by reading that database, never by trusting what the app reports.
class TestShopServer : AutoCloseable {
    private val dbFile = File.createTempFile("inscan-test", ".db").also { it.delete(); it.deleteOnExit() }
    val db = Database(dbFile.path)
    val auth = Auth(db)
    private val server: EmbeddedServer<*, *> = embeddedServer(CIO, port = 0, host = "127.0.0.1") { inscanServer(db) }.start()
    val baseUrl: String = "http://127.0.0.1:" + runBlocking { server.engine.resolvedConnectors().first().port }

    fun count(sql: String, vararg args: String): Int = db.read { c ->
        c.prepareStatement(sql).use { st ->
            args.forEachIndexed { i, a -> st.setString(i + 1, a) }
            st.executeQuery().use { it.next(); it.getInt(1) }
        }
    }

    fun value(sql: String, vararg args: String): String? = db.read { c ->
        c.prepareStatement(sql).use { st ->
            args.forEachIndexed { i, a -> st.setString(i + 1, a) }
            st.executeQuery().use { if (it.next()) it.getString(1) else null }
        }
    }

    override fun close() {
        server.stop(0, 0)
        db.close()
    }
}
