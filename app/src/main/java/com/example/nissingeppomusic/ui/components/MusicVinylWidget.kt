package com.example.nissingeppomusic.ui.components

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.graphics.Bitmap
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nissingeppomusic.data.AppPreferenceManager
import com.example.nissingeppomusic.service.LauncherMediaListenerService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlin.math.sign

fun isNotificationServiceEnabled(context: Context): Boolean {
    val packageName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat?.contains(packageName) == true
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MusicVinylWidget(
    modifier: Modifier = Modifier,
    discCenterXDp: Float,
    discCenterYDp: Float,
    discRadiusDp: Float = 165f,
    accentColor: Color = Color(0xFFE53935),
    textStartAngle: Float = -90f,
    artworkTilt3d: Boolean = false,
    onRackVisibilityChanged: (Boolean) -> Unit = {},
    onThemeSettingsRequest: () -> Unit = {},
    dopagaki: Boolean = false,
    rainbowGlow: Boolean = false,
    tonearmScaleOverride: Float? = null,
    spinFrozen: Boolean = false
) {
    val context = LocalContext.current
    val effAccent = if (rainbowGlow) dopaRainbowColor(seed = 13) else accentColor
    val prefManager = remember { AppPreferenceManager(context) }
    var isPlaying by remember { mutableStateOf(false) }
    var albumArtBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // アートワークがURIでしか来ないプレイヤー向けの取得キャッシュ
    var lastArtUriStr by remember { mutableStateOf<String?>(null) }
    var lastUriArtBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var lastUriAttemptStr by remember { mutableStateOf<String?>(null) }
    var lastUriFetchAttemptMs by remember { mutableStateOf(0L) }
    var trackTitle by remember { mutableStateOf("") }
    var trackArtist by remember { mutableStateOf("") }
    var trackAlbum by remember { mutableStateOf("") }
    var activePackageName by remember { mutableStateOf<String?>(null) }
    var sessionController by remember { mutableStateOf<MediaController?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    // ディスクラック用: セッションの再生キューと操作状態
    var sessionQueue by remember { mutableStateOf<List<MediaSession.QueueItem>>(emptyList()) }
    var lastQueueKey by remember { mutableStateOf("") }
    // キュー項目のアートワークキャッシュ（iconUri → Bitmap）。Spotifyは iconBitmap を入れないため、iconUri を辿って取得する
    var queueArtCache by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var queueArtFailTimes by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    LaunchedEffect(sessionQueue) {
        // キャッシュが際限なく育つと、コピーと再描画がだんだん重くなる。
        // 現在のキューに無いアートワークは捨てて、保持数にも上限を設ける
        val queueUris = sessionQueue.mapNotNull { it.description?.iconUri?.toString() }.toSet()
        if (queueArtCache.keys.any { it !in queueUris }) {
            queueArtCache = queueArtCache.filterKeys { it in queueUris }
        }
        if (queueArtFailTimes.keys.any { it !in queueUris }) {
            queueArtFailTimes = queueArtFailTimes.filterKeys { it in queueUris }
        }
        val failNow = System.currentTimeMillis()
        val missing = sessionQueue.mapNotNull { it.description?.iconUri?.toString() }.distinct()
            .filter { it !in queueArtCache }
            .filter { uriStr -> val ft = queueArtFailTimes[uriStr]; ft == null || failNow - ft > 60000L }
        for (uriStr in missing) {
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    val uri = android.net.Uri.parse(uriStr)
                    val raw = when {
                        uriStr.startsWith("spotify:image:") -> java.net.URL("https://i.scdn.co/image/" + uriStr.removePrefix("spotify:image:")).openStream().use { android.graphics.BitmapFactory.decodeStream(it) }
                        uri.scheme == "http" || uri.scheme == "https" -> java.net.URL(uriStr).openStream().use { android.graphics.BitmapFactory.decodeStream(it) }
                        else -> context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
                    }
                    raw?.let { if (it.width > 256) Bitmap.createScaledBitmap(it, 256, (it.height * 256f / it.width).toInt().coerceAtLeast(1), true) else it }
                }.getOrNull()
            }
            if (bmp != null) {
                var nextArt = queueArtCache + (uriStr to bmp)
                if (nextArt.size > 48) {
                    val dropKeys = nextArt.keys.take(nextArt.size - 48).toSet()
                    nextArt = nextArt.filterKeys { it !in dropKeys }
                }
                queueArtCache = nextArt
            } else {
                queueArtFailTimes = queueArtFailTimes + (uriStr to System.currentTimeMillis())
            }
        }
    }
    var rackOpen by remember { mutableStateOf(false) }
    var rackShow by remember { mutableStateOf(false) }
    val widgetScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val rackScrollAnim = remember { Animatable(0f) }
    var rackTargetOffset by remember { mutableIntStateOf(0) }
    var rackStepAnimRunning by remember { mutableStateOf(false) }
    // ダブルタップ判定: シングルタップは少し保留し、その間に2回目のタップが来たらダブルタップとして扱う
    var pendingTapJob by remember { mutableStateOf<Job?>(null) }
    var lastTapReleaseMs by remember { mutableLongStateOf(0L) }
    var tapCount by remember { mutableIntStateOf(0) }
    var grabRotX by remember { mutableStateOf(0f) }
    var grabRotY by remember { mutableStateOf(0f) }
    var userSpin by remember { mutableStateOf(0f) }
    var userSpinJob by remember { mutableStateOf<Job?>(null) }
    fun runRackSteps() {
        if (rackStepAnimRunning) return
        rackStepAnimRunning = true
        widgetScope.launch {
            while (rackScrollAnim.value != rackTargetOffset.toFloat()) {
                rackScrollAnim.animateTo(
                    targetValue = rackTargetOffset.toFloat(),
                    animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing)
                )
            }
            rackStepAnimRunning = false
        }
    }
    LaunchedEffect(rackOpen) {
        onRackVisibilityChanged(rackOpen)
        if (rackOpen) {
            delay(120)
            if (rackOpen) rackShow = true
        } else {
            rackShow = false
        }
    }

    val mediaSessionManager = remember { context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val listenerComponent = remember { ComponentName(context, LauncherMediaListenerService::class.java) }

    // 定期的に音楽再生状態とメタデータ（アルバムアート・曲情報）を取得
    LaunchedEffect(Unit) {
        var idleStreak = 0
        while (true) {
            if (!(context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isInteractive) {
                delay(3000)
                continue
            }
            val enabled = isNotificationServiceEnabled(context)
            if (enabled) {
                try {
                    val controllers = mediaSessionManager?.getActiveSessions(listenerComponent)
                    val activeController = controllers?.firstOrNull {
                        it.playbackState?.state == PlaybackState.STATE_PLAYING
                    } ?: controllers?.firstOrNull()

                    if (activeController != null) {
                        isPlaying = activeController.playbackState?.state == PlaybackState.STATE_PLAYING
                        val controllerPkg = activeController.packageName
                        activePackageName = if (controllerPkg != context.packageName) controllerPkg else null
                        sessionController = activeController
                        if (!rackOpen) {
                            // キューの読み出しは重いので、曲が変わったときだけ読み直す
                            val qKey = (activeController.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "") + "|" +
                                (activeController.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "")
                            if (qKey != lastQueueKey) {
                                lastQueueKey = qKey
                                try {
                                    val newQueue = activeController.queue ?: emptyList()
                                    val queueChanged = newQueue.size != sessionQueue.size ||
                                        newQueue.indices.any { qi ->
                                            newQueue[qi].queueId != sessionQueue[qi].queueId ||
                                                newQueue[qi].description?.title != sessionQueue[qi].description?.title
                                        }
                                    if (queueChanged) sessionQueue = newQueue
                                } catch (_: Exception) {}
                            }
                        }
                        val metadata = activeController.metadata
                        var bitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                        if (bitmap == null) {
                            // 画像そのものではなくURIだけ渡すプレイヤー（Spotify等）向けに、URIからも取得する
                            val uriStr = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)
                                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
                            if (uriStr != null) {
                                if (uriStr == lastArtUriStr && lastUriArtBitmap != null) {
                                    bitmap = lastUriArtBitmap
                                } else if (uriStr != lastUriAttemptStr || System.currentTimeMillis() - lastUriFetchAttemptMs > 15000L) {
                                    lastUriAttemptStr = uriStr
                                    lastUriFetchAttemptMs = System.currentTimeMillis()
                                    val fetched = withContext(Dispatchers.IO) {
                                        try {
                                            when {
                                                uriStr.startsWith("spotify:image:") -> java.net.URL("https://i.scdn.co/image/" + uriStr.removePrefix("spotify:image:")).openStream().use { android.graphics.BitmapFactory.decodeStream(it) }
                                                uriStr.startsWith("http://") || uriStr.startsWith("https://") -> java.net.URL(uriStr).openStream().use { android.graphics.BitmapFactory.decodeStream(it) }
                                                else -> context.contentResolver.openInputStream(android.net.Uri.parse(uriStr))?.use { android.graphics.BitmapFactory.decodeStream(it) }
                                            }
                                        } catch (_: Exception) { null }
                                    }
                                    if (fetched != null) {
                                        lastArtUriStr = uriStr
                                        lastUriArtBitmap = fetched
                                        bitmap = fetched
                                    }
                                }
                            }
                        }
                        if (bitmap == null) {
                            // 最後の手段として、メディア通知に載っているジャケットを使う
                            val nArt = LauncherMediaListenerService.mediaArtBitmap
                            val nTitle = LauncherMediaListenerService.mediaArtTitle
                            val curTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
                            if (nArt != null && LauncherMediaListenerService.mediaArtPackage == controllerPkg &&
                                (nTitle.isNullOrBlank() || curTitle.isBlank() || nTitle == curTitle)
                            ) {
                                bitmap = nArt
                            }
                        }
                        albumArtBitmap = bitmap
                        trackTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
                        trackArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                        trackAlbum = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
                    } else {
                        isPlaying = audioManager?.isMusicActive == true
                        albumArtBitmap = null
                        activePackageName = null
                        sessionController = null
                        trackTitle = ""
                        trackArtist = ""
                        trackAlbum = ""
                    }
                } catch (_: Exception) {
                    isPlaying = audioManager?.isMusicActive == true
                    albumArtBitmap = null
                    activePackageName = null
                    sessionController = null
                    trackTitle = ""
                    trackArtist = ""
                    trackAlbum = ""
                }
            } else {
                isPlaying = audioManager?.isMusicActive == true
                albumArtBitmap = null
                activePackageName = null
                sessionController = null
                trackTitle = ""
                trackArtist = ""
                trackAlbum = ""
            }
            // 再生セッションが無い状態が続いたら、確認間隔をゆっくりにする
            if (sessionController != null) idleStreak = 0 else idleStreak++
            delay(if (prefManager.isPowerSaveActive(context) || idleStreak >= 10) 4000L else 1500L)
        }
    }

    // 再生中の自動回転（5秒で1回転）。一時停止では慣性でゆっくり止まり、再生では滑らかに加速する
    var discAngle by remember { mutableStateOf(0f) }
    var discVel by remember { mutableStateOf(0f) }
    // 回転角は graphicsLayer（描画フェーズ）でのみ読む専用状態にし、毎フレームの再コンポーズを避ける
    val spinState = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying, spinFrozen) {
        var lastMs = System.currentTimeMillis()
        if (isPlaying) {
            while (true) {
                if (!(context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isInteractive) {
                    delay(400)
                    lastMs = System.currentTimeMillis()
                    continue
                }
                if (spinFrozen) {
                    // PV全画面や歌詞ブラウズで隠れている間は回転を止める
                    delay(300)
                    lastMs = System.currentTimeMillis()
                    continue
                }
                delay(33)
                val nowMs = System.currentTimeMillis()
                val dt = ((nowMs - lastMs) / 1000f).coerceIn(0.001f, 0.05f)
                lastMs = nowMs
                discVel += (72f - discVel) * (dt * 1.6f).coerceAtMost(1f)
                discAngle = (discAngle + discVel * dt) % 360f
                spinState.floatValue = discAngle + userSpin
            }
        } else {
            while (discVel > 0.5f) {
                delay(33)
                val nowMs = System.currentTimeMillis()
                val dt = ((nowMs - lastMs) / 1000f).coerceIn(0.001f, 0.05f)
                lastMs = nowMs
                discVel *= Math.pow(0.22, dt.toDouble()).toFloat()
                discAngle = (discAngle + discVel * dt) % 360f
                spinState.floatValue = discAngle + userSpin
            }
            discVel = 0f
        }
    }


    // 曲が変わった瞬間、アートワークが縦軸でくるっと1回転する
    val flipAnim = remember { Animatable(1f) }
    var lastFlipKey by remember { mutableStateOf("") }
    LaunchedEffect(trackTitle, trackArtist) {
        val key = "$trackTitle|$trackArtist"
        // 読み込み中はタイトルが一瞬空になることがある。空の状態は曲の切り替わりとみなさず、
        // 前の曲のキーを保持したまま、新しい曲が来たときだけ回転させる
        if (trackTitle.isNotBlank() && key != lastFlipKey) {
            val hadTrack = lastFlipKey.isNotBlank() && lastFlipKey != "|"
            lastFlipKey = key
            if (hadTrack) {
                flipAnim.snapTo(0f)
                flipAnim.animateTo(1f, animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing))
            }
        }
    }

    // ディスクラックの中身: キューが取れて現在曲が含まれていればキュー全体、
    // 取れなければ前・今・次の3枚だけを並べる（スクロールで前曲／次曲へ切り替える）
    val queueCurrentIndex = remember(sessionQueue, trackTitle, trackArtist) {
        sessionQueue.indexOfFirst { item ->
            val d = item.description
            val t = d?.title?.toString() ?: ""
            val sub = d?.subtitle?.toString() ?: ""
            t.isNotBlank() && t == trackTitle && (trackArtist.isBlank() || sub.isBlank() || sub == trackArtist)
        }
    }
    val queueMode = queueCurrentIndex >= 0
    val rackSize = if (queueMode) sessionQueue.size else 3
    val rackCurrentIndex = if (queueMode) queueCurrentIndex else 1
    fun rackTitleAt(index: Int): String = when {
        queueMode -> sessionQueue.getOrNull(index)?.description?.title?.toString() ?: ""
        index == rackCurrentIndex -> trackTitle
        index < rackCurrentIndex -> "前の曲"
        else -> "次の曲"
    }
    val artworkEnabled = prefManager.isArtworkEnabled()
    val artworkOutline = prefManager.isArtworkOutlineEnabled()
    val curvedTitle = prefManager.isCurvedTitleEnabled()
    val tonearmScale = tonearmScaleOverride ?: prefManager.getTonearmScale()
    val tonearmStylePref = prefManager.getTonearmStyle()
    val tonearmStyle = if (tonearmStylePref != "auto") tonearmStylePref else when (effectiveDiscStyle(prefManager)) {
        "clear" -> "acrylic"
        "glow" -> "glow"
        "cd", "md" -> "laser"
        "cassette_color", "cassette_clear", "cassette_black" -> "head"
        "reel" -> "head"
        "dat" -> "head"
        "eighttrack" -> "head"
        "floppy" -> "minimal"
        else -> "classic"
    }
    val tonearmAngle by animateFloatAsState(
        targetValue = if (isPlaying) 97f else 63f,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "TonearmAngle"
    )
    fun rackArtAt(index: Int): Bitmap? = if (!artworkEnabled) null else when {
        queueMode -> sessionQueue.getOrNull(index)?.description?.let { d -> d.iconBitmap ?: d.iconUri?.toString()?.let { queueArtCache[it] } } ?: if (index == rackCurrentIndex) albumArtBitmap else null
        index == rackCurrentIndex -> albumArtBitmap
        else -> null
    }
    // pointerInput のラムダは作られた時点の値を捕捉したままになるため、最新値を参照し続ける箱を用意する
    val latestRackCurrentIndex by rememberUpdatedState(rackCurrentIndex)
    val latestRackSize by rememberUpdatedState(rackSize)
    val latestQueueMode by rememberUpdatedState(queueMode)
    // 先読みは廃止（常時は計算しない）。ラックを開いた瞬間だけ、キューを取り直して
    // 見える範囲の色と線画を裏で温めておく（開いた直後のカクつき防止）
    LaunchedEffect(rackShow) {
        if (rackShow) {
            try {
                val c = sessionController
                if (c != null) {
                    val nq = c.queue ?: emptyList()
                    val changed = nq.size != sessionQueue.size ||
                        nq.indices.any { qi ->
                            nq[qi].queueId != sessionQueue[qi].queueId ||
                                nq[qi].description?.title != sessionQueue[qi].description?.title
                        }
                    if (changed) sessionQueue = nq
                }
            } catch (_: Exception) {}
            withContext(Dispatchers.Default) {
                val centerIdx = latestRackCurrentIndex
                for (i in sessionQueue.indices) {
                    if (kotlin.math.abs(i - centerIdx) > 10) continue
                    val art = rackArtAt(i) ?: continue
                    synchronized(rackAccentCache) {
                        if (art !in rackAccentCache) rackAccentCache[art] = rackAccentOf(art)
                    }
                    synchronized(rackLineArtCache) {
                        if (art !in rackLineArtCache) rackLineArtCache[art] = extractLineArtBitmap(art)?.asImageBitmap()
                    }
                }
            }
        }
    }
    val latestSessionQueue by rememberUpdatedState(sessionQueue)
    val rackTiltXState = animateFloatAsState(
        targetValue = if (rackShow) 55f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "RackTiltX"
    )

    if (showPermissionDialog) {
        val fontFamily = LocalLauncherFontFamily.current
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        try {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                ) {
                    Text("設定を開く", color = Color(0xFF88C0FF), fontFamily = fontFamily)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("キャンセル", color = Color.Gray, fontFamily = fontFamily)
                }
            },
            title = { Text("通知アクセスの許可", fontSize = 18.sp, fontFamily = fontFamily) },
            text = {
                Text(
                    text = "再生中の音楽のジャケット画像（アートワーク）や曲情報を取得するには、通知アクセスの許可が必要です。設定画面で本アプリを有効にしてください。\n\n※機種（Xiaomi、OPPO、Galaxyなど）によっては、設定画面の右上メニューやアプリ情報等から「制限付き設定を許可」を選択してからでないと通知アクセスを有効にできない場合があります。",
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    fontFamily = fontFamily
                )
            },
            containerColor = Color(0xFF141C2E),
            titleContentColor = Color.White
        )
    }

    // タップ: 再生中のプレイヤーアプリを開く
    fun openPlayerApp() {
                    if (!isNotificationServiceEnabled(context)) {
                        showPermissionDialog = true
                    } else {
                        var launched = false
                        val appContext = context.applicationContext

                        fun launchTaskIntent(intent: Intent): Boolean {
                            return try {
                                // 既存のタスクを前面に出すだけにして、新規セッション（別タスク）を
                                // 作らない。MULTIPLE_TASK / NEW_DOCUMENT は付けない
                                intent.addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                )
                                appContext.startActivity(intent)
                                true
                            } catch (_: Exception) {
                                false
                            }
                        }

                        // 1. アクティブな音楽アプリを通常起動で開く（既存タスクを前面へ）。
                        //    media3 系アプリはセッションアクティビティの PendingIntent が
                        //    実質効かない場合があり、送信が成功扱いでも画面が開かないことが
                        //    あるため、確実なパッケージ起動を先に試す
                        if (!launched) {
                            val activePkg = activePackageName
                            if (!activePkg.isNullOrBlank() && activePkg != context.packageName) {
                                val launchIntent = context.packageManager.getLaunchIntentForPackage(activePkg)
                                if (launchIntent != null) {
                                    launched = launchTaskIntent(launchIntent)
                                }
                            }
                        }

                        // 2. セッション自身が指定する画面を開く
                        //    （パッケージ起動が使えない場合のフォールバック）
                        if (!launched) {
                            val sessionActivity = sessionController?.sessionActivity
                            if (sessionActivity != null) {
                                launched = try {
                                    sessionActivity.send()
                                    true
                                } catch (_: Exception) {
                                    false
                                }
                            }
                        }

                        // 3. メディアプレーヤー標準/カテゴリインテント
                        if (!launched) {
                            try {
                                val musicCategoryIntent = Intent(Intent.ACTION_MAIN).apply {
                                    addCategory(Intent.CATEGORY_APP_MUSIC)
                                }
                                if (musicCategoryIntent.resolveActivity(context.packageManager) != null) {
                                    launched = launchTaskIntent(musicCategoryIntent)
                                }
                            } catch (_: Exception) {}
                        }

                        if (!launched) {
                            try {
                                val musicMediaIntent = Intent(MediaStore.INTENT_ACTION_MUSIC_PLAYER)
                                if (musicMediaIntent.resolveActivity(context.packageManager) != null) {
                                    launched = launchTaskIntent(musicMediaIntent)
                                }
                            } catch (_: Exception) {}
                        }

                        // 4. 主要音楽アプリの起動試行
                        if (!launched) {
                            val musicPackages = listOf(
                                "com.spotify.music",
                                "com.google.android.apps.youtube.music",
                                "com.apple.android.music",
                                "com.amazon.mp3",
                                "com.sec.android.app.music",
                                "com.sony.walkman",
                                "com.maxmpz.audioplayer",
                                "com.android.music"
                            )
                            for (pkg in musicPackages) {
                                if (pkg == context.packageName) continue
                                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                                if (intent != null) {
                                    if (launchTaskIntent(intent)) {
                                        launched = true
                                        break
                                    }
                                }
                            }
                        }
                    }
    }

    // 左円タップ系アクションの実行（"app" / "toggle" / "next" / "prev" / それ以外は無動作）
    fun runWidgetAction(action: String) {
        try {
            val tc = sessionController?.transportControls
            when (action) {
                "toggle" -> if (isPlaying) tc?.pause() else tc?.play()
                "next" -> tc?.skipToNext()
                "prev" -> tc?.skipToPrevious()
                "app" -> openPlayerApp()
                "theme" -> onThemeSettingsRequest()
            }
        } catch (e: Exception) {
            // 操作できないセッションでは何もしない
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .offset(x = (discCenterXDp - discRadiusDp).dp, y = (discCenterYDp - discRadiusDp).dp)
                .size((discRadiusDp * 2f).dp)
                .then(if (prefManager.isCaseCircleClipEnabled() || !isCaseStyle(effectiveDiscStyle(prefManager))) Modifier.clip(CircleShape) else Modifier)
                .pointerInput(sessionController != null) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val pressTapIndex = tapCount
                            if (pendingTapJob != null) {
                                pendingTapJob?.cancel()
                                pendingTapJob = null
                            }
                            val velocityTracker = VelocityTracker()
                            velocityTracker.addPosition(down.uptimeMillis, down.position)
                            val hasSession = sessionController != null
                            val startIdx = latestRackCurrentIndex
                            rackTargetOffset = 0
                            var moved = false
                            var maxDist = 0f
                            var stepBaseY = down.position.y
                            val dirSign = if (prefManager.isRackReverseScroll()) -1 else 1
                            val downWall = System.currentTimeMillis()
                            var longPressFired = false
                            var grabActive = false
                            val longPressJob = widgetScope.launch {
                                delay(500)
                                if (!moved && maxDist < 14.dp.toPx()) {
                                    val holdAction = if (pressTapIndex > 0) prefManager.getWidgetDoubleTapHoldAction() else prefManager.getWidgetLongPressAction()
                                    if (holdAction != "rack") {
                                        longPressFired = true
                                        rackOpen = false
                                        pendingTapJob?.cancel()
                                        pendingTapJob = null
                                        lastTapReleaseMs = 0L
                                        tapCount = 0
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (holdAction == "grab") {
                                            userSpinJob?.cancel()
                                            grabActive = true
                                        } else {
                                            runWidgetAction(holdAction)
                                        }
                                    } else if (pressTapIndex > 0) {
                                        rackOpen = true
                                    }
                                }
                            }
                            drag(down.id) { change ->
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                maxDist = maxOf(maxDist, (change.position - down.position).getDistance())
                                if (grabActive) {
                                    val dx = change.position.x - change.previousPosition.x
                                    val dy = change.position.y - change.previousPosition.y
                                    grabRotY = (grabRotY + dx * 0.45f).coerceIn(-70f, 70f)
                                    grabRotX = (grabRotX - dy * 0.45f).coerceIn(-70f, 70f)
                                } else if (hasSession && !longPressFired && rackOpen) {
                                    var dy = change.position.y - stepBaseY
                                    while (dy <= -40f || dy >= 40f) {
                                        val dir = (if (dy <= -40f) 1 else -1) * dirSign
                                        val newTarget = rackTargetOffset + dir
                                        if (startIdx + newTarget in 0 until latestRackSize) {
                                            rackTargetOffset = newTarget
                                            moved = true
                                            if (pendingTapJob != null) {
                                                pendingTapJob?.cancel()
                                                pendingTapJob = null
                                                lastTapReleaseMs = 0L
                                                tapCount = 0
                                            }
                                            runRackSteps()
                                        }
                                        if (dy <= -40f) {
                                            stepBaseY -= 40f
                                            dy += 40f
                                        } else {
                                            stepBaseY += 40f
                                            dy -= 40f
                                        }
                                    }
                                }
                                change.consume()
                            }
                            longPressJob?.cancel()
                            if (!longPressFired && !moved && !grabActive) {
                                val velX = velocityTracker.calculateVelocity().x
                                val velY = velocityTracker.calculateVelocity().y
                                if (kotlin.math.abs(velX) > 1000f && kotlin.math.abs(velX) > kotlin.math.abs(velY) * 1.2f) {
                                    userSpinJob?.cancel()
                                    val radiusPx = discRadiusDp.dp.toPx()
                                    userSpinJob = widgetScope.launch {
                                        var vel = (velX / radiusPx) * 57.3f * 1.1f
                                        var lastMs = System.currentTimeMillis()
                                        while (kotlin.math.abs(vel) > 5f) {
                                            delay(33)
                                            val nowMs = System.currentTimeMillis()
                                            val dt = ((nowMs - lastMs) / 1000f).coerceIn(0.001f, 0.05f)
                                            lastMs = nowMs
                                            userSpin = (userSpin + vel * dt) % 360f
                                            spinState.floatValue = discAngle + userSpin
                                            vel *= Math.pow(0.30, dt.toDouble()).toFloat()
                                        }
                                    }
                                }
                            }
                            if (grabActive) {
                                grabActive = false
                                val startRotX = grabRotX
                                val startRotY = grabRotY
                                widgetScope.launch {
                                    Animatable(startRotX).animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 260f)) { grabRotX = value }
                                }
                                widgetScope.launch {
                                    Animatable(startRotY).animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 260f)) { grabRotY = value }
                                }
                            }
                            val heldMs = System.currentTimeMillis() - downWall
                            val finalIndex = (startIdx + rackTargetOffset).coerceIn(0, (latestRackSize - 1).coerceAtLeast(0))
                            val shouldJump = hasSession && moved && finalIndex != startIdx
                            if (shouldJump) {
                                widgetScope.launch {
                                    var guard = 0
                                    while ((rackStepAnimRunning || rackScrollAnim.value != rackTargetOffset.toFloat()) && guard < 120) {
                                        delay(30)
                                        guard++
                                    }
                                    try {
                                        val tc = sessionController?.transportControls
                                        if (shouldJump) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (latestQueueMode) {
                                                latestSessionQueue.getOrNull(finalIndex)?.let { tc?.skipToQueueItem(it.queueId) }
                                            } else if (finalIndex < startIdx) {
                                                tc?.skipToPrevious()
                                            } else {
                                                tc?.skipToNext()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        // 操作できないセッションでは何もしない
                                    }
                                    rackOpen = false
                                    rackScrollAnim.snapTo(0f)
                                    rackTargetOffset = 0
                                }
                            } else {
                                // 短いタップ（またはセッション無し）: ラックを閉じる。ドラッグだった場合は何もしない
                                rackOpen = false
                                if (!longPressFired && maxDist < 14.dp.toPx() && heldMs < 300L) {
                                    userSpinJob?.cancel()
                                    val doubleAction = prefManager.getWidgetDoubleTapAction()
                                    val tripleAction = prefManager.getWidgetTripleTapAction()
                                    if (doubleAction == "none" && tripleAction == "none") {
                                        runWidgetAction(prefManager.getWidgetTapAction())
                                    } else {
                                        tapCount++
                                        val myCount = tapCount
                                        lastTapReleaseMs = System.currentTimeMillis()
                                        pendingTapJob?.cancel()
                                        pendingTapJob = widgetScope.launch {
                                            delay(300)
                                            pendingTapJob = null
                                            if (tapCount == myCount) {
                                                val c = tapCount
                                                tapCount = 0
                                                lastTapReleaseMs = 0L
                                                when {
                                                    c >= 3 -> runWidgetAction(if (tripleAction != "none") tripleAction else if (doubleAction != "none") doubleAction else prefManager.getWidgetTapAction())
                                                    c == 2 -> runWidgetAction(if (doubleAction != "none") doubleAction else prefManager.getWidgetTapAction())
                                                    else -> runWidgetAction(prefManager.getWidgetTapAction())
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                .graphicsLayer {
                    alpha = if (rackShow) 0f else 1f
                    rotationX = grabRotX
                    rotationY = grabRotY
                    cameraDistance = 9f * density
                },
            contentAlignment = Alignment.Center
        ) {
            if (artworkOutline) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val outerRadius = size.width / 2f

                // 外周の白い円盤輪郭
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = outerRadius,
                    center = center,
                    style = Stroke(width = 1.5f)
                )
            }
            }

            val discStyleNow = effectiveDiscStyle(prefManager)
            // 薄い立体感: アートワークの下に暗い円を少しずらして敷き、盤の厚みとして見せる（ケース表示時は背景にしない）
            if (artworkTilt3d && artworkEnabled && albumArtBitmap != null && discStyleNow != "md" && !isCassetteStyle(discStyleNow) && discStyleNow != "clear" && discStyleNow != "reel" && discStyleNow != "floppy" && discStyleNow != "dat" && discStyleNow != "eighttrack") {
                Box(
                    modifier = Modifier
                        .size((discRadiusDp * 2f * 0.92f).dp)
                        .offset(y = 3.5.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                )
            }

            // 大サイズ（円サイズの92%）の回転するアートワーク / レーベル部分
            if (discStyleNow == "md" || isCassetteStyle(discStyleNow) || discStyleNow == "reel" || discStyleNow == "floppy" || discStyleNow == "dat" || discStyleNow == "eighttrack") {
                val caseBitmap = if (artworkEnabled) albumArtBitmap else null
                val caseBitmapImage = remember(caseBitmap) { caseBitmap?.asImageBitmap() }
                val lineArtImage = remember(caseBitmap) { extractLineArtBitmap(caseBitmap)?.asImageBitmap() }
                Box(
                    modifier = Modifier.size((discRadiusDp * 2f * 0.92f).dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        discStyleNow == "md" -> MdCase(boxSizeDp = discRadiusDp * 2f * 0.92f, accentColor = effAccent, rotationState = spinState, bitmapImage = caseBitmapImage, lineArtImage = lineArtImage, title = trackTitle)
                        discStyleNow == "reel" -> ReelCase(boxSizeDp = discRadiusDp * 2f * 0.92f, accentColor = effAccent, rotationState = spinState, title = trackTitle)
                        discStyleNow == "floppy" -> FloppyCase(boxSizeDp = discRadiusDp * 2f * 0.92f, accentColor = effAccent, title = trackTitle, lineArtImage = lineArtImage)
                        discStyleNow == "dat" -> DatCase(boxSizeDp = discRadiusDp * 2f * 0.92f, accentColor = effAccent, rotationState = spinState, title = trackTitle)
                        discStyleNow == "eighttrack" -> EightTrackCase(boxSizeDp = discRadiusDp * 2f * 0.92f, accentColor = effAccent, title = trackTitle, lineArtImage = lineArtImage)
                        else -> CassetteCase(boxSizeDp = discRadiusDp * 2f * 0.92f, accentColor = effAccent, rotationState = spinState, title = trackTitle, artist = trackArtist, shellStyle = cassetteShellOf(discStyleNow), lineArtImage = lineArtImage)
                    }
                }
            } else {
            Box(
                modifier = Modifier
                    .size((discRadiusDp * 2f * 0.92f).dp)
                    .graphicsLayer {
                        rotationZ = spinState.floatValue
                        rotationY = (1f - flipAnim.value) * 360f
                        cameraDistance = 9f * density
                    }
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = if (artworkEnabled) albumArtBitmap else null
                val bitmapImage = remember(bitmap) { bitmap?.asImageBitmap() }
                when (discStyleNow) {
                    "vinyl" -> {
                        // レコード盤: 黒い盤面に溝、中央のレーベルがアートワーク
                        Canvas(Modifier.fillMaxSize()) {
                            val rMax = size.minDimension / 2f
                            drawCircle(Color(0xFF0A0A0C))
                            for (i in 1..9) {
                                drawCircle(
                                    Color.White.copy(alpha = if (i % 3 == 0) 0.10f else 0.05f),
                                    radius = rMax * (0.50f + i * 0.052f),
                                    style = Stroke(width = 1.2f)
                                )
                            }
                            drawCircle(Color.White.copy(alpha = 0.18f), radius = rMax * 0.99f, style = Stroke(width = 2f))
                        }
                        if (bitmapImage != null) {
                            Image(
                                bitmap = bitmapImage,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(0.44f).clip(CircleShape)
                            )
                        }
                    }
                    "clear" -> {
                        // クリアレコード: 半透明の盤面（アクセント色の色味）に溝、中央レーベルがアートワーク
                        Canvas(Modifier.fillMaxSize()) {
                            val rMax = size.minDimension / 2f
                            drawCircle(vividColorOf(effAccent).copy(alpha = 0.36f))
                            drawCircle(Color.Black.copy(alpha = 0.10f))
                            for (i in 1..9) {
                                drawCircle(
                                    Color.White.copy(alpha = if (i % 3 == 0) 0.16f else 0.09f),
                                    radius = rMax * (0.50f + i * 0.052f),
                                    style = Stroke(width = 1.2f)
                                )
                            }
                            drawCircle(Color.White.copy(alpha = 0.30f), radius = rMax * 0.99f, style = Stroke(width = 2f))
                        }
                        if (bitmapImage != null) {
                            Image(
                                bitmap = bitmapImage,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(0.44f).clip(CircleShape)
                            )
                        }
                    }
                    "glow" -> {
                        // 蓄光レコード: 青緑に光る盤面、中央レーベルがアートワーク
                        Canvas(Modifier.fillMaxSize()) {
                            val rMax = size.minDimension / 2f
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(Color(0xFFE4FFF3), Color(0xFF8FFFD6), Color(0xFF4DEBB8)),
                                    center = center,
                                    radius = rMax
                                )
                            )
                            for (i in 1..9) {
                                drawCircle(
                                    Color(0xFF1E7A5C).copy(alpha = 0.13f),
                                    radius = rMax * (0.50f + i * 0.052f),
                                    style = Stroke(width = 1.2f)
                                )
                            }
                            drawCircle(Color(0xFFB9FFE4), radius = rMax * 0.99f, style = Stroke(width = 3f))
                        }
                        if (bitmapImage != null) {
                            Image(
                                bitmap = bitmapImage,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(0.44f).clip(CircleShape)
                            )
                        }
                    }
                    "cd" -> {
                        // CD: アートワークに虹色の光沢、中央は透明リング＋穴（Clearで抜く）
                        Box(
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                            }
                        ) {
                            if (bitmapImage != null) {
                                Image(
                                    bitmap = bitmapImage,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Canvas(Modifier.fillMaxSize()) { drawCircle(Color(0xFFB9BEC6)) }
                            }
                            Canvas(Modifier.fillMaxSize()) {
                                val rMax = size.minDimension / 2f
                                drawCircle(
                                    brush = Brush.sweepGradient(
                                        listOf(
                                            Color(0xFFFF9E9E).copy(alpha = 0.20f),
                                            Color(0xFFFFFF9E).copy(alpha = 0.20f),
                                            Color(0xFF9EFFA8).copy(alpha = 0.20f),
                                            Color(0xFF9ED8FF).copy(alpha = 0.20f),
                                            Color(0xFFD09EFF).copy(alpha = 0.20f),
                                            Color(0xFFFF9E9E).copy(alpha = 0.20f)
                                        )
                                    )
                                )
                                for (i in 1..7) {
                                    drawCircle(Color.White.copy(alpha = 0.06f), radius = rMax * (0.30f + i * 0.09f), style = Stroke(width = 1f))
                                }
                                drawCircle(Color(0xFFD8D8D8).copy(alpha = 0.85f), radius = rMax * 0.16f)
                                drawCircle(Color.Black, radius = rMax * 0.07f, blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
                            }
                        }
                    }
                    else -> {
                        if (bitmapImage != null) {
                            Image(
                                bitmap = bitmapImage,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
                // アートワークが無い（再生していない）ときは色を塗らない。
                // センターホール（中心の点）は再生中・停止中ともに出さない（CDスタイルの穴は除く）
            }
            }
        }
    }

    // --- ディスクラック: 横倒しのレコード盤がカスケード状に並ぶ（音楽アプリと同じ見た目） ---
    // 描画は専用コンポーザブルに分離し、スクロールアニメ中は本体を再コンポーズしない
    if (rackShow && rackSize > 0) {
        RackDiscLayer(
            rackScrollAnim = rackScrollAnim,
            rackCurrentIndex = rackCurrentIndex,
            rackSize = rackSize,
            rackShow = rackShow,
            discCenterXDp = discCenterXDp,
            discCenterYDp = discCenterYDp,
            discRadiusDp = discRadiusDp,
            effAccent = effAccent,
            discStyle = effectiveDiscStyle(prefManager),
            rackTiltXState = rackTiltXState,
            haptic = haptic,
            artAt = { rackArtAt(it) },
            titleAt = { rackTitleAt(it) },
            caseCircleClip = prefManager.isCaseCircleClipEnabled()
        )
    }

    // --- 円の右側（画面内）の円周に沿って湾曲描画する曲情報 ---
    val noMusicText = "音楽を再生するとここに曲名が表示されます  —  No Music Playing  —  "
    val displayInfo = remember(trackTitle, trackArtist, trackAlbum, isPlaying, noMusicText) {
        val raw = listOfNotNull(
            trackTitle.ifBlank { null },
            trackArtist.ifBlank { null },
            trackAlbum.ifBlank { null }
        ).joinToString("  —  ")
        if (raw.isNotBlank()) raw else noMusicText
    }

    val customFontPath = remember(prefManager.getCustomFontPath(), prefManager.isUseCustomFont()) {
        if (prefManager.isUseCustomFont()) prefManager.getCustomFontPath() else null
    }
    val fontChoice = prefManager.getFontChoice()
    // 円周テキストのフォントはアプリ全体のフォント設定（parseFontFamily）と揃える。
    // 以前はカスタムフォントファイルしか見ておらず、同梱フォント（receipt）選択時にデフォルト固定になっていた
    val customTypeface = remember(customFontPath, fontChoice) {
        try {
            when (fontChoice) {
                "receipt" -> androidx.core.content.res.ResourcesCompat.getFont(
                    context,
                    com.example.nissingeppomusic.R.font.receipt_font
                )
                "denkichip" -> androidx.core.content.res.ResourcesCompat.getFont(
                    context,
                    com.example.nissingeppomusic.R.font.denkichip_font
                )
                else -> if (!customFontPath.isNullOrBlank()) {
                    val file = java.io.File(customFontPath)
                    if (file.exists()) android.graphics.Typeface.createFromFile(file) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    Box(
        modifier = modifier.fillMaxSize().graphicsLayer { alpha = if (rackShow) 0f else 1f }
    ) {
        if (curvedTitle) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = discCenterXDp.dp.toPx()
            val centerY = discCenterYDp.dp.toPx()
            val outerRadius = discRadiusDp.dp.toPx()
            val pathRadius = outerRadius + 14.dp.toPx()

            val textPath = android.graphics.Path().apply {
                addArc(
                    centerX - pathRadius,
                    centerY - pathRadius,
                    centerX + pathRadius,
                    centerY + pathRadius,
                    textStartAngle,
                    180f
                )
            }
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 11.sp.toPx()
                isAntiAlias = true
                letterSpacing = 0.15f
                textAlign = android.graphics.Paint.Align.LEFT
                setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                if (dopagaki) {
                    // ドパガキモード: 円周テキストも虹色グラデ＋金のグローにする
                    shader = android.graphics.LinearGradient(
                        0f, 0f, size.width, size.height,
                        intArrayOf(
                            android.graphics.Color.rgb(255, 93, 162),
                            android.graphics.Color.rgb(214, 51, 255),
                            android.graphics.Color.rgb(122, 92, 255),
                            android.graphics.Color.rgb(51, 181, 255),
                            android.graphics.Color.rgb(63, 224, 197),
                            android.graphics.Color.rgb(255, 217, 59),
                            android.graphics.Color.rgb(255, 138, 59)
                        ),
                        null,
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    setShadowLayer(6f, 0f, 0f, android.graphics.Color.rgb(255, 196, 0))
                }
                if (customTypeface != null) {
                    typeface = customTypeface
                }
            }
            // 文字列の中心が円の右下（約35°）に来るよう、弧の長さと文字幅から開始位置を決める
            val arcLen = (Math.PI * pathRadius).toFloat()
            val textW = paint.measureText(displayInfo)
            val centerPos = (35f - textStartAngle) / 180f * arcLen
            val hOff = (centerPos - textW / 2f).coerceIn(0f, maxOf(0f, arcLen - textW))
            drawContext.canvas.nativeCanvas.drawTextOnPath(displayInfo, textPath, hOff, 0f, paint)
        }
        }
        // トーンアーム（レコード針）: 再生中は針が盤の右端に乗り、停止中は右へ離れる
        if (tonearmStyle != "none") {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = discCenterXDp.dp.toPx()
                val cy = discCenterYDp.dp.toPx()
                val r = discRadiusDp.dp.toPx()
                val px = cx + r * 1.02f
                val py = cy - r * 1.02f
                val armLen = r * 1.106f * tonearmScale
                val rad = Math.toRadians(tonearmAngle.toDouble())
                val cosA = cos(rad).toFloat()
                val sinA = sin(rad).toFloat()
                val tipX = px + armLen * cosA
                val tipY = py + armLen * sinA
                val sc = tonearmScale
                when (tonearmStyle) {
                    "laser" -> {
                        // レーザーピックアップ: 四角い光学ユニットが先端位置を移動する
                        drawLine(color = Color.White.copy(alpha = 0.35f), start = Offset(px, py), end = Offset(tipX, tipY), strokeWidth = (2f * sc).dp.toPx())
                        val w = (36f * sc).dp.toPx()
                        val h = (23f * sc).dp.toPx()
                        drawRoundRect(color = Color(0xFF37474F), topLeft = Offset(tipX - w / 2f, tipY - h / 2f), size = Size(w, h), cornerRadius = CornerRadius((4f * sc).dp.toPx()))
                        drawRoundRect(color = Color(0xFF14181B), topLeft = Offset(tipX - w * 0.32f, tipY - h * 0.28f), size = Size(w * 0.64f, h * 0.56f), cornerRadius = CornerRadius((3f * sc).dp.toPx()))
                        drawCircle(color = Color(0xFF0D1B2E), radius = (4.5f * sc).dp.toPx(), center = Offset(tipX, tipY))
                        drawCircle(color = Color(0xFFFF1744), radius = (1.8f * sc).dp.toPx(), center = Offset(tipX, tipY))
                        drawCircle(color = Color(0xFF263238), radius = (6f * sc).dp.toPx(), center = Offset(px, py))
                    }
                    "head" -> {
                        // 再生ヘッド: 太めのブラケットの先に金属ヘッド
                        drawLine(color = Color(0xFF78909C), start = Offset(px, py), end = Offset(tipX, tipY), strokeWidth = (7f * sc).dp.toPx(), cap = StrokeCap.Round)
                        val w = (27f * sc).dp.toPx()
                        val h = (17f * sc).dp.toPx()
                        drawRoundRect(color = Color(0xFFB0BEC5), topLeft = Offset(tipX - w / 2f, tipY - h / 2f), size = Size(w, h), cornerRadius = CornerRadius((3f * sc).dp.toPx()))
                        drawLine(color = Color(0xFF37474F), start = Offset(tipX - w * 0.30f, tipY), end = Offset(tipX + w * 0.30f, tipY), strokeWidth = (2.5f * sc).dp.toPx())
                        drawCircle(color = Color(0xFF546E7A), radius = (2.2f * sc).dp.toPx(), center = Offset(tipX - w * 0.62f, tipY))
                        drawCircle(color = Color(0xFF546E7A), radius = (2.2f * sc).dp.toPx(), center = Offset(tipX + w * 0.62f, tipY))
                        drawCircle(color = Color(0xFF455A64), radius = (9f * sc).dp.toPx(), center = Offset(px, py))
                        drawCircle(color = Color(0xFFCFD8DC), radius = (3.5f * sc).dp.toPx(), center = Offset(px, py))
                    }
                    "minimal" -> {
                        drawLine(color = Color.White.copy(alpha = 0.85f), start = Offset(px, py), end = Offset(tipX, tipY), strokeWidth = (2f * sc).dp.toPx(), cap = StrokeCap.Round)
                        drawLine(color = Color.White, start = Offset(tipX, tipY), end = Offset(tipX, tipY + (4f * sc).dp.toPx()), strokeWidth = (1.6f * sc).dp.toPx())
                        drawCircle(color = Color.White.copy(alpha = 0.9f), radius = (3.5f * sc).dp.toPx(), center = Offset(px, py))
                    }
                    else -> {
                        // パレット: [0]カウンター [1]アーム [2]ハイライト [3]シェル [4]ピボット [5]ピボットリング [6]中心
                        val pal = when (tonearmStyle) {
                            "brass" -> listOf(Color(0xFF8C6D1F), Color(0xFFC9A227), Color(0xFFFFE082), Color(0xFF3E2723), Color(0xFF5D4037), Color(0xFFC9A227), Color(0xFFFFD54F))
                            "acrylic" -> listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.80f), Color.White.copy(alpha = 0.50f), Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.65f), Color.White)
                            "glow" -> listOf(Color(0xFF2E7D68), Color(0xFF4DEBB8), Color(0xFFE4FFF3), Color(0xFF4DEBB8), Color(0xFF0E3B2E), Color(0xFF4DEBB8), Color(0xFFE4FFF3))
                            "mecha" -> listOf(Color(0xFF263238), Color(0xFF37474F), accentColor, Color(0xFF263238), Color(0xFF263238), Color(0xFF90A4AE), Color(0xFFCFD8DC))
                            else -> listOf(Color(0xFF9E9E9E), Color(0xFFC9C9C9), Color(0xFFFFFFFF), accentColor, Color(0xFF424242), accentColor, Color(0xFFBDBDBD))
                        }
                        if (tonearmStyle == "glow") {
                            drawLine(color = Color(0xFF4DEBB8).copy(alpha = 0.30f), start = Offset(px, py), end = Offset(tipX, tipY), strokeWidth = (13f * sc).dp.toPx(), cap = StrokeCap.Round)
                        }
                        val armW = if (tonearmStyle == "mecha") 9f else if (tonearmStyle == "acrylic") 6f else 4.5f
                        drawLine(color = pal[0], start = Offset(px - cosA * (16f * sc).dp.toPx(), py - sinA * (16f * sc).dp.toPx()), end = Offset(px, py), strokeWidth = ((if (tonearmStyle == "mecha") 14f else 10f) * sc).dp.toPx(), cap = StrokeCap.Round)
                        drawLine(color = pal[1], start = Offset(px, py), end = Offset(tipX, tipY), strokeWidth = (armW * sc).dp.toPx(), cap = StrokeCap.Round)
                        drawLine(color = pal[2], start = Offset(px, py), end = Offset(tipX, tipY), strokeWidth = (1.6f * sc).dp.toPx(), cap = StrokeCap.Round)
                        if (tonearmStyle == "mecha") {
                            val mx = (px + tipX) / 2f
                            val my = (py + tipY) / 2f
                            drawCircle(color = Color(0xFF263238), radius = (6.5f * sc).dp.toPx(), center = Offset(mx, my))
                            drawCircle(color = accentColor, radius = (6.5f * sc).dp.toPx(), center = Offset(mx, my), style = Stroke(width = (1.5f * sc).dp.toPx()))
                        }
                        val shellRad = rad + Math.toRadians(18.0)
                        val shellLen = (17f * sc).dp.toPx()
                        drawLine(color = pal[3], start = Offset(tipX - (cos(shellRad) * shellLen).toFloat(), tipY - (sin(shellRad) * shellLen).toFloat()), end = Offset(tipX, tipY), strokeWidth = ((if (tonearmStyle == "mecha") 10f else 7f) * sc).dp.toPx(), cap = StrokeCap.Round)
                        drawLine(color = Color.White, start = Offset(tipX, tipY), end = Offset(tipX, tipY + (4f * sc).dp.toPx()), strokeWidth = (1.6f * sc).dp.toPx())
                        drawCircle(color = pal[4], radius = ((if (tonearmStyle == "mecha") 15f else 13f) * sc).dp.toPx(), center = Offset(px, py))
                        drawCircle(color = pal[5], radius = ((if (tonearmStyle == "mecha") 15f else 13f) * sc).dp.toPx(), center = Offset(px, py), style = Stroke(width = (1.5f * sc).dp.toPx()))
                        drawCircle(color = pal[6], radius = (4.5f * sc).dp.toPx(), center = Offset(px, py))
                    }
                }
            }
        }
    }
}

/**
 * ディスクラック専用の描画レイヤー。
 * スクロールアニメの値（rackScrollAnim）はこの中だけで読み、本体（MusicVinylWidget）を
 * 毎フレーム再コンポーズしない。各ディスクの位置と透明度はレイアウト／描画フェーズで
 * 計算する（offset ラムダ形式と graphicsLayer）ため、スクロール中の再コンポーズも最小限。
 * 見た目の計算式は分離前と同一。
 */
@Composable
private fun RackDiscLayer(
    rackScrollAnim: Animatable<Float, AnimationVector1D>,
    rackCurrentIndex: Int,
    rackSize: Int,
    rackShow: Boolean,
    discCenterXDp: Float,
    discCenterYDp: Float,
    discRadiusDp: Float,
    effAccent: Color,
    discStyle: String,
    rackTiltXState: State<Float>,
    haptic: HapticFeedback,
    artAt: (Int) -> Bitmap?,
    titleAt: (Int) -> String,
    caseCircleClip: Boolean
) {
    val stillSpinState = remember { mutableFloatStateOf(0f) }
    val rackFocusedIndex = if (rackSize == 0) 0 else (rackCurrentIndex + rackScrollAnim.value.roundToInt()).coerceIn(0, rackSize - 1)
    var rackLastHapticIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(rackShow, rackFocusedIndex) {
        if (rackShow && rackLastHapticIndex != -1 && rackFocusedIndex != rackLastHapticIndex) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        if (rackShow) rackLastHapticIndex = rackFocusedIndex
    }
    val virtualPos = rackCurrentIndex + rackScrollAnim.value
    val firstIdx = maxOf(0, floor(virtualPos - 6f).toInt())
    val lastIdx = minOf(rackSize - 1, ceil(virtualPos + 6f).toInt())
    for (targetIdx in firstIdx..lastIdx) {
        val isFocused = targetIdx == rackFocusedIndex
        Box(
            modifier = Modifier
                .offset {
                    val relPos = targetIdx - (rackCurrentIndex + rackScrollAnim.value)
                    val gapOffset = sign(relPos) * 24f * min(1f, abs(relPos))
                    val yPosDp = discCenterYDp + (relPos * 42f) + gapOffset
                    val focusBump = (1f - min(1f, abs(relPos))) * 30f
                    val xShiftDp = relPos * 7f + focusBump
                    IntOffset(
                        x = ((discCenterXDp - discRadiusDp + xShiftDp) * density).roundToInt(),
                        y = ((yPosDp - discRadiusDp) * density).roundToInt()
                    )
                }
                .size((discRadiusDp * 2f).dp)
                .graphicsLayer {
                    val relPos = targetIdx - (rackCurrentIndex + rackScrollAnim.value)
                    rotationX = rackTiltXState.value
                    cameraDistance = 12f * density
                    alpha = (1f - (abs(relPos) - 4.2f) / 2f).coerceIn(0f, 1f)
                }
                .then(if (discStyle == "md" || isCassetteStyle(discStyle) || (isCaseStyle(discStyle) && !caseCircleClip)) Modifier else Modifier.clip(CircleShape)),
            contentAlignment = Alignment.Center
        ) {
            val art = artAt(targetIdx)
            val artImage = remember(art) { art?.asImageBitmap() }
            val itemAccent = art?.let { synchronized(rackAccentCache) { rackAccentCache.getOrPut(it) { rackAccentOf(it) } } } ?: effAccent
            val itemLineArt = art?.let { synchronized(rackLineArtCache) { rackLineArtCache.getOrPut(it) { extractLineArtBitmap(it)?.asImageBitmap() } } }
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val outlineColor = if (isFocused) itemAccent else Color.White.copy(alpha = 0.5f)
                val outlineWidth = if (isFocused) 3f else 1.5f
                if (discStyle == "md" || isCassetteStyle(discStyle) || (isCaseStyle(discStyle) && !caseCircleClip)) {
                    val caseW = size.width * 0.92f
                    val caseH = caseW * (when (discStyle) { "md" -> 0.94f; "floppy" -> 0.96f; "reel" -> 0.92f; "dat" -> 0.72f; "eighttrack" -> 0.78f; else -> 0.64f })
                    drawRoundRect(
                        color = outlineColor,
                        topLeft = Offset(center.x - caseW / 2f, center.y - caseH / 2f),
                        size = androidx.compose.ui.geometry.Size(caseW, caseH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
                        style = Stroke(width = outlineWidth)
                    )
                } else {
                    drawCircle(
                        color = outlineColor,
                        radius = size.width / 2f,
                        center = center,
                        style = Stroke(width = outlineWidth)
                    )
                }
            }
            when (discStyle) {
                "md" -> MdCase(
                    boxSizeDp = discRadiusDp * 2f * 0.92f,
                    accentColor = itemAccent,
                    rotationState = stillSpinState,
                    bitmapImage = null,
                    lineArtImage = itemLineArt,
                    title = ""
                )
                "reel" -> ReelCase(
                    boxSizeDp = discRadiusDp * 2f * 0.92f,
                    accentColor = itemAccent,
                    rotationState = stillSpinState,
                    title = titleAt(targetIdx)
                )
                "eighttrack" -> EightTrackCase(
                    boxSizeDp = discRadiusDp * 2f * 0.92f,
                    accentColor = itemAccent,
                    title = titleAt(targetIdx),
                    lineArtImage = itemLineArt
                )
                "dat" -> DatCase(
                    boxSizeDp = discRadiusDp * 2f * 0.92f,
                    accentColor = itemAccent,
                    rotationState = stillSpinState,
                    title = titleAt(targetIdx)
                )
                "floppy" -> FloppyCase(
                    boxSizeDp = discRadiusDp * 2f * 0.92f,
                    accentColor = itemAccent,
                    title = titleAt(targetIdx),
                    lineArtImage = itemLineArt
                )
                "cassette_color", "cassette_clear", "cassette_black" -> CassetteCase(
                    boxSizeDp = discRadiusDp * 2f * 0.92f,
                    accentColor = itemAccent,
                    rotationState = stillSpinState,
                    title = titleAt(targetIdx),
                    artist = "",
                    shellStyle = cassetteShellOf(discStyle),
                    lineArtImage = itemLineArt
                )
                else -> Box(
                    modifier = Modifier
                        .size((discRadiusDp * 2f * 0.92f).dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    when (discStyle) {
                        "vinyl" -> {
                            Canvas(Modifier.fillMaxSize()) {
                                val rMax = size.minDimension / 2f
                                drawCircle(Color(0xFF0A0A0C))
                                for (i in 1..9) {
                                    drawCircle(
                                        Color.White.copy(alpha = if (i % 3 == 0) 0.10f else 0.05f),
                                        radius = rMax * (0.50f + i * 0.052f),
                                        style = Stroke(width = 1.2f)
                                    )
                                }
                            }
                            if (artImage != null) {
                                Image(bitmap = artImage, contentDescription = null, modifier = Modifier.fillMaxSize(0.44f).clip(CircleShape))
                            }
                        }
                        "clear" -> {
                            Canvas(Modifier.fillMaxSize()) {
                                drawCircle(vividColorOf(itemAccent).copy(alpha = 0.36f))
                                drawCircle(Color.Black.copy(alpha = 0.10f))
                            }
                            if (artImage != null) {
                                Image(bitmap = artImage, contentDescription = null, modifier = Modifier.fillMaxSize(0.44f).clip(CircleShape))
                            }
                        }
                        "glow" -> {
                            Canvas(Modifier.fillMaxSize()) {
                                drawCircle(lightGlowOf(itemAccent))
                            }
                            if (artImage != null) {
                                Image(bitmap = artImage, contentDescription = null, modifier = Modifier.fillMaxSize(0.44f).clip(CircleShape))
                            }
                        }
                        "cd" -> {
                            if (artImage != null) {
                                Image(bitmap = artImage, contentDescription = null, modifier = Modifier.fillMaxSize())
                            } else {
                                Canvas(Modifier.fillMaxSize()) { drawCircle(Color(0xFFB9BEC6)) }
                            }
                            Canvas(Modifier.fillMaxSize()) {
                                val rMax = size.minDimension / 2f
                                drawCircle(
                                    Brush.sweepGradient(
                                        listOf(
                                            Color(0xFFFF9D9D).copy(alpha = 0.20f),
                                            Color(0xFFFFFF9D).copy(alpha = 0.20f),
                                            Color(0xFF9DFF9D).copy(alpha = 0.20f),
                                            Color(0xFF9DFFFF).copy(alpha = 0.20f),
                                            Color(0xFF9D9DFF).copy(alpha = 0.20f),
                                            Color(0xFFFF9DFF).copy(alpha = 0.20f),
                                            Color(0xFFFF9D9D).copy(alpha = 0.20f)
                                        )
                                    )
                                )
                                drawCircle(Color(0xFF3A3F45), radius = rMax * 0.075f)
                            }
                        }
                        else -> {
                            if (artImage != null) {
                                Image(
                                    bitmap = artImage,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawCircle(color = itemAccent.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
            }
        }
        // 各ディスクの右に曲名を表示
        Text(
            text = titleAt(targetIdx),
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.65f),
            fontSize = if (isFocused) 14.sp else 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .offset {
                    val relPos = targetIdx - (rackCurrentIndex + rackScrollAnim.value)
                    val gapOffset = sign(relPos) * 24f * min(1f, abs(relPos))
                    val yPosDp = discCenterYDp + (relPos * 42f) + gapOffset
                    val focusBump = (1f - min(1f, abs(relPos))) * 30f
                    val xShiftDp = relPos * 7f + focusBump
                    IntOffset(
                        x = ((discCenterXDp + discRadiusDp + xShiftDp + 10f) * density).roundToInt(),
                        y = ((yPosDp - 9f) * density).roundToInt()
                    )
                }
                .graphicsLayer {
                    val relPos = targetIdx - (rackCurrentIndex + rackScrollAnim.value)
                    alpha = (1f - (abs(relPos) - 4.2f) / 2f).coerceIn(0f, 1f)
                }
                .width(105.dp)
        )
    }
}

private fun vividColorOf(c: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (c.red * 255).toInt().coerceIn(0, 255),
        (c.green * 255).toInt().coerceIn(0, 255),
        (c.blue * 255).toInt().coerceIn(0, 255),
        hsv
    )
    hsv[1] = 1f
    hsv[2] = 1f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun effectiveDiscStyle(prefManager: AppPreferenceManager): String {
    val raw = prefManager.getDiscStyle()
    if (raw != "cassette") return raw
    return when (prefManager.getCassetteStyle()) {
        "color" -> "cassette_color"
        "black" -> "cassette_black"
        else -> "cassette_clear"
    }
}

private fun isCassetteStyle(style: String): Boolean = style == "cassette" || style.startsWith("cassette_")

private fun isCaseStyle(style: String): Boolean = style == "md" || isCassetteStyle(style) || style == "reel" || style == "floppy" || style == "dat" || style == "eighttrack"

private fun cassetteShellOf(style: String): String = when (style) {
    "cassette_color" -> "color"
    "cassette_black" -> "black"
    else -> "clear"
}

private val rackAccentCache = object : LinkedHashMap<Bitmap, Color>(32, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Bitmap, Color>): Boolean = size > 64
}

private val rackLineArtCache = object : LinkedHashMap<Bitmap, ImageBitmap?>(32, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Bitmap, ImageBitmap?>): Boolean = size > 64
}

private fun rackAccentOf(bitmap: Bitmap): Color {
    val size = 48
    val scaled = if (bitmap.width == size && bitmap.height == size) bitmap else Bitmap.createScaledBitmap(bitmap, size, size, true)
    val bucketCount = 12
    val counts = IntArray(bucketCount)
    val satSums = FloatArray(bucketCount)
    val rSums = LongArray(bucketCount)
    val gSums = LongArray(bucketCount)
    val bSums = LongArray(bucketCount)
    val hsv = FloatArray(3)
    var totalR = 0L; var totalG = 0L; var totalB = 0L
    val totalPx = size * size
    for (y in 0 until size) {
        for (x in 0 until size) {
            val px = scaled.getPixel(x, y)
            val r = android.graphics.Color.red(px)
            val g = android.graphics.Color.green(px)
            val b = android.graphics.Color.blue(px)
            totalR += r; totalG += g; totalB += b
            android.graphics.Color.RGBToHSV(r, g, b, hsv)
            if (hsv[1] > 0.15f && hsv[2] > 0.15f) {
                val bucket = (hsv[0] / 30f).toInt().coerceIn(0, bucketCount - 1)
                counts[bucket]++
                satSums[bucket] += hsv[1]
                rSums[bucket] += r; gSums[bucket] += g; bSums[bucket] += b
            }
        }
    }
    if (scaled !== bitmap) scaled.recycle()
    var bestBucket = -1
    var bestScore = -1f
    for (i in 0 until bucketCount) {
        if (counts[i] == 0) continue
        val score = counts[i] * (satSums[i] / counts[i])
        if (score > bestScore) { bestScore = score; bestBucket = i }
    }
    var accent = if (bestBucket >= 0) {
        Color(
            red = (rSums[bestBucket] / counts[bestBucket]).toInt(),
            green = (gSums[bestBucket] / counts[bestBucket]).toInt(),
            blue = (bSums[bestBucket] / counts[bestBucket]).toInt()
        )
    } else {
        Color(
            red = (totalR / totalPx).toInt(),
            green = (totalG / totalPx).toInt(),
            blue = (totalB / totalPx).toInt()
        )
    }
    val accentLum = 0.299f * accent.red + 0.587f * accent.green + 0.114f * accent.blue
    if (accentLum < 0.22f) {
        accent = Color(
            red = accent.red + (1f - accent.red) * 0.35f,
            green = accent.green + (1f - accent.green) * 0.35f,
            blue = accent.blue + (1f - accent.blue) * 0.35f
        )
    }
    return accent
}

private fun lightGlowOf(c: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (c.red * 255).toInt().coerceIn(0, 255),
        (c.green * 255).toInt().coerceIn(0, 255),
        (c.blue * 255).toInt().coerceIn(0, 255),
        hsv
    )
    hsv[1] = (hsv[1] * 0.45f).coerceIn(0f, 1f)
    hsv[2] = 1f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun extractLineArtBitmap(src: Bitmap?): Bitmap? {
    if (src == null) return null
    return try {
        val w = 112
        val h = (w * src.height / src.width.coerceAtLeast(1)).coerceIn(16, 160)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val gray = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val px = small.getPixel(x, y)
                gray[y * w + x] = (0.299f * android.graphics.Color.red(px) + 0.587f * android.graphics.Color.green(px) + 0.114f * android.graphics.Color.blue(px)).toInt()
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val gx = if (x + 1 < w) kotlin.math.abs(gray[y * w + x + 1] - gray[y * w + x]) else 0
                val gy = if (y + 1 < h) kotlin.math.abs(gray[(y + 1) * w + x] - gray[y * w + x]) else 0
                val a = ((gx + gy - 26) * 3).coerceIn(0, 255)
                out.setPixel(x, y, android.graphics.Color.argb(a, 18, 18, 22))
            }
        }
        out
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun MdCase(boxSizeDp: Float, accentColor: Color, rotationState: State<Float>, bitmapImage: ImageBitmap?, lineArtImage: ImageBitmap?, title: String) {
    val caseW = boxSizeDp
    val caseH = boxSizeDp * 0.94f
    Box(
        modifier = Modifier
            .size(caseW.dp, caseH.dp)
            .clip(RoundedCornerShape(10.dp))
    ) {
        // 透けて見えるディスク本体（回転する）
        Box(
            modifier = Modifier
                .size((caseW * 0.88f).dp)
                .align(Alignment.Center)
                .offset(x = (-caseW * 0.03f).dp, y = (caseH * 0.02f).dp)
                .graphicsLayer {
                    rotationZ = rotationState.value
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                }
                .clip(CircleShape)
                .background(Color(0xFFB9BEC6)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val rMax = size.minDimension / 2f
                drawCircle(
                    Brush.sweepGradient(
                        listOf(
                            Color(0xFFFF9D9D).copy(alpha = 0.35f),
                            Color(0xFFFFFF9D).copy(alpha = 0.35f),
                            Color(0xFF9DFF9D).copy(alpha = 0.35f),
                            Color(0xFF9DFFFF).copy(alpha = 0.35f),
                            Color(0xFF9D9DFF).copy(alpha = 0.35f),
                            Color(0xFFFF9DFF).copy(alpha = 0.35f),
                            Color(0xFFFF9D9D).copy(alpha = 0.35f)
                        )
                    )
                )
                for (i in 1..6) {
                    drawCircle(Color.White.copy(alpha = 0.10f), radius = rMax * (0.46f + i * 0.085f), style = Stroke(width = 1f))
                }
                drawCircle(Color(0xFFD8D8D8).copy(alpha = 0.9f), radius = rMax * 0.16f, style = Stroke(width = 3f))
                drawCircle(Color(0xFF9FA5AD), radius = rMax * 0.21f)
                drawCircle(Color(0xFFC9CDD4), radius = rMax * 0.17f)
                drawCircle(Color(0xFF878D94).copy(alpha = 0.8f), radius = rMax * 0.13f, style = Stroke(width = 2f))
                drawCircle(Color(0xFFD8D8D8).copy(alpha = 0.9f), radius = rMax * 0.095f, style = Stroke(width = 2f))
            }
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color.Black, radius = size.minDimension / 2f * 0.075f, blendMode = androidx.compose.ui.graphics.BlendMode.Clear)
            }
            Text(
                text = title,
                color = Color(0xFF3A3F45).copy(alpha = 0.75f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-boxSizeDp * 0.27f).dp)
                    .width((boxSizeDp * 0.50f).dp)
            )
        }
        // 半透明の色つきケース（アクセント色）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(accentColor.copy(alpha = 0.60f), accentColor.copy(alpha = 0.42f), accentColor.copy(alpha = 0.55f))
                    )
                )
                .border(1.dp, accentColor.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
        )
        Canvas(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 5.dp)
                .size(14.dp)
        ) {
            val tri = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width / 2f, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(tri, Color.White.copy(alpha = 0.55f), style = Stroke(width = 1.5f))
        }
        Box(modifier = Modifier.align(Alignment.TopStart).padding(7.dp).size(9.dp).clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.30f), CircleShape))
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).size(9.dp).clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.30f), CircleShape))
        // シャッター（金属プレート、右中央）
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size((caseW * 0.46f).dp, (caseH * 0.36f).dp)
                .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFC9CED4), Color(0xFFF4F6F8), Color(0xFFAEB4BC), Color(0xFFD9DDE2))
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(Color(0xFF7A7F86))
            )
            if (lineArtImage != null) {
                Image(
                    bitmap = lineArtImage,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(start = 6.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                    contentScale = ContentScale.Fit
                )
            }
            Text(
                text = "74",
                color = Color(0xFF3A3F45),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 3.dp)
            )
        }
    }
}

