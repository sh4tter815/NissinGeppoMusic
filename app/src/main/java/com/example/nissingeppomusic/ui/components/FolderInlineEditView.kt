package com.example.nissingeppomusic.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nissingeppomusic.data.AppInfo

@Composable
fun FolderInlineEditView(
    folderName: String,
    onFolderNameChange: (String) -> Unit,
    allApps: List<AppInfo>,
    selectedPackages: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    accentColor: Color = Color(0xFF88C0FF),
    modifier: Modifier = Modifier
) {
    var appSearchQuery by remember { mutableStateOf("") }
    val fontFamily = LocalLauncherFontFamily.current

    val filteredApps = remember(allApps, appSearchQuery) {
        if (appSearchQuery.isBlank()) {
            allApps
        } else {
            allApps.filter {
                it.label.contains(appSearchQuery, ignoreCase = true) ||
                        it.customYomi.contains(appSearchQuery, ignoreCase = true) ||
                        it.initialJapanese.contains(appSearchQuery, ignoreCase = true)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x330B101D)) // 壁紙が綺麗に透ける透過ダーク背景
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. フォルダ名入力欄
            Text(
                text = "フォルダ名",
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(Color(0x99101728), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = folderName,
                    onValueChange = onFolderNameChange,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily
                    ),
                    cursorBrush = SolidColor(accentColor),
                    modifier = Modifier.weight(1f)
                ) { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (folderName.isEmpty()) {
                            Text(
                                text = "フォルダ名を入力...",
                                color = Color.Gray.copy(alpha = 0.7f),
                                fontSize = 15.sp,
                                fontFamily = fontFamily
                            )
                        }
                        innerTextField()
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. アプリ絞り込み検索欄
            Text(
                text = "アプリの追加・削除 (選択: ${selectedPackages.size}件)",
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Color(0x66101728), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = appSearchQuery,
                    onValueChange = { appSearchQuery = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 14.sp,
                        fontFamily = fontFamily
                    ),
                    cursorBrush = SolidColor(accentColor),
                    modifier = Modifier.weight(1f)
                ) { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (appSearchQuery.isEmpty()) {
                            Text(
                                text = "アプリを絞り込み...",
                                color = Color.Gray.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                fontFamily = fontFamily
                            )
                        }
                        innerTextField()
                    }
                }
                if (appSearchQuery.isNotEmpty()) {
                    Text(
                        text = "✕",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        fontFamily = fontFamily,
                        modifier = Modifier
                            .clickable { appSearchQuery = "" }
                            .padding(4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. アプリ一覧 (アイコン, アプリ名, チェックボックス)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val isChecked = selectedPackages.contains(app.packageName)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val updated = selectedPackages.toMutableSet()
                                if (isChecked) {
                                    updated.remove(app.packageName)
                                } else {
                                    updated.add(app.packageName)
                                }
                                onSelectionChange(updated)
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                val updated = selectedPackages.toMutableSet()
                                if (checked) {
                                    updated.add(app.packageName)
                                } else {
                                    updated.remove(app.packageName)
                                }
                                onSelectionChange(updated)
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = accentColor,
                                uncheckedColor = Color.Gray,
                                checkmarkColor = Color.Black
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        val iconBitmap = app.iconBitmap
                        if (iconBitmap != null) {
                            Image(
                                bitmap = iconBitmap,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        Text(
                            text = app.label,
                            color = if (isChecked) Color.White else Color.LightGray,
                            fontSize = 15.sp,
                            fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal,
                            fontFamily = fontFamily,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
