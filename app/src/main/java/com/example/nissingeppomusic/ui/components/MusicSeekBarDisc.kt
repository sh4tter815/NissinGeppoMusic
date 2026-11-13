package com.example.nissingeppomusic.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nissingeppomusic.data.AppInfo
import com.example.nissingeppomusic.data.AppPreferenceManager
import com.example.nissingeppomusic.data.MusicPlayerManager
import java.util.Locale
import kotlin.math.*

@Composable
fun MusicSeekBarDisc(
    modifier: Modifier = Modifier,
    playerManager: MusicPlayerManager,
    focusedTrack: AppInfo? = null,
    discCenterXDp: Float,
    discCenterYDp: Float,
    discRadiusDp: Float = 165f,
    accentColor: Color = Color(0xFFE53935)
) {
    val context = LocalContext.current
    val prefManager = remember { AppPreferenceManager(context) }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val currentPositionMs = playerManager.currentPosition
    val isPlaying = playerManager.isPlaying
    val durationMs = playerManager.duration.coerceAtLeast(1000L)
    val totalSeconds = (durationMs / 1000f).coerceAtLeast(1f)
    val currentSeconds = (currentPositionMs / 1000f).coerceIn(0f, totalSeconds)

    val centerPx = with(density) {
        Offset(discCenterXDp.dp.toPx(), discCenterYDp.dp.toPx())
    }
    val radiusPx = with(density) { discRadiusDp.dp.toPx() }

    var isDragging by remember { mutableStateOf(false) }
    var dragSeekSeconds by remember { mutableFloatStateOf(currentSeconds) }

    LaunchedEffect(currentPositionMs) {
        if (!isDragging) {
            dragSeekSeconds = currentSeconds
        }
    }

    val activeSeconds = if (isDragging) dragSeekSeconds else currentSeconds
    val totalSpanDegrees = 300f // 1周（360度）より少し足りない300度にして0と終端が被らないようにする
    val anglePerSecond = totalSpanDegrees / totalSeconds
    // 一番左（180度）に activeSeconds が来るような回転角度
    val rotationDegrees = 180f - (activeSeconds * anglePerSecond)

    // 現在位置の丸ぽちの鼓動（パルス）アニメーション
    val infiniteTransition = rememberInfiniteTransition(label = "DotPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val fontChoice = prefManager.getFontChoice()
    val customFontPath = if (prefManager.isUseCustomFont()) prefManager.getCustomFontPath() else null
    val customTypeface = remember(fontChoice, customFontPath) {
        parseNativeTypeface(context, fontChoice, customFontPath)
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // 画面右側（幅の半分）専用のタッチ入力エリア
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .align(Alignment.CenterEnd)
                .pointerInput(totalSeconds, centerPx, radiusPx) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var moved = false
                            var lastY = down.position.y
                            var lastHapticSec = dragSeekSeconds.toInt()

                            drag(down.id) { change ->
                                val deltaY = change.position.y - lastY
                                if (abs(deltaY) > 2f) {
                                    moved = true
                                    isDragging = true
                                }
                                lastY = change.position.y
                                if (moved) {
                                    val timeDelta = deltaY * 0.15f
                                    dragSeekSeconds = (dragSeekSeconds + timeDelta).coerceIn(0f, totalSeconds)
                                    val currentSecInt = dragSeekSeconds.toInt()
                                    if (currentSecInt != lastHapticSec) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        lastHapticSec = currentSecInt
                                    }
                                }
                                change.consume()
                            }

                            if (!moved) {
                                // タップされた場合は再生/一時停止をトグル
                                playerManager.togglePlayPause()
                            } else {
                                isDragging = false
                                playerManager.seekTo((dragSeekSeconds * 1000L).toLong())
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    }
                }
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = centerPx.x
            val cy = centerPx.y
            val r = radiusPx

            // 外周リング
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = 2f)
            )

            // 内側の進行状況インジケーターリング（300度スパン）
            val progressSweepAngle = activeSeconds * anglePerSecond
            drawArc(
                color = accentColor,
                startAngle = rotationDegrees,
                sweepAngle = progressSweepAngle,
                useCenter = false,
                topLeft = Offset(cx - r + 8f, cy - r + 8f),
                size = androidx.compose.ui.geometry.Size((r - 8f) * 2f, (r - 8f) * 2f),
                style = Stroke(width = 4f)
            )

            // 一番左（現在地点）に表示する白いパルスする丸い点
            val leftDotX = cx - r
            val leftDotY = cy
            // 鼓動する外側の光のリング
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = 12.dp.toPx() * pulseScale,
                center = Offset(leftDotX, leftDotY)
            )

            val nativeCanvas = drawContext.canvas.nativeCanvas

            // 中心には再生状態のアイコンを白で描く（再生中=一時停止マーク、停止中=再生マーク）
            val iconPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                isAntiAlias = true
                style = android.graphics.Paint.Style.FILL
                setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
            }
            if (isPlaying) {
                val barW = 3.2.dp.toPx()
                val barH = 11.dp.toPx()
                val gap = 3.4.dp.toPx()
                nativeCanvas.drawRect(
                    leftDotX - gap / 2f - barW, leftDotY - barH / 2f,
                    leftDotX - gap / 2f, leftDotY + barH / 2f, iconPaint
                )
                nativeCanvas.drawRect(
                    leftDotX + gap / 2f, leftDotY - barH / 2f,
                    leftDotX + gap / 2f + barW, leftDotY + barH / 2f, iconPaint
                )
            } else {
                val triW = 10.dp.toPx()
                val triH = 12.dp.toPx()
                val triPath = android.graphics.Path().apply {
                    moveTo(leftDotX - triW / 2f + 1.dp.toPx(), leftDotY - triH / 2f)
                    lineTo(leftDotX - triW / 2f + 1.dp.toPx(), leftDotY + triH / 2f)
                    lineTo(leftDotX + triW / 2f + 1.dp.toPx(), leftDotY)
                    close()
                }
                nativeCanvas.drawPath(triPath, iconPaint)
            }

            // 円の外（現在地点の丸の左側）に「経過時間」と「曲の長さ」を2行で直線区切り表示
            val activeMin = activeSeconds.toInt() / 60
            val activeSec = activeSeconds.toInt() % 60
            val totalMin = totalSeconds.toInt() / 60
            val totalSec = totalSeconds.toInt() % 60

            val elapsedText = String.format(Locale.getDefault(), "%d:%02d", activeMin, activeSec)
            val totalText = String.format(Locale.getDefault(), "%d:%02d", totalMin, totalSec)

            // 現在地点の丸から左へ40dp（円の外側）
            val textX = leftDotX - 40.dp.toPx()

            val elapsedPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 13.sp.toPx()
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
                setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                if (customTypeface != null) typeface = customTypeface
            }

            val linePaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                strokeWidth = 1.2f.dp.toPx()
                isAntiAlias = true
                setShadowLayer(3f, 0f, 0f, android.graphics.Color.BLACK)
            }

            val totalPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.LTGRAY
                textSize = 11.sp.toPx()
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
                setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                if (customTypeface != null) typeface = customTypeface
            }

            // 1行目: 現在の経過時間 (例: "1:23")
            nativeCanvas.drawText(elapsedText, textX, cy - 5.dp.toPx(), elapsedPaint)

            // 直線区切り線
            val lineWidth = 34.dp.toPx()
            nativeCanvas.drawLine(textX - lineWidth / 2f, cy, textX + lineWidth / 2f, cy, linePaint)

            // 2行目: 曲の長さ (例: "3:45")
            nativeCanvas.drawText(totalText, textX, cy + 13.dp.toPx(), totalPaint)

            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 11.sp.toPx()
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
                if (customTypeface != null) typeface = customTypeface
            }

            // 触っていない時は15秒刻み、触っている時は1秒刻みの細線を表示（テキスト表示は常に15秒単位 1:00, 1:15, 1:30... で統一）
            val step = if (isDragging) 1 else 15
            val maxSeconds = totalSeconds.toInt()

            for (sec in 0..maxSeconds step step) {
                val secAngleDeg = sec * anglePerSecond
                val totalDeg = secAngleDeg + rotationDegrees
                val rad = Math.toRadians(totalDeg.toDouble())

                val is15SecMajor = (sec % 15 == 0)
                val is5SecMedium = (sec % 5 == 0)

                val tickRadius = when {
                    is15SecMajor -> r - 18.dp.toPx()
                    is5SecMedium -> r - 13.dp.toPx()
                    else -> r - 9.dp.toPx()
                }
                val textRadius = r + 14.dp.toPx()

                val x1 = cx + tickRadius * cos(rad).toFloat()
                val y1 = cy + tickRadius * sin(rad).toFloat()
                val x2 = cx + r * cos(rad).toFloat()
                val y2 = cy + r * sin(rad).toFloat()

                // 目盛り線
                nativeCanvas.drawLine(
                    x1, y1, x2, y2,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        strokeWidth = if (is15SecMajor) 2f else if (is5SecMedium) 1.5f else 0.8f
                        isAntiAlias = true
                    }
                )

                // タイムスタンプテキストは常に15秒単位（0s, 15s, 30s, 45s, 1:00, 1:15, 1:30...）で統一描画
                if (is15SecMajor) {
                    val minutes = sec / 60
                    val seconds = sec % 60
                    val timeStr = if (minutes > 0) String.format(Locale.getDefault(), "%d:%02d", minutes, seconds) else "${sec}s"

                    val tx = cx + textRadius * cos(rad).toFloat()
                    val ty = cy + textRadius * sin(rad).toFloat() + 4.dp.toPx()

                    nativeCanvas.drawText(timeStr, tx, ty, paint)
                }
            }
        }
    }
}
