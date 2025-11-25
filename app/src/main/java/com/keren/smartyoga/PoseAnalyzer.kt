package com.keren.smartyoga

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.atan2
import kotlin.math.abs

object PoseAnalyzer {

    fun analyzePose(result: PoseLandmarkerResult): String {
        if (result.landmarks().isEmpty()) return "No pose detected"
        
        val landmarks = result.landmarks().get(0)
        
        // Example: Check Warrior II Pose (arms horizontal)
        // Landmarks: 11 (left shoulder), 13 (left elbow), 15 (left wrist)
        // Landmarks: 12 (right shoulder), 14 (right elbow), 16 (right wrist)
        
        // Ensure we have enough landmarks
        if (landmarks.size <= 16) return "Partial pose detected"

        val leftArmAngle = calculateAngle(landmarks[11], landmarks[13], landmarks[15])
        val rightArmAngle = calculateAngle(landmarks[12], landmarks[14], landmarks[16])
        
        if (abs(leftArmAngle - 180) < 20 && abs(rightArmAngle - 180) < 20) {
            return "Great Warrior II!"
        } else {
            return "Straighten your arms! Left: ${leftArmAngle.toInt()}°, Right: ${rightArmAngle.toInt()}°"
        }
    }

    private fun calculateAngle(a: NormalizedLandmark, b: NormalizedLandmark, c: NormalizedLandmark): Double {
        val angle = Math.toDegrees(
            atan2((c.y() - b.y()).toDouble(), (c.x() - b.x()).toDouble()) -
            atan2((a.y() - b.y()).toDouble(), (a.x() - b.x()).toDouble())
        )
        var absoluteAngle = abs(angle)
        if (absoluteAngle > 180) {
            absoluteAngle = 360 - absoluteAngle
        }
        return absoluteAngle
    }
}
