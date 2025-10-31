# Android + OpenCV + OpenGL Edge Renderer

Minimal Android R&D project that streams camera frames, offloads edge detection to native C++ (OpenCV), and renders the processed texture with OpenGL ES 2.0. The repo also ships a TypeScript-based viewer for sharing or debugging processed frames on the web.

## Implemented Features
- Real-time camera preview using `TextureView` (Camera2 API, 1280×720 target).
- JNI bridge to an OpenCV-backed C++ pipeline (NV21 → RGBA conversion + optional Canny edges).
- OpenGL ES renderer (`GLSurfaceView`) that uploads RGBA textures and displays them with a lightweight shader.
- UI toggle between raw and edge-processed output, with FPS and resolution overlay.
- Frame coalescing on the Kotlin side to drop stale frames and keep latency low.
- TypeScript debug viewer that displays a sample processed frame with live stats controls.

## Project Layout
```
├── app/                # Android application (Kotlin UI + JNI bridge)
│   └── src/main/cpp/   # CMake project for edgeproc shared library
├── gl/                 # Reusable OpenGL renderer module
├── jni/                # C++ EdgeProcessor (OpenCV) sources/headers
└── web/                # TypeScript viewer (tsc build)
```

## Android Build & Run
1. Install tooling: Android Studio (or command-line SDK), NDK r26+, CMake 3.22+, and OpenCV-for-Android (>=4.8).
2. Export the OpenCV package path so CMake can find it (adjust to your filesystem):
	```bash
	export OpenCV_DIR="/path/to/OpenCV-android-sdk/sdk/native/jni"
	```
3. Build and install with Gradle:
	```bash
	./gradlew assembleDebug
	./gradlew installDebug
	```
4. Grant camera permission on first launch. Use the toggle to switch between raw preview and edge rendering while monitoring FPS.

### Native pipeline
- Kotlin hands NV21 buffers to `NativeBridge`, which forwards to the `edgeproc` shared library.
- `EdgeProcessor` (C++) converts NV21 → RGBA via OpenCV and optionally runs Canny edge detection (5×5 Gaussian pre-filter).
- Processed RGBA frames are returned to Kotlin, uploaded to an OpenGL texture, and drawn by `EdgeRenderer`.

### Rendering stack
- `gl` Android library module encapsulates shader compilation (`ShaderProgram`) and texture rendering (`EdgeRenderer`).
- `EdgeSurfaceView` wraps `GLSurfaceView`, handles lifecycle, and forwards frames/rotation to the renderer.

## Web Debug Viewer
```
cd web
npm install
npm run build
```
Open `web/public/index.html` in a browser; it loads the compiled `dist/main.js`, renders a sample processed frame (embedded PNG), and exposes a mode selector with FPS/resolution text overlays.

## Notes & Next Steps
- The pipeline targets practicality over polish; additional shaders (grayscale/invert) or GPU-side filters can be dropped into `gl` without touching the app module.
- Consider wiring a real transport (WebSocket/HTTP) to stream live frames to the TypeScript viewer.
- For production, cache `ByteBuffer`s and reuse OpenGL textures to minimize allocations; current implementation favors clarity.