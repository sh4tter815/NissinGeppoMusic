package com.example.nissingeppomusic.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset

// ドパガキモード用のビカビカ文字（UI共用・軽量版）
// PVの歌詞と同じ虹色グラデ＋金のフチだが、アニメーション位相を持たない静止画にして
// アプリ名や時計など常時表示の文字に使っても重くならないようにしている
private val DopaUiRainbowColors = listOf(
    Color(0xFFFF5DA2), Color(0xFFD633FF), Color(0xFF7A5CFF), Color(0xFF33B5FF),
    Color(0xFF3FE0C5), Color(0xFFFFD93B), Color(0xFFFF8A3B)
)

@Composable
fun DopaFlashText(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified
) {
    val brush = remember { Brush.linearGradient(DopaUiRainbowColors) }
    val annotated = remember(text) {
        buildAnnotatedString {
            withStyle(SpanStyle(brush = brush)) { append(text) }
        }
    }
    val frame = (fontSize.value * 0.055f).dp
    Box(modifier) {
        // 金のフチ（斜め4方向）
        for ((ox, oy) in listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)) {
            Text(
                text = text,
                color = Color(0xFFFFC400),
                fontSize = fontSize,
                fontFamily = DopagakiFontFamily,
                textAlign = textAlign,
                maxLines = maxLines,
                overflow = overflow,
                letterSpacing = letterSpacing,
                lineHeight = lineHeight,
                modifier = Modifier.offset(x = frame * ox, y = frame * oy)
            )
        }
        // 本体: 虹色グラデーション
        Text(
            text = annotated,
            fontSize = fontSize,
            fontFamily = DopagakiFontFamily,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
            letterSpacing = letterSpacing,
            lineHeight = lineHeight
        )
    }
}

// ドパガキモードのUI共用: ゆっくり虹色に巡るグロー色（PV歌詞のグローと同じ 24 段階量子化）
@Composable
fun dopaRainbowColor(seed: Int): Color {
    val trans = rememberInfiniteTransition(label = "dopaRainbow")
    val phase by trans.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart),
        label = "dopaRainbowPhase"
    )
    val step = (phase * 24f).toInt()
    return Color.hsv(((step * 15 + seed * 47) % 360).toFloat(), 0.62f, 1f)
}
