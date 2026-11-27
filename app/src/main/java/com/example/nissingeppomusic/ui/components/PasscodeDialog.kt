package com.example.nissingeppomusic.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nissingeppomusic.data.AppPreferenceManager
import kotlin.math.cos
import kotlin.math.sin

/**
 * 1桁分の数字ダイヤル。本体の円盤と共通の DiscCarousel エンジンで回す（輪郭線だけ消す）。
 * 配置は従来どおり: 中心 (-20, 38)、半径 50、選択中の数字が (30, 38) に来る。
 */
@Composable
fun RotatableSingleDial(
    value: Int,
    onValueChange: (Int) -> Unit,
    isError: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val fontFamily = LocalLauncherFontFamily.current
    val state = rememberDiscCarouselState(initialIndex = value, stepAngle = 36f)

    Box(
        modifier = modifier
            .width(60.dp)
            .height(110.dp)
    ) {
        DiscCarousel(
            itemCount = 10,
            discCenterXDp = -20f,
            discCenterYDp = 38f,
            discRadiusDp = 50f,
            stepAngle = 36f,
            isRightDisc = false,
            isLoopable = true,
            state = state,
            showOutline = false,
            onSelectedIndexChange = { onValueChange(it) },
            modifier = Modifier.fillMaxSize()
        ) { index, rad, labelAlpha, isSelected ->
            val itemXDp = -20f + 50f * cos(rad)
            val itemYDp = 38f + 50f * sin(rad)
            Box(
                modifier = Modifier
                    .offset(x = (itemXDp - 18f).dp, y = (itemYDp - 18f).dp)
                    .size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = index.toString(),
                    color = if (isSelected) {
                        if (isError) Color(0xFFFF5252) else accentColor
                    } else {
                        Color.White.copy(alpha = 0.35f * labelAlpha)
                    },
                    fontSize = if (isSelected) 26.sp else 15.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Light,
                    fontFamily = fontFamily
                )
            }
        }
    }
}

@Composable
fun PasscodeDialog(
    accentColor: Color = Color(0xFF4DD0E1),
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

    BlurDialog(onDismissRequest = onDismiss) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DiscGlyph("lock", accentColor)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "ロック解除",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    fontFamily = fontFamily
                )
            }
        Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ダイヤルを回して合わせてください\n(初期値: 1234)",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = fontFamily
                )
                Spacer(modifier = Modifier.height(24.dp))

                // 4つの数字ダイヤル＋選択中の行を囲む白枠（本体の円盤の輪郭と同じ 1.5dp / 白0.85）
                Box(contentAlignment = Alignment.TopCenter) {
                    Box(
                        modifier = Modifier
                            .padding(top = 15.dp)
                            .width(268.dp)
                            .height(46.dp)
                            .border(1.5.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(23.dp))
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RotatableSingleDial(value = d1, onValueChange = { d1 = it; isError = false }, isError = isError, accentColor = accentColor)
                        RotatableSingleDial(value = d2, onValueChange = { d2 = it; isError = false }, isError = isError, accentColor = accentColor)
                        RotatableSingleDial(value = d3, onValueChange = { d3 = it; isError = false }, isError = isError, accentColor = accentColor)
                        RotatableSingleDial(value = d4, onValueChange = { d4 = it; isError = false }, isError = isError, accentColor = accentColor)
                    }
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

                // 解除ボタン（アクセント色で塗る）
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(accentColor)
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
                        color = Color.Black.copy(alpha = 0.85f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("キャンセル", color = Color.White.copy(alpha = 0.55f), fontSize = 14.sp, fontFamily = fontFamily)
                }
            }
    }
}
