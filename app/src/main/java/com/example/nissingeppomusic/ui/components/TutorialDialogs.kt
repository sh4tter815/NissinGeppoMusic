package com.example.nissingeppomusic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TutorialDialog(
    title: String,
    message: String,
    onDismiss: (Boolean) -> Unit // true なら「次回から表示しない」
) {
    var dontShowAgain by remember { mutableStateOf(false) }
    val fontFamily = LocalLauncherFontFamily.current

    AlertDialog(
        onDismissRequest = { onDismiss(dontShowAgain) },
        confirmButton = {
            TextButton(
                onClick = { onDismiss(dontShowAgain) }
            ) {
                Text("了解", color = Color(0xFF88C0FF), fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = fontFamily)
            }
        },
        title = { Text(title, fontSize = 18.sp, color = Color.White, fontFamily = fontFamily) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = message,
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    fontFamily = fontFamily
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dontShowAgain = !dontShowAgain },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF88C0FF),
                            uncheckedColor = Color.Gray,
                            checkmarkColor = Color.Black
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("次回から表示しない", color = Color.White, fontSize = 14.sp, fontFamily = fontFamily)
                }
            }
        },
        containerColor = Color(0xFF101728),
        titleContentColor = Color.White,
        shape = RoundedCornerShape(20.dp)
    )
}

