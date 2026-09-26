package ani.dantotsu.settings.saving.internal

import android.content.Context
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName
import ani.dantotsu.util.Logger

class Compat {
    companion object {
        fun importOldPrefs(context: Context) {
            migrateReposToStores(context)
            if (PrefManager.getVal(PrefName.HasUpdatedPrefs)) return
            PrefManager.setVal(PrefName.HasUpdatedPrefs, true)
        }

        private fun migrateReposToStores(context: Context) {
            val migratedKey = "has_migrated_repos_to_stores_v2"
            val migratedPrefs = context.getSharedPreferences("migration_prefs", Context.MODE_PRIVATE)
            if (migratedPrefs.getBoolean(migratedKey, false)) return

            fun migrateSet(prefName: PrefName) {
                try {
                    val current = PrefManager.getVal<Set<String>>(prefName)
                    val migrated = current.map { url ->
                        var newUrl = url
                        if (newUrl.contains("github.com") && newUrl.contains("blob")) {
                            newUrl = newUrl.replace("github.com", "raw.githubusercontent.com")
                                .replace("/blob/", "/")
                        }

                        newUrl
                    }.toSet()
                    PrefManager.setVal(prefName, migrated)
                } catch (e: Exception) {
                    Logger.log("Failed to migrate repos for $prefName: $e")
                }
            }

            migrateSet(PrefName.AnimeExtensionRepos)

            migratedPrefs.edit().putBoolean(migratedKey, true).apply()
        }
    }
}
