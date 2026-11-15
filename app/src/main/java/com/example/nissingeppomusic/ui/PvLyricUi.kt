package com.example.nissingeppomusic.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.nissingeppomusic.data.AppInfo
import com.example.nissingeppomusic.data.AppRepository
import com.example.nissingeppomusic.data.LyricLine
import com.example.nissingeppomusic.data.LyricsManager
import com.example.nissingeppomusic.data.MusicPlayerManager
import com.example.nissingeppomusic.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.example.nissingeppomusic.ui.components.parseFontFamily
import kotlin.math.roundToInt
import androidx.core.graphics.toColorInt
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.key
import com.example.nissingeppomusic.ui.components.LiquidGradientBackground
import kotlin.math.floor
import kotlin.math.ceil
import androidx.compose.animation.core.exponentialDecay

// 歌詞PVの描画まわり一式（文字PV・間奏アート・アイコン描画・自動フィット・設定のプレビューカード）。
// 正本はこの音楽アプリ側。ランチャー側にも同じ内容のファイルがあるので、直したらランチャー側へもコピーすること。

/** 白い枠線だけの小さな線画アイコンを描く（キャンバスは正方形前提） */
fun DrawScope.drawLyricIcon(kind: Int) {
    val col = Color.White.copy(alpha = 0.92f)
    val u = size.width / 100f
    val cx = size.width / 2f
    val cy = size.height / 2f
    val st = Stroke(width = 2.3f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun pt(dx: Float, dy: Float) = Offset(cx + dx * u, cy + dy * u)
    when (kind) {
        LyricIcon.STAR -> {
            val path = Path()
            for (i in 0..9) {
                val ang = Math.toRadians(-90.0 + i * 36.0)
                val r = if (i % 2 == 0) 34.0 else 15.0
                val x = cx + (r * kotlin.math.cos(ang)).toFloat() * u
                val y = cy + (r * kotlin.math.sin(ang)).toFloat() * u
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, col, style = st)
        }
        LyricIcon.HEART -> {
            val path = Path().apply {
                moveTo(cx, cy + 30f * u)
                cubicTo(cx - 44f * u, cy + 2f * u, cx - 28f * u, cy - 32f * u, cx, cy - 13f * u)
                cubicTo(cx + 28f * u, cy - 32f * u, cx + 44f * u, cy + 2f * u, cx, cy + 30f * u)
                close()
            }
            drawPath(path, col, style = st)
        }
        LyricIcon.MOON -> {
            // 三日月: 左に膨らんだ外周と、右から食い込む内周（中心基準で描く）
            val path = Path().apply {
                moveTo(cx + 12f * u, cy - 24f * u)
                cubicTo(cx - 26f * u, cy - 13f * u, cx - 26f * u, cy + 13f * u, cx + 12f * u, cy + 24f * u)
                cubicTo(cx - 3f * u, cy + 12f * u, cx - 3f * u, cy - 12f * u, cx + 12f * u, cy - 24f * u)
                close()
            }
            drawPath(path, col, style = st)
        }
        LyricIcon.SUN -> {
            drawCircle(col, radius = 15f * u, center = pt(0f, 0f), style = st)
            for (i in 0 until 8) {
                val ang = Math.toRadians(i * 45.0)
                val c = kotlin.math.cos(ang).toFloat(); val sn = kotlin.math.sin(ang).toFloat()
                drawLine(col, pt(c * 23f, sn * 23f), pt(c * 31f, sn * 31f), strokeWidth = 3.0f * u, cap = StrokeCap.Round)
            }
        }
        LyricIcon.CLOUD, LyricIcon.RAIN -> {
            val oy = if (kind == LyricIcon.RAIN) -8f else 0f
            drawLine(col, pt(-26f, 16f + oy), pt(26f, 16f + oy), strokeWidth = 3.0f * u, cap = StrokeCap.Round)
            drawArc(col, topLeft = pt(-28f, -8f + oy), size = Size(30f * u, 24f * u), startAngle = 180f, sweepAngle = 180f, useCenter = false, style = st)
            drawArc(col, topLeft = pt(-4f, -18f + oy), size = Size(34f * u, 34f * u), startAngle = 180f, sweepAngle = 180f, useCenter = false, style = st)
            drawArc(col, topLeft = pt(12f, -6f + oy), size = Size(26f * u, 22f * u), startAngle = 180f, sweepAngle = 180f, useCenter = false, style = st)
            drawLine(col, pt(-26f, 4f + oy), pt(-26f, 16f + oy), strokeWidth = 3.0f * u, cap = StrokeCap.Round)
            drawLine(col, pt(26f, 4f + oy), pt(26f, 16f + oy), strokeWidth = 3.0f * u, cap = StrokeCap.Round)
            if (kind == LyricIcon.RAIN) {
                drawLine(col, pt(-16f, 26f), pt(-20f, 36f), strokeWidth = 3.0f * u, cap = StrokeCap.Round)
                drawLine(col, pt(0f, 26f), pt(-4f, 36f), strokeWidth = 3.0f * u, cap = StrokeCap.Round)
                drawLine(col, pt(16f, 26f), pt(12f, 36f), strokeWidth = 3.0f * u, cap = StrokeCap.Round)
            }
        }
        LyricIcon.SNOW -> {
            for (i in 0 until 3) {
                val ang = Math.toRadians(i * 60.0)
                val c = kotlin.math.cos(ang).toFloat(); val sn = kotlin.math.sin(ang).toFloat()
                drawLine(col, pt(-c * 30f, -sn * 30f), pt(c * 30f, sn * 30f), strokeWidth = 2.8f * u, cap = StrokeCap.Round)
            }
            drawCircle(col, radius = 5f * u, center = pt(0f, 0f), style = st)
        }
        LyricIcon.UMBRELLA -> {
            drawArc(col, topLeft = pt(-32f, -28f), size = Size(64f * u, 54f * u), startAngle = 180f, sweepAngle = 180f, useCenter = false, style = st)
            drawLine(col, pt(-32f, -1f), pt(-11f, -1f), strokeWidth = 2.8f * u, cap = StrokeCap.Round)
            drawLine(col, pt(-11f, -1f), pt(11f, -1f), strokeWidth = 2.8f * u, cap = StrokeCap.Round)
            drawLine(col, pt(11f, -1f), pt(32f, -1f), strokeWidth = 2.8f * u, cap = StrokeCap.Round)
            drawLine(col, pt(0f, -28f), pt(0f, 20f), strokeWidth = 3.0f * u, cap = StrokeCap.Round)
            drawArc(col, topLeft = pt(0f, 10f), size = Size(18f * u, 18f * u), startAngle = 0f, sweepAngle = 180f, useCenter = false, style = st)
        }
        LyricIcon.FLOWER -> {
            for (i in 0 until 5) {
                val ang = Math.toRadians(-90.0 + i * 72.0)
                val px = (15.0 * kotlin.math.cos(ang)).toFloat(); val py = (15.0 * kotlin.math.sin(ang)).toFloat()
                drawCircle(col, radius = 10.5f * u, center = pt(px, py), style = st)
            }
            drawCircle(col, radius = 5.5f * u, center = pt(0f, 0f), style = st)
        }
        LyricIcon.WAVES -> {
            val path = Path()
            for (yOff in listOf(-6f, 12f)) {
                path.moveTo(cx - 32f * u, cy + yOff * u)
                path.quadraticBezierTo(cx - 16f * u, cy + (yOff - 15f) * u, cx, cy + yOff * u)
                path.quadraticBezierTo(cx + 16f * u, cy + (yOff + 15f) * u, cx + 32f * u, cy + yOff * u)
            }
            drawPath(path, col, style = st)
        }
        LyricIcon.KEY -> {
            drawCircle(col, radius = 13f * u, center = pt(-13f, -7f), style = st)
            drawLine(col, pt(-4f, 2f), pt(25f, 29f), strokeWidth = 3.3f * u, cap = StrokeCap.Round)
            drawLine(col, pt(15f, 19f), pt(23f, 11f), strokeWidth = 3.0f * u, cap = StrokeCap.Round)
            drawLine(col, pt(21f, 25f), pt(28f, 18f), strokeWidth = 3.0f * u, cap = StrokeCap.Round)
        }
        LyricIcon.NOTE -> {
            drawOval(col, topLeft = pt(-26f, 10f), size = Size(17f * u, 13f * u), style = st)
            drawOval(col, topLeft = pt(9f, 4f), size = Size(17f * u, 13f * u), style = st)
            drawLine(col, pt(-9f, 13f), pt(-9f, -22f), strokeWidth = 3.0f * u, cap = StrokeCap.Round)
            drawLine(col, pt(26f, 7f), pt(26f, -28f), strokeWidth = 3.0f * u, cap = StrokeCap.Round)
            drawLine(col, pt(-9f, -22f), pt(26f, -28f), strokeWidth = 5.0f * u, cap = StrokeCap.Round)
        }
        LyricIcon.ENVELOPE -> {
            drawRoundRect(col, topLeft = pt(-30f, -18f), size = Size(60f * u, 36f * u), cornerRadius = CornerRadius(4f * u, 4f * u), style = st)
            drawLine(col, pt(-29f, -17f), pt(0f, 5f), strokeWidth = 2.8f * u, cap = StrokeCap.Round)
            drawLine(col, pt(29f, -17f), pt(0f, 5f), strokeWidth = 2.8f * u, cap = StrokeCap.Round)
        }
        LyricIcon.CLOCK -> {
            drawCircle(col, radius = 28f * u, center = pt(0f, 0f), style = st)
            drawLine(col, pt(0f, 0f), pt(0f, -15f), strokeWidth = 3.0f * u, cap = StrokeCap.Round)
            drawLine(col, pt(0f, 0f), pt(11f, 6f), strokeWidth = 3.0f * u, cap = StrokeCap.Round)
        }
        LyricIcon.FLAME -> {
            val path = Path().apply {
                moveTo(cx, cy - 32f * u)
                cubicTo(cx + 20f * u, cy - 8f * u, cx + 22f * u, cy + 6f * u, cx + 14f * u, cy + 18f * u)
                cubicTo(cx + 8f * u, cy + 29f * u, cx - 8f * u, cy + 29f * u, cx - 14f * u, cy + 18f * u)
                cubicTo(cx - 22f * u, cy + 6f * u, cx - 13f * u, cy - 10f * u, cx, cy - 32f * u)
                close()
            }
            drawPath(path, col, style = st)
        }
        LyricIcon.DROP -> {
            val path = Path().apply {
                moveTo(cx, cy - 30f * u)
                cubicTo(cx + 17f * u, cy - 2f * u, cx + 20f * u, cy + 8f * u, cx + 20f * u, cy + 14f * u)
                cubicTo(cx + 20f * u, cy + 27f * u, cx - 20f * u, cy + 27f * u, cx - 20f * u, cy + 14f * u)
                cubicTo(cx - 20f * u, cy + 8f * u, cx - 17f * u, cy - 2f * u, cx, cy - 30f * u)
                close()
            }
            drawPath(path, col, style = st)
        }
        LyricIcon.RAINBOW -> {
            drawArc(col, topLeft = pt(-32f, -10f), size = Size(64f * u, 64f * u), startAngle = 180f, sweepAngle = 180f, useCenter = false, style = st)
            drawArc(col, topLeft = pt(-23f, -1f), size = Size(46f * u, 46f * u), startAngle = 180f, sweepAngle = 180f, useCenter = false, style = st)
            drawArc(col, topLeft = pt(-14f, 8f), size = Size(28f * u, 28f * u), startAngle = 180f, sweepAngle = 180f, useCenter = false, style = st)
        }
        LyricIcon.LIGHTNING -> {
            val path = Path().apply {
                moveTo(cx + 7f * u, cy - 31f * u)
                lineTo(cx - 14f * u, cy + 6f * u)
                lineTo(cx - 1f * u, cy + 6f * u)
                lineTo(cx - 7f * u, cy + 31f * u)
                lineTo(cx + 16f * u, cy - 8f * u)
                lineTo(cx + 3f * u, cy - 8f * u)
                close()
            }
            drawPath(path, col, style = st)
        }
        LyricIcon.SMILE -> {
            drawCircle(col, radius = 27f * u, center = pt(0f, 0f), style = st)
            drawCircle(col, radius = 2.8f * u, center = pt(-11f, -6f))
            drawCircle(col, radius = 2.8f * u, center = pt(11f, -6f))
            drawArc(col, topLeft = pt(-14f, -3f), size = Size(28f * u, 22f * u), startAngle = 25f, sweepAngle = 130f, useCenter = false, style = st)
        }
        LyricIcon.FISH -> {
            drawOval(col, topLeft = pt(-29f, -11f), size = Size(45f * u, 22f * u), style = st)
            val path = Path().apply {
                moveTo(cx + 13f * u, cy)
                lineTo(cx + 30f * u, cy - 12f * u)
                lineTo(cx + 30f * u, cy + 12f * u)
                close()
            }
            drawPath(path, col, style = st)
            drawCircle(col, radius = 2.6f * u, center = pt(-17f, -3f))
        }
        LyricIcon.MOUNTAIN -> {
            val path = Path().apply {
                moveTo(cx - 32f * u, cy + 22f * u)
                lineTo(cx - 8f * u, cy - 16f * u)
                lineTo(cx + 9f * u, cy + 22f * u)
                moveTo(cx - 3f * u, cy + 22f * u)
                lineTo(cx + 17f * u, cy - 5f * u)
                lineTo(cx + 32f * u, cy + 22f * u)
            }
            drawPath(path, col, style = st)
        }
        LyricIcon.LINE -> {
            // 「線」そのもの: 少し斜めの線を1本引く
            drawLine(col, pt(-30f, 8f), pt(30f, -10f), strokeWidth = 3.3f * u, cap = StrokeCap.Round)
        }
        LyricIcon.CIRCLE -> {
            // 「円」そのもの: 円を描く
            drawCircle(col, radius = 26f * u, center = pt(0f, 0f), style = st)
        }
        LyricIcon.COFFIN -> {
            // 棺: 頭側は狭く、肩で広がって足へ絞る六角形＋十字（中心基準で描く）
            val path = Path().apply {
                moveTo(cx - 10f * u, cy - 30f * u)
                lineTo(cx + 10f * u, cy - 30f * u)
                lineTo(cx + 24f * u, cy - 8f * u)
                lineTo(cx + 12f * u, cy + 30f * u)
                lineTo(cx - 12f * u, cy + 30f * u)
                lineTo(cx - 24f * u, cy - 8f * u)
                close()
            }
            drawPath(path, col, style = st)
            drawLine(col, pt(0f, -21f), pt(0f, -3f), strokeWidth = 2.6f * u, cap = StrokeCap.Round)
            drawLine(col, pt(-6f, -15f), pt(6f, -15f), strokeWidth = 2.6f * u, cap = StrokeCap.Round)
        }
        LyricIcon.BANDAGE -> {
            // 絆創膏: 斜めの帯＋中央のパッド＋穴2つ
            val cosr = 0.906f; val sinr = -0.423f
            fun rp(dx: Float, dy: Float) = Offset(cx + (dx * cosr - dy * sinr) * u, cy + (dx * sinr + dy * cosr) * u)
            val band = Path().apply {
                val a = rp(-34f, -12f); val b = rp(34f, -12f); val c = rp(34f, 12f); val d = rp(-34f, 12f)
                moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
            }
            drawPath(band, col, style = st)
            val pad = Path().apply {
                val a = rp(-9f, -12f); val b = rp(9f, -12f); val c = rp(9f, 12f); val d = rp(-9f, 12f)
                moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
            }
            drawPath(pad, col.copy(alpha = 0.35f))
            drawCircle(col, radius = 2.2f * u, center = rp(-22f, 0f))
            drawCircle(col, radius = 2.2f * u, center = rp(22f, 0f))
        }
        LyricIcon.CROSS -> {
            // バツ印
            drawLine(col, pt(-15f, -15f), pt(15f, 15f), strokeWidth = 3.9f * u, cap = StrokeCap.Round)
            drawLine(col, pt(15f, -15f), pt(-15f, 15f), strokeWidth = 3.9f * u, cap = StrokeCap.Round)
        }
        LyricIcon.EYE -> {
            // 目: アーモンド型の輪郭に虹彩（中心基準で描く）
            val path = Path().apply {
                moveTo(cx - 30f * u, cy)
                quadraticTo(cx, cy - 38f * u, cx + 30f * u, cy)
                quadraticTo(cx, cy + 38f * u, cx - 30f * u, cy)
                close()
            }
            drawPath(path, col, style = st)
            drawCircle(col, radius = 9f * u, center = pt(0f, 0f), style = st)
            drawCircle(col, radius = 3f * u, center = pt(0f, 0f))
        }
        LyricIcon.PLUS -> {
            drawLine(col, pt(-17f, 0f), pt(17f, 0f), strokeWidth = 3.9f * u, cap = StrokeCap.Round)
            drawLine(col, pt(0f, -17f), pt(0f, 17f), strokeWidth = 3.9f * u, cap = StrokeCap.Round)
        }
        LyricIcon.MINUS -> {
            drawLine(col, pt(-17f, 0f), pt(17f, 0f), strokeWidth = 3.9f * u, cap = StrokeCap.Round)
        }
        LyricIcon.MAP -> {
            // 折れた地図: 三つ折りの面と折り目（中心基準で描く）
            val path = Path().apply {
                moveTo(cx - 28f * u, cy - 17f * u)
                lineTo(cx - 10f * u, cy - 23f * u)
                lineTo(cx + 10f * u, cy - 17f * u)
                lineTo(cx + 28f * u, cy - 23f * u)
                lineTo(cx + 28f * u, cy + 17f * u)
                lineTo(cx + 10f * u, cy + 23f * u)
                lineTo(cx - 10f * u, cy + 17f * u)
                lineTo(cx - 28f * u, cy + 23f * u)
                close()
            }
            drawPath(path, col, style = st)
            drawLine(col, pt(-10f, -23f), pt(-10f, 17f), strokeWidth = 2.2f * u, cap = StrokeCap.Round)
            drawLine(col, pt(10f, -17f), pt(10f, 23f), strokeWidth = 2.2f * u, cap = StrokeCap.Round)
        }
        LyricIcon.BONE -> {
            // 骨: 斜めの軸の両端にこぶ
            drawLine(col, pt(-14f, 14f), pt(14f, -14f), strokeWidth = 5.0f * u, cap = StrokeCap.Round)
            drawCircle(col, radius = 7.5f * u, center = pt(-19f, 11f), style = st)
            drawCircle(col, radius = 7.5f * u, center = pt(-11f, 19f), style = st)
            drawCircle(col, radius = 7.5f * u, center = pt(11f, -19f), style = st)
            drawCircle(col, radius = 7.5f * u, center = pt(19f, -11f), style = st)
        }
        LyricIcon.GLOBE -> {
            // 地球儀: 円に経線と緯線
            drawCircle(col, radius = 27f * u, center = pt(0f, 0f), style = st)
            drawOval(col, topLeft = pt(-13f, -27f), size = Size(26f * u, 54f * u), style = st)
            drawOval(col, topLeft = pt(-27f, -10f), size = Size(54f * u, 20f * u), style = st)
            drawLine(col, pt(0f, -27f), pt(0f, 27f), strokeWidth = 2.2f * u, cap = StrokeCap.Round)
        }
        LyricIcon.BUBBLE -> {
            // 夢の吹き出し: 角丸の四角にしっぽ
            drawRoundRect(col, topLeft = pt(-28f, -25f), size = Size(56f * u, 40f * u), cornerRadius = CornerRadius(11f * u, 11f * u), style = st)
            drawLine(col, pt(-12f, 14f), pt(-19f, 30f), strokeWidth = 2.8f * u, cap = StrokeCap.Round)
            drawLine(col, pt(3f, 14f), pt(-19f, 30f), strokeWidth = 2.8f * u, cap = StrokeCap.Round)
        }
        LyricIcon.WIND -> {
            val path = Path().apply {
                moveTo(cx - 30f * u, cy - 13f * u)
                lineTo(cx + 6f * u, cy - 13f * u)
                quadraticBezierTo(cx + 27f * u, cy - 13f * u, cx + 25f * u, cy - 24f * u)
                quadraticBezierTo(cx + 23f * u, cy - 32f * u, cx + 15f * u, cy - 28f * u)
                moveTo(cx - 30f * u, cy + 2f * u)
                lineTo(cx + 14f * u, cy + 2f * u)
                quadraticBezierTo(cx + 31f * u, cy + 2f * u, cx + 29f * u, cy - 7f * u)
                quadraticBezierTo(cx + 27f * u, cy - 14f * u, cx + 20f * u, cy - 11f * u)
                moveTo(cx - 30f * u, cy + 17f * u)
                lineTo(cx + 2f * u, cy + 17f * u)
            }
            drawPath(path, col, style = st)
        }
    }
}

/** 4方向に尖ったキラキラ（スパークル）を塗りで描く */
fun DrawScope.drawSparkle(cx: Float, cy: Float, r: Float, color: Color, alpha: Float) {
    val path = Path().apply {
        moveTo(cx, cy - r)
        quadraticBezierTo(cx, cy, cx + r, cy)
        quadraticBezierTo(cx, cy, cx, cy + r)
        quadraticBezierTo(cx, cy, cx - r, cy)
        quadraticBezierTo(cx, cy, cx, cy - r)
        close()
    }
    drawPath(path, color.copy(alpha = alpha))
}

/** 歌詞の単語に反応する全画面エフェクト（0=雨、1=雪、2=桜）。PVレイヤーの上に重ねる */
@Composable
fun PvAmbientEffect(kind: Int, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pvambient")
    val duration = when (kind) { 0 -> 1100; 1 -> 9000; else -> 12000 }
    val t by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(duration, easing = LinearEasing), RepeatMode.Restart),
        label = "pvambientT"
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        fun frac(v: Float) = v - floor(v)
        when (kind) {
            0 -> {
                // 雨: 細い線がたくさん斜めに落ちてくる
                val col = Color.White.copy(alpha = 0.30f)
                for (i in 0 until 46) {
                    val speed = 0.85f + frac(i * 0.371f) * 0.8f
                    val x = frac(i * 0.618034f) * w + frac(i * 0.113f) * 30f
                    val y = frac(frac(i * 0.529f) + t * speed) * (h + 160f) - 80f
                    val len = 46f + frac(i * 0.77f) * 54f
                    drawLine(col, Offset(x, y), Offset(x - len * 0.16f, y + len), strokeWidth = 2.4f, cap = StrokeCap.Round)
                }
            }
            1 -> {
                // 雪: 白い点がゆっくり舞い落ちる
                val col = Color.White.copy(alpha = 0.55f)
                for (i in 0 until 30) {
                    val speed = 0.55f + frac(i * 0.371f) * 0.6f
                    val x = frac(i * 0.618034f) * w + sin(t * 6.2832f + i * 1.7f) * 16f
                    val y = frac(frac(i * 0.529f) + t * speed) * (h + 80f) - 40f
                    drawCircle(col, radius = 2.2f + frac(i * 0.77f) * 3.4f, center = Offset(x, y))
                }
            }
            else -> {
                // 桜: 花びらがひらひら回りながら落ちてくる
                val col = Color(0xFFF7B7C8)
                for (i in 0 until 14) {
                    val speed = 0.5f + frac(i * 0.371f) * 0.55f
                    val x = frac(i * 0.618034f) * w + sin(t * 6.2832f + i * 2.3f) * 34f
                    val y = frac(frac(i * 0.529f) + t * speed) * (h + 120f) - 60f
                    val petal = 9f + frac(i * 0.77f) * 7f
                    val rot = t * 360f * (if (i % 2 == 0) 1f else -1f) + i * 47f
                    val rad = Math.toRadians(rot.toDouble())
                    val cs = kotlin.math.cos(rad).toFloat()
                    val sn = kotlin.math.sin(rad).toFloat()
                    fun rp(lx: Float, ly: Float) = Offset(x + lx * cs - ly * sn, y + lx * sn + ly * cs)
                    val p0 = rp(0f, -petal)
                    val p1 = rp(petal * 0.95f, -petal * 0.15f)
                    val p2 = rp(0f, petal)
                    val p3 = rp(-petal * 0.95f, -petal * 0.15f)
                    val path = Path().apply {
                        moveTo(p0.x, p0.y)
                        quadraticBezierTo(p1.x, p1.y, p2.x, p2.y)
                        quadraticBezierTo(p3.x, p3.y, p0.x, p0.y)
                        close()
                    }
                    drawPath(path, col.copy(alpha = 0.8f))
                }
            }
        }
    }
}

