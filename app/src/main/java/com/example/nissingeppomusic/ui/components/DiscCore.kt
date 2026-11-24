package com.example.nissingeppomusic.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.nissingeppomusic.data.AppPreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * アプリ円盤（RotatableAppDisc）と同エンジンの、中身だけ差し替えられる円盤。
 * 回転・ドラッグ・スナップ・フェード・ハプティクス・輪郭描画はアプリ円盤からそのまま移植した。
 * アプリ円盤本体には一切手を入れていないので、アプリ一覧のデザイン・操作感は変わらない。
 * 項目の中身（アイコンやラベルの描画）は itemContent で呼び出し側が差し替える。
 */
class DiscCarouselState internal constructor(
    private val scope: CoroutineScope,
    initialIndex: Int,
    stepAngle: Float
) {
    val rotationAnim = Animatable(-initialIndex * stepAngle)
    var rawRotation by mutableFloatStateOf(-initialIndex * stepAngle)
    var isScrolling by mutableStateOf(false)
    var itemCount: Int = 0
    var stepAngle: Float = stepAngle

    /** タップなどで指定の項目まで回す（アプリ円盤のドラッグ終了時と同じバネ） */
    fun animateToIndex(index: Int) {
        if (itemCount <= 0) return
        val minRotation = -((itemCount - 1) * stepAngle).coerceAtLeast(0f)
        val target = (-index * stepAngle).coerceIn(minRotation, 0f)
        rawRotation = target
        scope.launch {
            rotationAnim.animateTo(
                targetValue = target,
                animationSpec = spring(stiffness = 220f, dampingRatio = 0.65f)
            )
            isScrolling = false
        }
    }
}

@Composable
fun rememberDiscCarouselState(initialIndex: Int = 0, stepAngle: Float = 20f): DiscCarouselState {
    val scope = rememberCoroutineScope()
    return remember { DiscCarouselState(scope, initialIndex, stepAngle) }
}