@Composable
private fun CassetteReel(reelSizeDp: Float, rotationState: State<Float>) {
    Box(
        modifier = Modifier
            .size(reelSizeDp.dp)
            .graphicsLayer { rotationZ = rotationState.value }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(Color(0xFFE9E9E9), radius = r)
            drawCircle(Color(0xFF2A2A2A), radius = r * 0.30f)
            for (i in 0 until 6) {
                val a = (i * 60f) * (Math.PI / 180f).toFloat()
                drawLine(
                    Color(0xFF2A2A2A),
                    Offset(center.x + cos(a) * r * 0.34f, center.y + sin(a) * r * 0.34f),
                    Offset(center.x + cos(a) * r * 0.94f, center.y + sin(a) * r * 0.94f),
                    strokeWidth = r * 0.18f
                )
            }
        }
    }
}

@Composable
private fun DatCase(boxSizeDp: Float, accentColor: Color, rotationState: State<Float>, title: String) {
    val caseW = boxSizeDp
    val caseH = boxSizeDp * 0.72f
    Box(
        modifier = Modifier
            .size(caseW.dp, caseH.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF2E2F36), Color(0xFF1B1C20))))
            .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
    ) {
        // 上部のフタ＋ラベル帯（アクセントのラインと曲名）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((caseH * 0.34f).dp)
                .background(accentColor.copy(alpha = 0.16f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(accentColor.copy(alpha = 0.85f))
            )
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = (caseW * 0.05f).dp, end = (caseW * 0.05f).dp)
                )
            }
            Text(
                text = "DAT",
                color = accentColor.copy(alpha = 0.9f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-(caseW * 0.04f)).dp, y = (-(caseH * 0.03f)).dp)
            )
        }
        // 下部の窓（テープと小さいリール2つ）
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-(caseH * 0.09f)).dp)
                .size((caseW * 0.62f).dp, (caseH * 0.44f).dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF101014).copy(alpha = 0.85f))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cy = size.height * 0.52f
                val lx = size.width * 0.28f
                val rx = size.width * 0.72f
                val pr = size.height * 0.34f
                drawRect(Color(0xFF4A3220), Offset(lx, cy - pr * 0.28f), Size(rx - lx, pr * 0.56f))
                drawCircle(Color(0xFF4A3220), radius = pr * 0.62f, center = Offset(lx, cy))
                drawCircle(Color(0xFF4A3220), radius = pr * 0.95f, center = Offset(rx, cy))
            }
            Box(modifier = Modifier.offset(x = (caseW * 0.62f * 0.28f - caseW * 0.085f).dp, y = (caseH * 0.44f * 0.52f - caseW * 0.085f).dp)) {
                CassetteReel(reelSizeDp = caseW * 0.17f, rotationState = rotationState)
            }
            Box(modifier = Modifier.offset(x = (caseW * 0.62f * 0.72f - caseW * 0.085f).dp, y = (caseH * 0.44f * 0.52f - caseW * 0.085f).dp)) {
                CassetteReel(reelSizeDp = caseW * 0.17f, rotationState = rotationState)
            }
        }
        // 下端の細かいリブ
        Canvas(modifier = Modifier.fillMaxSize()) {
            val y = size.height * 0.965f
            var x = size.width * 0.06f
            while (x < size.width * 0.94f) {
                drawLine(Color.White.copy(alpha = 0.13f), Offset(x, y - size.height * 0.018f), Offset(x, y), strokeWidth = size.width * 0.004f)
                x += size.width * 0.022f
            }
        }
    }
}

