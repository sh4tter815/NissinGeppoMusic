package com.example.nissingeppomusic.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nissingeppomusic.data.AppRepository
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt
import androidx.core.graphics.toColorInt

val ACCENT_PRESETS = listOf(
    Pair("赤", "#E53935"),
    Pair("青", "#88C0FF"),
    Pair("緑", "#4CAF50"),
    Pair("紫", "#AB47BC"),
    Pair("オレンジ", "#FF9800"),
    Pair("シアン", "#00E5FF"),
    Pair("ピンク", "#FF4081")
)

fun parseHexColor(hex: String, fallback: Color = Color(0xFFE53935)): Color {
    return try {
        Color(hex.toColorInt())
    } catch (_: Exception) {
        fallback
    }
}

/** フォルダピッカーのツリーURIを実ファイルパスへ変換する（内部ストレージ / SDカード想定） */
fun treeUriToPath(uri: Uri): String? {
    return try {
        val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
        val parts = docId.split(":", limit = 2)
        if (parts.size < 2) return null
        val base = if (parts[0] == "primary") "/storage/emulated/0" else "/storage/${parts[0]}"
        val relative = parts[1].trim('/')
        if (relative.isEmpty()) base else "${base}/${relative}"
    } catch (_: Exception) {
        null
    }
}

@Composable
fun SimpleTickSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 10,
    accentColor: Color = Color(0xFFE53935),
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    val stepCount = steps + 1
    val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .onGloballyPositioned { widthPx = it.size.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(valueRange, steps) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val updateVal = { xPx: Float ->
                            val f = (xPx / widthPx).coerceIn(0f, 1f)
                            val stepIdx = (f * stepCount).roundToInt().coerceIn(0, stepCount)
                            val newVal = valueRange.start + stepIdx * ((valueRange.endInclusive - valueRange.start) / stepCount)
                            onValueChange(newVal)
                        }
                        updateVal(down.position.x)
                        drag(down.id) { change ->
                            change.consume()
                            updateVal(change.position.x)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val startX = 12.dp.toPx()
            val endX = size.width - 12.dp.toPx()
            val usableWidth = endX - startX
            val centerY = size.height / 2f

            drawLine(
                color = Color.White.copy(alpha = 0.7f),
                start = Offset(startX, centerY),
                end = Offset(endX, centerY),
                strokeWidth = 2.5f
            )

            for (i in 0..stepCount) {
                val tickX = startX + i * (usableWidth / stepCount)
                drawLine(
                    color = Color.White.copy(alpha = 0.9f),
                    start = Offset(tickX, centerY - 6.dp.toPx()),
                    end = Offset(tickX, centerY + 6.dp.toPx()),
                    strokeWidth = 2f
                )
            }

            val thumbX = startX + fraction * usableWidth
            drawCircle(
                color = accentColor,
                radius = 9.dp.toPx(),
                center = Offset(thumbX, centerY)
            )
            drawCircle(
                color = Color.White,
                radius = 9.dp.toPx(),
                center = Offset(thumbX, centerY),
                style = Stroke(width = 2f)
            )
        }
    }
}

