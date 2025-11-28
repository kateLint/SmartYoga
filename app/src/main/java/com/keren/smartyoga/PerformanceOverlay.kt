package com.keren.smartyoga

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PerformanceOverlay(inferenceTime: Long) {
    Column(
        modifier = Modifier
            .padding(top = 80.dp, start = 16.dp) // Avoid overlapping with top bar
            .background(Color.Black.copy(alpha = 0.6f), shape = MaterialTheme.shapes.small)
            .padding(8.dp)
    ) {
        Text(
            text = "Inference Time: ${inferenceTime}ms",
            color = Color.Green,
            fontSize = 12.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
        Text(
            text = "FPS: ${if (inferenceTime > 0) 1000 / inferenceTime else 0}",
            color = Color.Green,
            fontSize = 12.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
        Text(
            text = "Device: GPU",
            color = Color.Green,
            fontSize = 12.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}
