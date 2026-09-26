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
import ani.dantotsu.download.DownloadsManager
import ani.dantotsu.download.findValidName
import ani.dantotsu.media.MediaType
import ani.dantotsu.connections.shinigami.ShinigamiLibraryClient
import ani.dantotsu.connections.shinigami.ShinigamiSessionStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

fun updateProgress(media: Media, number: String) {
    val incognito: Boolean = PrefManager.getVal(PrefName.Incognito)

    val autoDelete = PrefManager.getCustomVal("auto_delete_downloads", false)
    if (autoDelete) {
        val type = if (media.anime != null) MediaType.ANIME else if (media.format == "NOVEL") MediaType.NOVEL else MediaType.MANGA
        val downloadsManager = Injekt.get<DownloadsManager>()
        val downloadedTypes = when (type) {
            MediaType.ANIME -> downloadsManager.animeDownloadedTypes
            MediaType.MANGA -> downloadsManager.mangaDownloadedTypes
            MediaType.NOVEL -> downloadsManager.novelDownloadedTypes
        }
        val targetNum = ani.dantotsu.media.MediaNameAdapter.findChapterNumber(number)
        val downloadedType = downloadedTypes.find {
            it.titleName == media.mainName().findValidName() &&
                (it.chapterName == number.findValidName() ||
                    (targetNum != null && ani.dantotsu.media.MediaNameAdapter.findChapterNumber(it.chapterName) == targetNum))
        }
        if (downloadedType != null) downloadsManager.removeDownload(downloadedType, toast = false) {}
    }

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
