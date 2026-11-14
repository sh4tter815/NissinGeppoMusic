package com.example.nissingeppomusic.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.example.nissingeppomusic.MainActivity

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
        // 通知やランチャーのアートワークタップから開く先として、起動中の画面自身を指定する。
        // singleTask と合わせて、タップしても新規セッションにならず再生中の画面が前面に出る
        val sessionActivityIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val sessionActivity = PendingIntent.getActivity(
            appContext,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        mediaSession = MediaSession.Builder(appContext, player)
            .setSessionActivity(sessionActivity)
            .build()
    }
}
