package com.example.nissingeppomusic.data

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable? = null,
    val iconBitmap: ImageBitmap? = null,
    val albumArt: Bitmap? = null,
    val artist: String = "",
    val album: String = "",
    val filePath: String = "",
    val trackNumber: Int = 0,
    val initialJapanese: String = "＃", // 「あ」「か」「さ」など
    val initialEnglish: String = "＃",   // "A", "B", "C" など
    val customYomi: String = "",
    val searchAliases: List<String> = emptyList(), // 検索可能な別名（複数保存可能）
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false
)
