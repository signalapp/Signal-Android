package org.thoughtcrime.securesms.components.settings.app.usernamelinks

import androidx.annotation.WorkerThread
import androidx.compose.ui.unit.IntOffset
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
  val canSupportIconOverlay: Boolean,
  private val bits: BitSet
) {

  /**
   * Returns true if the bit in the QR code is "on" for the specified position, false if it is "off" or out of bounds.
   */
  fun get(position: IntOffset): Boolean {
    val (x, y) = position
    return if (x < 0 || y < 0 || x >= width || y >= height) {
      false
    } else {
      bits.get(y * width + x)
    }
  }

  companion object {

    /**
     * Converts the provided string data into a QR representation.
     *
     * @param supportIconOverlay indicates data can be rendered with the icon overlay. Rendering with an icon relies on more error correction
     * data in the QR which requires a denser rendering which is sometimes not easily scanned by our scanner. Set to false if data is expected to be
     * long to prevent scanning issues.
     */
    @WorkerThread
    fun forData(data: String, supportIconOverlay: Boolean = true): QrCodeData {
      val qrCodeWriter = QRCodeWriter()
      val hints = mapOf(EncodeHintType.ERROR_CORRECTION to if (supportIconOverlay) ErrorCorrectionLevel.Q.toString() else ErrorCorrectionLevel.L.toString())

      val padded = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, 64, 64, hints)
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

      return QrCodeData(width, height, supportIconOverlay, bitSet)
    }
  }
}
