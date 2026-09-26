package streamix.runtime

import com.baseprovider.streamix.StreamixRuntime as BaseStreamixRuntime
import com.baseprovider.streamix.jvm.JvmStreamixCloudflareResolver
import com.baseprovider.streamix.jvm.JvmStreamixCrypto
import com.baseprovider.streamix.jvm.JvmStreamixDispatchers
import com.baseprovider.streamix.jvm.JvmStreamixHttp
import com.baseprovider.streamix.jvm.JvmStreamixJs
import com.baseprovider.streamix.jvm.JvmStreamixJson
import com.baseprovider.streamix.jvm.JvmStreamixWebResolver
import com.alqanime.Alqanime
import com.animasu.AnimasuProvider
import com.animesail.AnimeSailProvider
import com.animein.AnimeinProvider
import com.Animexin.AnimexinProvider
import com.anoboy.AnoboyProvider
import com.hexated.KuramanimeProvider
import com.kuronime.KuronimeProvider
import com.hexated.Nimegami
import com.nontonanimeid.NontonAnimeIDProvider
import com.otakudesu.OtakudesuProvider
import com.samehadaku.SamehadakuProvider
import com.winbu.WinbuProvider
import streamix.core.StreamixProvider
import streamix.provider.NativeStreamixProviderRuntime
import streamix.provider.ProviderRuntime

object NativeProviderHost {
    private val providers: List<StreamixProvider> = listOf(
        Alqanime(), AnimasuProvider(), AnimeSailProvider(), AnimeinProvider(),
        AnimexinProvider(), AnoboyProvider(), KuramanimeProvider(), KuronimeProvider(),
        Nimegami(), NontonAnimeIDProvider(), OtakudesuProvider(),
        SamehadakuProvider(), WinbuProvider()
    )

    @Synchronized
    fun runtimes(): List<ProviderRuntime> {
        installRuntime()
        return providers.map { NativeStreamixProviderRuntime(it) }
    }

    private fun installRuntime() {
        BaseStreamixRuntime.install(
            http = JvmStreamixHttp(),
            json = JvmStreamixJson(),
            crypto = JvmStreamixCrypto(),
            js = JvmStreamixJs(),
            dispatchers = JvmStreamixDispatchers,
            webResolver = JvmStreamixWebResolver(),
            cloudflareResolver = JvmStreamixCloudflareResolver()
        )
    }
}
