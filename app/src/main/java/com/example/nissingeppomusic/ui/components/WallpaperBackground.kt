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
    // アートワーク背景の見せ方: "solid"（単色）/ "blur"（ぼかし画像）
    artworkBackgroundStyle: String = "solid",
    // 液体グラデーション背景を使うか（壁紙なし時／再生中はアート背景の上に薄く）
    liquidEnabled: Boolean = false,
    // 液体の基準色（アートワークから抽出した明るいアクセント色。音楽アプリと同じ見え方にする）
    liquidAccent: Color? = null,
    // 追加設定: 液体の速さ・濃さ倍率 / 壁紙のぼかし
    liquidSpeed: Float = 1f,
    liquidAlphaMul: Float = 1f,
    wallpaperBlurDp: Float = 0f
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
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeFile(customPath, decodeOptions)?.asImageBitmap()
            } else {
                // 壁紙未選択のときはシステムの壁紙を使わない（液体 or 真っ黒になる）
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    // ぼかし背景は曲ごとに1回だけ作る静止画（256pxまで縮小→強いボックスブラーを3回→
    // 画面サイズへバイリニアで引き伸ばし）。毎フレームの再計算はないので軽い
    val blurredArtImage = remember(currentAlbumArt) {
        currentAlbumArt?.let { makeBlurredBackground(it).asImageBitmap() }
    }

    val accentBase = remember { parseHexColor(prefManager.getAccentColorHex()) }

    Box(modifier = modifier.fillMaxSize()) {
        if (currentAlbumArt != null && artworkBackgroundStyle == "blur" && blurredArtImage != null) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawImage(
                    image = blurredArtImage,
                    dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.High
                )
            }
            // 音楽アプリと同じく、抽出した背景色を55%で重ねて色味を残す
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background((dynamicBackground ?: Color(0xFF0B101D)).copy(alpha = if (liquidEnabled) 0.68f else 0.55f))
            )
            // 液体ONのときは、アートワーク背景の一番上に液体を薄く重ねる
            if (liquidEnabled) {
                LiquidGradientBackground(baseColor = liquidAccent ?: dynamicBackground ?: accentBase, onBlack = false, layerAlpha = 1f, alphaBoost = 1.9f, speedFactor = liquidSpeed, alphaMul = liquidAlphaMul)
            }
        } else if (currentAlbumArt != null) {
            // アートワーク連動: 抽出した背景色で単色に塗る
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(dynamicBackground ?: Color(0xFF0B101D))
            )
            if (liquidEnabled) {
                LiquidGradientBackground(baseColor = liquidAccent ?: dynamicBackground ?: accentBase, onBlack = false, layerAlpha = 1f, alphaBoost = 1.9f, speedFactor = liquidSpeed, alphaMul = liquidAlphaMul)
            }
        } else if (backgroundBitmap != null) {
            Image(
                bitmap = backgroundBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(if (wallpaperBlurDp > 0f) Modifier.blur(wallpaperBlurDp.dp) else Modifier)
            )
            // ホーム画面は設定された不透明度（alpha）、他画面は暗め（0.8f）のオーバーレイ
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0B101D).copy(alpha = if (liquidEnabled) maxOf(homeAlpha, 0.62f) else homeAlpha))
            )
            // 壁紙画像を適用していても、液体ONなら画像の上に薄く液体を流す
            if (liquidEnabled) {
                LiquidGradientBackground(baseColor = liquidAccent ?: accentBase, onBlack = false, layerAlpha = 0.85f, alphaBoost = 1.7f, speedFactor = liquidSpeed, alphaMul = liquidAlphaMul)
            }
        } else if (customPath.isNullOrBlank() && liquidEnabled) {
            // 壁紙未選択＋液体ON: 黒地に液体グラデーションを流す
            LiquidGradientBackground(baseColor = liquidAccent ?: accentBase, alphaBoost = 1.6f, speedFactor = liquidSpeed, alphaMul = liquidAlphaMul)
        } else {
            // 壁紙未選択なら真っ暗な黒
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
 * ゆっくり漂わせ、全体を強くぼかして境界を溶かす。音楽アプリと同一実装。
 * パレットは基準色から色相をずらして作る。
 */
@Composable
fun LiquidGradientBackground(baseColor: Color, onBlack: Boolean = true, layerAlpha: Float = 1f, alphaBoost: Float = 1f, speedFactor: Float = 1f, alphaMul: Float = 1f) {
    val effBoost = alphaBoost * alphaMul
    val transition = rememberInfiniteTransition(label = "liquid")
    val t1 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween((11000 / speedFactor).toInt().coerceAtLeast(500), easing = LinearEasing), RepeatMode.Reverse),
        label = "liquid1"
    )
    val t2 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween((14000 / speedFactor).toInt().coerceAtLeast(500), easing = LinearEasing), RepeatMode.Reverse),
        label = "liquid2"
    )
    val t3 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween((17000 / speedFactor).toInt().coerceAtLeast(500), easing = LinearEasing), RepeatMode.Reverse),
        label = "liquid3"
    )
    val t4 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween((9000 / speedFactor).toInt().coerceAtLeast(500), easing = LinearEasing), RepeatMode.Reverse),
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
                        .background(Brush.radialGradient(listOf(c1.copy(alpha = (0.55f * effBoost).coerceAtMost(1f)), Color.Transparent)))
                )
                Box(
                    modifier = Modifier
                        .offset(x = w * (0.35f - 0.50f * t3), y = h * (0.30f - 0.35f * t1))
                        .size(w * 0.85f)
                        .background(Brush.radialGradient(listOf(c2.copy(alpha = (0.45f * effBoost).coerceAtMost(1f)), Color.Transparent)))
                )
                Box(
                    modifier = Modifier
                        .offset(x = w * (-0.10f + 0.45f * t4), y = h * (0.55f - 0.40f * t3))
                        .size(w * 1.05f)
                        .background(Brush.radialGradient(listOf(c3.copy(alpha = (0.50f * effBoost).coerceAtMost(1f)), Color.Transparent)))
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

private fun boxBlurBitmap(src: Bitmap, radius: Int): Bitmap {
    val w = src.width
    val h = src.height
    if (w <= 0 || h <= 0 || radius <= 0) return src
    val pixels = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)
    val tmp = IntArray(w * h)
    val div = 2 * radius + 1
    fun a(p: Int) = android.graphics.Color.alpha(p)
    fun r(p: Int) = android.graphics.Color.red(p)
    fun g(p: Int) = android.graphics.Color.green(p)
    fun b(p: Int) = android.graphics.Color.blue(p)
    // 横方向
    for (y in 0 until h) {
        var sa = 0; var sr = 0; var sg = 0; var sb = 0
        val row = y * w
        for (i in -radius..radius) {
            val p = pixels[row + i.coerceIn(0, w - 1)]
            sa += a(p); sr += r(p); sg += g(p); sb += b(p)
        }
        for (x in 0 until w) {
            tmp[row + x] = android.graphics.Color.argb(sa / div, sr / div, sg / div, sb / div)
            val po = pixels[row + (x - radius).coerceIn(0, w - 1)]
            val pi = pixels[row + (x + radius + 1).coerceIn(0, w - 1)]
            sa += a(pi) - a(po); sr += r(pi) - r(po); sg += g(pi) - g(po); sb += b(pi) - b(po)
        }
    }
    // 縦方向
    for (x in 0 until w) {
        var sa = 0; var sr = 0; var sg = 0; var sb = 0
        for (i in -radius..radius) {
            val p = tmp[i.coerceIn(0, h - 1) * w + x]
            sa += a(p); sr += r(p); sg += g(p); sb += b(p)
        }
        for (y in 0 until h) {
            pixels[y * w + x] = android.graphics.Color.argb(sa / div, sr / div, sg / div, sb / div)
            val po = tmp[(y - radius).coerceIn(0, h - 1) * w + x]
            val pi = tmp[(y + radius + 1).coerceIn(0, h - 1) * w + x]
            sa += a(pi) - a(po); sr += r(pi) - r(po); sg += g(pi) - g(po); sb += b(pi) - b(po)
        }
    }
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    out.setPixels(pixels, 0, w, 0, 0, w, h)
    return out
}

/** アートワークからぼかし背景用の小さな静止画を作る */
private fun makeBlurredBackground(source: Bitmap): Bitmap {
    val targetW = 256
    val targetH = maxOf(1, (source.height * (targetW.toFloat() / source.width)).toInt())
    var small = Bitmap.createScaledBitmap(source, targetW, targetH, true)
    small = boxBlurBitmap(small, 26)
    small = boxBlurBitmap(small, 26)
    small = boxBlurBitmap(small, 26)
    return small
}
