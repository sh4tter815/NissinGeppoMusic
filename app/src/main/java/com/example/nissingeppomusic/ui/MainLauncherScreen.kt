package com.example.nissingeppomusic.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.nissingeppomusic.data.AppInfo
import com.example.nissingeppomusic.data.AppRepository
import com.example.nissingeppomusic.data.LyricLine
import com.example.nissingeppomusic.data.LyricsManager
import com.example.nissingeppomusic.data.MusicPlayerManager
import com.example.nissingeppomusic.data.UpdateChecker
import com.example.nissingeppomusic.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*

/** 表示名（アーティスト名・アルバム名）から頭文字（日本語行／英字）を求める。MusicRepository.extractInitials と同じ規則 */
private fun initialsOfLabel(label: String): Pair<String, String> {
    val firstChar = label.firstOrNull() ?: return Pair("＃", "＃")
    val englishInitial = if (firstChar.isLetter() && firstChar.code < 128) {
        firstChar.uppercaseChar().toString()
    } else {
        "＃"
    }
    return Pair(japaneseGroupOf(firstChar), englishInitial)
}

/** カタカナをひらがなに寄せて小文字化する（五十音順ソート用）。MusicRepository.toHiragana と同じ変換 */
private fun hiraganaSortKey(text: String): String {
    return text.lowercase().map { ch ->
        val code = ch.code
        if (code in 0x30A1..0x30FA) (code - 0x60).toChar() else ch
    }.joinToString("")
}

private fun japaneseGroupOf(ch: Char): String {
    val code = ch.code
    val hiraChar = when (code) {
        in 0x30A1..0x30FA -> (code - 0x60).toChar()
        else -> ch
    }
    return when (hiraChar) {
        'あ', 'い', 'う', 'え', 'お', 'ぁ', 'ぃ', 'ぅ', 'ぇ', 'ぉ' -> "あ"
        'か', 'き', 'く', 'け', 'こ', 'が', 'ぎ', 'ぐ', 'げ', 'ご' -> "か"
        'さ', 'し', 'す', 'せ', 'そ', 'ざ', 'じ', 'ず', 'ぜ', 'ぞ' -> "さ"
        'た', 'ち', 'つ', 'て', 'と', 'だ', 'ぢ', 'づ', 'で', 'ど', 'っ' -> "た"
        'な', 'に', 'ぬ', 'ね', 'の' -> "な"
        'は', 'ひ', 'ふ', 'へ', 'ほ', 'ば', 'び', 'ぶ', 'べ', 'ぼ', 'ぱ', 'ぴ', 'ぷ', 'ぺ', 'ぽ' -> "は"
        'ま', 'み', 'む', 'め', 'も' -> "ま"
        'や', 'ゆ', 'よ', 'ゃ', 'ゅ', 'ょ' -> "や"
        'ら', 'り', 'る', 'れ', 'ろ' -> "ら"
        'わ', 'を', 'ん' -> "わ"
        else -> {
            if (ch.isLetter() && ch.code < 128) ch.uppercaseChar().toString() else "＃"
        }
    }
}

/**
 * アートワークからテーマカラーを抽出する（追加ライブラリなしの簡易実装）。
 * 戻り値: (アクセント色=前景, 背景色)
 * アクセントは「画素数 × 平均彩度」が最大の色相グループの平均色、
 * 背景は暗い画素の平均色（なければ全体平均を暗く落とす）。
 */
/** 相対輝度ベースのコントラスト比（WCAG方式、1.0〜21.0） */
private fun contrastRatio(a: Color, b: Color): Float {
    fun luminance(c: Color): Float {
        fun lin(v: Float): Float = if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
        return 0.2126f * lin(c.red) + 0.7152f * lin(c.green) + 0.0722f * lin(c.blue)
    }
    val l1 = luminance(a)
    val l2 = luminance(b)
    return (max(l1, l2) + 0.05f) / (min(l1, l2) + 0.05f)
}

private fun extractArtworkColors(bitmap: Bitmap): Pair<Color, Color> {
    val size = 48
    val scaled = if (bitmap.width == size && bitmap.height == size) bitmap
        else Bitmap.createScaledBitmap(bitmap, size, size, true)
    val bucketCount = 12
    val counts = IntArray(bucketCount)
    val satSums = FloatArray(bucketCount)
    val rSums = LongArray(bucketCount)
    val gSums = LongArray(bucketCount)
    val bSums = LongArray(bucketCount)
    val hsv = FloatArray(3)
    var darkCount = 0
    var darkR = 0L; var darkG = 0L; var darkB = 0L
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
            val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            if (lum < 0.35f) {
                darkCount++; darkR += r; darkG += g; darkB += b
            }
        }
    }
    if (scaled !== bitmap) scaled.recycle()

    var bestBucket = -1
    var bestScore = -1f
    for (i in 0 until bucketCount) {
        if (counts[i] == 0) continue
        val score = counts[i] * (satSums[i] / counts[i])
        if (score > bestScore) {
            bestScore = score
            bestBucket = i
        }
    }
    var accent = if (bestBucket >= 0) {
        Color(
            red = (rSums[bestBucket] / counts[bestBucket]).toInt(),
            green = (gSums[bestBucket] / counts[bestBucket]).toInt(),
            blue = (bSums[bestBucket] / counts[bestBucket]).toInt()
        )
    } else {
        // 無彩色寄りのアートワークでは全体の平均色を使う
        Color(
            red = (totalR / totalPx).toInt(),
            green = (totalG / totalPx).toInt(),
            blue = (totalB / totalPx).toInt()
        )
    }
    // 暗すぎるアクセントは白と混ぜて視認性を上げる
    val accentLum = 0.299f * accent.red + 0.587f * accent.green + 0.114f * accent.blue
    if (accentLum < 0.22f) {
        accent = Color(
            red = accent.red + (1f - accent.red) * 0.35f,
            green = accent.green + (1f - accent.green) * 0.35f,
            blue = accent.blue + (1f - accent.blue) * 0.35f
        )
    }

    var background = if (darkCount > 0) {
        Color(
            red = (darkR / darkCount).toInt(),
            green = (darkG / darkCount).toInt(),
            blue = (darkB / darkCount).toInt()
        )
    } else {
        Color(
            red = (totalR / totalPx).toInt(),
            green = (totalG / totalPx).toInt(),
            blue = (totalB / totalPx).toInt()
        )
    }
    // 背景が明るすぎる場合は文字が読める明るさまで落とす
    val bgLum = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
    if (bgLum > 0.16f) {
        val f = 0.16f / bgLum
        background = Color(red = background.red * f, green = background.green * f, blue = background.blue * f)
    }
    return accent to background
}