@Composable
fun DiscCarousel(
    itemCount: Int,
    modifier: Modifier = Modifier,
    discCenterXDp: Float,
    discCenterYDp: Float,
    discRadiusDp: Float = 165f,
    stepAngle: Float = 20f,
    isRightDisc: Boolean = true,
    isLoopable: Boolean = false,
    gesturesEnabled: Boolean = true,
    contentKey: Any? = null,
    forceSlideOut: Boolean = false,
    holdExpand: Boolean = false,
    holdActive: Boolean? = null,
    fadeLabelsOnScroll: Boolean = true,
    showOutline: Boolean = true,
    onHoldingChanged: ((Boolean) -> Unit)? = null,
    onEmptyTap: (() -> Unit)? = null,
    state: DiscCarouselState = rememberDiscCarouselState(0, stepAngle),
    onSelectedIndexChange: (Int) -> Unit = {},
    centerContent: (@Composable BoxScope.() -> Unit)? = null,
    itemContent: @Composable (index: Int, rad: Float, labelAlpha: Float, isSelected: Boolean) -> Unit
) {
    val context = LocalContext.current
    val prefManager = remember { AppPreferenceManager(context) }
    val isReverseScroll = prefManager.isReverseScroll()
    val discInertia = prefManager.getDiscInertia()
    val hapticsEnabled = prefManager.isHapticsEnabled()

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    state.itemCount = itemCount
    // 項目間隔が変わったら（初期生成時の間隔と違う場合も含む）、
    // 今選ばれている項目を保ったまま回転角を新しい間隔に合わせ直す。
    // これをしないと「回転角÷間隔」で出す選択位置がズレて、押した項目と違う設定が開く。
    LaunchedEffect(stepAngle) {
        if (state.stepAngle != stepAngle) {
            val idx = (-state.rawRotation / state.stepAngle).roundToInt().coerceAtLeast(0)
            state.stepAngle = stepAngle
            state.animateToIndex(idx)
        }
    }

    val baseCenterAngle = if (isRightDisc) 180f else 0f

    val minRotation = if (itemCount > 0) -((itemCount - 1) * stepAngle).coerceAtLeast(0f) else 0f
    val maxRotation = 0f

    val scrollAlpha by animateFloatAsState(
        targetValue = if (state.isScrolling && fadeLabelsOnScroll) 0.0f else 1.0f,
        animationSpec = tween(durationMillis = 100),
        label = "DiscCarouselScrollAlpha"
    )

    // 項目数が減ったら範囲内に安全クランプ（アプリ円盤と同じ）
    LaunchedEffect(itemCount) {
        if (itemCount > 0 && !isLoopable) {
            val minRot = -((itemCount - 1) * stepAngle).coerceAtLeast(0f)
            if (state.rawRotation < minRot) {
                state.rawRotation = minRot
                coroutineScope.launch { state.rotationAnim.snapTo(minRot) }
            }
        }
    }

    val currentRotation = state.rotationAnim.value
    // 内容が切り替わったとき、ページ切り替えと同じ「円が端に引っ込んで戻る」動き
    val slideOut = remember { Animatable(1f) }
    var slidePrevKey by remember { mutableStateOf<Any?>(null) }
    var slideArmed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        slideOut.animateTo(0f, tween(durationMillis = 220, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(forceSlideOut) {
        if (forceSlideOut) {
            slideOut.animateTo(1f, tween(durationMillis = 160, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(contentKey) {
        if (slideArmed && contentKey != slidePrevKey) {
            slideOut.animateTo(1f, tween(durationMillis = 160, easing = FastOutSlowInEasing))
            slideOut.animateTo(0f, tween(durationMillis = 220, easing = FastOutSlowInEasing))
        }
        slidePrevKey = contentKey
        slideArmed = true
    }
    val slideTravelPx = with(density) { (discRadiusDp - 80f).coerceAtLeast(0f).dp.toPx() }

    // 押している間の拡大（フォルダページの左円と同じ 1.30 倍。オプション）
    var internalHold by remember { mutableStateOf(false) }
    LaunchedEffect(internalHold) { onHoldingChanged?.invoke(internalHold) }
    val holdNow = holdActive ?: internalHold
    val holdScaleAnim by animateFloatAsState(
        targetValue = if (holdExpand && holdNow) 1.30f else 1.0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "DiscHoldScale"
    )

    val selectedIndex = remember(currentRotation, itemCount, isLoopable, stepAngle) {
        if (itemCount <= 0) -1
        else {
            val rawIdx = (-currentRotation / stepAngle).roundToInt()
            if (isLoopable) ((rawIdx % itemCount) + itemCount) % itemCount
            else rawIdx.coerceIn(0, itemCount - 1)
        }
    }

    var lastHapticIndex by remember { mutableStateOf(-1) }
    LaunchedEffect(selectedIndex) {
        if (hapticsEnabled && selectedIndex != -1 && selectedIndex != lastHapticIndex && lastHapticIndex != -1) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        lastHapticIndex = selectedIndex
        if (selectedIndex != -1) onSelectedIndexChange(selectedIndex)
    }

    val centerPx = with(density) { Offset(discCenterXDp.dp.toPx(), discCenterYDp.dp.toPx()) }
    val radiusPx = with(density) { discRadiusDp.dp.toPx() }

    val discDragModifier = if (gesturesEnabled) {
        Modifier.pointerInput(itemCount, discCenterXDp, discCenterYDp, discRadiusDp, isRightDisc, isLoopable) {
            awaitPointerEventScope {
                while (true) {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pos = down.position

                    val isTouchInTargetHalf = if (isRightDisc) {
                        pos.x >= size.width / 2f
                    } else {
                        pos.x < size.width / 2f
                    }
                    if (!isTouchInTargetHalf) continue
                    internalHold = true

                    val distFromCenter = sqrt((pos.x - centerPx.x).pow(2) + (pos.y - centerPx.y).pow(2))
                    val isInsideCircle = distFromCenter <= radiusPx

                    var currentPos = pos
                    var totalDx = 0f
                    var totalDy = 0f
                    var isDragStarted = false
                    var lastDeltaAngle = 0f
                    var lastTime = System.currentTimeMillis()

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
                                state.isScrolling = true
                            }
                        }

                        if (isDragStarted) {
                            change.consume()

                            val dyPx = currentPos.y - prevPos.y
                            val directionFactor = if (isReverseScroll) -1f else 1f
                            val deltaAngle = -(dyPx / radiusPx) * (180f / PI.toFloat()) * (if (isRightDisc) 1.2f else -1.2f) * directionFactor

                            val dt = (currentTime - lastTime).coerceAtLeast(1)
                            lastDeltaAngle = deltaAngle / dt * 16f
                            lastTime = currentTime

                            val targetAngle = state.rawRotation + deltaAngle
                            val constrainedAngle = if (isLoopable) {
                                targetAngle
                            } else {
                                targetAngle.coerceIn(minRotation - 20f, maxRotation + 20f)
                            }
                            state.rawRotation = constrainedAngle
                            coroutineScope.launch {
                                state.rotationAnim.snapTo(constrainedAngle)
                            }
                        }
                    }

                    internalHold = false
                    if (isDragStarted && itemCount > 0) {
                        if (isLoopable) {
                            val inertiaTarget = state.rawRotation + lastDeltaAngle * 10f * discInertia
                            val nearestIndex = (-inertiaTarget / stepAngle).roundToInt()
                            val finalSnapAngle = -nearestIndex * stepAngle

                            state.rawRotation = finalSnapAngle
                            coroutineScope.launch {
                                state.rotationAnim.animateTo(
                                    targetValue = finalSnapAngle,
                                    animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
                                )
                                state.isScrolling = false
                            }
                        } else {
                            val inertiaTarget = state.rawRotation + lastDeltaAngle * 6f * discInertia
                            val nearestIndex = (-inertiaTarget / stepAngle).roundToInt()
                            val finalSnapAngle = (-nearestIndex * stepAngle).coerceIn(minRotation, maxRotation)

                            state.rawRotation = finalSnapAngle
                            coroutineScope.launch {
                                state.rotationAnim.animateTo(
                                    targetValue = finalSnapAngle,
                                    animationSpec = spring(stiffness = 220f, dampingRatio = 0.65f)
                                )
                                state.isScrolling = false
                            }
                        }
                    } else {
                        state.isScrolling = false
                    }
                }
            }
        }
    } else Modifier

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(discDragModifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = slideOut.value * slideTravelPx * if (isRightDisc) 1f else -1f
                    alpha = 1f - slideOut.value
                    if (holdExpand) {
                        scaleX = holdScaleAnim
                        scaleY = holdScaleAnim
                        transformOrigin = TransformOrigin(
                            (centerPx.x / size.width).coerceIn(0f, 1f),
                            (centerPx.y / size.height).coerceIn(0f, 1f)
                        )
                    }
                }
        ) {
            if (showOutline) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = discRadiusDp.dp.toPx(),
                    center = Offset(discCenterXDp.dp.toPx(), discCenterYDp.dp.toPx()),
                    style = Stroke(width = 1.5f)
                )
            }
            }

            if (onEmptyTap != null) {
                Box(
                    modifier = Modifier
                        .offset(x = (discCenterXDp - discRadiusDp).dp, y = (discCenterYDp - discRadiusDp).dp)
                        .size((discRadiusDp * 2f).dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onEmptyTap() }
                )
            }

            if (itemCount <= 0) return@Box

            val textAlpha = 1.0f

            if (isLoopable) {
                val minK = floor((-80f - currentRotation) / stepAngle).toInt() - 1
                val maxK = ceil((80f - currentRotation) / stepAngle).toInt() + 1

                for (k in minK..maxK) {
                    val itemAngle = baseCenterAngle + (k * stepAngle) + currentRotation
                    val angleDiffFromCenter = abs(itemAngle - baseCenterAngle)

                    if (angleDiffFromCenter <= 80f) {
                        val itemIndex = ((k % itemCount) + itemCount) % itemCount
                        val rad = itemAngle * (PI.toFloat() / 180f)

                        val edgeFade = (1.0f - (angleDiffFromCenter / 80f).coerceIn(0f, 1f).pow(2)).coerceIn(0f, 1f)
                        val labelAlpha = textAlpha * scrollAlpha * edgeFade

                        itemContent(itemIndex, rad, labelAlpha, itemIndex == selectedIndex)
                    }
                }
            } else {
                for (index in 0 until itemCount) {
                    val itemAngle = baseCenterAngle + (index * stepAngle) + currentRotation
                    val angleDiffFromCenter = abs(itemAngle - baseCenterAngle)

                    if (angleDiffFromCenter <= 80f) {
                        val rad = itemAngle * (PI.toFloat() / 180f)

                        val edgeFade = (1.0f - (angleDiffFromCenter / 80f).coerceIn(0f, 1f).pow(2)).coerceIn(0f, 1f)
                        val labelAlpha = textAlpha * scrollAlpha * edgeFade

                        itemContent(index, rad, labelAlpha, index == selectedIndex)
                    }
                }
            }

            if (centerContent != null) {
                val previewCx = if (isRightDisc) discCenterXDp - 128f else discCenterXDp
                Box(
                    modifier = Modifier
                        .offset(x = (previewCx - 52f).dp, y = (discCenterYDp - 52f).dp)
                        .size(104.dp),
                    contentAlignment = Alignment.Center,
                    content = centerContent
                )
            }
        }
    }
}

