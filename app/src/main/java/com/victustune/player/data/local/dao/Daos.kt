package com.victustune.player.data.local.dao

import androidx.room.*
import com.victustune.player.data.local.entity.FavoriteEntity
import com.victustune.player.data.local.entity.HistoryEntity
import com.victustune.player.data.local.entity.PlaylistEntity
import com.victustune.player.data.local.entity.PlaylistSongCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :newName WHERE id = :id")
    suspend fun renamePlaylist(id: Long, newName: String)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongsToPlaylist(crossRefs: List<PlaylistSongCrossRef>)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY orderIndex ASC, addedAt ASC")
    fun getSongIdsForPlaylist(playlistId: Long): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    fun getTrackCountForPlaylist(playlistId: Long): Flow<Int>

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: Long)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT songId FROM favorites ORDER BY addedAt DESC")
    fun getAllFavoriteSongIds(): Flow<List<Long>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    fun isFavorite(songId: Long): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun removeFavorite(songId: Long)
}

@Dao
interface HistoryDao {
    @Query("SELECT songId FROM play_history ORDER BY playedAt DESC LIMIT :limit")
    fun getRecentSongIds(limit: Int = 100): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: HistoryEntity)

    @Query("DELETE FROM play_history")
    suspend fun clearHistory()
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT query FROM search_history ORDER BY timestamp DESC LIMIT 20")
    fun getRecentSearches(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(item: com.victustune.player.data.local.entity.SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearch(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()
}

@Dao
interface SyncedPlaylistDao {
    @Query("SELECT * FROM synced_playlists ORDER BY isLikedMusic DESC, syncedAt DESC")
    fun getSyncedPlaylists(): Flow<List<com.victustune.player.data.local.entity.SyncedPlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<com.victustune.player.data.local.entity.SyncedPlaylistEntity>)

    @Query("DELETE FROM synced_playlists")
    suspend fun clearAll()
}

@Dao
interface DownloadedTrackDao {
    @Query("SELECT * FROM downloaded_tracks ORDER BY downloadedAt DESC")
    fun getAllDownloadedTracks(): Flow<List<com.victustune.player.data.local.entity.DownloadedTrackEntity>>

    @Query("SELECT * FROM downloaded_tracks WHERE status = 'COMPLETED' ORDER BY downloadedAt DESC")
    fun getCompletedTracks(): Flow<List<com.victustune.player.data.local.entity.DownloadedTrackEntity>>

    @Query("SELECT * FROM downloaded_tracks WHERE videoId = :videoId LIMIT 1")
    fun getTrack(videoId: String): Flow<com.victustune.player.data.local.entity.DownloadedTrackEntity?>

    @Query("SELECT * FROM downloaded_tracks WHERE videoId = :videoId LIMIT 1")
    suspend fun getTrackSync(videoId: String): com.victustune.player.data.local.entity.DownloadedTrackEntity?

    @Query("SELECT * FROM downloaded_tracks WHERE songId = :songId LIMIT 1")
    suspend fun getTrackBySongId(songId: Long): com.victustune.player.data.local.entity.DownloadedTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(track: com.victustune.player.data.local.entity.DownloadedTrackEntity)

    @Query("UPDATE downloaded_tracks SET status = :status, progress = :progress WHERE videoId = :videoId")
    suspend fun updateProgress(videoId: String, status: String, progress: Float)

    @Query("UPDATE downloaded_tracks SET status = :status, progress = :progress, sizeBytes = :sizeBytes, downloadedAt = :downloadedAt WHERE videoId = :videoId")
    suspend fun updateCompleted(videoId: String, status: String, progress: Float, sizeBytes: Long, downloadedAt: Long = System.currentTimeMillis())

    @Query("UPDATE downloaded_tracks SET artworkPath = :artworkPath WHERE videoId = :videoId")
    suspend fun updateArtworkPath(videoId: String, artworkPath: String)

    @Query("DELETE FROM downloaded_tracks WHERE videoId = :videoId")
    suspend fun deleteTrack(videoId: String)

    @Query("DELETE FROM downloaded_tracks")
    suspend fun deleteAll()

    @Query("SELECT SUM(sizeBytes) FROM downloaded_tracks WHERE status = 'COMPLETED'")
    fun getTotalDownloadSize(): Flow<Long?>
}

@Dao
interface SavedTrackDao {
    @Query("SELECT * FROM saved_tracks")
    fun getAllSavedTracks(): Flow<List<com.victustune.player.data.local.entity.SavedTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(track: com.victustune.player.data.local.entity.SavedTrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(tracks: List<com.victustune.player.data.local.entity.SavedTrackEntity>)
}
