package com.example.nissingeppomusic.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            title = { Text(text = app.label, fontSize = 20.sp, fontFamily = fontFamily, fontWeight = fontWeight) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DialogOptionItem(
                        text = if (app.isFavorite) "お気に入りを解除" else "お気に入りに登録する",
                        fontFamily = fontFamily,
                        fontWeight = fontWeight
                    ) {
                        onToggleFavorite(app)
                        onDismiss()
                    }

                    DialogOptionItem(
                        text = if (app.customYomi.isNotBlank()) "よみがな変更 (${app.customYomi})" else "よみがな登録",
                        fontFamily = fontFamily,
                        fontWeight = fontWeight
                    ) {
                        showYomiInputDialog = true
                    }

                    DialogOptionItem(
                        text = if (app.searchAliases.isNotEmpty()) "検索可能な別名 (${app.searchAliases.size}件)" else "検索可能な別名を追加する",
                        fontFamily = fontFamily,
                        fontWeight = fontWeight
                    ) {
                        showAliasDialog = true
                    }

                    DialogOptionItem(
                        text = if (app.isHidden) "非表示を解除" else "非表示にする",
                        fontFamily = fontFamily,
                        fontWeight = fontWeight
                    ) {
                        onToggleHide(app)
                        onDismiss()
                    }

                    DialogOptionItem(text = "アプリ情報", fontFamily = fontFamily, fontWeight = fontWeight) {
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
    text: String,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    fontWeight: androidx.compose.ui.text.font.FontWeight,
    onClick: () -> Unit
) {
    Surface(
        color = Color(0xFF1E283D),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 16.sp,
                fontFamily = fontFamily,
                fontWeight = fontWeight
            )
        }
    }
}
