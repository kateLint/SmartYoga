package com.keren.smartyoga

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import android.opengl.GLSurfaceView

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
    poseDetector: PoseDetector,
    renderer: YogaGLRenderer,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    val glSurfaceView = remember { 
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        } 
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                val cameraProvider = ProcessCameraProvider.getInstance(context).get()
                cameraProvider.unbindAll()
            } catch (e: Exception) {
                // Provider might not be ready
            }
            cameraExecutor.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            // Create a SurfaceProvider that uses the SurfaceTexture from the Renderer
            val surfaceProvider = Preview.SurfaceProvider { request ->
                val surfaceTexture = renderer.surfaceTexture
                if (surfaceTexture != null) {
                    surfaceTexture.setDefaultBufferSize(request.resolution.width, request.resolution.height)
                    val surface = android.view.Surface(surfaceTexture)
                    // Use MainExecutor to avoid RejectedExecutionException if cameraExecutor is shut down
                    request.provideSurface(surface, ContextCompat.getMainExecutor(context)) {
                        surface.release()
                        // Don't release SurfaceTexture here as it's owned by Renderer
                    }
                } else {
                    // Wait for SurfaceTexture
                    renderer.onSurfaceTextureAvailable = { st ->
                        st.setDefaultBufferSize(request.resolution.width, request.resolution.height)
                        val surface = android.view.Surface(st)
                        request.provideSurface(surface, ContextCompat.getMainExecutor(context)) {
                            surface.release()
                        }
                    }
                }
            }

            val display = try {
                context.display
            } catch (e: NoSuchMethodError) {
                // Fallback for older APIs if needed, or use WindowManager
                (context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
            }
            val rotation = display?.rotation ?: android.view.Surface.ROTATION_0

            val preview = Preview.Builder()
                .setTargetRotation(rotation)
                .build()
                .also {
                it.setSurfaceProvider(surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetRotation(rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        val bitmap = imageProxy.toBitmap()
                        val imgRotation = imageProxy.imageInfo.rotationDegrees
                        poseDetector.detect(bitmap, imgRotation)
                        imageProxy.close()
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("CameraPreview", "Use case binding failed", exc)
                android.widget.Toast.makeText(context, "Camera binding failed: ${exc.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    AndroidView(
        factory = { glSurfaceView },
        modifier = modifier.fillMaxSize()
    )
}
