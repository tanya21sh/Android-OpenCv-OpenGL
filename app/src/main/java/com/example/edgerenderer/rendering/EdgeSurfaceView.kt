package com.example.edgerenderer.rendering

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.edgerenderer.gl.EdgeRenderer
import java.nio.ByteBuffer

class EdgeSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private val renderer = EdgeRenderer()

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            onHostResume()
        }

        override fun onPause(owner: LifecycleOwner) {
            onHostPause()
        }
    }

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        holder.setFormat(android.graphics.PixelFormat.OPAQUE)
    }

    fun setLifecycleOwner(owner: LifecycleOwner) {
        owner.lifecycle.addObserver(lifecycleObserver)
    }

    fun updateFrame(buffer: ByteBuffer, width: Int, height: Int) {
        queueEvent {
            renderer.updateFrame(buffer, width, height)
        }
        requestRender()
    }

    fun attachToCamera(sensorRotation: Int) {
        queueEvent {
            renderer.setSensorRotation(sensorRotation)
        }
    }

    fun onHostResume() {
        super.onResume()
    }

    fun onHostPause() {
        super.onPause()
    }

    fun currentFrameSize(): String = renderer.currentFrameSize()
}
