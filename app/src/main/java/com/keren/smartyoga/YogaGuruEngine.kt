package com.keren.smartyoga

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

class YogaGuruEngine(private val context: Context) {
    private var llmInference: LlmInference? = null
    
    // Path to the model file on the device
    private val modelPath = ModelDownloader.getModelFile(context).absolutePath

    private val _responseFlow = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val responseFlow: SharedFlow<String> = _responseFlow.asSharedFlow()

    fun initialize() {
        if (llmInference != null) return

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(1024)
            .setResultListener { partialResult, done ->
                _responseFlow.tryEmit(partialResult)
            }
            .build()

        try {
            llmInference = LlmInference.createFromOptions(context, options)
        } catch (e: Exception) {
            _responseFlow.tryEmit("Error: Model not found. Please download it first.")
            e.printStackTrace()
        }
    }

    fun generateResponse(prompt: String) {
        if (llmInference == null) {
            try {
                initialize()
            } catch (e: Exception) {
                _responseFlow.tryEmit("Error: Could not load Gemma model. Ensure it is at $modelPath")
                return
            }
        }

        val systemPrompt = "You are an expert Yoga Instructor named Guru. Keep answers concise, encouraging, and safe. Focus on alignment and breathing.\nUser: $prompt\nGuru:"
        
        try {
             llmInference?.generateResponseAsync(systemPrompt)
        } catch (e: Exception) {
            _responseFlow.tryEmit("Error generating response: ${e.message}")
        }
    }

    fun close() {
        llmInference = null
        // LlmInference doesn't always have a close method exposed in all versions, 
        // but if it does (via AutoCloseable), we should call it.
        // Assuming it might not, we just null it out. 
        // If it implements Closeable:
        // (llmInference as? java.io.Closeable)?.close()
    }
}
