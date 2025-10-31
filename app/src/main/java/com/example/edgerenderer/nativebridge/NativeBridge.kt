package com.example.edgerenderer.nativebridge

object NativeBridge {

    init {
        System.loadLibrary("edgeproc")
    }

    @JvmStatic
    fun init() {
        nativeInit()
    }

    fun processFrame(
        nv21: ByteArray,
        width: Int,
        height: Int,
        mode: ProcessingMode
    ): ByteArray = nativeProcessFrame(nv21, width, height, mode.modeId)

    enum class ProcessingMode(val modeId: Int) {
        RAW(0),
        CANNY(1)
    }

    private external fun nativeInit()

    private external fun nativeProcessFrame(
        nv21: ByteArray,
        width: Int,
        height: Int,
        mode: Int
    ): ByteArray
}
