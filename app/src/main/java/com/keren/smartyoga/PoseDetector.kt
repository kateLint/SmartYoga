package com.keren.smartyoga

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class PoseDetector(
    private val context: Context,
    private val listener: (PoseLandmarkerResult, Int, Int) -> Unit
) {
    private var poseLandmarker: PoseLandmarker? = null

    init {
        setupPoseLandmarker()
    }

    private fun setupPoseLandmarker() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_lite.task")
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result: PoseLandmarkerResult, inputImage: MPImage ->
                listener(result, inputImage.width, inputImage.height)
            }
            .build()

        poseLandmarker = PoseLandmarker.createFromOptions(context, options)
    }

    fun detect(bitmap: Bitmap, rotation: Int) {
        // Rotate bitmap if needed (CameraX usually gives rotated bitmap, but we might need to adjust)
        // For simplicity, assuming bitmap is already correct or we handle rotation in CameraAnalyzer
        val mpImage = BitmapImageBuilder(bitmap).build()
        val timestamp = SystemClock.uptimeMillis()
        poseLandmarker?.detectAsync(mpImage, timestamp)
    }

    fun close() {
        poseLandmarker?.close()
    }
}
