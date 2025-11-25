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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
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
        
        poseDetector = PoseDetector(this) { result, width, height ->
            viewModel.onPoseDetected(result, width, height)
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
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
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
            CameraPreview(poseDetector = poseDetector)
            
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
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Session Complete! 🎉",
                        color = Color.White,
                        style = MaterialTheme.typography.displayMedium
                    )
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
    }
}
