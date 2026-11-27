package com.example.nissingeppomusic.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.example.nissingeppomusic.ui.components.BlurDialog
import com.example.nissingeppomusic.ui.components.BlurDialogButtonRow
import com.example.nissingeppomusic.ui.components.BlurDialogTitle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.nissingeppomusic.data.UpdateChecker
import kotlinx.coroutines.launch

/** アップデート通知ダイアログ（ダウンロードとインストール画面の表示まで面倒を見る） */
@Composable
fun UpdateDialog(info: UpdateChecker.LatestRelease, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(-1f) }
    var error by remember { mutableStateOf<String?>(null) }
    BlurDialog(onDismissRequest = { if (!downloading) onDismiss() }) {
        BlurDialogTitle("アップデートがあります")
        Spacer(Modifier.height(10.dp))
        Text("新しいビルド #${info.buildNumber} が公開されています。", color = Color.White, fontSize = 14.sp)
        if (downloading) {
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = progress.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (progress >= 0f) "ダウンロード中… ${(progress * 100).toInt()}%"
                else "ダウンロード中…",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp
            )
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        BlurDialogButtonRow(
            confirmText = "更新する",
            onConfirm = {
                if (!UpdateChecker.canInstall(context)) {
                    UpdateChecker.openInstallPermissionSettings(context)
                    error = "このアプリからのインストールを許可してから、もう一度「更新する」を押してください"
                } else {
                    downloading = true
                    error = null
                    scope.launch {
                        val file = UpdateChecker.downloadApk(context, info) { progress = it }
                        downloading = false
                        if (file != null) {
                            UpdateChecker.installApk(context, file)
                            onDismiss()
                        } else {
                            error = "ダウンロードに失敗しました"
                        }
                    }
                }
            },
            confirmEnabled = !downloading,
            dismissText = "あとで",
            onDismiss = onDismiss,
            dismissEnabled = !downloading
        )
    }
}
