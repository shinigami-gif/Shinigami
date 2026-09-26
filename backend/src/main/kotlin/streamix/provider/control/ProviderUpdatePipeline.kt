package streamix.provider.control

interface ProviderSourceFetcher {
    suspend fun fetch(candidate: ProviderUpdateCandidate): ProviderSource
}

data class ProviderSource(
    val providerId: String,
    val version: String,
    val upstreamCommit: String,
    val sourceRef: String
)

interface ProviderJvmAdapter {
    suspend fun adapt(source: ProviderSource): ProviderBuildInput
}

data class ProviderBuildInput(
    val providerId: String,
    val version: String,
    val upstreamCommit: String,
    val sourceRef: String
)

interface ProviderBuildRunner {
    suspend fun build(input: ProviderBuildInput): ProviderBuildArtifact
}

data class ProviderBuildArtifact(
    val providerId: String,
    val version: String,
    val upstreamCommit: String,
    val artifactRef: String
)

interface ProviderContractTestRunner {
    suspend fun test(artifact: ProviderBuildArtifact): ProviderVerification
}

interface ProviderTestRunner {
    suspend fun test(artifact: ProviderBuildArtifact): ProviderVerification
}

interface ProviderStreamE2ERunner {
    suspend fun test(artifact: ProviderBuildArtifact): ProviderVerification
}

data class ProviderVerification(
    val passed: Boolean,
    val message: String? = null
)

interface ProviderArtifactPublisher {
    suspend fun publish(artifact: ProviderBuildArtifact): ProviderRelease
}

interface ProviderRuntimeReloader {
    suspend fun activate(release: ProviderRelease): Boolean
}

private data class ProviderVerificationCheck(
    val id: String,
    val name: String,
    val action: suspend () -> ProviderVerification
)

