package com.example.nissingeppomusic.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.nissingeppomusic.data.AppInfo
import com.example.nissingeppomusic.data.AppRepository
import com.example.nissingeppomusic.data.MusicPlayerManager
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

    // ページ管理 (0: 設定, 1: ホーム, 2: アプリ一覧, 3: フォルダ)
    var currentLauncherPage by remember { mutableIntStateOf(1) }

    var accentHex by remember { mutableStateOf(repo.prefManager.getAccentColorHex()) }
    val manualAccentColor = remember(accentHex) { parseHexColor(accentHex) }

    // アートワーク連動カラー: 設定でONのとき、再生中の曲のアートワークから
    // アクセント色（前景）と背景色を抽出してテーマカラーにする
    var matchArtwork by remember { mutableStateOf(repo.prefManager.isMatchArtworkColor()) }
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
    val accentColor = if (matchArtwork) artworkAccent ?: manualAccentColor else manualAccentColor

    // ラック（左円のキュー表示）表示中は、右円と下ダイヤルを隠す
    var isRackShown by remember { mutableStateOf(false) }

    // 下ダイヤルを押して拡大している間は、リピート/シャッフルボタンを隠す
    var isDialPressed by remember { mutableStateOf(false) }

    var showWelcomeTutorial by remember { mutableStateOf(false) }
    var showIndexTutorial by remember { mutableStateOf(false) }
    var showHideTutorial by remember { mutableStateOf(false) }

    // ページが切り替わるたびにアクセントカラー等を自動再読込
    LaunchedEffect(currentLauncherPage) {
        accentHex = repo.prefManager.getAccentColorHex()
        matchArtwork = repo.prefManager.isMatchArtworkColor()
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
    val activeDisc1CenterX = if (currentLauncherPage == 1) disc1CenterXHome else disc1CenterXNormal
    val disc1CenterY = screenHeightDp * 0.50f

    val disc2RadiusDp = 165f
    val disc2CenterX = screenWidthDp + 80f
    val disc2CenterY = screenHeightDp * 0.50f

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
            delay(220)
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
                customPath = customBackgroundPath,
                updateKey = backgroundUpdateKey,
                isHome = (currentLauncherPage == 1),
                currentAlbumArt = musicPlayerManager.currentAlbumArt,
                dynamicBackground = if (matchArtwork) artworkBackground else null
            )

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
                            },
                            onLibraryUpdated = {
                                refreshApps()
                            }
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
                    ) {
                        Text(
                            text = displayTitle,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.5.sp,
                            textAlign = TextAlign.Start
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = displayArtist,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            letterSpacing = 1.5.sp,
                            textAlign = TextAlign.Start
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = displayAlbum,
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            letterSpacing = 1.0.sp,
                            textAlign = TextAlign.Start
                        )
                    }

                    if (!isRackShown) {
                        MusicSeekBarDisc(
                            modifier = Modifier.fillMaxSize(),
                            playerManager = musicPlayerManager,
                            focusedTrack = focusedTrackPreview,
                            discCenterXDp = animatedDisc2CenterX,
                            discCenterYDp = disc2CenterY,
                            discRadiusDp = disc2RadiusDp,
                            accentColor = accentColor
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(50f)
                    ) {
                        MusicVinylWidget(
                            modifier = Modifier.fillMaxSize(),
                            playerManager = musicPlayerManager,
                            allApps = allApps,
                            discCenterXDp = animatedDisc1CenterX,
                            discCenterYDp = disc1CenterY,
                            discRadiusDp = disc1RadiusDp,
                            accentColor = accentColor,
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
                }
                2 -> {
                    // --- 2: アプリ一覧画面 ---
                    RotatableIndexWheel(
                        modifier = Modifier.fillMaxSize(),
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

                                        val displayedApps = remember(searchQuery, allApps, isHideMode, librarySortMode, selectedAlbumFilter, selectedArtistFilter) {
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
                                allApps.filter { !it.isHidden }
                                    .groupBy { it.album }
                                    .values
                                    .map { albumTracks ->
                                        val first = albumTracks.first()
                                        val (jp, en) = initialsOfLabel(first.album)
                                        first.copy(
                                            label = first.album,
                                            packageName = "album::" + first.album,
                                            initialJapanese = jp,
                                            initialEnglish = en
                                        )
                                    }
                                    // 頭文字の行順（あ→ん→A→Z）で昇順に並べ、左ホイールのインデックスと一致させる
                                    .sortedWith(compareBy(
                                        { val i = INDEX_ITEMS.indexOf(it.initialJapanese); if (i < 0) INDEX_ITEMS.size else i },
                                        { it.label.lowercase() }
                                    ))
                            }
                            librarySortMode == "artist" -> {
                                allApps.filter { !it.isHidden }
                                    .groupBy { it.artist }
                                    .values
                                    .map { artistTracks ->
                                        val first = artistTracks.first()
                                        val (jp, en) = initialsOfLabel(first.artist)
                                        first.copy(
                                            label = first.artist,
                                            packageName = "artist::" + first.artist,
                                            initialJapanese = jp,
                                            initialEnglish = en
                                        )
                                    }
                                    // 頭文字の行順（あ→ん→A→Z）で昇順に並べ、左ホイールのインデックスと一致させる
                                    .sortedWith(compareBy(
                                        { val i = INDEX_ITEMS.indexOf(it.initialJapanese); if (i < 0) INDEX_ITEMS.size else i },
                                        { it.label.lowercase() }
                                    ))
                            }
                            else -> allApps.filter { !it.isHidden }
                        }
                    }

                    RotatableAppDisc(
                        modifier = Modifier.fillMaxSize(),
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
                        onAppClick = { app ->
                            if (app.packageName.startsWith("album::")) {
                                selectedAlbumFilter = app.packageName.removePrefix("album::")
                            } else if (app.packageName.startsWith("artist::")) {
                                selectedArtistFilter = app.packageName.removePrefix("artist::")
                            } else {
                                onAppClick(app.packageName, displayedApps)
                            }
                        },
                        onLongClick = { app ->
                            if (!app.packageName.startsWith("album::") && !app.packageName.startsWith("artist::")) {
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
                            modifier = Modifier.fillMaxSize(),
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
                            modifier = Modifier.fillMaxSize(),
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
                        modifier = Modifier.fillMaxSize(),
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

                // --- 画面下部 ページ切り替え (ボタン式 / ダイヤル式を設定で切替、ラック表示中は隠す) ---
        if (!isRackShown) {
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
                    .zIndex(250f),
                onPressedChanged = { isDialPressed = it }
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
            )
        }
        }

        // --- ホーム画面のみ: リピート／シャッフルボタンを下ダイヤルの円周（右下）の外側に沿って配置 ---
        // ダイヤルの円: 中心は帯の下端から99dp下、半径165dp。
        // ボタンは輪郭の上に乗せず、少し離して外側（輪郭+ボタン半径+余白）に中心を置く
        if (currentLauncherPage == 1 && !isRackShown && !isDialPressed) {
            val dialRadiusForButtons = 192f
            val dialCenterBelowBand = 99f
            // リピート（円周の -54° の位置）
            run {
                val rad = -54f * (PI.toFloat() / 180f)
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
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "シャッフル",
                        tint = if (musicPlayerManager.shuffleEnabled) accentColor else Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
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
