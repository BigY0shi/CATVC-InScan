package com.countryatv.server

import com.countryatv.core.Problem
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

// Thrown by any route to refuse a request. Turned into the same JSON shape every time, so the app
// and the agent tools can show the message as-is.
class ApiProblem(
    val status: HttpStatusCode,
    val problem: Problem,
    val details: List<String> = emptyList()
) : RuntimeException(problem.name)

@Serializable
data class ProblemBody(
    val code: String,
    val message: String,
    // Specifics in plain words, e.g. the list of missing fields.
    val details: List<String> = emptyList()
)

fun ApiProblem.body() = ProblemBody(problem.name, problem.message, details)
