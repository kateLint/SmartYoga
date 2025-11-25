package com.keren.smartyoga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PoseViewModel : ViewModel() {
    private val _poseResult = MutableStateFlow<PoseResult?>(null)
    val poseResult: StateFlow<PoseResult?> = _poseResult.asStateFlow()
    
    private val _rawPoseResult = MutableStateFlow<PoseLandmarkerResult?>(null)
    val rawPoseResult: StateFlow<PoseLandmarkerResult?> = _rawPoseResult.asStateFlow()
    
    private val _inputDims = MutableStateFlow<Pair<Int, Int>>(Pair(0, 0))
    val inputDims: StateFlow<Pair<Int, Int>> = _inputDims.asStateFlow()
    
    // Session State
    private val poses = listOf(TargetPose.WARRIOR_II, TargetPose.TREE_POSE, TargetPose.WARRIOR_I)
    private var currentPoseIndex = 0
    
    private val _currentPose = MutableStateFlow(poses[currentPoseIndex])
    val currentPose: StateFlow<TargetPose> = _currentPose.asStateFlow()
    
    private val _timerValue = MutableStateFlow(0)
    val timerValue: StateFlow<Int> = _timerValue.asStateFlow()
    
    private val _isSessionComplete = MutableStateFlow(false)
    val isSessionComplete: StateFlow<Boolean> = _isSessionComplete.asStateFlow()

    private var timerJob: Job? = null
    private val HOLD_DURATION = 5 // seconds

    fun onPoseDetected(result: PoseLandmarkerResult, width: Int, height: Int) {
        if (_isSessionComplete.value) return

        _inputDims.value = Pair(width, height)
        _rawPoseResult.value = result
        
        val analysis = PoseAnalyzer.analyzePose(result, _currentPose.value)
        _poseResult.value = analysis
        
        if (analysis.isCorrect) {
            startTimer()
        } else {
            resetTimer()
        }
    }
    
    private fun startTimer() {
        if (timerJob?.isActive == true) return
        
        timerJob = viewModelScope.launch {
            while (_timerValue.value < HOLD_DURATION) {
                delay(1000)
                _timerValue.value += 1
            }
            // Pose Complete
            advanceToNextPose()
        }
    }
    
    private fun resetTimer() {
        timerJob?.cancel()
        timerJob = null
        _timerValue.value = 0
    }
    
    private fun advanceToNextPose() {
        resetTimer()
        if (currentPoseIndex < poses.size - 1) {
            currentPoseIndex++
            _currentPose.value = poses[currentPoseIndex]
        } else {
            _isSessionComplete.value = true
        }
    }
}
