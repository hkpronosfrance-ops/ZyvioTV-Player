package fr.zyviotv.player.data.store

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        GenerationEntity::class,
        CategoryEntity::class,
        LiveChannelEntity::class,
        MovieEntity::class,
        SeriesEntity::class,
        SeriesDetailEntity::class,
        EpisodeEntity::class,
        IdAliasEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class CatalogStoreDatabase : RoomDatabase() {
    abstract fun dao(): CatalogStoreDao

    companion object {
        /** App-private database file (no backup: `allowBackup="false"`). */
        const val FILE_NAME = "catalog-store.db"

        @Volatile private var instance: CatalogStoreDatabase? = null

        fun get(context: Context): CatalogStoreDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CatalogStoreDatabase::class.java,
                    FILE_NAME,
                ).build().also { instance = it }
            }
    }
}