/** アイコン対象から外す複合語（「心配」の「心」でハートが出ないように） */
/** 折り返さずに1行で収まる最大の文字サイズを返す（なるべく大きく、収まらなければ下げる） */
fun fitFontSizeSp(text: String, baseSp: Float, availWidthDp: Float): Float {
    if (text.isEmpty() || availWidthDp <= 0f) return baseSp
    var em = 0f
    for (ch in text) {
        em += when {
            ch == ' ' || ch == '　' -> 0.30f
            ch.code < 0x300 -> 0.55f
            else -> 1.02f
        }
    }
    if (em <= 0f) return baseSp
    return minOf(baseSp, availWidthDp * 0.95f / em).coerceAtLeast(8f)
}

/** コーラスの掛け声（yeah / ah / ラララ等）かどうか */
/** 間奏中に出す、曲線がいろんな方向に交差するビジュアルアート */
@Composable
fun PvInterludeArt(accentColor: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pvart")
    val t by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart),
        label = "pvartT"
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val ph = t * 6.2832f
        for (i in 0 until 7) {
            val seed = i * 1.618f
            val yBase = h * (0.16f + 0.12f * i)
            val amp = h * (0.16f + 0.05f * (i % 3))
            val colr = if (i % 3 == 0) Color.White.copy(alpha = 0.30f) else accentColor.copy(alpha = 0.38f)
            val path = Path().apply {
                moveTo(-w * 0.05f, yBase + sin(ph + seed) * h * 0.05f)
                cubicTo(
                    w * 0.30f, yBase - amp * sin(ph * 0.9f + seed * 2f),
                    w * 0.65f, yBase + amp * kotlin.math.cos(ph * 0.8f + seed),
                    w * 1.05f, yBase - sin(ph + seed * 3f) * h * 0.06f
                )
            }
            drawPath(path, colr, style = Stroke(width = 2.2f + (i % 3) * 0.9f, cap = StrokeCap.Round))
        }
        for (i in 0 until 3) {
            val cxp = w * (0.25f + 0.25f * i) + sin(ph * 0.7f + i * 2.1f) * w * 0.03f
            val cyp = h * (0.35f + 0.15f * i) + kotlin.math.cos(ph * 0.6f + i * 1.3f) * h * 0.05f
            drawCircle(
                color = Color.White.copy(alpha = 0.22f),
                radius = h * (0.10f + 0.04f * i) * (0.9f + 0.1f * sin(ph + i)),
                center = Offset(cxp, cyp),
                style = Stroke(width = 1.8f)
            )
        }
    }
}

