package com.example.nissingeppomusic.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 画面下からチラ見えする円形ページダイヤル。
 * フォルダ選択の円盤と同じ流儀: 塗りなし・白い輪郭線の円、アイコンは円周の内側に配置し、
 * 一番上（真上）に来たページが選択状態。円盤の上には現在のページ名を小さく表示する。
 * 操作はスワイプ式: 指を少し動かすと1ストップ分だけ進み／戻り、その間の回転は
 * アニメーションだけで補間する（指に追従する自由回転はしない）。
 * 右スワイプで前のストップ、左スワイプで次のストップ。離した時点でページが切り替わる。
 * 設定の「スクロール方向の反転」がONのときは向きが逆になる（他の円盤と共通）。
 */
@Composable
fun PeekPageDial(
    currentStop: Int,
    onStopSelected: (Int) -> Unit,
    accentColor: Color = Color(0xFFE53935),
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    // すべてのスクロールに共通の反転オプション（リスト円盤と同じ設定を読む）
    val context = LocalContext.current
    val prefManager = remember { com.example.nissingeppomusic.data.AppPreferenceManager(context) }
    val isReverseScroll = prefManager.isReverseScroll()

    // 押している間はフォルダ選択の円盤（MechanicalDisc）と同じく1.30倍に拡大し、指で隠れないようにする
    var isPressed by remember { mutableStateOf(false) }
    val expandScale by animateFloatAsState(
        targetValue = if (isPressed) 1.30f else 1.0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "DialExpandScale"
    )

    // ストップは6つ: 0=設定, 1=ホーム(再生中), 2=曲名, 3=アーティスト, 4=アルバム, 5=プレイリスト(フォルダ)
    val totalPages = 6
    val stopLabels = listOf("設定", "再生中", "曲名", "アーティスト名", "アルバム名", "プレイリスト")
    val stepAngle = 24f
    val discRadiusDp = 165f
    val baseCenterAngle = -90f // 真上

    // ダイヤルが向かっているストップ。回転はこの値へのアニメーションだけで行う
    var targetStop by remember { mutableIntStateOf(currentStop) }
    val animatableRotation = remember { Animatable(-currentStop * stepAngle) }

    // ページが外部から切り替わったとき（他UIからの遷移など）はダイヤルも追従させる
    LaunchedEffect(currentStop) {
        if (!isPressed) {
            targetStop = currentStop
        }
    }

    // 目標ストップが変わるたびに、回転アニメーションでその位置まで補間移動する
    LaunchedEffect(targetStop) {
        animatableRotation.animateTo(
            targetValue = -targetStop * stepAngle,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
        )
    }

    val currentRotation = animatableRotation.value

    val selectedIndex = remember(currentRotation) {
        (-currentRotation / stepAngle).roundToInt().coerceIn(0, totalPages - 1)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        val discCenterXDp = maxWidth.value / 2f
        val discCenterYDp = 6f + discRadiusDp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            isPressed = true

                            // フレームワーク標準の drag() は、他の円盤にイベントを消費されると
                            // 開始前にキャンセルされてしまう（ホーム画面のお気に入り円盤との競合）。
                            // 消費状態に依存しない自前のループで座標を追い、競合しても動くようにする。
                            var lastPos = down.position
                            // スワイプのステップ判定用: 指の横移動がこのpx数たまるごとに1ストップ進む／戻る
                            val stepThresholdPx = 28f
                            var accumulatedDx = 0f
                            var didSwipe = false
                            var pointerUp = false

                            while (!pointerUp) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null) {
                                    pointerUp = true
                                    break
                                }
                                if (!change.pressed) {
                                    pointerUp = true
                                    break
                                }

                                val dx = change.position.x - lastPos.x
                                lastPos = change.position
                                accumulatedDx += dx

                                // 右スワイプで前のストップ、左スワイプで次のストップへ
                                // （共通の反転オプションがONのときは逆になる）。
                                // 回転そのものは LaunchedEffect(targetStop) のアニメーションが行う
                                val stepForRight = if (isReverseScroll) 1 else -1
                                var stepped = false
                                while (accumulatedDx >= stepThresholdPx) {
                                    accumulatedDx -= stepThresholdPx
                                    val next = targetStop + stepForRight
                                    if (next in 0 until totalPages) {
                                        targetStop = next
                                        stepped = true
                                    }
                                }
                                while (accumulatedDx <= -stepThresholdPx) {
                                    accumulatedDx += stepThresholdPx
                                    val next = targetStop - stepForRight
                                    if (next in 0 until totalPages) {
                                        targetStop = next
                                        stepped = true
                                    }
                                }
                                if (stepped) {
                                    didSwipe = true
                                    change.consume()
                                    // フォルダ／インデックス円盤のパネルと同じ軽いクリック感
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                            }

                            isPressed = false

                            if (didSwipe && targetStop != currentStop) {
                                onStopSelected(targetStop)
                            } else if (!didSwipe) {
                                // スワイプしなかった（タップ等）場合は現在位置に戻す
                                targetStop = currentStop
                            }
                        }
                    }
                }
        ) {
            // フォルダ円盤と同じ白い輪郭線の円（塗りなし）
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(discCenterXDp.dp.toPx(), discCenterYDp.dp.toPx())
                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = (discRadiusDp * expandScale).dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.5f)
                )
            }

            val innerIconRadiusDp = (discRadiusDp - 18f) * expandScale

            for (index in 0 until totalPages) {
                val itemAngle = baseCenterAngle + (index * stepAngle) + currentRotation
                val angleDiffFromCenter = abs(itemAngle - baseCenterAngle)

                if (angleDiffFromCenter <= 80f) {
                    val rad = itemAngle * (PI.toFloat() / 180f)
                    val edgeFade = (1.0f - (angleDiffFromCenter / 80f).coerceIn(0f, 1f).pow(2))
                        .coerceIn(0f, 1f)
                    val isSelected = index == selectedIndex
                    val iconColor = if (isSelected) {
                        accentColor.copy(alpha = edgeFade)
                    } else {
                        Color.White.copy(alpha = 0.6f * edgeFade)
                    }

                    val iconXDp = discCenterXDp + innerIconRadiusDp * cos(rad)
                    val iconYDp = discCenterYDp + innerIconRadiusDp * sin(rad)

                    Box(
                        modifier = Modifier
                            .offset(x = (iconXDp - 22f).dp, y = (iconYDp - 22f).dp)
                            .size(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (index) {
                            0 -> WhiteGearIcon(size = (22f * expandScale).dp, color = iconColor)
                            1 -> WhiteHomeIcon(size = (22f * expandScale).dp, color = iconColor)
                            2 -> WhiteMusicNoteIcon(size = (22f * expandScale).dp, color = iconColor)
                            3 -> WhitePersonIcon(size = (22f * expandScale).dp, color = iconColor)
                            4 -> WhiteAlbumIcon(size = (22f * expandScale).dp, color = iconColor)
                            5 -> WhiteFolderIcon(size = (22f * expandScale).dp, color = iconColor)
                        }
                    }
                }
            }

            // 現在のページ名を、円の外側（ダイヤルの上）に小さくフローティング表示
            Text(
                text = stopLabels[targetStop],
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-34).dp)
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}
