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

#### [MODIFY] [PoseAnalyzer.kt]
- Refactor to support multiple poses (Warrior II, Tree Pose, Warrior I).
- Return a structured `PoseResult` object instead of a string string, containing:
    - Detected Pose Name
    - Correctness (Boolean)
    - Feedback Message
    - List of "Correct" limbs (for green highlighting) and "Incorrect" limbs (for red highlighting).

#### [MODIFY] [PoseViewModel.kt]
- Implement **Session Mode**:
    - List of poses to cycle through (Warrior II -> Tree -> Warrior I).
    - State Machine: `ShowInstruction` -> `Detecting` -> `Holding(timer)` -> `Success` -> `NextPose`.
    - `currentPoseImage`: Expose the reference image for the current pose.
    - `timer`: Count down from 5 seconds when pose is correct.

#### [MODIFY] [MainActivity.kt]
- **Reference Image Overlay**: Show the target pose image in the corner or semi-transparent over the screen.
- **Session UI**: "Pose 1/3", "Hold for 5s", "Next Pose in 3s".

### Assets
#### [NEW] [Reference Images]
- Generate images for:
    - `warrior2.png`
    - `tree_pose.png`
    - `warrior1.png`
- Place in `app/src/main/res/drawable`.

## Verification Plan
### Manual Verification
- Run app on physical device (emulator camera might be limited).
- Verify camera permission prompt.
- Verify skeleton overlay appears on body.
- Test specific poses (e.g., "Warrior II") and check feedback.
