package com.keren.smartyoga

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class YogaGLRenderer(private val context: Context) : GLSurfaceView.Renderer {

    private val TAG = "YogaGLRenderer"

    // Textures
    private var cameraTextureId: Int = 0
    private var backgroundTextureId: Int = 0
    private var maskTextureId: Int = 0

    var surfaceTexture: SurfaceTexture? = null
    var onSurfaceTextureAvailable: ((SurfaceTexture) -> Unit)? = null

    // Buffers
    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var textureBuffer: FloatBuffer

    // Program
    private var programId: Int = 0

    // Uniforms/Attributes
    private var positionHandle: Int = 0
    private var texCoordHandle: Int = 0
    private var cameraTextureHandle: Int = 0
    private var backgroundTextureHandle: Int = 0
    private var maskTextureHandle: Int = 0
    private var hasBackgroundHandle: Int = 0
    private var hasMaskHandle: Int = 0

    // State
    private var backgroundBitmap: Bitmap? = null
    private var newBackgroundBitmap: Bitmap? = null
    
    private var maskBuffer: ByteBuffer? = null
    private var maskWidth: Int = 0
    private var maskHeight: Int = 0
    private var newMaskBuffer: ByteBuffer? = null

    // Full screen quad
    private val vertexCoords = floatArrayOf(
        -1.0f, -1.0f, // Bottom Left
         1.0f, -1.0f, // Bottom Right
        -1.0f,  1.0f, // Top Left
         1.0f,  1.0f  // Top Right
    )

    // Texture coordinates (Standard)
    private val textureCoords = floatArrayOf(
        0.0f, 0.0f, // Bottom Left
        1.0f, 0.0f, // Bottom Right
        0.0f, 1.0f, // Top Left
        1.0f, 1.0f  // Top Right
    )

    // Shaders
    // Matrix
    private val transformMatrix = FloatArray(16)
    private var texMatrixHandle: Int = 0

    // Shaders
    private val vertexShaderCode = """
        attribute vec4 aPosition;
        attribute vec4 aTexCoord;
        uniform mat4 uTexMatrix;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = (uTexMatrix * aTexCoord).xy;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        
        varying vec2 vTexCoord;
        
        uniform samplerExternalOES uCameraTexture;
        uniform sampler2D uBackgroundTexture;
        uniform sampler2D uMaskTexture;
        
        uniform int uHasBackground;
        uniform int uHasMask;
        
        void main() {
            vec4 cameraColor = texture2D(uCameraTexture, vTexCoord);
            
            if (uHasBackground == 1) {
                // Background texture coordinates might need separate handling if we want it to stay upright
                // regardless of camera rotation, but usually background is already oriented.
                // However, vTexCoord is now transformed for the camera.
                // For simplicity, let's use the same coords for now, or we might need a separate vBgTexCoord.
                // But wait, if we rotate the camera texture, we are rotating the lookup.
                // If we use the same coords for background, the background will also be rotated/flipped?
                // Actually, the background is a static image. We probably want standard 0-1 coords for it.
                // Let's pass untransformed coords for background.
                
                vec4 bgColor = texture2D(uBackgroundTexture, vTexCoord); // This might be wrong if vTexCoord is rotated
                
                float maskVal = 0.0;
                if (uHasMask == 1) {
                     // Mask matches camera, so it should use vTexCoord? 
                     // The mask comes from MediaPipe which processes the rotated image.
                     // If MediaPipe output is already upright relative to the image we sent it...
                     // We sent MediaPipe the bitmap from ImageAnalysis.
                     // ImageAnalysis gives us a bitmap.
                     // If we use vTexCoord (which transforms texture coordinates to match the display),
                     // we should be fine if the mask aligns with the camera texture.
                    maskVal = texture2D(uMaskTexture, vTexCoord).a; 
                }
                
                gl_FragColor = mix(bgColor, cameraColor, maskVal);
            } else {
                gl_FragColor = cameraColor;
            }
        }
    """.trimIndent()
    
    // We need separate coords for background if we don't want it rotated by the camera matrix.
    // But for now, let's just fix the camera.
    // Actually, to do this properly, we should pass `aTexCoord` as is to `vBgTexCoord` and `(uTexMatrix * aTexCoord)` to `vCamTexCoord`.
    
    private val vertexShaderCodeImproved = """
        attribute vec4 aPosition;
        attribute vec4 aTexCoord;
        uniform mat4 uTexMatrix;
        varying vec2 vCamTexCoord;
        varying vec2 vBgTexCoord;
        varying vec2 vMaskTexCoord;
        void main() {
            gl_Position = aPosition;
            vCamTexCoord = (uTexMatrix * aTexCoord).xy;
            // Flip background texture vertically to fix upside-down issue
            vBgTexCoord = vec2(aTexCoord.x, 1.0 - aTexCoord.y);
            // Mask coordinates need to match the transformed camera coordinates
            vMaskTexCoord = (uTexMatrix * aTexCoord).xy;
        }
    """.trimIndent()

    private val fragmentShaderCodeImproved = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;

        varying vec2 vCamTexCoord;
        varying vec2 vBgTexCoord;
        varying vec2 vMaskTexCoord;

        uniform samplerExternalOES uCameraTexture;
        uniform sampler2D uBackgroundTexture;
        uniform sampler2D uMaskTexture;

        uniform int uHasBackground;
        uniform int uHasMask;

        void main() {
            vec4 cameraColor = texture2D(uCameraTexture, vCamTexCoord);

            if (uHasBackground == 1) {
                vec4 bgColor = texture2D(uBackgroundTexture, vBgTexCoord);

                float maskVal = 1.0;
                if (uHasMask == 1) {
                    // Mask should align with camera using transformed coordinates
                    // MediaPipe mask: 1.0 = person, 0.0 = background
                    // For mix(bg, camera, mask): we want person to show camera, bg to show background
                    // So we use the mask value directly (person=1.0 shows camera, bg=0.0 shows background)
                    maskVal = texture2D(uMaskTexture, vMaskTexCoord).a;
                }

                // mix(a, b, t) = a * (1-t) + b * t
                // When maskVal = 1.0 (person): shows cameraColor
                // When maskVal = 0.0 (background): shows bgColor
                gl_FragColor = mix(bgColor, cameraColor, maskVal);
            } else {
                gl_FragColor = cameraColor;
            }
        }
    """.trimIndent()

    init {
        vertexBuffer = ByteBuffer.allocateDirect(vertexCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertexCoords)
        vertexBuffer.position(0)

        textureBuffer = ByteBuffer.allocateDirect(textureCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(textureCoords)
        textureBuffer.position(0)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        programId = ShaderUtils.createProgram(vertexShaderCodeImproved, fragmentShaderCodeImproved)
        if (programId == 0) return

        positionHandle = GLES20.glGetAttribLocation(programId, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(programId, "aTexCoord")
        
        texMatrixHandle = GLES20.glGetUniformLocation(programId, "uTexMatrix")
        
        cameraTextureHandle = GLES20.glGetUniformLocation(programId, "uCameraTexture")
        backgroundTextureHandle = GLES20.glGetUniformLocation(programId, "uBackgroundTexture")
        maskTextureHandle = GLES20.glGetUniformLocation(programId, "uMaskTexture")
        
        hasBackgroundHandle = GLES20.glGetUniformLocation(programId, "uHasBackground")
        hasMaskHandle = GLES20.glGetUniformLocation(programId, "uHasMask")

        // Create Textures
        val textures = IntArray(3)
        GLES20.glGenTextures(3, textures, 0)
        cameraTextureId = textures[0]
        backgroundTextureId = textures[1]
        maskTextureId = textures[2]

        // Setup Camera Texture (OES)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        surfaceTexture = SurfaceTexture(cameraTextureId)
        onSurfaceTextureAvailable?.invoke(surfaceTexture!!)

        // Setup Background Texture
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTextureId)
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        
        // Setup Mask Texture
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTextureId)
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        surfaceTexture?.updateTexImage()
        surfaceTexture?.getTransformMatrix(transformMatrix)

        GLES20.glUseProgram(programId)

        // Upload Background if changed
        synchronized(this) {
            newBackgroundBitmap?.let {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTextureId)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, it, 0)
                backgroundBitmap = it
                newBackgroundBitmap = null
            }
        }

        // Upload Mask if changed
        synchronized(this) {
            newMaskBuffer?.let {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTextureId)
                // Assuming Alpha8 for mask
                GLES20.glTexImage2D(
                    GLES20.GL_TEXTURE_2D, 0, GLES20.GL_ALPHA, 
                    maskWidth, maskHeight, 0, 
                    GLES20.GL_ALPHA, GLES20.GL_UNSIGNED_BYTE, it
                )
                maskBuffer = it
                newMaskBuffer = null
            }
        }
        
        // Set Matrix
        GLES20.glUniformMatrix4fv(texMatrixHandle, 1, false, transformMatrix, 0)

        // Bind Camera Texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES20.glUniform1i(cameraTextureHandle, 0)

        // Bind Background Texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTextureId)
        GLES20.glUniform1i(backgroundTextureHandle, 1)
        
        // Bind Mask Texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskTextureId)
        GLES20.glUniform1i(maskTextureHandle, 2)

        // Set Flags
        // Only use background replacement if we have both background AND mask
        val hasValidBackground = backgroundBitmap != null && maskBuffer != null
        GLES20.glUniform1i(hasBackgroundHandle, if (hasValidBackground) 1 else 0)
        GLES20.glUniform1i(hasMaskHandle, if (maskBuffer != null) 1 else 0)

        // Debug logging (remove after testing)
        if (backgroundBitmap != null && maskBuffer == null) {
            Log.d(TAG, "Background set but no mask yet")
        } else if (hasValidBackground) {
            Log.d(TAG, "Background replacement active: mask ${maskWidth}x${maskHeight}")
        }

        // Draw Quad
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    fun setBackground(bitmap: Bitmap?) {
        synchronized(this) {
            newBackgroundBitmap = bitmap
            if (bitmap == null) {
                backgroundBitmap = null
            }
        }
    }

    fun setMask(buffer: ByteBuffer, width: Int, height: Int) {
        synchronized(this) {
            newMaskBuffer = buffer
            maskWidth = width
            maskHeight = height
        }
    }
}
