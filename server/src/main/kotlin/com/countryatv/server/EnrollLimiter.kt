package com.countryatv.server

import java.time.Clock
import java.time.Duration
import java.time.Instant

// Slows down guessing at setup codes. Connecting a phone is the one request that needs no token,
// so on a server the internet can reach, anyone can try codes. Five wrong codes from one network
// address inside 15 minutes lock that address out until the oldest of the five is 15 minutes old.
//
// Counts are kept in memory: a restart clears them, which costs a guesser nothing worth having.
// Phones behind one router share an address, so they share a count.
class EnrollLimiter(private val clock: Clock) {
    private val wrongTries = LinkedHashMap<String, ArrayDeque<Instant>>()

    // When this address may try again, or null if it isn't locked out.
    @Synchronized
    fun lockedUntil(address: String): Instant? {
        val tries = recent(address) ?: return null
        return if (tries.size >= MAX_WRONG) tries.first().plus(WINDOW) else null
    }

    // Records a wrong code. True when this try is the one that locks the address out.
    @Synchronized
    fun wrongCode(address: String): Boolean {
        val tries = recent(address) ?: ArrayDeque<Instant>().also { wrongTries[address] = it }
        tries.addLast(clock.instant())
        if (wrongTries.size > MAX_ADDRESSES) forgetOldest()
        return tries.size == MAX_WRONG
    }

    // A code that worked clears the address's earlier wrong tries.
    @Synchronized
    fun rightCode(address: String) { wrongTries.remove(address) }

    // The address's wrong tries inside the window, oldest first. Null when there are none left.
    private fun recent(address: String): ArrayDeque<Instant>? {
        val tries = wrongTries[address] ?: return null
        val cutoff = clock.instant().minus(WINDOW)
        while (tries.isNotEmpty() && !tries.first().isAfter(cutoff)) tries.removeFirst()
        if (tries.isEmpty()) { wrongTries.remove(address); return null }
        return tries
    }

    // Keeps the table small when tries arrive from a great many addresses. Expired entries go
    // first; if the table is still full, the addresses that have waited longest are dropped.
    private fun forgetOldest() {
        val cutoff = clock.instant().minus(WINDOW)
        wrongTries.entries.removeIf { !it.value.last().isAfter(cutoff) }
        val it = wrongTries.entries.iterator()
        while (wrongTries.size > MAX_ADDRESSES && it.hasNext()) { it.next(); it.remove() }
    }

    companion object {
        const val MAX_WRONG = 5
        val WINDOW: Duration = Duration.ofMinutes(15)
        const val MAX_ADDRESSES = 10_000
    }
}
