package com.example.nissingeppomusic.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.nissingeppomusic.R
import java.io.File

val LocalLauncherFontFamily = compositionLocalOf<FontFamily> { FontFamily.Default }
val LocalLauncherFontWeight = compositionLocalOf { FontWeight.Light }

fun loadCustomFontFamily(path: String?): FontFamily? {
    if (path.isNullOrBlank()) return null
    return try {
        val file = File(path)
        if (file.exists()) {
            val typeface = android.graphics.Typeface.createFromFile(file)
            FontFamily(typeface)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

val ReceiptFontFamily = FontFamily(Font(R.font.receipt_font))

fun parseFontFamily(fontChoice: String, customPath: String?): FontFamily {
    return when (fontChoice) {
        "receipt" -> ReceiptFontFamily
        "custom" -> loadCustomFontFamily(customPath) ?: FontFamily.Default
        else -> {
            if (!customPath.isNullOrBlank()) {
                loadCustomFontFamily(customPath) ?: FontFamily.Default
            } else {
                FontFamily.Default
            }
        }
    }
}

fun parseFontWeight(index: Int): FontWeight {
    return when (index) {
        0 -> FontWeight.Light
        1 -> FontWeight.Normal
        2 -> FontWeight.Medium
        3 -> FontWeight.Bold
        else -> FontWeight.Light
    }
}
