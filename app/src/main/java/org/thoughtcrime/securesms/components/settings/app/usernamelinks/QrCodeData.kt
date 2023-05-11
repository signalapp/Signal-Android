package org.thoughtcrime.securesms.components.settings.app.usernamelinks

import androidx.annotation.WorkerThread
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.BitSet

/**
 * Efficient representation of raw QR code data. Stored as an X/Y grid of points, where (0, 0) is the top left corner.
 * X increases as you move right, and Y increases as you go down.
 */
class QrCodeData(
  val width: Int,
  val height: Int,
  private val bits: BitSet
) {

  fun get(x: Int, y: Int): Boolean {
    return bits.get(y * width + x)
  }

  /**
   * Returns the position of the "eyes" of the QR code -- the big squares in the three corners.
   */
  fun eyes(): List<Eye> {
    val eyes: MutableList<Eye> = mutableListOf()

    val size: Int = getPossibleEyeSize()

    // Top left
    if (
      horizontalLineExists(0, 0, size) &&
      horizontalLineExists(0, size - 1, size) &&
      verticalLineExists(0, 0, size) &&
      verticalLineExists(size - 1, 0, size)
    ) {
      eyes += Eye(
        position = 0 to 0,
        size = size
      )
    }

    // Bottom left
    if (
      horizontalLineExists(0, height - size, size) &&
      horizontalLineExists(0, size - 1, size) &&
      verticalLineExists(0, height - size, size) &&
      verticalLineExists(size - 1, height - size, size)
    ) {
      eyes += Eye(
        position = 0 to height - size,
        size = size
      )
    }

    // Top right
    if (
      horizontalLineExists(width - size, 0, size) &&
      horizontalLineExists(width - size, size - 1, size) &&
      verticalLineExists(width - size, 0, size) &&
      verticalLineExists(width - 1, 0, size)
    ) {
      eyes += Eye(
        position = width - size to 0,
        size = size
      )
    }

    return eyes
  }

  private fun getPossibleEyeSize(): Int {
    var x = 0

    while (get(x, 0)) {
      x++
    }

    return x
  }

  private fun horizontalLineExists(x: Int, y: Int, length: Int): Boolean {
    for (p in x until x + length) {
      if (!get(p, y)) {
        return false
      }
    }
    return true
  }

  private fun verticalLineExists(x: Int, y: Int, length: Int): Boolean {
    for (p in y until y + length) {
      if (!get(x, p)) {
        return false
      }
    }
    return true
  }

  data class Eye(
    val position: Pair<Int, Int>,
    val size: Int
  )

  companion object {

    /**
     * Converts the provided string data into a QR representation.
     */
    @WorkerThread
    fun forData(data: String, size: Int): QrCodeData {
      val qrCodeWriter = QRCodeWriter()
      val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H.toString())

      val padded = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, size, size, hints)
      val dimens = padded.enclosingRectangle
      val xStart = dimens[0]
      val yStart = dimens[1]
      val width = dimens[2]
      val height = dimens[3]
      val bitSet = BitSet(width * height)

      for (x in xStart until xStart + width) {
        for (y in yStart until yStart + height) {
          if (padded.get(x, y)) {
            val destX = x - xStart
            val destY = y - yStart
            bitSet.set(destY * width + destX)
          }
        }
      }

      return QrCodeData(width, height, bitSet)
    }
  }
}
