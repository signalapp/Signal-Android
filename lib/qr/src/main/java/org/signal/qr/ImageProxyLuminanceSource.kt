package org.signal.qr

import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy
import com.google.zxing.LuminanceSource
import java.nio.ByteBuffer

/**
 * Luminance source that gets data via an [ImageProxy]. The main reason for this is because
 * the Y-Plane provided by the camera framework can have a row stride (number of bytes that make up a row)
 * that is different than the image width.
 *
 * An image width can be reported as 1080 but the row stride may be 1088. Thus when representing a row-major
 * 2D array as a 1D array, the math can go sideways if width is used instead of row stride.
 */
class ImageProxyLuminanceSource(image: ImageProxy) : LuminanceSource(image.width, image.height) {

  val yData: ByteArray

  init {
    require(image.format == ImageFormat.YUV_420_888) { "Invalid image format" }

    yData = ByteArray(image.width * image.height)

    val yBuffer: ByteBuffer = image.planes[0].buffer
    yBuffer.position(0)

    val yRowStride: Int = image.planes[0].rowStride

    for (y in 0 until image.height) {
      val yIndex: Int = y * yRowStride
      yBuffer.position(yIndex)
      yBuffer.get(yData, y * image.width, image.width)
    }
  }

  override fun getRow(y: Int, row: ByteArray?): ByteArray {
    require(y in 0 until height) { "Requested row is outside the image: $y" }

    val toReturn: ByteArray = if (row == null || row.size < width) {
      ByteArray(width)
    } else {
      row
    }

    val yIndex: Int = y * width

    yData.copyInto(toReturn, 0, yIndex, yIndex + width)

    return toReturn
  }

  override fun getMatrix(): ByteArray {
    return yData
  }

  fun render(): IntArray {
    val argbArray = IntArray(width * height)

    var yValue: Int
    yData.forEachIndexed { i, byte ->
      yValue = (byte.toInt() and 0xff).coerceIn(0..255)
      argbArray[i] = 255 shl 24 or (yValue and 255 shl 16) or (yValue and 255 shl 8) or (yValue and 255)
    }

    return argbArray
  }
}
