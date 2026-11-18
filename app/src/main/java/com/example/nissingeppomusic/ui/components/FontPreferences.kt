package com.example.nissingeppomusic.ui.components

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.res.ResourcesCompat
import com.example.nissingeppomusic.R
import java.io.File

val LocalLauncherFontFamily = compositionLocalOf<FontFamily> { FontFamily.Default }

val LocalLauncherFontWeight = compositionLocalOf { FontWeight.Light }

fun loadCustomFontFamily(path: String?): FontFamily? {
    if (path.isNullOrBlank()) return null
    return try {
        val file = File(path)
        if (file.exists()) {
            val typeface = Typeface.createFromFile(file)
            FontFamily(typeface)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

val ReceiptFontFamily = FontFamily(Font(R.font.receipt_font))

val DenkiChipFontFamily = FontFamily(Font(R.font.denkichip_font))

val TsukuhouFontFamily = FontFamily(Font(R.font.tsukuhou_font))

val FreeLicenseFontFamily = FontFamily(Font(R.font.free_license))

val MontserratBoldItalicFontFamily = FontFamily(Font(R.font.montserrat_bold_italic))

val KaibunshoFontFamily = FontFamily(Font(R.font.kaibunsyo_font))

fun parseFontFamily(fontChoice: String, customPath: String?): FontFamily {
    return when (fontChoice) {
        "receipt" -> ReceiptFontFamily
        "denkichip" -> DenkiChipFontFamily
        "tsukuhou" -> TsukuhouFontFamily

        "freelicense" -> FreeLicenseFontFamily

        "montserratbi" -> MontserratBoldItalicFontFamily

        "kaibunsho" -> KaibunshoFontFamily
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

fun parseNativeTypeface(context: Context, fontChoice: String, customPath: String?): Typeface {
    return when (fontChoice) {
        "receipt" -> {
            try {
                ResourcesCompat.getFont(context, R.font.receipt_font) ?: Typeface.DEFAULT
            } catch (_: Exception) {
                Typeface.DEFAULT
            }
        }
        "denkichip" -> {
            try {
                ResourcesCompat.getFont(context, R.font.denkichip_font) ?: Typeface.DEFAULT
            } catch (_: Exception) {
                Typeface.DEFAULT
            }
        }
        "tsukuhou" -> {
            try {
                ResourcesCompat.getFont(context, R.font.tsukuhou_font) ?: Typeface.DEFAULT
            } catch (_: Exception) {
                Typeface.DEFAULT
            }
        }
        "freelicense" -> {
            try {
                ResourcesCompat.getFont(context, R.font.free_license) ?: Typeface.DEFAULT
            } catch (_: Exception) {
                Typeface.DEFAULT
            }
        }
        "montserratbi" -> {
            try {
                ResourcesCompat.getFont(context, R.font.montserrat_bold_italic) ?: Typeface.DEFAULT
            } catch (_: Exception) {
                Typeface.DEFAULT
            }
        }
        "kaibunsho" -> {
            try {
                ResourcesCompat.getFont(context, R.font.kaibunsyo_font) ?: Typeface.DEFAULT
            } catch (_: Exception) {
                Typeface.DEFAULT
            }
        }
        "custom" -> {
            if (!customPath.isNullOrBlank()) {
                try {
                    val file = File(customPath)
                    if (file.exists()) Typeface.createFromFile(file) else Typeface.DEFAULT
                } catch (_: Exception) {
                    Typeface.DEFAULT
                }
            } else Typeface.DEFAULT
        }
        else -> {
            if (!customPath.isNullOrBlank()) {
                try {
                    val file = File(customPath)
                    if (file.exists()) Typeface.createFromFile(file) else Typeface.DEFAULT
                } catch (_: Exception) {
                    Typeface.DEFAULT
                }
            } else Typeface.DEFAULT
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
