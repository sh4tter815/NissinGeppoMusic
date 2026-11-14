package com.example.nissingeppomusic.service

import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.nissingeppomusic.data.PlaybackCore

/**
 * 再生をフォアグラウンドサービスとして維持し、メディア通知を出すためのサービス。
 * プレイヤー本体は PlaybackCore が所有し、画面側と共有する。
 */
class PlaybackService : MediaSessionService() {

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession =
        PlaybackCore.session(this)

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = PlaybackCore.sessionOrNull()?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }
}
