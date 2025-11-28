package com.keren.smartyoga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import android.util.Log
import com.keren.smartyoga.PoseAnalyzer
import com.keren.smartyoga.YogaGuruEngine
import java.math.BigDecimal
class PoseViewModel : ViewModel() {
    private val _poseResult = MutableStateFlow<PoseResult?>(null)
    val poseResult: StateFlow<PoseResult?> = _poseResult.asStateFlow()
    
    private val _rawPoseResult = MutableStateFlow<PoseLandmarkerResult?>(null)
    val rawPoseResult: StateFlow<PoseLandmarkerResult?> = _rawPoseResult.asStateFlow()
    
    private val _inputDims = MutableStateFlow<Pair<Int, Int>>(Pair(0, 0))
    val inputDims: StateFlow<Pair<Int, Int>> = _inputDims.asStateFlow()
    
    // Session State
    private val poses = listOf(
        TargetPose.CALIBRATION,
        TargetPose.WARRIOR_II, 
        TargetPose.TREE_POSE, 
        TargetPose.WARRIOR_I,
        TargetPose.DOWN_DOG,
        TargetPose.COBRA
    )
    private var currentPoseIndex = 0
    
    private val _currentPose = MutableStateFlow(poses[currentPoseIndex])
    val currentPose: StateFlow<TargetPose> = _currentPose.asStateFlow()
    
    private val _timerValue = MutableStateFlow(0)
    val timerValue: StateFlow<Int> = _timerValue.asStateFlow()
    
    private val _isSessionComplete = MutableStateFlow(false)
    val isSessionComplete: StateFlow<Boolean> = _isSessionComplete.asStateFlow()

    private var timerJob: Job? = null
    private val HOLD_DURATION = 5 // seconds
    
    // Tracking (will be set from MainActivity)
    var sessionTracker: SessionTracker? = null

    // Segmentation State
    private val _maskBuffer = MutableStateFlow<java.nio.ByteBuffer?>(null)
    val maskBuffer: StateFlow<java.nio.ByteBuffer?> = _maskBuffer.asStateFlow()
    
    private val _maskDims = MutableStateFlow<Pair<Int, Int>>(Pair(0, 0))
    val maskDims: StateFlow<Pair<Int, Int>> = _maskDims.asStateFlow()
    
    private val _backgroundBitmap = MutableStateFlow<android.graphics.Bitmap?>(null)
    val backgroundBitmap: StateFlow<android.graphics.Bitmap?> = _backgroundBitmap.asStateFlow()
    
    private val _inferenceTime = MutableStateFlow(0L)
    val inferenceTime: StateFlow<Long> = _inferenceTime.asStateFlow()

    // Logic & Stats State
    private val landmarkHistory = ArrayDeque<List<NormalizedLandmark>>()
    private var calibrationData = PoseAnalyzer.CalibrationData()

    fun setBackground(bitmap: android.graphics.Bitmap?) {
        _backgroundBitmap.value = bitmap
    }

    fun onPoseDetected(result: PoseLandmarkerResult, width: Int, height: Int, inputImage: com.google.mediapipe.framework.image.MPImage?, originalBitmap: android.graphics.Bitmap?, inferenceTime: Long) {
        _inferenceTime.value = inferenceTime
        if (_isSessionComplete.value) return

        _inputDims.value = Pair(width, height)

        // Update History
        if (result.landmarks().isNotEmpty()) {
            landmarkHistory.addLast(result.landmarks().get(0))
            if (landmarkHistory.size > 30) { // Keep last ~1 sec (30fps)
                landmarkHistory.removeFirst()
            }
        }

        val analysis = PoseAnalyzer.analyzePose(result, _currentPose.value, landmarkHistory, calibrationData)
        _poseResult.value = analysis

        if (analysis.isCorrect) {
            // Special handling for Calibration
            if (_currentPose.value == TargetPose.CALIBRATION) {
                if (result.landmarks().isNotEmpty()) {
                    val landmarks = result.landmarks().get(0)
                    val leftArmAngle = PoseAnalyzer.calculateAngle(landmarks[11], landmarks[13], landmarks[15])
                    val rightArmAngle = PoseAnalyzer.calculateAngle(landmarks[12], landmarks[14], landmarks[16])
                    val avgAngle = (leftArmAngle + rightArmAngle) / 2.0
                    calibrationData = calibrationData.copy(armStraightAngle = avgAngle)
                }
            }
            startTimer()
        } else {
            resetTimer()
        }

        // Handle Segmentation
        if (result.segmentationMasks().isPresent) {
            val mask = result.segmentationMasks().get()[0]
            val originalBuffer = com.google.mediapipe.framework.image.ByteBufferExtractor.extract(mask)

            // Copy the buffer since MediaPipe may reuse it
            val bufferSize = originalBuffer.remaining()
            val copiedBuffer = java.nio.ByteBuffer.allocateDirect(bufferSize)
            copiedBuffer.put(originalBuffer)
            copiedBuffer.rewind()
            originalBuffer.rewind()

            _maskBuffer.value = copiedBuffer
            _maskDims.value = Pair(mask.width, mask.height)

            Log.d("PoseViewModel", "Mask received: ${mask.width}x${mask.height}, buffer size: $bufferSize")
        } else {
            _maskBuffer.value = null
            Log.d("PoseViewModel", "No segmentation mask available")
        }
    }
    
