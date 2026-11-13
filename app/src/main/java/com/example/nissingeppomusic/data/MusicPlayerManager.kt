package com.example.nissingeppomusic.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.*
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import kotlinx.coroutines.*

class MusicPlayerManager(context: Context) {
    private val appContext = context.applicationContext
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(appContext).build()
    private var mediaSession: MediaSession? = try {
        MediaSession.Builder(appContext, exoPlayer).build()
    } catch (_: Exception) {
        null
    }

    var isPlaying by mutableStateOf(false)
        private set
    var currentSongUri by mutableStateOf<String?>(null)
        private set
    var currentTitle by mutableStateOf("")
        private set
    var currentArtist by mutableStateOf("")
        private set
    var currentAlbum by mutableStateOf("")
        private set
    var currentAlbumArt by mutableStateOf<Bitmap?>(null)
        private set
    var currentFileInfo by mutableStateOf("FLAC ・ 44.1kHz ・ 32bit")
        private set

    var currentPosition by mutableStateOf(0L)
        private set
    var duration by mutableStateOf(0L)
        private set

    private val prefManager = AppPreferenceManager(appContext)

    // 再生キュー（曲をタップしたとき、その一覧をそのままキューとして再生する）
    data class QueueItem(
        val uriString: String,
        val title: String,
        val artist: String,
        val album: String,
        val albumArt: Bitmap?,
        val filePath: String
    )

    var queue: List<QueueItem> = emptyList()
        private set
    var currentQueueIndex by mutableStateOf(-1)
        private set
    // リピート: 0=オフ, 1=全曲リピート, 2=1曲リピート
    var repeatMode by mutableStateOf(0)
        private set
    var shuffleEnabled by mutableStateOf(false)
        private set

    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                isPlaying = isPlayingChanged
                if (isPlayingChanged) {
                    startProgressTicker()
                } else {
                    stopProgressTicker()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                    stopProgressTicker()
                    currentPosition = 0L
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // 曲が切り替わったら（自動送り・シャッフル含む）表示情報をキュー側に同期する
                val item = mediaItem ?: return
                val idx = queue.indexOfFirst { it.uriString == item.mediaId }
                if (idx >= 0) {
                    applyQueueItem(queue[idx], idx)
                }
            }
        })

        // リピート／シャッフルの保存値を復元
        repeatMode = prefManager.getRepeatMode().coerceIn(0, 2)
        exoPlayer.repeatMode = when (repeatMode) {
            1 -> Player.REPEAT_MODE_ALL
            2 -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        shuffleEnabled = prefManager.isShuffleEnabled()
        exoPlayer.shuffleModeEnabled = shuffleEnabled
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = playerScope.launch {
            while (true) {
                if (exoPlayer.isPlaying) {
                    currentPosition = exoPlayer.currentPosition
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }
                delay(200)
            }
        }
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun applyQueueItem(item: QueueItem, index: Int) {
        currentQueueIndex = index
        currentSongUri = item.uriString
        currentTitle = item.title
        currentArtist = item.artist
        currentAlbum = item.album
        currentAlbumArt = item.albumArt
        currentFileInfo = formatAudioFileInfo(item.filePath, item.uriString)
        currentPosition = 0L
        duration = exoPlayer.duration.coerceAtLeast(1000L)
    }

    fun playQueue(items: List<QueueItem>, startIndex: Int) {
        if (items.isEmpty()) return
        try {
            queue = items
            val start = startIndex.coerceIn(0, items.size - 1)
            val mediaItems = items.map { item ->
                val metadata = MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setArtist(item.artist)
                    .setAlbumTitle(item.album)
                    .build()
                MediaItem.Builder()
                    .setUri(Uri.parse(item.uriString))
                    .setMediaId(item.uriString)
                    .setMediaMetadata(metadata)
                    .build()
            }
            exoPlayer.setMediaItems(mediaItems, start, 0L)
            exoPlayer.prepare()
            exoPlayer.play()
            applyQueueItem(items[start], start)
            isPlaying = true
        } catch (_: Exception) {}
    }

    fun playSong(uriString: String, title: String, artist: String = "", album: String = "", albumArt: Bitmap? = null, filePath: String = "") {
        playQueue(listOf(QueueItem(uriString, title, artist, album, albumArt, filePath)), 0)
    }

    // リピート切替: オフ → 全曲 → 1曲 → オフ…
    fun cycleRepeatMode() {
        repeatMode = (repeatMode + 1) % 3
        exoPlayer.repeatMode = when (repeatMode) {
            1 -> Player.REPEAT_MODE_ALL
            2 -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        prefManager.setRepeatMode(repeatMode)
    }

    fun toggleShuffle() {
        shuffleEnabled = !shuffleEnabled
        exoPlayer.shuffleModeEnabled = shuffleEnabled
        prefManager.setShuffleEnabled(shuffleEnabled)
    }

    private fun formatAudioFileInfo(filePath: String, uriString: String): String {
        val ext = filePath.substringAfterLast('.', "").uppercase()
        val format = when (ext) {
            "FLAC" -> "FLAC"
            "MP3" -> "MP3"
            "WAV" -> "WAV"
            "AAC", "M4A" -> "AAC"
            "OGG" -> "OGG"
            else -> if (uriString.contains("flac", true)) "FLAC" else "FLAC"
        }
        return "$format ・ 44.1kHz ・ 32bit"
    }

    fun seekTo(positionMs: Long) {
        try {
            exoPlayer.seekTo(positionMs)
            currentPosition = positionMs
        } catch (_: Exception) {}
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_IDLE) {
                exoPlayer.prepare()
            }
            exoPlayer.play()
        }
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun resume() {
        if (currentSongUri != null) {
            exoPlayer.play()
        }
    }

    fun stop() {
        exoPlayer.stop()
        isPlaying = false
        stopProgressTicker()
    }

    fun release() {
        stopProgressTicker()
        playerScope.cancel()
        try {
            mediaSession?.release()
            mediaSession = null
        } catch (_: Exception) {}
        exoPlayer.release()
    }
}
