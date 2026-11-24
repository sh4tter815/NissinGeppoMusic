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
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.viewinterop.AndroidView
import com.example.nissingeppomusic.R
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
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
import com.example.nissingeppomusic.data.LyricWord
import com.example.nissingeppomusic.data.LyricsManager
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.key
import com.example.nissingeppomusic.ui.components.LiquidGradientBackground
import kotlin.math.floor
import kotlin.math.ceil
import androidx.compose.animation.core.exponentialDecay

// 歌詞PVの描画まわり一式（文字PV・間奏アート・アイコン描画・自動フィット・設定のプレビューカード）。
// 正本は音楽アプリ（NissinGeppoMusic）側。変更は音楽側でしてから、こちらへコピーすること。

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
            // 炎: 先端を右へ倒し、左側に小さな flick（はね）を入れた非対称な輪郭＋内側の芯。
            // 左右対称な涙型（DROP）と見間違えないようにする
            val outer = Path().apply {
                moveTo(cx + 2f * u, cy - 34f * u)
                cubicTo(cx + 10f * u, cy - 20f * u, cx + 20f * u, cy - 14f * u, cx + 21f * u, cy - 2f * u)
                cubicTo(cx + 22f * u, cy + 8f * u, cx + 16f * u, cy + 14f * u, cx + 15f * u, cy + 20f * u)
                cubicTo(cx + 13f * u, cy + 30f * u, cx + 6f * u, cy + 33f * u, cx, cy + 33f * u)
                cubicTo(cx - 10f * u, cy + 33f * u, cx - 18f * u, cy + 27f * u, cx - 19f * u, cy + 16f * u)
                cubicTo(cx - 20f * u, cy + 6f * u, cx - 12f * u, cy, cx - 11f * u, cy - 8f * u)
                cubicTo(cx - 10f * u, cy - 15f * u, cx - 4f * u, cy - 18f * u, cx - 3f * u, cy - 24f * u)
                cubicTo(cx - 2f * u, cy - 28f * u, cx, cy - 31f * u, cx + 2f * u, cy - 34f * u)
                close()
            }
            drawPath(outer, col, style = st)
            val core = Path().apply {
                moveTo(cx, cy + 4f * u)
                cubicTo(cx + 6f * u, cy + 12f * u, cx + 9f * u, cy + 17f * u, cx + 9f * u, cy + 22f * u)
                cubicTo(cx + 9f * u, cy + 28f * u, cx - 9f * u, cy + 28f * u, cx - 9f * u, cy + 22f * u)
                cubicTo(cx - 9f * u, cy + 17f * u, cx - 5f * u, cy + 11f * u, cx, cy + 4f * u)
                close()
            }
            drawPath(core, col, style = st)
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
/**
 * 間奏とみなす行間のしきい値（ミリ秒）。曲全体の行間隔の平均 + 10秒。
 * 曲のペースに合わせて変わるので、速い曲では短い空白でも間奏になり、遅い曲では相当長くないとならない。
 */
fun lyricInterludeThresholdMs(lines: List<LyricLine>): Long {
    if (lines.size < 2) return Long.MAX_VALUE / 4
    var sum = 0L
    for (i in 0 until lines.size - 1) sum += (lines[i + 1].timeMs - lines[i].timeMs).coerceAtLeast(0L)
    return sum / (lines.size - 1) + 10000L
}

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
        // 漂う粒子: ゆっくり上昇しながら漂い、明滅する小さな点
        for (i in 0 until 22) {
            val s1 = ((i * 37) % 100) / 100f
            val s2 = ((i * 53 + 17) % 100) / 100f
            val driftX = sin(ph * 0.35f + i * 1.7f) * w * 0.03f
            val rise = (s2 + t * (0.02f + 0.015f * (i % 4))) % 1f
            val px = (s1 * w + driftX).coerceIn(0f, w)
            val py = h * (1f - rise)
            val tw = 0.5f + 0.5f * sin(ph * (0.8f + (i % 5) * 0.23f) + i * 2.1f)
            val r = (1.3f + (i % 3) * 0.8f) * (0.7f + 0.5f * tw)
            drawCircle(
                color = (if (i % 4 == 0) Color.White else accentColor).copy(alpha = 0.10f + 0.30f * tw),
                radius = r,
                center = Offset(px, py)
            )
        }
        // たまにキラッと光る星: 3点が順番に明滅する
        for (i in 0 until 3) {
            val sx = w * (0.22f + 0.27f * i)
            val sy = h * (0.30f + 0.18f * ((i * 2) % 3))
            val pulse = sin(ph * 0.9f + i * 2.1f) * 0.5f + 0.5f
            val sa = pulse * pulse * pulse * 0.85f
            if (sa > 0.03f) drawSparkle(sx, sy, 9f + 5f * pulse, Color(0xFFFFF2B0), sa)
        }
        // 円は出さない（波の線だけにする）
    }
}

/** PV入場の8方向（上・右上・右・右下・下・左下・左・左上） */
/**
 * カラオケ式テロップ表示: 文字同期（words）がある行は、歌い終わった文字から sungColor に変わっていく。
 * words が無い行は baseColor の通常テキストとして描く
 */
// 英字（半角ASCII）の範囲だけ別フォントで描くための混合フォント文字列を作る。
// enFontFamily が null のときは通常の文字列として返す。
// 半角記号（？！括弧など）だけの並びは英語扱いにせず、直前の文のフォントに合わせる
// （日本語の文中に出てくる半角記号は日本語フォント、英語の文中の記号は英語フォントになる）
fun lyricMixedFontText(text: String, enFontFamily: FontFamily?): androidx.compose.ui.text.AnnotatedString {
    if (enFontFamily == null) return androidx.compose.ui.text.AnnotatedString(text)
    // 半角／全角のランに分ける
    val runs = mutableListOf<Triple<Int, Int, Boolean>>()
    var i = 0
    while (i < text.length) {
        val ascii = text[i] in ' '..'~'
        var j = i
        while (j < text.length && (text[j] in ' '..'~') == ascii) j++
        runs.add(Triple(i, j, ascii))
        i = j
    }
    // 英字・数字を含むランだけを「英語」として英語フォントにする
    val useEn = BooleanArray(runs.size)
    for (k in runs.indices) {
        val (s, e, ascii) = runs[k]
        if (!ascii) continue
        if (text.substring(s, e).any { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }) useEn[k] = true
    }
    // 記号だけのランは直前のランの扱いに合わせる（先頭にある場合は直後を見る）
    for (k in runs.indices) {
        if (!runs[k].third || useEn[k]) continue
        useEn[k] = if (k > 0) useEn[k - 1] else (if (runs.size > 1) useEn[k + 1] else false)
    }
    // 1文字だけのランは、日本語の流れの一部として日本語フォントにする（「電ポルP」の P など）
    for (k in runs.indices) {
        val (s, e, _) = runs[k]
        if (e - s == 1) useEn[k] = false
    }
    return buildAnnotatedString {
        for (k in runs.indices) {
            val (s, e, _) = runs[k]
            if (useEn[k]) {
                withStyle(SpanStyle(fontFamily = enFontFamily)) { append(text.substring(s, e)) }
            } else {
                append(text.substring(s, e))
            }
        }
    }
}

@Composable
fun KaraokeLyricText(
    line: LyricLine,
    positionMs: Long,
    sungColor: Color,
    baseColor: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight = FontWeight.Normal,
    fontFamily: FontFamily? = null,
    enFontFamily: FontFamily? = null,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    textAlign: TextAlign = TextAlign.Start,
    shadow: Shadow? = null,
    modifier: Modifier = Modifier
) {
    if (line.words.isEmpty()) {
        Text(
            text = lyricMixedFontText(line.text, enFontFamily),
            color = baseColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            letterSpacing = letterSpacing,
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = LocalTextStyle.current.copy(fontFamily = fontFamily, shadow = shadow),
            modifier = modifier
        )
        return
    }
    // 文字ごとの開始・終了時刻（セグメントの中は均等割りで補間する）
    val timings = remember(line) {
        val list = mutableListOf<Pair<Long, Long>>()
        for (w in line.words) {
            val n = w.text.length.coerceAtLeast(1)
            val dur = (w.endMs - w.startMs).coerceAtLeast(0L)
            for (i in 0 until n) {
                list.add((w.startMs + dur * i / n) to (w.startMs + dur * (i + 1) / n))
            }
        }
        list
    }
    // 前後が半角でない孤立した1文字の半角は、日本語フォントのままにする
    val enCharFlags = BooleanArray(line.text.length) { idx ->
        if (line.text[idx] !in ' '..'~') false
        else (idx > 0 && line.text[idx - 1] in ' '..'~') || (idx < line.text.length - 1 && line.text[idx + 1] in ' '..'~')
    }
    val annotated = buildAnnotatedString {
        var ci = 0
        for (ch in line.text) {
            val t = timings.getOrNull(ci)
            val col = when {
                t == null -> baseColor
                positionMs >= t.second -> sungColor
                positionMs <= t.first -> baseColor
                else -> lerp(baseColor, sungColor, ((positionMs - t.first).toFloat() / (t.second - t.first).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f))
            }
            withStyle(SpanStyle(color = col, fontFamily = if (enFontFamily != null && enCharFlags.getOrElse(ci) { false }) enFontFamily else null)) { append(ch) }
            ci++
        }
    }
    Text(
        text = annotated,
        fontSize = fontSize,
        fontWeight = fontWeight,
        letterSpacing = letterSpacing,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = LocalTextStyle.current.copy(fontFamily = fontFamily, shadow = shadow),
        modifier = modifier
    )
}

/** PVでカタカナ語の下に小さく添える英単語（歌詞でよく出る語だけの内蔵対応表） */
// その行が、前の行から続く括弧の中から始まるかどうか（閉じ括弧だけの行もコーラス扱いにするために使う）
private fun pvStartsInParen(lines: List<LyricLine>, index: Int): Boolean {
    var depth = 0
    for (i in 0 until index.coerceAtMost(lines.size)) {
        for (ch in lines[i].text) {
            if (ch == '（' || ch == '(') depth++
            if (ch == '）' || ch == ')') depth--
        }
    }
    return depth > 0
}

