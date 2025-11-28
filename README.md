# SmartYoga 🧘‍♀️🤖
**AI-Powered Personal Yoga Instructor**

SmartYoga is a cutting-edge Android application that combines computer vision, real-time graphics, and generative AI to provide a personalized and immersive yoga experience. It goes beyond simple video playback by actively watching your form, analyzing your stability, and answering your questions using an on-device LLM.

## 🚀 Key Features

### 1. High-Performance Pose Detection
*   **GPU Acceleration**: Utilizes the GPU Delegate for MediaPipe Pose Landmarker to ensure smooth, real-time inference.
*   **Performance Monitoring**: Built-in overlay displays real-time Inference Time, FPS, and Render Time.

### 2. Smart Logic & Statistics
*   **Stability Analysis**: continuously monitors the standard deviation of your key body landmarks to detect shaking or instability, providing feedback like "Stabilize your body".
*   **Personal Calibration**: Starts every session with a "Calibration" phase (T-Pose) to learn your body's unique proportions and range of motion, dynamically adjusting pose tolerances.

### 3. Immersive Graphics Pipeline
*   **OpenGL ES Rendering**: Features a custom `YogaGLRenderer` that handles the camera stream and compositing on the GPU.
*   **Shader-Based Effects**: Uses GLSL shaders to perform real-time background replacement and masking, transporting you to a "Tropic" beach or other environments without green screens.

### 4. Hybrid AI (Yoga Guru)
*   **GenAI Integration**: Embeds Google's **Gemma 2B** Large Language Model (LLM) directly on the device via MediaPipe LLM Inference.
*   **Interactive Chat**: Switch to "Guru Mode" to ask questions like "How do I improve my balance?" or "What muscles does Warrior II work?".
*   **Smart Resource Management**: Automatically manages system resources by pausing the vision pipeline when chatting to prevent overheating and battery drain.

## 🛠 Technical Architecture

*   **Language**: Kotlin
*   **UI Framework**: Jetpack Compose
*   **Computer Vision**: MediaPipe Pose Landmarker (GPU)
*   **Graphics**: OpenGL ES 2.0, GLSL Shaders
*   **Generative AI**: MediaPipe LLM Inference (Gemma 2B INT4)
*   **Camera**: CameraX

### Data Flow
1.  **CameraX** captures frames.
2.  **OpenGL ES** renders the camera feed to a texture.
3.  **MediaPipe** analyzes the frame for Pose Landmarks and Segmentation Masks.
4.  **PoseAnalyzer** computes angles and stability statistics.
5.  **YogaGLRenderer** composites the user (masked) onto a virtual background using Shaders.
6.  **YogaGuruEngine** (when active) loads the LLM for text generation.

## 📦 Setup & Installation

1.  **Prerequisites**:
    *   Android Device with GPU support (Android 10+ recommended).
    *   Android Studio Ladybug or newer.

2.  **Model Setup**:
    *   **Pose Detection**: The `pose_landmarker_lite.task` is included in `assets/`.
    *   **GenAI (Gemma)**: You must push the Gemma 2B model to the device manually due to its size.
        ```bash
        adb shell mkdir -p /data/local/tmp/llm/
        adb push gemma-2b-it-gpu-int4.bin /data/local/tmp/llm/
        ```

3.  **Build & Run**:
    *   Open the project in Android Studio.
    *   Sync Gradle.
    *   Run on your physical Android device.

## 📱 Usage Guide

1.  **Calibration**: Stand in a "T-Pose" to calibrate the app to your body.
2.  **Session**: Follow the on-screen poses (Warrior II, Tree Pose, etc.). The app will time you when your form is correct.
3.  **Backgrounds**: Tap the "Image" icon to switch virtual backgrounds.
4.  **Ask Guru**: Tap the "Face" icon to pause the workout and chat with the AI Yoga Guru.
5.  **Debug**: Tap the "Info" icon to see performance stats.

---
*Built with ❤️ by the SmartYoga Team*
