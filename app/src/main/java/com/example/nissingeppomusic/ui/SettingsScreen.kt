package com.example.nissingeppomusic.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nissingeppomusic.data.AppRepository
import com.example.nissingeppomusic.ui.components.DiscEntry
import com.example.nissingeppomusic.data.SettingsTreeData
import com.example.nissingeppomusic.data.SettingItemDef
import com.example.nissingeppomusic.data.SettingNodeDef
import com.example.nissingeppomusic.ui.components.DiscInfoChip
import com.example.nissingeppomusic.ui.components.DiscValueWheel
import com.example.nissingeppomusic.ui.components.DiscGlyph
import com.example.nissingeppomusic.ui.components.WhiteSearchIcon
import com.example.nissingeppomusic.ui.components.DiscMiniSwitch
import com.example.nissingeppomusic.ui.components.lucidePath
import com.example.nissingeppomusic.ui.components.DiscSide
import com.example.nissingeppomusic.ui.components.DiscCarouselState
import com.example.nissingeppomusic.ui.components.DiscTouchHalf
import com.example.nissingeppomusic.ui.components.DiscWheel
import com.example.nissingeppomusic.ui.components.rememberDiscCarouselState
import com.example.nissingeppomusic.ui.components.parseFontFamily
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
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

// 円盤ナビの項目モデル
data class SettingChoice(
    val id: String,
    val label: String,
    val checked: Boolean,
    val illo: String? = null,
    val fontSample: FontFamily? = null,
    val colorHex: String? = null,
    val modePreview: String? = null,
    val onSelect: () -> Unit
)

data class SettingSliderSpec(
    val value: Float,
    val range: ClosedFloatingPointRange<Float>,
    val steps: Int,
    val format: (Float) -> String,
    val onSave: (Float) -> Unit
)

data class SettingItem(
    val id: String,
    val label: String,
    val desc: String,
    val glyph: String,
    val valueText: String,
    val choices: List<SettingChoice> = emptyList(),
    val slider: SettingSliderSpec? = null,
    val onAction: (() -> Unit)? = null,
    val onToggle: (() -> Unit)? = null,
    val currentIllo: String? = null,
    val currentFont: FontFamily? = null,
    val currentColorHex: String? = null
)

fun parseHexColor(hex: String, fallback: Color = Color(0xFFE53935)): Color {
    return try {
        Color(hex.toColorInt())
    } catch (_: Exception) {
        fallback
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
    val updateVal: (Float) -> Unit = { xPx ->
        val f = (xPx / widthPx).coerceIn(0f, 1f)
        val stepIdx = (f * stepCount).roundToInt().coerceIn(0, stepCount)
        onValueChange(valueRange.start + stepIdx * ((valueRange.endInclusive - valueRange.start) / stepCount))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .onGloballyPositioned { widthPx = it.size.width.toFloat().coerceAtLeast(1f) }
            // タップと横ドラッグだけをスライダーが受け取り、縦ドラッグは親のスクロールに渡す
            .pointerInput(valueRange, steps) {
                detectTapGestures { offset -> updateVal(offset.x) }
            }
            .pointerInput(valueRange, steps) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> updateVal(offset.x) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        updateVal(change.position.x)
                    }
                )
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
        "default" to "システムフォント",
        "receipt" to "レシートフォント (Receipt Font)",
        "denkichip" to "でんきチップ (DenkiChip)",
        "tsukuhou" to "築豊明朝 (Tsukuhou Mincho)",
        "kaibunsho" to "怪聞書フォント (Kaibunsho)",
        "zenkaku" to "Zen Kaku Gothic New (Medium)",
        "custom" to "カスタムフォント (.ttf/.otf)"
    )
    val currentLabel = fontOptions[currentFontChoice] ?: "システムフォント"

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

    }
}


