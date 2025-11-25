# Implementation Plan - Smart Yoga Pose Corrector

## Goal Description
Build a real-time yoga pose correction app using MediaPipe on Android. The app will analyze the user's pose via the camera and provide real-time feedback (e.g., "Straighten your back").

## User Review Required
> [!IMPORTANT]
> This requires a **NEW Android Project**. Please create a new project named "SmartYoga" with "Empty Activity" in Android Studio.

## Proposed Changes

### Dependencies
#### [NEW] [build.gradle.kts (Module: app)]
- `androidx.camera:camera-core`
- `androidx.camera:camera-camera2`
- `androidx.camera:camera-lifecycle`
- `androidx.camera:camera-view`
- `com.google.mediapipe:tasks-vision`

### Architecture
- **MVVM**: `PoseViewModel` to handle MediaPipe results.
- **CameraX**: For efficient camera preview and frame analysis.
- **MediaPipe**: `PoseLandmarker` for detecting 33 body landmarks.

### Key Components
#### [NEW] [CameraPreview.kt]
- Composable to display the camera feed.

#### [NEW] [PoseDetector.kt]
- Wrapper around MediaPipe `PoseLandmarker`.
- Processes frames and returns `PoseLandmarkerResult`.

#### [NEW] [PoseOverlay.kt]
- Canvas drawing to show skeleton overlay on top of camera feed.

#### [NEW] [PoseAnalyzer.kt]
- Logic to calculate angles (e.g., elbow angle, knee angle) and determine if a pose is correct.

## Verification Plan
### Manual Verification
- Run app on physical device (emulator camera might be limited).
- Verify camera permission prompt.
- Verify skeleton overlay appears on body.
- Test specific poses (e.g., "Warrior II") and check feedback.