@Composable
private fun EightTrackCase(boxSizeDp: Float, accentColor: Color, title: String, lineArtImage: ImageBitmap? = null) {
    val caseW = boxSizeDp
    val caseH = boxSizeDp * 0.78f
    Box(
        modifier = Modifier
            .size(caseW.dp, caseH.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFEDEAE2), Color(0xFFD8D3C8))))
            .border(1.5.dp, Color(0xFF9A958A), RoundedCornerShape(12.dp))
    ) {
        // 上端のノッチ
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size((caseW * 0.16f).dp, (caseH * 0.045f).dp)
                .background(Color(0xFF3A3A3E), RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
        )
        // 大きなラベル
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (caseH * 0.10f).dp)
                .size((caseW * 0.86f).dp, (caseH * 0.56f).dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFFFBF8F1))
                .border(1.dp, Color(0xFFC9C2B2), RoundedCornerShape(5.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((caseH * 0.075f).dp)
                    .background(accentColor)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (caseW * 0.045f).dp, end = (caseW * 0.03f).dp, top = (caseH * 0.13f).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color(0xFF33302A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height((caseH * 0.05f).dp))
                    Box(modifier = Modifier.fillMaxWidth(0.92f).height(1.dp).background(Color(0xFFC9C2B2)))
                    Spacer(modifier = Modifier.height((caseH * 0.055f).dp))
                    Box(modifier = Modifier.fillMaxWidth(0.92f).height(1.dp).background(Color(0xFFC9C2B2)))
                }
                if (lineArtImage != null) {
                    Image(
                        bitmap = lineArtImage,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxHeight(0.70f)
                            .aspectRatio(1f),
                        contentScale = ContentScale.Fit,
                        alpha = 0.85f
                    )
                }
            }
        }
        // 下部のテープ開口部（テープの帯＋ピンチローラー＋圧着パッド）
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-(caseH * 0.055f)).dp)
                .size((caseW * 0.72f).dp, (caseH * 0.17f).dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF26262B))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((caseH * 0.045f).dp)
                    .align(Alignment.Center)
                    .background(Color(0xFF4A3220))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (caseW * 0.10f).dp)
                    .size((caseW * 0.075f).dp, (caseH * 0.075f).dp)
                    .background(Color(0xFF8A6D3F), RoundedCornerShape(2.dp))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-(caseW * 0.08f)).dp)
                    .size((caseH * 0.11f).dp)
                    .clip(CircleShape)
                    .background(Color(0xFFB9BEC6))
            )
        }
        // 四隅のネジ
        Canvas(modifier = Modifier.fillMaxSize()) {
            val m = size.width * 0.04f
            for (cc in listOf(Offset(m, m), Offset(size.width - m, m), Offset(m, size.height - m), Offset(size.width - m, size.height - m))) {
                drawCircle(Color(0xFF6E6A60), radius = size.width * 0.014f, center = cc)
            }
        }
    }
}

