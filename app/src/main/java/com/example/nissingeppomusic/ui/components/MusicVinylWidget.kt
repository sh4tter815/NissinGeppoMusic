package com.example.nissingeppomusic.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
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
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.launch

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
    onFocusTrackChanged: (AppInfo?) -> Unit = {},
    onRackVisibilityChanged: (Boolean) -> Unit = {}
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

    // ディスクラック操作用状態（操作時は3D X軸回転で横倒しレコード盤になる）
    var isLeftInteracting by remember { mutableStateOf(false) }
    var rawDragY by remember { mutableFloatStateOf(0f) }

    // ラックのスクロール位置（曲のインデックス単位・小数）。指に追従し、離すと最も近い曲へスナップする
    val widgetScope = rememberCoroutineScope()
    val scrollAnim = remember { Animatable(0f) }

    // ラック表示中は外側（右円・下ダイヤル等）を隠すよう親へ通知する
    LaunchedEffect(isLeftInteracting) {
        onRackVisibilityChanged(isLeftInteracting)
    }

    val discTiltX by animateFloatAsState(
        targetValue = if (isLeftInteracting) 55f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "DiscTiltX"
    )

    val currentTrackIndex = remember(playerManager.currentSongUri, allApps) {
        val idx = allApps.indexOfFirst { it.packageName == playerManager.currentSongUri }
        if (idx != -1) idx else 0
    }

    // ラックの中身: 再生キューがあるときはキューを再生順（シャッフル時はその順序）で、
    // ないときは従来どおり全曲ライブラリを表示する
    val queueMode = playerManager.playOrder.isNotEmpty()
    val rackCurrentIndex = if (queueMode) playerManager.playOrderCurrentPos else currentTrackIndex
    val rackSize = if (queueMode) playerManager.playOrder.size else allApps.size
    fun rackArt(index: Int): Bitmap? =
        if (queueMode) playerManager.playOrder.getOrNull(index)?.albumArt else allApps.getOrNull(index)?.albumArt
    fun rackAppInfo(index: Int): AppInfo? =
        if (queueMode) {
            val uri = playerManager.playOrder.getOrNull(index)?.uriString
            allApps.find { it.packageName == uri }
        } else allApps.getOrNull(index)
    fun rackTitle(index: Int): String =
        rackAppInfo(index)?.label
            ?: if (queueMode) playerManager.playOrder.getOrNull(index)?.title ?: "" else ""

    val itemStepDp = 42f
    val itemStepPx = with(density) { itemStepDp.dp.toPx() }

    val focusedIndex = if (rackSize == 0) 0
        else (rackCurrentIndex + scrollAnim.value.roundToInt()).coerceIn(0, rackSize - 1)

    var lastHapticIndex by remember { mutableIntStateOf(rackCurrentIndex) }

    LaunchedEffect(isLeftInteracting, focusedIndex) {
        if (isLeftInteracting) {
            val focusedApp = rackAppInfo(focusedIndex)
            if (focusedApp != null) {
                onFocusTrackChanged(focusedApp)
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
                .pointerInput(allApps, playerManager.currentSongUri, playerManager.playOrder) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val pos = down.position
                            isLeftInteracting = true
                            var moved = false
                            val startY = pos.y
                            rawDragY = 0f
                            val startIdx = rackCurrentIndex
                            lastHapticIndex = startIdx

                            drag(down.id) { change ->
                                val deltaY = change.position.y - startY
                                rawDragY = deltaY
                                if (abs(deltaY) > 4f) {
                                    moved = true
                                }
                                // スクロール位置を指に追従させる
                                widgetScope.launch { scrollAnim.snapTo(rawDragY / itemStepPx) }
                                change.consume()
                            }

                            val finalIndex = (startIdx + scrollAnim.value.roundToInt()).coerceIn(0, (rackSize - 1).coerceAtLeast(0))
                            val targetOffset = (finalIndex - startIdx).toFloat()
                            val shouldPlay = moved && finalIndex != startIdx

                            // 離したら最も近い曲へ補間スナップし、完了後に再生/ジャンプしてラックを閉じる
                            // （左側を押してその場で離しただけでは何もしない。再生/一時停止は右のシーク円盤だけ）
                            widgetScope.launch {
                                scrollAnim.animateTo(
                                    targetValue = targetOffset,
                                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                                )
                                if (shouldPlay) {
                                    if (queueMode) {
                                        if (finalIndex in playerManager.playOrder.indices) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            playerManager.playAtQueuePosition(finalIndex)
                                        }
                                    } else if (allApps.isNotEmpty() && finalIndex in allApps.indices) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onPlayTrack(allApps[finalIndex])
                                    }
                                }
                                isLeftInteracting = false
                                scrollAnim.snapTo(0f)
                                rawDragY = 0f
                                onFocusTrackChanged(null)
                            }
                        }
                    }
                }
        )

        if (isLeftInteracting && rackSize > 0) {
            // --- 横倒しレコード盤スタック ---
            // スクロール位置（小数）に追従してディスクが滑らかに流れ、配置は左上から
            // 下に向かって少しずつ右へずれるカスケード状にする
            val virtualPos = rackCurrentIndex + scrollAnim.value
            val firstIdx = maxOf(0, floor(virtualPos - 4f).toInt())
            val lastIdx = minOf(rackSize - 1, ceil(virtualPos + 4f).toInt())

            for (targetTrackIdx in firstIdx..lastIdx) {
                val relPos = targetTrackIdx - virtualPos
                val gapOffset = sign(relPos) * 24f * min(1f, abs(relPos))
                val yPosDp = discCenterYDp + (relPos * itemStepDp) + gapOffset
                val isFocused = (targetTrackIdx == focusedIndex)

                // 上から下へ少しずつ右へずらし、選択中はさらに右へ押し出す
                val focusBump = (1f - min(1f, abs(relPos))) * 16f
                val xShiftDp = relPos * 7f + focusBump

                // 一番上と一番下は境目が分からないようグラデーションで消す
                val edgeFade = (1f - (abs(relPos) - 2.2f) / 1.8f).coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        .offset(x = (discCenterXDp - discRadiusDp + xShiftDp).dp, y = (yPosDp - discRadiusDp).dp)
                        .size((discRadiusDp * 2f).dp)
                        .graphicsLayer {
                            rotationX = discTiltX
                            cameraDistance = 12 * density.density
                            alpha = edgeFade
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
                        val art = rackArt(targetTrackIdx)
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

                    }
                }

                // 各ディスクの右に曲名を表示（右の円にかぶらない幅で省略表示）
                Text(
                    text = rackTitle(targetTrackIdx),
                    color = if (isFocused) Color.White.copy(alpha = edgeFade) else Color.White.copy(alpha = 0.65f * edgeFade),
                    fontSize = if (isFocused) 14.sp else 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .offset(
                            x = (discCenterXDp + discRadiusDp + xShiftDp + 10f).dp,
                            y = (yPosDp - 9f).dp
                        )
                        .width(105.dp)
                )
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

    // ラック表示中は曲情報（FLAC等）を隠す
    if (!isLeftInteracting) {
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
}
