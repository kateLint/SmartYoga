package com.keren.smartyoga

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.atan2
import kotlin.math.abs



data class PoseResult(
    val poseName: String,
    val isCorrect: Boolean,
    val feedback: String,
    val correctLandmarks: List<Int> = emptyList(),
    val incorrectLandmarks: List<Int> = emptyList()
)

enum class TargetPose {
    WARRIOR_II,
    TREE_POSE,
    WARRIOR_I,
    DOWN_DOG,
    COBRA
}

object PoseAnalyzer {

    fun analyzePose(result: PoseLandmarkerResult, targetPose: TargetPose): PoseResult {
        if (result.landmarks().isEmpty()) {
            return PoseResult(targetPose.name, false, "No pose detected")
        }
        
        val landmarks = result.landmarks().get(0)
        
        return when (targetPose) {
            TargetPose.WARRIOR_II -> analyzeWarriorII(landmarks)
            TargetPose.TREE_POSE -> analyzeTreePose(landmarks)
            TargetPose.WARRIOR_I -> analyzeWarriorI(landmarks)
            TargetPose.DOWN_DOG -> analyzeDownDog(landmarks)
            TargetPose.COBRA -> analyzeCobra(landmarks)
        }
    }

    private fun analyzeWarriorII(landmarks: List<NormalizedLandmark>): PoseResult {
        if (landmarks.size <= 16) return PoseResult("Warrior II", false, "Partial detection")

        val leftArmAngle = calculateAngle(landmarks[11], landmarks[13], landmarks[15])
        val rightArmAngle = calculateAngle(landmarks[12], landmarks[14], landmarks[16])
        
        val isLeftArmCorrect = abs(leftArmAngle - 180) < 20
        val isRightArmCorrect = abs(rightArmAngle - 180) < 20
        
        val correctLandmarks = mutableListOf<Int>()
        val incorrectLandmarks = mutableListOf<Int>()
        
        if (isLeftArmCorrect) correctLandmarks.addAll(listOf(11, 13, 15)) else incorrectLandmarks.addAll(listOf(11, 13, 15))
        if (isRightArmCorrect) correctLandmarks.addAll(listOf(12, 14, 16)) else incorrectLandmarks.addAll(listOf(12, 14, 16))

        if (isLeftArmCorrect && isRightArmCorrect) {
            return PoseResult("Warrior II", true, "Great Warrior II!", correctLandmarks, incorrectLandmarks)
        } else {
            return PoseResult("Warrior II", false, "Straighten arms!", correctLandmarks, incorrectLandmarks)
        }
    }

    private fun analyzeTreePose(landmarks: List<NormalizedLandmark>): PoseResult {
        // Simplified Tree Pose: Check if one foot is raised near the other knee
        // Left Knee (25), Left Ankle (27), Right Knee (26), Right Ankle (28)
        if (landmarks.size <= 28) return PoseResult("Tree Pose", false, "Partial detection")
        
        val leftFootY = landmarks[27].y()
        val rightFootY = landmarks[28].y()
        val leftKneeY = landmarks[25].y()
        val rightKneeY = landmarks[26].y()
        
        // Check if left foot is raised (higher Y value means lower on screen, so smaller Y is higher)
        // Actually Y increases downwards. So raised foot has smaller Y.
        // Check if one foot is significantly higher than the other (e.g. near knee height)
        
        val isLeftFootRaised = leftFootY < rightKneeY + 0.1 // Tolerance
        val isRightFootRaised = rightFootY < leftKneeY + 0.1
        
        if (isLeftFootRaised || isRightFootRaised) {
             return PoseResult("Tree Pose", true, "Great Balance!", listOf(25, 26, 27, 28), emptyList())
        }
        
        return PoseResult("Tree Pose", false, "Raise one foot to knee", emptyList(), listOf(27, 28))
    }

    private fun analyzeWarriorI(landmarks: List<NormalizedLandmark>): PoseResult {
        // Warrior I: Arms raised overhead (near 180 degrees vertical)
        if (landmarks.size <= 16) return PoseResult("Warrior I", false, "Partial detection")
        
        // Check arms raised
        // Angle: Shoulder-Elbow-Wrist should be straight (180) AND Shoulder-Hip-Elbow should be ~180 (vertical)
        // Simplified: Check if wrists are above shoulders (smaller Y)
        
        val leftWristY = landmarks[15].y()
        val rightWristY = landmarks[16].y()
        val leftShoulderY = landmarks[11].y()
        val rightShoulderY = landmarks[12].y()
        
        if (leftWristY < leftShoulderY && rightWristY < rightShoulderY) {
             return PoseResult("Warrior I", true, "Powerful Warrior!", listOf(11, 12, 15, 16), emptyList())
        }
        
        return PoseResult("Warrior I", false, "Raise arms overhead", emptyList(), listOf(15, 16))
    }

    private fun analyzeDownDog(landmarks: List<NormalizedLandmark>): PoseResult {
        // Down Dog: Inverted V shape. 
        // Hips (23, 24) should be the highest point (lowest Y).
        // Shoulders (11, 12) and Ankles (27, 28) should be lower (higher Y).
        // Angle at Hips should be ~60-90 degrees.
        
        if (landmarks.size <= 28) return PoseResult("Down Dog", false, "Partial detection")
        
        val hipY = (landmarks[23].y() + landmarks[24].y()) / 2
        val shoulderY = (landmarks[11].y() + landmarks[12].y()) / 2
        val ankleY = (landmarks[27].y() + landmarks[28].y()) / 2
        
        // Check if hips are above shoulders and ankles
        if (hipY < shoulderY && hipY < ankleY) {
            return PoseResult("Down Dog", true, "Great Down Dog!", listOf(11, 12, 23, 24, 27, 28), emptyList())
        }
        
        return PoseResult("Down Dog", false, "Lift your hips high!", emptyList(), listOf(23, 24))
    }

    private fun analyzeCobra(landmarks: List<NormalizedLandmark>): PoseResult {
        // Cobra: Lying prone, chest lifted.
        // Hips (23, 24) on ground (low Y).
        // Shoulders (11, 12) significantly higher than hips (smaller Y).
        
        if (landmarks.size <= 24) return PoseResult("Cobra", false, "Partial detection")
        
        val hipY = (landmarks[23].y() + landmarks[24].y()) / 2
        val shoulderY = (landmarks[11].y() + landmarks[12].y()) / 2
        
        // Check if shoulders are above hips (smaller Y)
        if (shoulderY < hipY - 0.1) { // 0.1 threshold
             return PoseResult("Cobra", true, "Great Cobra!", listOf(11, 12, 23, 24), emptyList())
        }
        
        return PoseResult("Cobra", false, "Lift your chest!", emptyList(), listOf(11, 12))
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
