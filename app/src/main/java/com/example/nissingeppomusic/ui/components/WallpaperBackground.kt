package com.example.nissingeppomusic.ui.components

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.nissingeppomusic.data.AppPreferenceManager
import com.example.nissingeppomusic.ui.parseHexColor
import java.io.File

@Composable
fun WallpaperBackground(
    modifier: Modifier = Modifier,
    customPath: String? = null,
    updateKey: Long = 0L,
    isHome: Boolean = false,
    currentAlbumArt: Bitmap? = null,
    // アートワーク連動カラーON時に、アートワークから抽出した背景色でオーバーレイを染める
    dynamicBackground: Color? = null,
    // 液体グラデーション背景を使うか（未選択時／再生中はアート背景の下地）
    liquidEnabled: Boolean = false
) {
    val context = LocalContext.current
    val prefManager = remember { AppPreferenceManager(context) }
    val homeAlpha = remember(isHome) {
        if (isHome) prefManager.getHomeWallpaperAlpha() else 0.8f
    }

    val fileLastModified = remember(customPath, updateKey) {
        if (!customPath.isNullOrBlank() && File(customPath).exists()) File(customPath).lastModified() else 0L
    }

    val backgroundBitmap = remember(customPath, updateKey, fileLastModified) {
        try {
            if (!customPath.isNullOrBlank() && File(customPath).exists()) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(customPath, options)
                var sampleSize = 1
                val maxDimension = 1920
                while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
                    sampleSize *= 2
                }
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeFile(customPath, decodeOptions)?.asImageBitmap()
            } else {
                // 壁紙未選択のときはシステムの壁紙を使わない（背景は真っ黒になる）
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    val accentBase = remember { parseHexColor(prefManager.getAccentColorHex()) }

    Box(modifier = modifier.fillMaxSize()) {
        if (currentAlbumArt != null) {
            Image(
                bitmap = currentAlbumArt.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 64.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background((dynamicBackground ?: Color(0xFF0B101D)).copy(alpha = 0.55f))
            )
            // 液体ON＋配色連動ONのときは、アートワーク背景の一番上に液体を薄く重ねる
            val liquidBase = if (liquidEnabled) dynamicBackground else null
            if (liquidBase != null) {
                LiquidGradientBackground(baseColor = liquidBase, onBlack = false, layerAlpha = 0.65f, alphaBoost = 1.35f)
            }
        } else if (backgroundBitmap != null) {
            Image(
                bitmap = backgroundBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0B101D).copy(alpha = homeAlpha))
            )
        } else if (customPath.isNullOrBlank() && liquidEnabled) {
            // 壁紙未選択＋液体ON: 黒地に液体グラデーションを流す
            LiquidGradientBackground(baseColor = accentBase)
        } else {
            // 壁紙未選択なら真っ暗な黒。読み込みに失敗した場合は従来の濃紺に倒す
            val bgColor = if (customPath.isNullOrBlank()) Color.Black
                else if (isHome) Color(0xFF0B101D).copy(alpha = homeAlpha)
                else Color(0xFF0B101D)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
            )
        }
    }
}

/**
 * 液体グラデーション背景: 大きなグラデーションの丸を3つ、それぞれ違う速さで
 * ゆっくり漂わせ、全体を強くぼかして境界を溶かす。黒地の上に薄く色が動く。
 * パレットは基準色から色相をずらして作る。
 */
@Composable
private fun LiquidGradientBackground(baseColor: Color, onBlack: Boolean = true, layerAlpha: Float = 1f, alphaBoost: Float = 1f) {
    val transition = rememberInfiniteTransition(label = "liquid")
    val t1 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Reverse),
        label = "liquid1"
    )
    val t2 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse),
        label = "liquid2"
    )
    val t3 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(17000, easing = LinearEasing), RepeatMode.Reverse),
        label = "liquid3"
    )
    val t4 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse),
        label = "liquid4"
    )
    val c1 = remember(baseColor) { shiftHue(baseColor, 0f, 1.0f) }
    val c2 = remember(baseColor) { shiftHue(baseColor, 55f, 1.15f) }
    val c3 = remember(baseColor) { shiftHue(baseColor, -45f, 0.55f) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(if (onBlack) Modifier.background(Color.Black) else Modifier)
            .graphicsLayer { alpha = layerAlpha }
    ) {
        Box(modifier = Modifier.fillMaxSize().blur(64.dp)) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val w = maxWidth
                val h = maxHeight
                Box(
                    modifier = Modifier
                        .offset(x = w * (-0.25f + 0.55f * t1), y = h * (-0.15f + 0.35f * t2))
                        .size(w * 0.95f)
                        .background(Brush.radialGradient(listOf(c1.copy(alpha = (0.55f * alphaBoost).coerceAtMost(1f)), Color.Transparent)))
                )
                Box(
                    modifier = Modifier
                        .offset(x = w * (0.35f - 0.50f * t3), y = h * (0.30f - 0.35f * t1))
                        .size(w * 0.85f)
                        .background(Brush.radialGradient(listOf(c2.copy(alpha = (0.45f * alphaBoost).coerceAtMost(1f)), Color.Transparent)))
                )
                Box(
                    modifier = Modifier
                        .offset(x = w * (-0.10f + 0.45f * t4), y = h * (0.55f - 0.40f * t3))
                        .size(w * 1.05f)
                        .background(Brush.radialGradient(listOf(c3.copy(alpha = (0.50f * alphaBoost).coerceAtMost(1f)), Color.Transparent)))
                )
            }
        }
    }
}

/** 基準色の色相をずらした色を作る。彩度が低すぎると液体が見えないので下限を設ける */
private fun shiftHue(color: Color, hueDelta: Float, valueMul: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color.red * 255).toInt().coerceIn(0, 255),
        (color.green * 255).toInt().coerceIn(0, 255),
        (color.blue * 255).toInt().coerceIn(0, 255),
        hsv
    )
    hsv[0] = (hsv[0] + hueDelta + 360f) % 360f
    hsv[1] = maxOf(hsv[1], 0.45f)
    hsv[2] = (hsv[2] * valueMul).coerceIn(0.08f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}
