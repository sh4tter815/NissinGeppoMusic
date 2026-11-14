package com.example.nissingeppomusic.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.nissingeppomusic.service.PlaybackService
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

class MusicPlayerManager(context: Context) {
    private val appContext = context.applicationContext
    // プレイヤーとセッションは PlaybackCore が所有し、PlaybackService と共有する
    val exoPlayer: ExoPlayer = PlaybackCore.player(appContext)

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

    // 実際の再生順（シャッフル時はシャッフル後の順序）で並べたキュー。ラック表示用
    var playOrder by mutableStateOf<List<QueueItem>>(emptyList())
        private set
    var playOrderIndices by mutableStateOf<List<Int>>(emptyList())
        private set
    var playOrderCurrentPos by mutableStateOf(0)
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
                refreshPlayOrder()
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                refreshPlayOrder()
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
                // 通知にジャケットを出すため、小さくしたアートワークをメタデータへ埋め込む
                item.albumArt?.let { art ->
                    try {
                        val scale = 160f / maxOf(art.width, art.height).coerceAtLeast(1)
                        val small = if (scale < 1f) {
                            Bitmap.createScaledBitmap(
                                art,
                                (art.width * scale).toInt().coerceAtLeast(1),
                                (art.height * scale).toInt().coerceAtLeast(1),
                                true
                            )
                        } else {
                            art
                        }
                        val stream = ByteArrayOutputStream()
                        small.compress(Bitmap.CompressFormat.JPEG, 75, stream)
                        metadata.setArtworkData(stream.toByteArray(), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    } catch (_: Exception) {
                    }
                }
                MediaItem.Builder()
                    .setUri(Uri.parse(item.uriString))
                    .setMediaId(item.uriString)
                    .setMediaMetadata(metadata.build())
                    .build()
            }
            exoPlayer.setMediaItems(mediaItems, start, 0L)
            exoPlayer.prepare()
            exoPlayer.play()
            // メディア通知を出すフォアグラウンドサービスを起動する
            try {
                ContextCompat.startForegroundService(
                    appContext,
                    Intent(appContext, PlaybackService::class.java)
                )
            } catch (_: Exception) {
            }
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
        refreshPlayOrder()
    }

    fun toggleShuffle() {
        shuffleEnabled = !shuffleEnabled
        exoPlayer.shuffleModeEnabled = shuffleEnabled
        prefManager.setShuffleEnabled(shuffleEnabled)
        refreshPlayOrder()
    }

    /**
     * 実際の再生順（シャッフル時はシャッフル後の順序）でキューを並べ直す。
     * プレイヤーのタイムラインを前後に辿って、現在曲を中心に再生順リストを作る。
     * 全曲リピート時は端で折り返す。
     */
    fun refreshPlayOrder() {
        if (queue.isEmpty()) {
            playOrder = emptyList()
            playOrderIndices = emptyList()
            playOrderCurrentPos = 0
            return
        }
        try {
            val timeline = exoPlayer.currentTimeline
            val currentIdx = exoPlayer.currentMediaItemIndex
            if (timeline.isEmpty || currentIdx !in queue.indices) {
                playOrder = queue
                playOrderIndices = queue.indices.toList()
                playOrderCurrentPos = currentIdx.coerceIn(0, queue.size - 1)
                return
            }
            val shuffle = shuffleEnabled
            // 全曲リピート時は列挙も折り返す（現在曲に戻ってきたら止めるので重複しない）
            val repeatForWalk = if (repeatMode == 1) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF

            val prevList = mutableListOf<Int>()
            var p = currentIdx
            while (prevList.size < queue.size - 1) {
                val prev = timeline.getPreviousWindowIndex(p, repeatForWalk, shuffle)
                if (prev == -1 || prev == currentIdx) break
                prevList.add(prev)
                p = prev
            }
            val nextList = mutableListOf<Int>()
            var n = currentIdx
            while (nextList.size < queue.size - 1) {
                val next = timeline.getNextWindowIndex(n, repeatForWalk, shuffle)
                if (next == -1 || next == currentIdx) break
                nextList.add(next)
                n = next
            }
            val order = prevList.reversed() + listOf(currentIdx) + nextList
            playOrderIndices = order
            playOrder = order.map { queue[it] }
            playOrderCurrentPos = prevList.size
        } catch (e: Exception) {
            playOrder = queue
            playOrderIndices = queue.indices.toList()
            playOrderCurrentPos = currentQueueIndex.coerceIn(0, queue.size - 1)
        }
    }

    // ラックで選んだ再生順ポジションへジャンプして再生する
    fun playAtQueuePosition(position: Int) {
        val mediaIdx = playOrderIndices.getOrNull(position) ?: return
        try {
            exoPlayer.seekTo(mediaIdx, 0L)
            exoPlayer.play()
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
        val base = "$format ・ 44.1kHz ・ 32bit"
        val kbps = computeBitrateKbps(filePath)
        return if (kbps > 0) "$base ・ ${kbps}kbps" else base
    }

    // ビットレート(kbps)を実ファイルから計算する。
    // メタデータにビットレートがあればそれを使い、なければ「ファイルサイズ×8÷再生時間」で求める
    private fun computeBitrateKbps(filePath: String): Int {
        if (filePath.isBlank()) return 0
        return try {
            val mmr = android.media.MediaMetadataRetriever()
            try {
                mmr.setDataSource(filePath)
                val direct = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
                if (direct != null && direct > 0) {
                    direct / 1000
                } else {
                    val durationMs = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    val sizeBytes = java.io.File(filePath).length()
                    if (durationMs > 0 && sizeBytes > 0) {
                        (sizeBytes * 8L * 1000L / durationMs / 1000L).toInt()
                    } else {
                        0
                    }
                }
            } finally {
                mmr.release()
            }
        } catch (e: Exception) {
            0
        }
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
        // プレイヤーとセッションは PlaybackCore（サービス側）が所有するため、ここでは解放しない
        stopProgressTicker()
        playerScope.cancel()
    }
}
