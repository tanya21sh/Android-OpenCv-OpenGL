package com.example.edgerenderer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.view.TextureView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.edgerenderer.camera.CameraController
import com.example.edgerenderer.databinding.ActivityMainBinding
import com.example.edgerenderer.nativebridge.NativeBridge
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class MainActivity : AppCompatActivity(), TextureView.SurfaceTextureListener,
    CameraController.FrameListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraController: CameraController

    private val processingExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var showEdges = true
    private val latestFrame = AtomicReference<CameraController.FrameData?>(null)
    private val isProcessing = AtomicBoolean(false)

    private var frameCounter = 0
    private var fpsWindowStart = 0L

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCameraIfReady()
            } else {
                Toast.makeText(this, R.string.permission_rationale, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraController = CameraController(this, this)
        binding.previewTexture.surfaceTextureListener = this
        binding.edgeSurface.setLifecycleOwner(this)

        binding.toggleButton.text = getString(R.string.toggle_edges_off)
        binding.frameStats.text = getString(R.string.frame_stats_template, 0.0f, "0x0")

        binding.toggleButton.setOnClickListener {
            showEdges = !showEdges
            binding.toggleButton.text = if (showEdges) {
                getString(R.string.toggle_edges_off)
            } else {
                getString(R.string.toggle_edges_on)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        binding.edgeSurface.onHostResume()
        if (hasCameraPermission()) {
            startCameraIfReady()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onPause() {
        latestFrame.set(null)
        frameCounter = 0
        fpsWindowStart = 0L
        cameraController.stopCamera()
        binding.edgeSurface.onHostPause()
        super.onPause()
    }

    override fun onDestroy() {
        processingExecutor.shutdown()
        cameraController.close()
        super.onDestroy()
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        startCameraIfReady()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        cameraController.stopCamera()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    override fun onFrame(frame: CameraController.FrameData) {
        if (processingExecutor.isShutdown) return
        latestFrame.set(frame)
        if (isProcessing.compareAndSet(false, true)) {
            processingExecutor.execute { drainFrames() }
        }
    }

    private fun drainFrames() {
        while (true) {
            val frame = latestFrame.getAndSet(null) ?: break
            processFrame(frame)
        }
        isProcessing.set(false)
        if (latestFrame.get() != null && isProcessing.compareAndSet(false, true)) {
            drainFrames()
        }
    }

    private fun processFrame(frame: CameraController.FrameData) {
        val processed = NativeBridge.processFrame(
            frame.nv21Data,
            frame.width,
            frame.height,
            if (showEdges) NativeBridge.ProcessingMode.CANNY else NativeBridge.ProcessingMode.RAW
        )
        val buffer = ByteBuffer.allocateDirect(processed.size)
            .order(ByteOrder.nativeOrder())
            .apply {
                put(processed)
                rewind()
            }
        binding.edgeSurface.updateFrame(buffer, frame.width, frame.height)
        updateFps(frame.timestampNs)
    }

    private fun updateFps(timestampNs: Long) {
        frameCounter++
        if (fpsWindowStart == 0L) {
            fpsWindowStart = timestampNs
        }
        val elapsed = timestampNs - fpsWindowStart
        if (elapsed >= TimeUnit.SECONDS.toNanos(1)) {
            val fps = frameCounter * 1_000_000_000L / elapsed.toFloat()
            runOnUiThread {
                binding.frameStats.text = getString(
                    R.string.frame_stats_template,
                    fps,
                    binding.edgeSurface.currentFrameSize()
                )
            }
            fpsWindowStart = timestampNs
            frameCounter = 0
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    private fun startCameraIfReady() {
        val texture = binding.previewTexture
        if (!texture.isAvailable || !hasCameraPermission()) return
        val surfaceTexture = texture.surfaceTexture ?: return
        cameraController.startCamera(surfaceTexture)
        binding.edgeSurface.attachToCamera(cameraController.sensorRotation)
    }
}