// PV全体の「カメラの手ブレ」用の共有時計（行が切り替わってもカメラの位相が飛ばないよう、開始時刻を全体で共有する）
private object PvCamClock {
    val startMs: Long = System.currentTimeMillis()
}

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
    words: List<LyricWord> = emptyList(),
    startsInParen: Boolean = false,
    lineStartMs: Long = 0L,
    partsEntry: Boolean = false,
    enFontFamily: FontFamily? = null,
    camFlow: Int = -1,
    tilt3d: Boolean = false,
    dopagaki: Boolean = false,
    // 追加設定: 文字サイズ倍率 / 素の文字色 / ルビ表示
    fontScale: Float = 1f,
    baseColor: Color = Color.White,
    rubyEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val pvEaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
    // 改行位置は塊（文節・英単語）の境界で決める。描画とアニメーションは文字ごと（英単語だけ単語ごと）
    val chunks = remember(text) { splitLyricUnits(text) }
    if (chunks.isEmpty()) return
    // 行頭の1・2文字目が両方ひらがなの行は、先頭文字だけを大きくして行の入りを読みやすくする
    val leadHiraganaBoost = remember(text) {
        val head = text.trimStart(' ', '　', '（', '(', '「')
        head.length >= 2 && head[0] in 'ぁ'..'ゟ' && head[1] in 'ぁ'..'ゟ'
    }
    // 文字同期（KRC）がある行用: 文字位置→その文字の開始時刻（絶対ms）のテーブル
    val charStartArr = remember(text, words) {
        if (words.isEmpty()) null else {
            val arr = LongArray(text.length) { lineStartMs }
            var p = 0
            for (w in words) {
                val end = minOf(p + w.text.length, text.length)
                for (i in p until end) arr[i] = w.startMs
                p = end
            }
            if (p in 1 until text.length) {
                val last = words.last().startMs
                for (i in p until text.length) arr[i] = last
            }
            arr
        }
    }
    var pvCharCursor = 0
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
        // 塊が1つしかない長い行（助詞で始まらない長いひらがな文など）は、文字単位で2つに割る
        if (list.size == 1) {
            val s = list[0]
            if (s.length >= 6 && chunkEm(s) > 12f) {
                val total = chunkEm(s)
                val particles = "はがをにへとやのもでからまでよりばねよわ"
                var bestI = -1
                var bestD = Float.MAX_VALUE
                var acc = 0f
                for (i in 1 until s.length) {
                    acc += charEm(s[i - 1]) * 1.02f
                    if (i >= 2 && i <= s.length - 2 && (s[i - 1] in particles || s[i - 1] == ' ' || s[i - 1] == '　')) {
                        val d = kotlin.math.abs(total / 2f - acc)
                        if (d < bestD) { bestD = d; bestI = i }
                    }
                }
                if (bestI < 0) {
                    acc = 0f
                    bestD = Float.MAX_VALUE
                    for (i in 1 until s.length) {
                        acc += charEm(s[i - 1]) * 1.02f
                        val d = kotlin.math.abs(total / 2f - acc)
                        if (d < bestD) { bestD = d; bestI = i }
                    }
                }
                val noStart = "ーぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮ・、。，．！!？?）)】」』"
                while (bestI > 1 && bestI < s.length && s[bestI] in noStart) bestI--
                while (bestI < s.length - 1 && bestI > 0 && s[bestI] in noStart) bestI++
                if (bestI in 1 until s.length) {
                    return listOf(listOf(s.substring(0, bestI)), listOf(s.substring(bestI)))
                }
            }
            return listOf(list)
        }
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
            // スペースで区切られている場合は、その区切りを改行位置として優先する（極端に偏る場合を除く）
            var spaceBest = -1
            var spaceDiff = Float.MAX_VALUE
            var sAcc = 0f
            for (idx in 1 until list.size) {
                sAcc += chunkEm(list[idx - 1]) + gapEm
                val prev = list[idx - 1]
                if (prev.endsWith(" ") || prev.endsWith("　")) {
                    val diff = kotlin.math.abs(totalEm / 2f - sAcc)
                    if (diff < spaceDiff) { spaceDiff = diff; spaceBest = idx }
                }
            }
            if (spaceBest > 0 && spaceDiff <= totalEm * 0.30f) best = spaceBest
            // て形＋補助動詞の境界（取り消して／しまった）も改行位置として優先する
            var auxBest = -1
            var auxDiff = Float.MAX_VALUE
            var aAcc = 0f
            val auxHeads = listOf("しまった", "しまう", "しちゃ", "ちゃった", "いる", "いた", "いく", "くる", "くれる", "もらう", "あげる", "ある", "おく", "みる", "みた", "みたい", "ほしい")
            for (idx in 1 until list.size) {
                aAcc += chunkEm(list[idx - 1]) + gapEm
                val prevEndsTe = list[idx - 1].endsWith("て") || list[idx - 1].endsWith("で")
                if (prevEndsTe && auxHeads.any { list[idx].startsWith(it) }) {
                    val diff = kotlin.math.abs(totalEm / 2f - aAcc)
                    if (diff < auxDiff) { auxDiff = diff; auxBest = idx }
                }
            }
            if (auxBest > 0 && auxDiff <= totalEm * 0.35f) best = auxBest
            // 行頭禁則: 次の行が「ー・小さいカナ・句読点・閉じ括弧」で始まる場合は、分割点をずらす
            val noLineStart = "ーぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮ・、。，．！!？?）)】」』"
            while (best > 1 && list[best].isNotEmpty() && list[best][0] in noLineStart) best--
            while (best < list.size - 1 && list[best].isNotEmpty() && list[best][0] in noLineStart) best++
            // 丁寧語の「お・ご」は後ろの単語と切り離さない（お別れ、ごはん等）
            if (best in 1 until list.size) {
                val prevChunk = list[best - 1]
                if (prevChunk == "お" || prevChunk == "ご") {
                    // お・ご単体の塊は、後ろの単語と同じ行へ送る
                    if (best > 1) best--
                } else if (prevChunk.length >= 2 && (prevChunk.endsWith("お") || prevChunk.endsWith("ご"))) {
                    // 塊の末尾のお・ごは、後ろの単語の塊を引き上げて同じ行にする（無理なら塊ごと次の行へ）
                    if (best < list.size - 1) best++ else if (best > 1) best--
                }
            }
            return listOf(list.subList(0, best), list.subList(best, list.size))
        }
        return listOf(list)
    }
    // 括弧セグメントが長すぎる場合は、丸ごと1行にせず2〜3行に割る（目安: 幅12em超）
    fun splitLongBracket(segChunks: List<String>): List<List<String>> {
        fun emOf(list: List<String>): Float = list.sumOf { chunkEm(it).toDouble() }.toFloat() + gapEm * (list.size - 1).coerceAtLeast(0)
        if (segChunks.size < 2 || emOf(segChunks) <= 12f) return listOf(segChunks)
        var parts = splitChunks(segChunks)
        if (parts.size == 1) return parts
        // まだ長い行があれば、その行だけもう一度割って最大3行にする
        val longestIdx = parts.indices.maxByOrNull { emOf(parts[it]) } ?: -1
        if (longestIdx >= 0 && emOf(parts[longestIdx]) > 12f) {
            val sub = splitChunks(parts[longestIdx])
            if (sub.size == 2) {
                parts = parts.toMutableList().also { it.removeAt(longestIdx); it.addAll(longestIdx, sub) }
            }
        }
        return parts
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
            rowsBuilt.addAll(splitLongBracket(segChunks))
        } else {
            runBuf.addAll(segChunks)
        }
    }
    flushRun()
    // 改行は最大3行まで。括弧を1行にすると3行を超える場合は、括弧ルールを無視して通常分割にする
    val baseRows: List<List<String>> = when {
        rowsBuilt.isEmpty() -> listOf(chunks)
        rowsBuilt.size > 3 -> splitChunks(chunks)
        else -> rowsBuilt
    }
    // クレジット行（作詞・作曲・編曲など）は「：」の直後を改行位置にする
    val creditWords = listOf("作詞", "作曲", "編曲", "作词", "编曲", "歌唱", "演唱", "ボーカル", "Vocal", "Lyrics", "Composer", "Arranger", "詞：", "曲：")
    val colonChunkIdx = if (creditWords.any { text.contains(it) }) chunks.indexOfFirst { it.contains("：") || it.contains(":") } else -1
    val rowsRaw: List<List<String>> = if (colonChunkIdx in 0 until chunks.size - 1) {
        listOf(chunks.subList(0, colonChunkIdx + 1), chunks.subList(colonChunkIdx + 1, chunks.size))
    } else baseRows
    // 1文字だけの行は作らない: 隣の行と合体させる（「お／膳立て」のように割れるのを防ぐ）
    val rows: List<List<String>> = run {
        val fixed = rowsRaw.toMutableList()
        var ri = 0
        while (fixed.size > 1 && ri < fixed.size) {
            val charCount = fixed[ri].sumOf { it.count { ch -> ch != ' ' && ch != '　' } }
            if (charCount == 1) {
                if (ri < fixed.size - 1) {
                    fixed[ri] = fixed[ri] + fixed[ri + 1]
                    fixed.removeAt(ri + 1)
                } else {
                    fixed[ri - 1] = fixed[ri - 1] + fixed[ri]
                    fixed.removeAt(ri)
                }
            } else {
                ri++
            }
        }
        fixed
    }
    fun rowEm(row: List<String>): Float = row.sumOf { chunkEm(it).toDouble() }.toFloat() + gapEm * (row.size - 1).coerceAtLeast(0)
    // ユニットごとの英字フォント適用フラグ。半角でも英字・数字を含むユニットだけを英字フォントにし、
    // 記号だけのユニット（() 等）は直前のユニットの扱いに合わせる（先頭にある場合は直後を見る）。
    // 日本語の文中で半角記号が英字フォントになってしまうのを防ぐ
    val unitEnFlags: List<Boolean> = if (enFontFamily == null) emptyList() else {
        val flat = rows.flatten().flatMap { renderUnitsOf(it) }
        // -1 = 記号のみ（未定）, 0 = 日本語フォント, 1 = 英字フォント
        val state = IntArray(flat.size) { idx ->
            val u = flat[idx]
            when {
                u.isEmpty() || u.any { it !in ' '..'~' } -> 0
                u.length == 1 -> 0 // 1文字だけのユニットは日本語の流れとして日本語フォント
                u.any { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' } -> 1
                else -> -1
            }
        }
        var prevFlag = -1
        for (idx in state.indices) {
            if (state[idx] == -1) {
                if (prevFlag != -1) state[idx] = prevFlag
            } else {
                prevFlag = state[idx]
            }
        }
        var nextFlag = 0
        for (idx in state.indices.reversed()) {
            if (state[idx] == -1) state[idx] = nextFlag else nextFlag = state[idx]
        }
        state.map { it == 1 }
    }
    val longestRowEm = rows.maxOf { rowEm(it) }.coerceAtLeast(1f)
    // 行ごとの大きさを均等に: どの行も「枠いっぱいに広がるサイズ」を基本にし、短い行だけ高さ上限で止める
    val heightCapBase = when {
        rows.size >= 3 -> 40f
        rows.size == 2 -> 52f
        else -> 64f
    }
    val heightCap = if (bigText) heightCapBase * 1.8f else heightCapBase
    val fitSize = (if (dopagaki) minOf((screenWidthDp - 2f) / (longestRowEm * 1.01f), heightCap * 1.7f) else minOf((screenWidthDp - 8f) / (longestRowEm * 1.06f), heightCap)) * fontScale
    val lineOffsetX = ((baseSeed / 7) % 17 - 8).dp
    val lineOffsetY = ((baseSeed / 11) % 13 - 6).dp
    val row2Indent = ((baseSeed / 13) % 4 * 14).dp
    // 退場は1本の時計（exitProgress）で全文字を駆動する（位相だけ文字ごとにずらす）
    val exitProgress = remember { Animatable(0f) }
    // 「落」を含む行は、退場のときに文字をバラさず行ごと画面下へ落下させる
    val fallLine = FALL_WORDS.any { text.contains(it) }
    // 単語で発火する入場・退場エフェクトの対象判定
    // 否定語（ない等）には打ち消し線ではなく☓アイコンを付ける（対象語の開始位置を塊ごとに探す）
    val sandLine = SAND_WORDS.any { text.contains(it) }
    val dreamLine = DREAM_WORDS.any { text.contains(it) }
    val windLine = WIND_WORDS.any { text.contains(it) }
    val rippleWord = RIPPLE_WORDS.any { text.contains(it) }
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
    val dreamTransition = rememberInfiniteTransition(label = "dreamBubble")
    val dreamPhase by dreamTransition.animateFloat(
        initialValue = 0f, targetValue = 6.2832f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 3400, easing = LinearEasing), RepeatMode.Restart),
        label = "dreamPhase"
    )
    val waveTransition = rememberInfiniteTransition(label = "waveBg")
    val wavePhase by waveTransition.animateFloat(
        initialValue = 0f, targetValue = 6.2832f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2600, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase"
    )
    // カメラワーク: 全行に、入場と退場がつながるカメラの流れを付ける。
    // camFlow: 0=右へ流れる（退場は右へ・入場は左から） 1=左へ 2=上へ 3=下へ 4=押し込む（ズームイン系） 5=引く（ズームアウト系） 6=回転（傾いて入ってまっすぐに戻る。退場は同じ向きに回転し続けて抜ける）
    // -1 のときは baseSeed から決める。入場はイーズアウト、退場はイーズインで、退場と次の入場が同じ流れを共有する
    val camFlowEff = if (camFlow >= 0) camFlow else Math.floorMod(baseSeed * 13 + 7, 7)
    val camAnim = remember { Animatable(if (exiting) 1f else 0f) }
    if (!exiting) {
        LaunchedEffect(Unit) {
            camAnim.animateTo(1f, animationSpec = tween(durationMillis = if (camFlowEff >= 4) 1100 else 800, easing = FastOutSlowInEasing))
        }
    }
    val camDensity = LocalDensity.current
    val camSlideXPx = with(camDensity) { (screenWidthDp * 0.24f * TUNE_CAM_SLIDE).dp.toPx() }
    val camSlideYPx = with(camDensity) { (fitSize * 1.7f * TUNE_CAM_SLIDE).dp.toPx() }
    val camDriftAmpXPx = with(camDensity) { (5f * TUNE_DRIFT_AMP).dp.toPx() }
    val camDriftAmpYPx = with(camDensity) { (3.5f * TUNE_DRIFT_AMP).dp.toPx() }
    // 規制の白塗りは行全体で1つの時計を共有し、連なった伏せ字を一気に覆う
    // ドパガキモード: 行全体で共有する虹色の位相（約1.4秒で色相が一周する）
    val dopaPhaseState = if (dopagaki) {
        val dopaTransition = rememberInfiniteTransition(label = "dopa")
        dopaTransition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
            label = "dopaPhase"
        )
    } else null
    // ドパガキモード: 鼓動の位相（約1.05秒周期、行全体で共有する）
    val dopaBeatState = if (dopagaki) {
        val beatTransition = rememberInfiniteTransition(label = "dopaBeat")
        beatTransition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1050, easing = LinearEasing), RepeatMode.Restart),
            label = "dopaBeatPh"
        )
    } else null
    val censorProgress = remember { Animatable(if (exiting) 1f else 0f) }
    if (!exiting) {
        LaunchedEffect(Unit) {
            delay(600)
            censorProgress.animateTo(1f, animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing))
        }
    }
    // カメラの手ブレ: 周期の違うサイン波を重ね、座標がずっとゆるく漂う（位相は共有時計で全行共通）
    // 初期値も現在時刻から入れる（0で始めると、行が切り替わる瞬間にカメラ位置が飛んでガタつくため）
    val camDriftMs = remember { mutableStateOf(System.currentTimeMillis() - PvCamClock.startMs) }
    LaunchedEffect(Unit) {
        while (true) {
            camDriftMs.value = System.currentTimeMillis() - PvCamClock.startMs
            delay(16)
        }
    }
    // 疑似視差用: この行のカメラ平行移動量（スライド＋手ブレ）を状態として共有する。
    // 文字ごとに「大きさ＝奥行き」ぶんのズレを足すことで、カメラが動いたときに視差が出る。
    // 値は PvLyricUnit の graphicsLayer 内でのみ読むので、再コンポーズは走らない
    val camFlowState = rememberUpdatedState(camFlowEff)
    val camExitingState = rememberUpdatedState(exiting)
    val camTransState = remember {
        derivedStateOf {
            val flow = camFlowState.value
            val ex = camExitingState.value
            val camP = if (ex) pvEaseIn.transform(exitProgress.value) else camAnim.value
            var tx = 0f
            var ty = 0f
            when (flow) {
                0 -> tx = (if (ex) camP else camP - 1f) * camSlideXPx
                1 -> tx = (if (ex) -camP else 1f - camP) * camSlideXPx
                2 -> ty = (if (ex) -camP else 1f - camP) * camSlideYPx
                3 -> ty = (if (ex) camP else camP - 1f) * camSlideYPx
            }
            val driftT = camDriftMs.value / 1000f
            val driftX = (Math.sin(driftT * 1.185) * 0.7 + Math.sin(driftT * 0.706 + 1.7) * 0.3).toFloat()
            val driftY = (Math.sin(driftT * 0.938 + 0.6) * 0.7 + Math.sin(driftT * 0.556 + 3.1) * 0.3).toFloat()
            tx += driftX * camDriftAmpXPx
            ty += driftY * camDriftAmpYPx
            Offset(tx, ty)
        }
    }
    // 「深」は縦に下へ、「長」は横に伸びる（時計は行全体で共有し、隣の文字を押す動きと同期させる）
    val hasStretch = STRETCH_VERTICAL_WORDS.any { text.contains(it) } || STRETCH_HORIZONTAL_WORDS.any { text.contains(it) }
    val stretchAnim = remember { Animatable(if (exiting) 1f else 0f) }
    if (!exiting && hasStretch) {
        LaunchedEffect(Unit) {
            delay(450)
            stretchAnim.animateTo(1f, animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing))
        }
    }
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
                // カメラワーク（入場はイーズアウト済みの camAnim、退場は exitProgress をイーズイン変換し、同じ流れで動かす）
                val camP = if (exiting) pvEaseIn.transform(exitProgress.value) else camAnim.value
                when (camFlowEff) {
                    0 -> translationX = (if (exiting) camP else camP - 1f) * camSlideXPx
                    1 -> translationX = (if (exiting) -camP else 1f - camP) * camSlideXPx
                    2 -> translationY = (if (exiting) -camP else 1f - camP) * camSlideYPx
                    3 -> translationY = (if (exiting) camP else camP - 1f) * camSlideYPx
                    4 -> { val s = if (exiting) 1f + 0.5f * camP else 0.72f + 0.28f * camP; scaleX = s; scaleY = s }
                    6 -> {
                        rotationZ = if (exiting) 10f * camP else -14f * (1f - camP)
                        val s = if (exiting) 1f else 0.92f + 0.08f * camP; scaleX = s; scaleY = s
                    }
                    else -> { val s = if (exiting) 1f - 0.3f * camP else 1.45f - 0.45f * camP; scaleX = s; scaleY = s }
                }
                // 手ブレ（サイン波の重ね合わせで、ゆるく漂い続ける）
                val driftT = camDriftMs.value / 1000f
                val driftX = (Math.sin(driftT * 1.185) * 0.7 + Math.sin(driftT * 0.706 + 1.7) * 0.3).toFloat()
                val driftY = (Math.sin(driftT * 0.938 + 0.6) * 0.7 + Math.sin(driftT * 0.556 + 3.1) * 0.3).toFloat()
                translationX += driftX * camDriftAmpXPx
                translationY += driftY * camDriftAmpYPx
                rotationZ += (Math.sin(driftT * 0.647 + 2.0) * 0.35).toFloat()
                // 3Dモード: 行全体を1枚の面として少し奥へ倒す
                if (tilt3d) {
                    rotationX = 15f
                    cameraDistance = 9f * camDensity.density
                }
                // 「落」の行は文字ごとにバラバラに落下する（PvLyricUnit 側で処理）
            }
    ) {
        if (dreamLine) {
            // 「夢・思い出」の行は、行全体をもくもくした雲のフレームで囲う（しっぽなどのアイコンは付けない）
            Canvas(modifier = Modifier.matchParentSize()) {
                val fadeA = if (exiting) 1f - exitProgress.value else 1f
                val col = Color.White.copy(alpha = 0.6f * fadeA)
                val W = size.width
                val H = size.height
                val padX = 16.dp.toPx()
                val padY = 12.dp.toPx()
                val bw = W + padX * 2f
                val bh = H + padY * 2f
                // もくもく感: 輪郭に沿って大きめの膨らみを密に重ね、外向きの弧だけを描く。
                // 各膨らみはサイン波で位相をずらしながら膨らんだりしぼんだりして、雲がうねって見える
                val puffs = 26
                val perimeter = (bw + bh) * 1.57f
                for (i in 0 until puffs) {
                    val t = i.toFloat() / puffs
                    val ang = t * 6.2832f
                    val wobble = kotlin.math.sin(dreamPhase + i * 0.85f)
                    val push = 1f + 0.045f * kotlin.math.sin(dreamPhase * 1.0f + i * 1.7f)
                    val cx = W / 2f + kotlin.math.cos(ang) * (bw / 2f) * push
                    val cy = H / 2f + kotlin.math.sin(ang) * (bh / 2f) * push
                    val baseR = perimeter / puffs * 0.66f * (0.8f + ((i * 37 + baseSeed) % 10) / 20f)
                    val r = baseR * (1f + 0.13f * wobble)
                    drawArc(
                        color = col,
                        startAngle = (ang * 57.2958f) - 62f,
                        sweepAngle = 124f,
                        useCenter = false,
                        topLeft = Offset(cx - r, cy - r),
                        size = Size(r * 2f, r * 2f),
                        style = Stroke(width = 1.6.dp.toPx())
                    )
                }
            }
        }
        if (WAVE_WORDS.any { text.contains(it) }) {
            // 流・波・海は、間奏アートと同じベジェ曲線の波を文字の後ろに敷く
            val waveFade = if (exiting) 1f - exitProgress.value else fxAnim.value
            PvInterludeArt(
                accentColor = accentColor,
                modifier = Modifier.matchParentSize().graphicsLayer { alpha = waveFade.coerceIn(0f, 1f) }
            )
        }
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
                    for (i in 0 until if (dopagaki) 26 else 12) {
                        val ang = (i * 137.5f + baseSeed * 7f) * 0.0174533f
                        val dist = pe * size.width * (0.16f + (i % 4) * 0.05f)
                        val px = bcx + kotlin.math.cos(ang.toDouble()).toFloat() * dist
                        val py = bcy + kotlin.math.sin(ang.toDouble()).toFloat() * dist * 0.6f
                        val pa = (1f - ft) * 0.8f
                        if (i % 3 == 0) {
                            drawSparkle(px, py, (1f - ft * 0.5f) * 13f, if (dopagaki) Color(0xFFFFE97A) else accentColor, pa)
                        } else {
                            drawCircle(color = (if (dopagaki) Color(0xFFFFF2B0) else accentColor).copy(alpha = pa), radius = if (dopagaki) 4.6f else 3.2f, center = Offset(px, py))
                        }
                    }
                }
            }
        }
        if (dopagaki && !exiting) {
            // ドパガキ改: 行のまわりに星屑を散らして瞬かせる
            Canvas(modifier = Modifier.matchParentSize().zIndex(6f)) {
                val phV = dopaPhaseState?.value ?: 0f
                for (i in 0 until 40) {
                    val sx = ((i * 389 + baseSeed * 53) % 100) / 100f * size.width
                    val sy = ((i * 641 + baseSeed * 29) % 100) / 100f * size.height
                    val al = (0.35f + 0.6f * ((i * 29) % 10) / 10f) * (0.55f + 0.45f * sin(phV * 6.2832f + i * 2.39f))
                    drawSparkle(sx, sy, 7f + (i % 3) * 3.5f, Color(0xFFFFF2B0), al * 0.9f)
                }
            }
        }
        // 単語で発火する入場・退場エフェクト: 響=波紋たくさん / 砂・消・壊=粒子化して吹き飛ぶ（モザイクはユニット側で「隠」の文字だけにかける）
        if (sandLine || rippleWord) {
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

            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
        var globalIndex = 0
        var prevUnitGi = -1
        var prevCharFactor = 1f
        var negIconPlaced = false
        var inParen = startsInParen
        rows.forEachIndexed { rowIdx, rowChunks ->
        val unitsInRow = rowChunks.sumOf { renderUnitsOf(it).size }
        var rowUnitIdx = 0
        // 同じ1文字が3つ以上連続する並び（超超超…など）を行単位で先に検出しておく。
        // 対象ユニットは横に詰めて重ね、先頭ほど薄くする
        val rowUnitTexts = rowChunks.flatMap { renderUnitsOf(it) }
        val repeatIdxArr = IntArray(rowUnitTexts.size) { -1 }
        val repeatLenArr = IntArray(rowUnitTexts.size) { 0 }
        val repeatStartArr = IntArray(rowUnitTexts.size) { -1 }
        val repeatSpanArr = IntArray(rowUnitTexts.size) { 0 }
        // 「長」が横に伸びたとき、隣の文字が押される量（近いほど大きく、2つ先まで）
        // 規制表現（伏せ字）の検出: ワード全体ではなく、規制された部分のユニットだけを白塗り対象にする
        val censorStartArr = FloatArray(rowUnitTexts.size) { 0f }
        val censorEndArr = FloatArray(rowUnitTexts.size) { 0f }
        // 連なった伏せ字を一気に覆うため、ユニットの規制区間が「ラン全体の何割〜何割」かも持つ
        val censorRunAArr = FloatArray(rowUnitTexts.size) { 0f }
        val censorRunBArr = FloatArray(rowUnitTexts.size) { 1f }
        run {
            val rowText = rowUnitTexts.joinToString("")
            val ranges = mutableListOf<IntRange>()
            // 「自主規制」は前後の括弧ごと対象にする
            var si = rowText.indexOf("自主規制")
            while (si >= 0) {
                var st = si
                var en = si + 4
                if (st > 0 && rowText[st - 1] in "（(【［") st--
                if (en < rowText.length && rowText[en] in "）)】］") en++
                ranges.add(st until en)
                si = rowText.indexOf("自主規制", en)
            }
            // 伏せ字グリフの連続: 〇○●■□は伏せ字専用に近いので1文字でも対象、*・―・-・×等は2文字以上の連続だけ対象
            val strongMask = setOf('〇', '○', '●', '■', '□')
            val weakMask = setOf('*', '＊', '―', '—', '－', '-', '×', '✕', '✖')
            var mi = 0
            while (mi < rowText.length) {
                if (rowText[mi] in strongMask || rowText[mi] in weakMask) {
                    var mj = mi
                    var hasStrong = false
                    while (mj < rowText.length && (rowText[mj] in strongMask || rowText[mj] in weakMask)) {
                        if (rowText[mj] in strongMask) hasStrong = true
                        mj++
                    }
                    if (hasStrong || mj - mi >= 2) ranges.add(mi until mj)
                    mi = mj
                } else mi++
            }
            if (ranges.isNotEmpty()) {
                var pos = 0
                for (idx in rowUnitTexts.indices) {
                    val uLen = rowUnitTexts[idx].length
                    val uEnd = pos + uLen
                    if (uLen > 0) {
                        for (r in ranges) {
                            val oStart = maxOf(pos, r.first)
                            val oEnd = minOf(uEnd, r.last + 1)
                            if (oEnd > oStart) {
                                val sf = (oStart - pos).toFloat() / uLen
                                val ef = (oEnd - pos).toFloat() / uLen
                                val censorFirst = censorEndArr[idx] == 0f
                                if (censorFirst || sf < censorStartArr[idx]) censorStartArr[idx] = sf
                                if (ef > censorEndArr[idx]) censorEndArr[idx] = ef
                                if (censorFirst) {
                                    val runLenChars = (r.last - r.first + 1).toFloat()
                                    censorRunAArr[idx] = (oStart - r.first).toFloat() / runLenChars
                                    censorRunBArr[idx] = (oEnd - r.first).toFloat() / runLenChars
                                }
                            }
                        }
                    }
                    pos = uEnd
                }
            }
        }
        // ルビ（漢字の直後に括弧書きされた読み仮名）の検出。
        // コーラス括弧と誤認しないよう、直前が漢字・中身は全部かな・短い・読みが漢字数以上、の全部を満たす時だけルビ扱いにする
        // （例: 「恋情（イエス）」のようなカナだけのコーラスは誤判定しうるが、それは仕様として許容する）
        val rubyModeArr = IntArray(rowUnitTexts.size) // 0=なし 1=括弧（表示しない） 2=ルビのかな
        val rubyShiftArr = FloatArray(rowUnitTexts.size)
        val rubyBaseSizeArr = FloatArray(rowUnitTexts.size)
        if (rubyEnabled) run {
            val rowText = rowUnitTexts.joinToString("")
            val unitStartArr = IntArray(rowUnitTexts.size)
            var upos = 0
            for (i in rowUnitTexts.indices) { unitStartArr[i] = upos; upos += rowUnitTexts[i].length }
            fun unitIndexAt(charPos: Int): Int {
                var lo = 0
                for (i in rowUnitTexts.indices) { if (unitStartArr[i] <= charPos) lo = i else break }
                return lo
            }
            fun isKanjiCh(c: Char): Boolean = c in '一'..'鿿' || c == '々' || c == '〆'
            // 各ユニットの実効シードとサイズ係数を、レイアウト側の uSize 計算とまったく同じ手順で再現する。
            // ルビの位置はこの「実際の送り幅」の見積もり精度で決まるため、ランダムな大きさの係数まで一致させる
            val rowGiStart = globalIndex
            val effGiArr = IntArray(rowUnitTexts.size)
            val sizeFactorArr = FloatArray(rowUnitTexts.size)
            run {
                var prevGi = -1
                var prevFactor = 1f
                for (u in rowUnitTexts.indices) {
                    val ru = rowUnitTexts[u]
                    val gi = rowGiStart + u
                    val eGi = if (ru == "ー" && prevGi >= 0) prevGi else gi
                    effGiArr[u] = eGi
                    val cf = when {
                        ru.length > 1 && ru.all { it in '゠'..'ヿ' } -> 1.05f
                        ru.length > 1 -> 0.98f
                        ru.isNotEmpty() && ru[0] in "ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮヵヶ" -> 1.0f
                        ru.isNotEmpty() && (ru[0] in '一'..'鿿' || ru[0] == '々' || ru[0] == '〆' || ru[0] == 'ヶ') -> 1.25f
                        ru.isNotEmpty() && ru[0] in 'ぁ'..'ゟ' -> 0.82f
                        ru.isNotEmpty() && ru[0] in '゠'..'ヿ' -> 1.05f
                        else -> 0.98f
                    }
                    val ecf = if (ru == "ー" && eGi != gi) prevFactor else cf
                    sizeFactorArr[u] = if (gi == 0 && leadHiraganaBoost) 1.25f else ecf
                    prevGi = gi
                    prevFactor = ecf
                }
            }
            // ユニットの送り幅（フォントサイズ×グリフ幅。全角は1em、小書きかなはスパンで0.6倍、括弧ユニットは非表示で0）
            fun unitAdvance(u: Int): Float {
                if (rubyModeArr[u] == 1) return 0f
                val ru = rowUnitTexts[u]
                val seed = Math.floorMod(baseSeed * 7 + effGiArr[u] * 131, 997)
                val rand = if (rubyModeArr[u] == 2) 1f else 0.92f + (seed % 22) / 100f
                var sz = (fitSize * rand * sizeFactorArr[u]).coerceIn(fitSize * 0.55f, fitSize * 1.35f)
                if (rubyModeArr[u] == 2) sz *= 0.52f
                var adv = 0f
                for (ch in ru) adv += sz * (if (ch in "ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮヵヶ") 0.6f else 1f)
                return adv
            }
            fun flowLeft(u: Int): Float {
                var acc = 0f
                for (k in 0 until u) acc += unitAdvance(k)
                return acc
            }
            fun uXShiftOf(u: Int): Float = ((unitsInRow - 1) / 2f - u) * fitSize * 0.05f
            var i = 0
            while (i < rowText.length) {
                val open = rowText[i]
                if (open == '（' || open == '(') {
                    val closeCh = if (open == '（') '）' else ')'
                    val close = rowText.indexOf(closeCh, i + 1)
                    if (close > i + 1 && i > 0 && isKanjiCh(rowText[i - 1])) {
                        val content = rowText.substring(i + 1, close)
                        val allKana = content.all { it in 'ぁ'..'ゟ' || it in 'ァ'..'ヶ' || it == 'ー' || it == '・' }
                        if (allKana && content.length in 1..8) {
                            var b = i
                            while (b > 0 && isKanjiCh(rowText[b - 1])) b--
                            val baseLen = i - b
                            if (baseLen in 1..6 && content.length >= baseLen) {
                                rubyModeArr[unitIndexAt(i)] = 1
                                rubyModeArr[unitIndexAt(close)] = 1
                                val baseFirst = unitIndexAt(b)
                                val baseLast = unitIndexAt(i - 1)
                                val firstRubyUnit = unitIndexAt(i + 1)
                                val lastRubyUnit = unitIndexAt(close - 1)
                                for (u in firstRubyUnit..lastRubyUnit) rubyModeArr[u] = 2
                                // グループルビ: 漢字全体の見た目の幅をルビのユニット数で等分し、各かなをその区間の中央に置く。
                                // 送り幅と寄せ量（uXShift）まで含めて実レイアウトと一致させるので、漢字の大きさがバラついてもずれない
                                val baseVisualLeft = flowLeft(baseFirst) + uXShiftOf(baseFirst)
                                val baseVisualRight = flowLeft(baseLast) + unitAdvance(baseLast) + uXShiftOf(baseLast)
                                val baseVisualWidth = baseVisualRight - baseVisualLeft
                                val rubyCount = lastRubyUnit - firstRubyUnit + 1
                                if (rubyCount > 0) {
                                    var baseSizeSum = 0f
                                    for (u in baseFirst..baseLast) {
                                        val bSeed = Math.floorMod(baseSeed * 7 + effGiArr[u] * 131, 997)
                                        val bRand = 0.92f + (bSeed % 22) / 100f
                                        baseSizeSum += (fitSize * bRand * sizeFactorArr[u]).coerceIn(fitSize * 0.55f, fitSize * 1.35f)
                                    }
                                    val baseAvg = baseSizeSum / (baseLast - baseFirst + 1)
                                    for (u in firstRubyUnit..lastRubyUnit) rubyBaseSizeArr[u] = baseAvg
                                }
                                if (rubyCount > 0 && baseVisualWidth > 0f) {
                                    for (j in 0 until rubyCount) {
                                        val u = firstRubyUnit + j
                                        val targetCenter = baseVisualLeft + (j + 0.5f) * baseVisualWidth / rubyCount
                                        rubyShiftArr[u] = targetCenter - unitAdvance(u) / 2f - flowLeft(u) - uXShiftOf(u)
                                    }
                                }
                                i = close
                            }
                        }
                    }
                }
                i++
            }
            // ルビのかなは Row の中で実幅を取ってしまうため、かなの後ろのユニットを
            // かな幅の累計分だけ引き戻し、漢字の次の文字との間隔が空かないようにする
            run {
                var pullAcc = 0f
                val pullArr = FloatArray(rowUnitTexts.size)
                var u = 0
                while (u < rowUnitTexts.size) {
                    if (rubyModeArr[u] == 2) {
                        var kanaTotal = 0f
                        var v = u
                        while (v < rowUnitTexts.size && rubyModeArr[v] == 2) { kanaTotal += unitAdvance(v); v++ }
                        pullAcc += kanaTotal
                        u = v
                    } else {
                        pullArr[u] = pullAcc
                        u++
                    }
                }
                for (w in rowUnitTexts.indices) rubyShiftArr[w] -= pullArr[w]
            }
        }
        val stretchPushArr = FloatArray(rowUnitTexts.size) { r ->
            var f = 0f
            for (s in rowUnitTexts.indices) {
                if (s != r && STRETCH_HORIZONTAL_WORDS.any { w -> rowUnitTexts[s].contains(w) }) {
                    val d = kotlin.math.abs(r - s)
                    val fall = when (d) { 1 -> 1.0f; 2 -> 0.45f; else -> 0f }
                    f += (if (r > s) 1f else -1f) * fall
                }
            }
            f
        }
        run {
            var runStart = 0
            while (runStart < rowUnitTexts.size) {
                val t = rowUnitTexts[runStart]
                var runEnd = runStart + 1
                while (runEnd < rowUnitTexts.size && rowUnitTexts[runEnd] == t) runEnd++
                val runLen = runEnd - runStart
                val ch = t.singleOrNull()
                // 四字熟語（堂々・戦々恐々など）や通常語の誤反応を避けるため、文字の種類を問わず3連続から対象にする
                val need = 3
                if (runLen >= need && ch != null && !ch.isWhitespace() && ch !in "。、・…‥！？!?ー") {
                    for (k in runStart until runEnd) {
                        repeatIdxArr[k] = k - runStart
                        repeatLenArr[k] = runLen
                        repeatStartArr[k] = runStart
                        repeatSpanArr[k] = runLen
                    }
                }
                runStart = runEnd
            }
        }
        // 単語・フレーズ単位の繰り返し（誰も誰も誰も、のような型）も重ね表示の対象にする。
        // 1文字の連続は上の処理が担当するので、ここでは2ユニット以上の型だけを見る
        run {
            var s = 0
            while (s < rowUnitTexts.size) {
                if (repeatLenArr[s] > 0) {
                    s++
                    continue
                }
                var placed = false
                for (blockLen in 2..6) {
                    if (s + blockLen * 2 > rowUnitTexts.size) break
                    val block = rowUnitTexts.subList(s, s + blockLen)
                    // 句読点・空白・長音を含む型と、全て同じユニットの型（1文字連続側で処理）は対象外
                    if (block.any { it.isBlank() || it in listOf("。", "、", "・", "…", "‥", "！", "？", "!", "?", "ー") }) continue
                    if (block.all { it == block[0] }) continue
                    // 繰り返しの間にスペースが挟まっていてもよい（ズキリ ズキリ、のような表記ゆれ対応）
                    val starts = mutableListOf(s)
                    var cursor = s + blockLen
                    while (true) {
                        var probe = cursor
                        while (probe < rowUnitTexts.size && rowUnitTexts[probe].isBlank()) probe++
                        if (probe + blockLen > rowUnitTexts.size) break
                        if (rowUnitTexts.subList(probe, probe + blockLen) != block) break
                        starts.add(probe)
                        cursor = probe + blockLen
                    }
                    val occ = starts.size
                    // 2回だけの繰り返しは、四字熟語やオノマトペの定型（ABAB）と区別がつかないため対象外。
                    // 強調の繰り返しとして扱うのは3回以上だけにする
                    if (occ < 3) continue
                    val spanEnd = cursor
                    // 型の途中に、すでに1文字連続で割り当て済みのユニットが混ざっていたらやめる
                    if ((s until spanEnd).any { repeatLenArr[it] > 0 }) continue
                    starts.forEachIndexed { oi, st ->
                        for (k in st until st + blockLen) {
                            repeatIdxArr[k] = oi
                            repeatLenArr[k] = occ
                            repeatStartArr[k] = s
                            repeatSpanArr[k] = spanEnd - s
                        }
                    }
                    s = spanEnd
                    placed = true
                    break
                }
                if (!placed) s++
            }
        }
        // 規制範囲にかかるユニットは、繰り返しの重ねエフェクトを解除して規制の白塗りを優先する
        for (idx in rowUnitTexts.indices) {
            if (censorEndArr[idx] > censorStartArr[idx]) {
                repeatIdxArr[idx] = -1
                repeatLenArr[idx] = 0
            }
        }
        Box {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (rowIdx > 0) Modifier.padding(start = row2Indent, top = (if ((0 until rowIdx).any { rd -> rows[rd].any { c -> STRETCH_VERTICAL_WORDS.any { w -> c.contains(w) } } }) (stretchAnim.value * fitSize * 0.45f).dp else 0.dp)) else Modifier
        ) {
            rowChunks.forEachIndexed { posInRow, chunk ->
                if (posInRow > 0 && latinDominant) {
                    Spacer(Modifier.width((fitSize * 0.32f).dp))
                }
                // コーラス部分（括弧の中・yeah等の掛け声）は薄く表示する
                val chunkDim = inParen || chunk.startsWith("（") || chunk.startsWith("(") || isChorusWord(chunk)
                if (chunk.contains("（") || chunk.contains("(")) inParen = true
                if (chunk.contains("）") || chunk.contains(")")) inParen = false
                val chunkIconMatches = findIconWords(chunk)
                // 否定語の開始位置（この塊の中に否定語があれば、その文字に☓アイコンを付ける）
                val chunkNegStart = NEG_WORDS.mapNotNull { w -> chunk.indexOf(w).takeIf { it >= 0 } }.minOrNull() ?: -1
                val chunkGhost = GHOST_WORDS.any { chunk.contains(it) }
                val chunkPileWord = PILE_WORDS.firstOrNull { chunk.contains(it) }
                val chunkPileStart = chunkPileWord?.let { chunk.indexOf(it) } ?: -1
                // 液体っぽい単語は、文字の下端から液が垂れるエフェクトにする（対象語と色はデータ配信側で管理）
                var dripStartFound = -1
                val chunkDrip: Pair<String, Color>? = DRIP_WORDS.firstOrNull { (w, _) ->
                    val ex = DRIP_EXCLUSIONS[w] ?: emptyList()
                    var from = 0
                    var ok = false
                    while (true) {
                        val i = chunk.indexOf(w, from)
                        if (i < 0) break
                        if (ex.any { chunk.startsWith(it, i) }) {
                            from = i + 1
                            continue
                        }
                        dripStartFound = i
                        ok = true
                        break
                    }
                    ok
                }
                val chunkDripStart = if (chunkDrip != null) dripStartFound else -1
                // バラバラ・壊れる系の単語は、設定に関係なく文字をパーツに分解したまま揺らす
                val chunkPartsWord = PARTS_WORDS.firstOrNull { chunk.contains(it) }
                val chunkPartsStart = chunkPartsWord?.let { chunk.indexOf(it) } ?: -1
                val chunkMosaicWord = MOSAIC_WORDS.firstOrNull { chunk.contains(it) }
                val chunkMosaicStart = chunkMosaicWord?.let { chunk.indexOf(it) } ?: -1
                // 跳ねる系の単語は、対象の文字だけスプリングで弾んで入場する（対象語はデータ配信側で管理）
                val chunkBounceWord = BOUNCE_WORDS.firstOrNull { chunk.contains(it) }
                val chunkBounceStart = chunkBounceWord?.let { chunk.indexOf(it) } ?: -1
                // 震える系の単語は、対象の文字だけ表示中ずっと小刻みに震える（対象語はデータ配信側で管理）
                val chunkQuakeWord = QUAKE_WORDS.firstOrNull { chunk.contains(it) }
                val chunkQuakeStart = chunkQuakeWord?.let { chunk.indexOf(it) } ?: -1
                // 複数文字の色ワード（希望・想い等）は塊単位で判定し、該当する文字だけに色を渡す
                val chunkColorHit = (COLOR_WORDS + ASSOC_COLOR_WORDS).firstOrNull { (w, _) -> w.length > 1 && chunk.contains(w) }
                val chunkColorStart = chunkColorHit?.let { chunk.indexOf(it.first) } ?: -1
                val chunkArrow = Regex("行[くきけこっか]").containsMatchIn(chunk)
                val chunkUnits = renderUnitsOf(chunk)
                var chunkCharPos = 0
                var unitIdxInChunk = 0
                for (ru in chunkUnits) {
                    val gi = globalIndex++
                    var thisUnitIcon: Int? = null
                    unitIdxInChunk++
                    val rIdx = rowUnitIdx++
                    // 文字同士がほんの少し被るくらいに、行の中心へ向けて詰める（画面外へのはみ出しは増やさない）
                    val uXShift = ((unitsInRow - 1) / 2f - rIdx) * fitSize * 0.05f
                    // 同字連続のユニットは並びの中心へさらに寄せて、横に重なるようにする
                    val repeatIdxHere = repeatIdxArr.getOrElse(rIdx) { -1 }
                    val repeatLenHere = repeatLenArr.getOrElse(rIdx) { 0 }
                    val repeatStartHere = repeatStartArr.getOrElse(rIdx) { -1 }
                    val repeatSpanHere = repeatSpanArr.getOrElse(rIdx) { 0 }
                    val repeatShift = if (repeatLenHere >= 2 && repeatIdxHere >= 0) {
                        val spanHere = if (repeatSpanHere > 0) repeatSpanHere else repeatLenHere
                        val runCenter = repeatStartHere + (spanHere - 1) / 2f
                        (runCenter - rIdx) * fitSize * 0.20f
                    } else 0f
                    val rubyModeHere = rubyModeArr.getOrElse(rIdx) { 0 }
                    val rubyShiftHere = rubyShiftArr.getOrElse(rIdx) { 0f }
                    val uXShiftTotal = uXShift + repeatShift + rubyShiftHere
                    // 長音（ー）は前の文字と1パーツとして扱い、座標・角度・揺れの種を前の文字に統一する（バラけると読みづらいため）
                    val effGi = if (ru == "ー" && prevUnitGi >= 0) prevUnitGi else gi
                    prevUnitGi = gi
                    val uSeed = Math.floorMod(baseSeed * 7 + effGi * 131, 997)
                    val charFactor = when {
                        ru.length > 1 && ru.all { it in '゠'..'ヿ' } -> 1.05f // カタカナ単語
                        ru.length > 1 -> 0.98f // 英単語
                        ru[0] in "ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮヵヶ" -> 1.0f // 小カナの縮小はPvLyricUnit内のスパンで1回だけ行う
                        ru[0] in '一'..'鿿' || ru[0] == '々' || ru[0] == '〆' || ru[0] == 'ヶ' -> 1.25f
                        ru[0] in 'ぁ'..'ゟ' -> 0.82f
                        ru[0] in '゠'..'ヿ' -> 1.05f
                        else -> 0.98f
                    }
                    // 長音は大きさの係数も前の文字に統一する（前の文字と段差が出ると別パーツに見えるため）
                    val effCharFactor = if (ru == "ー" && effGi != gi) prevCharFactor else charFactor
                    prevCharFactor = effCharFactor
                    // 行頭がひらがな続きの行は、先頭の1文字だけ漢字並みの大きさにする（2文字目より大きく）
                    val sizeFactor = if (gi == 0 && leadHiraganaBoost) 1.25f else effCharFactor
                    val unitStart = chunkCharPos; chunkCharPos += ru.length
                    // アイコン単語は塊の中の全部が対象（時計→時計、見る→目、のように複数出せる）
                    if (thisUnitIcon == null) {
                        thisUnitIcon = chunkIconMatches.firstOrNull { it.third >= unitStart && it.third < unitStart + ru.length }?.second
                    }
                    // 文字同期がある行では、このユニットの実時刻（行頭からの差分）で入場させる
                    var unitDelayMs = -1L
                    if (charStartArr != null) {
                        charStartArr.getOrNull(pvCharCursor)?.let { unitDelayMs = (it - lineStartMs).coerceIn(0L, 6000L) }
                        pvCharCursor += ru.length
                    }
                    // 否定語の先頭文字には☓アイコン（既に別アイコンが付いている場合はそちらを優先）
                    if (!negIconPlaced && thisUnitIcon == null && chunkNegStart >= 0 && unitStart <= chunkNegStart && chunkNegStart < unitStart + ru.length) {
                        thisUnitIcon = LyricIcon.CROSS
                        negIconPlaced = true
                    }
                    // モザイク対象語の文字にだけモザイクをかける
                    val mosaicStart = chunkMosaicStart
                    val mosaicLen = chunkMosaicWord?.length ?: 0
                    val unitMosaic = mosaicStart >= 0 && unitStart < mosaicStart + mosaicLen && unitStart + ru.length > mosaicStart
                    // 「積もる・重なる」系の文字は、同じ文字を上に重ねて表現する
                    val unitStretchKind = when {
                        STRETCH_VERTICAL_WORDS.any { w -> ru.contains(w) } -> 1
                        STRETCH_HORIZONTAL_WORDS.any { w -> ru.contains(w) } -> 2
                        else -> 0
                    }
                    val unitPile = chunkPileWord != null && unitStart < chunkPileStart + chunkPileWord.length && unitStart + ru.length > chunkPileStart
                    val unitDrip = if (chunkDrip != null && unitStart < chunkDripStart + chunkDrip.first.length && unitStart + ru.length > chunkDripStart) chunkDrip.second else null
                    val unitParts = chunkPartsWord != null && unitStart < chunkPartsStart + chunkPartsWord.length && unitStart + ru.length > chunkPartsStart
                    val unitBounce = chunkBounceWord != null && unitStart < chunkBounceStart + chunkBounceWord.length && unitStart + ru.length > chunkBounceStart
                    val unitQuake = chunkQuakeWord != null && unitStart < chunkQuakeStart + chunkQuakeWord.length && unitStart + ru.length > chunkQuakeStart
                    val unitChunkColor = if (chunkColorHit != null && unitStart < chunkColorStart + chunkColorHit.first.length && unitStart + ru.length > chunkColorStart) chunkColorHit.second else null
                    val censorStartHere = censorStartArr.getOrElse(rIdx) { 0f }
                    val censorEndHere = censorEndArr.getOrElse(rIdx) { 0f }
                    val unitCensored = censorEndHere > censorStartHere
                    PvLyricUnit(
                        bold = bold,
                        unitText = if (rubyModeHere == 1) "" else ru,
                        uSize = if (rubyModeHere == 2) ((fitSize * sizeFactor).coerceIn(fitSize * 0.55f, fitSize * 1.35f) * 0.52f).sp else ((fitSize * (0.92f + (uSeed % 22) / 100f) * sizeFactor).coerceIn(fitSize * 0.55f, fitSize * 1.35f)).sp,
                        uRot = if (unitCensored || rubyModeHere == 2) 0f else ((uSeed / 3) % 29 - 14).toFloat(),
                        uYOffset = if (unitCensored || rubyModeHere == 2) 0.dp else ((uSeed / 5) % 17 - 8).dp,
                        uXShiftDp = if (unitCensored) 0f else uXShiftTotal,
                        staggerIndex = effGi,
                        exiting = exiting,
                        exitProgressValue = if (sandLine && !fallLine) (exitProgress.value * 1.6f).coerceAtMost(1f) else exitProgress.value,
                        exitDelayMs = (uSeed % 5) * 35f + gi * 12f,
                        fontFamily = if (unitEnFlags.getOrElse(gi) { false }) enFontFamily else fontFamily,
                        accentColor = accentColor,
                        effectKind = effKind,
                        iconKind = thisUnitIcon,
                        dimmed = chunkDim,
                        ghost = chunkGhost,
                        mosaic = unitMosaic,
                        pile = unitPile,
                        dripColor = unitDrip,
                        fallExit = fallLine,
                        windExit = false,
                        crumbleExit = if (fallLine) 0 else if (sandLine) 1 else if (windLine) 2 else 0,
                        chunkColor = unitChunkColor,
                        baseColor = baseColor,
                        arrowUnder = chunkArrow && ru == "行",
                        swayScale = if (unitCensored || rubyModeHere == 2) 0f else if (bigText) 2.8f else 1f,
                        entryDelayMs = unitDelayMs,
                        partsEntry = partsEntry,
                        wordParts = unitParts,
                        bounce = unitBounce,
                        quake = unitQuake,
                        dopagaki = dopagaki,
                        dopaPhase = dopaPhaseState,
                        dopaBeat = dopaBeatState,
                        repeatIndex = repeatIdxHere,
                        repeatCount = repeatLenHere,
                        stretchKind = unitStretchKind,
                        stretchProgress = stretchAnim.value,
                        pushXFactor = stretchPushArr.getOrElse(rIdx) { 0f },
                        censorStart = censorStartHere,
                        censorEnd = censorEndHere,
                        censorUnit = unitCensored,
                        censorBandSp = fitSize * 1.18f,
                        censorRunA = censorRunAArr.getOrElse(rIdx) { 0f },
                        censorRunB = censorRunBArr.getOrElse(rIdx) { 1f },
                        censorT = censorProgress.value,
                        lineDurationMs = lineDurationMs,
                        rubyKana = rubyModeHere == 2,
                        rubyBaseSizeSp = rubyBaseSizeArr.getOrElse(rIdx) { 0f },
                        parallaxState = camTransState,
                        parallaxDepth = if (rubyModeHere == 2) 0.25f else effCharFactor - 1f
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
    mosaic: Boolean = false,
    pile: Boolean = false,
    dripColor: Color? = null,
    fallExit: Boolean = false,
    windExit: Boolean = false,
    crumbleExit: Int = 0,
    chunkColor: Color? = null,
    baseColor: Color = Color.White,
    arrowUnder: Boolean = false,
    entryDelayMs: Long = -1L,
    partsEntry: Boolean = false,
    wordParts: Boolean = false,
    bounce: Boolean = false,
    quake: Boolean = false,
    dopagaki: Boolean = false,
    dopaPhase: State<Float>? = null,
    dopaBeat: State<Float>? = null,
    repeatIndex: Int = -1,
    repeatCount: Int = 0,
    stretchKind: Int = 0,
    stretchProgress: Float = 0f,
    pushXFactor: Float = 0f,
    censorStart: Float = 0f,
    censorEnd: Float = 0f,
    censorRunA: Float = 0f,
    censorRunB: Float = 1f,
    censorT: Float = 0f,
    censorUnit: Boolean = false,
    censorBandSp: Float = 0f,
    lineDurationMs: Long = 0L,
    rubyKana: Boolean = false,
    rubyBaseSizeSp: Float = 0f,
    parallaxState: State<Offset>? = null,
    parallaxDepth: Float = 0f
) {
    val pvEaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)
    // 文字同期がある行では実時刻ディレイ、無い行では従来の等間隔ディレイで入場する
    val effDelayMs = if (entryDelayMs >= 0L) entryDelayMs else staggerIndex * 45L
    val uSeed = staggerIndex
    val uAnim = remember(staggerIndex) { Animatable(0f) }
    // 跳ねる単語の文字は、少し上からスプリングで落ちてきて、着地で小さく跳ね返る
    val bounceLift = remember(staggerIndex) { Animatable(1f) }
    if (!exiting && bounce) {
        LaunchedEffect(staggerIndex) {
            delay(effDelayMs)
            bounceLift.animateTo(0f, animationSpec = spring(dampingRatio = 0.32f, stiffness = 320f))
        }
    }
    // 震える単語の文字だけ、速い周期の位相を共有する（対象ユニットが少ないのでコストは小さい）
    val quakePhaseState = if (quake && !exiting) {
        val quakeTransition = rememberInfiniteTransition(label = "quake")
        quakeTransition.animateFloat(
            initialValue = 0f, targetValue = 6.2832f,
            animationSpec = infiniteRepeatable(tween(300, easing = LinearEasing), RepeatMode.Restart),
            label = "quakePhase"
        )
    } else null
    // 表示中ずっと、位置がごく小さくぐらぐら揺れる（角度は変えない）
    // 退場用インスタンスでは揺れ時計を表示経過時間から逆算した位相で止める。
    // 位相が0に戻ると液体ドリップなどが退場の瞬間にパッと縮んで「外れた」ように見えるため
    val swayInit = if (exiting) {
        val periodMs = 2400L + (uSeed % 5) * 380L
        val elapsedMs = lineDurationMs - effDelayMs
        if (lineDurationMs > 0L && elapsedMs > 0L) (elapsedMs % periodMs).toFloat() / periodMs.toFloat() else 0f
    } else 0f
    val sway = remember(staggerIndex) { Animatable(swayInit) }
    if (!exiting) {
        LaunchedEffect(staggerIndex) {
            delay(effDelayMs)
            uAnim.animateTo(1f, animationSpec = if (dopagaki) tween(durationMillis = 360, easing = FastOutSlowInEasing) else tween(durationMillis = 380, easing = FastOutSlowInEasing))
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
    // ドパガキ中は文字の周りのグローを虹色にする（位相は24段階に量子化して再描画を抑える）
    val glowColor = if (dopagaki) {
        val ph = dopaPhase?.value ?: 0f
        Color.hsv((((ph * 24f).toInt() * 15 + uSeed * 47) % 360).toFloat(), 0.62f, 1f)
    } else accentColor
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
            delay(effDelayMs)
            ulAnim.animateTo(1f, animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing))
            ulAnim.animateTo(2f, animationSpec = tween(durationMillis = 260, easing = pvEaseIn))
        }
    }
    // 粒子化して消える文字（消・砂・壊・砕=落下、風=右へ）は、描画済みの文字を画像として記録し、
    // その形のまま粒子に分解する。取れなかった場合は通常の退場に任せる
    val crumbleLayer = rememberGraphicsLayer()
    var crumblePoints by remember { mutableStateOf<FloatArray?>(null) }
    // 粒子が動き出すまでは文字本体を出し続ける（粒子側の開始待ちで一瞬消えるのを防ぐ）
    val crumbleFp = if (exiting && crumbleExit != 0) ((exitProgressValue * 720f - exitDelayMs) / 560f).coerceIn(0f, 1f) else 0f
    LaunchedEffect(exiting) {
        if (exiting && crumbleExit != 0 && crumblePoints == null) {
            // 退場用インスタンスのレイヤーは作られた直後だとまだ空で画像化に失敗することがある。取れるまで数フレーム粘る
            var attempt = 0
            while (crumblePoints == null && attempt < 14) {
            try {
                val bmp = crumbleLayer.toImageBitmap().asAndroidBitmap()
                val w = bmp.width
                val h = bmp.height
                if (w > 4 && h > 4) {
                    val px = IntArray(w * h)
                    bmp.getPixels(px, 0, w, 0, 0, w, h)
                    val step = maxOf(1, minOf(w, h) / 26)
                    val pts = mutableListOf<Float>()
                    var yy = step / 2
                    while (yy < h) {
                        var xx = step / 2
                        while (xx < w) {
                            val a = (px[yy * w + xx] ushr 24) and 0xFF
                            if (a > 110) {
                                pts.add(xx / w.toFloat())
                                pts.add(yy / h.toFloat())
                            }
                            xx += step
                        }
                        yy += step
                    }
                    val pairCount = pts.size / 2
                    val out = if (pairCount > 300) {
                        val stride = pairCount / 300 + 1
                        val o = mutableListOf<Float>()
                        var pi = 0
                        while (pi < pairCount) {
                            o.add(pts[pi * 2])
                            o.add(pts[pi * 2 + 1])
                            pi += stride
                        }
                        o.toFloatArray()
                    } else pts.toFloatArray()
                    if (out.isNotEmpty()) crumblePoints = out
                }
            } catch (e: Exception) {
                // 取れなければ次のフレームで取り直す（最後まで取れなければ通常の退場に任せる）
            }
            if (crumblePoints == null) delay(24)
            attempt++
            }
        }
    }
    // 色名の文字を、その色で左上から右下へ斜めに塗っていく
    // 退場中も色が消えないよう、exiting でも色ヒットは維持する
    val colorHit = (if (chunkColor != null) unitText to chunkColor else null) ?: COLOR_WORDS.firstOrNull { (word, _) ->
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
    }
    // 退場用に別インスタンスで作られた場合は、塗りが完了した状態から始める
    val wipeT = remember { Animatable(if (exiting) 1f else 0f) }
    if (colorHit != null) {
        LaunchedEffect(staggerIndex) {
            delay(effDelayMs + 200L)
            wipeT.animateTo(1f, animationSpec = tween(durationMillis = 650, easing = LinearEasing))
        }
    }
    // 規制表現の白塗り: 行全体で共有する進行度（censorT）を使い、連なった伏せ字を一気に覆う。
    // 自分の担当区間（censorRunA〜censorRunB）に進行度が差しかかった分だけ塗る
    val censorLocal = if (censorRunB > censorRunA) ((censorT - censorRunA) / (censorRunB - censorRunA)).coerceIn(0f, 1f) else censorT
    // 点滅(3)とグリッチ(4)用の進行度
    val fxT = remember { Animatable(0f) }
    if (!exiting && (effectKind == 3 || effectKind == 4)) {
        LaunchedEffect(staggerIndex) {
            delay(effDelayMs)
            fxT.animateTo(1f, animationSpec = tween(durationMillis = 800, easing = LinearEasing))
        }
    }
    // 同じ文字が3つ以上連続する場合、先頭ほど薄くする（横重ねエフェクト用）
    val repeatAlphaF = if (repeatCount >= 2 && repeatIndex >= 0) 0.42f + 0.58f * (repeatIndex.toFloat() / (repeatCount - 1).coerceAtLeast(1)) else 1f
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
                    alpha = (if (pe < 0.6f) 1f else 1f - (pe - 0.6f) / 0.4f) * (if (ghost) 0.38f else 1f) * repeatAlphaF
                    rotationZ = uRot + dirR * pe * (22f + (uSeed % 38))
                    translationX = dirR * pe * pe * (14f + (uSeed % 26))
                    translationY = pe * pe * (560f + (uSeed % 30) * 9f)
                } else if (exiting && windExit) {
                    // 「風」の行: 風に吹かれて右へ飛んでいきながら薄れる
                    val wx = ((exitProgressValue * 720f - exitDelayMs) / 520f).coerceIn(0f, 1f)
                    val wd = wx * wx
                    val wDir = if (uSeed % 2 == 0) 1f else -1f
                    alpha = (if (wx < 0.55f) 1f else 1f - (wx - 0.55f) / 0.45f) * (if (ghost) 0.38f else 1f) * repeatAlphaF
                    rotationZ = uRot + wd * (55f + (uSeed % 50)) * wDir
                    translationX = wd * (360f + (uSeed % 40) * 7f)
                    translationY = wd * ((uSeed % 41) - 20) * 2.4f - wd * 26f
                    val ws = 1f - wd * 0.15f
                    scaleX = ws
                    scaleY = ws
                } else if (exiting && crumbleExit != 0) {
                    // 粒子化して消える文字は、崩れる表現は粒子側が担当するので本体は姿勢だけ保つ
                    alpha = (if (ghost) 0.38f else 1f) * repeatAlphaF
                    rotationZ = uRot
                } else if (exiting) {
                    val localX = ((exitProgressValue * 720f - exitDelayMs) / 320f).coerceIn(0f, 1f)
                    val a = 1f - pvEaseIn.transform(localX)
                    alpha = a * (if (ghost) 0.38f else 1f) * repeatAlphaF
                    val es = 0.5f + 0.5f * a
                    scaleX = es
                    scaleY = es
                    rotationZ = uRot * (2f - a)
                    translationY = (1f - a) * 30f
                } else {
                    val a = if ((partsEntry || wordParts)) 1f else uAnim.value
                    val blinkF = if (effectKind == 3 && fxT.value < 1f) {
                        if (sin(fxT.value * 21.99f + uSeed * 1.3f) > -0.2f) 1f else 0.12f
                    } else 1f
                    alpha = a * blinkF * (if (ghost) 0.38f else 1f) * repeatAlphaF
                    val ns = 0.6f + 0.4f * a
                    scaleX = ns
                    scaleY = ns
                    rotationZ = uRot * a
                    // 入場は単位ごとにバラバラの方向（8方向）から飛んでくる
                    val dir = PV_ENTRY_DIRS[Math.floorMod(uSeed * 131 + 7, 8)]
                    // 波に乗るような揺れ: サイン波でなめらかに、位相は文字ごとにずらす
                    val ph = sway.value * 6.2832f + (uSeed % 7) * 0.9f
                    val swx = sin(ph) * 3.4f * swayScale * TUNE_SWAY_AMP
                    val swy = sin(ph + 1.3f) * 2.6f * swayScale * TUNE_SWAY_AMP
                    if (swayScale > 1f) rotationZ += sin(ph + 0.7f) * 2.4f * (swayScale - 1f)
                    translationX = dir.first * 52f * (1f - a) + swx * a + if (effectKind == 4 && fxT.value < 1f) sin(fxT.value * 55f + uSeed * 2.7f) * 5f * (1f - fxT.value) else 0f
                    translationY = dir.second * 52f * (1f - a) + swy * a + if (effectKind == 7) sin(a * 6.2832f + uSeed * 0.8f) * 15f * (1f - a) else 0f
                    if (bounce) translationY -= bounceLift.value * uSize.value * 1.15f
                    if (quake) {
                        val qp = quakePhaseState?.value ?: 0f
                        translationX += sin(qp * 3f + uSeed * 2.2f) * 1.7f
                        translationY += cos(qp * 4f + uSeed * 1.7f) * 1.4f
                    }
                    if (dopagaki) {
                        // ドパガキ改: 入場や揺れは通常PVのまま、鼓動パルスだけ上乗せする
                        val bt = dopaBeat?.value ?: 0f
                        val bump1 = if (bt < 0.18f) sin(bt / 0.18f * 3.14159f) else 0f
                        val bump2 = if (bt >= 0.24f && bt < 0.42f) sin((bt - 0.24f) / 0.18f * 3.14159f) else 0f
                        val pulseS = 1f + 0.10f * bump1 + 0.07f * bump2
                        scaleX *= pulseS
                        scaleY *= pulseS
                    }
                    if (rubyKana) {
                        // ルビは散らさない: 入場の飛来と揺れを消して、定位置にすっと出す
                        translationX = 0f
                        translationY = 0f
                    }
                }
                // 句読点の扱い: ！？は大きく傾け、。、・…は小さくする
                when (unitText) {
                    "！", "？", "!", "?" -> { scaleX *= 1.45f; scaleY *= 1.45f; rotationZ += -12f }
                    "。", "、", "・", "…", "‥" -> { scaleX *= 0.66f; scaleY *= 0.66f }
                }
                // 「深」は上を支点に縦へ下に伸び、「長」は横に伸びる。伸びた分、隣の文字を押す
                if (stretchKind == 1) {
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                    scaleY *= 1f + 0.55f * stretchProgress
                }
                if (stretchKind == 2) {
                    scaleX *= 1f + 0.7f * stretchProgress
                }
                if (pushXFactor != 0f && !rubyKana) {
                    translationX += pushXFactor * stretchProgress * uSize.value * 1.05f
                }
                // ルビのかなは漢字の真上に確実に置く。持ち上げ量は密度込みで計算する
                // （sp の数値をそのまま px として使うと、持ち上げが足りず漢字に被ってしまう）
                if (rubyKana) {
                    val baseSp = if (rubyBaseSizeSp > 0f) rubyBaseSizeSp else uSize.value / 0.52f
                    translationY -= (0.40f * (baseSp + uSize.value) + 2f) * density
                }
                // 疑似視差: カメラの平行移動に対し、文字の大きさ（奥行き）ぶんだけズレを足す。
                // 大きい文字（手前）ほど大きく、小さい文字（奥）ほど小さくズレる。
                // 落下・風・粒子化の退場中は専用の動きが付いているので足さない
                val pState = parallaxState
                if (pState != null && !(exiting && (fallExit || windExit || crumbleExit != 0))) {
                    val po = pState.value
                    translationX += po.x * parallaxDepth * 0.55f
                    translationY += po.y * parallaxDepth * 0.55f
                }
                // 伏せ字のユニットは姿勢を揃える: 回転・移動・視差を無効化して、きれいに横並びにする
                // （落下・風・粒子化の退場中は専用の動きを優先する）
                if (censorUnit && !(exiting && (fallExit || windExit || crumbleExit != 0))) {
                    rotationZ = 0f
                    translationX = 0f
                    translationY = 0f
                }
            }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
        Box(
            // 軽量化: 文字の形の画像化（GraphicsLayer への記録）は、粒子化する退場ユニットだけに絞る。
            // それ以外のユニットは内容を直接描く（見た目は同じで、オフスクリーン記録の分だけ軽くなる）
            modifier = if (exiting && crumbleExit != 0) {
                Modifier.drawWithContent {
                    crumbleLayer.record { this@drawWithContent.drawContent() }
                    if (crumblePoints == null || crumbleFp <= 0f) drawLayer(crumbleLayer)
                }
            } else Modifier
        ) {
            if (!exiting && effectKind == 4 && fxT.value < 1f) {
                // 色ゴーストは入場（uAnim）と同時にだけ出す。入場前から薄く見えてしまわないように、濃さを入場の進み具合に連動させる
                val flick = ((0.35f + 0.3f * sin(fxT.value * 47f + uSeed * 3.1f)) * uAnim.value).coerceIn(0f, 0.65f)
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
            if (pile) {
                // 積もる・重なる系の文字: 同じ文字を上に何枚も重ねて、積み重なりを表現する（上ほど薄く）
                for (layer in 2 downTo 1) {
                    Text(
                        text = annotatedUnit,
                        color = baseColor.copy(alpha = (if (dimmed) 0.40f else 0.92f) * (if (layer == 1) 0.38f else 0.20f)),
                        fontSize = uSize,
                        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Visible,
                        style = LocalTextStyle.current.copy(fontFamily = fontFamily, lineHeight = uSize * 1.02f),
                        modifier = Modifier.offset(x = (if (layer % 2 == 0) 1.5f else -1.5f).dp, y = (-(uSize.value * 0.20f * layer)).dp)
                    )
                }
            }
            if ((partsEntry || wordParts) && !exiting) {
                // パーツ分解: 文字を縦3スライスに分ける。
                // 単語由来（バラバラ等）は合体させず分解したまま揺らし、設定由来は入場時だけ分解して合体させる
                val pa = uAnim.value
                Box {
                    for (slice in 0 until 3) {
                        val sDir = PV_ENTRY_DIRS[Math.floorMod(uSeed * 131 + 7 + slice * 3, 8)]
                        // 単語由来は原型をとどめる程度の小さなズレで揺らし、設定由来（入場）は大きく散らして合体させる
                        val dist = if (wordParts) (9f + slice * 4.5f) * (1f - pa * 0.3f) else (44f + slice * 16f) * (1f - pa)
                        val sliceSwayX = if (wordParts) sin(sway.value * 6.2832f + slice * 2.1f + uSeed) * 1.7f else 0f
                        val sliceSwayY = if (wordParts) cos(sway.value * 6.2832f + slice * 1.7f + uSeed) * 1.3f else 0f
                        Text(
                            text = annotatedUnit,
                            color = baseColor.copy(alpha = if (dimmed) 0.40f else 0.92f),
                            fontSize = uSize,
                            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Visible,
                            style = LocalTextStyle.current.copy(
                                fontFamily = fontFamily,
                                lineHeight = uSize * 1.02f,
                                shadow = Shadow(color = glowColor.copy(alpha = if (dimmed) 0.35f else 0.9f), blurRadius = if (dopagaki) 46f else 22f)
                            ),
                            modifier = Modifier
                                .graphicsLayer {
                                    translationX = sDir.first * dist + sliceSwayX
                                    translationY = sDir.second * dist + sliceSwayY
                                    alpha = pa
                                }
                                .drawWithContent {
                                    val l = size.width * slice / 3f
                                    val r = size.width * (slice + 1) / 3f
                                    clipRect(left = l, top = 0f, right = r, bottom = size.height) {
                                        this@drawWithContent.drawContent()
                                    }
                                }
                        )
                    }
                }
            } else {
            Text(
                text = annotatedUnit,
            color = baseColor.copy(alpha = if (dimmed) 0.40f else 0.92f),
            fontSize = uSize,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Visible,
            style = LocalTextStyle.current.copy(
                fontFamily = fontFamily,
                lineHeight = uSize * 1.02f,
                    shadow = Shadow(color = glowColor.copy(alpha = if (dimmed) 0.35f else 0.9f), blurRadius = if (dopagaki) 46f else 22f)
                )
            )
            }

            if (dripColor != null) {
                // 液体エフェクト: 文字の下端から液が垂れ、周期的に伸びて滴が落ちる（位相は既存の揺れ時計を流用）
                val dc = dripColor
                Canvas(modifier = Modifier.matchParentSize()) {
                    val t = sway.value
                    for (k in 0 until 3) {
                        val fx = (0.22f + 0.28f * k + ((uSeed * (k + 3)) % 10) * 0.012f).coerceIn(0.08f, 0.92f)
                        val x = size.width * fx
                        val cyc = (t + (uSeed % 7) * 0.13f + k * 0.37f) % 1f
                        val grow = if (cyc < 0.7f) cyc / 0.7f else 1f
                        val len = (3f + 13f * grow * grow).dp.toPx()
                        val wdt = 2.6.dp.toPx()
                        val top = size.height * 0.86f
                        drawRoundRect(
                            color = dc.copy(alpha = 0.75f),
                            topLeft = Offset(x - wdt / 2f, top),
                            size = Size(wdt, len),
                            cornerRadius = CornerRadius(wdt / 2f, wdt / 2f)
                        )
                        if (cyc < 0.7f) {
                            drawCircle(color = dc.copy(alpha = 0.85f), radius = 2.3.dp.toPx(), center = Offset(x, top + len + 1.dp.toPx()))
                        } else {
                            val fall = (cyc - 0.7f) / 0.3f
                            drawCircle(
                                color = dc.copy(alpha = 0.85f * (1f - fall)),
                                radius = 2.3.dp.toPx(),
                                center = Offset(x, top + len + 1.dp.toPx() + fall * fall * 26.dp.toPx())
                            )
                        }
                    }
                }
            }
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
            if (mosaic && !exiting) {
                // 「隠」の文字は入場時もモザイクから現れる（入場が進むにつれてセルが解けていく）
                Canvas(modifier = Modifier.matchParentSize()) {
                    val fp = 1f - uAnim.value
                    if (fp > 0f) {
                        val cell = 6.dp.toPx()
                        val cols = (size.width / cell).toInt() + 1
                        val rowsN = (size.height / cell).toInt() + 1
                        for (gx in 0 until cols) for (gy in 0 until rowsN) {
                            val h = (((gx * 73856093) xor (gy * 19349663) xor (uSeed * 83492791)) and 0x7fffffff) % 100
                            if (h < fp * 135f) {
                                val c = when (h % 4) { 0 -> Color(0xFF9E9E9E); 1 -> accentColor; 2 -> Color(0xFF616161); else -> Color(0xFFBDBDBD) }
                                drawRect(color = c.copy(alpha = 0.9f), topLeft = Offset(gx * cell, gy * cell), size = Size(cell, cell))
                            }
                        }
                    }
                }
            }
            if (mosaic && exiting) {
                // 「隠」の文字だけをモザイク化する（退場に合わせてセルが増えていく）
                Canvas(modifier = Modifier.matchParentSize()) {
                    val fp = ((exitProgressValue * 720f - exitDelayMs) / 320f).coerceIn(0f, 1f)
                    if (fp > 0f) {
                        val cell = 6.dp.toPx()
                        val cols = (size.width / cell).toInt() + 1
                        val rowsN = (size.height / cell).toInt() + 1
                        for (gx in 0 until cols) for (gy in 0 until rowsN) {
                            val h = (((gx * 73856093) xor (gy * 19349663) xor (uSeed * 83492791)) and 0x7fffffff) % 100
                            if (h < fp * 135f) {
                                val c = when (h % 4) { 0 -> Color(0xFF9E9E9E); 1 -> accentColor; 2 -> Color(0xFF616161); else -> Color(0xFFBDBDBD) }
                                drawRect(color = c.copy(alpha = 0.9f), topLeft = Offset(gx * cell, gy * cell), size = Size(cell, cell))
                            }
                        }
                    }
                }
            }
            if (censorEnd > censorStart && censorLocal > 0f) {
                // 伏せ字の部分だけを白い帯で左から右へ塗りつぶす（英単語の途中にある ** 等にも対応するため、ユニット内の割合で範囲を指定）
                // 帯の高さは行全体で統一し中央にそろえることで、隣のユニットとつながって一続きのブロックに見える
                Canvas(modifier = Modifier.matchParentSize()) {
                    val x0 = size.width * censorStart
                    val bandH = if (censorBandSp > 0f) (censorBandSp.sp.toPx()).coerceAtMost(size.height) else size.height * 0.86f
                    val bandTop = if (censorBandSp > 0f) (size.height - bandH) / 2f else size.height * 0.07f
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(x0, bandTop),
                        size = Size(size.width * (censorEnd - censorStart) * censorLocal, bandH)
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
            if (!exiting && SPARKLE_WORDS.any { unitText.contains(it) }) {
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
        if (exiting && crumbleExit != 0 && crumblePoints != null) {
            // 文字の形のまま粉々になる粒子（1=その場で崩れて自然落下、2=右へ飛んでいく）
            val pts = crumblePoints!!
            Canvas(modifier = Modifier.matchParentSize()) {
                val fp2 = ((exitProgressValue * 720f - exitDelayMs) / 560f).coerceIn(0f, 1f)
                if (fp2 > 0f) {
                    val n = pts.size / 2
                    for (pi2 in 0 until n) {
                        val h1 = ((pi2 * 2654435761L) xor (staggerIndex * 131L)) and 0x7fffffffL
                        val ox = pts[pi2 * 2] * size.width
                        val oy = pts[pi2 * 2 + 1] * size.height
                        val dx: Float
                        val dy: Float
                        if (crumbleExit == 2) {
                            dx = fp2 * fp2 * (2.0f + (h1 % 50) / 28f) * size.width
                            dy = -sin(fp2 * 3.14159f) * (6f + (h1 % 30) / 3f) + fp2 * fp2 * 22f
                        } else {
                            dx = sin(fp2 * 5f + (h1 % 628) / 100f) * 3.5f * fp2
                            dy = fp2 * fp2 * (0.5f + (h1 % 45) / 100f) * size.height * 2.0f
                        }
                        val a = (1f - fp2) * (0.45f + (h1 % 55) / 100f)
                        val c = when ((h1 % 5).toInt()) { 0 -> accentColor; 1 -> Color(0xFFE8D8A8); else -> Color.White }
                        drawCircle(color = c.copy(alpha = a.coerceIn(0f, 1f)), radius = (1.1f + (h1 % 20) / 10f).dp.toPx() * 0.55f, center = Offset(ox + dx, oy + dy))
                    }
                }
            }
        }
        }
        val enCaption = if (!exiting && unitText.length > 1 && unitText.all { it in 'ァ'..'ヶ' || it == 'ー' || it == '・' }) KATAKANA_EN[unitText] else null
        if (!exiting && effectKind == 0 && !rubyKana) {
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
            // アイコンは大きく・薄く、文字に被せるように重ねる。
            // レイアウト上の大きさは0にして、文字の間隔が広がらないようにする（描画だけ大きく出す）
            Canvas(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .layout { measurable, _ ->
                        val iconPx = (uSize.value * 1.15f).dp.roundToPx()
                        val placeable = measurable.measure(Constraints.fixed(iconPx, iconPx))
                        layout(0, 0) {
                            placeable.place(
                                x = -iconPx / 2 + (uSize.value * 0.20f).dp.roundToPx(),
                                y = (uSize.value * 0.02f).dp.roundToPx()
                            )
                        }
                    }
                    .graphicsLayer {
                        alpha = ulAnim.value.coerceIn(0f, 1f) * 0.15f
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
    // て形＋補助動詞の境界（取り消して／しまった、聞いて／いた 等）を探す。
    // ひらがな部分の中で「て／で」の後ろに補助動詞が続く位置を返す（無ければ -1）
    val auxList = listOf(
        "しまった", "しまう", "しまい", "しちゃ", "ちゃった", "ちゃう",
        "いる", "いた", "いない", "いく", "いった", "くる", "きた",
        "くれる", "くれた", "もらう", "もらった", "あげる", "ある", "あった",
        "おく", "おいた", "みる", "みた", "みたい", "ほしい", "ください"
    )
    fun auxSplitAt(run: String): Int {
        for (i in 1 until run.length - 1) {
            if (run[i] == 'て' || run[i] == 'で') {
                val tail = run.substring(i + 1)
                if (auxList.any { tail.startsWith(it) }) return i + 1
            }
        }
        return -1
    }
    val chunks = mutableListOf<String>()
    val cur = StringBuilder()
    fun closeChunk() {
        if (cur.isNotEmpty()) {
            chunks.add(cur.toString())
            cur.clear()
        }
    }
    for (run in runs) {
        // ーや小さいカナで始まる部分は、前の塊にくっつける（分割したときに ーが行頭に来ないように）
        var rest = run
        if (cur.isNotEmpty()) {
            var cut = 0
            while (cut < rest.length && (rest[cut] == 'ー' || rest[cut] in "ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮヵヶ")) cut++
            if (cut > 0) {
                cur.append(rest.substring(0, cut))
                rest = rest.substring(cut)
                if (rest.isEmpty()) continue
            }
        }
        val t = typeOf(rest[0])
        when {
            t == 2 && cur.isNotEmpty() -> {
                val auxAt = auxSplitAt(rest)
                if (auxAt > 0) {
                    // て形までを今の塊に付けて閉じ、補助動詞からは新しい塊にする
                    cur.append(rest.substring(0, auxAt))
                    closeChunk()
                    cur.append(rest.substring(auxAt))
                } else {
                    // ひらがな部分: 先頭の助詞（最大2文字）の後ろに2文字以上残るなら、助詞までを今の塊に付けて次へ
                    var pLen = 0
                    while (pLen < rest.length && pLen < 2 && rest[pLen] in particles) pLen++
                    if (pLen >= 1 && rest.length - pLen >= 2) {
                        cur.append(rest.substring(0, pLen))
                        closeChunk()
                        cur.append(rest.substring(pLen))
                    } else {
                        cur.append(rest)
                    }
                }
            }
            t == 2 -> {
                // 行頭のひらがな: 補助動詞の境界があれば、そこまでは1つの塊にする
                val auxAt = auxSplitAt(rest)
                if (auxAt > 0) {
                    chunks.add(rest.substring(0, auxAt))
                    cur.append(rest.substring(auxAt))
                } else {
                    cur.append(rest)
                }
            }
            t == 0 -> cur.append(rest) // 句読点などは前の塊に付ける
            else -> {
                if (t == 3 && rest.length >= 12) {
                    // 1行に収まらないほど長いカタカナ語だけ「前の文字＋ー・小さいカナ」を1セットとして塊に分ける
                    // （「ハッピーヘヴンリーバースデー」のような語を行で割れるようにし、ーが行頭に来ないようにするため）
                    closeChunk()
                    val unit = StringBuilder()
                    fun flushUnit() {
                        if (unit.isNotEmpty()) {
                            chunks.add(unit.toString())
                            unit.clear()
                        }
                    }
                    for (ch in rest) {
                        if (ch == 'ー' || ch in "ァィゥェォッャュョヮ") {
                            unit.append(ch)
                        } else {
                            flushUnit()
                            unit.append(ch)
                        }
                    }
                    flushUnit()
                } else if (t == 3 && rest.length >= 6) {
                    // 通常のカタカナ語はまるごと1塊にして、行の途中で単語が割れないようにする
                    closeChunk()
                    chunks.add(rest)
                } else {
                    // 漢字・カタカナは新しい塊の始まり
                    closeChunk()
                    cur.append(rest)
                }
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
    onDismiss: () -> Unit,
    onCleared: () -> Unit = {}
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
    // この曲に付いている歌詞の指定（手動で適用した歌詞／歌詞なし）の状態。解除ボタンの表示に使う
    var hasOverride by remember { mutableStateOf(LyricsManager.hasLyricsOverride(context, currentTitle, currentArtist, currentAlbum)) }
    var instApplied by remember { mutableStateOf(LyricsManager.isInstrumentalOverride(context, currentTitle, currentArtist, currentAlbum)) }
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
                if (hasOverride) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            // 外したら「歌詞なし」にする（自動取得に戻すと、間違った歌詞がまた付いてしまうため）
                            LyricsManager.removeLyricsToNone(context, currentTitle, currentArtist, currentAlbum)
                            hasOverride = true
                            instApplied = true
                            onCleared()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("歌詞を外す（歌詞なしにする）") }
                }
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
                                        if (cand.instrumental) {
                                            // インスト候補を選んだら「この曲は歌詞なし」という指定として適用する
                                            LyricsManager.saveInstrumentalOverride(context, currentTitle, currentArtist, currentAlbum)
                                            onApply(emptyList(), "インスト（歌詞なし）")
                                            onDismiss()
                                        } else {
                                            applyingIndex = idx
                                            scope.launch {
                                                val lrc = LyricsManager.fetchCandidateLrc(cand)
                                                applyingIndex = -1
                                                if (lrc != null) {
                                                    LyricsManager.saveLyricsOverride(
                                                        context, currentTitle, currentArtist, currentAlbum, lrc
                                                    )
                                                    // Kugou候補は文字同期（KRC）も試し、取れたら文字タイミング付きで適用する
                                                    val krc = if (cand.source == "Kugou") LyricsManager.fetchCandidateKrc(cand) else null
                                                    val appliedLines = if (krc != null) {
                                                        LyricsManager.saveKrcCache(context, currentTitle, currentArtist, currentAlbum, krc)
                                                        LyricsManager.parseKrc(krc).ifEmpty { LyricsManager.parseLrc(lrc) }
                                                    } else {
                                                        LyricsManager.parseLrc(lrc)
                                                    }
                                                    onApply(appliedLines, cand.source)
                                                    onDismiss()
                                                } else {
                                                    message = "その候補の歌詞を取得できませんでした"
                                                }
                                            }
                                        }
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (applyingIndex == idx) "取得中…" else cand.title.ifBlank { "(タイトルなし)" },
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    // 同期の種類チップ（文字同期＝カラオケ可能／行同期＝行単位）
                                    if (!cand.instrumental) {
                                        Box(
                                            modifier = Modifier
                                                .padding(start = 8.dp)
                                                .background(
                                                    if (cand.wordSync) Color(0xFF7FD4F2) else Color(0xFF444B54),
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (cand.wordSync) "文字同期" else "行同期",
                                                color = if (cand.wordSync) Color(0xFF0B2536) else Color(0xFFD6DCE3),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                val dur = if (cand.durationSec > 0) {
                                    " ・ ${cand.durationSec / 60}:${(cand.durationSec % 60).toString().padStart(2, '0')}"
                                } else ""
                                Text(
                                    text = "${cand.artist}$dur ・ ${cand.source}" + if (cand.instrumental) " ・ インスト（歌詞なし）" else "",
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
                                LaunchedEffect(cand, currentFp, instApplied) {
                                    isCurrent = false
                                    if (cand.instrumental) {
                                        isCurrent = instApplied
                                    } else if (currentFp != null) {
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
fun PvDopagakiBackground(modifier: Modifier = Modifier, rotate90: Boolean = false) {
    // ドパガキ専用背景: 金の渦の動画をループ再生する。ホーム（縦画面）では90度回して敷き、フルスクリーン（横画面）ではそのまま流す
    val videoAspect = 16f / 9f
    BoxWithConstraints(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        val areaW = if (rotate90) maxHeight.value else maxWidth.value
        val areaH = if (rotate90) maxWidth.value else maxHeight.value
        val viewW: Float
        val viewH: Float
        if (areaW / areaH > videoAspect) {
            viewW = areaW
            viewH = areaW / videoAspect
        } else {
            viewH = areaH
            viewW = areaH * videoAspect
        }
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    var mp: MediaPlayer? = null
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                            val player = MediaPlayer()
                            mp = player
                            try {
                                player.setSurface(Surface(st))
                                player.setDataSource(ctx, Uri.parse("android.resource://" + ctx.packageName + "/" + R.raw.dopagaki_bg))
                                player.isLooping = true
                                player.setVolume(0f, 0f)
                                player.setOnPreparedListener { it.start() }
                                player.prepareAsync()
                            } catch (e: Exception) {
                            }
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            try { mp?.stop() } catch (e: Exception) {
                            }
                            mp?.release()
                            mp = null
                            return true
                        }
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                }
            },
            modifier = Modifier
                .requiredSize(viewW.dp, viewH.dp)
                .then(if (rotate90) Modifier.rotate(90f) else Modifier)
        )
    }
}

@Composable
private fun PvSkipInterludeButton(label: String, dopagaki: Boolean, fontFamily: FontFamily?, accentColor: Color, big: Boolean = false, onClick: () -> Unit) {
    val skipCtx = LocalContext.current
    // 間奏スキップ（大きめ表示用）: 半透明の黒ピルに白太文字の、普通のボタン
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xD90A0A14))
            .border(2.dp, accentColor, RoundedCornerShape(999.dp))
            .clickable {
                Toast.makeText(skipCtx, if (label.contains("次の曲")) "次の曲へ" else if (label.contains("前奏")) "前奏をスキップ" else "間奏をスキップ", Toast.LENGTH_SHORT).show()
                onClick()
            }
            .padding(horizontal = if (big) 38.dp else 24.dp, vertical = if (big) 21.dp else 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = if (big) 27.sp else 19.sp,
            fontWeight = FontWeight.Bold,
            style = LocalTextStyle.current.copy(fontFamily = fontFamily)
        )
    }
}

// 通常モード用: 小さな文字だけの控えめなスキップ表示
@Composable
private fun PvSkipInterludeText(label: String, fontFamily: FontFamily?, big: Boolean = false, accentColor: Color = Color.Transparent, onClick: () -> Unit) {
    val skipCtx = LocalContext.current
    Text(
        text = label,
        color = Color.White.copy(alpha = if (big) 0.97f else 0.85f),
        fontSize = if (big) 30.sp else 13.sp,
        fontWeight = if (big) FontWeight.Bold else FontWeight.Normal,
        style = if (big) LocalTextStyle.current.copy(fontFamily = fontFamily, shadow = Shadow(color = accentColor.copy(alpha = 0.9f), blurRadius = 26f)) else LocalTextStyle.current.copy(fontFamily = fontFamily),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                Toast.makeText(skipCtx, if (label.contains("次の曲")) "次の曲へ" else if (label.contains("前奏")) "前奏をスキップ" else "間奏をスキップ", Toast.LENGTH_SHORT).show()
                onClick()
            }
            .padding(horizontal = if (big) 28.dp else 12.dp, vertical = if (big) 18.dp else 10.dp)
    )
}

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
    translationTopDp: Dp,
    partsEntry: Boolean = false,
    enFontFamily: FontFamily? = null,
    onAccentFlash: (Color?) -> Unit = {},
    pvTilt3d: Boolean = false,
    dopagaki: Boolean = false,
    tiltX: State<Float>? = null,
    tiltY: State<Float>? = null,
    onSeek: (Long) -> Unit = {},
    onNextTrack: () -> Unit = {},
    onLineChange: (Int) -> Unit = {},
    // 追加設定: PVの文字サイズ倍率 / 素の文字色 / ルビ / 間奏アート
    pvFontScale: Float = 1f,
    pvBaseColor: Color = Color.White,
    pvRuby: Boolean = true,
    pvInterludeArt: Boolean = true
) {
    val pvPos = positionMs + lyricOffsetMs
    val pvIndex = lines.indexOfLast { it.timeMs <= pvPos }
    // PVは間奏では何も表示しない（音符マークも出さない）
    val pvCurLine = lines.getOrNull(pvIndex)
    val pvNextLine = lines.getOrNull(pvIndex + 1)
    val pvInterludeThreshold = remember(lines) { lyricInterludeThresholdMs(lines) }
    val pvInterlude = when {
        pvCurLine == null -> pvIndex < 0 && (lines.firstOrNull()?.let { (it.timeMs - pvPos) > pvInterludeThreshold } ?: false) // 前奏も間奏アートを出す（相当長い時だけ）
        pvNextLine == null -> (pvPos - pvCurLine.timeMs) > 15000
        else -> (pvNextLine.timeMs - pvCurLine.timeMs) > pvInterludeThreshold && (pvPos - pvCurLine.timeMs) > 10000 // 行間が平均+10秒を超える間奏だけ。次の行が近づいたら前の行に戻る、をしない
    }
    val pvDisplayIndex = if (pvInterlude) -2 else pvIndex
    // 色名・連想色に当たった行では、その色（文字と同じ淡色）を親に知らせてアクセントを一瞬変える
    val flashHit = remember(pvCurLine?.text, accentColor) {
        val t = pvCurLine?.text
        if (t == null) null else {
            val hit = COLOR_WORDS.firstOrNull { (w, _) ->
                if (w.all { it.code < 128 }) Regex("""(?i)(?<![A-Za-z])${Regex.escape(w)}(?![A-Za-z])""").containsMatchIn(t) else t.contains(w)
            } ?: ASSOC_COLOR_WORDS.firstOrNull { (w, _) ->
                if (w.all { it.code < 128 }) Regex("""(?i)(?<![A-Za-z])${Regex.escape(w)}(?![A-Za-z])""").containsMatchIn(t) else t.contains(w)
            }
            hit?.let { (_, base) ->
                Color(
                    red = (base.red * 0.55f + accentColor.red * 0.45f) * 0.55f + 0.45f,
                    green = (base.green * 0.55f + accentColor.green * 0.45f) * 0.55f + 0.45f,
                    blue = (base.blue * 0.55f + accentColor.blue * 0.45f) * 0.55f + 0.45f
                )
            }
        }
    }
    LaunchedEffect(flashHit, pvIndex) {
        if (flashHit != null) onAccentFlash(flashHit)
    }
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
        // カメラの流れも行ごとにランダム（前の行と同じ流れは避ける）。
        // 退場する行は「次の行の流れ」を共有するので、退場と入場がつながって見える
        val pvCamMap = remember { mutableStateMapOf<Int, Int>() }
        fun pvCamFlowFor(idx: Int, text: String): Int {
            pvCamMap[idx]?.let { return it }
            val raw = Math.floorMod(Math.floorMod(idx * 41 + text.hashCode(), 997), 7)
            val prev = pvCamMap[idx - 1]
            val flow = if (prev != null && raw == prev) (raw + 1) % 7 else raw
            pvCamMap[idx] = flow
            return flow
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
        val darkTarget = if (pvDisplayIndex >= 0 && lines.getOrNull(pvDisplayIndex)?.text?.let { t -> DARK_WORDS.any { t.contains(it) } } == true) 0.32f else 0f
        val darkAlpha = remember { Animatable(0f) }
        LaunchedEffect(darkTarget) {
            darkAlpha.animateTo(darkTarget, animationSpec = tween(durationMillis = 700))
        }
        // サビ判定: 同じ歌詞行が2回以上出てくる行はサビとみなし、背景をほんのり明るくする
        val chorusTexts = remember(lines) {
            lines.map { it.text.trim() }.filter { it.isNotEmpty() }
                .groupingBy { it }.eachCount().filterValues { it >= 2 }.keys
        }
        val chorusTarget = if (pvDisplayIndex >= 0 && chorusTexts.contains(lines.getOrNull(pvDisplayIndex)?.text?.trim())) 1f else 0f
        val chorusGlow = remember { Animatable(0f) }
        LaunchedEffect(chorusTarget) {
            chorusGlow.animateTo(chorusTarget, animationSpec = tween(durationMillis = 900))
        }
        // ドパガキ改: 通常PVの上に、行が変わるたびのフラッシュと星の瞬きを重ねる
        val dopaFlash = remember { Animatable(0f) }
        LaunchedEffect(pvDisplayIndex) {
            if (dopagaki && pvDisplayIndex >= 0) {
                dopaFlash.snapTo(0.26f)
                dopaFlash.animateTo(0f, animationSpec = tween(durationMillis = 320))
            }
        }
        // ドパガキ: 行が変わるたび、キラキラが画面を左から右へ流れる
        val dopaFlow = remember { Animatable(0f) }
        LaunchedEffect(pvDisplayIndex) {
            onLineChange(pvDisplayIndex)
            if (dopagaki) {
                dopaFlow.snapTo(0f)
                dopaFlow.animateTo(1f, animationSpec = tween(durationMillis = 800, easing = LinearEasing))
            }
        }
        val dopaTwinkleState = if (dopagaki) {
            val tr = rememberInfiniteTransition(label = "dopaTwinkle")
            tr.animateFloat(
                initialValue = 0f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
                label = "dopaTwinklePh"
            )
        } else null
        Box(modifier = Modifier.fillMaxSize().graphicsLayer {
                        val tx = tiltX?.value ?: 0f
                        val ty = tiltY?.value ?: 0f
                        rotationX = tx * 0.38f
                        rotationY = ty * 0.38f
                        cameraDistance = 9f * density
                        translationX = ty * 1.15f
                        translationY = -tx * 1.15f
                    }, contentAlignment = Alignment.TopCenter) {
            if (darkAlpha.value > 0.001f) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = darkAlpha.value)))
            }
            if (dopagaki) {
                // 画面全体に星の瞬きを散らす（位相は描画時にだけ読む）
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val tw = dopaTwinkleState?.value ?: 0f
                    for (i in 0 until 22) {
                        val sx = ((i * 389 + 71) % 100) / 100f * size.width
                        val sy = ((i * 641 + 37) % 100) / 100f * size.height
                        val al = 0.30f + 0.70f * (0.5f + 0.5f * sin(tw * 6.2832f * (1f + (i % 3) * 0.37f) + i * 2.39f))
                        drawSparkle(sx, sy, 6f + (i % 3) * 3f, Color(0xFFFFF2B0), al * 0.85f)
                    }
                }
                // 行が変わる瞬間のフラッシュ
                if (dopaFlash.value > 0.01f) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = dopaFlash.value)))
                }
            }
            if (chorusGlow.value > 0.001f) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = (pvTopDp - 70.dp).coerceAtLeast(0.dp)).height(320.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(accentColor.copy(alpha = 0.20f * chorusGlow.value), Color.Transparent)
                            )
                        )
                )
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
                        startsInParen = pvStartsInParen(lines, exIdx),
                        partsEntry = partsEntry,
                        enFontFamily = enFontFamily,
                        camFlow = pvCurrent?.let { pvCamFlowFor(it.first, it.second) } ?: pvCamFlowFor(exIdx + 1, lines.getOrNull(exIdx + 1)?.text ?: ""),
                        tilt3d = pvTilt3d, dopagaki = dopagaki, fontScale = pvFontScale, baseColor = pvBaseColor, rubyEnabled = pvRuby,
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
                                words = lines[curIdx].words,
                                lineStartMs = lines[curIdx].timeMs,
                                partsEntry = partsEntry,
                        enFontFamily = enFontFamily,
                                camFlow = pvCamFlowFor(curIdx, curText),
                                tilt3d = pvTilt3d, dopagaki = dopagaki, fontScale = pvFontScale, baseColor = pvBaseColor, rubyEnabled = pvRuby,
                                effectKind = pvKindFor(curIdx, curText),
                                lineDurationMs = lines.getOrNull(curIdx + 1)?.let { it.timeMs - lines[curIdx].timeMs } ?: Long.MAX_VALUE,
                                startsInParen = pvStartsInParen(lines, curIdx),
                                modifier = Modifier
                            )
                        }
                    }
                }
            }
        }
        if (pvDisplayIndex == -2) {
            if (pvInterludeArt) PvInterludeArt(accentColor = accentColor, modifier = Modifier.fillMaxWidth().padding(top = interludeTopDp).height(230.dp))
            // ドパガキモードの間奏中だけ、ギラギラボタンを出す（通常は次の行へスキップ、ラストの間奏は次の曲へ）
            val pvSkipTarget = pvNextLine?.let { (it.timeMs - lyricOffsetMs - 300L).coerceAtLeast(0L) }
            val pvSkipLabel = if (pvCurLine == null) "前奏をスキップ▷" else "間奏をスキップ▷"
            if (dopagaki) {
                // 行の入退場に合わせてキラキラが流れる（位相は描画時にだけ読む）
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val fv = dopaFlow.value
                    if (fv > 0f && fv < 1f) {
                        val fade = sin(fv * 3.14159f)
                        for (i in 0 until 26) {
                            val fx = ((i * 0.61803f) + fv * (0.5f + (i % 4) * 0.18f)) % 1f
                            val fy = ((i * 389 + 53) % 100) / 100f
                            val a = fade * (0.35f + 0.65f * ((i * 29) % 10) / 10f)
                            drawSparkle(size.width * fx, size.height * fy, 5f + (i % 3) * 3.5f, Color(0xFFFFF2B0), a * 0.9f)
                        }
                    }
                }
            }
            // 通常モードのスキップ表示は Main 側の最前面オーバーレイに移設した（円盤レイヤーにタップを奪われないため）
            if (pvSkipTarget != null) {
                if (dopagaki) {
                    Box(Modifier.fillMaxSize().zIndex(999f), contentAlignment = Alignment.Center) {
                        PvSkipInterludeText(pvSkipLabel, fontFamily, big = true, accentColor = accentColor) { onSeek(pvSkipTarget) }
                    }
                }
            } else if (pvCurLine != null) {
                if (dopagaki) {
                    Box(Modifier.fillMaxSize().zIndex(999f), contentAlignment = Alignment.Center) {
                        PvSkipInterludeText("次の曲へ▷", fontFamily, big = true, accentColor = accentColor) { onNextTrack() }
                    }
                }
            }
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
            val ambKind = AMBIENT_WORDS.firstOrNull { (w, _) -> curText.contains(w) }?.second ?: -1
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
    translations: List<String>? = null,
    onToggleTranslation: () -> Unit,
    reverseScroll: Boolean,
    onSeek: (Long) -> Unit,
    onRefresh: () -> Unit,
    onOpenSearch: () -> Unit,
    onSaveEditedLyrics: ((String) -> Unit)? = null,
    onPublishLyrics: ((String, (String) -> Unit) -> Unit)? = null,
    onClose: () -> Unit,
    enFontFamily: FontFamily? = null,
    dopagaki: Boolean = false,
    onToggleDopagaki: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val toastCtx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var follow by remember { mutableStateOf(true) }
    var showOffsetPanel by remember { mutableStateOf(false) }
    var showLyricEditor by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf(false) }
    var publishPhase by remember { mutableStateOf(0) }
    var publishMessage by remember { mutableStateOf("") }
    if (showLyricEditor) {
        AlertDialog(
            onDismissRequest = { showLyricEditor = false },
            containerColor = Color(0xFF141C2E),
            title = { Text("歌詞を編集", color = Color.White, fontSize = 18.sp) },
            text = {
                Column {
                    Text(
                        "1行ごとに [分:秒.ミリ秒]歌詞 の形です。誤字の修正のほか、時刻も直せます。保存するとキャッシュに残るので、次回からも直した歌詞が出ます（文字同期は外れます）。",
                        color = Color.Gray,
                        fontSize = 11.5.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it; editError = false },
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontFamily = fontFamily),
                        modifier = Modifier.fillMaxWidth().height(300.dp)
                    )
                    if (editError) {
                        Spacer(Modifier.height(6.dp))
                        Text("時刻タグ [00:00.00] のある行が1つもありません", color = Color(0xFFFF9E9E), fontSize = 12.sp)
                    }
                    if (onPublishLyrics != null) {
                        Spacer(Modifier.height(12.dp))
                        when (publishPhase) {
                            0 -> TextButton(onClick = { publishPhase = 1 }) {
                                Text("この歌詞を LRCLIB に投稿する", color = Color(0xFF88C0FF), fontSize = 13.sp)
                            }
                            1 -> Column {
                                Text(
                                    "LRCLIB は公開データベースです。投稿すると誰でも見られるようになり、取り消しはできません。直した歌詞をみんなで共有できます。",
                                    color = Color(0xFFFFD28F),
                                    fontSize = 11.5.sp
                                )
                                Row {
                                    TextButton(onClick = {
                                        publishPhase = 2
                                        onPublishLyrics.invoke(editText) { msg ->
                                            publishMessage = msg
                                            publishPhase = if (msg == "OK") 3 else 4
                                        }
                                    }) { Text("投稿する", color = Color(0xFF88C0FF)) }
                                    TextButton(onClick = { publishPhase = 0 }) { Text("やめる", color = Color.Gray) }
                                }
                            }
                            2 -> Text("投稿中…（計算パズルを解くため、数十秒かかることがあります）", color = Color.Gray, fontSize = 12.sp)
                            3 -> Text("LRCLIB に投稿しました", color = Color(0xFF7FD4F2), fontSize = 12.5.sp)
                            else -> Text(publishMessage, color = Color(0xFFFF9E9E), fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val ok = editText.lines().any { Regex("^\\[\\d{1,2}:\\d{2}").containsMatchIn(it) }
                    if (!ok) {
                        editError = true
                    } else {
                        onSaveEditedLyrics?.invoke(editText)
                        showLyricEditor = false
                    }
                }) { Text("保存", color = Color(0xFF88C0FF)) }
            },
            dismissButton = {
                TextButton(onClick = { showLyricEditor = false }) { Text("キャンセル", color = Color.Gray) }
            }
        )
    }
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
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp).zIndex(91f)
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
            // 選択中の行の前後は間隔を広げて、選択位置を分かりやすくする（スクロールに滑らかに追従）
            val focusSpread = 13f * (idx - virtualPos).coerceIn(-1f, 1f)
            return acc - fracPart * stepDp(baseIdx) + focusSpread
        }
        val browseWidthDp = maxWidth.value
        val firstIdx = maxOf(0, floor(virtualPos - 7f).toInt())
        val lastIdx = minOf(lines.size - 1, ceil(virtualPos + 7f).toInt())
        for (idx in firstIdx..lastIdx) {
            val relPos = idx - virtualPos
            val isFocusedLine = idx == focusedIdx
            val fade = (1f - (abs(relPos) - 4f) / 3f).coerceIn(0f, 1f)
            val myHeightDp = (lineHeightsPx[idx]?.let { it / densityNow.density } ?: 34f)
            if (isFocusedLine) {
                // 「移動」ボタンは選択中の行の横に丸いアイコンボタンで置く（幅を取らず、歌詞にも被せない）
                Box(
                    modifier = Modifier
                        .offset(
                            x = (browseWidthDp - 58f).dp,
                            y = (centerYDp + offsetDpFor(idx) - 21f).dp
                        )
                        .zIndex(95f)
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                        .clickable {
                            onSeek((lines[idx].timeMs - lyricOffsetMs).coerceAtLeast(0L))
                            onClose()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    LucideIcon(LucidePlay, size = 24.dp, color = Color.White)
                }
            }
            Column(
                modifier = Modifier
                    .offset(
                        x = (40f + relPos * 5f).dp,
                        y = (centerYDp + offsetDpFor(idx) - myHeightDp / 2f).dp
                    )
                    .fillMaxWidth(if (isFocusedLine) ((browseWidthDp - 118f) / browseWidthDp).coerceIn(0.3f, 0.85f) else 0.85f)
                    .onGloballyPositioned { lineHeightsPx[idx] = it.size.height }
            ) {
                if (idx == browsePlayingIdx && lines[idx].words.isNotEmpty()) {
                    // 再生中の行は、フォーカスが外れていてもカラオケ色付けで表示する
                    KaraokeLyricText(
                        line = lines[idx],
                        positionMs = positionMs + lyricOffsetMs,
                        sungColor = if (isFocusedLine) accentColor else accentColor.copy(alpha = 0.92f * fade),
                        baseColor = if (isFocusedLine) Color.White else Color.White.copy(alpha = 0.92f * fade),
                        fontSize = fitFontSizeSp(lines[idx].text, if (isFocusedLine) 26f else 15f, if (isFocusedLine) browseWidthDp - 118f else browseWidthDp * 0.85f).sp,
                        fontWeight = if (isFocusedLine) lyricWeight else FontWeight.Normal,
                        fontFamily = fontFamily,
                        enFontFamily = enFontFamily,
                        letterSpacing = 1.0.sp,
                        textAlign = TextAlign.Start,
                        shadow = if (accentGlow && isFocusedLine) Shadow(color = accentColor, blurRadius = 14f) else Shadow(color = Color.Black.copy(alpha = 0.75f), blurRadius = 10f)
                    )
                } else {
                    Text(
                        text = lyricMixedFontText(lines[idx].text, enFontFamily),
                        color = if (isFocusedLine) Color.White else Color.White.copy(alpha = 0.92f * fade),
                        fontSize = fitFontSizeSp(lines[idx].text, if (isFocusedLine) 26f else 15f, if (isFocusedLine) browseWidthDp - 118f else browseWidthDp * 0.85f).sp,
                        fontWeight = if (isFocusedLine) lyricWeight else FontWeight.Normal,
                        letterSpacing = 1.0.sp,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        style = LocalTextStyle.current.copy(
                            fontFamily = fontFamily,
                            shadow = if (accentGlow && isFocusedLine) Shadow(color = accentColor, blurRadius = 14f) else Shadow(color = Color.Black.copy(alpha = 0.75f), blurRadius = 10f)
                        )
                    )
                }
                if (idx == browsePlayingIdx) {
                    // 再生中の行には進行バーを付ける（文字同期の行も、カラオケ色付けと併せて出す）
                    val lineStartP = lines[idx].timeMs
                    val lineEndP = lines.getOrNull(idx + 1)?.timeMs ?: (lineStartP + 10000L)
                    val progP = ((positionMs + lyricOffsetMs - lineStartP).toFloat() / (lineEndP - lineStartP).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 3.dp).height(3.dp)
                            .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(2.dp))
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(progP).height(3.dp)
                                .background(accentColor, RoundedCornerShape(2.dp))
                        )
                    }
                }
                // 翻訳ONのときは、各行の下に小さく対訳を添える
                if (translationEnabled) {
                    val tr = translations?.getOrNull(idx)
                    if (!tr.isNullOrBlank()) {
                        Text(
                            text = tr,
                            color = Color.White.copy(alpha = if (isFocusedLine) 0.75f else 0.30f + 0.35f * fade),
                            fontSize = if (isFocusedLine) 12.sp else 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = LocalTextStyle.current.copy(fontFamily = fontFamily),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
    // 下部ボタン: 移動（フォーカス行へジャンプ）／タイミング調整／閉じる
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = topPaddingDp + 48.dp, end = 22.dp)
            .size(44.dp)
            .zIndex(95f)
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        LucideIcon(LucideX, size = 26.dp, color = Color.White.copy(alpha = 0.8f))
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 88.dp)
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
            // リセットを中央に置き、−は左へ、＋は右へ広がる配置
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)
                ) {
                    listOf(-1000L to "-1.0", -500L to "-0.5", -100L to "-0.1").forEach { (delta, label) ->
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
                }
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF263238))
                        .clickable { onOffsetChange(0L) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text("リセット", color = Color.White, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.Start)
                ) {
                    listOf(100L to "+0.1", 500L to "+0.5", 1000L to "+1.0").forEach { (delta, label) ->
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
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                LucideIcon(LucideRefreshCcw, size = 24.dp, color = Color.White)
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
                LucideIcon(LucideSearch, size = 24.dp, color = Color.White)
            }
            // 歌詞を手で編集（誤字修正など。キャッシュに保存される）
            if (onSaveEditedLyrics != null && lines.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF263238))
                        .clickable {
                            editText = buildLrcText(lines)
                            editError = false
                            publishPhase = 0
                            publishMessage = ""
                            showLyricEditor = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    LucideIcon(LucidePencil, size = 24.dp, color = Color.White)
                }
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
                LucideIcon(LucideTimer, size = 24.dp, color = Color.White)
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
                LucideIcon(LucideLanguages, size = 24.dp, color = Color.White)
            }
            // ドパガキモードON/OFF
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (dopagaki) Color(0xFFFFB300) else Color(0xFF263238))
                    .clickable {
                        if (!dopagaki) Toast.makeText(toastCtx, "ドパガキモード始動", Toast.LENGTH_SHORT).show()
                        onToggleDopagaki()
                    },
                contentAlignment = Alignment.Center
            ) {
                LucideIcon(LucideSparkles, size = 24.dp, color = Color.White)
            }
        }
    }
    }
}

