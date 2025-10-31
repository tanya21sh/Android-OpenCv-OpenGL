package com.example.edgerenderer.camera

import android.media.Image

object YuvUtils {

    fun toNV21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val nv21 = ByteArray(ySize + ySize / 2)

        fillYPlane(image, nv21, width, height)
        fillUVPlanes(image, nv21, width, height)

        return nv21
    }

    private fun fillYPlane(image: Image, output: ByteArray, width: Int, height: Int) {
        val plane = image.planes[0]
        val buffer = plane.buffer.duplicate()
        buffer.position(0)
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        var outputOffset = 0
        for (row in 0 until height) {
            val rowStart = row * rowStride
            if (pixelStride == 1) {
                buffer.position(rowStart)
                buffer.get(output, outputOffset, width)
            } else {
                var column = 0
                var inputIndex = rowStart
                while (column < width) {
                    output[outputOffset + column] = buffer.get(inputIndex)
                    column++
                    inputIndex += pixelStride
                }
            }
            outputOffset += width
        }
    }

    private fun fillUVPlanes(image: Image, output: ByteArray, width: Int, height: Int) {
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val uBuffer = uPlane.buffer.duplicate()
        val vBuffer = vPlane.buffer.duplicate()
        uBuffer.position(0)
        vBuffer.position(0)

        val chromaHeight = height / 2
        val chromaWidth = width / 2
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        var outputOffset = width * height
        for (row in 0 until chromaHeight) {
            var uRowIndex = row * uvRowStride
            var vRowIndex = row * vRowStride
            for (col in 0 until chromaWidth) {
                output[outputOffset++] = vBuffer.get(vRowIndex)
                output[outputOffset++] = uBuffer.get(uRowIndex)
                uRowIndex += uvPixelStride
                vRowIndex += vPixelStride
            }
        }
    }
}
