package com.example.nissingeppomusic.data

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession

/**
 * プレイヤーとメディアセッションの所有者。
 * 画面側（MusicPlayerManager）と PlaybackService が同じインスタンスを共有する。
 */
object PlaybackCore {
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    @Synchronized
    fun player(context: Context): ExoPlayer {
        ensure(context)
        return exoPlayer!!
    }

    @Synchronized
    fun session(context: Context): MediaSession {
        ensure(context)
        return mediaSession!!
    }

    fun sessionOrNull(): MediaSession? = mediaSession

    private fun ensure(context: Context) {
        if (exoPlayer != null) return
        val appContext = context.applicationContext
        // オーディオフォーカスはプレイヤーに任せる:
        // 自分が再生を始めるとフォーカスを取り、再生中の他アプリが止まる。
        // 逆に電話や他アプリが割り込むと、自動で一時停止／音量低下する
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val player = ExoPlayer.Builder(appContext)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        exoPlayer = player
        mediaSession = MediaSession.Builder(appContext, player).build()
    }
}