class PipelineProviderUpdateExecutor(
    private val detector: suspend (String, ProviderVersion?) -> ProviderUpdateCandidate?,
    private val sourceFetcher: ProviderSourceFetcher,
    private val jvmAdapter: ProviderJvmAdapter,
    private val buildRunner: ProviderBuildRunner,
    private val contractTests: ProviderContractTestRunner,
    private val providerTests: ProviderTestRunner,
    private val streamE2ETests: ProviderStreamE2ERunner,
    private val publisher: ProviderArtifactPublisher,
    private val runtimeReloader: ProviderRuntimeReloader,
    private val releaseStore: ProviderReleaseStore
) : ProviderUpdateExecutor {

    override suspend fun detect(
        providerId: String,
        activeVersion: ProviderVersion?
    ): ProviderUpdateCandidate? = detector(providerId, activeVersion)

    override suspend fun execute(
        candidate: ProviderUpdateCandidate,
        onStep: suspend (ProviderUpdateStep) -> Unit
    ): ProviderUpdateResult {
        suspend fun step(
            id: String,
            name: String,
            action: suspend () -> ProviderVerification
        ): ProviderVerification {
            onStep(ProviderUpdateStep(id, name, ProviderUpdateStepStatus.RUNNING))
            val started = System.nanoTime()
            return try {
                val result = action()
                val duration = (System.nanoTime() - started) / 1_000_000
                onStep(
                    ProviderUpdateStep(
                        id = id,
                        name = name,
                        status = if (result.passed) ProviderUpdateStepStatus.PASSED
                        else ProviderUpdateStepStatus.FAILED,
                        durationMs = duration,
                        message = result.message
                    )
                )
                result
            } catch (error: Throwable) {
                val duration = (System.nanoTime() - started) / 1_000_000
                onStep(
                    ProviderUpdateStep(
                        id = id,
                        name = name,
                        status = ProviderUpdateStepStatus.FAILED,
                        durationMs = duration,
                        message = error.message ?: error::class.simpleName
                    )
                )
                ProviderVerification(false, error.message)
            }
        }

        val source = try {
            onStep(ProviderUpdateStep("detect", "Detect upstream", ProviderUpdateStepStatus.PASSED))
            sourceFetcher.fetch(candidate)
        } catch (error: Throwable) {
            onStep(
                ProviderUpdateStep(
                    "fetch",
                    "Fetch upstream",
                    ProviderUpdateStepStatus.FAILED,
                    message = error.message
                )
            )
            return ProviderUpdateResult(false, error = error.message ?: "Upstream fetch failed")
        }

        val input = try {
            onStep(ProviderUpdateStep("adapt", "Adapt provider", ProviderUpdateStepStatus.RUNNING))
            val started = System.nanoTime()
            val value = jvmAdapter.adapt(source)
            onStep(
                ProviderUpdateStep(
                    "adapt",
                    "Adapt provider",
                    ProviderUpdateStepStatus.PASSED,
                    durationMs = (System.nanoTime() - started) / 1_000_000
                )
            )
            value
        } catch (error: Throwable) {
            onStep(
                ProviderUpdateStep(
                    "adapt",
                    "Adapt provider",
                    ProviderUpdateStepStatus.FAILED,
                    message = error.message
                )
            )
            return ProviderUpdateResult(false, error = error.message ?: "Provider adaptation failed")
        }

        val artifact = try {
            onStep(ProviderUpdateStep("build", "Build provider", ProviderUpdateStepStatus.RUNNING))
            val started = System.nanoTime()
            val value = buildRunner.build(input)
            onStep(
                ProviderUpdateStep(
                    "build",
                    "Build provider",
                    ProviderUpdateStepStatus.PASSED,
                    durationMs = (System.nanoTime() - started) / 1_000_000
                )
            )
            value
        } catch (error: Throwable) {
            onStep(
                ProviderUpdateStep(
                    "build",
                    "Build provider",
                    ProviderUpdateStepStatus.FAILED,
                    message = error.message
                )
            )
            return ProviderUpdateResult(false, error = error.message ?: "Provider build failed")
        }

        val checks = listOf(
            ProviderVerificationCheck("contract", "Contract Test") {
                contractTests.test(artifact)
            },
            ProviderVerificationCheck("provider", "Provider Test") {
                providerTests.test(artifact)
            },
            ProviderVerificationCheck("stream-e2e", "Stream E2E Test") {
                streamE2ETests.test(artifact)
            }
        )

        for (check in checks) {
            val result = step(check.id, check.name, check.action)
            if (!result.passed) {
                return ProviderUpdateResult(
                    false,
                    error = result.message ?: "${check.name} failed"
                )
            }
        }

        val release = try {
            onStep(ProviderUpdateStep("publish", "Publish", ProviderUpdateStepStatus.RUNNING))
            val started = System.nanoTime()
            val value = publisher.publish(artifact)
            releaseStore.publish(value)
            onStep(
                ProviderUpdateStep(
                    "publish",
                    "Publish",
                    ProviderUpdateStepStatus.PASSED,
                    durationMs = (System.nanoTime() - started) / 1_000_000
                )
            )
            value
        } catch (error: Throwable) {
            onStep(
                ProviderUpdateStep(
                    "publish",
                    "Publish",
                    ProviderUpdateStepStatus.FAILED,
                    message = error.message
                )
            )
            return ProviderUpdateResult(false, error = error.message ?: "Publish failed")
        }

        onStep(
            ProviderUpdateStep(
                "runtime-reload",
                "Runtime Reload",
                ProviderUpdateStepStatus.SKIPPED,
                message = "Waiting for release activation"
            )
        )

        return ProviderUpdateResult(
            success = true,
            activatedVersion = release.version,
            activatedCommit = release.upstreamCommit
        )
    }

    override suspend fun activate(candidate: ProviderUpdateCandidate): Boolean {
        val release = releaseStore.get(candidate.providerId, candidate.targetVersion) ?: return false
        if (!runtimeReloader.activate(release)) return false
        releaseStore.activate(candidate.providerId, candidate.targetVersion)
        return true
    }

    override suspend fun rollback(
        providerId: String,
        targetVersion: String
    ): ProviderUpdateResult {
        val release = releaseStore.get(providerId, targetVersion)
            ?: return ProviderUpdateResult(false, error = "Release not found: ${providerId}@${targetVersion}")
        if (!runtimeReloader.activate(release)) {
            return ProviderUpdateResult(false, error = "Runtime activation failed: ${providerId}@${targetVersion}")
        }
        releaseStore.rollback(providerId, targetVersion)
        return ProviderUpdateResult(
            success = true,
            activatedVersion = release.version,
            activatedCommit = release.upstreamCommit
        )
    }
}
