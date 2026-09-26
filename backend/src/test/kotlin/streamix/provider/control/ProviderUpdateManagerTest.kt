package streamix.provider.control

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderUpdateManagerTest {
    @Test
    fun update_requires_verified_candidate_before_activation() = runTest {
        val controlPlane = ProviderControlPlane(listOf("otakudesu"))
        val candidate = ProviderUpdateCandidate(
            providerId = "otakudesu",
            currentVersion = "1.2.3",
            currentCommit = "old",
            targetVersion = "1.2.4",
            targetCommit = "new",
            upstream = "Hatsune/AnimeX"
        )
        val executor = FakeExecutor(candidate)
        val manager = ProviderUpdateManager(controlPlane, executor)

        manager.check("otakudesu")
        manager.enqueue("otakudesu")
        val ready = manager.update("otakudesu")

        assertEquals(ProviderUpdateStatus.READY_TO_DEPLOY, ready.status)
        assertEquals("new", executor.executedCommit)

        val active = manager.activate("otakudesu")

        assertEquals(ProviderUpdateStatus.ACTIVE, active.status)
        assertEquals("1.2.4", controlPlane.activeVersion("otakudesu")?.version)
        assertEquals("new", controlPlane.activeVersion("otakudesu")?.upstreamCommit)
        assertTrue(executor.activated)
    }



    @Test
    fun rollback_updates_control_plane_to_target_release() = runTest {
        val controlPlane = ProviderControlPlane(listOf("otakudesu"))
        val candidate = ProviderUpdateCandidate(
            providerId = "otakudesu",
            currentVersion = "1.2.4",
            currentCommit = "new",
            targetVersion = "1.2.5",
            targetCommit = "newer",
            upstream = "Hatsune/AnimeX"
        )
        val executor = FakeExecutor(candidate)
        val manager = ProviderUpdateManager(controlPlane, executor)

        controlPlane.activateVersion("otakudesu", "1.2.4", "Hatsune/AnimeX", "new")
        manager.check("otakudesu")
        manager.enqueue("otakudesu")
        manager.update("otakudesu")

        val rolledBack = manager.rollback("otakudesu", "1.2.4")

        assertEquals(ProviderUpdateStatus.ROLLED_BACK, rolledBack.status)
        assertEquals("1.2.4", controlPlane.activeVersion("otakudesu")?.version)
        assertEquals("old", controlPlane.activeVersion("otakudesu")?.upstreamCommit)
    }

    private class FakeExecutor(
        private val candidate: ProviderUpdateCandidate
    ) : ProviderUpdateExecutor {
        var executedCommit: String? = null
        var activated = false

        override suspend fun detect(
            providerId: String,
            activeVersion: ProviderVersion?
        ): ProviderUpdateCandidate? = candidate

        override suspend fun execute(
            candidate: ProviderUpdateCandidate,
            onStep: suspend (ProviderUpdateStep) -> Unit
        ): ProviderUpdateResult {
            executedCommit = candidate.targetCommit
            onStep(
                ProviderUpdateStep(
                    id = "contract",
                    name = "Contract Test",
                    status = ProviderUpdateStepStatus.PASSED
                )
            )
            return ProviderUpdateResult(
                success = true,
                activatedVersion = candidate.targetVersion,
                activatedCommit = candidate.targetCommit
            )
        }

        override suspend fun activate(candidate: ProviderUpdateCandidate): Boolean {
            activated = true
            return true
        }

        override suspend fun rollback(
            providerId: String,
            targetVersion: String
        ): ProviderUpdateResult = ProviderUpdateResult(
            success = true,
            activatedVersion = targetVersion,
            activatedCommit = "old"
        )
    }
}