/** PV入場の8方向（上・右上・右・右下・下・左下・左・左上） */
/** PVでカタカナ語の下に小さく添える英単語（歌詞でよく出る語だけの内蔵対応表） */
@Composable
fun PvLyricLine(
    text: String,
    baseSeed: Int,
    fontFamily: FontFamily?,
    accentColor: Color,
    effectKind: Int = 0,
    bold: Boolean = true,
    bigText: Boolean = false,
    lineDurationMs: Long = Long.MAX_VALUE,
    screenWidthDp: Float,
    exiting: Boolean,
    modifier: Modifier = Modifier
) {
    val pvEaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
    // 改行位置は塊（文節・英単語）の境界で決める。描画とアニメーションは文字ごと（英単語だけ単語ごと）
    val iconWord = findIconWord(text)
    val chunks = remember(text) { splitLyricUnits(text) }
    if (chunks.isEmpty()) return
    fun isLatinWord(c: String): Boolean = c.isNotEmpty() && c.all { it.code < 0x300 }
    fun charEm(ch: Char): Float = when {
        ch in "ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮヵヶ" -> 0.6f
        ch in '一'..'鿿' || ch == '々' || ch == '〆' || ch == 'ヶ' -> 1.25f
        ch in 'ぁ'..'ゟ' -> 0.82f
        ch in '゠'..'ヿ' -> 1.05f
        ch.code < 0x300 -> 0.6f
        else -> 0.98f
    }
    fun chunkEm(c: String): Float = c.fold(0f) { acc, ch -> acc + charEm(ch) * 1.02f }
    val latinDominant = text.count { it.code in 0x41..0x7A } > text.count { it.code >= 0x300 }
    val gapEm = if (latinDominant) 0.32f else 0f
    // 基本2行構成: 塊の境界の中で、左右のemが一番均等になる位置で割る
    // ただし「」や（）で囲まれた部分は丸ごと1行で表示する（閉じ括弧がこの行に無い場合は通常扱い）
    val totalChars = text.count { it != ' ' && it != '　' }
    fun splitChunks(list: List<String>): List<List<String>> {
        if (list.size >= 2 && list.sumOf { it.count { ch -> ch != ' ' && ch != '　' } } >= 6) {
            val totalEm = list.sumOf { chunkEm(it).toDouble() }.toFloat() + gapEm * (list.size - 1)
            var acc = 0f
            var best = 1
            var bestDiff = Float.MAX_VALUE
            for (idx in 1 until list.size) {
                acc += chunkEm(list[idx - 1]) + gapEm
                val diff = kotlin.math.abs(totalEm / 2f - acc)
                if (diff < bestDiff) { bestDiff = diff; best = idx }
            }
            return listOf(list.subList(0, best), list.subList(best, list.size))
        }
        return listOf(list)
    }
    // 括弧セグメントの検出（開き括弧から閉じ括弧までを1つの塊にする）
    val segs = mutableListOf<Pair<Boolean, List<String>>>()
    var bracketBuf = mutableListOf<String>()
    var inBracket = false
    for (c in chunks) {
        if (!inBracket && (c.contains("（") || c.contains("(") || c.contains("「"))) {
            inBracket = true
            bracketBuf = mutableListOf(c)
            if (c.contains("）") || c.contains(")") || c.contains("」")) {
                segs.add(true to bracketBuf.toList())
                inBracket = false
            }
        } else if (inBracket) {
            bracketBuf.add(c)
            if (c.contains("）") || c.contains(")") || c.contains("」")) {
                segs.add(true to bracketBuf.toList())
                inBracket = false
            }
        } else {
            segs.add(false to listOf(c))
        }
    }
    if (inBracket) {
        // 閉じ括弧がこの行に無い → 括弧扱いをやめて通常の塊として並べ直す
        for (c in bracketBuf) segs.add(false to listOf(c))
    }
    val rowsBuilt = mutableListOf<List<String>>()
    var runBuf = mutableListOf<String>()
    fun flushRun() {
        if (runBuf.isNotEmpty()) {
            rowsBuilt.addAll(splitChunks(runBuf.toList()))
            runBuf = mutableListOf()
        }
    }
    for ((isBracket, segChunks) in segs) {
        if (isBracket) {
            flushRun()
            rowsBuilt.add(segChunks)
        } else {
            runBuf.addAll(segChunks)
        }
    }
    flushRun()
    // 改行は最大3行まで。括弧を1行にすると3行を超える場合は、括弧ルールを無視して通常分割にする
    val rows: List<List<String>> = when {
        rowsBuilt.isEmpty() -> listOf(chunks)
        rowsBuilt.size > 3 -> splitChunks(chunks)
        else -> rowsBuilt
    }
    fun rowEm(row: List<String>): Float = row.sumOf { chunkEm(it).toDouble() }.toFloat() + gapEm * (row.size - 1).coerceAtLeast(0)
    val longestRowEm = rows.maxOf { rowEm(it) }.coerceAtLeast(1f)
    // 行ごとの大きさを均等に: どの行も「枠いっぱいに広がるサイズ」を基本にし、短い行だけ高さ上限で止める
    val heightCapBase = when {
        rows.size >= 3 -> 40f
        rows.size == 2 -> 52f
        else -> 64f
    }
    val heightCap = if (bigText) heightCapBase * 1.8f else heightCapBase
    val fitSize = minOf((screenWidthDp - 8f) / (longestRowEm * 1.06f), heightCap)
    val lineOffsetX = ((baseSeed / 7) % 17 - 8).dp
    val lineOffsetY = ((baseSeed / 11) % 13 - 6).dp
    val row2Indent = ((baseSeed / 13) % 4 * 14).dp
    // 退場は1本の時計（exitProgress）で全文字を駆動する（位相だけ文字ごとにずらす）
    val exitProgress = remember { Animatable(0f) }
    // 「落」を含む行は、退場のときに文字をバラさず行ごと画面下へ落下させる
    val fallLine = text.contains("落")
    // 単語で発火する入場・退場エフェクトの対象判定
    val negLine = text.contains("なかった") || text.contains("無い") || text.contains("ない") || text.contains("なくて") || text.contains("なくな") || text.contains("じゃない")
    val mosaicLine = text.contains("隠") || text.contains("モザイク")
    val sandLine = text.contains("砂") || text.contains("消")
    val rippleWord = text.contains("響")
    if (exiting) {
        LaunchedEffect(Unit) {
            exitProgress.animateTo(1f, animationSpec = tween(durationMillis = 720, easing = LinearEasing))
        }
    }
    // 行の表示時間が極端に短いときは、装飾エフェクトを出す暇がないので歌詞の表示を優先する
    val effKind = when {
        lineDurationMs < 1400L -> 0
        text.contains("！") || text.contains("!") -> 4
        text.contains("円") -> 1
        text.contains("四角形") -> 10
        text.contains("三角形") -> 9
        text.contains("線") -> 8
        else -> effectKind
    }
    // 装飾エフェクト（親から渡される。前の行と被らないランダム）:
    // 0=下線スイープ / 1=波紋 / 2=白スワイプ / 3=点滅 / 4=グリッチ / 5=枠線 / 6=粒子バースト / 7=波打ち登場
    val barAnim = remember { Animatable(0f) }
    val rippleAnim = remember { Animatable(0f) }
    val fxAnim = remember { Animatable(0f) }
    if (!exiting) {
        LaunchedEffect(Unit) {
            barAnim.animateTo(1f, animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing))
            delay(60)
            barAnim.animateTo(2f, animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing))
            rippleAnim.animateTo(1f, animationSpec = tween(durationMillis = 1150, easing = LinearEasing))
        }
        LaunchedEffect(Unit) {
            fxAnim.animateTo(1f, animationSpec = tween(durationMillis = 1000, easing = LinearEasing))
        }
    }
    // 下線: 入場と同時に、左から右へ伸びて（イーズアウト）、すぐに左から右へ消える（イーズイン）
    val underline = remember { Animatable(0f) }
    if (!exiting) {
        LaunchedEffect(Unit) {
            underline.animateTo(1f, animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing))
            underline.animateTo(2f, animationSpec = tween(durationMillis = 320, easing = pvEaseIn))
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset(x = lineOffsetX, y = lineOffsetY)
            .graphicsLayer {
                // 退場の最後は行ごとフェードアウトして残像を残さない
                alpha = if (exiting) 1f - ((exitProgress.value - 0.6f) / 0.4f).coerceIn(0f, 1f) else 1f
                // 「落」の行は文字ごとにバラバラに落下する（PvLyricUnit 側で処理）
            }
    ) {
        if (effKind == 1) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val t = rippleAnim.value
                if (t > 0f && t < 1f) {
                    for (k in 0 until 3) {
                        val local = (t * 1.35f - k * 0.16f).coerceIn(0f, 1f)
                        if (local > 0f) {
                            val cx = size.width * (0.24f + 0.26f * k) + ((baseSeed / (k + 3)) % 40 - 20).toFloat()
                            val cy = size.height * (0.32f + 0.18f * ((baseSeed / (k + 5)) % 3))
                            drawCircle(
                                color = accentColor.copy(alpha = (1f - local) * 0.28f),
                                radius = local * size.height * 1.45f,
                                center = Offset(cx, cy),
                                style = Stroke(width = 3.5f)
                            )
                        }
                    }
                }
            }
        }
        if (effKind == 9 || effKind == 10) {
            // 図形の波紋: 三角形・四角形の輪が広がる
            Canvas(modifier = Modifier.matchParentSize()) {
                val t = rippleAnim.value
                if (t > 0f && t < 1f) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    for (k in 0 until 3) {
                        val lt = ((t * 1.25f) - k * 0.18f).coerceIn(0f, 1f)
                        if (lt <= 0f) continue
                        val r = lt * size.width * 0.42f
                        val alpha = (1f - lt) * 0.28f
                        if (effKind == 9) {
                            val path = Path().apply {
                                moveTo(cx, cy - r)
                                lineTo(cx + r * 0.87f, cy + r * 0.5f)
                                lineTo(cx - r * 0.87f, cy + r * 0.5f)
                                close()
                            }
                            drawPath(path, accentColor.copy(alpha = alpha), style = Stroke(width = 3.5f))
                        } else {
                            drawRect(accentColor.copy(alpha = alpha), topLeft = Offset(cx - r, cy - r), size = Size(r * 2f, r * 2f), style = Stroke(width = 3.5f))
                        }
                    }
                }
            }
        }
        if (effKind == 8) {
            // 線エフェクト: アクセント色の横線が3本、左から右へ伸びて消える
            Canvas(modifier = Modifier.matchParentSize()) {
                val t = fxAnim.value
                if (t > 0f && t < 1f) {
                    for (k in 0 until 3) {
                        val lt = ((t * 1.4f) - k * 0.16f).coerceIn(0f, 1f)
                        if (lt <= 0f) continue
                        val y = size.height * (0.22f + 0.28f * k)
                        drawLine(
                            color = accentColor.copy(alpha = sin(lt * 3.14159f) * 0.5f),
                            start = Offset(0f, y),
                            end = Offset(size.width * lt, y),
                            strokeWidth = 2.5f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
        if (effKind == 5 && !exiting) {
            // 枠線: 行を囲む角丸の枠がふわっと現れて消える
            val ft = fxAnim.value
            if (ft > 0f && ft < 1f) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val inset = (1f - ft) * size.width * 0.06f + 6f
                    drawRoundRect(
                        color = accentColor.copy(alpha = sin(ft * 3.14159f) * 0.5f),
                        topLeft = Offset(inset, inset * 0.6f),
                        size = Size(size.width - inset * 2f, size.height - inset * 1.2f),
                        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                        style = Stroke(width = 3f)
                    )
                }
            }
        }
        if (effKind == 6 && !exiting) {
            // 粒子バースト: 行の中心からキラキラと粒が飛び散る
            val ft = fxAnim.value
            if (ft > 0f && ft < 1f) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val pe = 1f - (1f - ft) * (1f - ft) * (1f - ft)
                    val bcx = size.width / 2f
                    val bcy = size.height / 2f
                    for (i in 0 until 12) {
                        val ang = (i * 137.5f + baseSeed * 7f) * 0.0174533f
                        val dist = pe * size.width * (0.16f + (i % 4) * 0.05f)
                        val px = bcx + kotlin.math.cos(ang.toDouble()).toFloat() * dist
                        val py = bcy + kotlin.math.sin(ang.toDouble()).toFloat() * dist * 0.6f
                        val pa = (1f - ft) * 0.8f
                        if (i % 3 == 0) {
                            drawSparkle(px, py, (1f - ft * 0.5f) * 13f, accentColor, pa)
                        } else {
                            drawCircle(color = accentColor.copy(alpha = pa), radius = 3.2f, center = Offset(px, py))
                        }
                    }
                }
            }
        }
        // 単語で発火する入場・退場エフェクト: 響=波紋たくさん / 否定=破線で打ち消し / 隠・モザイク=モザイク化 / 砂・消=粒子化して吹き飛ぶ
        if (negLine || mosaicLine || sandLine || rippleWord) {
            Canvas(modifier = Modifier.matchParentSize().zIndex(5f)) {
                val W = size.width
                val H = size.height
                if (rippleWord && !exiting) {
                    val ft = fxAnim.value
                    if (ft > 0f && ft < 1f) {
                        for (i in 0 until 6) {
                            val p = (ft * 1.5f - i * 0.13f).coerceIn(0f, 1f)
                            if (p <= 0f || p >= 1f) continue
                            val cx = W * (0.5f + ((baseSeed + i * 37) % 13 - 6) * 0.03f)
                            drawCircle(color = accentColor.copy(alpha = (1f - p) * 0.5f), radius = p * W * 0.34f, center = Offset(cx, H * 0.5f), style = Stroke(width = 1.6.dp.toPx()))
                        }
                    }
                }
                val fp = exitProgress.value
                if (exiting && fp > 0f) {
                    if (negLine) {
                        val sweep = (fp * 1.6f).coerceAtMost(1f)
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.argb(235, 255, 90, 90)
                            strokeWidth = 3.dp.toPx()
                            style = android.graphics.Paint.Style.STROKE
                            pathEffect = android.graphics.DashPathEffect(floatArrayOf(11f, 8f), 0f)
                            isAntiAlias = true
                        }
                        drawContext.canvas.nativeCanvas.drawLine(W * 0.03f, H * 0.58f, W * 0.03f + W * 0.94f * sweep, H * 0.42f, paint)
                    }
                    if (mosaicLine) {
                        val cell = 13.dp.toPx()
                        val cols = (W / cell).toInt() + 1
                        val rowsN = (H / cell).toInt() + 1
                        for (gx in 0 until cols) for (gy in 0 until rowsN) {
                            val h = (((gx * 73856093) xor (gy * 19349663) xor (baseSeed * 83492791)) and 0x7fffffff) % 100
                            if (h < fp * 135f) {
                                val c = when (h % 4) { 0 -> Color(0xFF9E9E9E); 1 -> accentColor; 2 -> Color(0xFF616161); else -> Color(0xFFBDBDBD) }
                                drawRect(color = c.copy(alpha = 0.88f), topLeft = Offset(gx * cell, gy * cell), size = Size(cell, cell))
                            }
                        }
                    }
                    if (sandLine) {
                        for (i in 0 until 240) {
                            val h1 = ((i * 2654435761L) xor (baseSeed * 97L)) and 0x7fffffffL
                            val sx = (h1 % 1000) / 1000f
                            val sy = ((h1 / 1000) % 1000) / 1000f
                            val drift = fp * fp * (0.25f + (h1 % 50) / 100f) * W
                            val rise = fp * ((h1 % 30) / 100f - 0.12f) * H
                            val px = sx * W + drift
                            val py = sy * H + rise + fp * fp * H * 0.25f
                            val a = (1f - fp) * (0.35f + (h1 % 60) / 100f)
                            val c = when ((h1 % 5).toInt()) { 0 -> accentColor; 1 -> Color(0xFFE8D8A8); else -> Color.White }
                            drawCircle(color = c.copy(alpha = a.coerceIn(0f, 1f)), radius = (1.1f + (h1 % 20) / 10f).dp.toPx() * 0.55f, center = Offset(px, py))
                        }
                    }
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
        var globalIndex = 0
        var iconPlaced = false
        var inParen = false
        rows.forEachIndexed { rowIdx, rowChunks ->
        val unitsInRow = rowChunks.sumOf { renderUnitsOf(it).size }
        var rowUnitIdx = 0
        Box {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (rowIdx > 0) Modifier.padding(start = row2Indent, top = 0.dp) else Modifier
        ) {
            rowChunks.forEachIndexed { posInRow, chunk ->
                if (posInRow > 0 && latinDominant) {
                    Spacer(Modifier.width((fitSize * 0.32f).dp))
                }
                // コーラス部分（括弧の中・yeah等の掛け声）は薄く表示する
                val chunkDim = inParen || chunk.startsWith("（") || chunk.startsWith("(") || isChorusWord(chunk)
                if (chunk.contains("（") || chunk.contains("(")) inParen = true
                if (chunk.contains("）") || chunk.contains(")")) inParen = false
                val chunkIconKind = if (!iconPlaced && iconWord != null && chunk.contains(iconWord.first)) iconWord.second else null
                val chunkGhost = chunk.contains("透明")
                // 複数文字の色ワード（希望・想い等）は塊単位で判定し、該当する文字だけに色を渡す
                val chunkColorHit = (COLOR_WORDS + ASSOC_COLOR_WORDS).firstOrNull { (w, _) -> w.length > 1 && chunk.contains(w) }
                val chunkColorStart = chunkColorHit?.let { chunk.indexOf(it.first) } ?: -1
                val chunkArrow = Regex("行[くきけこっか]").containsMatchIn(chunk)
                val chunkDream = chunk.contains("夢") || chunk.contains("思い出")
                val chunkUnits = renderUnitsOf(chunk)
                var chunkCharPos = 0
                var unitIdxInChunk = 0
                for (ru in chunkUnits) {
                    val gi = globalIndex++
                    val thisUnitIcon = if (chunkIconKind != null && unitIdxInChunk == 0) chunkIconKind else null
                    if (thisUnitIcon != null) iconPlaced = true
                    unitIdxInChunk++
                    val rIdx = rowUnitIdx++
                    // 文字同士がほんの少し被るくらいに、行の中心へ向けて詰める（画面外へのはみ出しは増やさない）
                    val uXShift = ((unitsInRow - 1) / 2f - rIdx) * fitSize * 0.05f
                    val uSeed = Math.floorMod(baseSeed * 7 + gi * 131, 997)
                    val charFactor = when {
                        ru.length > 1 && ru.all { it in '゠'..'ヿ' } -> 1.05f // カタカナ単語
                        ru.length > 1 -> 0.98f // 英単語
                        ru[0] in "ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮヵヶ" -> 1.0f // 小カナの縮小はPvLyricUnit内のスパンで1回だけ行う
                        ru[0] in '一'..'鿿' || ru[0] == '々' || ru[0] == '〆' || ru[0] == 'ヶ' -> 1.25f
                        ru[0] in 'ぁ'..'ゟ' -> 0.82f
                        ru[0] in '゠'..'ヿ' -> 1.05f
                        else -> 0.98f
                    }
                    val unitStart = chunkCharPos; chunkCharPos += ru.length
                    val unitChunkColor = if (chunkColorHit != null && unitStart < chunkColorStart + chunkColorHit.first.length && unitStart + ru.length > chunkColorStart) chunkColorHit.second else null
                    PvLyricUnit(
                        bold = bold,
                        unitText = ru,
                        uSize = (fitSize * (0.92f + (uSeed % 22) / 100f) * charFactor).coerceIn(fitSize * 0.55f, fitSize * 1.35f).sp,
                        uRot = ((uSeed / 3) % 29 - 14).toFloat(),
                        uYOffset = ((uSeed / 5) % 17 - 8).dp,
                        uXShiftDp = uXShift,
                        staggerIndex = gi,
                        exiting = exiting,
                        exitProgressValue = if (sandLine && !fallLine) (exitProgress.value * 1.6f).coerceAtMost(1f) else exitProgress.value,
                        exitDelayMs = (uSeed % 5) * 35f + gi * 12f,
                        fontFamily = fontFamily,
                        accentColor = accentColor,
                        effectKind = effKind,
                        iconKind = thisUnitIcon,
                        dimmed = chunkDim,
                        ghost = chunkGhost,
                        fallExit = fallLine,
                        chunkColor = unitChunkColor,
                        arrowUnder = chunkArrow && ru == "行",
                        bubble = if (!chunkDream) 0 else if (unitIdxInChunk == chunkUnits.size) 2 else 1,
                        swayScale = if (bigText) 2.8f else 1f
                    )
                }
            }
        }
            if (effKind == 2) {
                // 白い四角が文字の上を左から右へしゅっと塗り、そのまま右へしゅっと消える
                val bv = barAnim.value
                if (bv > 0f && bv < 2f) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val frac = if (bv <= 1f) bv else 2f - bv
                        val xOff = if (bv <= 1f) 0f else (bv - 1f) * size.width
                        // 文字ギリギリのサイズの、角丸なしの白い四角（点滅しながら塗る）
                        val blink = if (sin(bv * 9.42f) > 0f) 1f else 0.30f
                        drawRect(
                            color = Color.White.copy(alpha = blink),
                            topLeft = Offset(xOff, 0f),
                            size = Size(frac * size.width, size.height)
                        )
                    }
                }
            }
        }
        }
        }
    }
}