    // Chat State
    private val _isChatMode = MutableStateFlow(false)
    val isChatMode: StateFlow<Boolean> = _isChatMode.asStateFlow()
    
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()
    
    private val _isLlmLoading = MutableStateFlow(false)
    val isLlmLoading: StateFlow<Boolean> = _isLlmLoading.asStateFlow()
    
    private var yogaGuruEngine: YogaGuruEngine? = null
    
    // Initialize Engine lazily or on enterChatMode
    
    private val _isDownloadingModel = MutableStateFlow(false)
    val isDownloadingModel: StateFlow<Boolean> = _isDownloadingModel.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    fun enterChatMode(context: android.content.Context) {
        _isChatMode.value = true
        
        if (yogaGuruEngine == null) {
            yogaGuruEngine = YogaGuruEngine(context)
            
            // Start collecting responses
            viewModelScope.launch {
                yogaGuruEngine?.responseFlow?.collect { responseChunk ->
                    _isLlmLoading.value = false
                    val currentMessages = _chatMessages.value.toMutableList()
                    if (currentMessages.isNotEmpty() && !currentMessages.last().isUser) {
                        val lastMsg = currentMessages.removeAt(currentMessages.size - 1)
                        currentMessages.add(lastMsg.copy(text = lastMsg.text + responseChunk))
                    } else {
                        currentMessages.add(ChatMessage(responseChunk, false))
                    }
                    _chatMessages.value = currentMessages
                }
            }
        }
        
        // Check if model exists
        if (!ModelDownloader.isModelDownloaded(context)) {
            startModelDownload(context)
        } else {
            initializeEngine()
        }
    }

    private fun startModelDownload(context: android.content.Context) {
        if (_isDownloadingModel.value) return
        
        _isDownloadingModel.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val success = ModelDownloader.downloadModel(context) { progress ->
                _downloadProgress.value = progress
            }
            _isDownloadingModel.value = false
            if (success) {
                initializeEngine()
            } else {
                val currentMessages = _chatMessages.value.toMutableList()
                currentMessages.add(ChatMessage("Error: Failed to download model. Please check internet connection.", false))
                _chatMessages.value = currentMessages
            }
        }
    }

    private fun initializeEngine() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLlmLoading.value = true
            yogaGuruEngine?.initialize()
            _isLlmLoading.value = false
        }
    }
    
    fun exitChatMode() {
        _isChatMode.value = false
        // We can keep the engine alive for faster re-entry, or close it to save RAM.
        // For now, let's keep it alive but maybe release if memory is tight.
        // yogaGuruEngine?.close() 
        // yogaGuruEngine = null
    }
    
    fun sendChatMessage(text: String) {
        val currentMessages = _chatMessages.value.toMutableList()
        currentMessages.add(ChatMessage(text, true))
        _chatMessages.value = currentMessages
        
        _isLlmLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            yogaGuruEngine?.generateResponse(text)
        }
    }

    fun restartSession() {
        currentPoseIndex = 0
        _currentPose.value = poses[0]
        _isSessionComplete.value = false
        resetTimer()
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
            sessionTracker?.logSessionComplete()
        }
    }
}
