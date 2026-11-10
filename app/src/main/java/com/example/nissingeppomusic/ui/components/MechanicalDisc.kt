package com.example.nissingeppomusic.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

val INDEX_ITEMS = listOf(
    "あ", "か", "さ", "た", "な", "は", "ま", "や", "ら", "わ", "＃",
    "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
    "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"
)

// 完全な円の描画コンポーネント
@Composable
fun FullCircleDisc(
    modifier: Modifier = Modifier,
    sizeDp: Int = 260,
    isSqueezed: Boolean = false,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val scale by animateFloatAsState(
        targetValue = if (isSqueezed) 0.88f else 1.0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "DiscSqueezeScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .size(sizeDp.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.8f),
                style = Stroke(width = 1.5f)
            )
        }
        content()
    }
}

// 描画専用の索引ディスクコンポーネント（タッチ判定は上位レイヤーで分離）
@Composable
fun RotatableIndexWheel(
    modifier: Modifier = Modifier,
    discCenterXDp: Float,
    discCenterYDp: Float,
    discRadiusDp: Float = 165f,
    currentRotation: Float = 0f,
    isHolding: Boolean = false,
    stepAngle: Float = 360f / INDEX_ITEMS.size,
    accentColor: Color = Color(0xFFE53935),
    centerIcon: @Composable (() -> Unit)? = { WhiteSearchIcon(size = 22.dp) }
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.toFloat()
    val screenHeightDp = configuration.screenHeightDp.toFloat()

    // 操作中の拡大アニメーション (1.30倍)
    val wheelScale by animateFloatAsState(
        targetValue = if (isHolding) 1.30f else 1.0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "WheelScale"
    )

    val selectedIndex = remember(currentRotation, INDEX_ITEMS.size) {
        if (INDEX_ITEMS.isEmpty()) -1
        else {
            val rawIdx = (-currentRotation / stepAngle).roundToInt()
            ((rawIdx % INDEX_ITEMS.size) + INDEX_ITEMS.size) % INDEX_ITEMS.size
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        // --- 操作中のスケール拡大（中心点を軸に拡大） ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = wheelScale
                    scaleY = wheelScale
                    transformOrigin = TransformOrigin(
                        pivotFractionX = (discCenterXDp / screenWidthDp).coerceIn(0f, 1f),
                        pivotFractionY = (discCenterYDp / screenHeightDp).coerceIn(0f, 1f)
                    )
                }
        ) {
            // --- 円周（輪郭）と、インジケーター（ホールド時） ---
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(discCenterXDp.dp.toPx(), discCenterYDp.dp.toPx())
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = discRadiusDp.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.5f)
                )

                if (isHolding) {
                    // 真右（3時方向）の選択インジケーターノッチ
                    drawCircle(
                        color = accentColor,
                        radius = 8f,
                        center = Offset(discCenterXDp.dp.toPx() + discRadiusDp.dp.toPx() - 14f, discCenterYDp.dp.toPx())
                    )
                }
            }

            // 中央アイコン（検索虫眼鏡や「非表示リストへ」など）を画面内に見えている円の右端寄りに描画
            if (centerIcon != null) {
                val iconXDp = discCenterXDp + discRadiusDp - 32f
                val iconYDp = discCenterYDp
                Box(
                    modifier = Modifier
                        .offset(x = (iconXDp - 22f).dp, y = (iconYDp - 22f).dp)
                        .size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    centerIcon()
                }
            }

            // --- 輪郭の内側に「あかさたな…」を常時配置 ---
            val baseCenterAngle = 0f // 3時方向基準
            val totalItems = INDEX_ITEMS.size

            val minK = floor((-75f - currentRotation) / stepAngle).toInt() - 1
            val maxK = ceil((75f - currentRotation) / stepAngle).toInt() + 1

            for (k in minK..maxK) {
                val itemAngle = baseCenterAngle + (k * stepAngle) + currentRotation
                val angleDiffFromCenter = abs(itemAngle - baseCenterAngle)

                if (angleDiffFromCenter <= 75f) {
                    val itemIndex = ((k % totalItems) + totalItems) % totalItems
                    val char = INDEX_ITEMS[itemIndex]
                    val isSelected = (itemIndex == selectedIndex)

                    val radiusOffset = if (isHolding && isSelected) 32f else 20f
                    val currentRadiusDp = discRadiusDp + radiusOffset

                    val rad = itemAngle * (PI.toFloat() / 180f)
                    val itemXDp = discCenterXDp + currentRadiusDp * cos(rad)
                    val itemYDp = discCenterYDp + currentRadiusDp * sin(rad)

                    val edgeFade = (1.0f - (angleDiffFromCenter / 75f).coerceIn(0f, 1f).pow(2)).coerceIn(0f, 1f)
                    val charAlpha = when {
                        isHolding && isSelected -> 1.0f
                        isHolding -> 0.75f
                        else -> 0.35f
                    } * edgeFade

                    Box(
                        modifier = Modifier
                            .offset(x = (itemXDp - 20f).dp, y = (itemYDp - 20f).dp)
                            .size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = char,
                            color = if (isHolding && isSelected) accentColor else Color.White.copy(alpha = charAlpha),
                            fontSize = if (isHolding && isSelected) 22.sp else 13.sp,
                            fontWeight = if (isHolding && isSelected) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// フォルダ選択用ホイール（ユーザーページ 2 用）
@Composable
fun RotatableFolderWheel(
    folders: List<Pair<String, String>>, // folderId -> folderName
    modifier: Modifier = Modifier,
    discCenterXDp: Float,
    discCenterYDp: Float,
    discRadiusDp: Float = 165f,
    currentRotation: Float = 0f,
    isHolding: Boolean = false,
    stepAngle: Float = 24f,
    textSizeSp: Float = 16f,
    letterSpacingSp: Float = 1.2f,
    accentColor: Color = Color(0xFFE53935),
    onNewFolderClick: () -> Unit = {},
    onRenameFolderClick: (String, String) -> Unit = { _, _ -> }
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.toFloat()
    val screenHeightDp = configuration.screenHeightDp.toFloat()

    val wheelScale by animateFloatAsState(
        targetValue = if (isHolding) 1.30f else 1.0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "WheelScale"
    )

    val createNewStr = "新規作成"
    val folderItems = remember(folders, createNewStr) {
        folders + Pair("NEW_FOLDER", createNewStr)
    }

    val selectedIndex = remember(currentRotation, folderItems.size) {
        if (folderItems.isEmpty()) -1
        else {
            val rawIdx = (-currentRotation / stepAngle).roundToInt()
            rawIdx.coerceIn(0, folderItems.size - 1)
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = wheelScale
                    scaleY = wheelScale
                    transformOrigin = TransformOrigin(
                        pivotFractionX = (discCenterXDp / screenWidthDp).coerceIn(0f, 1f),
                        pivotFractionY = (discCenterYDp / screenHeightDp).coerceIn(0f, 1f)
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(discCenterXDp.dp.toPx(), discCenterYDp.dp.toPx())
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = discRadiusDp.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.5f)
                )

                if (isHolding) {
                    drawCircle(
                        color = accentColor,
                        radius = 8f,
                        center = Offset(discCenterXDp.dp.toPx() + discRadiusDp.dp.toPx() - 14f, discCenterYDp.dp.toPx())
                    )
                }
            }

            val baseCenterAngle = 0f
            val totalItems = folderItems.size
            val innerRadiusDp = discRadiusDp - 18f
            val outerRadiusDp = discRadiusDp + 28f

            for (index in 0 until totalItems) {
                val itemAngle = baseCenterAngle + (index * stepAngle) + currentRotation
                val angleDiffFromCenter = abs(itemAngle - baseCenterAngle)

                if (angleDiffFromCenter <= 80f) {
                    val item = folderItems[index]
                    val isSelected = (index == selectedIndex)
                    val rad = itemAngle * (PI.toFloat() / 180f)

                    val innerXDp = discCenterXDp + innerRadiusDp * cos(rad)
                    val innerYDp = discCenterYDp + innerRadiusDp * sin(rad)

                    val outerXDp = discCenterXDp + outerRadiusDp * cos(rad)
                    val outerYDp = discCenterYDp + outerRadiusDp * sin(rad)

                    val edgeFade = (1.0f - (angleDiffFromCenter / 80f).coerceIn(0f, 1f).pow(2)).coerceIn(0f, 1f)

                    // 1. 円の内側: 新規作成は＋アイコン、通常フォルダは「フォルダアイコン」のみ（鉛筆マーク削除）
                    Box(
                        modifier = Modifier
                            .offset(x = (innerXDp - 20f).dp, y = (innerYDp - 20f).dp)
                            .size(40.dp)
                            .clickable {
                                if (item.first == "NEW_FOLDER") {
                                    onNewFolderClick()
                                } else {
                                    onRenameFolderClick(item.first, item.second)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.first == "NEW_FOLDER") {
                            WhitePlusIcon(size = 22.dp, color = if (isSelected) accentColor else Color.White.copy(alpha = 0.85f * edgeFade))
                        } else {
                            WhiteFolderIcon(size = 22.dp, color = if (isSelected) accentColor else Color.White.copy(alpha = 0.85f * edgeFade))
                        }
                    }

                    // 2. 円の【外側】: テキスト（環境設定のテキストサイズ・文字間隔に準拠）
                    if (isHolding || isSelected) {
                        Box(
                            modifier = Modifier
                                .offset(x = outerXDp.dp, y = (outerYDp - 20f).dp)
                                .width(220.dp)
                                .height(40.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = item.second,
                                color = if (isSelected) accentColor else Color.White.copy(alpha = 0.7f * edgeFade),
                                fontSize = textSizeSp.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                letterSpacing = letterSpacingSp.sp,
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            }
        }
    }
}
