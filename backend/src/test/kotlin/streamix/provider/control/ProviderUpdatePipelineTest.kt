package streamix.provider.control

import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProviderUpdatePipelineTest {
    private val candidate = ProviderUpdateCandidate(
        providerId = "otakudesu",
        currentVersion = "1.2.3",
        currentCommit = "old",
        targetVersion = "1.2.4",
        targetCommit = "new",
        upstream = "Hatsune/AnimeX"
    )

    @Test
    fun execute_publishes_release_but_does_not_reload_runtime() = runTest {
        val store = InMemoryProviderReleaseStore()
        val reloader = FakeReloader()
        val publisher = FakePublisher()
        val executor = pipeline(
            store = store,
            reloader = reloader,
            publisher = publisher
        )

        val result = executor.execute(candidate) {}

        assertTrue(result.success)
        assertEquals("1.2.4", result.activatedVersion)
        assertEquals("new", result.activatedCommit)
        assertEquals("1.2.4", store.get("otakudesu", "1.2.4")?.version)
        assertNull(store.active("otakudesu"))
        assertFalse(reloader.activated)
        assertTrue(publisher.published)
    }

    @Test
    fun failed_verification_stops_before_publish() = runTest {
        val store = InMemoryProviderReleaseStore()
        val publisher = FakePublisher()
        val executor = pipeline(
            store = store,
            publisher = publisher,
            contractResult = ProviderVerification(false, "contract failed")
        )
        val steps = mutableListOf<ProviderUpdateStep>()

        val result = executor.execute(candidate) { steps += it }

        assertFalse(result.success)
        assertEquals("contract failed", result.error)
        assertFalse(publisher.published)
        assertNull(store.get("otakudesu", "1.2.4"))
        assertTrue(steps.any { it.id == "contract" && it.status == ProviderUpdateStepStatus.FAILED })
        assertFalse(steps.any { it.id == "publish" })
    }

    @Test
    fun activate_reloads_runtime_then_marks_release_active() = runTest {
        val store = InMemoryProviderReleaseStore()
        store.publish(release())
        val reloader = FakeReloader()
        val executor = pipeline(store = store, reloader = reloader)

        assertTrue(executor.activate(candidate))
        assertTrue(reloader.activated)
        assertEquals("1.2.4", reloader.lastRelease?.version)
        assertEquals("1.2.4", store.active("otakudesu")?.version)
    }

    @Test
    fun rollback_reloads_target_release_then_marks_it_active() = runTest {
        val store = InMemoryProviderReleaseStore()
        store.publish(release(version = "1.2.3", commit = "old", artifact = "artifact-old"))
        store.publish(release())
        store.activate("otakudesu", "1.2.4")

        val reloader = FakeReloader()
        val executor = pipeline(store = store, reloader = reloader)

        val result = executor.rollback("otakudesu", "1.2.3")
        assertTrue(result.success)
        assertEquals("1.2.3", result.activatedVersion)
        assertEquals("old", result.activatedCommit)
        assertEquals("1.2.3", reloader.lastRelease?.version)
        assertEquals("1.2.3", store.active("otakudesu")?.version)
    }

    private fun pipeline(
        store: ProviderReleaseStore,
        reloader: FakeReloader = FakeReloader(),
        publisher: FakePublisher = FakePublisher(),
        contractResult: ProviderVerification = ProviderVerification(true),
        providerResult: ProviderVerification = ProviderVerification(true),
        streamResult: ProviderVerification = ProviderVerification(true)
    ): PipelineProviderUpdateExecutor {
        val source = ProviderSource(
            providerId = candidate.providerId,
            version = candidate.targetVersion,
            upstreamCommit = candidate.targetCommit,
            sourceRef = "source-ref"
        )
        val input = ProviderBuildInput(
            providerId = candidate.providerId,
            version = candidate.targetVersion,
            upstreamCommit = candidate.targetCommit,
            sourceRef = source.sourceRef
        )
        val artifact = ProviderBuildArtifact(
            providerId = candidate.providerId,
            version = candidate.targetVersion,
            upstreamCommit = candidate.targetCommit,
            artifactRef = "artifact-new"
        )

        return PipelineProviderUpdateExecutor(
            detector = { _, _ -> candidate },
            sourceFetcher = object : ProviderSourceFetcher {
                override suspend fun fetch(candidate: ProviderUpdateCandidate) = source
            },
            jvmAdapter = object : ProviderJvmAdapter {
                override suspend fun adapt(source: ProviderSource) = input
            },
            buildRunner = object : ProviderBuildRunner {
                override suspend fun build(input: ProviderBuildInput) = artifact
            },
            contractTests = object : ProviderContractTestRunner {
                override suspend fun test(artifact: ProviderBuildArtifact) = contractResult
            },
            providerTests = object : ProviderTestRunner {
                override suspend fun test(artifact: ProviderBuildArtifact) = providerResult
            },
            streamE2ETests = object : ProviderStreamE2ERunner {
                override suspend fun test(artifact: ProviderBuildArtifact) = streamResult
            },
            publisher = publisher,
            runtimeReloader = reloader,
            releaseStore = store
        )
    }

    private fun release(
        version: String = candidate.targetVersion,
        commit: String = candidate.targetCommit,
        artifact: String = "artifact-new"
    ) = ProviderRelease(
        providerId = candidate.providerId,
        version = version,
        upstream = candidate.upstream,
        upstreamCommit = commit,
        artifactRef = artifact,
        publishedAt = Instant.now()
    )

    private class FakePublisher : ProviderArtifactPublisher {
        var published = false

        override suspend fun publish(artifact: ProviderBuildArtifact): ProviderRelease {
            published = true
            return ProviderRelease(
                providerId = artifact.providerId,
                version = artifact.version,
                upstream = "Hatsune/AnimeX",
                upstreamCommit = artifact.upstreamCommit,
                artifactRef = artifact.artifactRef,
                publishedAt = Instant.now()
            )
        }
    }

    private class FakeReloader : ProviderRuntimeReloader {
        var activated = false
        var lastRelease: ProviderRelease? = null

        override suspend fun activate(release: ProviderRelease): Boolean {
            activated = true
            lastRelease = release
            return true
        }
    }
}