/**
 * フォルダページのタッチ操作パネルと同じ方式で、画面の片側半分を受け持って円盤を回す操作レイヤー。
 * ヘッダーと下ダイヤルの帯は呼び出し側のパディングで外してある前提。
 * ドラッグはアプリ円盤と同じ連続式で、離したら DiscCarouselState.animateToIndex でスナップする。
 */
@Composable
fun DiscTouchHalf(
    state: DiscCarouselState?,
    isRight: Boolean,
    radiusPx: Float,
    isReverseScroll: () -> Boolean,
    modifier: Modifier = Modifier,
    onHoldingChanged: ((Boolean) -> Unit)? = null,
    discCenterXPx: Float = 0f,
    discCenterYPx: Float = 0f,
    onItemTap: ((Int) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val tapCb by rememberUpdatedState(onItemTap)
    val holdCb by rememberUpdatedState(onHoldingChanged)
    Box(
        modifier = modifier.pointerInput(state, isRight) {
            awaitPointerEventScope {
                while (true) {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val st = state ?: continue
                    if (st.itemCount <= 0) continue
                    holdCb?.invoke(true)
                    var lastY = down.position.y
                    var dragging = false
                    var maxDist = 0f
                    // フォルダページ方式のステップスクロール: 40px = 1ステップ、180ms の補間移動
                    val stepThreshold = 40f
                    var accumulatedDy = 0f
                    var stepCount = (-st.rawRotation / st.stepAngle).roundToInt()
                        .coerceIn(0, (st.itemCount - 1).coerceAtLeast(0))
                    var upChange: PointerInputChange? = null
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                        if (!change.pressed && change.previousPressed) {
                            upChange = change
                            break
                        }
                        val distFromDown = (change.position - down.position).getDistance()
                        if (distFromDown > maxDist) maxDist = distFromDown
                        if (!dragging && abs(change.position.y - down.position.y) > 6f) {
                            dragging = true
                            st.isScrolling = true
                        }
                        if (dragging) {
                            change.consume()
                            val rawDy = change.position.y - lastY
                            val deltaY = rawDy * (if (isReverseScroll()) -1f else 1f)
                            accumulatedDy += deltaY
                            while (accumulatedDy >= stepThreshold) {
                                accumulatedDy -= stepThreshold
                                val next = (stepCount + 1).coerceAtMost((st.itemCount - 1).coerceAtLeast(0))
                                if (next != stepCount) {
                                    stepCount = next
                                    val target = -stepCount * st.stepAngle
                                    st.rawRotation = target
                                    scope.launch {
                                        st.rotationAnim.animateTo(
                                            targetValue = target,
                                            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                        )
                                    }
                                    if (AppPreferenceManager(view.context).isHapticsEnabled()) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                            }
                            while (accumulatedDy <= -stepThreshold) {
                                accumulatedDy += stepThreshold
                                val next = (stepCount - 1).coerceAtLeast(0)
                                if (next != stepCount) {
                                    stepCount = next
                                    val target = -stepCount * st.stepAngle
                                    st.rawRotation = target
                                    scope.launch {
                                        st.rotationAnim.animateTo(
                                            targetValue = target,
                                            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                                        )
                                    }
                                    if (AppPreferenceManager(view.context).isHapticsEnabled()) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                            }
                        }
                        lastY = change.position.y
                    }
                    holdCb?.invoke(false)
                    val tapSlopPx = radiusPx * (18f / 165f)
                    if (tapCb != null && upChange != null && maxDist <= tapSlopPx) {
                        val dx = down.position.x - discCenterXPx
                        val dyv = down.position.y - discCenterYPx
                        val dist = sqrt(dx * dx + dyv * dyv)
                        if (dist >= radiusPx * 0.55f && dist <= radiusPx * 1.65f) {
                            val tapDeg = Math.toDegrees(atan2(dyv.toDouble(), dx.toDouble())).toFloat()
                            val rot = st.rotationAnim.value
                            val base = if (isRight) 180f else 0f
                            var best = -1
                            var bestDiff = Float.MAX_VALUE
                            for (i in 0 until st.itemCount) {
                                val itemAngle = base + i * st.stepAngle + rot
                                var diff = (tapDeg - itemAngle) % 360f
                                if (diff > 180f) diff -= 360f
                                if (diff < -180f) diff += 360f
                                val ad = abs(diff)
                                if (ad <= st.stepAngle * 0.5f && ad < bestDiff) {
                                    bestDiff = ad
                                    best = i
                                }
                            }
                            if (best >= 0) {
                                upChange?.consume()
                                tapCb?.invoke(best)
                            }
                        }
                    }
                    if (dragging) {
                        st.isScrolling = false
                    }
                }
            }
        }
    )
}
