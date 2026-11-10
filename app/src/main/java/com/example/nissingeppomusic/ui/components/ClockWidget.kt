package com.example.nissingeppomusic.ui.components

import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ClockWidget(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentTime by remember { mutableStateOf(Date()) }

    // 1秒毎にシステム時刻を更新
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Date()
            delay(1000)
        }
    }

    val hourFormat = SimpleDateFormat("HH", Locale.getDefault())
    val minuteFormat = SimpleDateFormat("mm", Locale.getDefault())
    val dateFormat = SimpleDateFormat("M/d (E)", Locale.JAPANESE)

    val hourText = hourFormat.format(currentTime)
    val minuteText = minuteFormat.format(currentTime)
    val dateText = dateFormat.format(currentTime)

    Column(modifier = modifier) {
        // 時刻ブロック (タップで時計アプリ起動)
        Column(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    try {
                        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        try {
                            val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.deskclock")
                                ?: context.packageManager.getLaunchIntentForPackage("com.android.deskclock")
                            if (intent != null) context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                }
        ) {
            Text(
                text = hourText,
                color = Color.White,
                fontSize = 54.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 54.sp
            )
            Text(
                text = minuteText,
                color = Color.White,
                fontSize = 54.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 54.sp
            )
        }

        // 日付ブロック (タップでカレンダーアプリ起動)
        Text(
            text = dateText,
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .padding(top = 16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = android.provider.CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build()
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        try {
                            val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.calendar")
                                ?: context.packageManager.getLaunchIntentForPackage("com.android.calendar")
                            if (intent != null) context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                }
        )
    }
}
