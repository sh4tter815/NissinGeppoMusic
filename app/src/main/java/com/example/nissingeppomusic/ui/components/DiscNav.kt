package com.example.nissingeppomusic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.material3.LocalTextStyle
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import com.example.nissingeppomusic.data.AppInfo
import kotlin.math.abs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// 2つの円で階層を移動する共通コンポーネント（フォルダページと同じ骨格）。
// 左の円・右の円どちらも「縁に項目が並び、ドラッグで回って、真ん中の高さに来た項目がフォーカス」。
// フォーカス中の項目をタップすると決定（onEntryClick）、それ以外はタップでその項目まで回る。

data class DiscEntry(
    val id: String,
    val label: String,
    val checked: Boolean = false,
    val valueText: String? = null,
    val immediate: Boolean = false,
    val toggleState: Boolean? = null,
    val icon: @Composable () -> Unit = {}
)

// トグル項目用の小さいスイッチ表示（オン/オフの文字の代わり）
@Composable
fun DiscMiniSwitch(on: Boolean, accentColor: Color) {
    Box(
        modifier = Modifier
            .width(30.dp)
            .height(17.dp)
            .background(if (on) accentColor else Color.White.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(13.dp)
                .align(if (on) Alignment.CenterEnd else Alignment.CenterStart)
                .background(Color.White, CircleShape)
        )
    }
}

enum class DiscSide { LEFT, RIGHT }


// 長いラベル用のティッカー表示: 収まりきらない時だけ、少し待ってからゆっくり流して繰り返す
// 幅は TextMeasurer で測り、移動はレイアウトオフセット(整数px)で行う(レイヤー経由だと文字がにじむため)
@Composable
fun MarqueeText(
    text: String,
    color: Color,
    fontWeight: FontWeight?,
    alignEnd: Boolean,
    modifier: Modifier = Modifier,
    fontSizeSp: Float = 15f,
    letterSpacingSp: Float = 0f
) {
    val density = LocalDensity.current
    val viewportPx = with(density) { 128.dp.toPx() }
    val textMeasurer = rememberTextMeasurer()
    val style = LocalTextStyle.current.copy(color = color, fontSize = fontSizeSp.sp, fontWeight = fontWeight, letterSpacing = letterSpacingSp.sp)
    val textWidthPx = remember(text, style) {
        textMeasurer.measure(text, style, softWrap = false).size.width.toFloat()
    }
    val overflow = textWidthPx > viewportPx + 1f
    val offset = remember { Animatable(0f) }
    LaunchedEffect(text, overflow) {
        if (overflow) {
            val distance = textWidthPx - viewportPx
            val duration = (distance * 25).toInt().coerceIn(700, 4500)
            while (true) {
                offset.snapTo(0f)
                delay(1100)
                offset.animateTo(-distance, tween(durationMillis = duration, easing = LinearEasing))
                delay(1100)
            }
        } else {
            offset.snapTo(0f)
        }
    }
    Box(
        modifier = modifier.width(128.dp).clipToBounds(),
        contentAlignment = if (overflow || !alignEnd) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Text(
            text = text,
            color = color,
            fontSize = fontSizeSp.sp,
            letterSpacing = letterSpacingSp.sp,
            fontWeight = fontWeight,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .width(with(density) { textWidthPx.toDp() })
                .offset { IntOffset(offset.value.roundToInt(), 0) }
        )
    }
}

