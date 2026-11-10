package com.example.nissingeppomusic.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.nissingeppomusic.data.AppInfo

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppItem(
    app: AppInfo,
    modifier: Modifier = Modifier,
    groupHeader: String? = null,
    isSelected: Boolean = false,
    isRightDisc: Boolean = true, // 右半円(DRAWER)か、左半円(SEARCH/FAVORITE)か
    onClick: (AppInfo) -> Unit,
    onLongClick: ((AppInfo) -> Unit)? = null
) {
    val iconBitmap = remember(app.icon) {
        try {
            app.icon?.toBitmap()?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    Box(
        modifier = modifier
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onClick(app) },
                onLongClick = if (onLongClick != null) { { onLongClick(app) } } else null
            )
            .padding(vertical = 4.dp)
    ) {
        if (isRightDisc) {
            // --- 右半円 (DRAWER) 用 ---
            // 右端（円の線の位置）に合わせてアプリ名を右揃え配置
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 選択中項目のアイコン（アプリ名の左横）
                if (isSelected && iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(22.dp)
                            .padding(end = 6.dp)
                    )
                }

                // アプリ名（右揃え）
                Text(
                    text = app.label,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f),
                    fontSize = if (isSelected) 20.sp else 17.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    letterSpacing = 1.5.sp,
                    textAlign = TextAlign.End
                )

                // 円の内側（右側）に見出し表示（「A」「B」等）
                if (!groupHeader.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = groupHeader,
                        color = Color(0xFF88C0FF),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start
                    )
                }
            }
        } else {
            // --- 左半円 (SEARCH / FAVORITE) 用 ---
            // 左端（円の線の位置）に合わせてアプリ名を左揃え配置
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                // アプリ名
                Text(
                    text = app.label,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f),
                    fontSize = if (isSelected) 20.sp else 17.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    letterSpacing = 1.5.sp,
                    textAlign = TextAlign.Start
                )

                // 選択中項目のアイコン（アプリ名の右横）
                if (isSelected && iconBitmap != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