@Composable
private fun ReelCase(boxSizeDp: Float, accentColor: Color, rotationState: State<Float>, title: String) {
    val caseW = boxSizeDp
    val caseH = boxSizeDp * 0.92f
    Box(
        modifier = Modifier
            .size(caseW.dp, caseH.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF2A2B31), Color(0xFF17181C))))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
    ) {
        // テープの巻きと通り道（巻きは回転しても見た目が変わらないので静止画）
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cy = size.height * 0.44f
            val lx = size.width * 0.27f
            val rx = size.width * 0.73f
            val reelR = size.width * 0.205f
            val tape = Color(0xFF4A3220)
            drawLine(tape, Offset(lx, cy + reelR * 0.82f), Offset(size.width * 0.40f, size.height * 0.80f), strokeWidth = size.width * 0.018f)
            drawLine(tape, Offset(size.width * 0.40f, size.height * 0.80f), Offset(size.width * 0.60f, size.height * 0.80f), strokeWidth = size.width * 0.018f)
            drawLine(tape, Offset(size.width * 0.60f, size.height * 0.80f), Offset(rx, cy + reelR * 0.82f), strokeWidth = size.width * 0.018f)
            drawCircle(Color(0xFFB9BEC6), radius = size.width * 0.016f, center = Offset(size.width * 0.40f, size.height * 0.80f))
            drawCircle(Color(0xFFB9BEC6), radius = size.width * 0.016f, center = Offset(size.width * 0.60f, size.height * 0.80f))
            drawCircle(tape, radius = reelR * 0.55f, center = Offset(lx, cy))
            drawCircle(tape, radius = reelR * 0.88f, center = Offset(rx, cy))
            drawCircle(Color(0xFF6B4E2E), radius = reelR * 0.55f, center = Offset(lx, cy), style = Stroke(width = size.width * 0.006f))
            drawCircle(Color(0xFF6B4E2E), radius = reelR * 0.88f, center = Offset(rx, cy), style = Stroke(width = size.width * 0.006f))
        }
        // 回るリール（金属フランジ＋3つの窓＋ハブ）
        for (cxFrac in listOf(0.27f, 0.73f)) {
            Box(
                modifier = Modifier
                    .offset(x = (caseW * (cxFrac - 0.205f)).dp, y = (caseH * 0.44f - caseW * 0.205f).dp)
                    .size((caseW * 0.41f).dp)
                    .graphicsLayer { rotationZ = rotationState.value }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = size.width / 2f
                    drawCircle(Color(0xFFC9CED4), radius = r, center = c, style = Stroke(width = size.width * 0.045f))
                    drawCircle(Color(0xFF8A9099), radius = r * 0.94f, center = c, style = Stroke(width = size.width * 0.012f))
                    for (aDeg in listOf(90.0, 210.0, 330.0)) {
                        val rad = Math.toRadians(aDeg)
                        val wc = Offset(c.x + (Math.cos(rad) * r * 0.55f).toFloat(), c.y + (Math.sin(rad) * r * 0.55f).toFloat())
                        drawCircle(Color(0xFF17181C), radius = r * 0.21f, center = wc)
                        drawCircle(Color(0xFF8A9099), radius = r * 0.21f, center = wc, style = Stroke(width = size.width * 0.010f))
                    }
                    drawCircle(Color(0xFFD7DCE2), radius = r * 0.20f, center = c)
                    drawCircle(Color(0xFF37474F), radius = r * 0.075f, center = c)
                }
            }
        }
        // 再生ヘッド（下部中央の金属ブロック）
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-(caseH * 0.115f)).dp)
                .size((caseW * 0.13f).dp, (caseH * 0.085f).dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF9AA0A8), Color(0xFFE8ECF0), Color(0xFF9AA0A8))))
        )
        // 曲名（上部に印刷風）
        if (title.isNotBlank()) {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = (caseW * 0.10f).dp)
                    .offset(y = (caseH * 0.035f).dp)
            )
        }
        // 四隅のネジ
        Canvas(modifier = Modifier.fillMaxSize()) {
            val m = size.width * 0.045f
            for (cc in listOf(Offset(m, m), Offset(size.width - m, m), Offset(m, size.height - m), Offset(size.width - m, size.height - m))) {
                drawCircle(Color(0xFF6E747C), radius = size.width * 0.016f, center = cc)
            }
        }
    }
}

