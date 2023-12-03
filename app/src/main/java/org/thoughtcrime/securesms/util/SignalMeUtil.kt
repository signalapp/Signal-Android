package org.thoughtcrime.securesms.util

import android.content.Context
import com.google.i18n.phonenumbers.PhoneNumberUtil
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter
import java.util.Locale

internal object SignalMeUtil {
  private val E164_REGEX = """^(https|sgnl)://signal\.me/#p/(\+[0-9]+)$""".toRegex()

  /**
   * If this is a valid signal.me link and has a valid e164, it will return the e164. Otherwise, it will return null.
   */
  @JvmStatic
  fun parseE164FromLink(context: Context, link: String?): String? {
    if (link.isNullOrBlank()) {
      return null
    }

    return E164_REGEX.find(link)?.let { match ->
      val e164: String = match.groups[2]?.value ?: return@let null

      if (PhoneNumberUtil.getInstance().isPossibleNumber(e164, Locale.getDefault().country)) {
        PhoneNumberFormatter.get(context).format(e164)
      } else {
        null
      }
    }
  }
}
