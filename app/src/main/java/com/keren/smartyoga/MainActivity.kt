package com.keren.smartyoga

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val viewModel: PoseViewModel by viewModels()
    private lateinit var poseDetector: PoseDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        poseDetector = PoseDetector(this) { result, width, height, inputImage, originalBitmap ->
            viewModel.onPoseDetected(result, width, height, inputImage, originalBitmap)
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SmartYogaApp(viewModel, poseDetector)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        poseDetector.close()
    }
}

@Composable
fun SmartYogaApp(viewModel: PoseViewModel, poseDetector: PoseDetector) {
    val context = LocalContext.current
    
    // Initialize Tracker
    LaunchedEffect(Unit) {
        viewModel.sessionTracker = SessionTracker(context)
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    // Music State
    var isMusicPlaying by remember { mutableStateOf(false) }
    val mediaPlayer = remember { 
        try {
            android.media.MediaPlayer.create(context, R.raw.calming_music).apply { isLooping = true }
        } catch (e: Exception) {
            null
        }
    }
    
    // Handle Lifecycle for Music
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer.pause()
                }
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (isMusicPlaying && mediaPlayer?.isPlaying == false) {
                    mediaPlayer.start()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mediaPlayer?.release()
        }
    }
    
    // Background State
    val backgroundBitmap by viewModel.backgroundBitmap.collectAsState()
    val segmentedBitmap by viewModel.segmentedBitmap.collectAsState()
    
    // Pickers
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val source = android.graphics.ImageDecoder.createSource(context.contentResolver, it)
            val bitmap = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true 
            }
            viewModel.setBackground(bitmap)
        }
    }
    
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let { viewModel.setBackground(it) }
    }
    


    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
            if (!granted) {
                Toast.makeText(context, "Camera permission required", Toast.LENGTH_LONG).show()
            }
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize()) {
            
            // Always show CameraPreview to keep the camera lifecycle active
            CameraPreview(poseDetector = poseDetector)
            
            if (segmentedBitmap != null) {
                // Show Segmented View (Background + Person) overlaying the camera
                Image(
                    bitmap = segmentedBitmap!!.asImageBitmap(),
                    contentDescription = "Segmented View",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // If background is selected but segmentation isn't ready yet (or failed), we might see camera.
            // But we want to force background mode if background is set.
            // Actually, if backgroundBitmap is set, we expect segmentedBitmap to update.
            // Let's add a loading indicator or just keep camera until then.
            
            val poseResult by viewModel.poseResult.collectAsState()
            val rawPoseResult by viewModel.rawPoseResult.collectAsState()
            val inputDims by viewModel.inputDims.collectAsState()
            
            PoseOverlay(
                result = poseResult,
                poseLandmarkerResult = rawPoseResult,
                inputDims = inputDims
            )
            
            // Session UI
            val currentPose by viewModel.currentPose.collectAsState()
            val timerValue by viewModel.timerValue.collectAsState()
            val isSessionComplete by viewModel.isSessionComplete.collectAsState()
            
            if (isSessionComplete) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Session Complete! 🎉",
                            color = Color.White,
                            style = MaterialTheme.typography.displayMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.restartSession() }) {
                            Text("Start Again")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Total Sessions: ${viewModel.sessionTracker?.getTotalSessions() ?: 0}",
                            color = Color.Gray
                        )
                    }
                }
            } else {
                // Top Bar: Pose Name and Reference Image
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Current Pose:",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = currentPose.name.replace("_", " "),
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                    
                    // Reference Image
                    val imageRes = when(currentPose) {
                        TargetPose.WARRIOR_II -> R.drawable.warrior2_pose
                        TargetPose.TREE_POSE -> R.drawable.tree_pose
                        TargetPose.WARRIOR_I -> R.drawable.warrior1_pose
                        else -> R.drawable.warrior2_pose // Fallback
                    }
                    
                    Image(
                        painter = painterResource(id = imageRes),
                        contentDescription = "Reference Pose",
                        modifier = Modifier
                            .size(100.dp)
                            .background(Color.White.copy(alpha = 0.8f), shape = MaterialTheme.shapes.medium)
                            .padding(4.dp)
                    )
                }
                
                // Controls (Music, Background)
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.medium)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                   Column {
                       IconButton(onClick = { 
                           isMusicPlaying = !isMusicPlaying
                           if (isMusicPlaying) mediaPlayer?.start() else mediaPlayer?.pause()
                       }) {
                           Icon(
                               imageVector = if (isMusicPlaying) Icons.Filled.MusicNote else Icons.Filled.MusicOff,
                               contentDescription = "Music",
                               tint = Color.White
                           )
                       }
                       
                       // Background Options
                       var showBgMenu by remember { mutableStateOf(false) }
                       IconButton(onClick = { showBgMenu = !showBgMenu }) {
                           Icon(
                               imageVector = Icons.Filled.Image,
                               contentDescription = "Background",
                               tint = if (backgroundBitmap != null) Color.Green else Color.White
                           )
                       }
                       
                       if (showBgMenu) {
                           Column(
                               modifier = Modifier
                                   .background(Color.Black.copy(alpha = 0.8f), shape = MaterialTheme.shapes.small)
                                   .padding(8.dp)
                           ) {
                               Button(onClick = { 
                                   showBgMenu = false
                                   // Load Tropic Background from Resources
                                   val bitmap = android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.tropic_bg)
                                   viewModel.setBackground(bitmap)
                               }) { Text("Tropic") }
                               
                               Button(onClick = { 
                                   galleryLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                   showBgMenu = false
                               }) { Text("Gallery") }
                               
                               Button(onClick = { 
                                   cameraLauncher.launch(null)
                                   showBgMenu = false
                               }) { Text("Camera") }
                               
                               Button(onClick = { 
                                   viewModel.setBackground(null)
                                   showBgMenu = false
                               }) { Text("Off") }
                           }
                       }
                   }
                }
                
                // Bottom Bar: Feedback and Timer
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (timerValue > 0) {
                        Text(
                            text = "Hold: ${5 - timerValue}s",
                            color = Color.Green,
                            style = MaterialTheme.typography.displayLarge
                        )
                    }
                    
                    Text(
                        text = poseResult?.feedback ?: "Waiting...",
                        color = if (poseResult?.isCorrect == true) Color.Green else Color.Red,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Please grant camera permission")
        }
    }
}