@Composable
private fun FloppyCase(boxSizeDp: Float, accentColor: Color, title: String, lineArtImage: ImageBitmap? = null) {
    val caseW = boxSizeDp
    val caseH = boxSizeDp * 0.96f
    Box(
        modifier = Modifier
            .size(caseW.dp, caseH.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(accentColor.copy(alpha = 0.55f), accentColor.copy(alpha = 0.38f))))
            .border(1.dp, accentColor.copy(alpha = 0.70f), RoundedCornerShape(10.dp))
    ) {
        // 金属シャッター（上部）
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (caseH * 0.045f).dp)
                .size((caseW * 0.54f).dp, (caseH * 0.30f).dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFFB9BEC6), Color(0xFFF2F4F6), Color(0xFFB9BEC6))))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = (-(caseW * 0.045f)).dp)
                    .size((caseW * 0.10f).dp, (caseH * 0.16f).dp)
                    .background(Color(0xFF3A3F45), RoundedCornerShape(2.dp))
            )
        }
        // ラベル（下部）
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-(caseH * 0.05f)).dp)
                .size((caseW * 0.86f).dp, (caseH * 0.44f).dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFF2EDE3))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(accentColor)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (caseW * 0.045f).dp, end = (caseW * 0.03f).dp, top = (caseH * 0.055f).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color(0xFF3A3F45),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height((caseH * 0.045f).dp))
                    Box(modifier = Modifier.fillMaxWidth(0.92f).height(1.dp).background(Color(0xFFB9B2A4)))
                    Spacer(modifier = Modifier.height((caseH * 0.05f).dp))
                    Box(modifier = Modifier.fillMaxWidth(0.92f).height(1.dp).background(Color(0xFFB9B2A4)))
                }
                if (lineArtImage != null) {
                    Image(
                        bitmap = lineArtImage,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxHeight(0.72f)
                            .aspectRatio(1f),
                        contentScale = ContentScale.Fit,
                        alpha = 0.85f
                    )
                }
            }
        }
        // 書き込み禁止タブ（左下）
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (caseW * 0.045f).dp, y = (-(caseH * 0.035f)).dp)
                .size((caseW * 0.055f).dp)
                .background(Color(0xFF26262D), RoundedCornerShape(1.dp))
        )
    }
}

