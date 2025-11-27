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
import kotlinx.coroutines.Dispatchers
import android.util.Log

class PoseViewModel : ViewModel() {
    private val _poseResult = MutableStateFlow<PoseResult?>(null)
    val poseResult: StateFlow<PoseResult?> = _poseResult.asStateFlow()
    
    private val _rawPoseResult = MutableStateFlow<PoseLandmarkerResult?>(null)
    val rawPoseResult: StateFlow<PoseLandmarkerResult?> = _rawPoseResult.asStateFlow()
    
    private val _inputDims = MutableStateFlow<Pair<Int, Int>>(Pair(0, 0))
    val inputDims: StateFlow<Pair<Int, Int>> = _inputDims.asStateFlow()
    
    // Session State
    private val poses = listOf(
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
    private val _segmentedBitmap = MutableStateFlow<android.graphics.Bitmap?>(null)
    val segmentedBitmap: StateFlow<android.graphics.Bitmap?> = _segmentedBitmap.asStateFlow()
    
    private val _backgroundBitmap = MutableStateFlow<android.graphics.Bitmap?>(null)
    val backgroundBitmap: StateFlow<android.graphics.Bitmap?> = _backgroundBitmap.asStateFlow()
    
    fun setBackground(bitmap: android.graphics.Bitmap?) {
        _backgroundBitmap.value = bitmap
    }

    fun onPoseDetected(result: PoseLandmarkerResult, width: Int, height: Int, inputImage: com.google.mediapipe.framework.image.MPImage?, originalBitmap: android.graphics.Bitmap?) {
        if (_isSessionComplete.value) return

        _inputDims.value = Pair(width, height)

        val analysis = PoseAnalyzer.analyzePose(result, _currentPose.value)
        _poseResult.value = analysis

        if (analysis.isCorrect) {
            startTimer()
        } else {
            resetTimer()
        }

        // Handle Segmentation if background is set
        if (_backgroundBitmap.value != null && originalBitmap != null) {
            // If we have a mask, use it. If not (no person detected), use an empty mask or just draw background.
            val mask = if (result.segmentationMasks().isPresent) result.segmentationMasks().get()[0] else null
            processSegmentation(originalBitmap, mask, _backgroundBitmap.value!!)
        } else {
            _segmentedBitmap.value = null
        }
    }
    
    private var isProcessingFrame = false
    
    private fun processSegmentation(inputBitmap: android.graphics.Bitmap, mask: com.google.mediapipe.framework.image.MPImage?, background: android.graphics.Bitmap) {
        if (isProcessingFrame) return

        // Mirroring (Front Camera)
        val matrix = android.graphics.Matrix()
        matrix.preScale(-1f, 1f)
        val mirroredInput = android.graphics.Bitmap.createBitmap(inputBitmap, 0, 0, inputBitmap.width, inputBitmap.height, matrix, true)

        val maskBitmap = if (mask != null) convertMaskToBitmap(mask) else null
        val mirroredMask = if (maskBitmap != null) {
             android.graphics.Bitmap.createBitmap(maskBitmap, 0, 0, maskBitmap.width, maskBitmap.height, matrix, true)
        } else null

        isProcessingFrame = true
        
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val startTime = System.currentTimeMillis()
                
                // 3. Resize
                val processWidth = 360
                val scaleFactor = processWidth.toFloat() / mirroredInput.width
                val processHeight = (mirroredInput.height * scaleFactor).toInt()
                
                val scaledInput = android.graphics.Bitmap.createScaledBitmap(mirroredInput, processWidth, processHeight, true)
                val scaledBackground = android.graphics.Bitmap.createScaledBitmap(background, processWidth, processHeight, true)
                
                // 4. Compose Output
                val output = android.graphics.Bitmap.createBitmap(processWidth, processHeight, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(output)
                
                // Draw Background
                canvas.drawBitmap(scaledBackground, 0f, 0f, null)
                
                if (mirroredMask != null) {
                    val scaledMaskForProcess = android.graphics.Bitmap.createScaledBitmap(mirroredMask, processWidth, processHeight, true)

                    // Create person layer
                    val personLayer = android.graphics.Bitmap.createBitmap(processWidth, processHeight, android.graphics.Bitmap.Config.ARGB_8888)
                    val personCanvas = android.graphics.Canvas(personLayer)

                    // 1. Draw the person (already color-corrected from convertMPImageToBitmap)
                    personCanvas.drawBitmap(scaledInput, 0f, 0f, null)

                    // 2. Apply mask to cut out background
                    val xferPaint = android.graphics.Paint()
                    xferPaint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
                    personCanvas.drawBitmap(scaledMaskForProcess, 0f, 0f, xferPaint)

                    // 3. Draw person layer onto background
                    canvas.drawBitmap(personLayer, 0f, 0f, null)
                }
                
                _segmentedBitmap.value = output
                
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isProcessingFrame = false
            }
        }
    }
    private fun convertMaskToBitmap(mask: com.google.mediapipe.framework.image.MPImage): android.graphics.Bitmap? {
        try {
            // Try standard extraction first
            return com.google.mediapipe.framework.image.BitmapExtractor.extract(mask)
        } catch (e: Exception) {
            // Fallback to ByteBuffer extraction
            try {
                val buffer = com.google.mediapipe.framework.image.ByteBufferExtractor.extract(mask)
                val width = mask.width
                val height = mask.height
                
                val pixelCount = width * height
                val bufferSize = buffer.capacity()
                
                // Use ARGB_8888 for better compatibility with Canvas drawing
                val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                val pixels = IntArray(pixelCount)
                
                if (bufferSize >= pixelCount * 4) {
                    // Float32 (4 bytes per pixel) - Confidence 0.0 to 1.0
                    buffer.rewind()
                    val floatBuffer = buffer.asFloatBuffer()
                    for (i in 0 until pixelCount) {
                        val confidence = floatBuffer.get(i)
                        val alpha = (confidence * 255f).toInt().coerceIn(0, 255)
                        // Create a White pixel with the calculated Alpha
                        // ARGB: Alpha, Red, Green, Blue
                        pixels[i] = (alpha shl 24) or 0x00FFFFFF
                    }
                } else if (bufferSize >= pixelCount) {
                    // UInt8 (1 byte per pixel)
                    buffer.rewind()
                    for (i in 0 until pixelCount) {
                        val confidenceByte = buffer.get(i).toInt() and 0xFF
                        // Assuming byte is 0-255 confidence
                        val alpha = confidenceByte
                        pixels[i] = (alpha shl 24) or 0x00FFFFFF
                    }
                } else {
                    return null
                }
                
                bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
                return bitmap
            } catch (e2: Exception) {
                e2.printStackTrace()
                return null
            }
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
