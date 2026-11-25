package com.victustune.player.data.repository

import com.victustune.player.data.local.dao.DownloadedTrackDao
import com.victustune.player.data.local.dao.FavoriteDao
import com.victustune.player.data.local.dao.HistoryDao
import com.victustune.player.data.local.dao.PlaylistDao
import com.victustune.player.data.local.dao.SavedTrackDao
import com.victustune.player.data.local.entity.FavoriteEntity
import com.victustune.player.data.local.entity.HistoryEntity
import com.victustune.player.data.local.entity.PlaylistEntity
import com.victustune.player.data.local.entity.PlaylistSongCrossRef
import com.victustune.player.data.local.entity.SavedTrackEntity
import com.victustune.player.data.model.Playlist
import com.victustune.player.data.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class PlaylistRepository(
    private val playlistDao: PlaylistDao,
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val audioRepository: AudioRepository,
    private val downloadedTrackDao: DownloadedTrackDao,
    private val savedTrackDao: SavedTrackDao
) {

    fun getPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getAllPlaylists().combine(audioRepository.songsFlow) { entities, _ ->
            entities.map { entity ->
                Playlist(
                    id = entity.id,
                    name = entity.name,
                    createdAt = entity.createdAt,
                    trackCount = 0
                )
            }
        }
    }

    suspend fun createPlaylist(name: String): Long {
        return playlistDao.insertPlaylist(PlaylistEntity(name = name))
    }

    suspend fun renamePlaylist(id: Long, newName: String) {
        playlistDao.renamePlaylist(id, newName)
    }

    suspend fun deletePlaylist(id: Long) {
        playlistDao.clearPlaylistSongs(id)
        playlistDao.deletePlaylistById(id)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        val currentIds = playlistDao.getSongIdsForPlaylist(playlistId).first()
        playlistDao.addSongToPlaylist(
            PlaylistSongCrossRef(
                playlistId = playlistId,
                songId = songId,
                orderIndex = currentIds.size
            )
        )
    }

    suspend fun addSongToPlaylist(playlistId: Long, song: Song) {
        if (song.isRemote) {
            savedTrackDao.insertOrUpdate(SavedTrackEntity.fromSong(song))
        }
        addSongToPlaylist(playlistId, song.id)
    }

    suspend fun createPlaylistWithSongs(name: String, songs: List<Song>): Playlist {
        val createdAt = System.currentTimeMillis()
        val playlistId = playlistDao.insertPlaylist(
            PlaylistEntity(name = name, createdAt = createdAt)
        )
        val uniqueSongs = songs.distinctBy { it.id }
        val remoteEntities = uniqueSongs.filter { it.isRemote }.map { SavedTrackEntity.fromSong(it) }
        if (remoteEntities.isNotEmpty()) {
            savedTrackDao.insertOrUpdateAll(remoteEntities)
        }
        val crossRefs = uniqueSongs.mapIndexed { index, song ->
            PlaylistSongCrossRef(
                playlistId = playlistId,
                songId = song.id,
                orderIndex = index,
                addedAt = createdAt
            )
        }
        if (crossRefs.isNotEmpty()) {
            playlistDao.addSongsToPlaylist(crossRefs)
        }
        return Playlist(
            id = playlistId,
            name = name,
            createdAt = createdAt,
            trackCount = uniqueSongs.size
        )
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        playlistDao.removeSongFromPlaylist(playlistId, songId)
    }

    fun getSongsForPlaylist(playlistId: Long): Flow<List<Song>> {
        return combine(
            playlistDao.getSongIdsForPlaylist(playlistId),
            audioRepository.songsFlow,
            downloadedTrackDao.getAllDownloadedTracks(),
            favoriteDao.getAllFavorites(),
            savedTrackDao.getAllSavedTracks()
        ) { ids, localSongs, downloadedEntities, favEntities, savedEntities ->
            val localMap = localSongs.associateBy { it.id }
            val downloadedMap = downloadedEntities.associateBy { it.songId }
            val favMap = favEntities.associateBy { it.songId }
            val savedMap = savedEntities.associateBy { it.songId }
            val downloadedVideoIds = downloadedEntities
                .filter { it.status == "COMPLETED" }
                .map { it.videoId }
                .toSet()

            ids.mapNotNull { id ->
                val baseSong = localMap[id]
                    ?: downloadedMap[id]?.toSong()
                    ?: favMap[id]?.toSong()
                    ?: savedMap[id]?.toSong()
                baseSong?.let { song ->
                    val isFav = favMap.containsKey(song.id)
                    val isDl = song.remoteVideoId?.let { downloadedVideoIds.contains(it) } ?: song.isDownloaded
                    song.copy(isFavorite = isFav || song.isFavorite, isDownloaded = isDl)
                }
            }
        }
    }

    fun getFavoriteSongs(): Flow<List<Song>> {
        return combine(
            favoriteDao.getAllFavorites(),
            audioRepository.songsFlow,
            downloadedTrackDao.getAllDownloadedTracks()
        ) { favEntities, localSongs, downloadedEntities ->
            val localMap = localSongs.associateBy { it.id }
            val downloadedVideoIds = downloadedEntities.filter { it.status == "COMPLETED" }.map { it.videoId }.toSet()
            favEntities.map { entity ->
                val baseSong = localMap[entity.songId]?.copy(isFavorite = true) ?: entity.toSong()
                val isDl = baseSong.remoteVideoId?.let { downloadedVideoIds.contains(it) } ?: baseSong.isDownloaded
                baseSong.copy(isDownloaded = isDl)
            }
        }
    }

    fun getFavoriteSongIds(): Flow<Set<Long>> {
        return favoriteDao.getAllFavoriteSongIds().map { it.toSet() }
    }

    suspend fun isFavorite(songId: Long): Boolean {
        return favoriteDao.isFavorite(songId).first()
    }

    suspend fun toggleFavorite(song: Song) {
        val isFav = favoriteDao.isFavorite(song.id).first()
        if (isFav) {
            favoriteDao.removeFavorite(song.id)
        } else {
            favoriteDao.addFavorite(FavoriteEntity.fromSong(song))
        }
    }

    suspend fun recordHistory(songId: Long) {
        historyDao.insertHistory(HistoryEntity(songId = songId))
    }

    fun getRecentSongs(): Flow<List<Song>> {
        return historyDao.getRecentSongIds(50).combine(audioRepository.songsFlow) { ids, songs ->
            val songMap = songs.associateBy { it.id }
            ids.mapNotNull { songMap[it] }.distinctBy { it.id }
        }
    }
}
