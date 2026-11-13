package com.example.nissingeppomusic.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 画面下からチラ見えする円形ページダイヤル。
 * フォルダ選択の円盤（RotatableAppDisc）と同じ流儀: 塗りなし・白い輪郭線の円、
 * アイコンは円周の内側に配置し、一番上（真上）に来たページが選択状態。
 * 横ドラッグで回転し、離すと慣性つきで一番近いページにスナップする。
 */
@Composable
fun PeekPageDial(
    currentStop: Int,
    onStopSelected: (Int) -> Unit,
    accentColor: Color = Color(0xFFE53935),
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // 強いハプティクス用（Compose標準のTextHandleMoveは弱いため、Vibratorを直接使う）
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
    fun vibrateStrong(durationMs: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(durationMs)
        }
    }

    // 押している間はフォルダ選択の円盤（MechanicalDisc）と同じく1.30倍に拡大し、指で隠れないようにする
    var isPressed by remember { mutableStateOf(false) }
    val expandScale by animateFloatAsState(
        targetValue = if (isPressed) 1.30f else 1.0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "DialExpandScale"
    )

    // ストップは6つ: 0=設定, 1=ホーム(再生中), 2=曲名, 3=アーティスト, 4=アルバム, 5=プレイリスト(フォルダ)
    val totalPages = 6
    val stepAngle = 24f
    val discRadiusDp = 165f
    val baseCenterAngle = -90f // 真上

    val animatableRotation = remember { Animatable(-currentStop * stepAngle) }
    var rawRotationAngle by remember { mutableFloatStateOf(-currentStop * stepAngle) }
    // pointerInput内のループは再コンポーズで再起動しないため、currentStop引数を直接使うと
    // 初回表示時の値（起動時のホーム=1）に固定されてしまう。rememberUpdatedStateで最新値を参照する。
    val latestCurrentPage = rememberUpdatedState(currentStop)

    val minRotation = -((totalPages - 1) * stepAngle)
    val maxRotation = 0f

    // ページが外部から切り替わったとき（他UIからの遷移など）にダイヤルを追従させる。
    // ドラッグで切り替えた場合は rawRotationAngle が既に目標角度なので何も起きない（＝バネ戻り防止）。
    LaunchedEffect(currentStop) {
        val targetAngle = -currentStop * stepAngle
        if (rawRotationAngle != targetAngle) {
            rawRotationAngle = targetAngle
            coroutineScope.launch {
                animatableRotation.animateTo(
                    targetValue = targetAngle,
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    val currentRotation = animatableRotation.value

    val selectedIndex = remember(currentRotation) {
        (-currentRotation / stepAngle).roundToInt().coerceIn(0, totalPages - 1)
    }

    var lastHapticIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex != lastHapticIndex && lastHapticIndex != -1) {
            vibrateStrong(35, 200)
        }
        lastHapticIndex = selectedIndex
    }

    val radiusPx = with(density) { discRadiusDp.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        val discCenterXDp = maxWidth.value / 2f
        val discCenterYDp = 6f + discRadiusDp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(discRadiusDp) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isPressed = true

                            // フレームワーク標準の drag() は、他の円盤にイベントを消費されると
                            // 開始前にキャンセルされてしまう（ホーム画面のお気に入り円盤との競合）。
                            // 消費状態に依存しない自前のループで座標を追い、競合しても回るようにする。
                            var lastPos = down.position
                            var totalDx = 0f
                            var totalDy = 0f
                            var isDragStarted = false
                            var lastDeltaAngle = 0f
                            var lastTime = System.currentTimeMillis()
                            var pointerUp = false

                            while (!pointerUp) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) {
                                    pointerUp = true
                                    break
                                }
                                if (!change.pressed) {
                                    pointerUp = true
                                    break
                                }

                                val dx = change.position.x - lastPos.x
                                val dy = change.position.y - lastPos.y
                                lastPos = change.position
                                totalDx += dx
                                totalDy += dy

                                if (!isDragStarted) {
                                    if (abs(totalDx) > 6f && abs(totalDx) >= abs(totalDy)) {
                                        isDragStarted = true
                                    }
                                }

                                if (isDragStarted) {
                                    change.consume()

                                    // フォルダ円盤と同じ換算（指の移動量 → 角度、ゲイン1.2）
                                    val deltaAngle = (dx / radiusPx) * (180f / PI.toFloat()) * 1.2f

                                    val currentTime = System.currentTimeMillis()
                                    val dt = (currentTime - lastTime).coerceAtLeast(1)
                                    lastDeltaAngle = deltaAngle / dt * 16f
                                    lastTime = currentTime

                                    val targetAngle = (rawRotationAngle + deltaAngle)
                                        .coerceIn(minRotation - 20f, maxRotation + 20f)
                                    rawRotationAngle = targetAngle
                                    coroutineScope.launch {
                                        animatableRotation.snapTo(targetAngle)
                                    }
                                }
                            }

                            isPressed = false

                            if (isDragStarted) {
                                val inertiaTarget = rawRotationAngle + lastDeltaAngle * 6f
                                val nearestIndex = (-inertiaTarget / stepAngle).roundToInt()
                                    .coerceIn(0, totalPages - 1)
                                val finalSnapAngle = -nearestIndex * stepAngle

                                rawRotationAngle = finalSnapAngle
                                coroutineScope.launch {
                                    animatableRotation.animateTo(
                                        targetValue = finalSnapAngle,
                                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                                    )
                                }
                                if (nearestIndex != latestCurrentPage.value) {
                                    vibrateStrong(60, 255)
                                    onStopSelected(nearestIndex)
                                }
                            }
                        }
                    }
                }
        ) {
            // フォルダ円盤と同じ白い輪郭線の円（塗りなし）
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(discCenterXDp.dp.toPx(), discCenterYDp.dp.toPx())
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = (discRadiusDp * expandScale).dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.5f)
                )
            }

            val innerIconRadiusDp = (discRadiusDp - 18f) * expandScale

            for (index in 0 until totalPages) {
                val itemAngle = baseCenterAngle + (index * stepAngle) + currentRotation
                val angleDiffFromCenter = abs(itemAngle - baseCenterAngle)

                if (angleDiffFromCenter <= 80f) {
                    val rad = itemAngle * (PI.toFloat() / 180f)
                    val edgeFade = (1.0f - (angleDiffFromCenter / 80f).coerceIn(0f, 1f).pow(2))
                        .coerceIn(0f, 1f)
                    val isSelected = index == selectedIndex
                    val iconColor = if (isSelected) {
                        accentColor.copy(alpha = edgeFade)
                    } else {
                        Color.White.copy(alpha = 0.6f * edgeFade)
                    }

                    val iconXDp = discCenterXDp + innerIconRadiusDp * cos(rad)
                    val iconYDp = discCenterYDp + innerIconRadiusDp * sin(rad)

                    Box(
                        modifier = Modifier
                            .offset(x = (iconXDp - 22f).dp, y = (iconYDp - 22f).dp)
                            .size(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (index) {
                            0 -> WhiteGearIcon(size = (22f * expandScale).dp, color = iconColor)
                            1 -> WhiteHomeIcon(size = (22f * expandScale).dp, color = iconColor)
                            2 -> WhiteMusicNoteIcon(size = (22f * expandScale).dp, color = iconColor)
                            3 -> WhitePersonIcon(size = (22f * expandScale).dp, color = iconColor)
                            4 -> WhiteAlbumIcon(size = (22f * expandScale).dp, color = iconColor)
                            5 -> WhiteFolderIcon(size = (22f * expandScale).dp, color = iconColor)
                        }
                    }
                }
            }
        }
    }
}