/** PVの1描画単位（1文字または英単語）。入場はスタッガード、退場は共通の時計から位相計算する */
@Composable
fun PvLyricUnit(
    unitText: String,
    uSize: androidx.compose.ui.unit.TextUnit,
    uRot: Float,
    uYOffset: androidx.compose.ui.unit.Dp,
    uXShiftDp: Float = 0f,
    staggerIndex: Int,
    exiting: Boolean,
    exitProgressValue: Float,
    exitDelayMs: Float,
    fontFamily: FontFamily?,
    accentColor: Color,
    effectKind: Int = 0,
    iconKind: Int? = null,
    dimmed: Boolean = false,
    bold: Boolean = true,
    swayScale: Float = 1f,
    ghost: Boolean = false,
    fallExit: Boolean = false,
    chunkColor: Color? = null,
    arrowUnder: Boolean = false,
    bubble: Int = 0
) {
    val pvEaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
    val uSeed = staggerIndex
    val uAnim = remember(staggerIndex) { Animatable(0f) }
    // 表示中ずっと、位置がごく小さくぐらぐら揺れる（角度は変えない）
    val sway = remember(staggerIndex) { Animatable(0f) }
    if (!exiting) {
        LaunchedEffect(staggerIndex) {
            delay(staggerIndex * 45L)
            uAnim.animateTo(1f, animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing))
        }
        LaunchedEffect(staggerIndex) {
            sway.animateTo(
                1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2400 + (uSeed % 5) * 380, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }
    val annotatedUnit = remember(unitText, uSize) {
        buildAnnotatedString {
            for (ch in unitText) {
                if (ch in "ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮヵヶ") {
                    withStyle(SpanStyle(fontSize = uSize * 0.6f)) { append(ch) }
                } else {
                    append(ch)
                }
            }
        }
    }
    // ユニットごとの下線: 入場に合わせて左から伸びて、左から消える
    val ulAnim = remember(staggerIndex) { Animatable(0f) }
    if (!exiting) {
        LaunchedEffect(staggerIndex) {
            delay(staggerIndex * 45L)
            ulAnim.animateTo(1f, animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing))
            ulAnim.animateTo(2f, animationSpec = tween(durationMillis = 260, easing = pvEaseIn))
        }
    }
    // 色名の文字を、その色で左上から右下へ斜めに塗っていく
    val colorHit = if (!exiting) COLOR_WORDS.firstOrNull { (word, _) ->
        if (word.all { it.code < 128 }) {
            // 英語の色名は単語単位で照合する（hundred の中の red などを拾わない）
            Regex("""(?i)(?<![A-Za-z])${Regex.escape(word)}(?![A-Za-z])""").containsMatchIn(unitText)
        } else {
            unitText.contains(word)
        }
    } ?: ASSOC_COLOR_WORDS.firstOrNull { (word, _) ->
        if (word.all { it.code < 128 }) {
            Regex("""(?i)(?<![A-Za-z])${Regex.escape(word)}(?![A-Za-z])""").containsMatchIn(unitText)
        } else {
            unitText.contains(word)
        }
    } ?: (if (chunkColor != null) unitText to chunkColor else null) else null
    val wipeT = remember { Animatable(0f) }
    if (colorHit != null) {
        LaunchedEffect(staggerIndex) {
            delay(staggerIndex * 45L + 200L)
            wipeT.animateTo(1f, animationSpec = tween(durationMillis = 650, easing = LinearEasing))
        }
    }
    // 点滅(3)とグリッチ(4)用の進行度
    val fxT = remember { Animatable(0f) }
    if (!exiting && (effectKind == 3 || effectKind == 4)) {
        LaunchedEffect(staggerIndex) {
            delay(staggerIndex * 45L)
            fxT.animateTo(1f, animationSpec = tween(durationMillis = 800, easing = LinearEasing))
        }
    }
    Box(
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .offset(x = uXShiftDp.dp, y = uYOffset)
            .graphicsLayer {
                if (exiting && fallExit) {
                    // 「落」の行: 文字ごとにバラバラな速度・角度で重力落下する
                    val fp2 = ((exitProgressValue * 900f - exitDelayMs * 1.4f) / 640f).coerceIn(0f, 1f)
                    val spd = 0.7f + ((uSeed % 50) / 50f) * 0.7f
                    val pe = (fp2 * spd).coerceAtMost(1f)
                    val dirR = if (uSeed % 2 == 0) 1f else -1f
                    alpha = (if (pe < 0.6f) 1f else 1f - (pe - 0.6f) / 0.4f) * (if (ghost) 0.38f else 1f)
                    rotationZ = uRot + dirR * pe * (22f + (uSeed % 38))
                    translationX = dirR * pe * pe * (14f + (uSeed % 26))
                    translationY = pe * pe * (560f + (uSeed % 30) * 9f)
                } else if (exiting) {
                    val localX = ((exitProgressValue * 720f - exitDelayMs) / 320f).coerceIn(0f, 1f)
                    val a = 1f - pvEaseIn.transform(localX)
                    alpha = a * (if (ghost) 0.38f else 1f)
                    val es = 0.5f + 0.5f * a
                    scaleX = es
                    scaleY = es
                    rotationZ = uRot * (2f - a)
                    translationY = (1f - a) * 30f
                } else {
                    val a = uAnim.value
                    val blinkF = if (effectKind == 3 && fxT.value < 1f) {
                        if (sin(fxT.value * 21.99f + uSeed * 1.3f) > -0.2f) 1f else 0.12f
                    } else 1f
                    alpha = a * blinkF * (if (ghost) 0.38f else 1f)
                    val ns = 0.6f + 0.4f * a
                    scaleX = ns
                    scaleY = ns
                    rotationZ = uRot * a
                    // 入場は単位ごとにバラバラの方向（8方向）から飛んでくる
                    val dir = PV_ENTRY_DIRS[Math.floorMod(uSeed * 131 + 7, 8)]
                    // 波に乗るような揺れ: サイン波でなめらかに、位相は文字ごとにずらす
                    val ph = sway.value * 6.2832f + (uSeed % 7) * 0.9f
                    val swx = sin(ph) * 3.4f * swayScale
                    val swy = sin(ph + 1.3f) * 2.6f * swayScale
                    if (swayScale > 1f) rotationZ += sin(ph + 0.7f) * 2.4f * (swayScale - 1f)
                    translationX = dir.first * 52f * (1f - a) + swx * a + if (effectKind == 4 && fxT.value < 1f) sin(fxT.value * 55f + uSeed * 2.7f) * 5f * (1f - fxT.value) else 0f
                    translationY = dir.second * 52f * (1f - a) + swy * a + if (effectKind == 7) sin(a * 6.2832f + uSeed * 0.8f) * 15f * (1f - a) else 0f
                }
            }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            if (!exiting && effectKind == 4 && fxT.value < 1f) {
                val flick = (0.35f + 0.3f * sin(fxT.value * 47f + uSeed * 3.1f)).coerceIn(0.1f, 0.65f)
                Text(
                    text = annotatedUnit,
                    color = accentColor.copy(alpha = flick),
                    fontSize = uSize,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Visible,
                    style = LocalTextStyle.current.copy(fontFamily = fontFamily, lineHeight = uSize * 1.02f),
                    modifier = Modifier.offset(x = 3.5.dp)
                )
                Text(
                    text = annotatedUnit,
                    color = accentColor.copy(alpha = flick * 0.55f),
                    fontSize = uSize,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Visible,
                    style = LocalTextStyle.current.copy(fontFamily = fontFamily, lineHeight = uSize * 1.02f),
                    modifier = Modifier.offset(x = (-3.5).dp)
                )
            }
            Text(
                text = annotatedUnit,
            color = Color.White.copy(alpha = if (dimmed) 0.40f else 0.92f),
            fontSize = uSize,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Visible,
            style = LocalTextStyle.current.copy(
                fontFamily = fontFamily,
                lineHeight = uSize * 1.02f,
                    shadow = Shadow(color = accentColor.copy(alpha = if (dimmed) 0.35f else 0.85f), blurRadius = 22f)
                )
            )
            if (ghost) {
                // 「透明」は中身を透けさせ、点線の輪郭だけで描く
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.6f),
                        topLeft = Offset(1f, 1f),
                        size = Size(size.width - 2f, size.height - 2f),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                        style = Stroke(width = 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f), 0f))
                    )
                }
            }
            if (arrowUnder) {
                // 「行く」の下に、右へ伸びる矢印
                Canvas(modifier = Modifier.matchParentSize()) {
                    val grow = ulAnim.value.coerceIn(0f, 1f)
                    if (grow > 0f) {
                        val y = size.height * 0.97f
                        val x0 = size.width * 0.02f
                        val xEnd = size.width * (0.1f + 1.3f * grow)
                        val colA = accentColor.copy(alpha = 0.75f * grow)
                        drawLine(colA, Offset(x0, y), Offset(xEnd, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(colA, Offset(xEnd, y), Offset(xEnd - 7.dp.toPx(), y - 4.5.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(colA, Offset(xEnd, y), Offset(xEnd - 7.dp.toPx(), y + 4.5.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
            }
            if (bubble > 0) {
                // 空想の吹き出し: 文字を楕円で包み、最後の文字には小さい丸を2つ落とす
                Canvas(modifier = Modifier.matchParentSize()) {
                    val a = (if (exiting) 1f else ulAnim.value.coerceIn(0f, 1f)) * 0.55f
                    drawOval(color = Color.White.copy(alpha = a), topLeft = Offset(-size.width * 0.08f, size.height * 0.02f), size = Size(size.width * 1.16f, size.height * 0.94f), style = Stroke(width = 1.3.dp.toPx()))
                    if (bubble == 2) {
                        drawCircle(color = Color.White.copy(alpha = a), radius = 3.dp.toPx(), center = Offset(size.width * 0.12f, size.height * 1.14f), style = Stroke(width = 1.2.dp.toPx()))
                        drawCircle(color = Color.White.copy(alpha = a), radius = 2.dp.toPx(), center = Offset(0f, size.height * 1.32f), style = Stroke(width = 1.1.dp.toPx()))
                    }
                }
            }
            if (colorHit != null) {
                val cWord = colorHit.first
                // 原色そのままではなく、アクセントカラーに色相を寄せて白で薄めた淡い色にする
                val base = colorHit.second
                val cColor = Color(
                    red = (base.red * 0.55f + accentColor.red * 0.45f) * 0.55f + 0.45f,
                    green = (base.green * 0.55f + accentColor.green * 0.45f) * 0.55f + 0.45f,
                    blue = (base.blue * 0.55f + accentColor.blue * 0.45f) * 0.55f + 0.45f
                )
                val cStart = unitText.indexOf(cWord, ignoreCase = true)
                val wp = wipeT.value.coerceIn(0f, 1f)
                val wipeBrush = Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to cColor,
                        wp to cColor,
                        (wp + 0.03f).coerceIn(0f, 1f) to Color.Transparent,
                        1f to Color.Transparent
                    ),
                    start = Offset.Zero,
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
                val overlayText = buildAnnotatedString {
                    unitText.forEachIndexed { ci, ch ->
                        val inWord = ci >= cStart && ci < cStart + cWord.length
                        val span = if (inWord) SpanStyle(brush = wipeBrush) else SpanStyle(color = Color.Transparent)
                        if (ch in "ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮヵヶ") {
                            withStyle(SpanStyle(fontSize = uSize * 0.6f)) {
                                withStyle(span) { append(ch) }
                            }
                        } else {
                            withStyle(span) { append(ch) }
                        }
                    }
                }
                Text(
                    text = overlayText,
                    fontSize = uSize,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Visible,
                    style = LocalTextStyle.current.copy(
                        fontFamily = fontFamily,
                        lineHeight = uSize * 1.02f
                    )
                )
            }
            if (!exiting && (unitText.contains("光") || unitText.contains("輝"))) {
                // 「光」「輝」はコーティングが反射するみたいにキラキラさせる
                Canvas(modifier = Modifier.matchParentSize()) {
                    val phBase = sway.value * 6.2832f
                    val sparkleCol = Color(0xFFFFF2B0)
                    val positions = listOf(0.10f to 0.02f, 0.90f to 0.22f, 0.58f to -0.06f)
                    positions.forEachIndexed { i, (fx, fy) ->
                        val tw = 0.5f + 0.5f * sin(phBase * 2f + i * 2.1f + uSeed)
                        val r = size.height * (0.15f + 0.14f * tw)
                        drawSparkle(size.width * fx, size.height * fy, r, sparkleCol, 0.30f + 0.65f * tw)
                    }
                }
            }
        }
        val enCaption = if (!exiting && unitText.length > 1 && unitText.all { it in 'ァ'..'ヶ' || it == 'ー' || it == '・' }) KATAKANA_EN[unitText] else null
        if (!exiting && effectKind == 0) {
            Spacer(modifier = Modifier.height(3.dp))
            val ulv = ulAnim.value
            Canvas(modifier = Modifier.fillMaxWidth().height(3.5.dp)) {
                val startFrac = if (ulv <= 1f) 0f else ulv - 1f
                val endFrac = if (ulv <= 1f) ulv else 1f
                if (endFrac > startFrac) {
                    drawRoundRect(
                        color = accentColor.copy(alpha = 0.6f),
                        topLeft = Offset(size.width * startFrac, 0f),
                        size = Size(size.width * (endFrac - startFrac), size.height),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                    )
                }
            }
        }
        if (enCaption != null) {
            Text(
                text = enCaption,
                color = accentColor.copy(alpha = 0.85f),
                fontSize = (uSize.value * 0.30f).sp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                letterSpacing = (uSize.value * 0.12f).sp,
                maxLines = 1,
                style = LocalTextStyle.current.copy(fontFamily = fontFamily),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        }
        if (!exiting && iconKind != null) {
            // アイコンはその単語の右肩に小さく重ねる
            Canvas(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 12.dp, y = (-12).dp)
                    .size((uSize.value * 0.55f).dp)
                    .graphicsLayer {
                        alpha = ulAnim.value.coerceIn(0f, 1f) * 0.45f
                        rotationZ = -9f
                    }
            ) {
                drawLyricIcon(iconKind)
            }
        }
    }
}

/** 塊を描画単位に分ける。カタカナの連続とラテン文字の連続はひとまとめ、それ以外は1文字ずつ */
fun renderUnitsOf(chunk: String): List<String> {
    val out = mutableListOf<String>()
    val sb = StringBuilder()
    fun flush() {
        if (sb.isNotEmpty()) {
            out.add(sb.toString())
            sb.clear()
        }
    }
    for (ch in chunk) {
        if (ch.code < 0x300) {
            sb.append(ch)
        } else {
            flush()
            out.add(ch.toString())
        }
    }
    flush()
    return out
}

/** 歌詞を行の表示ユニットに割る。ラテン文字の連続は単語としてまとめ、日本語は文節っぽい塊に切る */
fun splitLyricUnits(text: String): List<String> {
    val units = mutableListOf<String>()
    val latin = StringBuilder()
    val jp = StringBuilder()
    fun flushLatin() {
        if (latin.isNotEmpty()) {
            units.add(latin.toString())
            latin.clear()
        }
    }
    fun flushJp() {
        if (jp.isNotEmpty()) {
            units.addAll(chunkJapanese(jp.toString()))
            jp.clear()
        }
    }
    for (ch in text) {
        when {
            ch == ' ' || ch == '　' -> {
                flushLatin(); flushJp()
            }
            ch.code < 0x300 && (ch.isLetterOrDigit() || ch == '\'' || ch == '’' || ch == '-') -> {
                flushJp(); latin.append(ch)
            }
            else -> {
                flushLatin(); jp.append(ch)
            }
        }
    }
    flushLatin(); flushJp()
    return units
}

/**
 * 日本語の文字列を文節っぽい塊に切る軽量ヒューリスティクス。
 * 内容語（漢字・カタカナ・ひらがな語）に後ろのひらがな（送り仮名・助詞・活用語尾）をくっつけ、
 * ひらがな部分が助詞で始まって後ろに内容が続く場合は、助詞の後ろで次の塊に分ける。
 */
fun chunkJapanese(span: String): List<String> {
    fun typeOf(ch: Char): Int = when (ch) {
        in '一'..'鿿', '々', '〆', 'ヶ' -> 1 // 漢字
        in 'ぁ'..'ゟ' -> 2 // ひらがな
        in '゠'..'ヿ' -> 3 // カタカナ（ー含む）
        else -> 0
    }
    // 同種の連続に分ける
    val runs = mutableListOf<String>()
    val sb = StringBuilder()
    var prevType = -1
    for (ch in span) {
        val t = typeOf(ch)
        if (t != prevType && sb.isNotEmpty()) {
            runs.add(sb.toString())
            sb.clear()
        }
        sb.append(ch)
        prevType = t
    }
    if (sb.isNotEmpty()) runs.add(sb.toString())

    val particles = "はがをにへとやのもでからまでよりばねよわ"
    val chunks = mutableListOf<String>()
    val cur = StringBuilder()
    fun closeChunk() {
        if (cur.isNotEmpty()) {
            chunks.add(cur.toString())
            cur.clear()
        }
    }
    for (run in runs) {
        val t = typeOf(run[0])
        when {
            t == 2 && cur.isNotEmpty() -> {
                // ひらがな部分: 先頭の助詞（最大2文字）の後ろに2文字以上残るなら、助詞までを今の塊に付けて次へ
                var pLen = 0
                while (pLen < run.length && pLen < 2 && run[pLen] in particles) pLen++
                if (pLen >= 1 && run.length - pLen >= 2) {
                    cur.append(run.substring(0, pLen))
                    closeChunk()
                    cur.append(run.substring(pLen))
                } else {
                    cur.append(run)
                }
            }
            t == 2 -> cur.append(run) // 行頭のひらがなはそのまま塊の始まり
            t == 0 -> cur.append(run) // 句読点などは前の塊に付ける
            else -> {
                // 漢字・カタカナは新しい塊の始まり
                closeChunk()
                cur.append(run)
            }
        }
    }
    closeChunk()
    return chunks
}

@Composable
fun LyricsModePreviewCard(
    mode: String,
    label: String,
    selected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0B101D))
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) accentColor else Color.White.copy(alpha = 0.25f),
                    RoundedCornerShape(10.dp)
                )
        ) {
            when (mode) {
                "line" -> {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Box(Modifier.width(26.dp).height(4.dp).background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(2.dp)))
                        Spacer(Modifier.height(3.dp))
                        Box(Modifier.width(18.dp).height(3.dp).background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(2.dp)))
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.width(34.dp).height(3.dp).background(accentColor.copy(alpha = 0.9f), RoundedCornerShape(2.dp)))
                    }
                }
                "video" -> {
                    Text(
                        "歌", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center).graphicsLayer { rotationZ = -10f }
                    )
                    Text(
                        "詞", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).graphicsLayer { rotationZ = 12f }
                    )
                    Text(
                        "♪", color = accentColor, fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                    )
                }
                else -> {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Box(Modifier.width(28.dp).height(3.dp).background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.width(38.dp).height(5.dp).background(Color.White, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.width(28.dp).height(3.dp).background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 6.dp, bottom = 6.dp)
                            .size(20.dp)
                            .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 6.dp, bottom = 6.dp)
                            .size(20.dp)
                            .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = if (selected) accentColor else Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
        Text(if (selected) "●" else "○", color = if (selected) accentColor else Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
    }
}

