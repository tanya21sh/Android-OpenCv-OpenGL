package com.example.edgerenderer.gl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class EdgeRenderer : GLSurfaceView.Renderer {

    private val vertexBuffer: FloatBuffer = createFloatBuffer(VERTICES)
    private val texCoordBuffer: FloatBuffer = createFloatBuffer(TEX_COORDS)
    private val textureMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }

    private val frameLock = ReentrantLock()
    private var pendingFrame: ByteBuffer? = null
    private var frameWidth = 0
    private var frameHeight = 0
    private var updateFrame = false

    private var program = 0
    private var textureId = 0

    override fun onSurfaceCreated(gl: javax.microedition.khronos.opengles.GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        program = ShaderProgram.build(VERTEX_SHADER, FRAGMENT_SHADER)
        textureId = createTexture()
    }

    override fun onSurfaceChanged(gl: javax.microedition.khronos.opengles.GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: javax.microedition.khronos.opengles.GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        var width = 0
        var height = 0
        var uploadBuffer: ByteBuffer? = null
        var shouldUpload = false

        frameLock.withLock {
            if (pendingFrame != null) {
                width = frameWidth
                height = frameHeight
                if (updateFrame) {
                    pendingFrame!!.rewind()
                    uploadBuffer = pendingFrame
                    shouldUpload = true
                    updateFrame = false
                }
            }
        }

        if (width == 0 || height == 0) {
            return
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        if (shouldUpload && uploadBuffer != null) {
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                width,
                height,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                uploadBuffer
            )
        }

        GLES20.glUseProgram(program)
        val positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        val texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        val textureMatrixHandle = GLES20.glGetUniformLocation(program, "uTexMatrix")

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

        GLES20.glUniformMatrix4fv(textureMatrixHandle, 1, false, textureMatrix, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        val samplerHandle = GLES20.glGetUniformLocation(program, "uTexture")
        GLES20.glUniform1i(samplerHandle, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    fun updateFrame(buffer: ByteBuffer, width: Int, height: Int) {
        frameLock.withLock {
            if (pendingFrame == null || pendingFrame?.capacity() != buffer.capacity()) {
                pendingFrame = ByteBuffer.allocateDirect(buffer.capacity()).order(ByteOrder.nativeOrder())
            }
            pendingFrame?.apply {
                rewind()
                buffer.rewind()
                put(buffer)
                rewind()
            }
            frameWidth = width
            frameHeight = height
            updateFrame = true
        }
    }

    fun setSensorRotation(degrees: Int) {
        frameLock.withLock {
            Matrix.setIdentityM(textureMatrix, 0)
            Matrix.translateM(textureMatrix, 0, 0.5f, 0.5f, 0f)
            Matrix.rotateM(textureMatrix, 0, degrees.toFloat(), 0f, 0f, 1f)
            Matrix.translateM(textureMatrix, 0, -0.5f, -0.5f, 0f)
        }
    }

    fun currentFrameSize(): String = frameLock.withLock {
        if (frameWidth == 0 || frameHeight == 0) "—" else "${frameWidth}x${frameHeight}"
    }

    private fun createTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return textures[0]
    }

    private companion object {
        private val VERTICES = floatArrayOf(
            -1f, -1f,
             1f, -1f,
            -1f,  1f,
             1f,  1f
        )

        private val TEX_COORDS = floatArrayOf(
            0f, 1f,
            1f, 1f,
            0f, 0f,
            1f, 0f
        )

        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vec4 transformed = uTexMatrix * vec4(aTexCoord, 0.0, 1.0);
                vTexCoord = transformed.xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """

        private fun createFloatBuffer(data: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(data.size * java.lang.Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply {
                    put(data)
                    position(0)
                }
    }
}
