package org.signal.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.DecodeHintType
import com.google.zxing.FormatException
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

  fun getScannedData(
    data: ByteArray,
    width: Int,
    height: Int
  ): String? {
    try {
      val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)

      val bitmap = BinaryBitmap(HybridBinarizer(source))
      val result: Result? = reader.decode(bitmap, emptyMap<DecodeHintType, String>())

      if (result != null) {
        return result.text
      }
    } catch (e: NullPointerException) {
      Log.w(TAG, e)
    } catch (e: ChecksumException) {
      Log.w(TAG, e)
    } catch (e: FormatException) {
      Log.w(TAG, e)
    } catch (e: NotFoundException) {
      // Thanks ZXing...
    }
    return null
  }

  companion object {
    private val TAG = Log.tag(QrProcessor::class.java)
  }
}
