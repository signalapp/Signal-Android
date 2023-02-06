package org.signal.qr

import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.DecodeHintType
import com.google.zxing.FormatException
import com.google.zxing.LuminanceSource
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.signal.core.util.logging.Log

/**
 * Wraps [QRCodeReader] for use from API19 or API21+.
 */
class QrProcessor {

  private val reader = QRCodeReader()

  private var previousHeight = 0
  private var previousWidth = 0

  fun getScannedData(proxy: ImageProxy): String? {
    return getScannedData(ImageProxyLuminanceSource(proxy))
  }

  fun getScannedData(
    data: ByteArray,
    width: Int,
    height: Int
  ): String? {
    return getScannedData(PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false))
  }

  private fun getScannedData(source: LuminanceSource): String? {
    try {
      if (source.width != previousWidth || source.height != previousHeight) {
        Log.i(TAG, "Processing ${source.width} x ${source.height} image")
        previousWidth = source.width
        previousHeight = source.height
      }

      listener?.invoke(source)

      val bitmap = BinaryBitmap(HybridBinarizer(source))
      val result: Result? = reader.decode(bitmap, mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.CHARACTER_SET to "ISO-8859-1"))

      if (result != null) {
        return result.text
      }
    } catch (e: NullPointerException) {
      Log.w(TAG, "Random null", e)
    } catch (e: ChecksumException) {
      Log.w(TAG, "QR code read and decoded, but checksum failed", e)
    } catch (e: FormatException) {
      Log.w(TAG, "Thrown when a barcode was successfully detected, but some aspect of the content did not conform to the barcodes format rules.", e)
    } catch (e: NotFoundException) {
      // Thanks ZXing...
    }
    return null
  }

  companion object {
    private val TAG = Log.tag(QrProcessor::class.java)

    /** For debugging only */
    var listener: ((LuminanceSource) -> Unit)? = null
  }
}
