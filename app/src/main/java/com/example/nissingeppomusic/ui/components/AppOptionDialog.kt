package com.example.nissingeppomusic.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.example.nissingeppomusic.data.AppInfo

@Composable
fun AppOptionDialog(
    app: AppInfo,
    onDismiss: () -> Unit,
    onToggleFavorite: (AppInfo) -> Unit,
    onSetYomi: (AppInfo, String) -> Unit,
    onAddAlias: (AppInfo, String) -> Unit,
    onRemoveAlias: (AppInfo, String) -> Unit,
    onToggleHide: (AppInfo) -> Unit
) {
    val context = LocalContext.current
    var showYomiInputDialog by remember { mutableStateOf(false) }
    var showAliasDialog by remember { mutableStateOf(false) }
    var yomiInputText by remember { mutableStateOf(app.customYomi) }
    var aliasInputText by remember { mutableStateOf("") }

    val fontFamily = LocalLauncherFontFamily.current
    val fontWeight = LocalLauncherFontWeight.current
    val textStyle = TextStyle(fontFamily = fontFamily, fontWeight = fontWeight)

    if (showYomiInputDialog) {
        AlertDialog(
            onDismissRequest = { showYomiInputDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSetYomi(app, yomiInputText)
                        showYomiInputDialog = false
                        onDismiss()
                    }
                ) {
                    Text("保存", color = Color(0xFF88C0FF), fontFamily = fontFamily, fontWeight = fontWeight)
                }
            },
            dismissButton = {
                TextButton(onClick = { showYomiInputDialog = false }) {
                    Text("キャンセル", color = Color.Gray, fontFamily = fontFamily, fontWeight = fontWeight)
                }
            },
            title = { Text("よみがな登録", fontSize = 18.sp, fontFamily = fontFamily, fontWeight = fontWeight) },
            text = {
                Column {
                    Text(
                        text = "「${app.label}」のよみがな（ひらがな・カタカナ等）を入力してください。",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        fontFamily = fontFamily,
                        fontWeight = fontWeight
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = yomiInputText,
                        onValueChange = { yomiInputText = it },
                        singleLine = true,
                        placeholder = { Text("例: らいん", color = Color.Gray, fontFamily = fontFamily, fontWeight = fontWeight) },
                        textStyle = textStyle,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                }
            },
            containerColor = Color(0xFF141C2E),
            titleContentColor = Color.White
        )
    } else if (showAliasDialog) {
        AlertDialog(
            onDismissRequest = { showAliasDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (aliasInputText.isNotBlank()) {
                            onAddAlias(app, aliasInputText)
                            aliasInputText = ""
                        }
                    }
                ) {
                    Text("追加", color = Color(0xFF88C0FF), fontFamily = fontFamily, fontWeight = fontWeight)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAliasDialog = false; onDismiss() }) {
                    Text("完了", color = Color.Gray, fontFamily = fontFamily, fontWeight = fontWeight)
                }
            },
            title = { Text("検索可能な別名を追加する", fontSize = 18.sp, fontFamily = fontFamily, fontWeight = fontWeight) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "「${app.label}」を検索する際に追加でヒットさせるキーワード（例: 計算機、電卓など）を何個でも追加登録できます。",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        fontFamily = fontFamily,
                        fontWeight = fontWeight
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = aliasInputText,
                        onValueChange = { aliasInputText = it },
                        singleLine = true,
                        placeholder = { Text("例: 計算機", color = Color.Gray, fontFamily = fontFamily, fontWeight = fontWeight) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = textStyle,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.Gray
                        )
                    )

                    if (app.searchAliases.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "登録済みの別名:",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontFamily = fontFamily,
                            fontWeight = fontWeight
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp)
                        ) {
                            items(app.searchAliases) { alias ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "・ $alias",
                                        color = Color.LightGray,
                                        fontSize = 14.sp,
                                        fontFamily = fontFamily,
                                        fontWeight = fontWeight
                                    )
                                    Text(
                                        text = "削除",
                                        color = Color(0xFFFF8888),
                                        fontSize = 13.sp,
                                        fontFamily = fontFamily,
                                        fontWeight = fontWeight,
                                        modifier = Modifier.clickable {
                                            onRemoveAlias(app, alias)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            containerColor = Color(0xFF141C2E),
            titleContentColor = Color.White
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("閉じる", color = Color.Gray, fontFamily = fontFamily, fontWeight = fontWeight)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val bmp = app.iconBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column {
                        Text(text = app.label, fontSize = 19.sp, fontFamily = fontFamily, fontWeight = fontWeight)
                        Text(
                            text = listOf(app.artist, app.album).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { app.packageName },
                            fontSize = 11.sp,
                            color = Color.Gray,
                            fontFamily = fontFamily,
                            fontWeight = fontWeight
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DialogOptionItem(
                        kind = "favorite",
                        text = if (app.isFavorite) "お気に入りを解除" else "お気に入りに登録する",
                        subtitle = if (app.isFavorite) "お気に入りから外します" else "お気に入りに登録します",
                        fontFamily = fontFamily,
                        fontWeight = fontWeight
                    ) {
                        onToggleFavorite(app)
                        onDismiss()
                    }

                    DialogOptionItem(
                        kind = "yomi",
                        text = if (app.customYomi.isNotBlank()) "よみがな変更" else "よみがな登録",
                        subtitle = if (app.customYomi.isNotBlank()) "現在: ${app.customYomi}" else "読み方を登録して並び順や検索に使います",
                        fontFamily = fontFamily,
                        fontWeight = fontWeight
                    ) {
                        showYomiInputDialog = true
                    }

                    DialogOptionItem(
                        kind = "alias",
                        text = "検索可能な別名",
                        subtitle = if (app.searchAliases.isNotEmpty()) "登録済み ${app.searchAliases.size}件" else "別の呼び方でも検索にヒットさせます",
                        fontFamily = fontFamily,
                        fontWeight = fontWeight
                    ) {
                        showAliasDialog = true
                    }

                    DialogOptionItem(
                        kind = "hide",
                        text = if (app.isHidden) "非表示を解除" else "非表示にする",
                        subtitle = if (app.isHidden) "一覧に再び表示します" else "一覧から隠します",
                        danger = true,
                        fontFamily = fontFamily,
                        fontWeight = fontWeight
                    ) {
                        onToggleHide(app)
                        onDismiss()
                    }

                    DialogOptionItem(
                        kind = "info",
                        text = "アプリ情報",
                        subtitle = app.filePath.ifBlank { app.packageName },
                        fontFamily = fontFamily,
                        fontWeight = fontWeight
                    ) {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            "package:${app.packageName}".toUri()
                        )
                        context.startActivity(intent)
                        onDismiss()
                    }
                }
            },
            containerColor = Color(0xFF141C2E),
            titleContentColor = Color.White
        )
    }
}

