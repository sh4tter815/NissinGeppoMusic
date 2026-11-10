package com.example.nissingeppomusic.ui.components

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.nissingeppomusic.data.AppInfo

@Composable
fun FolderAppSelectDialog(
    folderName: String,
    allApps: List<AppInfo>,
    selectedPackages: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    var currentSelected by remember(selectedPackages) {
        mutableStateOf(selectedPackages.toSet())
    }

    val fontFamily = LocalLauncherFontFamily.current
    val fontWeight = LocalLauncherFontWeight.current

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(currentSelected.toList())
                    onDismiss()
                }
            ) {
                Text("保存", color = Color(0xFF88C0FF), fontSize = 16.sp, fontFamily = fontFamily, fontWeight = fontWeight)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル", color = Color.Gray, fontSize = 16.sp, fontFamily = fontFamily, fontWeight = fontWeight)
            }
        },
        title = { Text("「$folderName」のアプリ編集", fontSize = 18.sp, fontFamily = fontFamily, fontWeight = fontWeight) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "フォルダに追加するアプリを選択してください。",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    fontFamily = fontFamily,
                    fontWeight = fontWeight
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    items(allApps) { app ->
                        val isChecked = currentSelected.contains(app.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val updated = currentSelected.toMutableSet()
                                    if (isChecked) {
                                        updated.remove(app.packageName)
                                    } else {
                                        updated.add(app.packageName)
                                    }
                                    currentSelected = updated
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    val updated = currentSelected.toMutableSet()
                                    if (checked) {
                                        updated.add(app.packageName)
                                    } else {
                                        updated.remove(app.packageName)
                                    }
                                    currentSelected = updated
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF88C0FF),
                                    uncheckedColor = Color.Gray,
                                    checkmarkColor = Color.Black
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            val iconBitmap = remember(app.icon) {
                                try {
                                    app.icon?.toBitmap()?.asImageBitmap()
                                } catch (_: Exception) {
                                    null
                                }
                            }
                            if (iconBitmap != null) {
                                Image(
                                    bitmap = iconBitmap,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }

                            Text(
                                text = app.label,
                                color = if (isChecked) Color.White else Color.LightGray,
                                fontSize = 15.sp,
                                fontFamily = fontFamily,
                                fontWeight = fontWeight
                            )
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFF141C2E),
        titleContentColor = Color.White
    )
}
