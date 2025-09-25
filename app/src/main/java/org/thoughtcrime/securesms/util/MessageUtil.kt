package org.thoughtcrime.securesms.util

import android.content.Context
import org.signal.core.util.kibiBytes
import org.signal.core.util.splitByByteLength
import org.thoughtcrime.securesms.mms.TextSlide
import org.thoughtcrime.securesms.providers.BlobProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Optional

object MessageUtil {
  /** The maximum size of an inlined text body we'll allow in a proto. Anything larger than this will need to be a long-text attachment. */
  @JvmField
  val MAX_INLINE_BODY_SIZE_BYTES: Int = 2.kibiBytes.bytes.toInt()

  /** The maximum total message size we'll allow ourselves to send, even as a long text attachment. */
  @JvmField
  val MAX_TOTAL_BODY_SIZE_BYTES = 64.kibiBytes.bytes.toInt()

  /**
   * @return If the message is longer than the allowed text size, this will return trimmed text with
   * an accompanying TextSlide. Otherwise it'll just return the original text.
   */
  @JvmStatic
  fun getSplitMessage(context: Context, rawText: String): SplitResult {
    val (trimmed, remainder) = rawText.splitByByteLength(MAX_INLINE_BODY_SIZE_BYTES)

    return if (remainder != null) {
      val textData = rawText.toByteArray()
      val timestamp = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())
      val filename = String.format("signal-%s.txt", timestamp)
      val textUri = BlobProvider.getInstance()
        .forData(textData)
        .withMimeType(MediaUtil.LONG_TEXT)
        .withFileName(filename)
        .createForSingleSessionInMemory()

      val textSlide = Optional.of(TextSlide(context, textUri, filename, textData.size.toLong()))

      SplitResult(trimmed, textSlide)
    } else {
      SplitResult(trimmed, Optional.empty())
    }
  }

  data class SplitResult(
    val body: String,
    val textSlide: Optional<TextSlide>
  )
}