@Composable
private fun DialogOptionItem(
    kind: String,
    text: String,
    subtitle: String,
    danger: Boolean = false,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    fontWeight: androidx.compose.ui.text.font.FontWeight,
    onClick: () -> Unit
) {
    Surface(
        color = Color(0xFF1E283D),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OptionKindIcon(kind = kind, danger = danger)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = text,
                    color = if (danger) Color(0xFFFFB4B4) else Color.White,
                    fontSize = 15.5.sp,
                    fontFamily = fontFamily,
                    fontWeight = fontWeight
                )
                Text(
                    text = subtitle,
                    color = Color.Gray,
                    fontSize = 11.5.sp,
                    fontFamily = fontFamily,
                    fontWeight = fontWeight
                )
            }
        }
    }
}

// 長押しメニューの小さな線画アイコン（設定画面のカテゴリアイコンと同じタッチ）
@Composable
private fun OptionKindIcon(kind: String, danger: Boolean) {
    val accent = if (danger) Color(0xFFFF9E9E) else Color(0xFF88C0FF)
    Canvas(modifier = Modifier.size(30.dp)) {
        val w = size.width
        val h = size.height
        val sw = w * 0.055f
        when (kind) {
            "favorite" -> {
                val cx = w * 0.5f
                val cy = h * 0.52f
                val rO = w * 0.40f
                val rI = w * 0.17f
                val path = Path()
                for (i in 0 until 10) {
                    val r = if (i % 2 == 0) rO else rI
                    val a = Math.toRadians(-90.0 + i * 36.0)
                    val x = cx + (r * kotlin.math.cos(a)).toFloat()
                    val y = cy + (r * kotlin.math.sin(a)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, color = accent, style = Stroke(width = sw, cap = StrokeCap.Round))
            }
            "yomi" -> {
                // 鉛筆: 斜めの軸と三角の芯先、下線
                drawLine(accent, Offset(w * 0.30f, h * 0.74f), Offset(w * 0.68f, h * 0.36f), strokeWidth = sw * 1.5f, cap = StrokeCap.Round)
                drawLine(accent, Offset(w * 0.68f, h * 0.36f), Offset(w * 0.76f, h * 0.24f), strokeWidth = sw * 1.5f, cap = StrokeCap.Round)
                drawLine(accent, Offset(w * 0.76f, h * 0.24f), Offset(w * 0.38f, h * 0.82f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(accent, Offset(w * 0.16f, h * 0.88f), Offset(w * 0.84f, h * 0.88f), strokeWidth = sw, cap = StrokeCap.Round)
            }
            "alias" -> {
                drawCircle(color = accent, radius = w * 0.24f, center = Offset(w * 0.42f, h * 0.42f), style = Stroke(width = sw))
                drawLine(accent, Offset(w * 0.60f, h * 0.60f), Offset(w * 0.82f, h * 0.82f), strokeWidth = sw * 1.4f, cap = StrokeCap.Round)
            }
            "hide" -> {
                val path = Path()
                path.moveTo(w * 0.08f, h * 0.52f)
                path.cubicTo(w * 0.30f, h * 0.08f, w * 0.70f, h * 0.08f, w * 0.92f, h * 0.52f)
                path.cubicTo(w * 0.70f, h * 0.96f, w * 0.30f, h * 0.96f, w * 0.08f, h * 0.52f)
                drawPath(path, color = accent, style = Stroke(width = sw, cap = StrokeCap.Round))
                drawCircle(color = accent, radius = w * 0.09f, center = Offset(w * 0.5f, h * 0.52f), style = Stroke(width = sw))
                drawLine(accent, Offset(w * 0.18f, h * 0.86f), Offset(w * 0.82f, h * 0.18f), strokeWidth = sw * 1.3f, cap = StrokeCap.Round)
            }
            else -> {
                // info: 円と「i」
                drawCircle(color = accent, radius = w * 0.38f, center = Offset(w * 0.5f, h * 0.5f), style = Stroke(width = sw))
                drawCircle(color = accent, radius = w * 0.045f, center = Offset(w * 0.5f, h * 0.33f))
                drawLine(accent, Offset(w * 0.5f, h * 0.47f), Offset(w * 0.5f, h * 0.70f), strokeWidth = sw * 1.4f, cap = StrokeCap.Round)
            }
        }
    }
}