/** タグ（曲名・アーティスト名）を指定して歌詞候補を検索し、選んで適用するダイアログ */
@Composable
fun LyricTagSearchDialog(
    currentTitle: String,
    currentArtist: String,
    currentAlbum: String,
    currentLines: List<LyricLine>,
    onApply: (List<LyricLine>, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember {
        mutableStateOf(currentTitle.replace(Regex("""^\s*\d{1,3}\s*[.．、:：_\-]\s*"""), ""))
    }
    var artist by remember { mutableStateOf(currentArtist) }
    var searching by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<LyricsManager.LyricCandidate>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    var applyingIndex by remember { mutableIntStateOf(-1) }
    var message by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("タグを指定して検索") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("曲名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("アーティスト名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    enabled = !searching && title.isNotBlank(),
                    onClick = {
                        searching = true
                        message = null
                        scope.launch {
                            candidates = LyricsManager.searchCandidates(title.trim(), artist.trim())
                            searching = false
                            searched = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (searching) "検索中…" else "検索") }
                message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                if (searched && !searching && candidates.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("候補が見つかりませんでした")
                }
                if (candidates.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(candidates.size) { idx ->
                            val cand = candidates[idx]
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = applyingIndex < 0) {
                                        applyingIndex = idx
                                        scope.launch {
                                            val lrc = LyricsManager.fetchCandidateLrc(cand)
                                            applyingIndex = -1
                                            if (lrc != null) {
                                                LyricsManager.saveLyricsOverride(
                                                    context, currentTitle, currentArtist, currentAlbum, lrc
                                                )
                                                onApply(LyricsManager.parseLrc(lrc), cand.source)
                                                onDismiss()
                                            } else {
                                                message = "その候補の歌詞を取得できませんでした"
                                            }
                                        }
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = if (applyingIndex == idx) "取得中…" else cand.title.ifBlank { "(タイトルなし)" },
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val dur = if (cand.durationSec > 0) {
                                    " ・ ${cand.durationSec / 60}:${(cand.durationSec % 60).toString().padStart(2, '0')}"
                                } else ""
                                Text(
                                    text = "${cand.artist}$dur ・ ${cand.source}",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                // 今適用されている歌詞と内容が一致する候補には印を付ける
                                val currentFp = remember(currentLines) {
                                    if (currentLines.isEmpty()) null
                                    else currentLines.joinToString("\n") { it.text }.hashCode()
                                }
                                var isCurrent by remember(cand) { mutableStateOf(false) }
                                LaunchedEffect(cand, currentFp) {
                                    isCurrent = false
                                    if (currentFp != null) {
                                        val lrcText = cand.syncedLyrics
                                            ?: if (cand.source == "Kugou") LyricsManager.fetchCandidateLrc(cand) else null
                                        if (lrcText != null) {
                                            val fp = LyricsManager.parseLrc(lrcText)
                                                .joinToString("\n") { it.text }.hashCode()
                                            isCurrent = fp == currentFp
                                        }
                                    }
                                }
                                if (isCurrent) {
                                    Text(
                                        text = "現在適用されています",
                                        color = Color(0xFF7FD4F2),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color.White.copy(alpha = 0.08f))
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        }
    )
}

// ============================================================
// 歌詞まわりの大きい塊（PVレイヤー／全文ブラウズ／フルスクリーン）
// MainLauncherScreenが巨大化してVerifyErrorで落ちるのを防ぐため、
// 画面本体から切り出してここに置く。音楽アプリ側が正本。
// ============================================================

/** PVレイヤー: 現在の行を文字PVで表示し、前行を退場アニメーションで消す。対訳もここに出す */
@Composable
fun LyricsPvLayer(
    lines: List<LyricLine>,
    positionMs: Long,
    lyricOffsetMs: Long,
    bold: Boolean,
    fontFamily: FontFamily?,
    accentColor: Color,
    screenWidthDp: Float,
    translationEnabled: Boolean,
    translations: List<String>?,
    pvTopDp: Dp,
    interludeTopDp: Dp,
    translationTopDp: Dp
) {
    val pvPos = positionMs + lyricOffsetMs
    val pvIndex = lines.indexOfLast { it.timeMs <= pvPos }
    // PVは間奏では何も表示しない（音符マークも出さない）
    val pvCurLine = lines.getOrNull(pvIndex)
    val pvNextLine = lines.getOrNull(pvIndex + 1)
    val pvInterlude = when {
        pvCurLine == null -> false
        pvNextLine == null -> (pvPos - pvCurLine.timeMs) > 8000
        else -> (pvNextLine.timeMs - pvCurLine.timeMs) > 15000 && (pvPos - pvCurLine.timeMs) > 5000 // 行間が長い間奏だけ。次の行が近づいたら前の行に戻る、をしない
    }
    val pvDisplayIndex = if (pvInterlude) -2 else pvIndex
    if (pvIndex >= 0) {
                // 前の行は残像にせず、文字ごとにバラバラの遅延で退場アニメーションをかけて消す
        // エフェクトは行ごとにランダム。ただし前の行と同じ種類は避ける
        val pvKindMap = remember { mutableStateMapOf<Int, Int>() }
        fun pvKindFor(idx: Int, text: String): Int {
            pvKindMap[idx]?.let { return it }
            val raw = Math.floorMod(Math.floorMod(idx * 31 + text.hashCode(), 997), 8)
            val prev = pvKindMap[idx - 1]
            val kind = if (prev != null && raw == prev) (raw + 1) % 8 else raw
            pvKindMap[idx] = kind
            return kind
        }
        var pvCurrent by remember { mutableStateOf<Pair<Int, String>?>(null) }
        var pvExiting by remember { mutableStateOf<Pair<Int, String>?>(null) }
        // 対訳を「PVの2行目の真下」に置くため、現在のPV行の実高さを測る
        var pvLineHeightDp by remember { mutableStateOf(0.dp) }
        val pvDensity = LocalDensity.current
        LaunchedEffect(lines) {
            pvCurrent = null
            pvExiting = null
        }
        LaunchedEffect(pvDisplayIndex) {
            if (pvDisplayIndex >= 0) {
                if (pvCurrent?.first != pvDisplayIndex) {
                    pvCurrent?.let { pvExiting = it }
                    if (pvExiting?.first == pvDisplayIndex) pvExiting = null
                    pvCurrent = pvDisplayIndex to lines[pvDisplayIndex].text
                }
            } else if (pvDisplayIndex == -2) {
                // 間奏: 今の行は退場アニメーションで消して、新しい行は出さない
                pvCurrent?.let { pvExiting = it }
                pvCurrent = null
            }
            if (pvExiting != null) {
                delay(750)
                pvExiting = null
            }
        }
        // 「暗」を含む行では背景を少し暗くし、次の行で戻す
        val darkTarget = if (pvDisplayIndex >= 0 && lines.getOrNull(pvDisplayIndex)?.text?.contains("暗") == true) 0.32f else 0f
        val darkAlpha = remember { Animatable(0f) }
        LaunchedEffect(darkTarget) {
            darkAlpha.animateTo(darkTarget, animationSpec = tween(durationMillis = 700))
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (darkAlpha.value > 0.001f) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = darkAlpha.value)))
            }
            pvExiting?.let { (exIdx, exText) ->
                key("pvexit$exIdx") {
                    PvLyricLine(
                        bold = bold,
                        text = exText,
                        baseSeed = Math.floorMod(exIdx * 31 + exText.hashCode(), 997),
                        fontFamily = fontFamily,
                        accentColor = accentColor,
                        screenWidthDp = screenWidthDp,
                        exiting = true,
                        effectKind = pvKindFor(exIdx, exText),
                        lineDurationMs = lines.getOrNull(exIdx + 1)?.let { it.timeMs - lines[exIdx].timeMs } ?: Long.MAX_VALUE,
                        modifier = Modifier.padding(top = pvTopDp)
                    )
                }
            }
            pvCurrent?.let { (curIdx, curText) ->
                key("pvcur$curIdx") {
                    Box(modifier = Modifier.padding(top = pvTopDp)) {
                        Box(modifier = Modifier.onSizeChanged { sz -> pvLineHeightDp = with(pvDensity) { sz.height.toDp() } }) {
                            PvLyricLine(
                                bold = bold,
                                text = curText,
                                baseSeed = Math.floorMod(curIdx * 31 + curText.hashCode(), 997),
                                fontFamily = fontFamily,
                                accentColor = accentColor,
                                screenWidthDp = screenWidthDp,
                                exiting = false,
                                effectKind = pvKindFor(curIdx, curText),
                                lineDurationMs = lines.getOrNull(curIdx + 1)?.let { it.timeMs - lines[curIdx].timeMs } ?: Long.MAX_VALUE,
                                modifier = Modifier
                            )
                        }
                    }
                }
            }
        }
        if (pvDisplayIndex == -2) {
            PvInterludeArt(accentColor = accentColor, modifier = Modifier.fillMaxWidth().padding(top = interludeTopDp).height(230.dp))
        }
        // 対訳: PVの3行目として、現在の行の日本語訳を小さく出す
        if (translationEnabled && pvDisplayIndex >= 0) {
            val translation = translations?.getOrNull(pvDisplayIndex)
            if (!translation.isNullOrBlank()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = if (pvLineHeightDp > 0.dp) pvTopDp + pvLineHeightDp + 12.dp else translationTopDp, start = 32.dp, end = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = translation,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontFamily = fontFamily,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = LocalTextStyle.current.copy(
                            shadow = Shadow(color = accentColor.copy(alpha = 0.6f), blurRadius = 10f)
                        )
                    )
                }
            }
        }
        pvCurrent?.let { (_, curText) ->
            val ambKind = when {
                curText.contains("桜") -> 2
                curText.contains("雨") -> 0
                curText.contains("雪") -> 1
                else -> -1
            }
            if (ambKind >= 0) {
                PvAmbientEffect(kind = ambKind, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** 歌詞の全文ブラウズ: ドラッグで前後の行へ移動し、下部ボタンでシークやタイミング調整ができる */
@Composable
fun LyricsBrowseOverlay(
    lines: List<LyricLine>,
    startIndex: Int,
    positionMs: Long,
    lyricOffsetMs: Long,
    onOffsetChange: (Long) -> Unit,
    fontFamily: FontFamily?,
    lyricWeight: FontWeight,
    accentColor: Color,
    accentGlow: Boolean,
    topPaddingDp: Dp,
    source: String,
    translationEnabled: Boolean,
    onToggleTranslation: () -> Unit,
    reverseScroll: Boolean,
    onSeek: (Long) -> Unit,
    onRefresh: () -> Unit,
    onOpenSearch: () -> Unit,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var follow by remember { mutableStateOf(true) }
    var showOffsetPanel by remember { mutableStateOf(false) }
    // 自由スクロール: 位置は「行」単位の小数で持ち、指に追従させて離すと慣性で流れる
    val scrollAnim = remember { Animatable(startIndex.toFloat()) }
    val maxPos = (lines.size - 1).coerceAtLeast(0).toFloat()
    LaunchedEffect(lines.size) { scrollAnim.updateBounds(0f, maxPos) }
    Box(modifier = Modifier.fillMaxSize().zIndex(96f)) {
    if (source.isNotBlank()) {
        Text(
            text = "歌詞ソース: $source",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).zIndex(91f)
        )
    }
        val virtualPos = scrollAnim.value
        val focusedIdx = virtualPos.roundToInt().coerceIn(0, lines.size - 1)
    // 同期モード中は再生中の行へ自動で追従する
    val browsePlayingIdx = lines.indexOfLast { it.timeMs <= positionMs + lyricOffsetMs }
    LaunchedEffect(browsePlayingIdx, follow) {
        if (follow && browsePlayingIdx >= 0 && browsePlayingIdx.toFloat() != scrollAnim.value) {
            scrollAnim.animateTo(
                browsePlayingIdx.toFloat(),
                tween(durationMillis = 300, easing = FastOutSlowInEasing)
            )
        }
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.30f to Color.Black,
                        0.86f to Color.Black,
                        1f to Color.Transparent
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
            .zIndex(90f)
            .pointerInput(lines.size) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val dirSign = if (reverseScroll) -1f else 1f
                        var lastY = down.position.y
                        var lastT = down.uptimeMillis
                        var velocityPx = 0f
                        var moved = false
                        var done = false
                        scope.launch { scrollAnim.stop() }
                        while (!done) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUp()) {
                                done = true
                                if (moved && abs(velocityPx) > 60f) {
                                    val linePx = 48f * density
                                    val vLines = (-velocityPx / linePx) * dirSign
                                    scope.launch { scrollAnim.animateDecay(vLines, exponentialDecay()) }
                                }
                            } else {
                                val dy = change.position.y - lastY
                                val dt = (change.uptimeMillis - lastT).coerceAtLeast(1L)
                                velocityPx = velocityPx * 0.7f + (dy / dt * 1000f) * 0.3f
                                lastY = change.position.y
                                lastT = change.uptimeMillis
                                if (dy != 0f) {
                                    if (!moved && abs(change.position.y - down.position.y) > 6f) {
                                        moved = true
                                        follow = false
                                    }
                                    if (moved) {
                                        val linePx = 48f * density
                                        val delta = (-dy / linePx) * dirSign
                                        val target = (scrollAnim.value + delta).coerceIn(0f, maxPos)
                                        scope.launch { scrollAnim.snapTo(target) }
                                    }
                                }
                                change.consume()
                            }
                        }
                    }
                }
            }
    ) {
        // 明るい背景（液体やアートワーク）でも歌詞が読めるよう、薄い暗幕を敷く
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.34f),
                            Color.Black.copy(alpha = 0.48f),
                            Color.Black.copy(alpha = 0.40f)
                        )
                    )
                )
        )
        val centerYDp = maxHeight.value / 2f
        val densityNow = LocalDensity.current
        // 折り返した行は高さが変わるので、実測した行高で累積配置する
        val lineHeightsPx = remember { mutableStateMapOf<Int, Int>() }
        fun stepDp(i: Int): Float {
            val hPx = lineHeightsPx[i]
            return (if (hPx != null) hPx / densityNow.density else 34f) + 14f
        }
        val baseIdx = floor(virtualPos).toInt().coerceIn(0, lines.size - 1)
        val fracPart = virtualPos - floor(virtualPos)
        fun offsetDpFor(idx: Int): Float {
            var acc = 0f
            if (idx > baseIdx) {
                for (i in baseIdx until idx) acc += stepDp(i)
            } else if (idx < baseIdx) {
                for (i in idx until baseIdx) acc -= stepDp(i)
            }
            return acc - fracPart * stepDp(baseIdx)
        }
        val firstIdx = maxOf(0, floor(virtualPos - 7f).toInt())
        val lastIdx = minOf(lines.size - 1, ceil(virtualPos + 7f).toInt())
        for (idx in firstIdx..lastIdx) {
            val relPos = idx - virtualPos
            val isFocusedLine = idx == focusedIdx
            val fade = (1f - (abs(relPos) - 4f) / 3f).coerceIn(0f, 1f)
            val myHeightDp = (lineHeightsPx[idx]?.let { it / densityNow.density } ?: 34f)
            Text(
                text = lines[idx].text,
                color = if (isFocusedLine) Color.White else Color.White.copy(alpha = 0.92f * fade),
                fontSize = fitFontSizeSp(lines[idx].text, if (isFocusedLine) 26f else 15f, maxWidth.value * 0.85f).sp,
                fontWeight = if (isFocusedLine) lyricWeight else FontWeight.Normal,
                letterSpacing = 1.0.sp,
                textAlign = TextAlign.Start,
                maxLines = 1,
                style = LocalTextStyle.current.copy(
                    fontFamily = fontFamily,
                    shadow = if (accentGlow && isFocusedLine) Shadow(color = accentColor, blurRadius = 14f) else Shadow(color = Color.Black.copy(alpha = 0.75f), blurRadius = 10f)
                ),
                modifier = Modifier
                    .offset(
                        x = (40f + relPos * 5f).dp,
                        y = (centerYDp + offsetDpFor(idx) - myHeightDp / 2f).dp
                    )
                    .fillMaxWidth(0.85f)
                    .onGloballyPositioned { lineHeightsPx[idx] = it.size.height }
            )
        }
    }
    // 下部ボタン: 移動（フォーカス行へジャンプ）／タイミング調整／閉じる
    val focusedLineForButtons = lines.getOrNull(focusedIdx)
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = topPaddingDp + 48.dp, end = 22.dp)
            .size(44.dp)
            .zIndex(95f)
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Close, contentDescription = "閉じる", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(26.dp))
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 44.dp)
            .zIndex(95f)
    ) {
        if (!follow) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF263238))
                    .clickable { follow = true }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text("同期", color = accentColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
        if (showOffsetPanel) {
            Text(
                text = "タイミング調整: " + when {
                    lyricOffsetMs > 0 -> "+${lyricOffsetMs / 1000.0}秒（歌詞を早める）"
                    lyricOffsetMs < 0 -> "${lyricOffsetMs / 1000.0}秒（歌詞を遅らせる）"
                    else -> "±0秒"
                },
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(-1000L to "-1.0", -500L to "-0.5", -100L to "-0.1", 100L to "+0.1", 500L to "+0.5", 1000L to "+1.0").forEach { (delta, label) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF263238))
                            .clickable {
                                val newOffset = (lyricOffsetMs + delta).coerceIn(-30000L, 30000L)
                                onOffsetChange(newOffset)
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(label, color = Color.White, fontSize = 13.sp)
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF263238))
                        .clickable { onOffsetChange(0L) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text("リセット", color = Color.White, fontSize = 13.sp)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左: 歌詞の再取得
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF263238))
                    .clickable { onRefresh() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "歌詞を再取得", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            // タグを指定して検索（間違った歌詞を掴んだとき用）
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF263238))
                    .clickable { onOpenSearch() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Search, contentDescription = "タグを指定して検索", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            // 中央: フォーカス行へ移動
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor)
                    .clickable {
                        if (focusedLineForButtons != null) {
                            onSeek((focusedLineForButtons.timeMs - lyricOffsetMs).coerceAtLeast(0L))
                        }
                        onClose()
                    }
                    .padding(horizontal = 22.dp, vertical = 12.dp)
            ) {
                Text("移動", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            // 右: タイミング調整（オフセット）
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (showOffsetPanel || lyricOffsetMs != 0L) accentColor else Color(0xFF263238))
                    .clickable { showOffsetPanel = !showOffsetPanel },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Timer, contentDescription = "タイミング調整", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            // 翻訳ON/OFF（洋楽の対訳をPVの下に出す）
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (translationEnabled) accentColor else Color(0xFF263238))
                    .clickable { onToggleTranslation() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Translate, contentDescription = "翻訳", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
    }
}