// 3択などの選択肢を、イラスト付きのカードで選ぶための部品
@Composable
fun ChoiceIllustration(kind: String, accentColor: Color, selected: Boolean) {
    Canvas(modifier = Modifier.size(40.dp)) {
        val w = size.width
        val h = size.height
        val ink = Color.White.copy(alpha = 0.9f)
        val thin = Stroke(width = w * 0.05f)
        when (kind) {
            "disc_classic" -> {
                drawCircle(color = accentColor, radius = w * 0.19f, center = Offset(w * 0.30f, h * 0.50f))
                drawCircle(color = ink, radius = w * 0.19f, center = Offset(w * 0.70f, h * 0.50f))
            }
            "disc_artwork" -> {
                drawCircle(color = accentColor, radius = w * 0.26f, center = Offset(w * 0.36f, h * 0.50f))
                drawCircle(color = ink, radius = w * 0.12f, center = Offset(w * 0.78f, h * 0.50f))
            }
            "disc_auto" -> {
                drawCircle(color = accentColor, radius = w * 0.25f, center = Offset(w * 0.38f, h * 0.50f))
                val tri = Path()
                tri.moveTo(w * 0.31f, h * 0.37f)
                tri.lineTo(w * 0.31f, h * 0.63f)
                tri.lineTo(w * 0.52f, h * 0.50f)
                tri.close()
                drawPath(path = tri, color = Color(0xFF101728))
                drawCircle(color = ink, radius = w * 0.11f, center = Offset(w * 0.79f, h * 0.50f))
            }
            "date_sideways" -> {
                drawCircle(color = ink, radius = w * 0.20f, center = Offset(w * 0.33f, h * 0.50f), style = thin)
                drawLine(color = ink, start = Offset(w * 0.33f, h * 0.50f), end = Offset(w * 0.33f, h * 0.37f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
                drawLine(color = ink, start = Offset(w * 0.33f, h * 0.50f), end = Offset(w * 0.43f, h * 0.55f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
                drawCircle(color = accentColor, radius = w * 0.045f, center = Offset(w * 0.72f, h * 0.27f))
                drawCircle(color = accentColor, radius = w * 0.045f, center = Offset(w * 0.72f, h * 0.50f))
                drawCircle(color = accentColor, radius = w * 0.045f, center = Offset(w * 0.72f, h * 0.73f))
            }
            "date_under" -> {
                drawCircle(color = ink, radius = w * 0.17f, center = Offset(w * 0.50f, h * 0.36f), style = thin)
                drawLine(color = ink, start = Offset(w * 0.50f, h * 0.36f), end = Offset(w * 0.50f, h * 0.26f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
                drawLine(color = ink, start = Offset(w * 0.50f, h * 0.36f), end = Offset(w * 0.58f, h * 0.40f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
                drawLine(color = accentColor, start = Offset(w * 0.28f, h * 0.68f), end = Offset(w * 0.72f, h * 0.68f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
                drawLine(color = ink, start = Offset(w * 0.36f, h * 0.82f), end = Offset(w * 0.64f, h * 0.82f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
            }
            "date_auto" -> {
                drawCircle(color = ink, radius = w * 0.18f, center = Offset(w * 0.36f, h * 0.40f), style = thin)
                drawLine(color = ink, start = Offset(w * 0.36f, h * 0.40f), end = Offset(w * 0.36f, h * 0.29f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
                drawLine(color = ink, start = Offset(w * 0.36f, h * 0.40f), end = Offset(w * 0.45f, h * 0.45f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
                drawLine(color = accentColor, start = Offset(w * 0.56f, h * 0.70f), end = Offset(w * 0.84f, h * 0.70f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
                drawLine(color = accentColor, start = Offset(w * 0.84f, h * 0.70f), end = Offset(w * 0.74f, h * 0.61f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
                drawLine(color = accentColor, start = Offset(w * 0.84f, h * 0.86f), end = Offset(w * 0.56f, h * 0.86f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
                drawLine(color = accentColor, start = Offset(w * 0.56f, h * 0.86f), end = Offset(w * 0.66f, h * 0.77f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
            }
            "bg_wallpaper" -> {
                drawRoundRect(color = ink, topLeft = Offset(w * 0.13f, h * 0.17f), size = Size(w * 0.74f, h * 0.66f), cornerRadius = CornerRadius(w * 0.06f, w * 0.06f), style = thin)
                drawCircle(color = accentColor, radius = w * 0.08f, center = Offset(w * 0.68f, h * 0.35f))
                val m1 = Path()
                m1.moveTo(w * 0.20f, h * 0.75f)
                m1.lineTo(w * 0.42f, h * 0.45f)
                m1.lineTo(w * 0.58f, h * 0.75f)
                m1.close()
                drawPath(path = m1, color = ink.copy(alpha = 0.85f))
                val m2 = Path()
                m2.moveTo(w * 0.48f, h * 0.75f)
                m2.lineTo(w * 0.64f, h * 0.53f)
                m2.lineTo(w * 0.80f, h * 0.75f)
                m2.close()
                drawPath(path = m2, color = ink.copy(alpha = 0.6f))
            }
            "bg_solid" -> {
                drawRoundRect(color = accentColor, topLeft = Offset(w * 0.16f, h * 0.20f), size = Size(w * 0.68f, h * 0.60f), cornerRadius = CornerRadius(w * 0.06f, w * 0.06f))
            }
            "bg_blur" -> {
                drawCircle(color = accentColor.copy(alpha = 0.45f), radius = w * 0.20f, center = Offset(w * 0.38f, h * 0.42f))
                drawCircle(color = ink.copy(alpha = 0.35f), radius = w * 0.20f, center = Offset(w * 0.62f, h * 0.42f))
                drawCircle(color = Color(0xFF9AA0A6).copy(alpha = 0.40f), radius = w * 0.20f, center = Offset(w * 0.50f, h * 0.63f))
            }
        }
    }
}

@Composable
fun IllustratedChoiceCard(
    kind: String,
    label: String,
    selected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) accentColor.copy(alpha = 0.25f) else Color(0xFF1F293D),
        shape = RoundedCornerShape(16.dp),
        border = if (selected) BorderStroke(1.5.dp, accentColor) else null,
        modifier = modifier.height(84.dp).clickable { onClick() }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            ChoiceIllustration(kind = kind, accentColor = accentColor, selected = selected)
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = label,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

// 設定トップのカテゴリ行に付ける小さなイラスト（Canvas の簡単な線画）
@Composable
fun SettingsCategoryIcon(kind: String, accentColor: Color) {
    Canvas(modifier = Modifier.size(38.dp)) {
        val w = size.width
        val h = size.height
        val ink = Color.White.copy(alpha = 0.9f)
        when (kind) {
            "background" -> {
                // Lucide "image"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawRoundRect(color = ink, topLeft = Offset(3f * sc, 3f * sc), size = Size(18f * sc, 18f * sc), cornerRadius = CornerRadius(2f * sc, 2f * sc), style = ls)
                drawCircle(color = ink, radius = 2f * sc, center = Offset(9f * sc, 9f * sc), style = ls)
                drawPath(lucidePath("m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21", sc), color = ink, style = ls)
            }
            "ui" -> {
                // Lucide "sliders-horizontal"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("M10 5H3", sc), color = ink, style = ls)
                drawPath(lucidePath("M12 19H3", sc), color = ink, style = ls)
                drawPath(lucidePath("M14 3v4", sc), color = ink, style = ls)
                drawPath(lucidePath("M16 17v4", sc), color = ink, style = ls)
                drawPath(lucidePath("M21 12h-9", sc), color = ink, style = ls)
                drawPath(lucidePath("M21 19h-5", sc), color = ink, style = ls)
                drawPath(lucidePath("M21 5h-7", sc), color = ink, style = ls)
                drawPath(lucidePath("M8 10v4", sc), color = ink, style = ls)
                drawPath(lucidePath("M8 12H3", sc), color = ink, style = ls)
            }
            "lyrics" -> {
                // Lucide "audio-lines"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("M2 10v3", sc), color = ink, style = ls)
                drawPath(lucidePath("M6 6v11", sc), color = ink, style = ls)
                drawPath(lucidePath("M10 3v18", sc), color = ink, style = ls)
                drawPath(lucidePath("M14 8v7", sc), color = ink, style = ls)
                drawPath(lucidePath("M18 5v13", sc), color = ink, style = ls)
                drawPath(lucidePath("M22 10v3", sc), color = ink, style = ls)
            }
            "library" -> {
                // Lucide "folder"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z", sc), color = ink, style = ls)
            }
            "security" -> {
                // Lucide "lock"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawRoundRect(color = ink, topLeft = Offset(3f * sc, 11f * sc), size = Size(18f * sc, 11f * sc), cornerRadius = CornerRadius(2f * sc, 2f * sc), style = ls)
                drawPath(lucidePath("M7 11V7a5 5 0 0 1 10 0v4", sc), color = ink, style = ls)
            }
            "tools" -> {
                // Lucide "wrench"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.106-3.105c.32-.322.863-.22.983.218a6 6 0 0 1-8.259 7.057l-7.91 7.91a1 1 0 0 1-2.999-3l7.91-7.91a6 6 0 0 1 7.057-8.259c.438.12.54.662.219.984z", sc), color = ink, style = ls)
            }
            "other" -> {
                // Lucide "ellipsis"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawCircle(color = ink, radius = 1f * sc, center = Offset(12f * sc, 12f * sc), style = ls)
                drawCircle(color = ink, radius = 1f * sc, center = Offset(19f * sc, 12f * sc), style = ls)
                drawCircle(color = ink, radius = 1f * sc, center = Offset(5f * sc, 12f * sc), style = ls)
            }
            else -> {
                drawCircle(color = ink, radius = w * 0.30f, style = Stroke(width = w * 0.06f))
            }
        }
    }
}


// フォント選択カード: 閉じた状態では「現在のフォント名＋見本文字」だけを見せ、タップで候補リストを展開する
@Composable
fun FontPickerCard(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    accentColor: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelected: (String) -> Unit,
    fontFor: (String) -> FontFamily?,
    sampleText: String,
    footer: @Composable (() -> Unit)? = null
) {
    Surface(
        color = Color(0x99101728),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 14.sp)
                    Text(
                        text = options.firstOrNull { it.first == selected }?.second ?: "",
                        color = accentColor,
                        fontSize = 13.sp,
                        fontFamily = fontFor(selected)
                    )
                }
                Text(
                    text = sampleText,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 20.sp,
                    fontFamily = fontFor(selected),
                    modifier = Modifier.padding(end = 12.dp)
                )
                Text(if (expanded) "△" else "▽", color = Color.Gray, fontSize = 14.sp)
            }
            if (expanded) {
                options.forEach { (key, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(key) }
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = selected == key,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = accentColor, unselectedColor = Color.Gray)
                        )
                        Text(
                            text = label,
                            color = if (selected == key) accentColor else Color.White.copy(alpha = 0.8f),
                            fontSize = 15.sp,
                            fontFamily = fontFor(key)
                        )
                    }
                }
            }
            footer?.invoke()
        }
    }
}

@Composable
fun SettingsScreen(
    repo: AppRepository,
    onBack: () -> Unit = {},
    onChanged: () -> Unit = {},
    onAppsUpdated: () -> Unit = {},
    // 画面側で算出された現在のアクセント色（アートワーク連動時はその抽出色）
    accentColor: Color = Color(0xFFE53935),
    disc3d: Boolean = false,
    tiltX: State<Float>? = null,
    tiltY: State<Float>? = null
) {
    val context = LocalContext.current
    var accentHex by remember { mutableStateOf(repo.prefManager.getAccentColorHex()) }
    var matchArtwork by remember { mutableStateOf(repo.prefManager.isMatchArtworkColor()) }
    var liquidBackground by remember { mutableStateOf(repo.prefManager.isLiquidBackgroundEnabled()) }
    var homeDiscLayout by remember { mutableStateOf(repo.prefManager.getHomeDiscLayout()) }
    var disc3d by remember { mutableStateOf(repo.prefManager.isDisc3dEnabled()) }
    var disc3dTilt by remember { mutableStateOf(repo.prefManager.isDisc3dTiltEnabled()) }
    var dateDisplayMode by remember { mutableStateOf(repo.prefManager.getDateDisplayMode()) }
    // 配色をアートワークに合わせるONのときは、画面側で算出されたアートワーク由来の
    // アクセント色（accentColor引数）を設定UIの色にも使う。OFFのときは手動色
    val currentAccentColor = if (matchArtwork) accentColor else parseHexColor(accentHex)

    var iconSize by remember { mutableFloatStateOf(repo.prefManager.getAppDiscIconSizeDp()) }
    var textSize by remember { mutableFloatStateOf(repo.prefManager.getAppDiscTextSizeSp()) }
    var letterSpacing by remember { mutableFloatStateOf(repo.prefManager.getAppDiscLetterSpacingSp()) }
    var stepAngle by remember { mutableFloatStateOf(repo.prefManager.getAppDiscStepAngle()) }
    // 2026-11-23 一括追加分の状態
    var lyricsEnabled by remember { mutableStateOf(repo.prefManager.isLyricsEnabled()) }
    var lyricTranslation by remember { mutableStateOf(repo.prefManager.isLyricTranslationEnabled()) }
    var pvRuby by remember { mutableStateOf(repo.prefManager.isPvRubyEnabled()) }
    var pvInterludeArt by remember { mutableStateOf(repo.prefManager.isPvInterludeArtEnabled()) }
    var haptics by remember { mutableStateOf(repo.prefManager.isHapticsEnabled()) }
    var clockSeconds by remember { mutableStateOf(repo.prefManager.isClockShowSeconds()) }
    var clock12h by remember { mutableStateOf(repo.prefManager.isClock12h()) }
    var clockBattery by remember { mutableStateOf(repo.prefManager.isClockShowBattery()) }
    var dateWareki by remember { mutableStateOf(repo.prefManager.isDateWareki()) }
    var keepScreenOn by remember { mutableStateOf(repo.prefManager.isKeepScreenOn()) }
    var librarySort by remember { mutableStateOf(repo.prefManager.getLibrarySortMode()) }
    var pvTextColor by remember { mutableStateOf(repo.prefManager.getPvTextColor()) }
    var startupPage by remember { mutableStateOf(repo.prefManager.getStartupPage()) }
    var dialVisibility by remember { mutableStateOf(repo.prefManager.getDialVisibility()) }
    var discInertia by remember { mutableFloatStateOf(repo.prefManager.getDiscInertia()) }
    var pvFontScale by remember { mutableFloatStateOf(repo.prefManager.getPvFontScale()) }
    var pvTopOffset by remember { mutableFloatStateOf(repo.prefManager.getPvTopOffsetDp()) }
    var liquidSpeed by remember { mutableFloatStateOf(repo.prefManager.getLiquidSpeed()) }
    var liquidAlpha by remember { mutableFloatStateOf(repo.prefManager.getLiquidAlpha()) }
    var pageTransitionSpeed by remember { mutableFloatStateOf(repo.prefManager.getPageTransitionSpeed()) }
    var wallpaperBlur by remember { mutableFloatStateOf(repo.prefManager.getWallpaperBlurDp()) }
    var pageWallpapers by remember { mutableStateOf((0..3).map { repo.prefManager.getPageWallpaperPath(it) }) }
    var pendingWallpaperPage by remember { mutableIntStateOf(-1) }
    var artworkVisible by remember { mutableStateOf(repo.prefManager.isArtworkEnabled()) }
    var rackReverse by remember { mutableStateOf(repo.prefManager.isRackReverseScroll()) }
    var artworkOutline by remember { mutableStateOf(repo.prefManager.isArtworkOutlineEnabled()) }
    var curvedTitle by remember { mutableStateOf(repo.prefManager.isCurvedTitleEnabled()) }
    var caseCircleClip by remember { mutableStateOf(repo.prefManager.isCaseCircleClipEnabled()) }
    var discStyle by remember { mutableStateOf(repo.prefManager.getDiscStyle()) }
    var tonearmStyle by remember { mutableStateOf(repo.prefManager.getTonearmStyle()) }
    var widgetTapAction by remember { mutableStateOf(repo.prefManager.getWidgetTapAction()) }
    var widgetDoubleTapAction by remember { mutableStateOf(repo.prefManager.getWidgetDoubleTapAction()) }
    var widgetTripleTapAction by remember { mutableStateOf(repo.prefManager.getWidgetTripleTapAction()) }
    var widgetLongPressAction by remember { mutableStateOf(repo.prefManager.getWidgetLongPressAction()) }
    var widgetDoubleTapHoldAction by remember { mutableStateOf(repo.prefManager.getWidgetDoubleTapHoldAction()) }
    var backgroundPath by remember { mutableStateOf(repo.prefManager.getCustomBackgroundPath()) }
    var wallpaperAlpha by remember { mutableFloatStateOf(repo.prefManager.getHomeWallpaperAlpha()) }
    var excludedFolders by remember { mutableStateOf(repo.prefManager.getExcludedFolders().sorted()) }
    var showExcludedFoldersDialog by remember { mutableStateOf(false) }
    val excludedFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val path = treeUriToPath(uri)
            if (path != null) {
                repo.prefManager.addExcludedFolder(path)
                excludedFolders = repo.prefManager.getExcludedFolders().sorted()
                onAppsUpdated()
                android.widget.Toast.makeText(context, "除外フォルダに追加しました", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(context, "フォルダの場所を特定できませんでした", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    var showPasscodeChangeDialog by remember { mutableStateOf(false) }
    var newPasscodeField by remember { mutableStateOf(repo.prefManager.getPasscode()) }

    var isReverseScroll by remember { mutableStateOf(repo.prefManager.isReverseScroll()) }
    var sleepTimerEndMs by remember { mutableStateOf(repo.prefManager.getSleepTimerEndMs()) }
    var sleepTimerMinutes by remember { mutableStateOf(repo.prefManager.getSleepTimerMinutes()) }
    var sleepNowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            sleepNowMs = System.currentTimeMillis()
            if (sleepTimerEndMs > 0L && sleepTimerEndMs <= sleepNowMs) sleepTimerEndMs = 0L
            delay(20000)
        }
    }
    var usePasscodeForHidden by remember { mutableStateOf(repo.prefManager.isUsePasscodeForHidden()) }
    var currentFontChoice by remember { mutableStateOf(repo.prefManager.getFontChoice()) }
    var lyricsDisplayMode by remember { mutableStateOf(repo.prefManager.getLyricsDisplayMode()) }
    var fontPickerExpanded by remember { mutableStateOf<String?>(null) }
    var lyricsPvFont by remember { mutableStateOf(repo.prefManager.getLyricsPvFont()) }
    var lyricsEnFont by remember { mutableStateOf(repo.prefManager.getLyricsEnFont()) }
    var lyricsEnCustomPath by remember { mutableStateOf(repo.prefManager.getLyricsEnCustomFontPath()) }
    var lyricsBold by remember { mutableStateOf(repo.prefManager.isLyricsBold()) }
    var pvPartsEntry by remember { mutableStateOf(repo.prefManager.isPvPartsEntryEnabled()) }
    var dopagakiMode by remember { mutableStateOf(repo.prefManager.isDopagakiEnabled()) }
    var dopaBlackFont by remember { mutableStateOf(repo.prefManager.isDopaBlackFontEnabled()) }
        var pageSwitchStyle by remember { mutableStateOf(repo.prefManager.getPageSwitchStyle()) }
    var isUpdatingApps by remember { mutableStateOf(false) }
    val settingsScope = rememberCoroutineScope()

    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = currentAccentColor,
        checkedTrackColor = currentAccentColor.copy(alpha = 0.4f),
        uncheckedThumbColor = Color.Gray,
        uncheckedTrackColor = Color(0xFF1E283D)
    )

    // システム環境設定風 2段階ナビゲーション ("behavior", "appearance", "security", "tutorial")
    // 円盤ナビの状態（1: カテゴリ/項目, 2: 選択肢/スライダー）
    var navPath by remember { mutableStateOf<List<String>>(emptyList()) }
    var focusId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var leftDiscState by remember { mutableStateOf<DiscCarouselState?>(null) }
    var leftHeld by remember { mutableStateOf(false) }
    var backFocused by remember { mutableStateOf(false) }
    var rightDiscState by remember { mutableStateOf<DiscCarouselState?>(null) }
    fun goBack() {
        val dropped = navPath.lastOrNull() ?: return
        navPath = navPath.dropLast(1)
        focusId = dropped
    }
    // 退場（円が端に引っ込む）を挟んでから遷移する
    var discExiting by remember { mutableStateOf(false) }
    var navBusy by remember { mutableStateOf(false) }
    fun navigateAnimated(change: () -> Unit) {
        if (navBusy) return
        navBusy = true
        settingsScope.launch {
            discExiting = true
            kotlinx.coroutines.delay(175)
            change()
            discExiting = false
            navBusy = false
        }
    }
    // 階層を降りる時、右円で選んだラベルが回転しながら左円の縦ラベルへ飛ぶ
    var flightLabel by remember { mutableStateOf<String?>(null) }
    val flightAnim = remember { Animatable(0f) }
    var flightReverse by remember { mutableStateOf(false) }
    val backLabelState = remember { mutableStateOf<String?>(null) }
    fun startBackFlight() {
        val label = backLabelState.value ?: return
        flightReverse = true
        settingsScope.launch {
            flightLabel = label
            flightAnim.snapTo(0f)
            flightAnim.animateTo(1f, animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing))
            flightLabel = null
        }
    }
    var updateChecking by remember { mutableStateOf(false) }
    var knownBuild by remember { mutableStateOf(0) }
    var updateInfo by remember { mutableStateOf<com.example.nissingeppomusic.data.UpdateChecker.LatestRelease?>(null) }
    var fxChecking by remember { mutableStateOf(false) }
    var fxVersion by remember { mutableStateOf(LyricEffectsData.appliedVersion) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    if (showUpdateDialog && updateInfo != null) {
        UpdateDialog(
            info = updateInfo!!,
            onDismiss = {
                showUpdateDialog = false
                com.example.nissingeppomusic.data.UpdateChecker.markDismissed(context, updateInfo!!.buildNumber)
            }
        )
    }
    // 外観内のサブタブ ("wallpaper", "color", "font")

    var customFontPath by remember { mutableStateOf(repo.prefManager.getCustomFontPath()) }
    var useCustomFont by remember { mutableStateOf(repo.prefManager.isUseCustomFont()) }

    // システム「戻る」キーを押した時、カテゴリ詳細画面なら設定トップに戻る
    BackHandler(enabled = navPath.isNotEmpty() || searchQuery.isNotBlank()) {
        if (searchQuery.isNotBlank()) {
            searchQuery = ""
            focusId = null
        } else {
            startBackFlight()
            navigateAnimated { goBack() }
        }
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

    // ページ別壁紙ピッカー（保存先ファイルと pref をページごとに分ける以外は共通ピッカーと同じ）
    val pageImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val page = pendingWallpaperPage
        if (uri != null && page in 0..3) {
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
                            val destFile = File(context.filesDir, "custom_bg_p$page.png")
                            FileOutputStream(destFile).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                            }
                            bitmap.recycle()
                            val path = destFile.absolutePath
                            repo.prefManager.setPageWallpaperPath(page, path)
                            pageWallpapers = (0..3).map { repo.prefManager.getPageWallpaperPath(it) }
                            onChanged()
                            Toast.makeText(context, "壁紙を変更しました", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(context, "壁紙の読み込みに失敗しました", Toast.LENGTH_SHORT).show()
            }
        }
        pendingWallpaperPage = -1
    }

    // 設定バックアップ: 書き出し（作成先を選ぶ）と読み込み（ファイルを選ぶ）
    val settingsExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(repo.prefManager.exportAllJson().toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "設定を書き出しました", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, "書き出しに失敗しました", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val settingsImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                if (text != null && repo.prefManager.importAllJson(text)) {
                    onChanged()
                    onAppsUpdated()
                    Toast.makeText(context, "設定を読み込みました", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "読み込みに失敗しました（ファイルが違います）", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, "読み込みに失敗しました", Toast.LENGTH_SHORT).show()
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
                    repo.prefManager.setFontChoice("custom")
                    customFontPath = path
                    useCustomFont = true
                    currentFontChoice = "custom"
                    onChanged()
                    Toast.makeText(context, "カスタムフォントを読み込みました", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, "フォントの読み込みに失敗しました", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 英字フォント専用のカスタムフォント選択ピッカー (.ttf / .otf)
    val enFontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val destFile = File(context.filesDir, "custom_font_en.ttf")
                    FileOutputStream(destFile).use { out ->
                        inputStream.copyTo(out)
                    }
                    val path = destFile.absolutePath
                    repo.prefManager.setLyricsEnCustomFontPath(path)
                    repo.prefManager.setLyricsEnFont("custom")
                    lyricsEnCustomPath = path
                    lyricsEnFont = "custom"
                    onChanged()
                    Toast.makeText(context, "英字用カスタムフォントを読み込みました", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, "フォントの読み込みに失敗しました", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ===== 円盤ナビの項目モデル（状態と保存処理は従来のものをそのまま使う） =====
    fun toggleChoices(current: Boolean, set: (Boolean) -> Unit): List<SettingChoice> = listOf(
        SettingChoice("on", "オン", current) { set(true) },
        SettingChoice("off", "オフ", !current) { set(false) }
    )
    // ===== 設定ツリー（settings_tree.json 駆動）: 構造と項目定義はJSON、動作はここの対応表 =====
    data class SettingNode(
        val id: String,
        val label: String,
        val glyph: String? = null,
        val catIcon: Boolean = false,
        val item: SettingItem? = null,
        val children: List<SettingNode> = emptyList()
    )

    var treeData by remember { mutableStateOf(SettingsTreeData.load(context)) }
    LaunchedEffect(Unit) {
        val newer = SettingsTreeData.refresh(context)
        if (newer != null) treeData = newer
    }

    val boolBinds: Map<String, Pair<() -> Boolean, (Boolean) -> Unit>> = mapOf(
        "reverseScroll" to Pair({ isReverseScroll }, { v: Boolean -> isReverseScroll = v; repo.prefManager.setReverseScroll(v); onChanged() }),
        "disc3d" to Pair({ disc3d }, { v: Boolean -> disc3d = v; repo.prefManager.setDisc3dEnabled(v); onChanged() }),
        "disc3dTilt" to Pair({ disc3dTilt }, { v: Boolean -> disc3dTilt = v; repo.prefManager.setDisc3dTiltEnabled(v); onChanged() }),
        "liquidBackground" to Pair({ liquidBackground }, { v: Boolean -> liquidBackground = v; repo.prefManager.setLiquidBackgroundEnabled(v); onChanged() }),
        "matchArtwork" to Pair({ matchArtwork }, { v: Boolean -> matchArtwork = v; repo.prefManager.setMatchArtworkColor(v); onChanged() }),
        "lyricsBold" to Pair({ lyricsBold }, { v: Boolean -> lyricsBold = v; repo.prefManager.setLyricsBold(v); onChanged() }),
        "pvPartsEntry" to Pair({ pvPartsEntry }, { v: Boolean -> pvPartsEntry = v; repo.prefManager.setPvPartsEntryEnabled(v); onChanged() }),
        "dopagaki" to Pair({ dopagakiMode }, { v: Boolean -> dopagakiMode = v; repo.prefManager.setDopagakiEnabled(v); onChanged() }),
        "dopaBlackFont" to Pair({ dopaBlackFont }, { v: Boolean -> dopaBlackFont = v; repo.prefManager.setDopaBlackFontEnabled(v); onChanged() }),
        "usePasscodeForHidden" to Pair({ usePasscodeForHidden }, { v: Boolean -> usePasscodeForHidden = v; repo.prefManager.setUsePasscodeForHidden(v); onChanged() }),
        "lyricsEnabled" to Pair({ lyricsEnabled }, { v: Boolean -> lyricsEnabled = v; repo.prefManager.setLyricsEnabled(v); onChanged() }),
        "lyricTranslation" to Pair({ lyricTranslation }, { v: Boolean -> lyricTranslation = v; repo.prefManager.setLyricTranslationEnabled(v); onChanged() }),
        "pvRuby" to Pair({ pvRuby }, { v: Boolean -> pvRuby = v; repo.prefManager.setPvRubyEnabled(v); onChanged() }),
        "pvInterludeArt" to Pair({ pvInterludeArt }, { v: Boolean -> pvInterludeArt = v; repo.prefManager.setPvInterludeArtEnabled(v); onChanged() }),
        "haptics" to Pair({ haptics }, { v: Boolean -> haptics = v; repo.prefManager.setHapticsEnabled(v); onChanged() }),
        "clockSeconds" to Pair({ clockSeconds }, { v: Boolean -> clockSeconds = v; repo.prefManager.setClockShowSeconds(v); onChanged() }),
        "clock12h" to Pair({ clock12h }, { v: Boolean -> clock12h = v; repo.prefManager.setClock12h(v); onChanged() }),
        "clockBattery" to Pair({ clockBattery }, { v: Boolean -> clockBattery = v; repo.prefManager.setClockShowBattery(v); onChanged() }),
        "dateWareki" to Pair({ dateWareki }, { v: Boolean -> dateWareki = v; repo.prefManager.setDateWareki(v); onChanged() }),
        "keepScreenOn" to Pair({ keepScreenOn }, { v: Boolean -> keepScreenOn = v; repo.prefManager.setKeepScreenOn(v); onChanged() }),
        "artworkEnabled" to Pair({ artworkVisible }, { v: Boolean -> artworkVisible = v; repo.prefManager.setArtworkEnabled(v); onChanged() }),
        "rackReverse" to Pair({ rackReverse }, { v: Boolean -> rackReverse = v; repo.prefManager.setRackReverseScroll(v); onChanged() }),
        "artworkOutline" to Pair({ artworkOutline }, { v: Boolean -> artworkOutline = v; repo.prefManager.setArtworkOutlineEnabled(v); onChanged() }),
        "curvedTitle" to Pair({ curvedTitle }, { v: Boolean -> curvedTitle = v; repo.prefManager.setCurvedTitleEnabled(v); onChanged() }),
        "caseCircleClip" to Pair({ caseCircleClip }, { v: Boolean -> caseCircleClip = v; repo.prefManager.setCaseCircleClipEnabled(v); onChanged() })
    )

    val stringBinds: Map<String, Pair<() -> String, (String) -> Unit>> = mapOf(
        "pageSwitchStyle" to Pair({ pageSwitchStyle }, { v: String -> pageSwitchStyle = v; repo.prefManager.setPageSwitchStyle(v); onChanged() }),
        "homeDiscLayout" to Pair({ homeDiscLayout }, { v: String -> homeDiscLayout = v; repo.prefManager.setHomeDiscLayout(v); onChanged() }),
        "dateDisplayMode" to Pair({ dateDisplayMode }, { v: String -> dateDisplayMode = v; repo.prefManager.setDateDisplayMode(v); onChanged() }),
        "accentHex" to Pair({ accentHex }, { v: String -> accentHex = v; repo.prefManager.setAccentColorHex(v); onChanged() }),
        "fontChoice" to Pair({ currentFontChoice }, { v: String -> currentFontChoice = v; repo.prefManager.setFontChoice(v); useCustomFont = v == "custom"; repo.prefManager.setUseCustomFont(v == "custom"); onChanged() }),
        "lyricsDisplayMode" to Pair({ lyricsDisplayMode }, { v: String -> lyricsDisplayMode = v; repo.prefManager.setLyricsDisplayMode(v); onChanged() }),
        "lyricsPvFont" to Pair({ lyricsPvFont }, { v: String -> lyricsPvFont = v; repo.prefManager.setLyricsPvFont(v); onChanged() }),
        "lyricsEnFont" to Pair({ lyricsEnFont }, { v: String -> lyricsEnFont = v; repo.prefManager.setLyricsEnFont(v); onChanged() }),
        "pvTextColor" to Pair({ pvTextColor }, { v: String -> pvTextColor = v; repo.prefManager.setPvTextColor(v); onChanged() }),
        "startupPage" to Pair({ startupPage }, { v: String -> startupPage = v; repo.prefManager.setStartupPage(v); onChanged() }),
        "dialVisibility" to Pair({ dialVisibility }, { v: String -> dialVisibility = v; repo.prefManager.setDialVisibility(v); onChanged() }),
        "librarySort" to Pair({ librarySort }, { v: String -> librarySort = v; repo.prefManager.setLibrarySortMode(v); onChanged(); onAppsUpdated() }),
        "discStyle" to Pair({ discStyle }, { v: String -> discStyle = v; repo.prefManager.setDiscStyle(v); onChanged() }),
        "tonearmStyle" to Pair({ tonearmStyle }, { v: String -> tonearmStyle = v; repo.prefManager.setTonearmStyle(v); onChanged() }),
        "widgetTapAction" to Pair({ widgetTapAction }, { v: String -> widgetTapAction = v; repo.prefManager.setWidgetTapAction(v); onChanged() }),
        "widgetDoubleTapAction" to Pair({ widgetDoubleTapAction }, { v: String -> widgetDoubleTapAction = v; repo.prefManager.setWidgetDoubleTapAction(v); onChanged() }),
        "widgetTripleTapAction" to Pair({ widgetTripleTapAction }, { v: String -> widgetTripleTapAction = v; repo.prefManager.setWidgetTripleTapAction(v); onChanged() }),
        "widgetLongPressAction" to Pair({ widgetLongPressAction }, { v: String -> widgetLongPressAction = v; repo.prefManager.setWidgetLongPressAction(v); onChanged() }),
        "widgetDoubleTapHoldAction" to Pair({ widgetDoubleTapHoldAction }, { v: String -> widgetDoubleTapHoldAction = v; repo.prefManager.setWidgetDoubleTapHoldAction(v); onChanged() })
    )

    val floatBinds: Map<String, Pair<() -> Float, (Float) -> Unit>> = mapOf(
        "wallpaperAlpha" to Pair({ wallpaperAlpha }, { v: Float -> wallpaperAlpha = v; repo.prefManager.setHomeWallpaperAlpha(v); onChanged() }),
        "iconSize" to Pair({ iconSize }, { v: Float -> iconSize = v; repo.prefManager.setAppDiscIconSizeDp(v); onChanged() }),
        "textSize" to Pair({ textSize }, { v: Float -> textSize = v; repo.prefManager.setAppDiscTextSizeSp(v); onChanged() }),
        "letterSpacing" to Pair({ letterSpacing }, { v: Float -> letterSpacing = v; repo.prefManager.setAppDiscLetterSpacingSp(v); onChanged() }),
        "stepAngle" to Pair({ stepAngle }, { v: Float -> stepAngle = v; repo.prefManager.setAppDiscStepAngle(v); onChanged() }),
        "discInertia" to Pair({ discInertia }, { v: Float -> discInertia = v; repo.prefManager.setDiscInertia(v); onChanged() }),
        "pvFontScale" to Pair({ pvFontScale }, { v: Float -> pvFontScale = v; repo.prefManager.setPvFontScale(v); onChanged() }),
        "pvTopOffset" to Pair({ pvTopOffset }, { v: Float -> pvTopOffset = v; repo.prefManager.setPvTopOffsetDp(v); onChanged() }),
        "liquidSpeed" to Pair({ liquidSpeed }, { v: Float -> liquidSpeed = v; repo.prefManager.setLiquidSpeed(v); onChanged() }),
        "liquidAlpha" to Pair({ liquidAlpha }, { v: Float -> liquidAlpha = v; repo.prefManager.setLiquidAlpha(v); onChanged() }),
        "pageTransitionSpeed" to Pair({ pageTransitionSpeed }, { v: Float -> pageTransitionSpeed = v; repo.prefManager.setPageTransitionSpeed(v); onChanged() }),
        "wallpaperBlur" to Pair({ wallpaperBlur }, { v: Float -> wallpaperBlur = v; repo.prefManager.setWallpaperBlurDp(v); onChanged() })
    )

    val sliderFormats: Map<String, (Float) -> String> = mapOf(
        "percentInv" to { v: Float -> "${((1f - v) * 100).toInt()}%" },
        "dp" to { v: Float -> "${v.toInt()} dp" },
        "sp" to { v: Float -> "${v.toInt()} sp" },
        "sp1" to { v: Float -> String.format(java.util.Locale.ROOT, "%.1f sp", v) },
        "deg" to { v: Float -> String.format(java.util.Locale.ROOT, "%.1f°", v) },
        "x" to { v: Float -> String.format(java.util.Locale.ROOT, "×%.1f", v) },
        "pct" to { v: Float -> "${(v * 100).toInt()}%" }
    )

    val fontResolvers: Map<String, Pair<() -> FontFamily?, (String) -> FontFamily?>> = mapOf(
        "uiFont" to Pair(
            { parseFontFamily(currentFontChoice, if (currentFontChoice == "custom") customFontPath else null) },
            { k: String -> parseFontFamily(k, if (k == "custom") customFontPath else null) }
        ),
        "jpFont" to Pair(
            { parseFontFamily(lyricsPvFont, if (lyricsPvFont == "custom") customFontPath else null) },
            { k: String -> parseFontFamily(k, if (k == "custom") customFontPath else null) }
        ),
        "enFont" to Pair(
            {
                when (lyricsEnFont) {
                    "same" -> parseFontFamily(lyricsPvFont, if (lyricsPvFont == "custom") customFontPath else null)
                    "custom" -> parseFontFamily("custom", lyricsEnCustomPath)
                    else -> parseFontFamily(lyricsEnFont, null)
                }
            },
            { k: String ->
                when (k) {
                    "same" -> parseFontFamily(lyricsPvFont, if (lyricsPvFont == "custom") customFontPath else null)
                    "custom" -> parseFontFamily("custom", lyricsEnCustomPath)
                    else -> parseFontFamily(k, null)
                }
            }
        )
    )

    val choiceEffects: Map<String, (String) -> Unit> = mapOf(
        "pickFont" to { v: String -> if (v == "custom") fontPickerLauncher.launch("*/*") },
        "pickFontIfNone" to { v: String -> if (v == "custom" && customFontPath == null) fontPickerLauncher.launch("*/*") },
        "pickEnFont" to { v: String -> if (v == "custom") enFontPickerLauncher.launch("*/*") }
    )

    val actionBuilders: Map<String, (SettingItemDef) -> SettingItem> = mapOf(
        "wallpaperPick" to { def: SettingItemDef ->
            SettingItem(
                def.id, def.label, def.desc, def.glyph,
                valueText = if (backgroundPath != null) "設定済み" else "未設定",
                onAction = { imagePickerLauncher.launch("image/*") }
            )
        },
        "wallpaperPickPage" to { def: SettingItemDef ->
            val page = when (def.id) {
                "wallpaperPickHome" -> 1
                "wallpaperPickApps" -> 2
                "wallpaperPickFolder" -> 3
                else -> 0
            }
            SettingItem(
                def.id, def.label, def.desc, def.glyph,
                valueText = if (pageWallpapers.getOrNull(page) != null) "設定済み" else "共通",
                onAction = {
                    pendingWallpaperPage = page
                    pageImagePickerLauncher.launch("image/*")
                }
            )
        },
        "wallpaperClearPages" to { def: SettingItemDef ->
            SettingItem(
                def.id, def.label, def.desc, def.glyph, valueText = "",
                onAction = {
                    (0..3).forEach { repo.prefManager.setPageWallpaperPath(it, null) }
                    pageWallpapers = (0..3).map { null }
                    onChanged()
                }
            )
        },
        "wallpaperClear" to { def: SettingItemDef ->
            SettingItem(
                def.id, def.label, def.desc, def.glyph, valueText = "",
                onAction = {
                    repo.prefManager.setCustomBackgroundPath(null)
                    backgroundPath = null
                    onChanged()
                }
            )
        },
        "updateLibrary" to { def: SettingItemDef ->
            SettingItem(
                def.id, def.label, def.desc, def.glyph,
                valueText = "差分更新",
                onAction = {
                    if (!isUpdatingApps) {
                        isUpdatingApps = true
                        settingsScope.launch {
                            val tracks = repo.getMusicTracks(forceRefresh = true)
                            isUpdatingApps = false
                            android.widget.Toast.makeText(context, "ライブラリを更新しました（全 ${tracks.size} 曲）", android.widget.Toast.LENGTH_SHORT).show()
                            onAppsUpdated()
                        }
                    }
                }
            )
        },
        "excludedFolders" to { def: SettingItemDef ->
            SettingItem(
                def.id, def.label, def.desc, def.glyph,
                valueText = if (excludedFolders.isEmpty()) "なし" else "${excludedFolders.size}件",
                onAction = { showExcludedFoldersDialog = true }
            )
        },
        "layoutEdit" to { def: SettingItemDef ->
            SettingItem(
                def.id, def.label, def.desc, def.glyph, valueText = "",
                onAction = {
                    repo.prefManager.requestLayoutEdit()
                    onChanged()
                    android.widget.Toast.makeText(context, "ホームで編集モードを開きます", android.widget.Toast.LENGTH_SHORT).show()
                }
            )
        },
        "settingsExport" to { def: SettingItemDef ->
            SettingItem(
                def.id, def.label, def.desc, def.glyph, valueText = "",
                onAction = { settingsExportLauncher.launch("nissingeppo_settings.json") }
            )
        },
        "settingsImport" to { def: SettingItemDef ->
            SettingItem(
                def.id, def.label, def.desc, def.glyph, valueText = "",
                onAction = { settingsImportLauncher.launch("*/*") }
            )
        },
        "treeUpdate" to { def: SettingItemDef ->
            SettingItem(
                def.id, def.label, def.desc, def.glyph,
                valueText = "v${treeData.version}",
                onAction = {
                    settingsScope.launch {
                        val newer = com.example.nissingeppomusic.data.SettingsTreeData.refresh(context)
                        if (newer != null) {
                            treeData = newer
                            android.widget.Toast.makeText(context, "設定データを更新しました（v${newer.version}）", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, "設定データは最新です", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        },
        "fxUpdate" to { def: SettingItemDef ->
            SettingItem(
                def.id, def.label, def.desc, def.glyph,
                valueText = "v$fxVersion",
                onAction = {
                    if (!fxChecking) {
                        fxChecking = true
                        settingsScope.launch {
                            val result = LyricEffectsData.refresh(context)
                            fxChecking = false
                            fxVersion = LyricEffectsData.appliedVersion
                            when (result) {
                                0 -> android.widget.Toast.makeText(context, "最新のデータです（データ v${LyricEffectsData.appliedVersion}）", android.widget.Toast.LENGTH_SHORT).show()
                                1 -> android.widget.Toast.makeText(context, "歌詞データを更新しました（v${LyricEffectsData.appliedVersion}）", android.widget.Toast.LENGTH_SHORT).show()
                                else -> android.widget.Toast.makeText(context, "確認に失敗しました" + (LyricEffectsData.lastRefreshError?.let { "（$it）" } ?: ""), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )
        },
        "checkUpdate" to { def: SettingItemDef ->
            SettingItem(
                def.id, def.label, def.desc, def.glyph,
                valueText = if (knownBuild > 0) "#$knownBuild" else "—",
                onAction = {
                    if (!updateChecking) {
                        updateChecking = true
                        settingsScope.launch {
                            val info = com.example.nissingeppomusic.data.UpdateChecker.fetchLatest(context)
                            updateChecking = false
                            if (info != null) knownBuild = info.buildNumber
                            if (info?.isNewer == true) {
                                updateInfo = info
                                showUpdateDialog = true
                            } else if (info == null) {
                                android.widget.Toast.makeText(context, "確認に失敗しました", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(context, "新しいビルドはありませんでした（最新: #${info.buildNumber}）", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )
        },
        "passcodeChange" to { def: SettingItemDef ->
            SettingItem(
                def.id, def.label, def.desc, def.glyph, valueText = "",
                onAction = {
                    newPasscodeField = repo.prefManager.getPasscode()
                    showPasscodeChangeDialog = true
                }
            )
        },
        "guideReshow" to { def: SettingItemDef ->
            SettingItem(
                def.id, def.label, def.desc, def.glyph, valueText = "",
                onAction = {
                    repo.prefManager.setFirstWelcomeTutorialShown(false)
                    repo.prefManager.setFirstIndexTutorialShown(false)
                    repo.prefManager.setFirstHideTutorialShown(false)
                    Toast.makeText(context, "チュートリアルを再表示するように設定しました", Toast.LENGTH_SHORT).show()
                    onChanged()
                }
            )
        }
    )

    val specialBuilders: Map<String, (SettingItemDef) -> SettingItem> = mapOf(
        "sleep" to { def: SettingItemDef ->
            SettingItem(
                def.id, def.label, def.desc, def.glyph,
                valueText = if (sleepTimerEndMs > sleepNowMs) "あと約 ${(sleepTimerEndMs - sleepNowMs + 59999L) / 60000L} 分" else "オフ",
                choices = listOf(0, 15, 30, 60).map { min ->
                    SettingChoice(
                        id = "min$min", label = if (min == 0) "オフ" else "${min}分",
                        checked = if (min == 0) sleepTimerEndMs <= 0L else (sleepTimerEndMs > sleepNowMs && sleepTimerMinutes == min)
                    ) {
                        sleepTimerMinutes = min
                        repo.prefManager.setSleepTimerMinutes(min)
                        val end = if (min == 0) 0L else System.currentTimeMillis() + min * 60000L
                        sleepTimerEndMs = end
                        repo.prefManager.setSleepTimerEndMs(end)
                        onChanged()
                    }
                }
            )
        },
    )

    fun buildItem(def: SettingItemDef): SettingItem? {
        return when (def.type) {
            "toggle" -> {
                val b = boolBinds[def.bind] ?: return null
                val cur = b.first()
                SettingItem(
                    def.id, def.label, def.desc, def.glyph,
                    valueText = if (cur) "オン" else "オフ",
                    choices = toggleChoices(cur) { v -> b.second(v) },
                    onToggle = { b.second(!cur); onChanged() }
                )
            }
            "choices" -> {
                val b = stringBinds[def.bind] ?: return null
                val cur = b.first()
                val sel = def.choices.firstOrNull { it.v == cur }
                SettingItem(
                    def.id, def.label, def.desc, def.glyph,
                    valueText = sel?.let { it.short ?: it.label } ?: cur,
                    currentIllo = sel?.illo,
                    currentColorHex = if (def.id == "accentColor") cur else null,
                    currentFont = fontResolvers[def.id]?.first?.invoke(),
                    choices = def.choices.map { c ->
                        SettingChoice(
                            id = c.v, label = c.label, checked = cur == c.v,
                            illo = c.illo, colorHex = c.colorHex,
                            fontSample = fontResolvers[def.id]?.second?.invoke(c.v),
                            modePreview = c.preview
                        ) {
                            b.second(c.v)
                            c.effect?.let { eff -> choiceEffects[eff]?.invoke(c.v) }
                        }
                    }
                )
            }
            "slider" -> {
                val b = floatBinds[def.bind] ?: return null
                val fmt = sliderFormats[def.format] ?: { v: Float -> v.toString() }
                val cur = b.first()
                SettingItem(
                    def.id, def.label, def.desc, def.glyph,
                    valueText = fmt(cur),
                    slider = SettingSliderSpec(cur, def.min..def.max, def.steps, fmt) { v -> b.second(v) }
                )
            }
            "action" -> actionBuilders[def.action]?.invoke(def)
            "special" -> specialBuilders[def.id]?.invoke(def)
            else -> null
        }
    }

    fun buildNode(def: SettingNodeDef): SettingNode? {
        if (def.itemId != null) {
            val itemDef = treeData.items[def.itemId] ?: return null
            if (itemDef.visibleIf == "wallpaperSet" && backgroundPath == null) return null
            val item = buildItem(itemDef) ?: return null
            return SettingNode(itemDef.id, itemDef.label, glyph = itemDef.glyph, item = item)
        }
        val kids = def.children.mapNotNull { buildNode(it) }
        return SettingNode(def.id, def.label, glyph = def.glyph, catIcon = def.cat, children = kids)
    }
    val settingsTree: List<SettingNode> = treeData.tree.mapNotNull { buildNode(it) }

    val searchResults: List<SettingNode> = if (searchQuery.isBlank()) emptyList() else {
        val all = mutableListOf<SettingNode>()
        fun walk(nodes: List<SettingNode>) {
            for (n in nodes) {
                if (n.item != null) all += n
                walk(n.children)
            }
        }
        walk(settingsTree)
        all.filter { it.label.contains(searchQuery, ignoreCase = true) || (it.item?.desc?.contains(searchQuery, ignoreCase = true) == true) }
    }
    val currentList: List<SettingNode> = if (searchQuery.isNotBlank()) searchResults else navPath.fold(settingsTree) { list, id -> list.firstOrNull { it.id == id }?.children ?: emptyList() }
    val focusedNode: SettingNode? = currentList.firstOrNull { it.id == focusId } ?: currentList.firstOrNull()
    val currentItem: SettingItem? = focusedNode?.item
    val ownerNode: SettingNode? = run {
        var list = settingsTree
        var node: SettingNode? = null
        for (id in navPath) {
            node = list.firstOrNull { it.id == id }
            list = node?.children ?: emptyList()
        }
        node
    }
    backLabelState.value = focusedNode?.label

    val leftEntryClick: (Int) -> Unit = { idx ->
        if (idx >= currentList.size) {
            startBackFlight()
            navigateAnimated { goBack() }
        } else {
            val node = currentList[idx]
            val act = node.item?.onAction
            leftDiscState?.animateToIndex(idx)
            if (act != null) act.invoke() else focusId = node.id
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x330B101D))
    ) {
            // --- 階層ツリーの2円ナビ（左=現在の階層の一覧, 右=フォーカス中の子一覧 or 選択肢） ---
            val statusTopDp = with(LocalDensity.current) { WindowInsets.statusBars.getTop(LocalDensity.current).toDp() }
            val discDensity = LocalDensity.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (disc3d) Modifier.graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            rotationX = 27f + (tiltX?.value ?: 0f)
                            rotationY = tiltY?.value ?: 0f
                            cameraDistance = 6.5f * discDensity.density
                        } else Modifier
                    )
            ) {
                fun nodeEntry(node: SettingNode): DiscEntry = DiscEntry(
                    id = node.id, label = node.label,
                    valueText = node.item?.valueText?.ifEmpty { null },
                    toggleState = node.item?.let { item -> if (item.onToggle != null) item.valueText == "オン" else null },
                    icon = {
                        if (node.catIcon) SettingsCategoryIcon(kind = node.id, accentColor = currentAccentColor)
                        else DiscGlyph(node.glyph ?: node.item?.glyph ?: "toggle", currentAccentColor)
                    }
                )
                // 左円: 現在の階層の一覧（パネル駆動）
                key("tree-left-${navPath.joinToString("/")}-$searchQuery") {
                    val lState = rememberDiscCarouselState(currentList.indexOfFirst { it.id == focusId }.coerceAtLeast(0), stepAngle)
                    LaunchedEffect(lState) { leftDiscState = lState }
                    DiscWheel(
                        entries = currentList.map { nodeEntry(it) } + (
                            if (navPath.isNotEmpty() && searchQuery.isBlank()) listOf(
                                DiscEntry(id = "__back", label = "戻る", immediate = true, icon = { DiscGlyph("back", currentAccentColor) })
                            ) else emptyList()
                        ),
                        side = DiscSide.LEFT,
                        accentColor = currentAccentColor,
                        modifier = Modifier.fillMaxSize(),
                        state = lState, gesturesEnabled = false, stepAngle = stepAngle, iconSizeDp = iconSize, textSizeSp = textSize, letterSpacingSp = letterSpacing,
                        focusedLabelOnly = true, fadeLabelsOnScroll = false, showValues = false, verticalRestLabel = true,
                        forceSlideOut = discExiting, holdExpand = true, holdActive = leftHeld,
                        contentKey = navPath.joinToString("/") + "-" + searchQuery,
                        initialFocus = currentList.indexOfFirst { it.id == focusId }.coerceAtLeast(0),
                        onFocusChange = { idx ->
                            backFocused = idx >= currentList.size
                            currentList.getOrNull(idx)?.let { focusId = it.id }
                        },
                        onEntryClick = leftEntryClick
                    )
                }
                // 右円: 子一覧 / ゲージ / 選択肢（「戻る」にフォーカス中は右円に何も表示しない）
                val currentItem = if (backFocused) null else currentItem
                val fNode = if (backFocused) null else focusedNode
                if (fNode != null && fNode.item == null && fNode.children.isNotEmpty()) {
                    key("tree-right-${navPath.joinToString("/")}-${fNode.id}") {
                        val rState = rememberDiscCarouselState(0, stepAngle)
                        LaunchedEffect(rState) { rightDiscState = rState }
                        DiscWheel(
                            entries = fNode.children.map { nodeEntry(it) },
                            side = DiscSide.RIGHT,
                            accentColor = currentAccentColor,
                            modifier = Modifier.fillMaxSize(),
                            state = rState, gesturesEnabled = true, stepAngle = stepAngle, iconSizeDp = iconSize, textSizeSp = textSize, letterSpacingSp = letterSpacing,
                            accentSelectedLabel = true, fadeLabelsOnScroll = false, dimmed = leftDiscState?.isScrolling == true,
                            forceSlideOut = discExiting,
                            contentKey = "r-${navPath.joinToString("/")}-${fNode.id}",
                            initialFocus = 0,
                            onFocusChange = { },
                            onEntryClick = { idx ->
                                val child = fNode.children[idx]
                                val act = child.item?.onAction
                                if (act != null) {
                                    act.invoke()
                                } else {
                                    flightReverse = false
                                    flightLabel = child.label
                                    settingsScope.launch {
                                        flightAnim.snapTo(0f)
                                        flightAnim.animateTo(1f, animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing))
                                        flightLabel = null
                                    }
                                    navigateAnimated {
                                        navPath = navPath + fNode.id
                                        focusId = child.id
                                    }
                                }
                            }
                        )
                    }
                } else if (currentItem != null && currentItem.slider != null) {
                    key("disc-value-${currentItem.id}") {
                        DiscValueWheel(
                            title = currentItem.label,
                            initialValue = currentItem.slider.value,
                            range = currentItem.slider.range,
                            steps = currentItem.slider.steps,
                            format = currentItem.slider.format,
                            accentColor = currentAccentColor,
                            modifier = Modifier.fillMaxSize(),
                            onSave = { v -> currentItem.slider.onSave(v) }
                        )
                    }
                } else if (currentItem != null) {
key("disc-choices-${currentItem.id}") {
                    val choiceState = rememberDiscCarouselState(currentItem.choices.indexOfFirst { it.checked }.coerceAtLeast(0), stepAngle)
                    LaunchedEffect(choiceState) { rightDiscState = choiceState }
                    DiscWheel(
                        entries = currentItem.choices.map { choice ->
                            DiscEntry(
                                id = choice.id, label = choice.label, checked = choice.checked,
                                icon = {
                                    val cHex = choice.colorHex
                                    val cFont = choice.fontSample
                                    val cIllo = choice.illo
                                    when {
                                        choice.id == "on" || choice.id == "off" -> DiscMiniSwitch(choice.id == "on", currentAccentColor)
                                        cHex != null -> Box(
                                            modifier = Modifier.size(30.dp).clip(CircleShape).background(parseHexColor(cHex))
                                        )
                                        cFont != null -> Text("あ", fontFamily = cFont, color = Color.White, fontSize = 19.sp)
                                        cIllo != null -> Box(modifier = Modifier.graphicsLayer { scaleX = 0.78f; scaleY = 0.78f }) {
                                            ChoiceIllustration(cIllo, currentAccentColor, choice.checked)
                                        }
                                        else -> DiscGlyph(currentItem.glyph, currentAccentColor)
                                    }
                                }
                            )
                        },
                        side = DiscSide.RIGHT,
                        accentColor = currentAccentColor,
                        modifier = Modifier.fillMaxSize(),
                        state = choiceState, gesturesEnabled = true, stepAngle = stepAngle, iconSizeDp = iconSize, textSizeSp = textSize, letterSpacingSp = letterSpacing, accentSelectedLabel = true, fadeLabelsOnScroll = false, tapToApply = true, dimmed = leftDiscState?.isScrolling == true, contentKey = "c-${currentItem.id}", forceSlideOut = discExiting,
                        initialFocus = currentItem.choices.indexOfFirst { it.checked }.coerceAtLeast(0),
                        onFocusChange = { },
                        onEntryClick = { idx -> currentItem.choices[idx].onSelect() },
                    )
                }
                }
            }

        // --- タッチ操作パネル（左円駆動＋タップ判定＋ホールド信号。円盤レイヤーの上） ---
        val panelRadiusPx = with(LocalDensity.current) { 165.dp.toPx() }
        val discCenterXPx = with(LocalDensity.current) { (-80).dp.toPx() }
        val screenHDp = LocalConfiguration.current.screenHeightDp.toFloat()
        val discCenterYPx = with(LocalDensity.current) { (screenHDp / 2f - statusTopDp.value - 104f).dp.toPx() }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusTopDp + 104.dp, bottom = 72.dp)
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                DiscTouchHalf(
                    state = leftDiscState,
                    isRight = false,
                    radiusPx = panelRadiusPx,
                    isReverseScroll = { isReverseScroll },
                    modifier = Modifier.fillMaxSize(),
                    onHoldingChanged = { leftHeld = it },
                    discCenterXPx = discCenterXPx,
                    discCenterYPx = discCenterYPx,
                    onItemTap = leftEntryClick
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight())
        }

        // --- 検索（文字だけ＋検索アイコン） ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusTopDp + 10.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Row(
                modifier = Modifier
                    .width(320.dp)
                    .height(48.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WhiteSearchIcon(size = 18.dp, color = Color.White.copy(alpha = 0.85f))
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        focusId = null
                    },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    ),
                    cursorBrush = SolidColor(currentAccentColor),
                    modifier = Modifier.weight(1f)
                ) { innerTextField ->
                    Box(contentAlignment = Alignment.Center) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "設定を検索",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        innerTextField()
                    }
                }
                if (searchQuery.isNotEmpty()) {
                    Text(
                        text = "✕",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickable {
                                searchQuery = ""
                                focusId = null
                            }
                            .padding(start = 8.dp)
                    )
                }
            }
        }

        // --- チップ（タイトル＋戻る / 検索結果のクリア） ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusTopDp + 106.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            if (searchQuery.isNotBlank()) {
                DiscInfoChip(
                    label = "「${searchQuery}」の結果 ${searchResults.size}件",
                    accentColor = currentAccentColor,
                    onClear = {
                        searchQuery = ""
                        focusId = null
                    }
                )
            } else if (navPath.isNotEmpty()) {
                DiscInfoChip(
                    label = "‹ ${ownerNode?.label ?: "設定"}",
                    accentColor = currentAccentColor,
                    onClick = { startBackFlight(); navigateAnimated { goBack() } }
                )
            }
        }

        // --- 階層遷移のラベル飛行（右円の選択ラベル → 左円の縦ラベル） ---
        flightLabel?.let { label ->
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val p = flightAnim.value
                val measurer = rememberTextMeasurer()
                val textW = remember(label) {
                    measurer.measure(label, TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold), softWrap = false).size.width
                }
                val boxWDp = with(LocalDensity.current) { textW.toDp().value }.coerceIn(40f, 200f)
                val rightCenterX = maxWidth.value - 93f - boxWDp / 2f
                val centerX = if (flightReverse) 25f + (rightCenterX - 25f) * p else rightCenterX + (25f - rightCenterX) * p
                val centerY = maxHeight.value / 2f
                Box(
                    modifier = Modifier
                        .offset { IntOffset(((centerX - boxWDp / 2f).dp.roundToPx()), ((centerY - 22f).dp.roundToPx())) }
                        .size(boxWDp.dp, 44.dp)
                        .graphicsLayer {
                            rotationZ = if (flightReverse) -90f * (1f - p) else -90f * p
                            alpha = if (flightReverse) {
                                if (p < 0.85f) 1f else (1f - (p - 0.85f) / 0.15f).coerceIn(0f, 1f)
                            } else if (p < 0.82f) 1f else (1f - (p - 0.82f) / 0.18f).coerceIn(0f, 1f)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = currentAccentColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (showExcludedFoldersDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExcludedFoldersDialog = false },
            title = { androidx.compose.material3.Text("除外フォルダ") },
            text = {
                androidx.compose.foundation.layout.Column {
                    if (excludedFolders.isEmpty()) {
                        androidx.compose.material3.Text("除外フォルダはありません")
                    } else {
                        excludedFolders.forEach { path ->
                            androidx.compose.foundation.layout.Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.Text(
                                    path, fontSize = 12.sp,
                                    modifier = androidx.compose.ui.Modifier.weight(1f)
                                )
                                androidx.compose.material3.TextButton(onClick = {
                                    repo.prefManager.removeExcludedFolder(path)
                                    excludedFolders = repo.prefManager.getExcludedFolders().sorted()
                                    onAppsUpdated()
                                }) { androidx.compose.material3.Text("削除") }
                            }
                        }
                    }
                    androidx.compose.material3.TextButton(onClick = { excludedFolderPickerLauncher.launch(null) }) {
                        androidx.compose.material3.Text("フォルダを追加")
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showExcludedFoldersDialog = false }) {
                    androidx.compose.material3.Text("閉じる")
                }
            }
        )
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

fun treeUriToPath(uri: android.net.Uri): String? {
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
