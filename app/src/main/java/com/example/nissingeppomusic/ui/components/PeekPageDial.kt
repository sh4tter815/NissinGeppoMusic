package com.example.nissingeppomusic.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 画面下からチラ見えする円形ページダイヤル。
 * 長押ししたまま横にドラッグするとダイヤルが回り、離した位置に一番近いページへ切り替わる。
 */
@Composable
fun PeekPageDial(
    currentPage: Int,
    onPageSelected: (Int) -> Unit,
    accentColor: Color = Color(0xFFE53935),
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val totalPages = 4
    val stepAngle = 30f
    val radius = 175.dp
    val stripHeight = 64.dp

    var dragPage by remember { mutableFloatStateOf(Float.NaN) }
    var lastCandidate by remember { mutableIntStateOf(currentPage) }
    val isDragging = !dragPage.isNaN()
    val pageFloat by animateFloatAsState(
        targetValue = if (isDragging) dragPage else currentPage.toFloat(),
        animationSpec = tween(durationMillis = if (isDragging) 0 else 260),
        label = "PeekPageFloat"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(stripHeight)
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val radiusPx = with(density) { radius.toPx() }
        val centerXPx = widthPx / 2f
        val discLeftPx = centerXPx - radiusPx
        val discTopPx = with(density) { 6.dp.toPx() }

        Box(
            modifier = Modifier
                .offset { IntOffset(discLeftPx.roundToInt(), discTopPx.roundToInt()) }
                .size(radius * 2)
                .clip(CircleShape)
                .background(Color(0x99101728))
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                .pointerInput(currentPage) {
                    val pxPerPage = with(density) { 90.dp.toPx() }
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            dragPage = currentPage.toFloat()
                            lastCandidate = currentPage
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragEnd = {
                            val target = dragPage.roundToInt().coerceIn(0, totalPages - 1)
                            dragPage = Float.NaN
                            if (target != currentPage) onPageSelected(target)
                        },
                        onDragCancel = { dragPage = Float.NaN },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (!dragPage.isNaN()) {
                                val next = (dragPage - dragAmount.x / pxPerPage)
                                    .coerceIn(0f, (totalPages - 1).toFloat())
                                dragPage = next
                                val candidate = next.roundToInt()
                                if (candidate != lastCandidate) {
                                    lastCandidate = candidate
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                    )
                }
        ) {
            // ページアイコンを円周上に配置
            val iconRingPx = radiusPx * 0.80f
            for (index in 0 until totalPages) {
                val angleRad = Math.toRadians(((index - pageFloat) * stepAngle).toDouble())
                val iconCenterXPx = radiusPx + (iconRingPx * sin(angleRad)).toFloat()
                val iconCenterYPx = radiusPx - (iconRingPx * cos(angleRad)).toFloat()
                val isSelected = index == pageFloat.roundToInt()
                val iconColor = if (isSelected) accentColor else Color.White.copy(alpha = 0.6f)
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (iconCenterXPx - with(density) { 22.dp.toPx() }).roundToInt(),
                                (iconCenterYPx - with(density) { 22.dp.toPx() }).roundToInt()
                            )
                        }
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) accentColor.copy(alpha = 0.2f) else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    when (index) {
                        0 -> WhiteGearIcon(size = 22.dp, color = iconColor)
                        1 -> WhiteHomeIcon(size = 22.dp, color = iconColor)
                        2 -> WhiteHamburgerIcon(size = 22.dp, color = iconColor)
                        3 -> WhiteFolderIcon(size = 22.dp, color = iconColor)
                    }
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = (-4).dp)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(accentColor)
                        )
                    }
                }
            }
        }

        // 選択位置の目印（上端中央の小さなドット）
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(6.dp)
                .clip(CircleShape)
                .background(accentColor)
        )
    }
}