// 編集用に、行リストを LRC 形式のテキストへ戻す（LyricsManager.linesToLrc と同じ書式）
private fun buildLrcText(lines: List<LyricLine>): String {
    val sb = StringBuilder()
    for (line in lines) {
        val totalSec = line.timeMs / 1000
        val mm = (totalSec / 60).toString().padStart(2, '0')
        val ss = (totalSec % 60).toString().padStart(2, '0')
        val cs = ((line.timeMs % 1000) / 10).toString().padStart(2, '0')
        sb.append("[$mm:$ss.$cs]").append(line.text).append("\n")
    }
    return sb.toString()
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
    onClose: () -> Unit,
    partsEntry: Boolean = false,
    enFontFamily: FontFamily? = null,
    pvTilt3d: Boolean = false,
    dopagaki: Boolean = false,
    tiltX: State<Float>? = null,
    tiltY: State<Float>? = null,
    onSeek: (Long) -> Unit = {},
    onNextTrack: () -> Unit = {},
    // 追加設定: PVと共通（文字サイズ・色・ルビ・間奏アート・液体）
    pvFontScale: Float = 1f,
    pvBaseColor: Color = Color.White,
    pvRuby: Boolean = true,
    pvInterludeArt: Boolean = true,
    liquidSpeed: Float = 1f,
    liquidAlphaMul: Float = 1f
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
            // ドパガキ中は、行が変わるごとに液体の色も変わる
            val fsLiquidBase = if (dopagaki) {
                val fsIdxForHue = fsLines.indexOfLast { it.timeMs <= positionMs + lyricOffsetMs }
                Color.hsv(Math.floorMod(fsIdxForHue * 47, 360).toFloat(), 0.72f, 1f)
            } else accentColor
            LiquidGradientBackground(baseColor = fsLiquidBase, onBlack = false, layerAlpha = 1f, alphaBoost = 1.9f, speedFactor = liquidSpeed, alphaMul = liquidAlphaMul)
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
        val fsInterludeThreshold = remember(fsLines) { lyricInterludeThresholdMs(fsLines) }
        val fsInterlude = when {
            fsCurLine == null -> fsIndex < 0 && (fsLines.firstOrNull()?.let { (it.timeMs - fsPos) > fsInterludeThreshold } ?: false) // 前奏も間奏アートを出す（相当長い時だけ）
            fsNextLine == null -> (fsPos - fsCurLine.timeMs) > 15000
            else -> (fsNextLine.timeMs - fsCurLine.timeMs) > fsInterludeThreshold && (fsPos - fsCurLine.timeMs) > 10000
        }
        if (displayMode == "video") {
            // PVだけを中央に
            val fsDisplayIndex = if (fsInterlude) -2 else fsIndex
            // ドパガキ改: 行が変わるたびのフラッシュと星の瞬き（通常PVの上に重ねる）
            val fsDopaFlash = remember { Animatable(0f) }
            LaunchedEffect(fsDisplayIndex) {
                if (dopagaki && fsDisplayIndex >= 0) {
                    fsDopaFlash.snapTo(0.26f)
                    fsDopaFlash.animateTo(0f, animationSpec = tween(durationMillis = 320))
                }
            }
            val fsDopaTwinkleState = if (dopagaki) {
                val tr = rememberInfiniteTransition(label = "fsDopaTwinkle")
                tr.animateFloat(
                    initialValue = 0f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
                    label = "fsDopaTwinklePh"
                )
            } else null
            val fsDopaFlow = remember { Animatable(0f) }
            LaunchedEffect(fsDisplayIndex) {
                if (dopagaki) {
                    fsDopaFlow.snapTo(0f)
                    fsDopaFlow.animateTo(1f, animationSpec = tween(durationMillis = 800, easing = LinearEasing))
                }
            }
            if (dopagaki) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val tw = fsDopaTwinkleState?.value ?: 0f
                    for (i in 0 until 22) {
                        val sx = ((i * 389 + 71) % 100) / 100f * size.width
                        val sy = ((i * 641 + 37) % 100) / 100f * size.height
                        val al = 0.30f + 0.70f * (0.5f + 0.5f * sin(tw * 6.2832f * (1f + (i % 3) * 0.37f) + i * 2.39f))
                        drawSparkle(sx, sy, 6f + (i % 3) * 3f, Color(0xFFFFF2B0), al * 0.85f)
                    }
                }
                if (fsDopaFlash.value > 0.01f) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = fsDopaFlash.value)))
                }
                // 行の入退場に合わせてキラキラが流れる（位相は描画時にだけ読む）
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val fv = fsDopaFlow.value
                    if (fv > 0f && fv < 1f) {
                        val fade = sin(fv * 3.14159f)
                        for (i in 0 until 26) {
                            val fx = ((i * 0.61803f) + fv * (0.5f + (i % 4) * 0.18f)) % 1f
                            val fy = ((i * 389 + 53) % 100) / 100f
                            val a = fade * (0.35f + 0.65f * ((i * 29) % 10) / 10f)
                            drawSparkle(size.width * fx, size.height * fy, 5f + (i % 3) * 3.5f, Color(0xFFFFF2B0), a * 0.9f)
                        }
                    }
                }
            }
            if (fsIndex >= 0) {
                var fsCurrent by remember { mutableStateOf<Pair<Int, String>?>(null) }
                var fsExiting by remember { mutableStateOf<Pair<Int, String>?>(null) }
                LaunchedEffect(lines) { fsCurrent = null; fsExiting = null }
                // カメラの流れ（退場と入場で共有してつなげる）
                val fsCamMap = remember { mutableStateMapOf<Int, Int>() }
                fun fsCamFlowFor(idx: Int, text: String): Int {
                    fsCamMap[idx]?.let { return it }
                    val raw = Math.floorMod(Math.floorMod(idx * 41 + text.hashCode(), 997), 7)
                    val prev = fsCamMap[idx - 1]
                    val flow = if (prev != null && raw == prev) (raw + 1) % 7 else raw
                    fsCamMap[idx] = flow
                    return flow
                }
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
                val fsDarkTarget = if (fsDisplayIndex >= 0 && fsLines.getOrNull(fsDisplayIndex)?.text?.let { t -> DARK_WORDS.any { t.contains(it) } } == true) 0.32f else 0f
                val fsDarkAlpha = remember { Animatable(0f) }
                LaunchedEffect(fsDarkTarget) {
                    fsDarkAlpha.animateTo(fsDarkTarget, animationSpec = tween(durationMillis = 700))
                }
                Box(modifier = Modifier.fillMaxSize().graphicsLayer {
                        val tx = tiltX?.value ?: 0f
                        val ty = tiltY?.value ?: 0f
                        rotationX = tx * 0.38f
                        rotationY = ty * 0.38f
                        cameraDistance = 9f * density
                        translationX = ty * 1.15f
                        translationY = -tx * 1.15f
                    }, contentAlignment = Alignment.Center) {
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
                                startsInParen = pvStartsInParen(fsLines, exIdx),
                                partsEntry = partsEntry,
                                enFontFamily = enFontFamily,
                                camFlow = fsCurrent?.let { fsCamFlowFor(it.first, it.second) } ?: fsCamFlowFor(exIdx + 1, fsLines.getOrNull(exIdx + 1)?.text ?: ""),
                                tilt3d = pvTilt3d, dopagaki = dopagaki, fontScale = pvFontScale, baseColor = pvBaseColor, rubyEnabled = pvRuby,
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
                                startsInParen = pvStartsInParen(fsLines, curIdx),
                                partsEntry = partsEntry,
                                enFontFamily = enFontFamily,
                                camFlow = fsCamFlowFor(curIdx, curText),
                                tilt3d = pvTilt3d, dopagaki = dopagaki, fontScale = pvFontScale, baseColor = pvBaseColor, rubyEnabled = pvRuby,
                                exiting = false,
                                words = fsLines[curIdx].words,
                                lineStartMs = fsLines[curIdx].timeMs,
                                effectKind = Math.floorMod(Math.floorMod(curIdx * 31 + curText.hashCode(), 997), 8),
                                modifier = Modifier
                            )
                        }
                    }
                }
                if (fsDisplayIndex == -2) {
                    if (pvInterludeArt) PvInterludeArt(accentColor = accentColor, modifier = Modifier.fillMaxSize().padding(60.dp))
                    val fsSkipTarget = fsNextLine?.let { (it.timeMs - lyricOffsetMs - 300L).coerceAtLeast(0L) }
                    val fsSkipLabel = if (fsCurLine == null) "前奏をスキップ▷" else "間奏をスキップ▷"
                    if (fsSkipTarget != null) {
                        if (dopagaki) {
                            Box(Modifier.fillMaxSize().zIndex(999f), contentAlignment = Alignment.Center) {
                                PvSkipInterludeText(fsSkipLabel, fontFamily, big = true, accentColor = accentColor) { onSeek(fsSkipTarget) }
                            }
                        } else {
                            Box(Modifier.fillMaxSize().padding(end = 20.dp, bottom = 24.dp).zIndex(999f), contentAlignment = Alignment.BottomEnd) {
                                PvSkipInterludeText(fsSkipLabel, fontFamily) { onSeek(fsSkipTarget) }
                            }
                        }
                    } else if (fsCurLine != null) {
                        if (dopagaki) {
                            Box(Modifier.fillMaxSize().zIndex(999f), contentAlignment = Alignment.Center) {
                                PvSkipInterludeText("次の曲へ▷", fontFamily, big = true, accentColor = accentColor) { onNextTrack() }
                            }
                        } else {
                            Box(Modifier.fillMaxSize().padding(end = 20.dp, bottom = 24.dp).zIndex(999f), contentAlignment = Alignment.BottomEnd) {
                                PvSkipInterludeText("次の曲へ▷", fontFamily) { onNextTrack() }
                            }
                        }
                    }
                }
                fsCurrent?.let { (_, curText) ->
                    val ambKind = when {
                        AMBIENT_WORDS.any { (w, _) -> curText.contains(w) } -> AMBIENT_WORDS.first { (w, _) -> curText.contains(w) }.second
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
                            Text(lyricMixedFontText(prevText, enFontFamily), color = Color.White.copy(alpha = 0.45f), fontSize = fitFontSizeSp(prevText, 18f, fsWidth - 80f).sp, letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = LocalTextStyle.current.copy(fontFamily = fontFamily))
                            Spacer(Modifier.height(14.dp))
                        }
                        if (curText.isNotEmpty()) {
                            val fsCurK = fsCurLine
                            if (!fsInterlude && fsCurK != null && fsCurK.words.isNotEmpty()) {
                                KaraokeLyricText(
                                    line = fsCurK,
                                    positionMs = fsPos,
                                    sungColor = accentColor,
                                    baseColor = Color.White,
                                    fontSize = fitFontSizeSp(curText, 30f, fsWidth - 108f).sp,
                                    fontWeight = lyricWeight,
                                    fontFamily = fontFamily,
                                    enFontFamily = enFontFamily,
                                    letterSpacing = 3.sp,
                                    shadow = Shadow(color = Color.White.copy(alpha = 0.85f), blurRadius = 18f),
                                    modifier = Modifier.padding(start = 28.dp)
                                )
                            } else {
                                Text(
                                    lyricMixedFontText(curText, enFontFamily),
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
                        }
                        if (nextText.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            Text(lyricMixedFontText(nextText, enFontFamily), color = Color.White.copy(alpha = 0.45f), fontSize = fitFontSizeSp(nextText, 18f, fsWidth - 136f).sp, letterSpacing = 2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = LocalTextStyle.current.copy(fontFamily = fontFamily), modifier = Modifier.padding(start = 56.dp))
                        }
                    } else {
                        val lineText = if (fsIndex < 0) "" else if (fsInterlude) "♪" else (fsCurLine?.text ?: "")
                        if (lineText.isNotEmpty()) {
                            val fsCurK2 = fsCurLine
                            if (!fsInterlude && fsCurK2 != null && fsCurK2.words.isNotEmpty()) {
                                KaraokeLyricText(
                                    line = fsCurK2,
                                    positionMs = fsPos,
                                    sungColor = accentColor,
                                    baseColor = Color.White,
                                    fontSize = fitFontSizeSp(lineText, 26f, fsWidth - 80f).sp,
                                    fontWeight = lyricWeight,
                                    fontFamily = fontFamily,
                                    enFontFamily = enFontFamily,
                                    letterSpacing = 2.sp,
                                    shadow = Shadow(color = Color.White.copy(alpha = 0.85f), blurRadius = 18f)
                                )
                            } else {
                                Text(
                                    lyricMixedFontText(lineText, enFontFamily),
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
        }
        // 終了ボタン: もう一度押すと縦画面に戻る。
        // 横画面では右端にナビゲーションバーが出るため、その幅の分だけ左へ逃がす
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = 18.dp, end = 22.dp)
                .size(44.dp)
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            LucideIcon(
                icon = LucideMinimize,
                size = 26.dp,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}
