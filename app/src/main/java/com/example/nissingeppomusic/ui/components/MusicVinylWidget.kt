package com.example.nissingeppomusic.ui.components

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.graphics.Bitmap
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nissingeppomusic.data.AppPreferenceManager
import com.example.nissingeppomusic.service.LauncherMediaListenerService
import kotlinx.coroutines.delay

fun isNotificationServiceEnabled(context: Context): Boolean {
    val packageName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat?.contains(packageName) == true
}

@Composable
fun MusicVinylWidget(
    modifier: Modifier = Modifier,
    discCenterXDp: Float,
    discCenterYDp: Float,
    discRadiusDp: Float = 165f,
    accentColor: Color = Color(0xFFE53935)
) {
    val context = LocalContext.current
    val prefManager = remember { AppPreferenceManager(context) }
    var isPlaying by remember { mutableStateOf(false) }
    var albumArtBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var trackTitle by remember { mutableStateOf("") }
    var trackArtist by remember { mutableStateOf("") }
    var trackAlbum by remember { mutableStateOf("") }
    var activePackageName by remember { mutableStateOf<String?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    val mediaSessionManager = remember { context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val listenerComponent = remember { ComponentName(context, LauncherMediaListenerService::class.java) }

    // 定期的に音楽再生状態とメタデータ（アルバムアート・曲情報）を取得
    LaunchedEffect(Unit) {
        while (true) {
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
                        val metadata = activeController.metadata
                        val bitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                        albumArtBitmap = bitmap
                        trackTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
                        trackArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                        trackAlbum = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
                    } else {
                        isPlaying = audioManager?.isMusicActive == true
                        albumArtBitmap = null
                        activePackageName = null
                        trackTitle = ""
                        trackArtist = ""
                        trackAlbum = ""
                    }
                } catch (_: Exception) {
                    isPlaying = audioManager?.isMusicActive == true
                    albumArtBitmap = null
                    activePackageName = null
                    trackTitle = ""
                    trackArtist = ""
                    trackAlbum = ""
                }
            } else {
                isPlaying = audioManager?.isMusicActive == true
                albumArtBitmap = null
                activePackageName = null
                trackTitle = ""
                trackArtist = ""
                trackAlbum = ""
            }
            delay(1500)
        }
    }

    // 再生中の自動回転アニメーション (5秒で360度回転)
    val infiniteTransition = rememberInfiniteTransition(label = "VinylRotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    val currentRotation = if (isPlaying) rotation else 0f

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

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .offset(x = (discCenterXDp - discRadiusDp).dp, y = (discCenterYDp - discRadiusDp).dp)
                .size((discRadiusDp * 2f).dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (!isNotificationServiceEnabled(context)) {
                        showPermissionDialog = true
                    } else {
                        var launched = false
                        val appContext = context.applicationContext

                        fun launchTaskIntent(intent: Intent): Boolean {
                            return try {
                                intent.addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                                            Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                                )
                                appContext.startActivity(intent)
                                true
                            } catch (_: Exception) {
                                false
                            }
                        }

                        // 1. アクティブな音楽アプリを優先起動
                        val activePkg = activePackageName
                        if (!activePkg.isNullOrBlank() && activePkg != context.packageName) {
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(activePkg)
                            if (launchIntent != null) {
                                launched = launchTaskIntent(launchIntent)
                            }
                        }

                        // 2. メディアプレーヤー標準/カテゴリインテント
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

                        // 3. 主要音楽アプリの起動試行
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
                },
            contentAlignment = Alignment.Center
        ) {
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

            // 大サイズ（円サイズの92%）の回転するアートワーク / レーベル部分
            Box(
                modifier = Modifier
                    .size((discRadiusDp * 2f * 0.92f).dp)
                    .graphicsLayer {
                        rotationZ = currentRotation
                    }
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = albumArtBitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // フォールバックのスタイリッシュなレーベル
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(accentColor, accentColor.copy(alpha = 0.6f)),
                                center = center,
                                radius = size.width / 2f
                            ),
                            radius = size.width / 2f,
                            center = center
                        )
                    }
                }

                // センターホール（スピンドル穴）
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0B101D)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
        }
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
    val customTypeface = remember(customFontPath) {
        if (!customFontPath.isNullOrBlank()) {
            try {
                val file = java.io.File(customFontPath)
                if (file.exists()) android.graphics.Typeface.createFromFile(file) else null
            } catch (_: Exception) {
                null
            }
        } else null
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
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
                    -90f,
                    180f
                )
            }
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 11.sp.toPx()
                isAntiAlias = true
                letterSpacing = 0.15f
                textAlign = android.graphics.Paint.Align.CENTER
                setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                if (customTypeface != null) {
                    typeface = customTypeface
                }
            }
            drawContext.canvas.nativeCanvas.drawTextOnPath(displayInfo, textPath, 0f, 0f, paint)
        }
    }
}
