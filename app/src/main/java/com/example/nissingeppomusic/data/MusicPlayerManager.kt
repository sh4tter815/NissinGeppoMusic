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
        })
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

    fun playSong(uriString: String, title: String, artist: String = "", album: String = "", albumArt: Bitmap? = null, filePath: String = "") {
        try {
            currentSongUri = uriString
            currentTitle = title
            currentArtist = artist
            currentAlbum = album
            currentAlbumArt = albumArt
            currentFileInfo = formatAudioFileInfo(filePath, uriString)

            val metadata = MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(uriString))
                .setMediaMetadata(metadata)
                .build()

            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
            isPlaying = true
            duration = exoPlayer.duration.coerceAtLeast(1000L)
        } catch (_: Exception) {}
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
