package com.example.nissingeppomusic.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * ポップアップ共通部品。
 * 背景はぼかしのみ（パネルの塗りは無し）、文字は白が前提。
 * アプリ長押しポップアップと同じ見た目に全ポップアップを統一するための土台。
 */

/** ダイアログの背後をぼかす（API 31+）。Dialog の中身の先頭で1回呼ぶ。 */
@Composable
fun DialogBlurBehind() {
    val dialogView = LocalView.current
    DisposableEffect(dialogView) {
        val window = (dialogView.parent as? DialogWindowProvider)?.window
        var blurApplied = false
        if (window != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            blurApplied = runCatching {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply { blurBehindRadius = 48 }
                true
            }.getOrDefault(false)
        }
        onDispose {
            if (blurApplied && window != null) {
                runCatching {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    window.attributes = window.attributes.apply { blurBehindRadius = 0 }
                }
            }
        }
    }
}

/** ぼかし背景のダイアログ。中身は縦並び（Column）で受ける。 */
@Composable
fun BlurDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        DialogBlurBehind()
        Column(
            modifier = modifier
                .fillMaxWidth(0.94f)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            content = content
        )
    }
}

/** BlurDialog 用のタイトル（白・太字）。 */
@Composable
fun BlurDialogTitle(
    text: String,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight? = null
) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 17.sp,
        fontWeight = fontWeight ?: FontWeight.Bold,
        fontFamily = fontFamily
    )
}

/** BlurDialog 用のボタン行（右寄せ。キャンセル→確定の順）。 */
@Composable
fun BlurDialogButtonRow(
    confirmText: String,
    onConfirm: () -> Unit,
    confirmColor: Color = Color(0xFF88C0FF),
    confirmEnabled: Boolean = true,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    dismissColor: Color = Color.White.copy(alpha = 0.65f),
    dismissEnabled: Boolean = true,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dismissText != null && onDismiss != null) {
            TextButton(onClick = onDismiss, enabled = dismissEnabled) {
                Text(dismissText, color = dismissColor, fontSize = 15.sp, fontFamily = fontFamily, fontWeight = fontWeight)
            }
        }
        TextButton(onClick = onConfirm, enabled = confirmEnabled) {
            Text(confirmText, color = confirmColor, fontSize = 15.sp, fontWeight = fontWeight ?: FontWeight.Bold, fontFamily = fontFamily)
        }
    }
}
