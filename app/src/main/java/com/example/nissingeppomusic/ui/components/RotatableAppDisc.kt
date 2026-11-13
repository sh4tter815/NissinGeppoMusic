package com.example.nissingeppomusic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nissingeppomusic.data.AppInfo
import kotlinx.coroutines.launch
import kotlin.math.*

sealed class DiscItem {
    data class AppEntry(val app: AppInfo) : DiscItem()
    data class IndexEntry(val char: String) : DiscItem()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RotatableAppDisc(
    apps: List<AppInfo>,
    modifier: Modifier = Modifier,
    discCenterXDp: Float,
    discCenterYDp: Float,
    discRadiusDp: Float = 165f,
    stepAngle: Float = 14f,
    textSizeSp: Float = 16f,
    iconSizeDp: Float = 20f,
    letterSpacingSp: Float = 1.2f,
    accentColor: Color = Color(0xFFE53935),
    selectedIndexChar: String? = null,
    resetKey: Any? = null,
    isRightDisc: Boolean = true,
    showIndices: Boolean = true,
    isLoopable: Boolean = false, // 全アプリ一覧のみ true (循環ループ)。お気に入り・検索・フォルダアプリは false (ループなし)
    selectedPackages: Set<String>? = null,
    onAppClick: (AppInfo) -> Unit,
    onLongClick: (AppInfo) -> Unit,
    onHoldingChanged: (Boolean) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefManager = remember { com.example.nissingeppomusic.data.AppPreferenceManager(context) }
    val isReverseScroll = prefManager.isReverseScroll()

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val baseCenterAngle = if (isRightDisc) 180f else 0f

    val animatableRotation = remember { Animatable(0f) }
    var rawRotationAngle by remember { mutableFloatStateOf(0f) }

    val discItems = remember(apps, showIndices) {
        val items = mutableListOf<DiscItem>()
        if (showIndices) {
            var lastInitial: String? = null
            for (app in apps) {
                val initial = app.initialJapanese.ifBlank { "＃" }
                if (initial != lastInitial) {
                    items.add(DiscItem.IndexEntry(initial))
                    lastInitial = initial
                }
                items.add(DiscItem.AppEntry(app))
            }
        } else {
            for (app in apps) {
                items.add(DiscItem.AppEntry(app))
            }
        }
        items
    }

    // ループなし (isLoopable == false) 時の回転角度制限
    val minRotation = if (discItems.isNotEmpty()) -((discItems.size - 1) * stepAngle).coerceAtLeast(0f) else 0f
    val maxRotation = 0f

    var isScrolling by remember { mutableStateOf(false) }
    // 音楽アプリでは回転中も文字を消さない（ランチャー版はフェード仕様のまま）
    val scrollAlpha by animateFloatAsState(
        targetValue = 1.0f,
        animationSpec = tween(durationMillis = 100),
        label = "ScrollAlpha"
    )

    // モード切替や絞り込み変更時は円盤を先頭に戻す（切替前の回転位置が残って末尾から始まるのを防ぐ）
    LaunchedEffect(resetKey) {
        if (resetKey != null) {
            rawRotationAngle = 0f
            animatableRotation.snapTo(0f)
        }
    }

    LaunchedEffect(selectedIndexChar) {
        if (!selectedIndexChar.isNullOrEmpty() && discItems.isNotEmpty()) {
            val targetIndex = discItems.indexOfFirst { item ->
                when (item) {
                    is DiscItem.IndexEntry -> item.char.equals(selectedIndexChar, ignoreCase = true)
                    is DiscItem.AppEntry -> (item.app.initialJapanese == selectedIndexChar) ||
                            (item.app.initialEnglish.equals(selectedIndexChar, ignoreCase = true))
                }
            }
            if (targetIndex != -1) {
                val rawTarget = -targetIndex * stepAngle
                val targetAngle = if (isLoopable) rawTarget else rawTarget.coerceIn(minRotation, maxRotation)
                coroutineScope.launch {
                    animatableRotation.snapTo(targetAngle)
                    rawRotationAngle = targetAngle
                    isScrolling = false
                }
            }
        }
    }

    // アプリリスト（非表示設定など）が更新された場合、スクロール位置を維持しつつ範囲内に安全クランプ
    LaunchedEffect(discItems.size) {
        if (discItems.isNotEmpty() && !isLoopable) {
            val minRot = -((discItems.size - 1) * stepAngle).coerceAtLeast(0f)
            if (rawRotationAngle < minRot) {
                rawRotationAngle = minRot
                coroutineScope.launch {
                    animatableRotation.snapTo(minRot)
                }
            }
        }
    }

    val currentRotation = animatableRotation.value

    val selectedIndex = remember(currentRotation, discItems.size, isLoopable) {
        if (discItems.isEmpty()) -1
        else {
            val rawIdx = (-currentRotation / stepAngle).roundToInt()
            if (isLoopable) {
                ((rawIdx % discItems.size) + discItems.size) % discItems.size
            } else {
                rawIdx.coerceIn(0, discItems.size - 1)
            }
        }
    }

    var lastHapticIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(selectedIndex) {
        if ((selectedIndex != -1) && (selectedIndex != lastHapticIndex) && (lastHapticIndex != -1)) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        lastHapticIndex = selectedIndex
    }

    val centerPx = with(density) {
        Offset(discCenterXDp.dp.toPx(), discCenterYDp.dp.toPx())
    }
    val radiusPx = with(density) { discRadiusDp.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(discItems.size, discCenterXDp, discCenterYDp, discRadiusDp, isRightDisc, isLoopable) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val pos = down.position

                        val isTouchInTargetHalf = if (isRightDisc) {
                            pos.x >= size.width / 2f
                        } else {
                            pos.x < size.width / 2f
                        }

                        if (!isTouchInTargetHalf) {
                            continue
                        }

                        val distFromCenter = sqrt((pos.x - centerPx.x).pow(2) + (pos.y - centerPx.y).pow(2))
                        val isInsideCircle = distFromCenter <= radiusPx

                        var currentPos = pos
                        var totalDx = 0f
                        var totalDy = 0f
                        var isDragStarted = false
                        var lastDeltaAngle = 0f
                        var lastTime = System.currentTimeMillis()
                        // ステップ式回転用（フォルダ／インデックス円盤のタッチパネルと同じ閾値40px）
                        val stepThresholdPx = 40f
                        var accumulatedDy = 0f
                        var currentStepIndex = (-rawRotationAngle / stepAngle).roundToInt()

                        drag(down.id) { change: PointerInputChange ->
                            val prevPos = currentPos
                            currentPos = change.position
                            val currentTime = System.currentTimeMillis()

                            val dx = currentPos.x - prevPos.x
                            val dy = currentPos.y - prevPos.y
                            totalDx += dx
                            totalDy += dy

                            if (!isDragStarted) {
                                if (isInsideCircle && abs(totalDx) > abs(totalDy) && abs(totalDx) > 10f) {
                                    return@drag
                                }
                                if (abs(totalDy) > 6f || abs(totalDx) > 6f || !isInsideCircle) {
                                    isDragStarted = true
                                    isScrolling = true
                                }
                            }

                            if (isDragStarted) {
                                change.consume()

                                // フォルダ／インデックス円盤と同じステップ式: 指の移動が閾値を超えるたびに
                                // 1項目分だけアニメーションで吸い付くように回る（自由回転はしない）
                                val directionFactor = if (isReverseScroll) -1f else 1f
                                accumulatedDy += dy * directionFactor
                                val stepSign = if (isRightDisc) 1 else -1
                                var stepped = false
                                while (accumulatedDy >= stepThresholdPx) {
                                    accumulatedDy -= stepThresholdPx
                                    currentStepIndex += stepSign
                                    stepped = true
                                }
                                while (accumulatedDy <= -stepThresholdPx) {
                                    accumulatedDy += stepThresholdPx
                                    currentStepIndex -= stepSign
                                    stepped = true
                                }
                                if (stepped) {
                                    if (!isLoopable) {
                                        currentStepIndex = currentStepIndex.coerceIn(0, (discItems.size - 1).coerceAtLeast(0))
                                    }
                                    val targetAngle = if (isLoopable) {
                                        -currentStepIndex * stepAngle
                                    } else {
                                        (-currentStepIndex * stepAngle).coerceIn(minRotation, maxRotation)
                                    }
                                    rawRotationAngle = targetAngle
                                    coroutineScope.launch {
                                        animatableRotation.animateTo(
                                            targetValue = targetAngle,
                                            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                        )
                                    }
                                }
                            }
                        }

                        if (isDragStarted && discItems.isNotEmpty()) {
                            // ステップ式: 離した時点のステップ位置にそのまま収束させる（自由回転の慣性フリングはしない）
                            val finalAngle = rawRotationAngle
                            coroutineScope.launch {
                                animatableRotation.animateTo(
                                    targetValue = finalAngle,
                                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                )
                                isScrolling = false
                            }
                        } else {
                            isScrolling = false
                        }
                    }
                }
            }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(discCenterXDp.dp.toPx(), discCenterYDp.dp.toPx())
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = discRadiusDp.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.5f)
                )
            }

            if (discItems.isEmpty()) return@Box

            val outerAppRadiusDp = discRadiusDp + 8f
            val innerIconRadiusDp = discRadiusDp - 18f
            val totalItems = discItems.size
            val textAlpha = 1.0f

            if (isLoopable) {
                // --- 全アプリ一覧: 無限循環ループ配置 ---
                val minK = floor((-80f - currentRotation) / stepAngle).toInt() - 1
                val maxK = ceil((80f - currentRotation) / stepAngle).toInt() + 1

                for (k in minK..maxK) {
                    val itemAngle = baseCenterAngle + (k * stepAngle) + currentRotation
                    val angleDiffFromCenter = abs(itemAngle - baseCenterAngle)

                    if (angleDiffFromCenter <= 80f) {
                        val itemIndex = ((k % totalItems) + totalItems) % totalItems
                        val discItem = discItems[itemIndex]
                        val rad = itemAngle * (PI.toFloat() / 180f)

                        val edgeFade = (1.0f - (angleDiffFromCenter / 80f).coerceIn(0f, 1f).pow(2)).coerceIn(0f, 1f)
                        val finalAlpha = textAlpha * scrollAlpha * edgeFade

                        RenderDiscItem(
                            discItem = discItem,
                            isRightDisc = isRightDisc,
                            discCenterXDp = discCenterXDp,
                            discCenterYDp = discCenterYDp,
                            outerAppRadiusDp = outerAppRadiusDp,
                            innerIconRadiusDp = innerIconRadiusDp,
                            rad = rad,
                            textAlpha = finalAlpha,
                            textSizeSp = textSizeSp,
                            iconSizeDp = iconSizeDp,
                            letterSpacingSp = letterSpacingSp,
                            accentColor = accentColor,
                            selectedPackages = selectedPackages,
                            onAppClick = onAppClick,
                            onLongClick = onLongClick
                        )
                    }
                }
            } else {
                // --- お気に入り・検索・フォルダアプリ: 1回のみのリスト配置（ダブりなし・端っこバウンス） ---
                for (index in 0 until totalItems) {
                    val itemAngle = baseCenterAngle + (index * stepAngle) + currentRotation
                    val angleDiffFromCenter = abs(itemAngle - baseCenterAngle)

                    if (angleDiffFromCenter <= 80f) {
                        val discItem = discItems[index]
                        val rad = itemAngle * (PI.toFloat() / 180f)

                        val edgeFade = (1.0f - (angleDiffFromCenter / 80f).coerceIn(0f, 1f).pow(2)).coerceIn(0f, 1f)
                        val finalAlpha = textAlpha * scrollAlpha * edgeFade

                        RenderDiscItem(
                            discItem = discItem,
                            isRightDisc = isRightDisc,
                            discCenterXDp = discCenterXDp,
                            discCenterYDp = discCenterYDp,
                            outerAppRadiusDp = outerAppRadiusDp,
                            innerIconRadiusDp = innerIconRadiusDp,
                            rad = rad,
                            textAlpha = finalAlpha,
                            textSizeSp = textSizeSp,
                            iconSizeDp = iconSizeDp,
                            letterSpacingSp = letterSpacingSp,
                            accentColor = accentColor,
                            selectedPackages = selectedPackages,
                            onAppClick = onAppClick,
                            onLongClick = onLongClick
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RenderDiscItem(
    discItem: DiscItem,
    isRightDisc: Boolean,
    discCenterXDp: Float,
    discCenterYDp: Float,
    outerAppRadiusDp: Float,
    innerIconRadiusDp: Float,
    rad: Float,
    textAlpha: Float,
    textSizeSp: Float,
    iconSizeDp: Float,
    letterSpacingSp: Float,
    accentColor: Color,
    selectedPackages: Set<String>?,
    onAppClick: (AppInfo) -> Unit,
    onLongClick: (AppInfo) -> Unit
) {
    when (discItem) {
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
                    text = discItem.char,
                    color = accentColor.copy(alpha = textAlpha),
                    fontSize = textSizeSp.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = letterSpacingSp.sp,
                    textAlign = TextAlign.End
                )
            }
        }
        is DiscItem.AppEntry -> {
            val app = discItem.app
            val appXDp = discCenterXDp + outerAppRadiusDp * cos(rad)
            val appYDp = discCenterYDp + outerAppRadiusDp * sin(rad)
            val iconXDp = discCenterXDp + innerIconRadiusDp * cos(rad)
            val iconYDp = discCenterYDp + innerIconRadiusDp * sin(rad)

            val iconBitmap = app.iconBitmap

            if (iconBitmap != null) {
                Box(
                    modifier = Modifier
                        .offset(x = (iconXDp - (iconSizeDp / 2f)).dp, y = (iconYDp - (iconSizeDp / 2f)).dp)
                        .size(iconSizeDp.dp)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onAppClick(app) },
                            onLongClick = { onLongClick(app) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        modifier = Modifier.size((iconSizeDp - 4f).coerceAtLeast(12f).dp)
                    )
                }
            }

            val isChecked = selectedPackages?.contains(app.packageName) == true
            val displayText = if (isChecked) "${app.label}  ✓" else app.label
            val textColor = if (isChecked) accentColor else Color.White.copy(alpha = 0.9f * textAlpha)
            val textWeight = if (isChecked) FontWeight.Bold else FontWeight.Medium

            if (isRightDisc) {
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
            } else {
                Box(
                    modifier = Modifier
                        .offset(x = appXDp.dp, y = (appYDp - 22f).dp)
                        .width(240.dp)
                        .height(44.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
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
                            color = textColor,
                            fontSize = textSizeSp.sp,
                            fontWeight = textWeight,
                            letterSpacing = letterSpacingSp.sp,
                            textAlign = TextAlign.Start,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
