package org.thoughtcrime.securesms.util

import android.content.Context
import org.signal.core.util.splitByByteLength
import org.thoughtcrime.securesms.mms.TextSlide
import org.thoughtcrime.securesms.providers.BlobProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Optional

object MessageUtil {
  const val MAX_MESSAGE_SIZE_BYTES: Int = 2000 // Technically 2048, but we'll play it a little safe

  /**
   * @return If the message is longer than the allowed text size, this will return trimmed text with
   * an accompanying TextSlide. Otherwise it'll just return the original text.
   */
  @JvmStatic
  fun getSplitMessage(context: Context, rawText: String): SplitResult {
    val (trimmed, remainder) = rawText.splitByByteLength(MAX_MESSAGE_SIZE_BYTES)

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
