package com.keren.smartyoga

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object ModelDownloader {
    // Valid Hugging Face URL for Gemma 2B INT4 model
    // Alternative: User can manually place the model in app's files directory
    private const val MODEL_URL = "https://huggingface.co/google/gemma-2b-it-GGUF/resolve/main/gemma-2b-it-q4_0.gguf"
    private const val MODEL_FILENAME = "gemma-2b-it-gpu-int4.bin"

    fun getModelFile(context: Context): File {
        return File(context.filesDir, MODEL_FILENAME)
    }

    fun isModelDownloaded(context: Context): Boolean {
        val file = getModelFile(context)
        return file.exists() && file.length() > 0
    }

    suspend fun downloadModel(context: Context, onProgress: (Float) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val file = getModelFile(context)

                // If file already exists and has reasonable size, consider it downloaded
                if (file.exists() && file.length() > 10_000_000) { // > 10MB
                    android.util.Log.d("ModelDownloader", "Model already exists: ${file.length()} bytes")
                    return@withContext true
                }

                android.util.Log.d("ModelDownloader", "Starting download from: $MODEL_URL")

                val url = URL(MODEL_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 30000 // 30 seconds
                connection.readTimeout = 60000 // 60 seconds per chunk
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "SmartYoga-Android")
                connection.setRequestProperty("Accept", "*/*")
                connection.connect()

                val responseCode = connection.responseCode
                android.util.Log.d("ModelDownloader", "Response code: $responseCode")

                if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_MOVED_TEMP && responseCode != HttpURLConnection.HTTP_MOVED_PERM) {
                    android.util.Log.e("ModelDownloader", "HTTP error: $responseCode")
                    connection.disconnect()
                    return@withContext false
                }

                val fileLength = connection.contentLength
                android.util.Log.d("ModelDownloader", "File size: $fileLength bytes (${fileLength / 1024 / 1024}MB)")

                val input = connection.inputStream
                val output = FileOutputStream(file)

                val data = ByteArray(16384) // 16KB buffer
                var total: Long = 0
                var count: Int
                var lastProgress = 0f

                while (input.read(data).also { count = it } != -1) {
                    total += count
                    output.write(data, 0, count)

                    if (fileLength > 0) {
                        val progress = total.toFloat() / fileLength
                        // Update progress every 5%
                        if (progress - lastProgress >= 0.05f) {
                            onProgress(progress)
                            lastProgress = progress
                            android.util.Log.d("ModelDownloader", "Progress: ${(progress * 100).toInt()}%")
                        }
                    }
                }

                output.flush()
                output.close()
                input.close()
                connection.disconnect()

                android.util.Log.d("ModelDownloader", "Downloaded ${file.length()} bytes successfully")
                return@withContext true
            } catch (e: java.net.SocketTimeoutException) {
                android.util.Log.e("ModelDownloader", "Timeout: ${e.message}", e)
                return@withContext false
            } catch (e: Exception) {
                android.util.Log.e("ModelDownloader", "Download failed: ${e.message}", e)
                e.printStackTrace()
                return@withContext false
            }
        }
    }
}