@Composable
fun MainLauncherScreen() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current

    val insets = WindowInsets.systemBars.asPaddingValues()
    val topPaddingDp = insets.calculateTopPadding()
    val bottomPaddingDp = insets.calculateBottomPadding()

    val screenWidthDp = configuration.screenWidthDp.toFloat()
    val screenHeightDp = configuration.screenHeightDp.toFloat()

    val coroutineScope = rememberCoroutineScope()
    val repo = remember { AppRepository(context) }
    val musicPlayerManager = remember { MusicPlayerManager(context) }
    val keyboardController = LocalSoftwareKeyboardController.current

    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var favoriteApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var foldersMap by remember { mutableStateOf(repo.prefManager.getFoldersMap()) }

    val refreshApps: () -> Unit = {
        coroutineScope.launch {
            val installed = repo.getInstalledApps()
            allApps = installed
            favoriteApps = installed.filter { it.isFavorite && !it.isHidden }
            foldersMap = repo.prefManager.getFoldersMap()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            refreshApps()
        }
    }

    LaunchedEffect(Unit) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            refreshApps()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    // 通知権限（メディア通知を出すために必要）
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ページ管理 (0: 設定, 1: ホーム, 2: アプリ一覧, 3: フォルダ)
    var currentLauncherPage by remember { mutableIntStateOf(if (repo.prefManager.getStartupPage() == "last") repo.prefManager.getLastPage().coerceIn(0, 3) else 1) }
    LaunchedEffect(currentLauncherPage) { repo.prefManager.setLastPage(currentLauncherPage) }

    var accentHex by remember { mutableStateOf(repo.prefManager.getAccentColorHex()) }
    val manualAccentColor = remember(accentHex) { parseHexColor(accentHex) }

    // アートワーク連動カラー: 設定でONのとき、再生中の曲のアートワークから
    // アクセント色（前景）と背景色を抽出してテーマカラーにする
    var matchArtwork by remember { mutableStateOf(repo.prefManager.isMatchArtworkColor()) }
    var homeDiscLayout by remember { mutableStateOf(repo.prefManager.getHomeDiscLayout()) }
    var disc3d by remember { mutableStateOf(repo.prefManager.isDisc3dEnabled()) }
    var disc3dTilt by remember { mutableStateOf(repo.prefManager.isDisc3dTiltEnabled()) }
    // 傾き連動の現在値（ローパス済み）。graphicsLayer の中でのみ読むので再コンポーズは走らない
    val discTiltX = remember { mutableFloatStateOf(0f) }
    val discTiltY = remember { mutableFloatStateOf(0f) }
    DisposableEffect(disc3d, disc3dTilt, currentLauncherPage) {
        if (!disc3d || !disc3dTilt || currentLauncherPage !in 1..3) {
            discTiltX.value = 0f
            discTiltY.value = 0f
            return@DisposableEffect onDispose { }
        }
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sm == null || sensor == null) return@DisposableEffect onDispose { }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val k = 0.14f
                val targetY = (e.values[0] / 9.8f * 14f).coerceIn(-14f, 14f)
                val targetX = ((e.values[1] - 9.8f) / 9.8f * 14f).coerceIn(-14f, 14f)
                discTiltY.value += (targetY - discTiltY.value) * k
                discTiltX.value += (targetX - discTiltX.value) * k
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) { }
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    var liquidBackground by remember { mutableStateOf(repo.prefManager.isLiquidBackgroundEnabled()) }
    var liquidSpeed by remember { mutableFloatStateOf(repo.prefManager.getLiquidSpeed()) }
    var liquidAlpha by remember { mutableFloatStateOf(repo.prefManager.getLiquidAlpha()) }
    var wallpaperBlur by remember { mutableFloatStateOf(repo.prefManager.getWallpaperBlurDp()) }
    var pvFontScale by remember { mutableFloatStateOf(repo.prefManager.getPvFontScale()) }
    var pvTopOffset by remember { mutableFloatStateOf(repo.prefManager.getPvTopOffsetDp()) }
    var pvTextColorMode by remember { mutableStateOf(repo.prefManager.getPvTextColor()) }
    var pvRuby by remember { mutableStateOf(repo.prefManager.isPvRubyEnabled()) }
    var pvInterludeArt by remember { mutableStateOf(repo.prefManager.isPvInterludeArtEnabled()) }
    var lyricsMasterEnabled by remember { mutableStateOf(repo.prefManager.isLyricsEnabled()) }
    var dopagakiMode by remember { mutableStateOf(repo.prefManager.isDopagakiEnabled()) }
    var keepScreenOnPref by remember { mutableStateOf(repo.prefManager.isKeepScreenOn()) }
    var dialVisibility by remember { mutableStateOf(repo.prefManager.getDialVisibility()) }
    var pageTransitionSpeed by remember { mutableFloatStateOf(repo.prefManager.getPageTransitionSpeed()) }
    var dateDisplayMode by remember { mutableStateOf(repo.prefManager.getDateDisplayMode()) }
    var clockSeconds by remember { mutableStateOf(repo.prefManager.isClockShowSeconds()) }
    var clock12h by remember { mutableStateOf(repo.prefManager.isClock12h()) }
    var clockBattery by remember { mutableStateOf(repo.prefManager.isClockShowBattery()) }
    var dateWareki by remember { mutableStateOf(repo.prefManager.isDateWareki()) }
    var clockOffsetX by remember { mutableFloatStateOf(repo.prefManager.getClockOffsetXDp()) }
    var clockOffsetY by remember { mutableFloatStateOf(repo.prefManager.getClockOffsetYDp()) }
    var pvOffsetX by remember { mutableFloatStateOf(repo.prefManager.getPvOffsetXDp()) }
    var layoutEditMode by remember { mutableStateOf(false) }
    LaunchedEffect(keepScreenOnPref, musicPlayerManager.isPlaying) {
        view.keepScreenOn = keepScreenOnPref && musicPlayerManager.isPlaying
    }
    LaunchedEffect(Unit) {
        while (true) {
            val sleepEnd = repo.prefManager.getSleepTimerEndMs()
            if (sleepEnd > 0L && System.currentTimeMillis() >= sleepEnd) {
                musicPlayerManager.stopPlayback()
                repo.prefManager.setSleepTimerEndMs(0L)
            }
            delay(15000)
        }
    }
    LaunchedEffect(currentLauncherPage) {
        if (currentLauncherPage == 1 && repo.prefManager.consumeLayoutEditRequest()) {
            layoutEditMode = true
        }
    }
    var lyricsDisplayMode by remember { mutableStateOf(repo.prefManager.getLyricsDisplayMode()) }
    val lyricsPvMode = lyricsDisplayMode == "video"
    var lyricsPvFont by remember { mutableStateOf(repo.prefManager.getLyricsPvFont()) }
    var lyricsRefreshTick by remember { mutableStateOf(0) }
    // 歌詞PVの色フラッシュ: 色名ヒットの行で一時的にアクセント色をその色へ寄せる
    var pvFlashColor by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(pvFlashColor) {
        if (pvFlashColor != null) {
            delay(2400)
            pvFlashColor = null
        }
    }
    var artworkAccent by remember { mutableStateOf<Color?>(null) }
    var artworkBackground by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(musicPlayerManager.currentAlbumArt, matchArtwork) {
        val art = musicPlayerManager.currentAlbumArt
        if (matchArtwork && art != null) {
            val (fg, bg) = withContext(Dispatchers.Default) { extractArtworkColors(art) }
            artworkAccent = fg
            artworkBackground = bg
        } else {
            artworkAccent = null
            artworkBackground = null
        }
    }
    val accentColor = if (matchArtwork) {
        val extracted = artworkAccent
        val bg = artworkBackground
        if (extracted != null && bg != null && contrastRatio(extracted, bg) < 2.8f) {
            // コントラストが低い場合は、同じ色味のまま白へ寄せて読みやすくする（グローと併用）
            val fg: Color = extracted!!
            val bgNN: Color = bg!!
            var blended = fg
            var t = 0f
            while (contrastRatio(blended, bgNN) < 3.0f && t < 0.5f) {
                t += 0.1f
                blended = Color(
                    red = fg.red + (1f - fg.red) * t,
                    green = fg.green + (1f - fg.green) * t,
                    blue = fg.blue + (1f - fg.blue) * t,
                    alpha = fg.alpha
                )
            }
            blended
        } else {
            extracted ?: manualAccentColor
        }
    } else {
        manualAccentColor
    }
    // 色フラッシュ中はフラッシュ色へなめらかに寄せたアクセント色を使う
    val effectiveAccent by animateColorAsState(
        targetValue = pvFlashColor ?: accentColor,
        animationSpec = tween(durationMillis = 800),
        label = "pvAccentFlash"
    )

    // 抽出アクセント色と背景色のコントラストが低いときは、アクセント色の
    // アイコンや文字にグロー（発光）をかけて見やすくする
    // グローはアクセントの色味に関係なく常時ON（ユーザー希望）
    val accentGlow = true

    // 選曲されたら、その曲の原画（最大1000px）を読み込んでホーム表示を高画質化する。
    // 一覧やラックのアートは通常キャッシュのまま（軽さ優先）、ホームだけ原画に差し替わる
    LaunchedEffect(musicPlayerManager.currentSongUri) {
        val uri = musicPlayerManager.currentSongUri?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val path = musicPlayerManager.queue.getOrNull(musicPlayerManager.currentQueueIndex)?.filePath ?: ""
        val hq = withContext(Dispatchers.IO) { repo.loadOriginalArtwork(path, uri) }
        if (hq != null) musicPlayerManager.upgradeCurrentArtwork(uri, hq)
    }

    // ラック（左円のキュー表示）表示中は、右円と下ダイヤルを隠す
    var isRackShown by remember { mutableStateOf(false) }

    // 下ダイヤルを押して拡大している間は、リピート/シャッフルボタンを隠す
    var isDialPressed by remember { mutableStateOf(false) }

    // 歌詞ブラウズ（画面上部を長押し→歌詞全文をラック式にステップスクロール→離すとその行へジャンプ）
    var isLyricsBrowsing by remember { mutableStateOf(false) }
    // 歌詞フルスクリーン（横画面）: PVモードではPVだけ、1行/複数行モードでは曲名などと一緒に表示
    var pvFullscreen by remember { mutableStateOf(false) }

    // アプリ内アップデート: 起動時に新ビルドを確認する（6時間に1回まで）
    var updateInfo by remember { mutableStateOf<UpdateChecker.LatestRelease?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showLyricSearch by remember { mutableStateOf(false) }
    var lyricsSource by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val info = UpdateChecker.checkIfDue(context)
        if (info != null) {
            updateInfo = info
            showUpdateDialog = true
        }
    }
    // 歌詞エフェクトデータ: 端末キャッシュを読み込んでから、配信データの更新を確認する
    LaunchedEffect(Unit) {
        LyricEffectsData.init(context)
    }
    val pvActivity = LocalContext.current as? android.app.Activity
    LaunchedEffect(pvFullscreen) {
        pvActivity?.requestedOrientation = if (pvFullscreen) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    // 曲ごとの歌詞タイミング補正（ミリ秒）と、その調整パネルの表示状態
    var lyricOffsetMs by remember { mutableStateOf(0L) }
    LaunchedEffect(musicPlayerManager.currentTitle, musicPlayerManager.currentArtist) {
        lyricOffsetMs = repo.prefManager.getLyricOffsetMs(
            musicPlayerManager.currentTitle, musicPlayerManager.currentArtist
        )
    }
    var lyricsBrowseStartIndex by remember { mutableIntStateOf(0) }

    // 歌詞モード（ボタンだけ先に用意。歌詞ページは後で作る）
    var showLyrics by remember { mutableStateOf(true) }

    // 同期歌詞: 歌詞モードONの間、再生中の曲の同期歌詞を取得して現在の1行を表示する
    var lyricsLines by remember { mutableStateOf<List<LyricLine>?>(null) }
    var lyricsSongUri by remember { mutableStateOf<String?>(null) }
    var lyricsLoading by remember { mutableStateOf(false) }
    var lyricsNotFound by remember { mutableStateOf(false) }
    // 歌詞が見つからなかった曲は、その曲の間だけ歌詞表示をオフにする（曲が変われば自動で再検索）
    var lyricsDisabledUri by remember { mutableStateOf<String?>(null) }
    var notFoundMsgVisible by remember { mutableStateOf(false) }
    // 歌詞の対訳（洋楽向け）: 歌詞ブラウズ内のボタンでON/OFFする
    var lyricTranslations by remember { mutableStateOf<List<String>?>(null) }
    var translationEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        translationEnabled = repo.prefManager.isLyricTranslationEnabled()
    }
    LaunchedEffect(lyricsLines, translationEnabled) {
        lyricTranslations = null
        val lines = lyricsLines
        if (translationEnabled && lines != null && lines.isNotEmpty()) {
            lyricTranslations = LyricsManager.translateToJapanese(
                context,
                musicPlayerManager.currentTitle,
                musicPlayerManager.currentArtist,
                musicPlayerManager.currentAlbum,
                lines
            )
        }
    }
    LaunchedEffect(lyricsDisabledUri) {
        if (lyricsDisabledUri != null) {
            notFoundMsgVisible = true
            delay(4000)
            notFoundMsgVisible = false
        }
    }
    LaunchedEffect(showLyrics, musicPlayerManager.currentSongUri, lyricsRefreshTick) {
        val uri = musicPlayerManager.currentSongUri
        if (showLyrics && uri != null && uri != lyricsSongUri) {
            lyricsLoading = true
            lyricsLines = null // 前の曲の歌詞が残らないよう、取得開始で即クリア
            lyricsSource = ""
            lyricsNotFound = false
            val song = allApps.firstOrNull { it.packageName == uri }
            // 選曲直後は「前の曲の長さ」が残っていて、それを使うと別曲・別バージョンの歌詞を
            // 掴む誤照合の原因になる。この曲の長さが確定するまで最大2秒だけ待つ
            var waitedMs = 0
            while (waitedMs < 2000 && !(musicPlayerManager.durationSongUri == uri && musicPlayerManager.duration > 0L)) {
                delay(100)
                waitedMs += 100
            }
            val fetchDurationMs = if (musicPlayerManager.durationSongUri == uri) musicPlayerManager.duration else 0L
            val result = LyricsManager.getSyncedLyrics(
                context = context,
                title = musicPlayerManager.currentTitle,
                artist = musicPlayerManager.currentArtist,
                album = musicPlayerManager.currentAlbum,
                durationMs = fetchDurationMs,
                filePath = song?.filePath
            )
            lyricsLines = result
            lyricsSource = if (result != null) LyricsManager.lastFetchSource else ""
            lyricsSongUri = uri
            lyricsNotFound = result == null
            if (result == null) lyricsDisabledUri = uri else if (lyricsDisabledUri == uri) lyricsDisabledUri = null
            lyricsLoading = false
        }
    }

    var showWelcomeTutorial by remember { mutableStateOf(false) }
    var showIndexTutorial by remember { mutableStateOf(false) }
    var showHideTutorial by remember { mutableStateOf(false) }

    // ページが切り替わるたびにアクセントカラー等を自動再読込
    LaunchedEffect(currentLauncherPage) {
        accentHex = repo.prefManager.getAccentColorHex()
        matchArtwork = repo.prefManager.isMatchArtworkColor()
        homeDiscLayout = repo.prefManager.getHomeDiscLayout()
        disc3d = repo.prefManager.isDisc3dEnabled()
        disc3dTilt = repo.prefManager.isDisc3dTiltEnabled()
        liquidBackground = repo.prefManager.isLiquidBackgroundEnabled()
        lyricsDisplayMode = repo.prefManager.getLyricsDisplayMode()
        lyricsPvFont = repo.prefManager.getLyricsPvFont()
        if (currentLauncherPage == 1) {
            if (!repo.prefManager.isFirstWelcomeTutorialShown()) {
                showWelcomeTutorial = true
            }
        } else if (currentLauncherPage == 2) {
            if (!repo.prefManager.isFirstIndexTutorialShown()) {
                showIndexTutorial = true
            } else if (!repo.prefManager.isFirstHideTutorialShown()) {
                showHideTutorial = true
            }
        }
    }

    var searchQuery by remember { mutableStateOf("") }
        var librarySortMode by remember { mutableStateOf(repo.prefManager.getLibrarySortMode()) }
        var selectedAlbumFilter by remember { mutableStateOf<String?>(null) }
        var selectedArtistFilter by remember { mutableStateOf<String?>(null) }
    var isSearchActive by remember { mutableStateOf(false) }

    // ページ切り替え時の一時的引っ込みアニメーション用状態
    var isTransitioning by remember { mutableStateOf(false) }

    var customBackgroundPath by remember { mutableStateOf(repo.prefManager.getCustomBackgroundPath()) }
    var backgroundUpdateKey by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                val destFile = File(context.filesDir, "custom_bg.png")
                FileOutputStream(destFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                val path = destFile.absolutePath
                repo.prefManager.setCustomBackgroundPath(path)
                customBackgroundPath = path
            } catch (_: Exception) {}
        }
    }

    var selectedAppForOption by remember { mutableStateOf<AppInfo?>(null) }
    // アルバム／アーティストの読み仮名登録ダイアログの対象（packageName に "album::名前" / "artist::名前"、customYomi に現在の読みが入る）
    var nameYomiTarget by remember { mutableStateOf<AppInfo?>(null) }
    var yomiVersion by remember { mutableIntStateOf(0) }

    var showSettingsScreen by remember { mutableStateOf(false) }

    // --- フォルダ管理状態 (Page 2 用) ---
    var showFolderAppSelectDialog by remember { mutableStateOf(false) }
    var showDeleteFolderDialog by remember { mutableStateOf(false) }

    var isEditingFolder by remember { mutableStateOf(false) }
    var editingFolderNameInput by remember { mutableStateOf("") }
    var editingSelectedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }

    var isHideMode by remember { mutableStateOf(false) }
    var showPasscodeDialog by remember { mutableStateOf(false) }
    var isReadyToOpenHiddenList by remember { mutableStateOf(false) }

    var newFolderNameInput by remember { mutableStateOf("") }

    val folderList = remember(foldersMap) {
        foldersMap.map { (id, pair) -> Pair(id, pair.first) }
    }

    // Page 2 左ディスク (フォルダ選択ダイヤル) の回転角度
    val folderWheelRotationAnim = remember { Animatable(0f) }
    val folderWheelRotation = folderWheelRotationAnim.value
    var isHoldingFolderWheel by remember { mutableStateOf(false) }
    val folderStepAngle = 24f

    val createNewFolderStr = "新規作成"
    val folderItemsWithNew = remember(folderList, createNewFolderStr) {
        folderList + Pair("NEW_FOLDER", createNewFolderStr)
    }

    val selectedFolderItemIndex = remember(folderWheelRotation, folderItemsWithNew.size) {
        if (folderItemsWithNew.isEmpty()) -1
        else {
            val rawIdx = (-folderWheelRotation / folderStepAngle).roundToInt()
            ((rawIdx % folderItemsWithNew.size) + folderItemsWithNew.size) % folderItemsWithNew.size
        }
    }

    val selectedFolderPair = remember(selectedFolderItemIndex, folderItemsWithNew) {
        if (selectedFolderItemIndex in folderItemsWithNew.indices) folderItemsWithNew[selectedFolderItemIndex] else null
    }

    val defaultFolderName = "フォルダ"
    val selectedFolderId = selectedFolderPair?.first
    val selectedFolderName = selectedFolderPair?.second ?: defaultFolderName

    // --- 左右の円の固定絶対座標配置 ---
    val disc1RadiusDp = 165f
    val disc1CenterXNormal = -80f
    val disc1CenterXHome = 15f // ホーム画面ではさらに右へ寄せてジャケットを見やすく
    // レイアウト設定: 大きめ（常時）／従来（常時）／自動（再生中だけ大きめ、停止すると引っ込む）
    val isBigDiscLayout = currentLauncherPage == 1 &&
        (homeDiscLayout == "artwork" || (homeDiscLayout == "auto" && musicPlayerManager.isPlaying))
    val activeDisc1CenterX = if (isBigDiscLayout) disc1CenterXHome else disc1CenterXNormal
    // PV表示中は左右の円盤を少し下げて、上の歌詞文字と被らないようにする
    val pvDiscShiftY by animateFloatAsState(
        targetValue = when {
            lyricsDisplayMode == "multi" && showLyrics && lyricsMasterEnabled && currentLauncherPage == 1 && !isLyricsBrowsing && lyricsLines != null -> 120f
            lyricsPvMode && showLyrics && lyricsMasterEnabled && currentLauncherPage == 1 && !isLyricsBrowsing && lyricsLines != null -> 80f
            else -> 0f
        },
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "pvDiscShiftY"
    )
    val disc1CenterY = screenHeightDp * 0.50f + pvDiscShiftY

    val disc2RadiusDp = 165f
    val disc2CenterX = screenWidthDp + 80f
    val disc2CenterY = screenHeightDp * 0.50f + pvDiscShiftY

    // --- 左側（索引ダイヤル）の状態管理 ---
    val leftRotationAnim = remember { Animatable(0f) }
    val leftRotation = leftRotationAnim.value
    var isHoldingLeft by remember { mutableStateOf(false) }
    var isHoldingRight by remember { mutableStateOf(false) }
    val stepAngle = 360f / INDEX_ITEMS.size

    val animatedDisc1CenterX by animateFloatAsState(
        targetValue = when {
            isTransitioning || isHoldingRight -> -disc1RadiusDp
            isReadyToOpenHiddenList -> activeDisc1CenterX + 110f // 右の円等が開く際のオフセット
            else -> activeDisc1CenterX
        },
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "Disc1CenterX"
    )

    val animatedDisc2CenterX by animateFloatAsState(
        targetValue = when {
            isTransitioning || isHoldingLeft || isHoldingFolderWheel -> screenWidthDp + disc2RadiusDp
            else -> disc2CenterX
        },
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "Disc2CenterX"
    )

    val selectedIndex = remember(leftRotation, INDEX_ITEMS.size) {
        if (INDEX_ITEMS.isEmpty()) -1
        else {
            val rawIdx = (-leftRotation / stepAngle).roundToInt()
            ((rawIdx % INDEX_ITEMS.size) + INDEX_ITEMS.size) % INDEX_ITEMS.size
        }
    }
    val selectedIndexChar = remember(selectedIndex) {
        if (selectedIndex in INDEX_ITEMS.indices) INDEX_ITEMS[selectedIndex] else null
    }

    var lastHapticIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(selectedIndex) {
        if ((selectedIndex != -1) && (selectedIndex != lastHapticIndex) && (lastHapticIndex != -1)) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        lastHapticIndex = selectedIndex
    }

    fun changePageWithTransition(targetPage: Int) {
        if (currentLauncherPage == targetPage || isTransitioning) return
        coroutineScope.launch {
            isHoldingLeft = false
            isHoldingRight = false
            isHoldingFolderWheel = false
            isTransitioning = true
            delay((220 / pageTransitionSpeed).toLong().coerceAtLeast(0L))
            currentLauncherPage = targetPage
            isSearchActive = false
            searchQuery = ""
            keyboardController?.hide()
            delay(60)
            isTransitioning = false
        }
    }

    // 曲タップ: 表示中の一覧をそのまま再生キューにして、タップした曲から再生する
    val onAppClick: (String, List<AppInfo>) -> Unit = { songUri, contextList ->
        coroutineScope.launch {
            val song = contextList.find { it.packageName == songUri }
                ?: allApps.find { it.packageName == songUri }
            if (song != null) {
                val queueApps = contextList.filter {
                    !it.packageName.startsWith("album::") && !it.packageName.startsWith("artist::")
                }
                val startIndex = queueApps.indexOfFirst { it.packageName == songUri }
                if (queueApps.size > 1 && startIndex >= 0) {
                    musicPlayerManager.playQueue(
                        queueApps.map {
                            MusicPlayerManager.QueueItem(it.packageName, it.label, it.artist, it.album, it.albumArt, it.filePath)
                        },
                        startIndex
                    )
                } else {
                    musicPlayerManager.playSong(
                        uriString = song.packageName,
                        title = song.label,
                        artist = song.artist,
                        album = song.album,
                        albumArt = song.albumArt,
                        filePath = song.filePath
                    )
                }
                changePageWithTransition(1)
            }
        }
    }

    val transitionAlpha by animateFloatAsState(
        targetValue = if (isTransitioning) 0.0f else 1.0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "TransitionAlpha"
    )

    val appDiscIconSize = repo.prefManager.getAppDiscIconSizeDp()
    val appDiscTextSize = repo.prefManager.getAppDiscTextSizeSp()
    val appDiscLetterSpacing = repo.prefManager.getAppDiscLetterSpacingSp()
    val appDiscStepAngle = repo.prefManager.getAppDiscStepAngle()

    var fontChoice by remember { mutableStateOf(repo.prefManager.getFontChoice()) }
    var customFontPath by remember { mutableStateOf(repo.prefManager.getCustomFontPath()) }
    var useCustomFont by remember { mutableStateOf(repo.prefManager.isUseCustomFont()) }

    val currentFontFamily = remember(fontChoice, customFontPath, useCustomFont) {
        parseFontFamily(fontChoice, if (useCustomFont) customFontPath else null)
    }

    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = currentFontFamily),
        LocalLauncherFontFamily provides currentFontFamily
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            WallpaperBackground(
                customPath = repo.prefManager.getPageWallpaperPath(currentLauncherPage) ?: customBackgroundPath,
                updateKey = backgroundUpdateKey,
                isHome = (currentLauncherPage == 1),
                currentAlbumArt = musicPlayerManager.currentAlbumArt,
                dynamicBackground = if (matchArtwork) artworkBackground else null,
                liquidEnabled = liquidBackground,
                liquidAccent = effectiveAccent,
                liquidSpeed = liquidSpeed,
                liquidAlphaMul = liquidAlpha,
                wallpaperBlurDp = wallpaperBlur
            )

            // 文字PV風レイヤー: 背景のすぐ上（他の要素の奥）に、行ごとに大きさ・傾き・位置を変えて大きく表示
            val lyricsFontFamily = parseFontFamily(lyricsPvFont, if (lyricsPvFont == "custom") customFontPath else null)
            val lyricsEnFontChoice = repo.prefManager.getLyricsEnFont()
            val lyricsEnFontFamily = if (lyricsEnFontChoice == "same") lyricsFontFamily else parseFontFamily(lyricsEnFontChoice, if (lyricsEnFontChoice == "custom") repo.prefManager.getLyricsEnCustomFontPath() else null)
            val pvPartsEntry = repo.prefManager.isPvPartsEntryEnabled()
            val lyricsBold = repo.prefManager.isLyricsBold()
            val lyricWeight = if (lyricsBold) FontWeight.Bold else FontWeight.Normal
            if (lyricsPvMode && showLyrics && lyricsMasterEnabled && currentLauncherPage == 1 && !isLyricsBrowsing) {
                val pvLines = lyricsLines
                if (pvLines != null) {
                    Box(modifier = Modifier.offset(x = pvOffsetX.dp)) {
                        LyricsPvLayer(
                            lines = pvLines,
                            positionMs = musicPlayerManager.currentPosition,
                            lyricOffsetMs = lyricOffsetMs,
                            bold = lyricsBold,
                            fontFamily = lyricsFontFamily,
                            accentColor = effectiveAccent,
                            screenWidthDp = screenWidthDp,
                            translationEnabled = translationEnabled,
                            translations = lyricTranslations,
                            pvTopDp = (195f + pvTopOffset).dp,
                            interludeTopDp = (175f + pvTopOffset).dp,
                            translationTopDp = 350.dp,
                            partsEntry = pvPartsEntry,
                            enFontFamily = lyricsEnFontFamily,
                            onAccentFlash = { pvFlashColor = it },
                            pvTilt3d = disc3d,
                            dopagaki = dopagakiMode,
                            tiltX = discTiltX,
                            tiltY = discTiltY,
                            onSeek = { ms -> musicPlayerManager.seekTo(ms) },
                            onNextTrack = {
                                val qi = musicPlayerManager.currentQueueIndex
                                if (qi >= 0) musicPlayerManager.playAtQueuePosition(qi + 1)
                            },
                            pvFontScale = pvFontScale,
                            pvBaseColor = when (pvTextColorMode) {
                                "accent" -> effectiveAccent
                                "black" -> Color.Black
                                else -> Color.White
                            },
                            pvRuby = pvRuby,
                            pvInterludeArt = pvInterludeArt
                        )
                    }
                }
            }

            // 歌詞の取得中は、PV／複数行モードでも前の曲の歌詞を残さず読み込み表示を出す
            if (showLyrics && lyricsMasterEnabled && currentLauncherPage == 1 && !isLyricsBrowsing && lyricsLoading && lyricsLines == null && lyricsDisplayMode != "line") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 215.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(color = accentColor, modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                        Text(
                            text = "取得中…",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 15.sp,
                            style = LocalTextStyle.current.copy(fontFamily = lyricsFontFamily)
                        )
                    }
                }
            }

            if (showLyrics && lyricsMasterEnabled && currentLauncherPage == 1 && !isLyricsBrowsing && notFoundMsgVisible && lyricsDisplayMode != "line") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 215.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "歌詞が見つかりませんでした",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 15.sp,
                        style = LocalTextStyle.current.copy(fontFamily = lyricsFontFamily)
                    )
                }
            }

            // 複数行モード: 円を下げてできた上の空白に、前・今・次の3行を表示（今の行が大きめ）
            if (lyricsDisplayMode == "multi" && showLyrics && lyricsMasterEnabled && currentLauncherPage == 1 && !isLyricsBrowsing) {
                val multiLines = lyricsLines
                if (multiLines != null) {
                    MusicMultiLineLyrics(
                        lines = multiLines,
                        positionMs = musicPlayerManager.currentPosition,
                        lyricOffsetMs = lyricOffsetMs,
                        translationEnabled = translationEnabled,
                        translations = lyricTranslations,
                        fontFamily = lyricsFontFamily,
                        enFontFamily = lyricsEnFontFamily,
                        lyricWeight = lyricWeight,
                        accentColor = accentColor,
                        screenWidthDp = screenWidthDp
                    )
                }
            }

            // --- メインコンテンツ描画 (0: 設定, 1: ホーム, 2: アプリ一覧, 3: フォルダ) ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = transitionAlpha }
            ) {
                when (currentLauncherPage) {
                    0 -> {
                        // --- 0: 設定画面 ---
                        SettingsScreen(
                            repo = repo,
                            onBack = {
                                changePageWithTransition(1)
                            },
                            onChanged = {
                                accentHex = repo.prefManager.getAccentColorHex()
                                customBackgroundPath = repo.prefManager.getCustomBackgroundPath()
                                backgroundUpdateKey = System.currentTimeMillis()
                                customFontPath = repo.prefManager.getCustomFontPath()
                                useCustomFont = repo.prefManager.isUseCustomFont()
                                fontChoice = repo.prefManager.getFontChoice()
                                liquidBackground = repo.prefManager.isLiquidBackgroundEnabled()
                                liquidSpeed = repo.prefManager.getLiquidSpeed()
                                liquidAlpha = repo.prefManager.getLiquidAlpha()
                                wallpaperBlur = repo.prefManager.getWallpaperBlurDp()
                                pvFontScale = repo.prefManager.getPvFontScale()
                                pvTopOffset = repo.prefManager.getPvTopOffsetDp()
                                pvTextColorMode = repo.prefManager.getPvTextColor()
                                pvRuby = repo.prefManager.isPvRubyEnabled()
                                pvInterludeArt = repo.prefManager.isPvInterludeArtEnabled()
                                lyricsMasterEnabled = repo.prefManager.isLyricsEnabled()
                                dopagakiMode = repo.prefManager.isDopagakiEnabled()
                                keepScreenOnPref = repo.prefManager.isKeepScreenOn()
                                dialVisibility = repo.prefManager.getDialVisibility()
                                pageTransitionSpeed = repo.prefManager.getPageTransitionSpeed()
                                dateDisplayMode = repo.prefManager.getDateDisplayMode()
                                clockSeconds = repo.prefManager.isClockShowSeconds()
                                clock12h = repo.prefManager.isClock12h()
                                clockBattery = repo.prefManager.isClockShowBattery()
                                dateWareki = repo.prefManager.isDateWareki()
                                clockOffsetX = repo.prefManager.getClockOffsetXDp()
                                clockOffsetY = repo.prefManager.getClockOffsetYDp()
                                pvOffsetX = repo.prefManager.getPvOffsetXDp()
                            },
                            onAppsUpdated = {
                                refreshApps()
                            },
                            accentColor = accentColor,
                            disc3d = disc3d,
                            tiltX = discTiltX,
                            tiltY = discTiltY
                        )
                    }
                1 -> {
                    // --- 1: ホーム画面 ---
                    var focusedTrackPreview by remember { mutableStateOf<AppInfo?>(null) }
                    val displayTitle = focusedTrackPreview?.label ?: musicPlayerManager.currentTitle.ifBlank { "楽曲未選択" }
                    val displayArtist = focusedTrackPreview?.artist ?: musicPlayerManager.currentArtist.ifBlank { "アーティスト名" }
                    val displayAlbum = focusedTrackPreview?.album ?: musicPlayerManager.currentAlbum.ifBlank { "アルバム名" }

                    Column(
                        modifier = Modifier
                            .padding(start = 28.dp, top = topPaddingDp + 48.dp)
                            .zIndex(10f)
                            .fillMaxWidth()
                            .heightIn(min = 190.dp)
                    ) {
                        Text(
                            text = displayTitle,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.5.sp,
                            textAlign = TextAlign.Start,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = 76.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = displayArtist,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            letterSpacing = 1.5.sp,
                            textAlign = TextAlign.Start,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = 76.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = displayAlbum,
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            letterSpacing = 1.0.sp,
                            textAlign = TextAlign.Start,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = 76.dp)
                        )
                        if (showLyrics && lyricsDisplayMode == "line") { // 小さい歌詞行は「現在の行」モード専用
                            Spacer(modifier = Modifier.height(10.dp))
                            val lines = lyricsLines
                            when {
                                lyricsLoading -> {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        CircularProgressIndicator(color = accentColor, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        Text(
                                            text = "取得中…",
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 12.sp,
                                            letterSpacing = 1.0.sp,
                                            textAlign = TextAlign.Start,
                                            style = LocalTextStyle.current.copy(fontFamily = lyricsFontFamily)
                                        )
                                    }
                                }
                                lines != null -> {
                                    // 同期歌詞の現在の1行を、行の切り替わりにフェードをかけて表示
                                    // 間奏（次の行まで間が空いている区間）では歌詞の代わりに音符マークを出す
                                    val posNow = musicPlayerManager.currentPosition + lyricOffsetMs
                                    val currentLyricIndex = lines.indexOfLast { it.timeMs <= posNow }
                                    val curLine = lines.getOrNull(currentLyricIndex)
                                    val nextLine = lines.getOrNull(currentLyricIndex + 1)
                                    val inInterlude = when {
                                        curLine == null -> nextLine != null && (nextLine.timeMs - posNow) > 5000
                                        nextLine == null -> (posNow - curLine.timeMs) > 8000
                                        else -> (nextLine.timeMs - curLine.timeMs) > 15000 && (posNow - curLine.timeMs) > 5000 // 行間が長い間奏だけ。次の行が近づいたら前の行に戻る、をしない
                                    }
                                    Crossfade(
                                        targetState = if (inInterlude) -2 else currentLyricIndex,
                                        animationSpec = tween(durationMillis = 350),
                                        label = "lyricFade"
                                    ) { idx ->
                                        val fadeLineObj = if (idx >= 0) lines.getOrNull(idx) else null
                                        if (fadeLineObj != null && fadeLineObj.words.isNotEmpty()) {
                                            KaraokeLyricText(
                                                line = fadeLineObj,
                                                positionMs = musicPlayerManager.currentPosition + lyricOffsetMs,
                                                sungColor = accentColor,
                                                baseColor = Color.White,
                                                fontSize = fitFontSizeSp(fadeLineObj.text, 15f, screenWidthDp - 116f).sp,
                                                fontWeight = lyricWeight,
                                                fontFamily = lyricsFontFamily,
                                                enFontFamily = lyricsEnFontFamily,
                                                letterSpacing = 2.0.sp,
                                                textAlign = TextAlign.Start,
                                                shadow = Shadow(color = Color.White.copy(alpha = 0.85f), blurRadius = 16f)
                                            )
                                        } else {
                                            Text(
                                                text = when {
                                                    idx == -2 -> "♪"
                                                    idx >= 0 -> lines[idx].text
                                                    else -> ""
                                                },
                                                color = Color.White,
                                                fontSize = fitFontSizeSp(
                                                    when {
                                                        idx == -2 -> "♪"
                                                        idx >= 0 -> lines[idx].text
                                                        else -> ""
                                                    },
                                                    15f,
                                                    screenWidthDp - 116f
                                                ).sp,
                                                fontWeight = lyricWeight,
                                                letterSpacing = 2.0.sp,
                                                textAlign = TextAlign.Start,
                                                maxLines = 1,
                                                style = LocalTextStyle.current.copy(
                                                    fontFamily = lyricsFontFamily,
                                                    shadow = Shadow(color = Color.White.copy(alpha = 0.85f), blurRadius = 16f)
                                                )
                                            )
                                        }
                                    }
                                    if (translationEnabled && !inInterlude && currentLyricIndex >= 0) {
                                        val tr = lyricTranslations?.getOrNull(currentLyricIndex)
                                        if (!tr.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = tr,
                                                color = Color.White.copy(alpha = 0.7f),
                                                fontSize = 11.sp,
                                                letterSpacing = 1.0.sp,
                                                textAlign = TextAlign.Start,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = LocalTextStyle.current.copy(fontFamily = lyricsFontFamily),
                                                modifier = Modifier.padding(end = 76.dp)
                                            )
                                        }
                                    }
                                }
                                lyricsNotFound && notFoundMsgVisible -> {
                                    Text(
                                        text = "歌詞が見つかりませんでした",
                                        color = Color.White.copy(alpha = 0.45f),
                                        fontSize = 12.sp,
                                        letterSpacing = 1.0.sp,
                                        textAlign = TextAlign.Start
                                    )
                                }
                            }
                        }
                    }

                    if (!isRackShown && !isLyricsBrowsing && !pvFullscreen) {
                        MusicSeekBarDisc(
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                                                if (disc3d) {
                                                                transformOrigin = TransformOrigin((animatedDisc2CenterX / screenWidthDp).coerceIn(-2f, 3f), (disc2CenterY / screenHeightDp).coerceIn(-2f, 3f))
                                                                rotationX = 27f + discTiltX.value
                                                                rotationY = discTiltY.value
                                                                cameraDistance = 6.5f * density
                                                            }
                            },
                            playerManager = musicPlayerManager,
                            focusedTrack = focusedTrackPreview,
                            discCenterXDp = animatedDisc2CenterX,
                            discCenterYDp = disc2CenterY,
                            discRadiusDp = disc2RadiusDp,
                            accentColor = accentColor,
                            accentGlow = accentGlow
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(50f)
                            .graphicsLayer {
                                alpha = if (isLyricsBrowsing) 0f else 1f
                                                                if (disc3d) {
                                                                transformOrigin = TransformOrigin((animatedDisc1CenterX / screenWidthDp).coerceIn(-2f, 3f), (disc1CenterY / screenHeightDp).coerceIn(-2f, 3f))
                                                                rotationX = 27f + discTiltX.value
                                                                rotationY = discTiltY.value
                                                                cameraDistance = 6.5f * density
                                                            }
                            }
                    ) {
                        MusicVinylWidget(
                            artworkTilt3d = disc3d,
                            modifier = Modifier.fillMaxSize(),
                            playerManager = musicPlayerManager,
                            allApps = allApps,
                            discCenterXDp = animatedDisc1CenterX,
                            discCenterYDp = disc1CenterY,
                            discRadiusDp = disc1RadiusDp,
                            accentColor = accentColor,
                            accentGlow = accentGlow,
                            topZoneReserved = showLyrics && lyricsLines != null,
                            onPlayTrack = { song ->
                                // レコードラックの一覧（全曲）をキューにして選んだ曲から再生
                                val startIndex = allApps.indexOfFirst { it.packageName == song.packageName }
                                if (allApps.size > 1 && startIndex >= 0) {
                                    musicPlayerManager.playQueue(
                                        allApps.map {
                                            MusicPlayerManager.QueueItem(it.packageName, it.label, it.artist, it.album, it.albumArt, it.filePath)
                                        },
                                        startIndex
                                    )
                                } else {
                                    musicPlayerManager.playSong(
                                        uriString = song.packageName,
                                        title = song.label,
                                        artist = song.artist,
                                        album = song.album,
                                        albumArt = song.albumArt,
                                        filePath = song.filePath
                                    )
                                }
                            },
                            onFocusTrackChanged = { focusedTrackPreview = it },
                            onRackVisibilityChanged = { isRackShown = it }
                        )
                    }

                    // 歌詞ブラウズ中: 歌詞全文をラック表示のように上から下へ並べる
                    if (isLyricsBrowsing && lyricsLines != null) {
                        LyricsBrowseOverlay(
                            lines = lyricsLines!!,
                            startIndex = lyricsBrowseStartIndex,
                            positionMs = musicPlayerManager.currentPosition,
                            lyricOffsetMs = lyricOffsetMs,
                            onOffsetChange = { newOffset ->
                                lyricOffsetMs = newOffset
                                repo.prefManager.setLyricOffsetMs(
                                    musicPlayerManager.currentTitle,
                                    musicPlayerManager.currentArtist,
                                    newOffset
                                )
                            },
                            fontFamily = lyricsFontFamily,
                            lyricWeight = lyricWeight,
                            accentColor = accentColor,
                            accentGlow = accentGlow,
                            topPaddingDp = topPaddingDp,
                            source = lyricsSource,
                            translationEnabled = translationEnabled,
                            translations = lyricTranslations,
                            onToggleTranslation = {
                                translationEnabled = !translationEnabled
                                repo.prefManager.setLyricTranslationEnabled(translationEnabled)
                            },
                            reverseScroll = repo.prefManager.isReverseScroll(),
                            onSeek = { pos -> musicPlayerManager.seekTo(pos) },
                            onRefresh = {
                                LyricsManager.invalidateCache(context, musicPlayerManager.currentTitle, musicPlayerManager.currentArtist, musicPlayerManager.currentAlbum)
                                lyricsSongUri = null
                                lyricsLines = null
                                lyricsRefreshTick++
                                isLyricsBrowsing = false
                            },
                            onOpenSearch = { showLyricSearch = true },
                            enFontFamily = lyricsEnFontFamily,
                            onSaveEditedLyrics = { editedText ->
                                LyricsManager.saveEditedLyrics(context, musicPlayerManager.currentTitle, musicPlayerManager.currentArtist, musicPlayerManager.currentAlbum, editedText)
                                lyricsRefreshTick++
                            },
                            onPublishLyrics = { lrcText, report ->
                                coroutineScope.launch {
                                    val err = LyricsManager.publishToLrclib(
                                        musicPlayerManager.currentTitle, musicPlayerManager.currentArtist, musicPlayerManager.currentAlbum,
                                        (musicPlayerManager.duration / 1000).toInt(), lrcText
                                    )
                                    report(if (err == null) "OK" else err)
                                }
                            },
                            onClose = { isLyricsBrowsing = false }
                        )
                    }
                }
                2 -> {
                    // --- 2: アプリ一覧画面 ---
                    RotatableIndexWheel(
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                                                        if (disc3d) {
                                                            transformOrigin = TransformOrigin((animatedDisc1CenterX / screenWidthDp).coerceIn(-2f, 3f), (disc1CenterY / screenHeightDp).coerceIn(-2f, 3f))
                                                            rotationX = 27f + discTiltX.value
                                                            rotationY = discTiltY.value
                                                            cameraDistance = 6.5f * density
                                                        }
                        },
                        discCenterXDp = animatedDisc1CenterX,
                        discCenterYDp = disc1CenterY,
                        discRadiusDp = disc1RadiusDp,
                        currentRotation = leftRotation,
                        isHolding = isHoldingLeft,
                        stepAngle = stepAngle,
                        accentColor = accentColor,
                        centerIcon = {
                            if (isReadyToOpenHiddenList) {
                                Text(
                                    text = "非表示\nリストへ",
                                    color = accentColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                WhiteSearchIcon(size = 24.dp)
                            }
                        }
                    )

                                        val displayedApps = remember(searchQuery, allApps, isHideMode, librarySortMode, selectedAlbumFilter, selectedArtistFilter, yomiVersion) {
                        when {
                            isHideMode -> allApps.filter { it.isHidden }
                            selectedAlbumFilter != null -> {
                                allApps
                                    .filter { it.album == selectedAlbumFilter && !it.isHidden }
                                    .sortedWith(compareBy({ if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }, { it.label }))
                                    .mapIndexed { index, track -> track.copy(label = "${index + 1}. ${track.label}") }
                            }
                            selectedArtistFilter != null -> {
                                // アーティストの全曲をアルバムに関係なく五十音順に並べ、曲名の頭文字でインデックスする
                                allApps
                                    .filter { it.artist == selectedArtistFilter && !it.isHidden }
                                    .map { track ->
                                        // 頭文字は曲名基準に付け替える（通常の頭文字はアーティスト名基準のため）
                                        val (jp, en) = initialsOfLabel(if (track.customYomi.isNotBlank()) track.customYomi else track.label)
                                        if (track.initialJapanese == jp && track.initialEnglish == en) track
                                        else track.copy(initialJapanese = jp, initialEnglish = en)
                                    }
                                    .sortedWith(compareBy(
                                        { val i = INDEX_ITEMS.indexOf(it.initialJapanese); if (i < 0) INDEX_ITEMS.size else i },
                                        { hiraganaSortKey(if (it.customYomi.isNotBlank()) it.customYomi else it.label) },
                                        { it.label.lowercase() }
                                    ))
                            }
                            searchQuery.isNotBlank() -> {
                                allApps.filter {
                                    (it.label.contains(searchQuery, ignoreCase = true) ||
                                            it.artist.contains(searchQuery, ignoreCase = true) ||
                                            it.album.contains(searchQuery, ignoreCase = true) ||
                                            it.customYomi.contains(searchQuery, ignoreCase = true) ||
                                            it.initialJapanese.contains(searchQuery, ignoreCase = true) ||
                                            it.searchAliases.any { alias -> alias.contains(searchQuery, ignoreCase = true) }) && !it.isHidden
                                }
                            }
                            librarySortMode == "album" -> {
                                val yomiMap = repo.prefManager.getYomiMap()
                                allApps.filter { !it.isHidden }
                                    .groupBy { it.album }
                                    .values
                                    .map { albumTracks ->
                                        val first = albumTracks.first()
                                        // アルバム名の読み仮名が登録されていれば、頭文字と並びは読み優先
                                        val albumYomi = yomiMap["album::" + first.album] ?: ""
                                        val (jp, en) = initialsOfLabel(if (albumYomi.isNotBlank()) albumYomi else first.album)
                                        first.copy(
                                            label = first.album,
                                            packageName = "album::" + first.album,
                                            initialJapanese = jp,
                                            initialEnglish = en,
                                            customYomi = albumYomi
                                        )
                                    }
                                    // 頭文字の行順（あ→ん→A→Z）で昇順に並べ、左ホイールのインデックスと一致させる
                                    .sortedWith(compareBy(
                                        { val i = INDEX_ITEMS.indexOf(it.initialJapanese); if (i < 0) INDEX_ITEMS.size else i },
                                        { hiraganaSortKey(if (it.customYomi.isNotBlank()) it.customYomi else it.label) },
                                        { it.label.lowercase() }
                                    ))
                            }
                            librarySortMode == "artist" -> {
                                val yomiMap = repo.prefManager.getYomiMap()
                                allApps.filter { !it.isHidden }
                                    .groupBy { it.artist }
                                    .values
                                    .map { artistTracks ->
                                        val first = artistTracks.first()
                                        // アーティスト名の読み仮名が登録されていれば、頭文字と並びは読み優先
                                        val artistYomi = yomiMap["artist::" + first.artist] ?: ""
                                        val (jp, en) = initialsOfLabel(if (artistYomi.isNotBlank()) artistYomi else first.artist)
                                        first.copy(
                                            label = first.artist,
                                            packageName = "artist::" + first.artist,
                                            initialJapanese = jp,
                                            initialEnglish = en,
                                            customYomi = artistYomi
                                        )
                                    }
                                    // 頭文字の行順（あ→ん→A→Z）で昇順に並べ、左ホイールのインデックスと一致させる
                                    .sortedWith(compareBy(
                                        { val i = INDEX_ITEMS.indexOf(it.initialJapanese); if (i < 0) INDEX_ITEMS.size else i },
                                        { hiraganaSortKey(if (it.customYomi.isNotBlank()) it.customYomi else it.label) },
                                        { it.label.lowercase() }
                                    ))
                            }
                            else -> allApps.filter { !it.isHidden }
                        }
                    }

                    RotatableAppDisc(
                        iconTilt3d = disc3d,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                                                        if (disc3d) {
                                                            transformOrigin = TransformOrigin((animatedDisc2CenterX / screenWidthDp).coerceIn(-2f, 3f), (disc2CenterY / screenHeightDp).coerceIn(-2f, 3f))
                                                            rotationX = 27f + discTiltX.value
                                                            rotationY = discTiltY.value
                                                            cameraDistance = 6.5f * density
                                                        }
                        },
                        apps = displayedApps,
                        discCenterXDp = animatedDisc2CenterX,
                        discCenterYDp = disc2CenterY,
                        discRadiusDp = disc2RadiusDp,
                        textSizeSp = appDiscTextSize,
                        iconSizeDp = appDiscIconSize,
                        letterSpacingSp = appDiscLetterSpacing,
                        stepAngle = appDiscStepAngle,
                        selectedIndexChar = selectedIndexChar,
                        resetKey = librarySortMode + "|" + (selectedAlbumFilter ?: "") + "|" + (selectedArtistFilter ?: ""),
                        isRightDisc = true,
                        showIndices = true,
                        isLoopable = (!isHideMode && searchQuery.isBlank() && librarySortMode == "title"), // タイトル表示(検索なし)のみ循環ループ、検索時や非表示リスト表示時、アルバム/アーティスト表示時はループなし
                        accentColor = accentColor,
                        accentGlow = accentGlow,
                        onAppClick = { app ->
                            if (app.packageName.startsWith("album::")) {
                                selectedAlbumFilter = app.packageName.removePrefix("album::")
                            } else if (app.packageName.startsWith("artist::")) {
                                selectedArtistFilter = app.packageName.removePrefix("artist::")
                            } else if (searchQuery.isNotBlank() && !isHideMode) {
                                // 検索結果から再生しても、キューは検索結果ではなく全曲にする
                                onAppClick(app.packageName, allApps.filter { !it.isHidden })
                            } else {
                                onAppClick(app.packageName, displayedApps)
                            }
                        },
                        onLongClick = { app ->
                            if (app.packageName.startsWith("album::") || app.packageName.startsWith("artist::")) {
                                // アルバム／アーティストを長押ししたら読み仮名の登録ダイアログを開く
                                nameYomiTarget = app
                            } else {
                                selectedAppForOption = app
                            }
                        },
                        onHoldingChanged = { isHoldingRight = it }
                    )
                }
                3 -> {
                    // --- 3: フォルダ画面 ---
                    if (isEditingFolder) {
                        RotatableIndexWheel(
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                                                if (disc3d) {
                                                                transformOrigin = TransformOrigin((animatedDisc1CenterX / screenWidthDp).coerceIn(-2f, 3f), (disc1CenterY / screenHeightDp).coerceIn(-2f, 3f))
                                                                rotationX = 27f + discTiltX.value
                                                                rotationY = discTiltY.value
                                                                cameraDistance = 6.5f * density
                                                            }
                            },
                            discCenterXDp = animatedDisc1CenterX,
                            discCenterYDp = disc1CenterY,
                            discRadiusDp = disc1RadiusDp,
                            currentRotation = leftRotation,
                            isHolding = isHoldingLeft,
                            stepAngle = stepAngle,
                            accentColor = accentColor,
                            centerIcon = { WhiteSearchIcon(size = 24.dp) }
                        )
                    } else {
                        // 左側: フォルダ選択ダイヤル
                        RotatableFolderWheel(
                            folders = folderList,
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                if (disc3d) {
                                    transformOrigin = TransformOrigin((animatedDisc1CenterX / screenWidthDp).coerceIn(-2f, 3f), (disc1CenterY / screenHeightDp).coerceIn(-2f, 3f))
                                    rotationX = 27f + discTiltX.value
                                    rotationY = discTiltY.value
                                    cameraDistance = 6.5f * density
                                }
                            },
                            discCenterXDp = animatedDisc1CenterX,
                            discCenterYDp = disc1CenterY,
                            discRadiusDp = disc1RadiusDp,
                            currentRotation = folderWheelRotation,
                            isHolding = isHoldingFolderWheel,
                            stepAngle = folderStepAngle,
                            textSizeSp = appDiscTextSize,
                            letterSpacingSp = appDiscLetterSpacing,
                            accentColor = accentColor,
                            onNewFolderClick = {
                                val idx = folderItemsWithNew.indexOfFirst { it.first == "NEW_FOLDER" }
                                if (idx != -1) {
                                    coroutineScope.launch {
                                        folderWheelRotationAnim.animateTo(-idx * folderStepAngle, animationSpec = tween(200))
                                    }
                                }
                                newFolderNameInput = ""
                            },
                            onRenameFolderClick = { id, _ ->
                                val idx = folderItemsWithNew.indexOfFirst { it.first == id }
                                if (idx != -1) {
                                    coroutineScope.launch {
                                        folderWheelRotationAnim.animateTo(-idx * folderStepAngle, animationSpec = tween(200))
                                    }
                                }
                            }
                        )
                    }

                    // 右側: 通常時はフォルダ内アプリ、編集時は全アプリ（チェック付き）
                    val folderPackages = remember(selectedFolderId, foldersMap) {
                        if (selectedFolderId != null && selectedFolderId != "NEW_FOLDER") {
                            foldersMap[selectedFolderId]?.second ?: emptyList()
                        } else {
                            emptyList()
                        }
                    }

                    val discApps = if (isEditingFolder) {
                        allApps
                    } else {
                        allApps.filter { folderPackages.contains(it.packageName) }
                    }

                    RotatableAppDisc(
                        iconTilt3d = disc3d,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                                                        if (disc3d) {
                                                            transformOrigin = TransformOrigin((animatedDisc2CenterX / screenWidthDp).coerceIn(-2f, 3f), (disc2CenterY / screenHeightDp).coerceIn(-2f, 3f))
                                                            rotationX = 27f + discTiltX.value
                                                            rotationY = discTiltY.value
                                                            cameraDistance = 6.5f * density
                                                        }
                        },
                        apps = discApps,
                        discCenterXDp = animatedDisc2CenterX,
                        discCenterYDp = disc2CenterY,
                        discRadiusDp = disc2RadiusDp,
                        textSizeSp = appDiscTextSize,
                        iconSizeDp = appDiscIconSize,
                        letterSpacingSp = appDiscLetterSpacing,
                        stepAngle = appDiscStepAngle,
                        selectedIndexChar = if (isEditingFolder) selectedIndexChar else null,
                        isRightDisc = true,
                        showIndices = isEditingFolder,
                        isLoopable = false, // フォルダ内アプリはループしない
                        selectedPackages = if (isEditingFolder) editingSelectedPackages else null,
                        accentColor = accentColor,
                        accentGlow = accentGlow,
                        onAppClick = { app ->
                            if (isEditingFolder) {
                                val updated = editingSelectedPackages.toMutableSet()
                                if (updated.contains(app.packageName)) {
                                    updated.remove(app.packageName)
                                } else {
                                    updated.add(app.packageName)
                                }
                                editingSelectedPackages = updated
                            } else {
                                onAppClick(app.packageName, discApps)
                            }
                        },
                        onLongClick = { app ->
                            if (!isEditingFolder) {
                                selectedAppForOption = app
                            }
                        },
                        onHoldingChanged = { isHoldingRight = it }
                    )
                }
            }
        }


        // --- 時計（ホームのみ。レイアウト編集モードで移動可能） ---
        if (currentLauncherPage == 1 && !pvFullscreen && !isLyricsBrowsing) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 20.dp, top = 54.dp)
                    .offset(x = clockOffsetX.dp, y = clockOffsetY.dp)
                    .zIndex(90f)
                    .then(
                        if (layoutEditMode) {
                            Modifier
                                .border(2.dp, accentColor, RoundedCornerShape(8.dp))
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragEnd = { repo.prefManager.setClockOffset(clockOffsetX, clockOffsetY) }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        clockOffsetX += dragAmount.x / density
                                        clockOffsetY += dragAmount.y / density
                                    }
                                }
                        } else {
                            Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = { layoutEditMode = true }
                            )
                        }
                    )
            ) {
                ClockWidget(
                    dateMode = dateDisplayMode,
                    glowColor = effectiveAccent,
                    showSeconds = clockSeconds,
                    hour12 = clock12h,
                    showBattery = clockBattery,
                    wareki = dateWareki,
                    dopagaki = dopagakiMode
                )
            }
        }

        // --- レイアウト編集モードのUI ---
        if (layoutEditMode && currentLauncherPage == 1) {
            BackHandler(enabled = true) { layoutEditMode = false }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = (195f + pvTopOffset).dp)
                    .offset(x = pvOffsetX.dp)
                    .zIndex(95f)
                    .size(width = 104.dp, height = 44.dp)
                    .background(accentColor.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                repo.prefManager.setPvOffsetX(pvOffsetX)
                                repo.prefManager.setPvTopOffsetDp(pvTopOffset)
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            pvOffsetX += dragAmount.x / density
                            pvTopOffset = (pvTopOffset + dragAmount.y / density).coerceIn(-120f, 300f)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("PV", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp)
                    .zIndex(96f)
                    .background(accentColor, RoundedCornerShape(999.dp))
                    .clickable { layoutEditMode = false }
                    .padding(horizontal = 22.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("完了", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = "時計とPVをドラッグで移動できます",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 110.dp)
                    .zIndex(96f)
            )
        }

                // --- 画面下部 ページ切り替え (ボタン式 / ダイヤル式を設定で切替、ラック表示中は隠す) ---
        if (!isRackShown && !isLyricsBrowsing && !pvFullscreen && dialVisibility != "hidden") {
        if (repo.prefManager.getPageSwitchStyle() == "dial") {
            // ダイヤルは6ストップ: 0=設定, 1=ホーム(再生中), 2=曲名, 3=アーティスト, 4=アルバム, 5=プレイリスト(フォルダ)
            PeekPageDial(
                currentStop = when (currentLauncherPage) {
                    2 -> 2 + listOf("title", "artist", "album").indexOf(librarySortMode).coerceAtLeast(0)
                    3 -> 5
                    else -> currentLauncherPage
                },
                onStopSelected = { stop ->
                    when (stop) {
                        0, 1 -> changePageWithTransition(stop)
                        2, 3, 4 -> {
                            val mode = listOf("title", "artist", "album")[stop - 2]
                            if (librarySortMode != mode) {
                                librarySortMode = mode
                                selectedAlbumFilter = null
                                selectedArtistFilter = null
                                repo.prefManager.setLibrarySortMode(mode)
                                AppRepository.invalidateCache()
                                refreshApps()
                            }
                            changePageWithTransition(2)
                        }
                        5 -> changePageWithTransition(3)
                    }
                },
                accentColor = accentColor,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomPaddingDp + 4.dp)
                    .zIndex(250f)
                    .graphicsLayer {
                        if (disc3d) {
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            rotationX = 27f + discTiltX.value
                            rotationY = discTiltY.value
                            cameraDistance = 6.5f * density
                        }
                    },
                onPressedChanged = { isDialPressed = it },
                accentGlow = accentGlow
            )
        } else {
            BottomPageDial(
                currentPage = currentLauncherPage,
                onPageSelected = { targetPage ->
                    changePageWithTransition(targetPage)
                },
                accentColor = accentColor,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomPaddingDp + 4.dp)
                    .zIndex(250f)
                    .graphicsLayer {
                        if (disc3d) {
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            rotationX = 27f + discTiltX.value
                            rotationY = discTiltY.value
                            cameraDistance = 6.5f * density
                        }
                    }
            )
        }
        }

        // --- ホーム画面のみ: リピート／シャッフルボタンを下ダイヤルの円周（右下）の外側に沿って配置 ---
        // ダイヤルの円: 中心は帯の下端から99dp下、半径165dp。
        // ボタンは輪郭の上に乗せず、少し離して外側（輪郭+ボタン半径+余白）に中心を置く
        if (currentLauncherPage == 1 && !isRackShown && !isDialPressed && !isLyricsBrowsing) {
            val dialRadiusForButtons = 192f
            val dialCenterBelowBand = 99f
            // リピート（円周の -141° の位置。旧歌詞ボタンがあった左下）
            run {
                val rad = -141f * (PI.toFloat() / 180f)
                val dx = dialRadiusForButtons * cos(rad)
                val dy = dialCenterBelowBand + dialRadiusForButtons * sin(rad) + 20f
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomPaddingDp + 4.dp)
                        .offset(x = dx.dp, y = dy.dp)
                        .size(40.dp)
                        .zIndex(260f)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            musicPlayerManager.cycleRepeatMode()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (accentGlow && musicPlayerManager.repeatMode != 0) {
                        Icon(
                            imageVector = if (musicPlayerManager.repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp).blur(6.dp)
                        )
                    }
                    Icon(
                        imageVector = if (musicPlayerManager.repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "リピート",
                        tint = if (musicPlayerManager.repeatMode != 0) accentColor else Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            // シャッフル（円周の -39° の位置）
            run {
                val rad = -39f * (PI.toFloat() / 180f)
                val dx = dialRadiusForButtons * cos(rad)
                val dy = dialCenterBelowBand + dialRadiusForButtons * sin(rad) + 20f
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomPaddingDp + 4.dp)
                        .offset(x = dx.dp, y = dy.dp)
                        .size(40.dp)
                        .zIndex(260f)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            musicPlayerManager.toggleShuffle()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (accentGlow && musicPlayerManager.shuffleEnabled) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp).blur(6.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "シャッフル",
                        tint = if (musicPlayerManager.shuffleEnabled) accentColor else Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 歌詞ボタン: 曲名と同じ高さ、画面右側。タップで歌詞全文表示（歌詞が無いときはタグ検索）、長押しで歌詞を再取得
        if (currentLauncherPage == 1 && !isRackShown && !isLyricsBrowsing && musicPlayerManager.currentSongUri != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = topPaddingDp + 48.dp, end = 22.dp)
                    .size(44.dp)
                    .zIndex(95f)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                val lines = lyricsLines
                                if (lines == null) {
                                    // 歌詞が取得できていないときは、タグを指定して手動検索できるようにする
                                    showLyricSearch = true
                                } else {
                                    lyricsBrowseStartIndex = lines.indexOfLast {
                                        it.timeMs <= musicPlayerManager.currentPosition + lyricOffsetMs
                                    }.coerceAtLeast(0)
                                    isLyricsBrowsing = true
                                }
                            },
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                // 歌詞の再取得（誤った歌詞を掴んだとき用）: キャッシュを消して取り直す
                                LyricsManager.invalidateCache(context, musicPlayerManager.currentTitle, musicPlayerManager.currentArtist, musicPlayerManager.currentAlbum)
                                lyricsSongUri = null
                                lyricsLines = null
                                lyricsRefreshTick++
                                showLyrics = true
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (accentGlow) {
                    Icon(
                        imageVector = Icons.Default.Lyrics,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp).blur(6.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.Lyrics,
                    contentDescription = "歌詞",
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // フルスクリーンボタン: 歌詞ボタンの下。押すと横画面で歌詞だけの画面になる
        if (currentLauncherPage == 1 && !isRackShown && !isLyricsBrowsing && musicPlayerManager.currentSongUri != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = topPaddingDp + 100.dp, end = 22.dp)
                    .size(44.dp)
                    .zIndex(95f)
                    .clickable { pvFullscreen = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "歌詞を全画面表示",
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 歌詞フルスクリーン（横画面）: PVモードならPVだけ、1行/複数行モードなら曲名・アーティストと一緒に歌詞を表示
        if (pvFullscreen && currentLauncherPage == 1) {
            LyricsFullscreenOverlay(
                lines = lyricsLines,
                positionMs = musicPlayerManager.currentPosition,
                lyricOffsetMs = lyricOffsetMs,
                displayMode = lyricsDisplayMode,
                bold = lyricsBold,
                fontFamily = lyricsFontFamily,
                enFontFamily = lyricsEnFontFamily,
                partsEntry = pvPartsEntry,
                pvTilt3d = disc3d,
                lyricWeight = lyricWeight,
                accentColor = accentColor,
                artwork = musicPlayerManager.currentAlbumArt,
                artworkBackground = artworkBackground,
                liquidEnabled = liquidBackground,
                title = musicPlayerManager.currentTitle,
                artist = musicPlayerManager.currentArtist,
                album = musicPlayerManager.currentAlbum,
                onClose = { pvFullscreen = false }
            )
        }

        // --- タッチ操作パネル (左半分: アプリ一覧(2) & フォルダ(3)) ---
        if (!isTransitioning && (currentLauncherPage == 2 || currentLauncherPage == 3)) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPaddingDp + 60.dp, bottom = bottomPaddingDp + 60.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .pointerInput(currentLauncherPage, isEditingFolder) {
                            awaitPointerEventScope {
                                try {
                                    while (true) {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val isIndexWheel = (currentLauncherPage == 2 || (currentLauncherPage == 3 && isEditingFolder))
                                        if (isIndexWheel) {
                                            isHoldingLeft = true
                                        } else {
                                            isHoldingFolderWheel = true
                                        }
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                        var lastY = down.position.y
                                        var accumulatedDy = 0f
                                        var currentStepCount = if (isIndexWheel) {
                                            (-leftRotationAnim.value / stepAngle).roundToInt()
                                        } else {
                                            (-folderWheelRotationAnim.value / folderStepAngle).roundToInt().coerceIn(0, folderItemsWithNew.size - 1)
                                        }
                                        val stepThreshold = 40f // スワイプの感度調整

                                        drag(down.id) { change ->
                                            change.consume()
                                            val pos = change.position
                                            if (currentLauncherPage == 2 && !isEditingFolder) {
                                                val shouldOpen = pos.x >= size.width * 0.78f
                                                if (shouldOpen != isReadyToOpenHiddenList) {
                                                    isReadyToOpenHiddenList = shouldOpen
                                                    if (shouldOpen) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                }
                                            }

                                            val rawDeltaY = change.position.y - lastY
                                            val deltaY = rawDeltaY * (if (repo.prefManager.isReverseScroll()) -1f else 1f)
                                            lastY = change.position.y
                                            accumulatedDy += deltaY

                                            while (accumulatedDy >= stepThreshold) {
                                                accumulatedDy -= stepThreshold
                                                currentStepCount++
                                                if (isIndexWheel) {
                                                    val targetRotation = -currentStepCount * stepAngle
                                                    coroutineScope.launch {
                                                        leftRotationAnim.animateTo(
                                                            targetValue = targetRotation,
                                                            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                                        )
                                                    }
                                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                } else {
                                                    if (folderItemsWithNew.isNotEmpty()) {
                                                        val minFolderRotation = -((folderItemsWithNew.size - 1) * folderStepAngle).coerceAtLeast(0f)
                                                        val maxFolderRotation = 0f
                                                        val clampedStep = currentStepCount.coerceIn(0, folderItemsWithNew.size - 1)
                                                        currentStepCount = clampedStep
                                                        val targetRotation = (-clampedStep * folderStepAngle).coerceIn(minFolderRotation, maxFolderRotation)
                                                        coroutineScope.launch {
                                                            folderWheelRotationAnim.animateTo(
                                                                targetValue = targetRotation,
                                                                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                                            )
                                                        }
                                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    }
                                                }
                                            }

                                            while (accumulatedDy <= -stepThreshold) {
                                                accumulatedDy += stepThreshold
                                                currentStepCount--
                                                if (isIndexWheel) {
                                                    val targetRotation = -currentStepCount * stepAngle
                                                    coroutineScope.launch {
                                                        leftRotationAnim.animateTo(
                                                            targetValue = targetRotation,
                                                            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                                        )
                                                    }
                                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                } else {
                                                    if (folderItemsWithNew.isNotEmpty()) {
                                                        val minFolderRotation = -((folderItemsWithNew.size - 1) * folderStepAngle).coerceAtLeast(0f)
                                                        val maxFolderRotation = 0f
                                                        val clampedStep = currentStepCount.coerceIn(0, folderItemsWithNew.size - 1)
                                                        currentStepCount = clampedStep
                                                        val targetRotation = (-clampedStep * folderStepAngle).coerceIn(minFolderRotation, maxFolderRotation)
                                                        coroutineScope.launch {
                                                            folderWheelRotationAnim.animateTo(
                                                                targetValue = targetRotation,
                                                                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                                            )
                                                        }
                                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                    }
                                                }
                                            }
                                        }

                                        // 指を離した瞬間
                                        if (currentLauncherPage == 2 && !isEditingFolder) {
                                            if (isReadyToOpenHiddenList) {
                                                if (repo.prefManager.isUsePasscodeForHidden()) {
                                                    showPasscodeDialog = true
                                                } else {
                                                    isHideMode = true
                                                }
                                            }
                                            isReadyToOpenHiddenList = false
                                        }

                                        if (currentLauncherPage == 3 && folderItemsWithNew.isNotEmpty()) {
                                            if (selectedFolderId == "NEW_FOLDER") {
                                                newFolderNameInput = ""
                                            }
                                        }

                                        isHoldingLeft = false
                                        isHoldingFolderWheel = false
                                    }
                                } finally {
                                    isHoldingLeft = false
                                    isHoldingFolderWheel = false
                                }
                            }
                        }
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                )
            }
        }

        // --- 画面上部 検索ボックス / 非表示リストを閉じるボタン ---
        if (currentLauncherPage == 2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPaddingDp + 48.dp)
                    .zIndex(150f),
                contentAlignment = Alignment.TopCenter
            ) {
                if (isHideMode) {
                    Row(
                        modifier = Modifier
                            .width(320.dp)
                            .height(48.dp)
                            .background(Color(0x99101728), RoundedCornerShape(16.dp))
                            .clickable { isHideMode = false }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "非表示リストを閉じる ✕",
                            color = Color(0xFFFF5252),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .width(320.dp)
                            .height(48.dp)
                            .background(Color(0x99101728), RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            ),
                            cursorBrush = SolidColor(Color(0xFF88C0FF)),
                            modifier = Modifier.weight(1f)
                        ) { innerTextField ->
                            Box(contentAlignment = Alignment.Center) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "曲を検索…",
                                        color = Color.Gray.copy(alpha = 0.7f),
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                innerTextField()
                            }
                        }

                        if (searchQuery.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "✕",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .clickable { searchQuery = "" }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- 一覧ページ: 並び替え基準の切り替え（曲名 / アーティスト / アルバム） ---
        if (currentLauncherPage == 2 && !isHideMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPaddingDp + 106.dp)
                    .zIndex(150f),
                contentAlignment = Alignment.TopCenter
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("title" to "曲名", "artist" to "アーティスト", "album" to "アルバム").forEach { (mode, label) ->
                        val isSelected = librarySortMode == mode
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isSelected) accentColor else Color(0x99101728),
                                    RoundedCornerShape(999.dp)
                                )
                                .clickable {
                                    if (librarySortMode != mode) {
                                        librarySortMode = mode
                                        selectedAlbumFilter = null
                                        selectedArtistFilter = null
                                        repo.prefManager.setLibrarySortMode(mode)
                                        AppRepository.invalidateCache()
                                        refreshApps()
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // --- 一覧ページ: アルバム絞り込み中チップ ---
        if (currentLauncherPage == 2 && selectedAlbumFilter != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPaddingDp + 148.dp)
                    .zIndex(150f),
                contentAlignment = Alignment.TopCenter
            ) {
                Row(
                    modifier = Modifier
                        .background(accentColor, RoundedCornerShape(999.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "アルバム: $selectedAlbumFilter",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "✕",
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { selectedAlbumFilter = null }
                    )
                }
            }
        }

                // --- 一覧ページ: アーティスト絞り込み中チップ ---
        if (currentLauncherPage == 2 && selectedArtistFilter != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPaddingDp + 148.dp)
                    .zIndex(150f),
                contentAlignment = Alignment.TopCenter
            ) {
                Row(
                    modifier = Modifier
                        .background(accentColor, RoundedCornerShape(999.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "アーティスト: $selectedArtistFilter",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "✕",
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { selectedArtistFilter = null }
                    )
                }
            }
        }

        // --- 画面上部 フォルダ名＆編集バー / 新規作成バー (フォルダ画面(3)用、半透明ダークグラス) ---
        if (currentLauncherPage == 3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPaddingDp + 48.dp)
                    .zIndex(150f),
                contentAlignment = Alignment.TopCenter
            ) {
                if (selectedFolderId == "NEW_FOLDER") {
                    Row(
                        modifier = Modifier
                            .width(320.dp)
                            .height(48.dp)
                            .background(Color(0x99101728), RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        BasicTextField(
                            value = newFolderNameInput,
                            onValueChange = { newFolderNameInput = it },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            ),
                            cursorBrush = SolidColor(Color(0xFF88C0FF)),
                            modifier = Modifier.weight(1f)
                        ) { innerTextField ->
                            Box(contentAlignment = Alignment.Center) {
                                if (newFolderNameInput.isEmpty()) {
                                    Text(
                                        text = "新規フォルダ名を入力...",
                                        color = Color.Gray.copy(alpha = 0.7f),
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                innerTextField()
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        val defaultNewFolderName = "新規フォルダ"
                        TextButton(
                            onClick = {
                                val name = newFolderNameInput.ifBlank { defaultNewFolderName }
                                val newId = System.currentTimeMillis().toString()
                                repo.prefManager.saveFolder(newId, name, emptyList())
                                refreshApps()
                                newFolderNameInput = ""
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("作成", color = accentColor, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (selectedFolderId != null) {
                    Row(
                        modifier = Modifier
                            .width(320.dp)
                            .height(48.dp)
                            .background(Color(0x99101728), RoundedCornerShape(16.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isEditingFolder) {
                            // 編集モード中: 左半分 = 適用、右半分 = キャンセル
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable {
                                        val name = editingFolderNameInput.ifBlank { selectedFolderName }
                                        repo.prefManager.saveFolder(selectedFolderId, name, editingSelectedPackages.toList())
                                        refreshApps()
                                        isEditingFolder = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    WhiteCheckIcon(size = 18.dp, color = Color(0xFF4CAF50))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "適用",
                                        color = Color(0xFF4CAF50),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 中央区切り線
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(20.dp)
                                    .background(Color.White.copy(alpha = 0.2f))
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable {
                                        isEditingFolder = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    WhiteCloseIcon(size = 18.dp, color = Color.Gray)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "キャンセル",
                                        color = Color.LightGray,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            // 通常時: 左半分 = 編集、右半分 = 削除
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable {
                                        editingFolderNameInput = selectedFolderName
                                        editingSelectedPackages = (foldersMap[selectedFolderId]?.second ?: emptyList()).toSet()
                                        isEditingFolder = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    WhiteEditIcon(size = 18.dp, color = accentColor)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "編集",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 中央区切り線
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(20.dp)
                                    .background(Color.White.copy(alpha = 0.2f))
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable {
                                        showDeleteFolderDialog = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    WhiteDeleteIcon(size = 18.dp, color = Color(0xFFFF5252))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "削除",
                                        color = Color(0xFFFF5252),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 画面上部 フォルダ名編集欄 (編集モード中) ---
        if (currentLauncherPage == 3 && isEditingFolder) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPaddingDp + 102.dp)
                    .zIndex(150f),
                contentAlignment = Alignment.TopCenter
            ) {
                Row(
                    modifier = Modifier
                        .width(320.dp)
                        .height(44.dp)
                        .background(Color(0x99101728), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = editingFolderNameInput,
                        onValueChange = { editingFolderNameInput = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            fontFamily = LocalLauncherFontFamily.current
                        ),
                        cursorBrush = SolidColor(accentColor),
                        modifier = Modifier.weight(1f)
                    ) { innerTextField ->
                        Box(contentAlignment = Alignment.Center) {
                            if (editingFolderNameInput.isEmpty()) {
                                Text(
                                    text = "フォルダ名を入力...",
                                    color = Color.Gray.copy(alpha = 0.7f),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                            innerTextField()
                        }
                    }
                }
            }
        }

        // --- システムの戻るキー対応 ---
        BackHandler(enabled = currentLauncherPage != 1 || isSearchActive || isEditingFolder) {
            if (isEditingFolder) {
                isEditingFolder = false
            } else if (isSearchActive) {
                isSearchActive = false
                searchQuery = ""
                keyboardController?.hide()
            } else if (currentLauncherPage != 1) {
                changePageWithTransition(1)
            }
        }

        // --- ダイアログ類 ---
        // 0. パスコード認証ダイアログ (非表示リスト用)
        if (showPasscodeDialog) {
            PasscodeDialog(
                onDismiss = { showPasscodeDialog = false },
                onSuccess = {
                    isHideMode = true
                }
            )
        }

        // 0.0 ウェルカムチュートリアル
        if (showWelcomeTutorial) {
            TutorialDialog(
                title = "インストールしていただきありがとうございます",
                message = "インストールしていただきありがとうございます！\n\nアプリをお気に入り登録するには、アプリ一覧（三本線）でアプリを長押しし、「お気に入りに追加」をタップします。",
                onDismiss = { dontShow ->
                    if (dontShow) {
                        repo.prefManager.setFirstWelcomeTutorialShown(true)
                    }
                    showWelcomeTutorial = false
                }
            )
        }

        // 0.1 インデックスダイヤル解説チュートリアル
        if (showIndexTutorial) {
            TutorialDialog(
                title = "インデックスダイヤルの使い方",
                message = "左側の円（インデックスダイヤル）を上下にスワイプして回転させると、頭文字（あ・か・さ・た・な…）ごとにアプリリストを素早くジャンプ・スクロールできます。",
                onDismiss = { dontShow ->
                    if (dontShow) {
                        repo.prefManager.setFirstIndexTutorialShown(true)
                    }
                    showIndexTutorial = false
                    if (!repo.prefManager.isFirstHideTutorialShown()) {
                        showHideTutorial = true
                    }
                }
            )
        }

        // 0.2 非表示リスト右スワイプチュートリアル
        if (showHideTutorial) {
            TutorialDialog(
                title = "非表示リストの開き方",
                message = "左の円を長押ししたまま、画面の右端までグッとスワイプすると、パスコード認証を経て「非表示アプリリスト」を開くことができます。",
                onDismiss = { dontShow ->
                    if (dontShow) {
                        repo.prefManager.setFirstHideTutorialShown(true)
                    }
                    showHideTutorial = false
                }
            )
        }



        // タグ指定で歌詞を検索するダイアログ
        if (showLyricSearch) {
            LyricTagSearchDialog(
                currentTitle = musicPlayerManager.currentTitle,
                currentArtist = musicPlayerManager.currentArtist,
                currentAlbum = musicPlayerManager.currentAlbum,
                currentLines = lyricsLines ?: emptyList(),
                onApply = { applied, source ->
                    lyricsLines = applied
                    lyricsSource = source
                    lyricsSongUri = musicPlayerManager.currentSongUri
                    if (lyricsDisabledUri == musicPlayerManager.currentSongUri) lyricsDisabledUri = null
                    showLyrics = true
                },
                onCleared = {
                    lyricsLines = null
                    lyricsSongUri = null
                    lyricsSource = ""
                    lyricsNotFound = false
                    if (lyricsDisabledUri == musicPlayerManager.currentSongUri) lyricsDisabledUri = null
                    lyricsRefreshTick++
                },
                onDismiss = { showLyricSearch = false }
            )
        }

        // アップデート通知ダイアログ
        if (showUpdateDialog && updateInfo != null) {
            UpdateDialog(
                info = updateInfo!!,
                onDismiss = {
                    showUpdateDialog = false
                    UpdateChecker.markDismissed(context, updateInfo!!.buildNumber)
                }
            )
        }

        // 3. フォルダ削除確認ダイアログ
        if (showDeleteFolderDialog && selectedFolderId != null) {
            AlertDialog(
                onDismissRequest = { showDeleteFolderDialog = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            repo.prefManager.deleteFolder(selectedFolderId)
                            refreshApps()
                            showDeleteFolderDialog = false
                        }
                    ) {
                        Text("削除", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteFolderDialog = false }) {
                        Text("キャンセル", color = Color.Gray)
                    }
                },
                title = { Text("フォルダの削除", fontSize = 18.sp) },
                text = {
                    Text(
                        text = "「$selectedFolderName」を削除してもよろしいですか？（フォルダ内のアプリ自体はアンインストールされません）",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                },
                containerColor = Color(0xFF141C2E),
                titleContentColor = Color.White
            )
        }

        // 3. フォルダ内アプリ選択モーダル
        if (showFolderAppSelectDialog && selectedFolderId != null) {
            FolderAppSelectDialog(
                folderName = selectedFolderName,
                allApps = allApps,
                selectedPackages = foldersMap[selectedFolderId]?.second ?: emptyList(),
                onDismiss = { showFolderAppSelectDialog = false },
                onSave = { updatedPackages ->
                    repo.prefManager.saveFolder(selectedFolderId, selectedFolderName, updatedPackages)
                    refreshApps()
                }
            )
        }

        // 3.5 アルバム／アーティストの読み仮名登録ダイアログ
        nameYomiTarget?.let { target ->
            NameYomiDialog(
                name = target.label,
                kindLabel = if (target.packageName.startsWith("album::")) "アルバム" else "アーティスト",
                currentYomi = target.customYomi,
                onSave = { yomi ->
                    repo.setYomi(target.packageName, yomi)
                    yomiVersion++
                    nameYomiTarget = null
                },
                onDismiss = { nameYomiTarget = null }
            )
        }

        // 4. アプリ長押し設定ダイアログ
        selectedAppForOption?.let { app ->
            AppOptionDialog(
                app = app,
                onDismiss = { selectedAppForOption = null },
                onToggleFavorite = { targetApp ->
                    repo.setFavorite(targetApp.packageName, !targetApp.isFavorite)
                    refreshApps()
                },
                onSetYomi = { targetApp, yomi ->
                    repo.setYomi(targetApp.packageName, yomi)
                    refreshApps()
                },
                onAddAlias = { targetApp, alias ->
                    repo.addSearchAlias(targetApp.packageName, alias)
                    refreshApps()
                },
                onRemoveAlias = { targetApp, alias ->
                    repo.removeSearchAlias(targetApp.packageName, alias)
                    refreshApps()
                },
                onToggleHide = { targetApp ->
                    val willHide = !targetApp.isHidden
                    repo.setHidden(targetApp.packageName, willHide)
                    refreshApps()
                }
            )
        }
    }
}
}


/**
 * 文字PV風の1行表示。
 * 現在の行: 1文字ずつイーズアウトで大袈裟に入場（大きさ・角度・位置が文字ごとにバラバラ）。
 * 前の行（residual）: イーズインで小さく・薄く・ぼんやりしながら上へ移動して残る。
 * 漢字は大きめ、ひらがなは小さめにするとバランスが良い。
 */
/** 歌詞アイコン種別 */


/** アルバム名／アーティスト名の読み仮名を登録するダイアログ（並び替えと頭文字インデックスに使う） */
@Composable
fun NameYomiDialog(
    name: String,
    kindLabel: String,
    currentYomi: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var yomi by remember { mutableStateOf(currentYomi) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$kindLabel の読み仮名") },
        text = {
            Column {
                Text(name, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = yomi,
                    onValueChange = { yomi = it },
                    label = { Text("読み仮名（ひらがな）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "並び替えと頭文字の判定に使います。空にして保存すると登録を消します。",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(yomi) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

// 複数行モードの歌詞表示（VerifyError 予防のため MainLauncherScreen から切り出したトップレベル関数）
@Composable
private fun MusicMultiLineLyrics(
    lines: List<LyricLine>,
    positionMs: Long,
    lyricOffsetMs: Long,
    translationEnabled: Boolean,
    translations: List<String>?,
    fontFamily: FontFamily?,
    enFontFamily: FontFamily? = null,
    lyricWeight: FontWeight,
    accentColor: Color,
    screenWidthDp: Float
) {
    val mPos = positionMs + lyricOffsetMs
    val mRaw = lines.indexOfLast { it.timeMs <= mPos }
    val mCur = lines.getOrNull(mRaw)
    val mNext = lines.getOrNull(mRaw + 1)
    val mInterlude = when {
        mCur == null -> true
        mNext == null -> (mPos - mCur.timeMs) > 8000
        else -> (mNext.timeMs - mCur.timeMs) > 15000 && (mPos - mCur.timeMs) > 5000
    }
    Crossfade(targetState = mRaw, animationSpec = tween(250), label = "multiLyrics") { rawIdx ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 215.dp)
        ) {
            val prevText = lines.getOrNull(rawIdx - 1)?.text
            val curText = lines.getOrNull(rawIdx)?.text
            val nextText = lines.getOrNull(rawIdx + 1)?.text
            if (prevText != null) {
                Text(prevText, color = Color.White.copy(alpha = 0.45f), fontSize = fitFontSizeSp(prevText, 18f, screenWidthDp - 48f).sp, letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = LocalTextStyle.current.copy(fontFamily = fontFamily), modifier = Modifier.padding(start = 0.dp))
                if (translationEnabled) {
                    val trP = translations?.getOrNull(rawIdx - 1)
                    if (!trP.isNullOrBlank()) {
                        Text(trP, color = Color.White.copy(alpha = 0.35f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = LocalTextStyle.current.copy(fontFamily = fontFamily))
                    }
                }
            } else {
                Spacer(Modifier.height(24.dp))
            }
            Spacer(Modifier.height(16.dp))
            val curLineObj = lines.getOrNull(rawIdx)
            if (!mInterlude && curLineObj != null && curLineObj.words.isNotEmpty()) {
                KaraokeLyricText(
                    line = curLineObj,
                    positionMs = positionMs + lyricOffsetMs,
                    sungColor = accentColor,
                    baseColor = Color.White,
                    fontSize = fitFontSizeSp(curLineObj.text, 30f, screenWidthDp - 76f).sp,
                    fontWeight = lyricWeight,
                    fontFamily = fontFamily,
                    enFontFamily = enFontFamily,
                    letterSpacing = 3.sp,
                    shadow = Shadow(color = accentColor.copy(alpha = 0.8f), blurRadius = 16f),
                    modifier = Modifier.padding(start = 28.dp)
                )
            } else {
                Text(
                    text = if (mInterlude) "♪" else (curText ?: ""),
                    color = Color.White,
                    fontSize = fitFontSizeSp(if (mInterlude) "♪" else (curText ?: ""), 30f, screenWidthDp - 76f).sp,
                    fontWeight = lyricWeight,
                    letterSpacing = 3.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = LocalTextStyle.current.copy(fontFamily = fontFamily, shadow = Shadow(color = accentColor.copy(alpha = 0.8f), blurRadius = 16f)),
                    modifier = Modifier.padding(start = 28.dp)
                )
            }
            if (translationEnabled && !mInterlude) {
                val trC = translations?.getOrNull(rawIdx)
                if (!trC.isNullOrBlank()) {
                    Text(trC, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = LocalTextStyle.current.copy(fontFamily = fontFamily), modifier = Modifier.padding(start = 28.dp, top = 4.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            if (nextText != null) {
                Text(nextText, color = Color.White.copy(alpha = 0.45f), fontSize = fitFontSizeSp(nextText, 18f, screenWidthDp - 104f).sp, letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = LocalTextStyle.current.copy(fontFamily = fontFamily), modifier = Modifier.padding(start = 56.dp))
                if (translationEnabled) {
                    val trN = translations?.getOrNull(rawIdx + 1)
                    if (!trN.isNullOrBlank()) {
                        Text(trN, color = Color.White.copy(alpha = 0.35f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = LocalTextStyle.current.copy(fontFamily = fontFamily), modifier = Modifier.padding(start = 56.dp))
                    }
                }
            }
        }
    }
}
