package com.countryatv.server

import com.countryatv.core.Problem
import io.ktor.http.HttpStatusCode
import java.time.Duration
import java.time.Instant

// When something happened on the phone, as opposed to when the server heard about it. A check-in
// made offline at 11:50 pm Sunday and sent at 8 am Monday belongs to Sunday.
data class Occurred(val at: Instant, val clamped: Boolean) {
    val stamp: String get() = stampOf(at)

    companion object {
        // A phone clock a little ahead of the server's is normal; more than this isn't believed.
        val MAX_AHEAD: Duration = Duration.ofMinutes(5)

        // The phone's time, kept inside [received - maxQueueAge, received + MAX_AHEAD]. A time
        // outside that window is moved to its nearest believable value and flagged: a time in the
        // future becomes the received time, a time older than any queued change could be becomes
        // the oldest allowed. No time sent (older phones) means "when the server got it".
        fun of(sent: String?, received: Instant, maxQueueAge: Duration, field: String = "occurredAt"): Occurred {
            if (sent == null) return Occurred(received, clamped = false)
            val phone = parseTime(sent) ?: throw ApiProblem(HttpStatusCode.BadRequest, Problem.BAD_REQUEST, listOf(field))
            val oldest = received.minus(maxQueueAge)
            return when {
                phone.isAfter(received.plus(MAX_AHEAD)) -> Occurred(received, clamped = true)
                phone.isBefore(oldest) -> Occurred(oldest, clamped = true)
                else -> Occurred(phone, clamped = false)
            }
        }
    }
}
