package com.example.nissingeppomusic.service

import android.app.Notification
import android.graphics.Bitmap
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LauncherMediaListenerService : NotificationListenerService() {
    // MediaSessionManager.getActiveSessions 用の通知リスナーサービス。
    // 通知バッジ用に、アプリごとの通知数も数える（常駐通知とグループのまとめ通知は数えない）
    // あわせて、メディア通知に載っているジャケット画像も保持する
    // （セッションのメタデータに画像を渡さないプレイヤー向けの予備の取り口）

    companion object {
        private val _badgeCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
        val badgeCounts: StateFlow<Map<String, Int>> = _badgeCounts

        // 直近のメディア通知のジャケット画像と、その通知のパッケージ名・タイトル
        @Volatile
        var mediaArtBitmap: Bitmap? = null

        @Volatile
        var mediaArtPackage: String? = null

        @Volatile
        var mediaArtTitle: String? = null
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            for (sbn in activeNotifications ?: emptyArray()) {
                captureMediaArt(sbn)
            }
        } catch (_: Exception) {
        }
        recompute()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        captureMediaArt(sbn)
        recompute()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        try {
            if (sbn != null && sbn.packageName == mediaArtPackage && isMediaNotification(sbn.notification)) {
                mediaArtBitmap = null
                mediaArtPackage = null
                mediaArtTitle = null
            }
        } catch (_: Exception) {
        }
        recompute()
    }

    private fun isMediaNotification(n: Notification?): Boolean {
        if (n == null) return false
        val extras = n.extras ?: return false
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return true
        val template = extras.getString(Notification.EXTRA_TEMPLATE) ?: ""
        if (template.contains("MediaStyle")) return true
        return n.category == Notification.CATEGORY_TRANSPORT
    }

    private fun captureMediaArt(sbn: StatusBarNotification?) {
        try {
            val n = sbn?.notification ?: return
            if (!isMediaNotification(n)) return
            val bmp = extractArt(n) ?: return
            mediaArtBitmap = bmp
            mediaArtPackage = sbn.packageName
            mediaArtTitle = n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        } catch (_: Exception) {
        }
    }

    private fun extractArt(n: Notification): Bitmap? {
        // BigPictureStyle 系は EXTRA_PICTURE に画像が入る
        try {
            @Suppress("DEPRECATION")
            val pic = n.extras?.getParcelable<Bitmap>(Notification.EXTRA_PICTURE)
            if (pic != null) return pic
        } catch (_: Exception) {
        }
        // メディア通知のジャケットはラージアイコンに入っていることが多い
        try {
            val icon = n.getLargeIcon() ?: return null
            val d = icon.loadDrawable(this) ?: return null
            val w = d.intrinsicWidth
            val h = d.intrinsicHeight
            if (w <= 0 || h <= 0) return null
            val scale = if (w > 512 || h > 512) 512f / maxOf(w, h) else 1f
            val bw = (w * scale).toInt().coerceAtLeast(1)
            val bh = (h * scale).toInt().coerceAtLeast(1)
            val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(b)
            d.setBounds(0, 0, bw, bh)
            d.draw(canvas)
            return b
        } catch (_: Exception) {
            return null
        }
    }

    private fun recompute() {
        try {
            val counts = mutableMapOf<String, Int>()
            for (sbn in activeNotifications ?: emptyArray()) {
                val n = sbn.notification ?: continue
                val flags = n.flags
                if (flags and Notification.FLAG_ONGOING_EVENT != 0) continue
                if (flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
                if (sbn.packageName == packageName) continue
                counts[sbn.packageName] = (counts[sbn.packageName] ?: 0) + 1
            }
            _badgeCounts.value = counts
        } catch (_: Exception) {
        }
    }
}
