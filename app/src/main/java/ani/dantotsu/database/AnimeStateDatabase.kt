package ani.dantotsu.database

import android.content.Context
import app.cash.sqldelight.driver.androidx.AndroidSqliteDriver

object AnimeStateDatabase {
    @Volatile
    private var database: ShinigamiDatabase? = null

    fun get(context: Context): ShinigamiDatabase =
        database ?: synchronized(this) {
            database ?: ShinigamiDatabase(
                AndroidSqliteDriver(
                    schema = ShinigamiDatabase.Schema,
                    context = context.applicationContext,
                    name = "shinigami.db",
                ),
            ).also { database = it }
        }
}
