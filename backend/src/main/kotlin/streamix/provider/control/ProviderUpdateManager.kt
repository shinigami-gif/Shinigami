package streamix.provider.control

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

enum class ProviderUpdateStatus { UP_TO_DATE, UPDATE_AVAILABLE, QUEUED, RUNNING, READY_TO_DEPLOY, ACTIVE, FAILED, ROLLED_BACK }
enum class ProviderUpdateStepStatus { PENDING, RUNNING, PASSED, FAILED, SKIPPED }

data class ProviderUpdateStep(
    val id: String,
    val name: String,
    val status: ProviderUpdateStepStatus = ProviderUpdateStepStatus.PENDING,
    val durationMs: Long? = null,
    val message: String? = null
)

data class ProviderUpdateCandidate(
    val providerId: String,
    val currentVersion: String?,
    val currentCommit: String?,
    val targetVersion: String,
    val targetCommit: String,
    val upstream: String,
    val changelog: List<String> = emptyList(),
    val detectedAt: Instant = Instant.now()
)

data class ProviderUpdateJob(
    val providerId: String,
    val candidate: ProviderUpdateCandidate,
    val status: ProviderUpdateStatus,
    val steps: List<ProviderUpdateStep> = emptyList(),
    val queuedAt: Instant? = null,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val error: String? = null
)

interface ProviderUpdateExecutor {
    suspend fun detect(providerId: String, activeVersion: ProviderVersion?): ProviderUpdateCandidate?
    suspend fun execute(candidate: ProviderUpdateCandidate, onStep: suspend (ProviderUpdateStep) -> Unit): ProviderUpdateResult
    suspend fun activate(candidate: ProviderUpdateCandidate): Boolean
    suspend fun rollback(providerId: String, targetVersion: String): ProviderUpdateResult
}

data class ProviderUpdateResult(
    val success: Boolean,
    val activatedVersion: String? = null,
    val activatedCommit: String? = null,
    val error: String? = null
)

class ProviderUpdateManager(
    private val controlPlane: ProviderControlPlane,
    private val executor: ProviderUpdateExecutor
) {
    private val jobs = ConcurrentHashMap<String, ProviderUpdateJob>()

    fun jobs(): List<ProviderUpdateJob> =
        jobs.values.sortedBy { it.providerId.lowercase() }

    fun job(providerId: String): ProviderUpdateJob? = jobs[providerId.lowercase()]

    suspend fun check(providerId: String): ProviderUpdateJob {
        val active = controlPlane.activeVersion(providerId)
        val candidate = executor.detect(providerId, active)
        val job = if (candidate == null) {
            ProviderUpdateJob(
                providerId = providerId,
                candidate = ProviderUpdateCandidate(
                    providerId = providerId,
                    currentVersion = active?.version,
                    currentCommit = active?.upstreamCommit,
                    targetVersion = active?.version ?: "unknown",
                    targetCommit = active?.upstreamCommit ?: "unknown",
                    upstream = active?.upstream ?: "unknown"
                ),
                status = ProviderUpdateStatus.UP_TO_DATE
            )
        } else {
            ProviderUpdateJob(providerId, candidate, ProviderUpdateStatus.UPDATE_AVAILABLE)
        }
        jobs[providerId.lowercase()] = job
        return job
    }

    fun enqueue(providerId: String): ProviderUpdateJob {
        val current = requireNotNull(jobs[providerId.lowercase()]) {
            "No update check exists for provider: " + providerId
        }
        require(current.status == ProviderUpdateStatus.UPDATE_AVAILABLE) {
            "Provider update is not available: " + current.status
        }
        return current.copy(
            status = ProviderUpdateStatus.QUEUED,
            queuedAt = Instant.now(),
            error = null
        ).also { jobs[providerId.lowercase()] = it }
    }

    suspend fun update(providerId: String): ProviderUpdateJob {
        val key = providerId.lowercase()
        val queued = requireNotNull(jobs[key]) {
            "No update job exists for provider: " + providerId
        }
        require(
            queued.status == ProviderUpdateStatus.QUEUED ||
                queued.status == ProviderUpdateStatus.UPDATE_AVAILABLE
        ) {
            "Provider update is not ready to run: " + queued.status
        }

        var running = queued.copy(
            status = ProviderUpdateStatus.RUNNING,
            startedAt = Instant.now(),
            error = null
        )
        jobs[key] = running

        return try {
            val result = executor.execute(queued.candidate) { step ->
                running = running.copy(
                    steps = running.steps.filterNot { it.id == step.id } + step
                )
                jobs[key] = running
            }

            if (!result.success) {
                running.copy(
                    status = ProviderUpdateStatus.FAILED,
                    completedAt = Instant.now(),
                    error = result.error ?: "Provider update failed"
                ).also { jobs[key] = it }
            } else {
                running.copy(
                    status = ProviderUpdateStatus.READY_TO_DEPLOY,
                    completedAt = Instant.now(),
                    error = null
                ).also { jobs[key] = it }
            }
        } catch (error: Throwable) {
            running.copy(
                status = ProviderUpdateStatus.FAILED,
                completedAt = Instant.now(),
                error = error.message ?: error::class.simpleName
            ).also { jobs[key] = it }
        }
    }

    suspend fun activate(providerId: String): ProviderUpdateJob {
        val key = providerId.lowercase()
        val job = requireNotNull(jobs[key]) {
            "No update job exists for provider: " + providerId
        }
        require(job.status == ProviderUpdateStatus.READY_TO_DEPLOY) {
            "Provider update is not ready to deploy: " + job.status
        }

        if (!executor.activate(job.candidate)) {
            return job.copy(
                status = ProviderUpdateStatus.FAILED,
                completedAt = Instant.now(),
                error = "Provider activation failed"
            ).also { jobs[key] = it }
        }

        val candidate = job.candidate
        controlPlane.activateVersion(
            providerId = candidate.providerId,
            version = candidate.targetVersion,
            upstream = candidate.upstream,
            upstreamCommit = candidate.targetCommit
        )

        return job.copy(status = ProviderUpdateStatus.ACTIVE).also { jobs[key] = it }
    }

    suspend fun rollback(providerId: String, targetVersion: String): ProviderUpdateJob {
        val key = providerId.lowercase()
        val job = requireNotNull(jobs[key]) {
            "No update job exists for provider: " + providerId
        }
        val result = executor.rollback(providerId, targetVersion)
        if (!result.success) {
            return job.copy(
                status = ProviderUpdateStatus.FAILED,
                completedAt = Instant.now(),
                error = result.error ?: "Rollback failed"
            ).also { jobs[key] = it }
        }

        val version = result.activatedVersion
        val commit = result.activatedCommit
        require(!version.isNullOrBlank()) { "Rollback succeeded without an active version" }
        require(!commit.isNullOrBlank()) { "Rollback succeeded without an active commit" }

        controlPlane.activateVersion(
            providerId = providerId,
            version = version,
            upstream = job.candidate.upstream,
            upstreamCommit = commit
        )

        return job.copy(
            status = ProviderUpdateStatus.ROLLED_BACK,
            completedAt = Instant.now(),
            error = null
        ).also { jobs[key] = it }
    }
}
