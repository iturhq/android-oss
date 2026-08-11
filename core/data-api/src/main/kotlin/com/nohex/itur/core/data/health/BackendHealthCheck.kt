/*
 * Itur © 2025 by Max Noé <code@itur.cat>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nohex.itur.core.data.health

/**
 * Stable identity and user-facing label for one independently failing backend service.
 */
data class BackendService(
    val id: String,
    val displayName: String,
)

/** Stable service identifiers used when mapping failures to dependent UI actions. */
object BackendServiceIds {
    const val FIREBASE_AUTH = "firebase-auth"
    const val FIREBASE_FIRESTORE = "firebase-firestore"
}

/** Ordered health states shared by probes, operation reporters, and host presentation. */
enum class BackendHealthStatus(
    internal val severity: Int,
) {
    WORKING(0),
    DEGRADED(1),
    UNAVAILABLE(2),
}

/** Returns the most severe state, with an empty set treated as working. */
fun aggregateBackendHealthStatus(
    statuses: Iterable<BackendHealthStatus>,
): BackendHealthStatus = statuses.maxByOrNull(BackendHealthStatus::severity)
    ?: BackendHealthStatus.WORKING

/** Sanitised technical evidence suitable for support diagnostics, never normal UI copy. */
class BackendDiagnosticEvidence private constructor(
    val summary: String,
    val diagnosticTrace: String?,
) {
    companion object {
        fun from(
            failure: Throwable,
            fallbackMessage: String? = null,
        ): BackendDiagnosticEvidence = BackendDiagnosticEvidence(
            summary = BackendDiagnosticSanitizer.summary(failure, fallbackMessage),
            diagnosticTrace = BackendDiagnosticSanitizer.trace(failure),
        )

        fun sanitized(
            summary: String,
            diagnosticTrace: String? = null,
        ): BackendDiagnosticEvidence = BackendDiagnosticEvidence(
            summary = BackendDiagnosticSanitizer.sanitize(summary).take(MAX_SUMMARY_LENGTH),
            diagnosticTrace = diagnosticTrace
                ?.let(BackendDiagnosticSanitizer::sanitize)
                ?.take(MAX_TRACE_LENGTH),
        )
    }
}

/** Shared snapshot for one service. */
data class BackendServiceHealth(
    val id: String,
    val name: String,
    val status: BackendHealthStatus,
    val detail: String,
    val diagnosticTrace: String? = null,
) {
    /** Compatibility view for existing two-state consumers. */
    val available: Boolean
        get() = status == BackendHealthStatus.WORKING
}

/** Evidence strength matters: reachability success cannot erase a failed real operation. */
sealed interface BackendHealthObservation {
    data class ReachabilitySucceeded(val detail: String) : BackendHealthObservation

    data class ReachabilityFailed(
        val evidence: BackendDiagnosticEvidence,
    ) : BackendHealthObservation

    data class OperationSucceeded(val detail: String) : BackendHealthObservation

    data class OperationFailed(
        val evidence: BackendDiagnosticEvidence,
    ) : BackendHealthObservation
}

/**
 * Applies one observation with explicit precedence.
 *
 * Unavailable is the most severe state. A reachability success repairs an unavailable result,
 * but is weaker than operation evidence and therefore preserves degradation. A successful real
 * operation is strong enough to restore working immediately.
 */
fun BackendServiceHealth.withObservation(
    observation: BackendHealthObservation,
): BackendServiceHealth = when (observation) {
    is BackendHealthObservation.ReachabilitySucceeded -> if (status == BackendHealthStatus.DEGRADED) {
        this
    } else {
        copy(
            status = BackendHealthStatus.WORKING,
            detail = observation.detail,
            diagnosticTrace = null,
        )
    }

    is BackendHealthObservation.ReachabilityFailed -> copy(
        status = BackendHealthStatus.UNAVAILABLE,
        detail = observation.evidence.summary,
        diagnosticTrace = observation.evidence.diagnosticTrace,
    )

    is BackendHealthObservation.OperationSucceeded -> copy(
        status = BackendHealthStatus.WORKING,
        detail = observation.detail,
        diagnosticTrace = null,
    )

    is BackendHealthObservation.OperationFailed -> if (status == BackendHealthStatus.UNAVAILABLE) {
        this
    } else {
        copy(
            status = BackendHealthStatus.DEGRADED,
            detail = observation.evidence.summary,
            diagnosticTrace = observation.evidence.diagnosticTrace,
        )
    }
}

object BackendDiagnosticSanitizer {
    private const val REDACTED = "<redacted>"

    private val urlQuery = Regex("""(?i)\b(https?://[^\s?]+)\?[^\s]+""")
    private val authorization = Regex(
        """(?i)\bauthorization(\s*[:=]\s*)(?:bearer\s+)?[^\s,;]+""",
    )
    private val credentialAssignment = Regex(
        """(?i)\b(api[_-]?key|credential|password|secret|token)""" +
            """(\s*[:=]\s*)(?:"[^"]*"|'[^']*'|[^\s,;]+)""",
    )
    private val bearerToken = Regex("""(?i)\bbearer\s+[A-Za-z0-9._~+/\-=]+""")
    private val githubToken = Regex("""\b(?:github_pat_|gh[pousr]_)[A-Za-z0-9_]{8,}\b""")
    private val jwt = Regex("""\b[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b""")

    fun summary(
        failure: Throwable,
        fallbackMessage: String? = null,
    ): String {
        val type = failure.javaClass.simpleName.ifBlank {
            failure.javaClass.name.substringAfterLast('.')
        }
        val message = failure.message?.takeIf(String::isNotBlank) ?: fallbackMessage
        return sanitize(if (message == null) type else "$type: $message")
            .take(MAX_SUMMARY_LENGTH)
    }

    fun trace(failure: Throwable): String = sanitize(failure.stackTraceToString()).take(MAX_TRACE_LENGTH)

    fun sanitize(value: String): String = value
        .replace(urlQuery, "$1?$REDACTED")
        .replace(authorization) { match ->
            "Authorization${match.groupValues[1]}$REDACTED"
        }
        .replace(credentialAssignment) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}$REDACTED"
        }
        .replace(bearerToken, "Bearer $REDACTED")
        .replace(githubToken, REDACTED)
        .replace(jwt, REDACTED)
}

/**
 * Side-effect-free connectivity probe contributed to the application's Hilt set.
 *
 * [probe] returns normally when the service is available and throws when it is not. Callers
 * impose their own timeout, so implementations must also use a bounded native operation.
 * [recognizes] lets an operation failure mark the matching service unavailable immediately,
 * before the next scheduled probe.
 */
interface BackendHealthCheck {
    val service: BackendService

    suspend fun probe()

    fun recognizes(cause: Throwable): Boolean = false
}

private const val MAX_SUMMARY_LENGTH = 500
private const val MAX_TRACE_LENGTH = 12_000
