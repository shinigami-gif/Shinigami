package ani.dantotsu.connections

import ani.dantotsu.R
import ani.dantotsu.App
import ani.dantotsu.database.AnimeStateDatabase
import ani.dantotsu.database.AnimeStateRepository
import ani.dantotsu.Refresh
import ani.dantotsu.currContext
import ani.dantotsu.media.Media
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ani.dantotsu.connections.shinigami.ShinigamiLibraryClient
import ani.dantotsu.connections.shinigami.ShinigamiSessionStore

fun updateProgress(media: Media, number: String) {
    val incognito: Boolean = PrefManager.getVal(PrefName.Incognito)

    val progressInt = ani.dantotsu.media.MediaNameAdapter.findEpisodeNumber(number)?.toInt()
        ?: number.toFloatOrNull()?.toInt()
        ?: return

    if (incognito) {
        toast("Sneaky sneaky :3")
        return
    }

    CoroutineScope(Dispatchers.IO).launch {
        val token = currContext()?.let { ShinigamiSessionStore(it).getToken() }
        if (token == null) {
            toast(currContext()?.getString(R.string.login_anilist_account))
            return@launch
        }
        if (progressInt > (media.userProgress ?: -1)) {
            ShinigamiLibraryClient().updateProgress(token, media.id.toLong(), progressInt)
            AnimeStateRepository(AnimeStateDatabase.get(App.instance!!)).updateProgress(media.id, progressInt)
            media.userProgress = progressInt
            toast(currContext()?.getString(R.string.setting_progress, progressInt))
            Refresh.all()
        }
    }
}