@Composable
private fun CassetteCase(boxSizeDp: Float, accentColor: Color, rotationState: State<Float>, title: String, artist: String, shellStyle: String = "clear", lineArtImage: ImageBitmap? = null) {
    val caseW = boxSizeDp
    val caseH = boxSizeDp * 0.64f
    val packR = caseH * 0.30f
    val shellBrush = when (shellStyle) {
        "color" -> Brush.verticalGradient(listOf(accentColor.copy(alpha = 0.45f), accentColor.copy(alpha = 0.28f)))
        "black" -> Brush.verticalGradient(listOf(Color(0xFF26262D).copy(alpha = 0.96f), Color(0xFF101014).copy(alpha = 0.96f)))
        else -> Brush.verticalGradient(listOf(Color(0xFFD8DCE2).copy(alpha = 0.30f), Color(0xFF9AA0A8).copy(alpha = 0.20f)))
    }
    val shellBorder = when (shellStyle) {
        "color" -> accentColor.copy(alpha = 0.65f)
        "black" -> Color(0xFF45454F)
        else -> Color.White.copy(alpha = 0.40f)
    }
    val tapeColor = if (shellStyle == "black") Color(0xFF4A3421) else Color(0xFF241A12)
    val tapeRing = if (shellStyle == "black") Color(0xFF6B4E2E) else Color(0xFF3A2A1A)
    Box(
        modifier = Modifier
            .size(caseW.dp, caseH.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(shellBrush)
            .border(1.dp, shellBorder, RoundedCornerShape(12.dp))
    ) {
        if (shellStyle == "black") {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (caseH * 0.02f).dp)
                    .size((caseW * 0.66f).dp, (caseH * 0.56f).dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
            )
        }
        // テープの帯と巻き（左右で量が違う）
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(caseH.dp)
        ) {
            val cy = size.height * 0.52f
            val lx = size.width * 0.26f
            val rx = size.width * 0.74f
            val pr = packR.dp.toPx()
            drawRect(tapeColor, Offset(lx, cy - pr * 0.30f), androidx.compose.ui.geometry.Size(rx - lx, pr * 0.60f))
            drawCircle(tapeColor, radius = pr * 0.62f, center = Offset(lx, cy))
            drawCircle(tapeColor, radius = pr, center = Offset(rx, cy))
            drawCircle(tapeRing, radius = pr * 0.56f, center = Offset(lx, cy), style = Stroke(width = 2f))
            drawCircle(tapeRing, radius = pr * 0.94f, center = Offset(rx, cy), style = Stroke(width = 2f))
        }
        // リール
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = (caseW * 0.26f - caseH * 0.17f).dp)
                .offset(y = (caseH * 0.02f).dp)
        ) {
            CassetteReel(reelSizeDp = caseH * 0.34f, rotationState = rotationState)
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = (caseW * 0.26f - caseH * 0.17f).dp)
                .offset(y = (caseH * 0.02f).dp)
        ) {
            CassetteReel(reelSizeDp = caseH * 0.34f, rotationState = rotationState)
        }
        // ラベルシール（クリーム色の帯、曲名入り）
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .size((caseW * 0.92f).dp, (caseH * 0.30f).dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFF2EDE3))
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(accentColor))
            Column(
                modifier = Modifier.align(Alignment.Center).padding(start = 10.dp, end = if (lineArtImage != null) (caseH * 0.30f).dp else 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title.ifBlank { "NO TITLE" },
                    color = Color(0xFF222222),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(text = artist, color = Color(0xFF777777), fontSize = 10.sp, maxLines = 1)
            }
            if (lineArtImage != null) {
                Image(
                    bitmap = lineArtImage,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 26.dp, top = 4.dp)
                        .fillMaxHeight(0.78f)
                        .aspectRatio(1f)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(16.dp)
                    .background(accentColor),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "A", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        // 下の台形まわり: 穴2つ
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 7.dp)
                .size((caseW * 0.54f).dp, (caseH * 0.17f).dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(4.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(11.dp).clip(CircleShape).background(Color(0xFF101114)))
                Box(modifier = Modifier.size((caseW * 0.16f).dp, 5.dp).background(Color(0xFF101114)))
                Box(modifier = Modifier.size(11.dp).clip(CircleShape).background(Color(0xFF101114)))
            }
        }
        // 四隅のネジ
        Box(modifier = Modifier.align(Alignment.TopStart).padding(5.dp).size(7.dp).clip(CircleShape).background(Color(0xFF6E747C)))
        Box(modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(7.dp).clip(CircleShape).background(Color(0xFF6E747C)))
        Box(modifier = Modifier.align(Alignment.BottomStart).padding(5.dp).size(7.dp).clip(CircleShape).background(Color(0xFF6E747C)))
        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).size(7.dp).clip(CircleShape).background(Color(0xFF6E747C)))
    }
}
