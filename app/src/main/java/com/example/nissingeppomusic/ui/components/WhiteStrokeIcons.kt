package com.example.nissingeppomusic.ui.components

// アイコンは Lucide（ISC License: https://lucide.dev）のパスデータを Canvas で描画する。
// Lucide is licensed under the ISC License. Copyright (c) 2022-present, Lucide Contributors.

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Lucideアイコンの描画データ（24x24座標系のパスと円） */
data class LucideDraw(val paths: List<String>, val circles: List<Triple<Float, Float, Float>> = emptyList())

val LucideHome = LucideDraw(listOf("M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8", "M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"))
val LucideSearch = LucideDraw(listOf("m21 21-4.34-4.34"), listOf(Triple(11f, 11f, 8f)))
val LucideSettings = LucideDraw(listOf("M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915"), listOf(Triple(12f, 12f, 3f)))
val LucideMenu = LucideDraw(listOf("M4 5h16", "M4 12h16", "M4 19h16"))
val LucideFolder = LucideDraw(listOf("M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z"))
val LucidePlus = LucideDraw(listOf("M5 12h14", "M12 5v14"))
val LucidePencil = LucideDraw(listOf("M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z", "m15 5 4 4"))
val LucideTrash = LucideDraw(listOf("M10 11v6", "M14 11v6", "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6", "M3 6h18", "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"))
val LucideCheck = LucideDraw(listOf("M20 6 9 17l-5-5"))
val LucideX = LucideDraw(listOf("M18 6 6 18", "m6 6 12 12"))
val LucidePlay = LucideDraw(listOf("M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1 5 19z"))
val LucideRefreshCcw = LucideDraw(listOf("M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5", "M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16", "M16 16h5v5"))
val LucideTimer = LucideDraw(listOf("M10 2h4", "M12 14l3-3"), listOf(Triple(12f, 14f, 8f)))
val LucideLanguages = LucideDraw(listOf("m5 8 6 6", "m4 14 6-6 2-3", "M2 5h12", "M7 2h1", "m22 22-5-10-5 10", "M14 18h6"))
val LucideSparkles = LucideDraw(listOf("M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z", "M20 2v4", "M22 4h-4"), listOf(Triple(4f, 20f, 2f)))
val LucideMaximize = LucideDraw(listOf("M8 3H5a2 2 0 0 0-2 2v3", "M21 8V5a2 2 0 0 0-2-2h-3", "M3 16v3a2 2 0 0 0 2 2h3", "M16 21h3a2 2 0 0 0 2-2v-3"))
val LucideMinimize = LucideDraw(listOf("M8 3v3a2 2 0 0 1-2 2H3", "M21 8h-3a2 2 0 0 1-2-2V3", "M3 16h3a2 2 0 0 1 2 2v3", "M16 21v-3a2 2 0 0 1 2-2h3"))
val LucideListMusic = LucideDraw(listOf("M16 5H3", "M11 12H3", "M11 19H3", "M21 16V5"), listOf(Triple(18f, 16f, 3f)))

@Composable
fun LucideIcon(icon: LucideDraw, size: Dp = 24.dp, color: Color = Color.White, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val sc = this.size.minDimension / 24f
        val stroke = Stroke(width = 2f * sc, cap = StrokeCap.Round, join = StrokeJoin.Round)
        for (d in icon.paths) drawPath(lucidePath(d, sc), color = color, style = stroke)
        for ((cx, cy, r) in icon.circles) {
            drawCircle(color = color, radius = r * sc, center = Offset(cx * sc, cy * sc), style = stroke)
        }
    }
}

@Composable
fun WhiteHomeIcon(size: Dp = 22.dp, color: Color = Color.White) {
    LucideIcon(LucideHome, size, color)
}

@Composable
fun WhiteSearchIcon(size: Dp = 22.dp, color: Color = Color.White) {
    LucideIcon(LucideSearch, size, color)
}

@Composable
fun WhiteGearIcon(size: Dp = 22.dp, color: Color = Color.White) {
    LucideIcon(LucideSettings, size, color)
}

@Composable
fun WhiteHamburgerIcon(size: Dp = 22.dp, color: Color = Color.White) {
    LucideIcon(LucideMenu, size, color)
}

@Composable
fun WhiteFolderIcon(size: Dp = 22.dp, color: Color = Color.White) {
    LucideIcon(LucideFolder, size, color)
}

@Composable
fun WhitePlusIcon(size: Dp = 22.dp, color: Color = Color.White) {
    LucideIcon(LucidePlus, size, color)
}

@Composable
fun WhiteEditIcon(size: Dp = 16.dp, color: Color = Color(0xFF88C0FF)) {
    LucideIcon(LucidePencil, size, color)
}

@Composable
fun WhiteDeleteIcon(size: Dp = 16.dp, color: Color = Color(0xFFFF5252)) {
    LucideIcon(LucideTrash, size, color)
}

@Composable
fun WhiteCheckIcon(size: Dp = 16.dp, color: Color = Color(0xFF4CAF50)) {
    LucideIcon(LucideCheck, size, color)
}

@Composable
fun WhiteCloseIcon(size: Dp = 16.dp, color: Color = Color.Gray) {
    LucideIcon(LucideX, size, color)
}

// ===== 音楽アプリ専用（アルバム/アーティスト表示用に残す） =====
fun WhiteMusicNoteIcon(size: Dp = 22.dp, color: Color = Color.White) {
    Icon(
        imageVector = Icons.Default.MusicNote,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(size)
    )
}

fun WhitePersonIcon(size: Dp = 22.dp, color: Color = Color.White) {
    Icon(
        imageVector = Icons.Default.Person,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(size)
    )
}

fun WhiteAlbumIcon(size: Dp = 22.dp, color: Color = Color.White) {
    Icon(
        imageVector = Icons.Default.Album,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(size)
    )
}