@Composable
fun FontSelectionSection(
    currentFontChoice: String,
    onFontSelected: (String) -> Unit,
    onImportClick: () -> Unit,
    customFontPath: String?,
    useCustomFont: Boolean,
    onUseCustomFontChanged: (Boolean) -> Unit,
    switchColors: SwitchColors
) {
    var expanded by remember { mutableStateOf(false) }
    val fontOptions = mapOf(
        "default" to "デフォルトフォント (Default)",
        "receipt" to "レシートフォント (Receipt Font)",
        "custom" to "カスタムフォント (.ttf/.otf)"
    )
    val currentLabel = fontOptions[currentFontChoice] ?: "デフォルトフォント (Default)"

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("フォント選択", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal)

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(currentLabel, fontSize = 14.sp)
                    Text("▼", fontSize = 12.sp)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF141C2E))
            ) {
                fontOptions.forEach { (key, label) ->
                    DropdownMenuItem(
                        text = { Text(label, color = Color.White) },
                        onClick = {
                            onFontSelected(key)
                            expanded = false
                        }
                    )
                }
            }
        }

        Button(
            onClick = onImportClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F293D)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("フォントファイル (.ttf / .otf) をインポート", color = Color.White, fontWeight = FontWeight.Light)
        }

        if (!customFontPath.isNullOrBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("カスタムフォントの利用", color = Color.White, fontSize = 13.sp)
                Switch(
                    checked = useCustomFont,
                    onCheckedChange = onUseCustomFontChanged,
                    colors = switchColors
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    repo: AppRepository,
    onBack: () -> Unit = {},
    onChanged: () -> Unit = {},
        onLibraryUpdated: () -> Unit = {}
) {
    val context = LocalContext.current
    var accentHex by remember { mutableStateOf(repo.prefManager.getAccentColorHex()) }
    val currentAccentColor = parseHexColor(accentHex)

    var iconSize by remember { mutableFloatStateOf(repo.prefManager.getAppDiscIconSizeDp()) }
    var textSize by remember { mutableFloatStateOf(repo.prefManager.getAppDiscTextSizeSp()) }
    var letterSpacing by remember { mutableFloatStateOf(repo.prefManager.getAppDiscLetterSpacingSp()) }
    var stepAngle by remember { mutableFloatStateOf(repo.prefManager.getAppDiscStepAngle()) }
    var backgroundPath by remember { mutableStateOf(repo.prefManager.getCustomBackgroundPath()) }
    var wallpaperAlpha by remember { mutableFloatStateOf(repo.prefManager.getHomeWallpaperAlpha()) }
    var showPasscodeChangeDialog by remember { mutableStateOf(false) }
    var newPasscodeField by remember { mutableStateOf(repo.prefManager.getPasscode()) }

    var isReverseScroll by remember { mutableStateOf(repo.prefManager.isReverseScroll()) }
    var usePasscodeForHidden by remember { mutableStateOf(repo.prefManager.isUsePasscodeForHidden()) }
    var currentFontChoice by remember { mutableStateOf(repo.prefManager.getFontChoice()) }
        var pageSwitchStyle by remember { mutableStateOf(repo.prefManager.getPageSwitchStyle()) }
        var libraryUpdateStatus by remember { mutableStateOf("") }
        var isUpdatingLibrary by remember { mutableStateOf(false) }
        val settingsScope = rememberCoroutineScope()

    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = currentAccentColor,
        checkedTrackColor = currentAccentColor.copy(alpha = 0.4f),
        uncheckedThumbColor = Color.Gray,
        uncheckedTrackColor = Color(0xFF1E283D)
    )

    // システム環境設定風 2段階ナビゲーション ("behavior", "appearance", "security", "tutorial")
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    // 外観内のサブタブ ("wallpaper", "color", "font")
    var appearanceSubTab by remember { mutableStateOf("wallpaper") }

    var customFontPath by remember { mutableStateOf(repo.prefManager.getCustomFontPath()) }
    var useCustomFont by remember { mutableStateOf(repo.prefManager.isUseCustomFont()) }

    // システム「戻る」キーを押した時、カテゴリ詳細画面なら設定トップに戻る
    BackHandler(enabled = selectedCategory != null) {
        selectedCategory = null
    }

    // 壁紙選択ピッカー
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(inputStream, null, options)

                    var sampleSize = 1
                    val maxDimension = 1920
                    while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
                        sampleSize *= 2
                    }

                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }

                    context.contentResolver.openInputStream(uri)?.use { stream2 ->
                        val bitmap = BitmapFactory.decodeStream(stream2, null, decodeOptions)
                        if (bitmap != null) {
                            val destFile = File(context.filesDir, "custom_bg.png")
                            FileOutputStream(destFile).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                            }
                            bitmap.recycle()
                            val path = destFile.absolutePath
                            repo.prefManager.setCustomBackgroundPath(path)
                            backgroundPath = path
                            onChanged()
                            Toast.makeText(context, "壁紙を変更しました", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(context, "壁紙の読み込みに失敗しました", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // フォントファイル選択ピッカー (.ttf / .otf)
    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val destFile = File(context.filesDir, "custom_font.ttf")
                    FileOutputStream(destFile).use { out ->
                        inputStream.copyTo(out)
                    }
                    val path = destFile.absolutePath
                    repo.prefManager.setCustomFontPath(path)
                    repo.prefManager.setUseCustomFont(true)
                    customFontPath = path
                    useCustomFont = true
                    onChanged()
                    Toast.makeText(context, "カスタムフォントを読み込みました", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, "フォントの読み込みに失敗しました", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var excludedFolders by remember { mutableStateOf(repo.prefManager.getExcludedFolders().sorted()) }

    // 除外フォルダ選択ピッカー
    val excludedFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = treeUriToPath(uri)
            if (path != null) {
                repo.prefManager.addExcludedFolder(path)
                excludedFolders = repo.prefManager.getExcludedFolders().sorted()
                onLibraryUpdated()
                Toast.makeText(context, "除外フォルダに追加しました", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "フォルダの場所を特定できませんでした", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x330B101D))
            .padding(top = 40.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // --- 上部 タイトルバー ---
            Surface(
                color = Color(0x99101728),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedCategory != null) {
                        Text(
                            text = "‹ 戻る",
                            color = currentAccentColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier
                                .clickable { selectedCategory = null }
                                .padding(end = 16.dp)
                        )
                    }
                    Text(
                        text = when (selectedCategory) {
                            "behavior" -> "動作設定"
                            "appearance" -> "外観設定"
                            "library" -> "ライブラリ"
                            "security" -> "セキュリティ"
                            "tutorial" -> "チュートリアル"
                            else -> "環境設定"
                        },
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            val categories = listOf(
                Triple("behavior", "動作", "ダイヤルのスクロール方向や操作感の設定"),
                Triple("appearance", "外観", "壁紙、配色、フォントなどの見た目のカスタマイズ"),
                Triple("library", "ライブラリ", "曲データの差分更新（追加・削除された曲の反映）"),
                Triple("security", "セキュリティ", "非表示アプリ用のパスコード(PIN)設定"),
                Triple("tutorial", "チュートリアル", "ガイドや使い方の再表示設定")
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (selectedCategory == null) {
                    // --- 1段階目: ジャンル選択リスト ---
                    items(categories) { (id, title, subtitle) ->
                        Surface(
                            color = Color(0x99101728),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCategory = id }
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = title,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = subtitle,
                                        color = Color.Gray,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Light
                                    )
                                }
                                Text(
                                    text = "›",
                                    color = Color.Gray,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Light
                                )
                            }
                        }
                    }
                } else {
                    // --- 2段階目: 各カテゴリの詳細設定 ---
                    when (selectedCategory) {
                        "behavior" -> {
                            item {
                                Surface(
                                    color = Color(0x99101728),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Text("ダイヤルの操作設定", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal)

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("スクロール方向の反転", color = Color.White, fontSize = 14.sp)
                                                Text("有効にすると、ダイヤルをドラッグした際の回転方向が逆になります", color = Color.Gray, fontSize = 12.sp)
                                            }
                                            Switch(
                                                checked = isReverseScroll,
                                                onCheckedChange = {
                                                    isReverseScroll = it
                                                    repo.prefManager.setReverseScroll(it)
                                                    onChanged()
                                                },
                                                colors = switchColors
                                            )
                                        }
                                    }
                                }
                            }
                        }
                                                "library" -> {
                            item {
                                Surface(
                                    color = Color(0x99101728),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Text("曲データの更新", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal)
                                        Text("端末内の曲の追加・削除・変更をチェックして、ライブラリに反映します（差分更新）。通常は起動時に自動でチェックされるため、手動更新が要るのは曲ファイルを入れ替えた直後などです。", color = Color.Gray, fontSize = 12.sp)
                                        Button(
                                            onClick = {
                                                if (!isUpdatingLibrary) {
                                                    isUpdatingLibrary = true
                                                    libraryUpdateStatus = "更新中…"
                                                    settingsScope.launch {
                                                        val tracks = repo.getMusicTracks(forceRefresh = true)
                                                        libraryUpdateStatus = "更新しました（全 ${tracks.size} 曲）"
                                                        isUpdatingLibrary = false
                                                        onLibraryUpdated()
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F293D)),
                                            elevation = null
                                        ) {
                                            Text("ライブラリを更新", color = Color.White, fontWeight = FontWeight.Light)
                                        }
                                        if (libraryUpdateStatus.isNotEmpty()) {
                                            Text(libraryUpdateStatus, color = Color.Gray, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                            item {
                                Surface(
                                    color = Color(0x99101728),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Text("除外フォルダ", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal)
                                        Text("ここに入れたフォルダ配下の曲は、ライブラリに表示されません。着信音やダウンロードした一時ファイルを置くフォルダなどにどうぞ。", color = Color.Gray, fontSize = 12.sp)
                                        if (excludedFolders.isEmpty()) {
                                            Text("（未設定）", color = Color.Gray, fontSize = 13.sp)
                                        } else {
                                            excludedFolders.forEach { folderPath ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(folderPath, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                                    OutlinedButton(
                                                        onClick = {
                                                            repo.prefManager.removeExcludedFolder(folderPath)
                                                            excludedFolders = repo.prefManager.getExcludedFolders().sorted()
                                                            onLibraryUpdated()
                                                        },
                                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                                                    ) {
                                                        Text("削除", fontWeight = FontWeight.Light)
                                                    }
                                                }
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Button(
                                                onClick = { excludedFolderPickerLauncher.launch(null) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F293D)),
                                                elevation = null
                                            ) {
                                                Text("フォルダを選んで追加", color = Color.White, fontWeight = FontWeight.Light)
                                            }
                                            val downloadPath = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).absolutePath
                                            if (!excludedFolders.contains(downloadPath)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        repo.prefManager.addExcludedFolder(downloadPath)
                                                        excludedFolders = repo.prefManager.getExcludedFolders().sorted()
                                                        onLibraryUpdated()
                                                    },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                                ) {
                                                    Text("Downloadを追加", fontWeight = FontWeight.Light)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "appearance" -> {
                            item {
                                Surface(
                                    color = Color(0x99101728),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text("ページ切り替えの方法", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal)
                                        Text("「ダイヤル」は画面下からチラ見えする円盤を長押しして横にドラッグし、回してページを切り替えます。", color = Color.Gray, fontSize = 12.sp)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf("buttons" to "ボタン", "dial" to "ダイヤル").forEach { (style, label) ->
                                                val isSelected = pageSwitchStyle == style
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(999.dp))
                                                        .background(if (isSelected) currentAccentColor else Color(0xFF1F293D))
                                                        .clickable {
                                                            pageSwitchStyle = style
                                                            repo.prefManager.setPageSwitchStyle(style)
                                                            onChanged()
                                                        }
                                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(label, color = Color.White, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    // 外観サブタブ選択
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(
                                            "wallpaper" to "壁紙",
                                            "color" to "配色",
                                            "font" to "フォント"
                                        ).forEach { (tabId, label) ->
                                            val isSelected = appearanceSubTab == tabId
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(40.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(if (isSelected) currentAccentColor else Color(0x99101728))
                                                    .clickable { appearanceSubTab = tabId },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = label,
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }

                                    // サブタブ内容
                                    when (appearanceSubTab) {
                                        "wallpaper" -> {
                                            Surface(
                                                color = Color(0x99101728),
                                                shape = RoundedCornerShape(20.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(20.dp),
                                                    verticalArrangement = Arrangement.spacedBy(20.dp)
                                                ) {
                                                    Text("壁紙画像", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal)
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Button(
                                                            onClick = { imagePickerLauncher.launch("image/*") },
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F293D)),
                                                            elevation = null
                                                        ) {
                                                            Text("画像を選択", color = Color.White, fontWeight = FontWeight.Light)
                                                        }
                                                        if (backgroundPath != null) {
                                                            OutlinedButton(
                                                                onClick = {
                                                                    repo.prefManager.setCustomBackgroundPath(null)
                                                                    backgroundPath = null
                                                                    onChanged()
                                                                },
                                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                                                                border = null
                                                            ) {
                                                                Text("解除", fontWeight = FontWeight.Light)
                                                            }
                                                        }
                                                    }

                                                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                                                    Column {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text("ホーム背景の明るさ", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Light)
                                                            Text("${((1f - wallpaperAlpha) * 100).toInt()}%", color = Color.LightGray, fontSize = 14.sp, fontWeight = FontWeight.Light)
                                                        }
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        SimpleTickSlider(
                                                            value = wallpaperAlpha,
                                                            onValueChange = {
                                                                wallpaperAlpha = it
                                                                repo.prefManager.setHomeWallpaperAlpha(it)
                                                                onChanged()
                                                            },
                                                            valueRange = 0.05f..0.9f,
                                                            steps = 17,
                                                            accentColor = currentAccentColor
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        "color" -> {
                                            Surface(
                                                color = Color(0x99101728),
                                                shape = RoundedCornerShape(20.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(20.dp),
                                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                                ) {
                                                    Text("アクセントカラー", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal)
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        ACCENT_PRESETS.forEach { (_, hex) ->
                                                            val color = parseHexColor(hex)
                                                            val isSelected = accentHex.equals(hex, ignoreCase = true)
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(36.dp)
                                                                    .clip(CircleShape)
                                                                    .background(color)
                                                                    .clickable {
                                                                        accentHex = hex
                                                                        repo.prefManager.setAccentColorHex(hex)
                                                                        onChanged()
                                                                    },
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                if (isSelected) {
                                                                    Box(
                                                                        modifier = Modifier
                                                                            .size(14.dp)
                                                                            .clip(CircleShape)
                                                                            .background(Color.White)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        "font" -> {
                                            Surface(
                                                color = Color(0x99101728),
                                                shape = RoundedCornerShape(20.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(20.dp),
                                                    verticalArrangement = Arrangement.spacedBy(20.dp)
                                                ) {
                                                    FontSelectionSection(
                                                        currentFontChoice = currentFontChoice,
                                                        onFontSelected = { newChoice ->
                                                            currentFontChoice = newChoice
                                                            repo.prefManager.setFontChoice(newChoice)
                                                            onChanged()
                                                        },
                                                        onImportClick = { fontPickerLauncher.launch("*/*") },
                                                        customFontPath = customFontPath,
                                                        useCustomFont = useCustomFont,
                                                        onUseCustomFontChanged = { use ->
                                                            useCustomFont = use
                                                            repo.prefManager.setUseCustomFont(use)
                                                            onChanged()
                                                        },
                                                        switchColors = switchColors
                                                    )

                                                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                                                    // 1. アイコンサイズ
                                                    Column {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text("アイコンサイズ", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Light)
                                                            Text("${iconSize.toInt()} dp", color = Color.LightGray, fontSize = 14.sp, fontWeight = FontWeight.Light)
                                                        }
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        SimpleTickSlider(
                                                            value = iconSize,
                                                            onValueChange = {
                                                                iconSize = it
                                                                repo.prefManager.setAppDiscIconSizeDp(it)
                                                                onChanged()
                                                            },
                                                            valueRange = 16f..32f,
                                                            steps = 15,
                                                            accentColor = currentAccentColor
                                                        )
                                                    }

                                                    // 2. テキストサイズ
                                                    Column {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text("テキストサイズ", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Light)
                                                            Text("${textSize.toInt()} sp", color = Color.LightGray, fontSize = 14.sp, fontWeight = FontWeight.Light)
                                                        }
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        SimpleTickSlider(
                                                            value = textSize,
                                                            onValueChange = {
                                                                textSize = it
                                                                repo.prefManager.setAppDiscTextSizeSp(it)
                                                                onChanged()
                                                            },
                                                            valueRange = 12f..22f,
                                                            steps = 9,
                                                            accentColor = currentAccentColor
                                                        )
                                                    }

                                                    // 3. 文字間隔
                                                    Column {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text("文字間隔", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Light)
                                                            Text(String.format(java.util.Locale.ROOT, "%.1f sp", letterSpacing), color = Color.LightGray, fontSize = 14.sp, fontWeight = FontWeight.Light)
                                                        }
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        SimpleTickSlider(
                                                            value = letterSpacing,
                                                            onValueChange = {
                                                                letterSpacing = it
                                                                repo.prefManager.setAppDiscLetterSpacingSp(it)
                                                                onChanged()
                                                            },
                                                            valueRange = 0.5f..3.0f,
                                                            steps = 25,
                                                            accentColor = currentAccentColor
                                                        )
                                                    }

                                                    // 4. リストの間隔
                                                    Column {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text("リストの間隔", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Light)
                                                            Text(String.format(java.util.Locale.ROOT, "%.1f°", stepAngle), color = Color.LightGray, fontSize = 14.sp, fontWeight = FontWeight.Light)
                                                        }
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        SimpleTickSlider(
                                                            value = stepAngle,
                                                            onValueChange = {
                                                                stepAngle = it
                                                                repo.prefManager.setAppDiscStepAngle(it)
                                                                onChanged()
                                                            },
                                                            valueRange = 10f..20f,
                                                            steps = 20,
                                                            accentColor = currentAccentColor
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "security" -> {
                            item {
                                Surface(
                                    color = Color(0x99101728),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(20.dp)
                                    ) {
                                        Text("セキュリティ設定", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal)

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("非表示アプリのパスコード認証", color = Color.White, fontSize = 14.sp)
                                                Text("有効にすると、非表示リストを開く際にパスコード認証を行います", color = Color.Gray, fontSize = 12.sp)
                                            }
                                            Switch(
                                                checked = usePasscodeForHidden,
                                                onCheckedChange = {
                                                    usePasscodeForHidden = it
                                                    repo.prefManager.setUsePasscodeForHidden(it)
                                                    onChanged()
                                                },
                                                colors = switchColors
                                            )
                                        }

                                        Column {
                                            Text("パスコード (PIN)", color = Color.White, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedButton(
                                                onClick = {
                                                    newPasscodeField = repo.prefManager.getPasscode()
                                                    showPasscodeChangeDialog = true
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                            ) {
                                                Text("パスコードを変更する")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        "tutorial" -> {
                            item {
                                Surface(
                                    color = Color(0x99101728),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        verticalArrangement = Arrangement.spacedBy(20.dp)
                                    ) {
                                        Text("チュートリアル設定", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Normal)

                                        Column {
                                            Text("ガイドの再表示", color = Color.White, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedButton(
                                                onClick = {
                                                    repo.prefManager.setFirstWelcomeTutorialShown(false)
                                                    repo.prefManager.setFirstIndexTutorialShown(false)
                                                    repo.prefManager.setFirstHideTutorialShown(false)
                                                    Toast.makeText(context, "チュートリアルを再表示するように設定しました", Toast.LENGTH_SHORT).show()
                                                    onChanged()
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                            ) {
                                                Text("チュートリアルを再表示する")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showPasscodeChangeDialog) {
            AlertDialog(
                onDismissRequest = { showPasscodeChangeDialog = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newPasscodeField.isNotBlank()) {
                                repo.prefManager.setPasscode(newPasscodeField)
                                showPasscodeChangeDialog = false
                            }
                        }
                    ) {
                        Text("保存", color = currentAccentColor, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPasscodeChangeDialog = false }) {
                        Text("キャンセル", color = Color.Gray)
                    }
                },
                title = { Text("パスコードの変更 (PIN)") },
                text = {
                    Column {
                        Text("非表示リスト解除用の新しいパスコードを入力してください。", color = Color.LightGray, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = newPasscodeField,
                            onValueChange = { newPasscodeField = it },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = currentAccentColor,
                                unfocusedBorderColor = Color.Gray
                            )
                        )
                    }
                },
                containerColor = Color(0xFF141C2E),
                titleContentColor = Color.White
            )
        }
    }
}
