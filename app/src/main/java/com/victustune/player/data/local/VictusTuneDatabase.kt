package com.victustune.player.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.victustune.player.data.local.dao.DownloadedTrackDao
import com.victustune.player.data.local.dao.FavoriteDao
import com.victustune.player.data.local.dao.HistoryDao
import com.victustune.player.data.local.dao.PlaylistDao
import com.victustune.player.data.local.dao.SavedTrackDao
import com.victustune.player.data.local.dao.SearchHistoryDao
import com.victustune.player.data.local.dao.SyncedPlaylistDao
import com.victustune.player.data.local.entity.DownloadedTrackEntity
import com.victustune.player.data.local.entity.FavoriteEntity
import com.victustune.player.data.local.entity.HistoryEntity
import com.victustune.player.data.local.entity.PlaylistEntity
import com.victustune.player.data.local.entity.PlaylistSongCrossRef
import com.victustune.player.data.local.entity.SavedTrackEntity
import com.victustune.player.data.local.entity.SearchHistoryEntity
import com.victustune.player.data.local.entity.SyncedPlaylistEntity

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        FavoriteEntity::class,
        HistoryEntity::class,
        SearchHistoryEntity::class,
        SyncedPlaylistEntity::class,
        DownloadedTrackEntity::class,
        SavedTrackEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class VictusTuneDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun syncedPlaylistDao(): SyncedPlaylistDao
    abstract fun downloadedTrackDao(): DownloadedTrackDao
    abstract fun savedTrackDao(): SavedTrackDao

    companion object {
        @Volatile
        private var INSTANCE: VictusTuneDatabase? = null

        fun getInstance(context: Context): VictusTuneDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VictusTuneDatabase::class.java,
                    "victustune_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
