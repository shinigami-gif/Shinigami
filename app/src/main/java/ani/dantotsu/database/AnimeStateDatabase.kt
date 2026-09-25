package ani.dantotsu.database

import android.content.Context
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDatabaseType
import com.eygraber.sqldelight.androidx.driver.AndroidxSqliteDriver

object AnimeStateDatabase {
    @Volatile
    private var database: ShinigamiDatabase? = null

    fun get(context: Context): ShinigamiDatabase =
        database ?: synchronized(this) {
            database ?: ShinigamiDatabase(
                AndroidxSqliteDriver(
                    driver = BundledSQLiteDriver(),
                    databaseType = AndroidxSqliteDatabaseType.FileProvider { context.getDatabasePath("shinigami.db").path },
                    schema = ShinigamiDatabase.Schema,
                ),
            ).also { database = it }
        }
}
