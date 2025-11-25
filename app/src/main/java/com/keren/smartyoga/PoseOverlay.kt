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
    result: PoseResult?,
    poseLandmarkerResult: PoseLandmarkerResult?,
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

        poseLandmarkerResult?.landmarks()?.firstOrNull()?.let { landmarks ->
            val points = landmarks.map { 
                // Mirror X for front camera (1 - x)
                val x = (1f - it.x()) * scaledWidth + offsetX
                val y = it.y() * scaledHeight + offsetY
                Offset(x, y)
            }
            
            // Draw points
            points.forEachIndexed { index, point ->
                val color = if (result?.correctLandmarks?.contains(index) == true) Color.Green 
                           else if (result?.incorrectLandmarks?.contains(index) == true) Color.Red 
                           else Color.White
                
                drawCircle(
                    color = color,
                    radius = 10f,
                    center = point
                )
            }
            
            // Helper to draw connection
            fun drawConnection(start: Int, end: Int) {
                if (points.size > maxOf(start, end)) {
                    val isCorrect = result?.correctLandmarks?.contains(start) == true && 
                                    result?.correctLandmarks?.contains(end) == true
                    val color = if (isCorrect) Color.Green else Color.White
                    
                    drawLine(
                        color = color,
                        start = points[start],
                        end = points[end],
                        strokeWidth = 8f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // Draw connections
            // Left Arm
            drawConnection(11, 13)
            drawConnection(13, 15)
            
            // Right Arm
            drawConnection(12, 14)
            drawConnection(14, 16)
            
            // Shoulders
            drawConnection(11, 12)
            
            // Body
            drawConnection(11, 23)
            drawConnection(12, 24)
            drawConnection(23, 24)
            
            // Legs
            drawConnection(23, 25)
            drawConnection(25, 27)
            drawConnection(24, 26)
            drawConnection(26, 28)
        }
    }
}
