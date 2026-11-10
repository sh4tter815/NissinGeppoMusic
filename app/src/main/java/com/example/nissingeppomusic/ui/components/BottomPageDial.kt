package com.example.nissingeppomusic.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

@Composable
fun BottomPageDial(
    currentPage: Int,
    onPageSelected: (Int) -> Unit,
    accentColor: Color = Color(0xFFE53935),
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val totalPages = 4

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // iOS Liquid Glass 風のすりガラス調ピル型フローティングタブバー
        Row(
            modifier = Modifier
                .height(56.dp)
                .background(
                    color = Color(0x99101728), // 半透明ダークグラス
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (index in 0 until totalPages) {
                val isSelected = (index == currentPage)
                val iconColor = if (isSelected) accentColor else Color.White.copy(alpha = 0.6f)

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            if (isSelected) accentColor.copy(alpha = 0.2f) else Color.Transparent
                        )
                        .clickable {
                            if (currentPage != index) {
                                onPageSelected(index)
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    when (index) {
                        0 -> WhiteGearIcon(size = 22.dp, color = iconColor)
                        1 -> WhiteHomeIcon(size = 22.dp, color = iconColor)
                        2 -> WhiteHamburgerIcon(size = 22.dp, color = iconColor)
                        3 -> WhiteFolderIcon(size = 22.dp, color = iconColor)
                    }

                    // 選択中のインジケータードット
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 4.dp)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(accentColor)
                        )
                    }
                }
            }
        }
    }
}
