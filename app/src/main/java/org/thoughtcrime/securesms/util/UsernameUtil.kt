package org.thoughtcrime.securesms.util

import androidx.annotation.WorkerThread
import org.signal.core.util.logging.Log
import org.signal.libsignal.usernames.BaseUsernameException
import org.signal.libsignal.usernames.Username
import org.thoughtcrime.securesms.database.SignalDatabase.Companion.recipients
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.UsernameLinkComponents
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.api.util.toByteArray
import org.whispersystems.util.Base64UrlSafe
import java.io.IOException
import java.util.Locale
import java.util.Optional
import java.util.UUID
import java.util.regex.Pattern

object UsernameUtil {
  private val TAG = Log.tag(UsernameUtil::class.java)
  const val MIN_LENGTH = 3
  const val MAX_LENGTH = 32
  private val FULL_PATTERN = Pattern.compile(String.format(Locale.US, "^[a-zA-Z_][a-zA-Z0-9_]{%d,%d}$", MIN_LENGTH - 1, MAX_LENGTH - 1), Pattern.CASE_INSENSITIVE)
  private val DIGIT_START_PATTERN = Pattern.compile("^[0-9].*$")
  private val URL_PATTERN = """(https://)?signal.me/?#eu/([a-zA-Z0-9+\-_/]+)""".toRegex()
  private const val BASE_URL_SCHEMELESS = "signal.me/#eu/"
  private const val BASE_URL = "https://$BASE_URL_SCHEMELESS"

  private val SEARCH_PATTERN = Pattern.compile(
    String.format(
      Locale.US,
      "^[a-zA-Z_][a-zA-Z0-9_]{%d,%d}(.[0-9]+)?$",
      MIN_LENGTH - 1,
      MAX_LENGTH - 1,
      Pattern.CASE_INSENSITIVE
    )
  )

  fun isValidUsernameForSearch(value: String): Boolean {
    return value.isNotEmpty() && SEARCH_PATTERN.matcher(value).matches()
  }

  @JvmStatic
  fun checkUsername(value: String?): InvalidReason? {
    return when {
      value == null -> {
        InvalidReason.TOO_SHORT
      }
      value.length < MIN_LENGTH -> {
        InvalidReason.TOO_SHORT
      }
      value.length > MAX_LENGTH -> {
        InvalidReason.TOO_LONG
      }
      DIGIT_START_PATTERN.matcher(value).matches() -> {
        InvalidReason.STARTS_WITH_NUMBER
      }
      !FULL_PATTERN.matcher(value).matches() -> {
        InvalidReason.INVALID_CHARACTERS
      }
      else -> {
        null
      }
    }
  }

  @JvmStatic
  @WorkerThread
  fun fetchAciForUsername(username: String): Optional<ServiceId> {
    val localId = recipients.getByUsername(username)

    if (localId.isPresent) {
      val recipient = Recipient.resolved(localId.get())
      if (recipient.serviceId.isPresent) {
        Log.i(TAG, "Found username locally -- using associated UUID.")
        return recipient.serviceId
      } else {
        Log.w(TAG, "Found username locally, but it had no associated UUID! Clearing it.")
        recipients.clearUsernameIfExists(username)
      }
    }

    Log.d(TAG, "No local user with this username. Searching remotely.")

    return try {
      fetchAciForUsernameHash(Base64UrlSafe.encodeBytesWithoutPadding(Username.hash(username)))
    } catch (e: BaseUsernameException) {
      Optional.empty()
    }
  }

  /**
   * Hashes a username to a url-safe base64 string.
   * @throws BaseUsernameException If the username is invalid and un-hashable.
   */
  @Throws(BaseUsernameException::class)
  fun hashUsernameToBase64(username: String?): String {
    return Base64UrlSafe.encodeBytesWithoutPadding(Username.hash(username))
  }

  @JvmStatic
  @WorkerThread
  fun fetchAciForUsernameHash(base64UrlSafeEncodedUsernameHash: String): Optional<ServiceId> {
    return try {
      val aci = ApplicationDependencies.getSignalServiceAccountManager().getAciByUsernameHash(base64UrlSafeEncodedUsernameHash)
      Optional.ofNullable(aci)
    } catch (e: IOException) {
      Log.w(TAG, "Failed to get ACI for username hash", e)
      Optional.empty()
    }
  }

  /**
   * Generates a username link from the provided [UsernameLinkComponents].
   */
  fun generateLink(components: UsernameLinkComponents): String {
    val combined: ByteArray = components.entropy + components.serverId.toByteArray()
    val base64 = Base64UrlSafe.encodeBytesWithoutPadding(combined)
    return BASE_URL + base64
  }

  /**
   * Parses out the [UsernameLinkComponents] from a link if possible, otherwise null.
   * You need to make a separate network request to convert these components into a username.
   */
  fun parseLink(url: String): UsernameLinkComponents? {
    val match: MatchResult = URL_PATTERN.find(url) ?: return null
    val path: String = match.groups[2]?.value ?: return null
    val allBytes: ByteArray = Base64UrlSafe.decodePaddingAgnostic(path)

    if (allBytes.size != 48) {
      return null
    }

    val entropy: ByteArray = allBytes.slice(0 until 32).toByteArray()
    val serverId: ByteArray = allBytes.slice(32 until allBytes.size).toByteArray()
    val serverIdUuid: UUID = UuidUtil.parseOrNull(serverId) ?: return null

    return UsernameLinkComponents(entropy = entropy, serverId = serverIdUuid)
  }

  enum class InvalidReason {
    TOO_SHORT,
    TOO_LONG,
    INVALID_CHARACTERS,
    STARTS_WITH_NUMBER
  }
}
