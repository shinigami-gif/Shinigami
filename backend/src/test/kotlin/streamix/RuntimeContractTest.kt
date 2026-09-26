package streamix

import kotlin.test.Test
import kotlin.test.assertEquals
import streamix.runtime.NativeProviderHost

class RuntimeContractTest {
    @Test
    fun nativeProviderContractContainsAllMigratedProviders() {
        assertEquals(
            setOf(
                "alqanime", "animasu", "animesail", "animein", "animexin",
                "anoboy", "kuramanime", "kuronime", "nimegami",
                "nontonanimeid", "otakudesu", "samehadaku", "winbu"
            ),
            NativeProviderHost.runtimes().map { it.providerId }.toSet()
        )
    }
}
