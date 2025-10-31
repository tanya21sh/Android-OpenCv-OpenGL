package com.example.edgerenderer.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.graphics.SurfaceTexture
import android.view.WindowManager
import androidx.core.content.getSystemService
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class CameraController(
    private val context: Context,
    private val listener: FrameListener
) {

    interface FrameListener {
        fun onFrame(frame: FrameData)
    }

    data class FrameData(
        val nv21Data: ByteArray,
        val width: Int,
        val height: Int,
        val timestampNs: Long
    )

    private val cameraManager = context.getSystemService<CameraManager>()
        ?: error("CameraManager unavailable")

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private var previewOutputSurface: Surface? = null

    private val isStreaming = AtomicBoolean(false)

    private lateinit var previewSize: Size
    private var cameraId: String = ""

    var sensorRotation: Int = 0
        private set

    fun startCamera(previewTexture: SurfaceTexture) {
        if (isStreaming.get()) return
        val camera = selectCameraId() ?: run {
            Log.e(TAG, "No suitable camera found")
            return
        }
        cameraId = camera.first
        previewSize = camera.second
        sensorRotation = computeRelativeRotation(cameraId)
    previewTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
    previewOutputSurface = Surface(previewTexture)
        startBackgroundThread()
        setupImageReader()
        openCamera()
    }

    fun stopCamera() {
        isStreaming.set(false)
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
    imageReader?.close()
    imageReader = null
    previewOutputSurface?.release()
    previewOutputSurface = null
        stopBackgroundThread()
    }

    fun close() {
        stopCamera()
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {
        val handler = backgroundHandler ?: return
        val preview = previewOutputSurface ?: return
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                cameraDevice = device
                createSession(device)
            }

            override fun onDisconnected(device: CameraDevice) {
                isStreaming.set(false)
                device.close()
            }

            override fun onError(device: CameraDevice, error: Int) {
                Log.e(TAG, "Camera error: $error")
                isStreaming.set(false)
                device.close()
            }
        }, handler)
    }

    private fun createSession(device: CameraDevice) {
        val reader = imageReader ?: return
    val preview = previewOutputSurface ?: return
        val surfaces = listOf(preview, reader.surface)
        device.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                captureSession = session
                val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(preview)
                    addTarget(reader.surface)
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                }
                session.setRepeatingRequest(request.build(), null, backgroundHandler)
                isStreaming.set(true)
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                isStreaming.set(false)
                Log.e(TAG, "Capture session configuration failed")
            }
        }, backgroundHandler)
    }

    private fun setupImageReader() {
        val handler = backgroundHandler ?: return
        val size = previewSize
        imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, IMAGE_BUFFER_SIZE).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                image.use {
                    val nv21 = YuvUtils.toNV21(it)
                    listener.onFrame(
                        FrameData(
                            nv21Data = nv21,
                            width = it.width,
                            height = it.height,
                            timestampNs = it.timestamp
                        )
                    )
                }
            }, handler)
        }
    }

    private fun selectCameraId(): Pair<String, Size>? {
        val ids = cameraManager.cameraIdList
        var selected: Pair<String, Size>? = null
        for (id in ids) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (lensFacing != null && lensFacing != CameraCharacteristics.LENS_FACING_BACK) continue
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue
            val sizes = map.getOutputSizes(ImageFormat.YUV_420_888) ?: continue
            val target = chooseOptimalSize(sizes)
            selected = id to target
            break
        }
        return selected
    }

    private fun chooseOptimalSize(choices: Array<Size>): Size {
        val targetArea = 1280 * 720
        var bestChoice = choices.first()
        var minDiff = Int.MAX_VALUE
        for (option in choices) {
            val area = option.width * option.height
            val diff = abs(area - targetArea)
            if (diff < minDiff) {
                bestChoice = option
                minDiff = diff
            }
        }
        return bestChoice
    }

    private fun computeRelativeRotation(id: String): Int {
        val characteristics = cameraManager.getCameraCharacteristics(id)
        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val displayRotationDegrees = getDisplayRotationDegrees()
        val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
        val sign = if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) 1 else -1
        return (sensorOrientation - sign * displayRotationDegrees + 360) % 360
    }

    private fun getDisplayRotationDegrees(): Int {
        val wm = context.getSystemService(WindowManager::class.java)
        val rotation = wm?.defaultDisplay?.rotation ?: run {
            val dm = context.getSystemService(DisplayManager::class.java)
            dm?.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.rotation
        } ?: Surface.ROTATION_0
        return when (rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    private fun startBackgroundThread() {
        if (backgroundThread != null) return
        backgroundThread = HandlerThread("CameraBackground").also { thread ->
            thread.start()
            backgroundHandler = Handler(thread.looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread?.join()
        backgroundThread = null
        backgroundHandler = null
    }

    companion object {
        private const val IMAGE_BUFFER_SIZE = 3
        private const val TAG = "CameraController"
    }
}

private inline fun <T : AutoCloseable, R> T.use(block: (T) -> R): R {
    return try {
        block(this)
    } finally {
        close()
    }
}
