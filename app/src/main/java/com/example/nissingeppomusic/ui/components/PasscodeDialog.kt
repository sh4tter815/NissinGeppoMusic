package com.example.nissingeppomusic.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nissingeppomusic.data.AppPreferenceManager
import kotlinx.coroutines.launch
import kotlin.math.*

@Composable
fun RotatableSingleDial(
    value: Int,
    onValueChange: (Int) -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val fontFamily = LocalLauncherFontFamily.current

    val stepAngle = 36f // 360度 / 10数字
    val rotationAnim = remember { Animatable(-value * stepAngle) }

    val currentRotation = rotationAnim.value
    val selectedIndex = remember(currentRotation) {
        val rawIdx = (-currentRotation / stepAngle).roundToInt()
        ((rawIdx % 10) + 10) % 10
    }

    var lastHapticIndex by remember { mutableIntStateOf(selectedIndex) }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex != lastHapticIndex) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onValueChange(selectedIndex)
            lastHapticIndex = selectedIndex
        }
    }

    // 数字をしっかり上に引き上げた配置基準（Box高さ110dpの中央手前上寄り）
    val discRadiusDp = 50f
    val discCenterXDp = -20f
    val discCenterYDp = 38f

    Box(
        modifier = modifier
            .width(60.dp)
            .height(110.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var lastY = down.position.y
                        var rawRotation = rotationAnim.value

                        drag(down.id) { change ->
                            change.consume()
                            val dy = change.position.y - lastY
                            lastY = change.position.y

                            val deltaAngle = -(dy / discRadiusDp) * (180f / PI.toFloat()) * 0.85f
                            rawRotation += deltaAngle
                            coroutineScope.launch {
                                rotationAnim.snapTo(rawRotation)
                            }
                        }

                        // 指を離した瞬間: 一番近い数字へ吸い付くように補間スナップ
                        val nearestIndex = (-rawRotation / stepAngle).roundToInt()
                        val snapAngle = -nearestIndex * stepAngle
                        coroutineScope.launch {
                            rotationAnim.animateTo(
                                targetValue = snapAngle,
                                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                            )
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // 弧に沿って 0〜9 の数字をアーチ配置（半円線の描画なし）
        val totalItems = 10
        val baseCenterAngle = 0f

        val minK = floor((-70f - currentRotation) / stepAngle).toInt() - 1
        val maxK = ceil((70f - currentRotation) / stepAngle).toInt() + 1

        for (k in minK..maxK) {
            val itemAngle = baseCenterAngle + (k * stepAngle) + currentRotation
            val angleDiffFromCenter = abs(itemAngle - baseCenterAngle)

            if (angleDiffFromCenter <= 70f) {
                val itemIndex = ((k % totalItems) + totalItems) % totalItems
                val isSelected = (itemIndex == selectedIndex)
                val rad = itemAngle * (PI.toFloat() / 180f)

                val itemXDp = discCenterXDp + discRadiusDp * cos(rad)
                val itemYDp = discCenterYDp + discRadiusDp * sin(rad)

                val edgeFade = (1.0f - (angleDiffFromCenter / 70f).coerceIn(0f, 1f).pow(2)).coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        .offset(x = (itemXDp - 18f).dp, y = (itemYDp - 18f).dp)
                        .size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = itemIndex.toString(),
                        color = if (isSelected) {
                            if (isError) Color(0xFFFF5252) else Color.White
                        } else {
                            Color.White.copy(alpha = 0.35f * edgeFade)
                        },
                        fontSize = if (isSelected) 26.sp else 15.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Light,
                        fontFamily = fontFamily
                    )
                }
            }
        }
    }
}

@Composable
fun PasscodeDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefManager = remember { AppPreferenceManager(context) }
    val savedPasscode = remember { prefManager.getPasscode() } // 例: "1234"
    val fontFamily = LocalLauncherFontFamily.current

    var d1 by remember { mutableIntStateOf(0) }
    var d2 by remember { mutableIntStateOf(0) }
    var d3 by remember { mutableIntStateOf(0) }
    var d4 by remember { mutableIntStateOf(0) }

    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        title = {
            Text(
                text = "ロック解除",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                fontFamily = fontFamily
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "インデックスダイヤルを回して合わせてください\n(初期値: 1234)",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = fontFamily
                )
                Spacer(modifier = Modifier.height(24.dp))

                // 4つの回転スナップインデックスダイヤル
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RotatableSingleDial(value = d1, onValueChange = { d1 = it; isError = false }, isError = isError)
                    RotatableSingleDial(value = d2, onValueChange = { d2 = it; isError = false }, isError = isError)
                    RotatableSingleDial(value = d3, onValueChange = { d3 = it; isError = false }, isError = isError)
                    RotatableSingleDial(value = d4, onValueChange = { d4 = it; isError = false }, isError = isError)
                }

                if (isError) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "パスコードが違います",
                        color = Color(0xFFFF5252),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // 解除ボタン（白枠線のメカニカルボタン）
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(44.dp)
                        .border(1.5.dp, Color.White, RoundedCornerShape(22.dp))
                        .clip(RoundedCornerShape(22.dp))
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            val entered = "$d1$d2$d3$d4"
                            if (entered == savedPasscode) {
                                onSuccess()
                                onDismiss()
                            } else {
                                isError = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "解錠する",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("キャンセル", color = Color.Gray, fontSize = 14.sp, fontFamily = fontFamily)
                }
            }
        },
        containerColor = Color(0xFF101728),
        shape = RoundedCornerShape(24.dp)
    )
}
