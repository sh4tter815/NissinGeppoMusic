package com.example.nissingeppomusic.ui.components

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp


@Composable
fun DefaultLauncherDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val fontFamily = LocalLauncherFontFamily.current
    val fontWeight = LocalLauncherFontWeight.current

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    openDefaultHomeSettings(context)
                    onDismiss()
                }
            ) {
                Text("設定を開く", color = Color(0xFF88C0FF), fontSize = 16.sp, fontFamily = fontFamily, fontWeight = fontWeight)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("あとで", color = Color.Gray, fontFamily = fontFamily, fontWeight = fontWeight)
            }
        },
        title = { Text("デフォルトアプリに設定", fontSize = 18.sp, fontFamily = fontFamily, fontWeight = fontWeight) },
        text = {
            Text(
                text = "「日進月歩ランチャー」をデフォルトのホームアプリ（ホーム画面）として設定しますか？",
                color = Color.LightGray,
                fontSize = 14.sp,
                fontFamily = fontFamily,
                fontWeight = fontWeight
            )
        },
        containerColor = Color(0xFF141C2E),
        titleContentColor = Color.White
    )
}

fun openDefaultHomeSettings(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                context.startActivity(intent)
                return
            }
        }
        val intent = Intent(Settings.ACTION_HOME_SETTINGS)
        context.startActivity(intent)
    } catch (_: Exception) {
        val intent = Intent(Settings.ACTION_SETTINGS)
        context.startActivity(intent)
    }
}