/** 歌詞フルスクリーン（横画面）: PVだけ、または曲情報と歌詞を大きく表示する */
@Composable
fun LyricsFullscreenOverlay(
    lines: List<LyricLine>?,
    positionMs: Long,
    lyricOffsetMs: Long,
    displayMode: String,
    bold: Boolean,
    fontFamily: FontFamily?,
    lyricWeight: FontWeight,
    accentColor: Color,
    artwork: Bitmap?,
    artworkBackground: Color?,
    liquidEnabled: Boolean,
    title: String,
    artist: String,
    album: String,
    onClose: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().zIndex(96f)) {
        val fsWidth = maxWidth.value
        val fsLines = lines ?: emptyList()
        if (artwork != null) {
            Image(
                bitmap = artwork!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(64.dp)
            )
            Box(Modifier.fillMaxSize().background((artworkBackground ?: Color(0xFF0B101D)).copy(alpha = 0.55f)))
        } else {
            Box(Modifier.fillMaxSize().background(Color.Black))
        }
        // 横画面でも液体グラデーションを流す（設定ONのとき）
        if (liquidEnabled) {
            LiquidGradientBackground(baseColor = accentColor, onBlack = false, layerAlpha = 1f, alphaBoost = 1.9f)
        }
        if (fsLines.isEmpty()) {
            // 歌詞が無い曲: 左にアートワーク、右に曲名などの情報だけを出す
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (artwork != null) {
                    Image(
                        bitmap = artwork!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(240.dp).clip(RoundedCornerShape(16.dp))
                    )
                } else {
                    Box(Modifier.size(240.dp).clip(RoundedCornerShape(16.dp)).background(accentColor.copy(alpha = 0.25f)))
                }
                Spacer(Modifier.width(40.dp))
                Column {
                    Text(title, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (artist.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(artist, color = Color.White.copy(alpha = 0.75f), fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (album.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(album, color = Color.White.copy(alpha = 0.55f), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } else {
        val fsPos = positionMs + lyricOffsetMs
        val fsIndex = fsLines.indexOfLast { it.timeMs <= fsPos }
        val fsCurLine = fsLines.getOrNull(fsIndex)
        val fsNextLine = fsLines.getOrNull(fsIndex + 1)
        val fsInterlude = when {
            fsCurLine == null -> false
            fsNextLine == null -> (fsPos - fsCurLine.timeMs) > 8000
            else -> (fsNextLine.timeMs - fsCurLine.timeMs) > 15000 && (fsPos - fsCurLine.timeMs) > 5000
        }
        if (displayMode == "video") {
            // PVだけを中央に
            val fsDisplayIndex = if (fsInterlude) -2 else fsIndex
            if (fsIndex >= 0) {
                var fsCurrent by remember { mutableStateOf<Pair<Int, String>?>(null) }
                var fsExiting by remember { mutableStateOf<Pair<Int, String>?>(null) }
                LaunchedEffect(lines) { fsCurrent = null; fsExiting = null }
                LaunchedEffect(fsDisplayIndex) {
                    if (fsDisplayIndex >= 0) {
                        if (fsCurrent?.first != fsDisplayIndex) {
                            fsCurrent?.let { fsExiting = it }
                            if (fsExiting?.first == fsDisplayIndex) fsExiting = null
                            fsCurrent = fsDisplayIndex to fsLines[fsDisplayIndex].text
                        }
                    } else if (fsDisplayIndex == -2) {
                        fsCurrent?.let { fsExiting = it }
                        fsCurrent = null
                    }
                    if (fsExiting != null) { delay(750); fsExiting = null }
                }
                // 「暗」を含む行では背景を少し暗くし、次の行で戻す
                val fsDarkTarget = if (fsDisplayIndex >= 0 && fsLines.getOrNull(fsDisplayIndex)?.text?.contains("暗") == true) 0.32f else 0f
                val fsDarkAlpha = remember { Animatable(0f) }
                LaunchedEffect(fsDarkTarget) {
                    fsDarkAlpha.animateTo(fsDarkTarget, animationSpec = tween(durationMillis = 700))
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (fsDarkAlpha.value > 0.001f) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = fsDarkAlpha.value)))
                    }
                    fsExiting?.let { (exIdx, exText) ->
                        key("fsexit$exIdx") {
                            PvLyricLine(
                                bold = bold,
                                text = exText,
                                baseSeed = Math.floorMod(exIdx * 31 + exText.hashCode(), 997),
                                fontFamily = fontFamily,
                                accentColor = accentColor,
                                screenWidthDp = fsWidth,
                                bigText = true,
                                lineDurationMs = fsLines.getOrNull(exIdx + 1)?.let { it.timeMs - fsLines[exIdx].timeMs } ?: Long.MAX_VALUE,
                                exiting = true,
                                effectKind = Math.floorMod(Math.floorMod(exIdx * 31 + exText.hashCode(), 997), 8),
                                modifier = Modifier
                            )
                        }
                    }
                    fsCurrent?.let { (curIdx, curText) ->
                        key("fscur$curIdx") {
                            PvLyricLine(
                                bold = bold,
                                text = curText,
                                baseSeed = Math.floorMod(curIdx * 31 + curText.hashCode(), 997),
                                fontFamily = fontFamily,
                                accentColor = accentColor,
                                screenWidthDp = fsWidth,
                                bigText = true,
                                lineDurationMs = fsLines.getOrNull(curIdx + 1)?.let { it.timeMs - fsLines[curIdx].timeMs } ?: Long.MAX_VALUE,
                                exiting = false,
                                effectKind = Math.floorMod(Math.floorMod(curIdx * 31 + curText.hashCode(), 997), 8),
                                modifier = Modifier
                            )
                        }
                    }
                }
                if (fsDisplayIndex == -2) {
                    PvInterludeArt(accentColor = accentColor, modifier = Modifier.fillMaxSize().padding(60.dp))
                }
                fsCurrent?.let { (_, curText) ->
                    val ambKind = when {
                        curText.contains("桜") -> 2
                        curText.contains("雨") -> 0
                        curText.contains("雪") -> 1
                        else -> -1
                    }
                    if (ambKind >= 0) PvAmbientEffect(kind = ambKind, modifier = Modifier.fillMaxSize())
                }
            }
        } else {
            // 曲名・アーティスト・アルバムと一緒に歌詞（1行 or 複数行）を横画面レイアウトで
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier.widthIn(max = 600.dp).padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = LocalTextStyle.current.copy(shadow = Shadow(color = accentColor.copy(alpha = 0.7f), blurRadius = 16f))
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = listOf(artist, album).filter { it.isNotBlank() }.joinToString(" ・ "),
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(30.dp))
                    if (displayMode == "multi") {
                        val prevText = if (fsIndex > 0) fsLines.getOrNull(fsIndex - 1)?.text ?: "" else ""
                        val curText = if (fsInterlude) "♪" else (fsCurLine?.text ?: "")
                        val nextText = fsNextLine?.text ?: ""
                        if (prevText.isNotEmpty()) {
                            Text(prevText, color = Color.White.copy(alpha = 0.45f), fontSize = fitFontSizeSp(prevText, 18f, fsWidth - 80f).sp, letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = LocalTextStyle.current.copy(fontFamily = fontFamily))
                            Spacer(Modifier.height(14.dp))
                        }
                        if (curText.isNotEmpty()) {
                            Text(
                                curText,
                                color = Color.White,
                                fontSize = fitFontSizeSp(curText, 30f, fsWidth - 108f).sp,
                                fontWeight = lyricWeight,
                                letterSpacing = 3.sp,
                                maxLines = 1,
                                style = LocalTextStyle.current.copy(
                                    fontFamily = fontFamily,
                                    shadow = Shadow(color = Color.White.copy(alpha = 0.85f), blurRadius = 18f)
                                ),
                                modifier = Modifier.padding(start = 28.dp)
                            )
                        }
                        if (nextText.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            Text(nextText, color = Color.White.copy(alpha = 0.45f), fontSize = fitFontSizeSp(nextText, 18f, fsWidth - 136f).sp, letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = LocalTextStyle.current.copy(fontFamily = fontFamily), modifier = Modifier.padding(start = 56.dp))
                        }
                    } else {
                        val lineText = if (fsIndex < 0) "" else if (fsInterlude) "♪" else (fsCurLine?.text ?: "")
                        if (lineText.isNotEmpty()) {
                            Text(
                                lineText,
                                color = Color.White,
                                fontSize = fitFontSizeSp(lineText, 26f, fsWidth - 80f).sp,
                                fontWeight = lyricWeight,
                                letterSpacing = 2.sp,
                                maxLines = 1,
                                style = LocalTextStyle.current.copy(
                                    fontFamily = fontFamily,
                                    shadow = Shadow(color = Color.White.copy(alpha = 0.85f), blurRadius = 18f)
                                )
                            )
                        }
                    }
                }
            }
        }
        }
        // 終了ボタン: もう一度押すと縦画面に戻る
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 18.dp, end = 22.dp)
                .size(44.dp)
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.FullscreenExit,
                contentDescription = "全画面を終了",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
