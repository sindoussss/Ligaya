package com.ligaya.core.ai

/**
 * Step 51's latency instrumentation for the Emergency Companion loop's own Gemini call — wraps a
 * real [CompanionResponseProvider] without changing
 * [com.ligaya.feature.companion.EmergencyCompanionCoordinator]'s own logic at all. See
 * [PipelineLatencyLog]'s own doc comment for why instrumentation lives here, at the composition
 * root's wiring, rather than inside the pipeline itself.
 */
class TimingCompanionResponseProvider(
    private val delegate: CompanionResponseProvider,
) : CompanionResponseProvider {
    override suspend fun respond(history: List<CompanionTurn>, latestUserUtterance: String): String =
        PipelineLatencyLog.measure("gemini_companion") { delegate.respond(history, latestUserUtterance) }
}
