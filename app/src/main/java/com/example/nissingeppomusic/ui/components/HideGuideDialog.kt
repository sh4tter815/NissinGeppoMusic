package com.example.nissingeppomusic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HideGuideDialog(
    onDismiss: (dontShowAgain: Boolean) -> Unit
) {
    var dontShowAgain by remember { mutableStateOf(false) }
    val fontFamily = LocalLauncherFontFamily.current
    val fontWeight = LocalLauncherFontWeight.current

    AlertDialog(
        onDismissRequest = { onDismiss(dontShowAgain) },
        confirmButton = {
            TextButton(onClick = { onDismiss(dontShowAgain) }) {
                Text("OK", color = Color(0xFF88C0FF), fontSize = 16.sp, fontFamily = fontFamily, fontWeight = fontWeight)
            }
        },
        title = { Text("アプリを非表示にしました", fontSize = 18.sp, fontFamily = fontFamily, fontWeight = fontWeight) },
        text = {
            Column {
                Text(
                    text = "非表示にしたアプリは、アプリ一覧画面で２本指でピンチアウト（拡大）操作をすると確認できます。",
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    fontFamily = fontFamily,
                    fontWeight = fontWeight,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { dontShowAgain = !dontShowAgain }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF88C0FF),
                            uncheckedColor = Color.Gray
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "今後この説明を表示しない",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontFamily = fontFamily,
                        fontWeight = fontWeight
                    )
                }
            }
        },
        containerColor = Color(0xFF141C2E),
        titleContentColor = Color.White
    )
}

