package com.keren.smartyoga

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

@Composable
fun PoseOverlay(
    result: PoseLandmarkerResult?,
    inputDims: Pair<Int, Int>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val (imgW, imgH) = inputDims
        if (imgW == 0 || imgH == 0) return@Canvas

        // Calculate scale to fill screen (CenterCrop)
        val scale = maxOf(size.width / imgW, size.height / imgH)
        
        // Calculate offsets to center the scaled image
        val scaledWidth = imgW * scale
        val scaledHeight = imgH * scale
        val offsetX = (size.width - scaledWidth) / 2f
        val offsetY = (size.height - scaledHeight) / 2f

        result?.landmarks()?.firstOrNull()?.let { landmarks ->
            val points = landmarks.map { 
                // Mirror X for front camera (1 - x)
                val x = (1f - it.x()) * scaledWidth + offsetX
                val y = it.y() * scaledHeight + offsetY
                Offset(x, y)
            }
            
            // Draw points
            drawPoints(
                points = points,
                pointMode = PointMode.Points,
                color = Color.Red,
                strokeWidth = 20f,
                cap = StrokeCap.Round
            )
            
            // Draw connections
            // Left Arm
            if (points.size > 15) {
                drawLine(Color.Green, points[11], points[13], 10f)
                drawLine(Color.Green, points[13], points[15], 10f)
            }
            
            // Right Arm
            if (points.size > 16) {
                drawLine(Color.Green, points[12], points[14], 10f)
                drawLine(Color.Green, points[14], points[16], 10f)
            }
            
            // Shoulders
            if (points.size > 12) {
                drawLine(Color.Green, points[11], points[12], 10f)
            }
        }
    }
}
