package com.example.nissingeppomusic.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nissingeppomusic.data.AppInfo
import com.example.nissingeppomusic.data.AppPreferenceManager
import com.example.nissingeppomusic.data.MusicPlayerManager
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun MusicVinylWidget(
    modifier: Modifier = Modifier,
    playerManager: MusicPlayerManager,
    allApps: List<AppInfo> = emptyList(),
    discCenterXDp: Float,
    discCenterYDp: Float,
    discRadiusDp: Float = 165f,
    accentColor: Color = Color(0xFFE53935),
    onPlayTrack: (AppInfo) -> Unit = {},
    onFocusTrackChanged: (AppInfo?) -> Unit = {}
) {
    val context = LocalContext.current
    val prefManager = remember { AppPreferenceManager(context) }
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val isPlaying = playerManager.isPlaying
    val albumArtBitmap = playerManager.currentAlbumArt

    // 累積回転角度の状態管理
    var rotationAngle by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            var lastTime = withFrameNanos { it }
            while (isPlaying) {
                withFrameNanos { frameTime ->
                    val deltaSeconds = (frameTime - lastTime) / 1_000_000_000f
                    lastTime = frameTime
                    val degreesPerSecond = 72f
                    rotationAngle = (rotationAngle + degreesPerSecond * deltaSeconds) % 360f
                }
            }
        } else {
            val startAngle = rotationAngle
            val degreesToDecelerate = 25f
            val targetAngle = startAngle + degreesToDecelerate
            val animatable = Animatable(startAngle)
            animatable.animateTo(
                targetValue = targetAngle,
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
            ) {
                rotationAngle = value % 360f
            }
        }
    }

    // ディスクラック操作用状態（操作時は3D X軸回転で横倒しレコード盤になり、位置は固定で中身だけ切り替わる）
    var isLeftInteracting by remember { mutableStateOf(false) }
    var rawDragY by remember { mutableFloatStateOf(0f) }

    val discTiltX by animateFloatAsState(
        targetValue = if (isLeftInteracting) 55f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "DiscTiltX"
    )

    val currentTrackIndex = remember(playerManager.currentSongUri, allApps) {
        val idx = allApps.indexOfFirst { it.packageName == playerManager.currentSongUri }
        if (idx != -1) idx else 0
    }

    val itemStepDp = 42f
    val itemStepPx = with(density) { itemStepDp.dp.toPx() }

    val focusIndexOffset = (-rawDragY / itemStepPx).roundToInt()
    val focusedIndex = remember(currentTrackIndex, focusIndexOffset, allApps.size) {
        if (allApps.isEmpty()) 0
        else (currentTrackIndex + focusIndexOffset).coerceIn(0, allApps.size - 1)
    }

    var lastHapticIndex by remember { mutableIntStateOf(currentTrackIndex) }

    LaunchedEffect(isLeftInteracting, focusedIndex) {
        if (isLeftInteracting) {
            if (focusedIndex in allApps.indices) {
                onFocusTrackChanged(allApps[focusedIndex])
                if (focusedIndex != lastHapticIndex) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    lastHapticIndex = focusedIndex
                }
            }
        } else {
            onFocusTrackChanged(null)
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // 画面左側（幅の半分）専用のタッチ入力エリア
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .align(Alignment.CenterStart)
                .pointerInput(allApps, playerManager.currentSongUri) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val pos = down.position
                            isLeftInteracting = true
                            var moved = false
                            val startY = pos.y
                            rawDragY = 0f
                            val startIdx = allApps.indexOfFirst { it.packageName == playerManager.currentSongUri }.coerceAtLeast(0)
                            lastHapticIndex = startIdx

                            drag(down.id) { change ->
                                val deltaY = change.position.y - startY
                                rawDragY = deltaY
                                if (abs(deltaY) > 4f) {
                                    moved = true
                                }
                                change.consume()
                            }

                            val finalOffset = (-rawDragY / itemStepPx).roundToInt()
                            val finalIndex = (startIdx + finalOffset).coerceIn(0, (allApps.size - 1).coerceAtLeast(0))

                            if (!moved) {
                                playerManager.togglePlayPause()
                            } else {
                                if (allApps.isNotEmpty() && finalIndex in allApps.indices) {
                                    val selectedApp = allApps[finalIndex]
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onPlayTrack(selectedApp)
                                }
                            }

                            isLeftInteracting = false
                            rawDragY = 0f
                            onFocusTrackChanged(null)
                        }
                    }
                }
        )

        if (isLeftInteracting && allApps.isNotEmpty()) {
            // --- 横倒しレコード盤の【位置固定】スロット型スタック描画 ---
            // ディスクの位置(Y座標)は移動させず、Y位置は固定。スクロールすると各スロットのアートワーク中身のみ切り替わる
            val relOffsets = listOf(-3, -2, -1, 0, 1, 2, 3)

            for (relOffset in relOffsets) {
                val targetTrackIdx = focusedIndex + relOffset
                if (targetTrackIdx !in allApps.indices) continue

                val gapOffset = when {
                    relOffset < 0 -> -24f
                    relOffset > 0 -> 24f
                    else -> 0f
                }
                // Y座標の位置はピクセルドラッグによらず完全固定
                val yPosDp = discCenterYDp + (relOffset * itemStepDp) + gapOffset
                val isFocused = (relOffset == 0)
                val trackApp = allApps[targetTrackIdx]

                Box(
                    modifier = Modifier
                        .offset(x = (discCenterXDp - discRadiusDp).dp, y = (yPosDp - discRadiusDp).dp)
                        .size((discRadiusDp * 2f).dp)
                        .graphicsLayer {
                            rotationX = discTiltX
                            cameraDistance = 12 * density.density
                            alpha = if (isFocused) 1.0f else 0.45f
                        }
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val outerRadius = size.width / 2f
                        drawCircle(
                            color = if (isFocused) accentColor else Color.White.copy(alpha = 0.5f),
                            radius = outerRadius,
                            center = center,
                            style = Stroke(width = if (isFocused) 3f else 1.5f)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size((discRadiusDp * 2f * 0.92f).dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val art = trackApp.albumArt
                        if (art != null) {
                            Image(
                                bitmap = art.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(color = accentColor.copy(alpha = 0.5f))
                            }
                        }

                        // 選択中以外のディスクは暗く透けないようにオーバーレイを重ねる
                        if (!isFocused) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF0B101D).copy(alpha = 0.65f))
                            )
                        }
                    }
                }
            }
        } else {
            // --- 通常時の単一回転レコード盤 ---
            Box(
                modifier = Modifier
                    .offset(x = (discCenterXDp - discRadiusDp).dp, y = (discCenterYDp - discRadiusDp).dp)
                    .size((discRadiusDp * 2f).dp)
                    .graphicsLayer {
                        rotationX = discTiltX
                        cameraDistance = 12 * density.density
                    }
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val outerRadius = size.width / 2f

                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = outerRadius,
                        center = center,
                        style = Stroke(width = 1.5f)
                    )
                }

                Box(
                    modifier = Modifier
                        .size((discRadiusDp * 2f * 0.92f).dp)
                        .graphicsLayer {
                            rotationZ = rotationAngle
                        }
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = albumArtBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(accentColor, accentColor.copy(alpha = 0.6f)),
                                    center = center,
                                    radius = size.width / 2f
                                ),
                                radius = size.width / 2f,
                                center = center
                            )
                        }
                    }
                }
            }
        }
    }

    // --- 円の右側（画面内）の円周に沿って湾曲描画するファイル情報 ---
    val displayInfo = playerManager.currentFileInfo

    val fontChoice = prefManager.getFontChoice()
    val customFontPath = if (prefManager.isUseCustomFont()) prefManager.getCustomFontPath() else null
    val customTypeface = remember(fontChoice, customFontPath) {
        parseNativeTypeface(context, fontChoice, customFontPath)
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = discCenterXDp.dp.toPx()
            val centerY = discCenterYDp.dp.toPx()
            val outerRadius = discRadiusDp.dp.toPx()
            val pathRadius = outerRadius + 14.dp.toPx()

            val textPath = android.graphics.Path().apply {
                addArc(
                    centerX - pathRadius,
                    centerY - pathRadius,
                    centerX + pathRadius,
                    centerY + pathRadius,
                    -90f,
                    180f
                )
            }
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 11.sp.toPx()
                isAntiAlias = true
                letterSpacing = 0.15f
                textAlign = android.graphics.Paint.Align.CENTER
                setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                typeface = customTypeface
            }
            drawContext.canvas.nativeCanvas.drawTextOnPath(displayInfo, textPath, 0f, 0f, paint)
        }
    }
}