@Composable
fun DiscWheel(
    entries: List<DiscEntry>,
    side: DiscSide,
    accentColor: Color,
    modifier: Modifier = Modifier,
    initialFocus: Int = 0,
    onFocusChange: (Int) -> Unit = {},
    onEntryClick: (Int) -> Unit = {},
    centerContent: (@Composable BoxScope.() -> Unit)? = null,
    state: DiscCarouselState,
    gesturesEnabled: Boolean = true,
    iconSizeDp: Float = 25f,
    textSizeSp: Float = 15f,
    letterSpacingSp: Float = 0f,
    contentKey: Any? = null,
    forceSlideOut: Boolean = false,
    holdExpand: Boolean = false,
    holdActive: Boolean? = null,
    focusedLabelOnly: Boolean = false,
    fadeLabelsOnScroll: Boolean = true,
    accentSelectedLabel: Boolean = false,
    tapToApply: Boolean = false,
    dimmed: Boolean = false,
    showValues: Boolean = true,
    verticalRestLabel: Boolean = false,
    stepAngle: Float = 20f,
    isLoopable: Boolean = false,
    discCenterXDp: Float? = null,
    discCenterYDp: Float? = null,
    dotMarkers: Boolean = false,
    restLabelText: String? = null
) {
    if (entries.isEmpty()) return
    var focusIdx by remember { mutableStateOf(initialFocus) }
    val dimFactor by animateFloatAsState(
        targetValue = if (dimmed) 0.2f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "DiscWheelDim"
    )
    // 中身だけを設定用に入れ替えて、円盤のエンジン自体はアプリ円盤と共通の DiscCarousel を使う
    BoxWithConstraints(modifier = modifier) {
        val centerX = discCenterXDp ?: if (side == DiscSide.LEFT) -80f else maxWidth.value + 80f
        val centerY = discCenterYDp ?: (maxHeight.value / 2f)
        val carouselState = state
        val restP by animateFloatAsState(
            targetValue = if (verticalRestLabel && side == DiscSide.LEFT && !(holdActive == true || carouselState.isScrolling)) 1f else 0f,
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            label = "DiscWheelRest"
        )
        DiscCarousel(
            itemCount = entries.size,
            discCenterXDp = centerX,
            discCenterYDp = centerY,
            discRadiusDp = 165f,
            stepAngle = stepAngle,
            isRightDisc = side == DiscSide.RIGHT,
            isLoopable = isLoopable,
            gesturesEnabled = gesturesEnabled,
        contentKey = contentKey,
        forceSlideOut = forceSlideOut,
        holdExpand = holdExpand,
        holdActive = holdActive,
        fadeLabelsOnScroll = fadeLabelsOnScroll,
            state = carouselState,
            onSelectedIndexChange = { idx -> focusIdx = idx; onFocusChange(idx) },
            centerContent = centerContent,
            modifier = Modifier.fillMaxSize()
        ) { index, rad, labelAlpha, isSelected ->
            val entry = entries[index]
            if (dotMarkers) {
                // 索引モード: ●は円の内側に常駐。スクロール中だけ行頭の文字が円の外にずらっと出る
                val dotX = centerX + 147f * cos(rad)
                val dotY = centerY + 147f * sin(rad)
                Canvas(
                    modifier = Modifier
                        .offset(x = (dotX - 3f).dp, y = (dotY - 3f).dp)
                        .size(6.dp)
                ) {
                    drawCircle(
                        color = if (isSelected) accentColor else Color.White,
                        radius = (if (isSelected) 2.6f else 1.6f).dp.toPx(),
                        alpha = labelAlpha * (if (isSelected) 1f else 0.55f)
                    )
                }
                if (carouselState.isScrolling) {
                    val ax = centerX + 173f * cos(rad)
                    val ay = centerY + 173f * sin(rad)
                    Box(
                        modifier = Modifier
                            .offset(x = (if (side == DiscSide.LEFT) ax else ax - 240f).dp, y = (ay - 22f).dp)
                            .width(240.dp)
                            .height(44.dp),
                        contentAlignment = if (side == DiscSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd
                    ) {
                        Text(
                            entry.label,
                            color = if (isSelected) accentColor else Color.White.copy(alpha = 0.9f),
                            fontSize = textSizeSp.sp,
                            letterSpacing = letterSpacingSp.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.graphicsLayer { this.alpha = labelAlpha }
                        )
                    }
                }
            } else {
            // アイコン: アプリ円盤と同じく輪郭の内側（半径-18）
            val iconX = centerX + 147f * cos(rad)
            val iconY = centerY + 147f * sin(rad)
            val iconSize = iconSizeDp + 14f
            Box(
                modifier = Modifier
                    .offset(x = (iconX - iconSize / 2f).dp, y = (iconY - iconSize / 2f).dp)
                    .size(iconSize.dp)
                    .clickable {
                        when {
                                isSelected || entry.immediate -> onEntryClick(index)
                                tapToApply -> { carouselState.animateToIndex(index); onEntryClick(index) }
                                else -> carouselState.animateToIndex(index)
                            }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.graphicsLayer { val sc = (if (isSelected) 1.12f else 1f) * (iconSizeDp / 36f) * 1.0f; scaleX = sc; scaleY = sc }) {
                    entry.icon()
                }
            }
            // ラベル: アプリ円盤と同じく輪郭の外側（半径+8）を起点に置く
            val ax = centerX + 173f * cos(rad)
            val ay = centerY + 173f * sin(rad)
            Box(
                modifier = Modifier
                    .offset(x = (if (side == DiscSide.LEFT) ax else ax - 240f).dp, y = (ay - 22f).dp)
                    .width(240.dp)
                    .height(44.dp)
                    .graphicsLayer { this.alpha = (if (focusedLabelOnly && !isSelected && !carouselState.isScrolling) 0f else labelAlpha) * dimFactor * (if (verticalRestLabel && side == DiscSide.LEFT && isSelected) (1f - restP) else 1f) },
                contentAlignment = if (side == DiscSide.LEFT) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Row(
                    modifier = Modifier.clickable {
                        when {
                                isSelected || entry.immediate -> onEntryClick(index)
                                tapToApply -> { carouselState.animateToIndex(index); onEntryClick(index) }
                                else -> carouselState.animateToIndex(index)
                            }
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (side == DiscSide.LEFT) Arrangement.Start else Arrangement.End
                ) {
                    if (isSelected) {
                        MarqueeText(
                            text = entry.label + if (entry.checked) "  ✓" else "",
                            color = if (entry.checked || ((focusedLabelOnly || accentSelectedLabel) && isSelected)) accentColor else Color.White.copy(alpha = 0.9f),
                            fontWeight = if (isSelected || entry.checked) FontWeight.Bold else FontWeight.Medium,
                            alignEnd = side == DiscSide.RIGHT,
                            fontSizeSp = textSizeSp,
                            letterSpacingSp = letterSpacingSp
                        )
                    } else {
                        Text(
                            text = entry.label + if (entry.checked) "  ✓" else "",
                            color = if (entry.checked || ((focusedLabelOnly || accentSelectedLabel) && isSelected)) accentColor else Color.White.copy(alpha = 0.9f),
                            fontSize = textSizeSp.sp,
                            letterSpacing = letterSpacingSp.sp,
                            fontWeight = if (isSelected || entry.checked) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 150.dp)
                        )
                    }
                    if ((side == DiscSide.RIGHT || showValues) && entry.toggleState != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        DiscMiniSwitch(entry.toggleState, accentColor)
                    } else if ((side == DiscSide.RIGHT || showValues) && entry.valueText != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(entry.valueText, color = accentColor.copy(alpha = 0.75f), fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
            }
        }
        // 左円の静止時ラベル: 指を離すと円の中へ横倒し(縦)になって移動する
        if (verticalRestLabel && side == DiscSide.LEFT && restP > 0.01f) {
            val focusEntry = entries.getOrNull(focusIdx)
            if (focusEntry != null) {
                Box(
                    modifier = Modifier
                        .offset(x = (centerX + 165f - 60f - 100f).dp, y = (centerY - 22f).dp)
                        .width(200.dp)
                        .height(44.dp)
                        .graphicsLayer {
                            alpha = restP
                            rotationZ = -90f * restP
                            translationX = (1f - restP) * 40.dp.toPx()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        restLabelText ?: focusEntry.label,
                        color = accentColor,
                        fontSize = textSizeSp.sp,
                        letterSpacing = letterSpacingSp.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// 共通チップ（音楽アプリの絞り込みチップと同じ形）: ラベル＋任意で✕。タイトル表示と戻る/クリアの役割を持たせる
@Composable
fun DiscInfoChip(
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .background(accentColor, RoundedCornerShape(999.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        if (onClear != null) {
            Text(
                text = "✕",
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.clickable { onClear() }
            )
        }
    }
}

// 値の円盤（音楽アプリのシーク円盤の移植）: 右の円を回して、左端に来た値がそのまま設定値になる
@Composable
fun DiscValueWheel(
    title: String,
    initialValue: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    format: (Float) -> String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onSave: (Float) -> Unit
) {
    val stepSize = (range.endInclusive - range.start) / (steps + 1)
    val maxIndex = steps + 1
    fun valueAt(index: Int): Float = (range.start + index * stepSize).coerceIn(range.start, range.endInclusive)
    fun indexOf(v: Float): Int = Math.round((v - range.start) / stepSize).coerceIn(0, maxIndex)
    val textMeasurer = rememberTextMeasurer()
    val view = LocalView.current
    val uiFontFamily = LocalTextStyle.current.fontFamily
    val scope = rememberCoroutineScope()
    val indexAnim = remember { Animatable(indexOf(initialValue).toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    var lastHapticIndex by remember { mutableStateOf(indexOf(initialValue)) }

    // 現在位置の丸ぽちの鼓動（シーク円盤と同じ）
    val infiniteTransition = rememberInfiniteTransition(label = "ValueDotPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ValuePulseScale"
    )

    BoxWithConstraints(modifier = modifier) {
        val wDp = maxWidth.value
        val hDp = maxHeight.value
        val rDp = 165f
        val centerX = wDp + 80f
        val centerY = hDp / 2f
        val displayIndex = indexAnim.value
        val currentIndex = Math.round(displayIndex).coerceIn(0, maxIndex)
        val totalSpanDegrees = 300f
        val anglePerStep = totalSpanDegrees / maxIndex
        val rotationDegrees = 180f - displayIndex * anglePerStep

        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(centerX.dp.toPx(), centerY.dp.toPx())
            val r = rDp.dp.toPx()
            // 外周リング
            drawCircle(color = Color.White.copy(alpha = 0.85f), radius = r, center = c, style = Stroke(width = 1.5f))
            // 値の弧（始端→現在値）
            drawArc(
                color = accentColor,
                startAngle = rotationDegrees,
                sweepAngle = displayIndex * anglePerStep,
                useCenter = false,
                topLeft = Offset(c.x - r + 8.dp.toPx(), c.y - r + 8.dp.toPx()),
                size = Size((r - 8.dp.toPx()) * 2f, (r - 8.dp.toPx()) * 2f),
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )
            // 目盛りと数値ラベル（ステップ位置）
            for (k in 0..maxIndex) {
                val ang = Math.toRadians((rotationDegrees + k * anglePerStep).toDouble())
                val cosA = cos(ang).toFloat()
                val sinA = sin(ang).toFloat()
                drawLine(
                    color = Color.White.copy(alpha = 0.55f),
                    start = Offset(c.x + (r - 18.dp.toPx()) * cosA, c.y + (r - 18.dp.toPx()) * sinA),
                    end = Offset(c.x + r * cosA, c.y + r * sinA),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round
                )
                val layout = textMeasurer.measure(
                    format(valueAt(k)),
                    TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, fontFamily = uiFontFamily)
                )
                val lr = r + 14.dp.toPx()
                val pos = Offset(c.x + lr * cosA, c.y + lr * sinA)
                drawText(layout, topLeft = Offset(pos.x - layout.size.width / 2f, pos.y - layout.size.height / 2f))
                // 小目盛り（ステップ間を4分割）
                if (k < maxIndex) {
                    for (s in 1..3) {
                        val a2 = Math.toRadians((rotationDegrees + (k + s / 4f) * anglePerStep).toDouble())
                        val c2 = cos(a2).toFloat()
                        val s2 = sin(a2).toFloat()
                        drawLine(
                            color = Color.White.copy(alpha = 0.28f),
                            start = Offset(c.x + (r - 9.dp.toPx()) * c2, c.y + (r - 9.dp.toPx()) * s2),
                            end = Offset(c.x + r * c2, c.y + r * s2),
                            strokeWidth = 0.8f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
            // 左端（現在値の位置）の丸ぽち
            val dotC = Offset(c.x - r, c.y)
            drawCircle(color = Color.White.copy(alpha = 0.35f), radius = 12.dp.toPx() * pulseScale, center = dotC)
            drawCircle(color = Color.White, radius = 4.5.dp.toPx(), center = dotC)
            // 円の中: 現在値と項目名（画面右端から38dp内側、シーク円盤と同じ位置）
            val textX = size.width - 38.dp.toPx()
            val valueLayout = textMeasurer.measure(
                format(valueAt(currentIndex)),
                TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = uiFontFamily)
            )
            drawText(valueLayout, topLeft = Offset(textX - valueLayout.size.width / 2f, c.y - 26.dp.toPx()))
            drawLine(
                color = Color.White.copy(alpha = 0.8f),
                start = Offset(textX - 17.dp.toPx(), c.y - 2.dp.toPx()),
                end = Offset(textX + 17.dp.toPx(), c.y - 2.dp.toPx()),
                strokeWidth = 1.2.dp.toPx()
            )
            val titleLayout = textMeasurer.measure(
                title,
                TextStyle(color = Color.Gray, fontSize = 11.sp, fontFamily = uiFontFamily)
            )
            drawText(titleLayout, topLeft = Offset(textX - titleLayout.size.width / 2f, c.y + 6.dp.toPx()))
        }

        // 右半分のドラッグで回す（シーク円盤と同じ操作）
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .align(Alignment.CenterEnd)
                .pointerInput(maxIndex) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var moved = false
                            var lastY = down.position.y
                            drag(down.id) { change ->
                                val deltaY = change.position.y - lastY
                                if (kotlin.math.abs(deltaY) > 2f) {
                                    moved = true
                                    isDragging = true
                                }
                                lastY = change.position.y
                                if (moved) {
                                    val deltaIndex = deltaY / 280.dp.toPx() * maxIndex
                                    val next = (indexAnim.value + deltaIndex).coerceIn(0f, maxIndex.toFloat())
                                    scope.launch { indexAnim.snapTo(next) }
                                    val idx = Math.round(next)
                                    if (idx != lastHapticIndex) {
                                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                        lastHapticIndex = idx
                                    }
                                }
                                change.consume()
                            }
                            if (moved) {
                                isDragging = false
                                val target = Math.round(indexAnim.value).coerceIn(0, maxIndex)
                                scope.launch {
                                    indexAnim.animateTo(target.toFloat(), tween(durationMillis = 150, easing = FastOutSlowInEasing))
                                    onSave(valueAt(target))
                                }
                            }
                        }
                    }
                }
        )
    }
}

// 一部のアイコンは Lucide Icons (https://lucide.dev) のパスデータを使用している。
// Copyright (c) Lucide Icons and Contributors — ISC License:
// Permission to use, copy, modify, and/or distribute this software for any purpose
// with or without fee is hereby granted, provided that the above copyright notice
// and this permission notice appear in all copies.
fun lucidePath(d: String, scale: Float): Path {
    val p = PathParser().parsePathString(d).toPath()
    val m = Matrix()
    m.scale(scale, scale, 1f)
    p.transform(m)
    return p
}

@Composable
fun DiscGlyph(kind: String, accentColor: Color) {
    Canvas(modifier = Modifier.size(36.dp)) {
        val w = size.width
        val h = size.height
        val ink = Color.White.copy(alpha = 0.9f)
        val thin = Stroke(width = w * 0.055f)
        when (kind) {
            "toggle" -> {
                // Lucide "toggle-left"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawCircle(color = ink, radius = 3f * sc, center = Offset(9f * sc, 12f * sc), style = ls)
                drawRoundRect(color = ink, topLeft = Offset(2f * sc, 5f * sc), size = Size(20f * sc, 14f * sc), cornerRadius = CornerRadius(7f * sc, 7f * sc), style = ls)
            }
            "slider" -> {
                drawLine(color = ink, start = Offset(w * 0.12f, h * 0.50f), end = Offset(w * 0.88f, h * 0.50f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
                drawCircle(color = accentColor, radius = w * 0.12f, center = Offset(w * 0.58f, h * 0.50f))
                drawCircle(color = Color(0xFF101728), radius = w * 0.05f, center = Offset(w * 0.58f, h * 0.50f))
            }
            "font" -> {
                drawLine(color = ink, start = Offset(w * 0.24f, h * 0.80f), end = Offset(w * 0.50f, h * 0.20f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
                drawLine(color = ink, start = Offset(w * 0.50f, h * 0.20f), end = Offset(w * 0.76f, h * 0.80f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
                drawLine(color = accentColor, start = Offset(w * 0.35f, h * 0.60f), end = Offset(w * 0.65f, h * 0.60f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
            }
            "back" -> {
                // Lucide "arrow-left"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("m12 19-7-7 7-7", sc), color = ink, style = ls)
                drawPath(lucidePath("M19 12H5", sc), color = ink, style = ls)
            }
            "palette" -> {
                // Lucide "palette"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("M12 22a1 1 0 0 1 0-20 10 9 0 0 1 10 9 5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1.75 1.75 0 0 1-1.4 2.8z", sc), color = ink, style = ls)
                drawCircle(color = ink, radius = 0.5f * sc, center = Offset(13.5f * sc, 6.5f * sc), style = ls)
                drawCircle(color = ink, radius = 0.5f * sc, center = Offset(17.5f * sc, 10.5f * sc), style = ls)
                drawCircle(color = ink, radius = 0.5f * sc, center = Offset(6.5f * sc, 12.5f * sc), style = ls)
                drawCircle(color = ink, radius = 0.5f * sc, center = Offset(8.5f * sc, 7.5f * sc), style = ls)
            }
            "color" -> {
                val p = Path()
                p.moveTo(w * 0.50f, h * 0.10f)
                p.cubicTo(w * 0.50f, h * 0.10f, w * 0.16f, h * 0.52f, w * 0.16f, h * 0.64f)
                p.cubicTo(w * 0.16f, h * 0.84f, w * 0.32f, h * 0.92f, w * 0.50f, h * 0.92f)
                p.cubicTo(w * 0.68f, h * 0.92f, w * 0.84f, h * 0.84f, w * 0.84f, h * 0.64f)
                p.cubicTo(w * 0.84f, h * 0.52f, w * 0.50f, h * 0.10f, w * 0.50f, h * 0.10f)
                p.close()
                drawPath(path = p, color = accentColor)
            }
            "image" -> {
                drawRoundRect(color = ink, topLeft = Offset(w * 0.12f, h * 0.18f), size = Size(w * 0.76f, h * 0.64f), cornerRadius = CornerRadius(w * 0.06f, w * 0.06f), style = thin)
                drawCircle(color = accentColor, radius = w * 0.08f, center = Offset(w * 0.68f, h * 0.36f))
                val m = Path()
                m.moveTo(w * 0.18f, h * 0.76f)
                m.lineTo(w * 0.44f, h * 0.48f)
                m.lineTo(w * 0.82f, h * 0.76f)
                m.close()
                drawPath(path = m, color = ink.copy(alpha = 0.8f))
            }
            "clock" -> {
                drawCircle(color = ink, radius = w * 0.36f, center = Offset(w * 0.50f, h * 0.50f), style = thin)
                drawLine(color = ink, start = Offset(w * 0.50f, h * 0.50f), end = Offset(w * 0.50f, h * 0.30f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
                drawLine(color = accentColor, start = Offset(w * 0.50f, h * 0.50f), end = Offset(w * 0.66f, h * 0.58f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
            }
            "lock" -> {
                drawArc(color = ink, startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = Offset(w * 0.29f, h * 0.08f), size = Size(w * 0.42f, h * 0.42f), style = thin)
                drawRoundRect(color = accentColor, topLeft = Offset(w * 0.18f, h * 0.34f), size = Size(w * 0.64f, h * 0.52f), cornerRadius = CornerRadius(w * 0.08f, w * 0.08f))
                drawCircle(color = Color(0xFF101728), radius = w * 0.07f, center = Offset(w * 0.50f, h * 0.58f))
            }
            "music" -> {
                drawOval(color = accentColor, topLeft = Offset(w * 0.10f, h * 0.64f), size = Size(w * 0.30f, h * 0.22f))
                drawLine(color = ink, start = Offset(w * 0.40f, h * 0.72f), end = Offset(w * 0.40f, h * 0.16f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
                drawOval(color = ink, topLeft = Offset(w * 0.56f, h * 0.58f), size = Size(w * 0.28f, h * 0.20f))
                drawLine(color = ink, start = Offset(w * 0.84f, h * 0.66f), end = Offset(w * 0.84f, h * 0.10f), strokeWidth = w * 0.05f, cap = StrokeCap.Round)
                drawLine(color = ink, start = Offset(w * 0.40f, h * 0.16f), end = Offset(w * 0.84f, h * 0.10f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
            }
            "disc" -> {
                // Lucide "disc"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawCircle(color = ink, radius = 10f * sc, center = Offset(12f * sc, 12f * sc), style = ls)
                drawCircle(color = ink, radius = 2f * sc, center = Offset(12f * sc, 12f * sc), style = ls)
            }
            "update" -> {
                drawArc(color = ink, startAngle = -60f, sweepAngle = 260f, useCenter = false, topLeft = Offset(w * 0.14f, h * 0.14f), size = Size(w * 0.72f, h * 0.72f), style = Stroke(width = w * 0.06f, cap = StrokeCap.Round))
                val tri = Path()
                tri.moveTo(w * 0.86f, h * 0.10f)
                tri.lineTo(w * 0.86f, h * 0.34f)
                tri.lineTo(w * 0.64f, h * 0.22f)
                tri.close()
                drawPath(path = tri, color = accentColor)
            }
            "guide" -> {
                drawCircle(color = ink, radius = w * 0.36f, center = Offset(w * 0.50f, h * 0.50f), style = thin)
                val q = Path()
                q.moveTo(w * 0.38f, h * 0.37f)
                q.cubicTo(w * 0.38f, h * 0.23f, w * 0.62f, h * 0.23f, w * 0.62f, h * 0.37f)
                q.cubicTo(w * 0.62f, h * 0.49f, w * 0.50f, h * 0.50f, w * 0.50f, h * 0.61f)
                drawPath(path = q, color = accentColor, style = Stroke(width = w * 0.055f, cap = StrokeCap.Round))
                drawCircle(color = accentColor, radius = w * 0.045f, center = Offset(w * 0.50f, h * 0.73f))
            }
            "apps" -> {
                val cell = w * 0.32f
                val rad = CornerRadius(w * 0.06f, w * 0.06f)
                drawRoundRect(color = accentColor, topLeft = Offset(w * 0.13f, h * 0.13f), size = Size(cell, cell), cornerRadius = rad)
                drawRoundRect(color = ink, topLeft = Offset(w * 0.55f, h * 0.13f), size = Size(cell, cell), cornerRadius = rad)
                drawRoundRect(color = ink, topLeft = Offset(w * 0.13f, h * 0.55f), size = Size(cell, cell), cornerRadius = rad)
                drawRoundRect(color = accentColor, topLeft = Offset(w * 0.55f, h * 0.55f), size = Size(cell, cell), cornerRadius = rad)
            }
            "sparkle" -> {
                val p = Path()
                p.moveTo(w * 0.50f, h * 0.06f)
                p.lineTo(w * 0.60f, h * 0.40f)
                p.lineTo(w * 0.94f, h * 0.50f)
                p.lineTo(w * 0.60f, h * 0.60f)
                p.lineTo(w * 0.50f, h * 0.94f)
                p.lineTo(w * 0.40f, h * 0.60f)
                p.lineTo(w * 0.06f, h * 0.50f)
                p.lineTo(w * 0.40f, h * 0.40f)
                p.close()
                drawPath(path = p, color = accentColor)
            }
            "text" -> {
                drawLine(color = ink, start = Offset(w * 0.14f, h * 0.30f), end = Offset(w * 0.86f, h * 0.30f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
                drawLine(color = accentColor, start = Offset(w * 0.14f, h * 0.52f), end = Offset(w * 0.68f, h * 0.52f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
                drawLine(color = ink, start = Offset(w * 0.14f, h * 0.74f), end = Offset(w * 0.78f, h * 0.74f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
            }
            "swap" -> {
                val lw = w * 0.06f
                drawLine(color = accentColor, start = Offset(w * 0.16f, h * 0.35f), end = Offset(w * 0.76f, h * 0.35f), strokeWidth = lw, cap = StrokeCap.Round)
                drawLine(color = accentColor, start = Offset(w * 0.76f, h * 0.35f), end = Offset(w * 0.60f, h * 0.21f), strokeWidth = lw, cap = StrokeCap.Round)
                drawLine(color = accentColor, start = Offset(w * 0.76f, h * 0.35f), end = Offset(w * 0.60f, h * 0.49f), strokeWidth = lw, cap = StrokeCap.Round)
                drawLine(color = ink, start = Offset(w * 0.84f, h * 0.65f), end = Offset(w * 0.24f, h * 0.65f), strokeWidth = lw, cap = StrokeCap.Round)
                drawLine(color = ink, start = Offset(w * 0.24f, h * 0.65f), end = Offset(w * 0.40f, h * 0.51f), strokeWidth = lw, cap = StrokeCap.Round)
                drawLine(color = ink, start = Offset(w * 0.24f, h * 0.65f), end = Offset(w * 0.40f, h * 0.79f), strokeWidth = lw, cap = StrokeCap.Round)
            }
            "sun" -> {
                // Lucide "sun"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawCircle(color = ink, radius = 4f * sc, center = Offset(w / 2f, h / 2f), style = ls)
                drawPath(lucidePath("M12 2v2", sc), color = ink, style = ls)
                drawPath(lucidePath("M12 20v2", sc), color = ink, style = ls)
                drawPath(lucidePath("m4.93 4.93 1.41 1.41", sc), color = ink, style = ls)
                drawPath(lucidePath("m17.66 17.66 1.41 1.41", sc), color = ink, style = ls)
                drawPath(lucidePath("M2 12h2", sc), color = ink, style = ls)
                drawPath(lucidePath("M20 12h2", sc), color = ink, style = ls)
                drawPath(lucidePath("m6.34 17.66-1.41 1.41", sc), color = ink, style = ls)
                drawPath(lucidePath("m19.07 4.93-1.41 1.41", sc), color = ink, style = ls)
            }
            "trash" -> {
                // Lucide "trash"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("M10 11v6", sc), color = ink, style = ls)
                drawPath(lucidePath("M14 11v6", sc), color = ink, style = ls)
                drawPath(lucidePath("M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6", sc), color = ink, style = ls)
                drawPath(lucidePath("M3 6h18", sc), color = ink, style = ls)
                drawPath(lucidePath("M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2", sc), color = ink, style = ls)
            }
            "cursor" -> {
                // Lucide "mouse-pointer-2"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("M4.037 4.688a.495.495 0 0 1 .651-.651l16 6.5a.5.5 0 0 1-.063.947l-6.124 1.58a2 2 0 0 0-1.438 1.435l-1.579 6.126a.5.5 0 0 1-.947.063z", sc), color = ink, style = ls)
            }
            "eye" -> {
                // Lucide "eye"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0", sc), color = ink, style = ls)
                drawCircle(color = ink, radius = 3f * sc, center = Offset(w / 2f, h / 2f), style = ls)
            }
            "droplets" -> {
                // Lucide "droplets"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("M7 16.3c2.2 0 4-1.83 4-4.05 0-1.16-.57-2.26-1.71-3.19S7.29 6.75 7 5.3c-.29 1.45-1.14 2.84-2.29 3.76S3 11.1 3 12.25c0 2.22 1.8 4.05 4 4.05z", sc), color = ink, style = ls)
                drawPath(lucidePath("M12.56 6.6A10.97 10.97 0 0 0 14 3.02c.5 2.5 2 4.9 4 6.5s3 3.5 3 5.5a6.98 6.98 0 0 1-11.91 4.97", sc), color = ink, style = ls)
            }
            "pipette" -> {
                // Lucide "pipette"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("m12 9-8.414 8.414A2 2 0 0 0 3 18.828v1.344a2 2 0 0 1-.586 1.414A2 2 0 0 1 3.828 21h1.344a2 2 0 0 0 1.414-.586L15 12", sc), color = ink, style = ls)
                drawPath(lucidePath("m18 9 .4.4a1 1 0 1 1-3 3l-3.8-3.8a1 1 0 1 1 3-3l.4.4 3.4-3.4a1 1 0 1 1 3 3z", sc), color = ink, style = ls)
                drawPath(lucidePath("m2 22 .414-.414", sc), color = ink, style = ls)
            }
            "arrow-up-down" -> {
                // Lucide "arrow-up-down"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("m21 16-4 4-4-4", sc), color = ink, style = ls)
                drawPath(lucidePath("M17 20V4", sc), color = ink, style = ls)
                drawPath(lucidePath("m3 8 4-4 4 4", sc), color = ink, style = ls)
                drawPath(lucidePath("M7 4v16", sc), color = ink, style = ls)
            }
            "move-diagonal" -> {
                // Lucide "move-diagonal"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("M11 19H5v-6", sc), color = ink, style = ls)
                drawPath(lucidePath("M13 5h6v6", sc), color = ink, style = ls)
                drawPath(lucidePath("M19 5 5 19", sc), color = ink, style = ls)
            }
            "scaling" -> {
                // Lucide "scaling"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("M12 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7", sc), color = ink, style = ls)
                drawPath(lucidePath("M14 15H9v-5", sc), color = ink, style = ls)
                drawPath(lucidePath("M16 3h5v5", sc), color = ink, style = ls)
                drawPath(lucidePath("M21 3 9 15", sc), color = ink, style = ls)
            }
            "chevrons-up-down" -> {
                // Lucide "chevrons-up-down"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("m7 15 5 5 5-5", sc), color = ink, style = ls)
                drawPath(lucidePath("m7 9 5-5 5 5", sc), color = ink, style = ls)
            }
            "box" -> {
                // Lucide "box"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z", sc), color = ink, style = ls)
                drawPath(lucidePath("m3.3 7 8.7 5 8.7-5", sc), color = ink, style = ls)
                drawPath(lucidePath("M12 22V12", sc), color = ink, style = ls)
            }
            "rotate-3d" -> {
                // Lucide "rotate-3d"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("m15.194 13.707 3.814 1.86-1.86 3.814", sc), color = ink, style = ls)
                drawPath(lucidePath("M16.47214 7.52786 A 5 10 0 1 0 13 21.79796", sc), color = ink, style = ls)
                drawPath(lucidePath("M21.79796 11 A 10 5 0 1 0 19 15.57071", sc), color = ink, style = ls)
            }
            "calendar" -> {
                // Lucide "calendar"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("M8 2v3", sc), color = ink, style = ls)
                drawPath(lucidePath("M16 2v3", sc), color = ink, style = ls)
                drawRoundRect(color = ink, topLeft = Offset(3f * sc, 3f * sc), size = Size(18f * sc, 18f * sc), cornerRadius = CornerRadius(2f * sc, 2f * sc), style = ls)
                drawPath(lucidePath("M3 9h18", sc), color = ink, style = ls)
            }
            "arrow-left-right" -> {
                // Lucide "arrow-left-right"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("M8 3 4 7l4 4", sc), color = ink, style = ls)
                drawPath(lucidePath("M4 7h16", sc), color = ink, style = ls)
                drawPath(lucidePath("m16 21 4-4-4-4", sc), color = ink, style = ls)
                drawPath(lucidePath("M20 17H4", sc), color = ink, style = ls)
            }
            "type" -> {
                // Lucide "type"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("M12 4v16", sc), color = ink, style = ls)
                drawPath(lucidePath("M4 7V5a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v2", sc), color = ink, style = ls)
                drawPath(lucidePath("M9 20h6", sc), color = ink, style = ls)
            }
            "a-large-small" -> {
                // Lucide "a-large-small"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("m15 16 2.536-7.328a1.02 1.02 1 0 1 1.928 0L22 16", sc), color = ink, style = ls)
                drawPath(lucidePath("M15.697 14h5.606", sc), color = ink, style = ls)
                drawPath(lucidePath("m2 16 4.039-9.69a.5.5 0 0 1 .923 0L11 16", sc), color = ink, style = ls)
                drawPath(lucidePath("M3.304 13h6.392", sc), color = ink, style = ls)
            }
            "move-horizontal" -> {
                // Lucide "move-horizontal"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("m18 8 4 4-4 4", sc), color = ink, style = ls)
                drawPath(lucidePath("M2 12h20", sc), color = ink, style = ls)
                drawPath(lucidePath("m6 8-4 4 4 4", sc), color = ink, style = ls)
            }
            "languages" -> {
                // Lucide "languages"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("m5 8 6 6", sc), color = ink, style = ls)
                drawPath(lucidePath("m4 14 6-6 2-3", sc), color = ink, style = ls)
                drawPath(lucidePath("M2 5h12", sc), color = ink, style = ls)
                drawPath(lucidePath("M7 2h1", sc), color = ink, style = ls)
                drawPath(lucidePath("m22 22-5-10-5 10", sc), color = ink, style = ls)
                drawPath(lucidePath("M14 18h6", sc), color = ink, style = ls)
            }
            "case-upper" -> {
                // Lucide "case-upper"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("M15 11h4.5a1 1 0 0 1 0 5h-4a.5.5 0 0 1-.5-.5v-9a.5.5 0 0 1 .5-.5h3a1 1 0 0 1 0 5", sc), color = ink, style = ls)
                drawPath(lucidePath("m2 16 4.039-9.69a.5.5 0 0 1 .923 0L11 16", sc), color = ink, style = ls)
                drawPath(lucidePath("M3.304 13h6.392", sc), color = ink, style = ls)
            }
            "brush" -> {
                // Lucide "brush"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("m11 10 3 3", sc), color = ink, style = ls)
                drawPath(lucidePath("M6.5 21A3.5 3.5 0 1 0 3 17.5a2.62 2.62 0 0 1-.708 1.792A1 1 0 0 0 3 21z", sc), color = ink, style = ls)
                drawPath(lucidePath("M9.969 17.031 21.378 5.624a1 1 0 0 0-3.002-3.002L6.967 14.031", sc), color = ink, style = ls)
            }
            "video" -> {
                // Lucide "video"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("m16 13 5.223 3.482a.5.5 0 0 0 .777-.416V7.87a.5.5 0 0 0-.752-.432L16 10.5", sc), color = ink, style = ls)
                drawRoundRect(color = ink, topLeft = Offset(2f * sc, 6f * sc), size = Size(14f * sc, 12f * sc), cornerRadius = CornerRadius(2f * sc, 2f * sc), style = ls)
            }
            "puzzle" -> {
                // Lucide "puzzle"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("M15.39 4.39a1 1 0 0 0 1.68-.474 2.5 2.5 0 1 1 3.014 3.015 1 1 0 0 0-.474 1.68l1.683 1.682a2.414 2.414 0 0 1 0 3.414L19.61 15.39a1 1 0 0 1-1.68-.474 2.5 2.5 0 1 0-3.014 3.015 1 1 0 0 1 .474 1.68l-1.683 1.682a2.414 2.414 0 0 1-3.414 0L8.61 19.61a1 1 0 0 0-1.68.474 2.5 2.5 0 1 1-3.014-3.015 1 1 0 0 0 .474-1.68l-1.683-1.682a2.414 2.414 0 0 1 0-3.414L4.39 8.61a1 1 0 0 1 1.68.474 2.5 2.5 0 1 0 3.014-3.015 1 1 0 0 1-.474-1.68l1.683-1.682a2.414 2.414 0 0 1 3.414 0z", sc), color = ink, style = ls)
            }
            "sparkles" -> {
                // Lucide "sparkles"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z", sc), color = ink, style = ls)
                drawPath(lucidePath("M20 2v4", sc), color = ink, style = ls)
                drawPath(lucidePath("M22 4h-4", sc), color = ink, style = ls)
                drawCircle(color = ink, radius = 2f * sc, center = Offset(4f * sc, 20f * sc), style = ls)
            }
            "panel-top" -> {
                // Lucide "panel-top"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawRoundRect(color = ink, topLeft = Offset(3f * sc, 3f * sc), size = Size(18f * sc, 18f * sc), cornerRadius = CornerRadius(2f * sc, 2f * sc), style = ls)
                drawPath(lucidePath("M3 9h18", sc), color = ink, style = ls)
            }
            "layout-grid" -> {
                // Lucide "layout-grid"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawRoundRect(color = ink, topLeft = Offset(3f * sc, 3f * sc), size = Size(7f * sc, 7f * sc), cornerRadius = CornerRadius(1f * sc, 1f * sc), style = ls)
                drawRoundRect(color = ink, topLeft = Offset(14f * sc, 3f * sc), size = Size(7f * sc, 7f * sc), cornerRadius = CornerRadius(1f * sc, 1f * sc), style = ls)
                drawRoundRect(color = ink, topLeft = Offset(14f * sc, 14f * sc), size = Size(7f * sc, 7f * sc), cornerRadius = CornerRadius(1f * sc, 1f * sc), style = ls)
                drawRoundRect(color = ink, topLeft = Offset(3f * sc, 14f * sc), size = Size(7f * sc, 7f * sc), cornerRadius = CornerRadius(1f * sc, 1f * sc), style = ls)
            }
            "wand-sparkles" -> {
                // Lucide "wand-sparkles"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("m21.64 3.64-1.28-1.28a1.21 1.21 0 0 0-1.72 0L2.36 18.64a1.21 1.21 0 0 0 0 1.72l1.28 1.28a1.2 1.2 0 0 0 1.72 0L21.64 5.36a1.2 1.2 0 0 0 0-1.72", sc), color = ink, style = ls)
                drawPath(lucidePath("m14 7 3 3", sc), color = ink, style = ls)
                drawPath(lucidePath("M5 6v4", sc), color = ink, style = ls)
                drawPath(lucidePath("M19 14v4", sc), color = ink, style = ls)
                drawPath(lucidePath("M10 2v2", sc), color = ink, style = ls)
                drawPath(lucidePath("M7 8H3", sc), color = ink, style = ls)
                drawPath(lucidePath("M21 16h-4", sc), color = ink, style = ls)
                drawPath(lucidePath("M11 3H9", sc), color = ink, style = ls)
            }
            "database" -> {
                // Lucide "database"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("M3 5V19A9 3 0 0 0 21 19V5", sc), color = ink, style = ls)
                drawPath(lucidePath("M3 12A9 3 0 0 0 21 12", sc), color = ink, style = ls)
            }
            "download" -> {
                // Lucide "download"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("M12 15V3", sc), color = ink, style = ls)
                drawPath(lucidePath("M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", sc), color = ink, style = ls)
                drawPath(lucidePath("m7 10 5 5 5-5", sc), color = ink, style = ls)
            }
            "fingerprint" -> {
                // Lucide "fingerprint-pattern"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("M12 10a2 2 0 0 0-2 2c0 1.02-.1 2.51-.26 4", sc), color = ink, style = ls)
                drawPath(lucidePath("M14 13.12c0 2.38 0 6.38-1 8.88", sc), color = ink, style = ls)
                drawPath(lucidePath("M17.29 21.02c.12-.6.43-2.3.5-3.02", sc), color = ink, style = ls)
                drawPath(lucidePath("M2 12a10 10 0 0 1 18-6", sc), color = ink, style = ls)
                drawPath(lucidePath("M2 16h.01", sc), color = ink, style = ls)
                drawPath(lucidePath("M21.8 16c.2-2 .131-5.354 0-6", sc), color = ink, style = ls)
                drawPath(lucidePath("M5 19.5C5.5 18 6 15 6 12a6 6 0 0 1 .34-2", sc), color = ink, style = ls)
                drawPath(lucidePath("M8.65 22c.21-.66.45-1.32.57-2", sc), color = ink, style = ls)
                drawPath(lucidePath("M9 6.8a6 6 0 0 1 9 5.2v2", sc), color = ink, style = ls)
            }
            "key-round" -> {
                // Lucide "key-round"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("M2.586 17.414A2 2 0 0 0 2 18.828V21a1 1 0 0 0 1 1h3a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h1a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h.172a2 2 0 0 0 1.414-.586l.814-.814a6.5 6.5 0 1 0-4-4z", sc), color = ink, style = ls)
                drawCircle(color = ink, radius = 0.5f * sc, center = Offset(16.5f * sc, 7.5f * sc), style = ls)
            }
            "timer" -> {
                // Lucide "timer"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawLine(color = ink, start = Offset(10f * sc, 2f * sc), end = Offset(14f * sc, 2f * sc), strokeWidth = w * (2f / 24f), cap = StrokeCap.Round)
                drawLine(color = ink, start = Offset(12f * sc, 14f * sc), end = Offset(15f * sc, 11f * sc), strokeWidth = w * (2f / 24f), cap = StrokeCap.Round)
                drawCircle(color = ink, radius = 8f * sc, center = Offset(12f * sc, 14f * sc), style = ls)
            }
            "circle-help" -> {
                // Lucide "circle-question-mark"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawCircle(color = ink, radius = 10f * sc, center = Offset(12f * sc, 12f * sc), style = ls)
                drawPath(lucidePath("M9 9.5a3 2.5 0 016 0c0 2-2 2-3 3.5", sc), color = ink, style = ls)
                drawPath(lucidePath("M12 17h.01", sc), color = ink, style = ls)
            }
            "bold" -> {
                drawContext.canvas.nativeCanvas.apply {
                    val p = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = w * 0.80f
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    drawText("B", w * 0.5f, h * 0.78f, p)
                }
            }
            "wallpaper" -> {
                // Lucide "wallpaper"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("M12 17v4", sc), color = ink, style = ls)
                drawPath(lucidePath("M8 21h8", sc), color = ink, style = ls)
                drawPath(lucidePath("m9 17 6.1-6.1a2 2 0 0 1 2.81.01L22 15", sc), color = ink, style = ls)
                drawCircle(color = ink, radius = 2f * sc, center = Offset(8f * sc, 9f * sc), style = ls)
                drawRoundRect(color = ink, topLeft = Offset(2f * sc, 3f * sc), size = Size(20f * sc, 14f * sc), cornerRadius = CornerRadius(2f * sc, 2f * sc), style = ls)
            }
            "droplet" -> {
                // Lucide "droplet"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("M12 22a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5s-3.5-4-4-6.5c-.5 2.5-2 4.9-4 6.5C6 11.1 5 13 5 15a7 7 0 0 0 7 7z", sc), color = ink, style = ls)
            }
            "case-sensitive" -> {
                // Lucide "case-sensitive"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("m2 16 4.039-9.69a.5.5 0 0 1 .923 0L11 16", sc), color = ink, style = ls)
                drawPath(lucidePath("M22 9v7", sc), color = ink, style = ls)
                drawPath(lucidePath("M3.304 13h6.392", sc), color = ink, style = ls)
                drawCircle(color = ink, radius = 3.5f * sc, center = Offset(18.5f * sc, 12.5f * sc), style = ls)
            }
            "clapperboard" -> {
                // Lucide "clapperboard"
                val ls = Stroke(width = w * (2f / 24f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val sc = w / 24f
                drawPath(lucidePath("m12.296 3.464 3.02 3.956", sc), color = ink, style = ls)
                drawPath(lucidePath("M20.2 6 3 11l-.9-2.4c-.3-1.1.3-2.2 1.3-2.5l13.5-4c1.1-.3 2.2.3 2.5 1.3z", sc), color = ink, style = ls)
                drawPath(lucidePath("M3 11h18v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", sc), color = ink, style = ls)
                drawPath(lucidePath("m6.18 5.276 3.1 3.899", sc), color = ink, style = ls)
            }
            else -> {
                drawCircle(color = accentColor, radius = w * 0.16f, center = Offset(w * 0.50f, h * 0.50f))
            }
        }
    }
}


// ===== アプリ円盤・フォルダ円盤・索引円盤（DiscCarousel のラッパー。旧 RotatableAppDisc / RotatableFolderWheel / RotatableIndexWheel の見た目と操作感を共通部品で再現） =====

/** RotatableAppDisc と同じ DiscItem 列を作る（showIndices の時は頭文字の IndexEntry を差し込む） */
fun buildDiscItems(apps: List<AppInfo>, showIndices: Boolean): List<DiscItem> {
    val items = mutableListOf<DiscItem>()
    if (showIndices) {
        var lastInitial: String? = null
        for (app in apps) {
            val initial = app.discGroup.ifBlank { "＃" }
            if (initial != lastInitial) {
                items.add(DiscItem.IndexEntry(initial))
                lastInitial = initial
            }
            items.add(DiscItem.AppEntry(app))
        }
    } else {
        for (app in apps) items.add(DiscItem.AppEntry(app))
    }
    return items
}

/** アプリ円盤の1項目（右円用）。RotatableAppDisc の RenderDiscItem と同じ描画 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiscAppItem(
    item: DiscItem,
    discCenterXDp: Float,
    discCenterYDp: Float,
    discRadiusDp: Float,
    rad: Float,
    labelAlpha: Float,
    textSizeSp: Float,
    iconSizeDp: Float,
    letterSpacingSp: Float,
    accentColor: Color,
    selectedPackages: Set<String>?,
    iconTilt3d: Boolean,
    rainbowGlow: Boolean = false,
    showLabels: Boolean = true,
    isFocused: Boolean = false,
    onAppClick: (AppInfo) -> Unit,
    onLongClick: (AppInfo) -> Unit
) {
    val effAccent = if (rainbowGlow) dopaRainbowColor(seed = 9) else accentColor
    val outerAppRadiusDp = discRadiusDp + 8f
    val innerIconRadiusDp = discRadiusDp - 18f
    when (item) {
        is DiscItem.IndexEntry -> {
            val appXDp = discCenterXDp + outerAppRadiusDp * cos(rad)
            val appYDp = discCenterYDp + outerAppRadiusDp * sin(rad)
            Box(
                modifier = Modifier
                    .offset(x = (appXDp - 240f).dp, y = (appYDp - 22f).dp)
                    .width(240.dp)
                    .height(44.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = item.char,
                    color = effAccent.copy(alpha = labelAlpha),
                    fontSize = textSizeSp.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = letterSpacingSp.sp,
                    textAlign = TextAlign.End
                )
            }
        }
        is DiscItem.AppEntry -> {
            val app = item.app
            val appXDp = discCenterXDp + outerAppRadiusDp * cos(rad)
            val appYDp = discCenterYDp + outerAppRadiusDp * sin(rad)
            val iconXDp = discCenterXDp + innerIconRadiusDp * cos(rad)
            val iconYDp = discCenterYDp + innerIconRadiusDp * sin(rad)
            val iconBitmap = app.iconBitmap
            if (iconBitmap != null) {
                Box(
                    modifier = Modifier
                        .offset(x = (iconXDp - ((iconSizeDp + 14f) / 2f)).dp, y = (iconYDp - ((iconSizeDp + 14f) / 2f)).dp)
                        .size((iconSizeDp + 14f).dp)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onAppClick(app) },
                            onLongClick = { onLongClick(app) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (iconTilt3d) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = null,
                            modifier = Modifier
                                .size((iconSizeDp * 1.125f).coerceAtLeast(12f).dp)
                                .offset(y = 2.5.dp),
                            colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.38f), BlendMode.SrcIn)
                        )
                    }
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        modifier = Modifier.size((iconSizeDp * 1.125f).coerceAtLeast(12f).dp)
                    )
                }
            }
            val isChecked = selectedPackages?.contains(app.packageName) == true
            val displayText = if (isChecked) "${app.label}  ✓" else app.label
            val textColor = when {
                isChecked -> effAccent
                isFocused -> effAccent.copy(alpha = labelAlpha)
                else -> Color.White.copy(alpha = 0.9f * labelAlpha)
            }
            val textWeight = if (isChecked || isFocused) FontWeight.Bold else FontWeight.Medium
            if (showLabels) Box(
                modifier = Modifier
                    .offset(x = (appXDp - 240f).dp, y = (appYDp - 22f).dp)
                    .width(240.dp)
                    .height(44.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .wrapContentSize()
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onAppClick(app) },
                            onLongClick = { onLongClick(app) }
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = displayText,
                        modifier = Modifier.widthIn(max = 140.dp),
                        color = textColor,
                        fontSize = textSizeSp.sp,
                        fontWeight = textWeight,
                        letterSpacing = letterSpacingSp.sp,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** フォルダ選択ホイール。旧 RotatableFolderWheel と同じ描画。回転は外（DiscTouchHalf）が state 経由で駆動する */


/** フォルダページ右円の項目モデル（子フォルダ・アプリ・新規作成が混ざる） */
sealed class FolderDiscEntry {
    data class Folder(val folderId: String, val name: String) : FolderDiscEntry()
    data class App(val app: AppInfo) : FolderDiscEntry()
    object NewFolder : FolderDiscEntry()
}

/** フォルダページ右円の1項目。DiscAppItem と同じ配置・見た目で、フォルダと新規作成も描ける */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiscFolderItem(
    entry: FolderDiscEntry,
    discCenterXDp: Float,
    discCenterYDp: Float,
    discRadiusDp: Float,
    rad: Float,
    labelAlpha: Float,
    textSizeSp: Float,
    iconSizeDp: Float,
    letterSpacingSp: Float,
    accentColor: Color,
    isFocused: Boolean,
    iconTilt3d: Boolean,
    onFolderClick: (String) -> Unit,
    onAppClick: (AppInfo) -> Unit,
    onAppLongClick: (AppInfo) -> Unit,
    onNewFolderClick: () -> Unit
) {
    val outerAppRadiusDp = discRadiusDp + 8f
    val innerIconRadiusDp = discRadiusDp - 18f
    val appXDp = discCenterXDp + outerAppRadiusDp * cos(rad)
    val appYDp = discCenterYDp + outerAppRadiusDp * sin(rad)
    val iconXDp = discCenterXDp + innerIconRadiusDp * cos(rad)
    val iconYDp = discCenterYDp + innerIconRadiusDp * sin(rad)
    val label = when (entry) {
        is FolderDiscEntry.Folder -> entry.name
        is FolderDiscEntry.App -> entry.app.label
        FolderDiscEntry.NewFolder -> "新規作成"
    }
    val click: () -> Unit = when (entry) {
        is FolderDiscEntry.Folder -> ({ onFolderClick(entry.folderId) })
        is FolderDiscEntry.App -> ({ onAppClick(entry.app) })
        FolderDiscEntry.NewFolder -> ({ onNewFolderClick() })
    }
    val glyphSize = (iconSizeDp * 1.0f).dp
    Box(
        modifier = Modifier
            .offset(x = (iconXDp - ((iconSizeDp + 14f) / 2f)).dp, y = (iconYDp - ((iconSizeDp + 14f) / 2f)).dp)
            .size((iconSizeDp + 14f).dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = click,
                onLongClick = { if (entry is FolderDiscEntry.App) onAppLongClick(entry.app) }
            ),
        contentAlignment = Alignment.Center
    ) {
        when (entry) {
            is FolderDiscEntry.Folder -> WhiteFolderIcon(size = glyphSize, color = Color.White)
            FolderDiscEntry.NewFolder -> WhitePlusIcon(size = glyphSize, color = Color.White)
            is FolderDiscEntry.App -> {
                val iconBitmap = entry.app.iconBitmap
                if (iconBitmap != null) {
                    if (iconTilt3d) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = null,
                            modifier = Modifier
                                .size((iconSizeDp * 1.125f).coerceAtLeast(12f).dp)
                                .offset(y = 2.5.dp),
                            colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.38f), BlendMode.SrcIn)
                        )
                    }
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        modifier = Modifier.size((iconSizeDp * 1.125f).coerceAtLeast(12f).dp)
                    )
                }
            }
        }
    }
    Box(
        modifier = Modifier
            .offset(x = (appXDp - 240f).dp, y = (appYDp - 22f).dp)
            .width(240.dp)
            .height(44.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier
                .wrapContentSize()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = click,
                    onLongClick = { if (entry is FolderDiscEntry.App) onAppLongClick(entry.app) }
                )
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = label,
                modifier = Modifier.widthIn(max = 140.dp),
                color = if (isFocused) accentColor.copy(alpha = labelAlpha) else Color.White.copy(alpha = 0.9f * labelAlpha),
                fontSize = textSizeSp.sp,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                letterSpacing = letterSpacingSp.sp,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
