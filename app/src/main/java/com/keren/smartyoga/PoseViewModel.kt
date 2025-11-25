package com.keren.smartyoga

import androidx.lifecycle.ViewModel
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PoseViewModel : ViewModel() {
    private val _poseResult = MutableStateFlow<PoseLandmarkerResult?>(null)
    val poseResult: StateFlow<PoseLandmarkerResult?> = _poseResult.asStateFlow()
    
    private val _inputDims = MutableStateFlow<Pair<Int, Int>>(Pair(0, 0))
    val inputDims: StateFlow<Pair<Int, Int>> = _inputDims.asStateFlow()
    
    private val _feedback = MutableStateFlow("Waiting for pose...")
    val feedback: StateFlow<String> = _feedback.asStateFlow()

    fun onPoseDetected(result: PoseLandmarkerResult, width: Int, height: Int) {
        _poseResult.value = result
        _inputDims.value = Pair(width, height)
        _feedback.value = PoseAnalyzer.analyzePose(result)
    }
}
