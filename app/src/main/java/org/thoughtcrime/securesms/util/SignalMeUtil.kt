package org.thoughtcrime.securesms.util

import android.content.Context
import com.google.i18n.phonenumbers.PhoneNumberUtil
import org.signal.core.util.Base64
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter
import org.whispersystems.signalservice.api.push.UsernameLinkComponents
import org.whispersystems.signalservice.api.util.UuidUtil
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.util.Locale

internal object SignalMeUtil {
  private val E164_REGEX = """^(https|sgnl)://signal\.me/#p/(\+[0-9]+)$""".toRegex()
  private val USERNAME_REGEX = """^(https|sgnl)://signal\.me/#eu/(.+)$""".toRegex()

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

  /**
   * If this is a valid signal.me link and has valid username link components, it will return those components. Otherwise, it will return null.
   */
  @JvmStatic
  fun parseUsernameComponentsFromLink(link: String?): UsernameLinkComponents? {
    if (link.isNullOrBlank()) {
      return null
    }

    return USERNAME_REGEX.find(link)?.let { match ->
      val usernameLinkBase64: String = match.groups[2]?.value ?: return@let null

      try {
        val usernameLinkData: ByteArray = Base64.decode(usernameLinkBase64).takeIf { it.size == 48 } ?: return@let null
        val entropy: ByteArray = usernameLinkData.sliceArray(0 until 32)
        val uuidBytes: ByteArray = usernameLinkData.sliceArray(32 until usernameLinkData.size)
        val uuid = UuidUtil.parseOrNull(uuidBytes)

        UsernameLinkComponents(entropy, uuid)
      } catch (e: UnsupportedEncodingException) {
        null
      } catch (e: IOException) {
        null
      }
    }
  }
}
