package org.thoughtcrime.securesms.util

import org.signal.core.util.logging.Log
import org.signal.libsignal.usernames.BadDiscriminatorCharacterException
import org.signal.libsignal.usernames.BadNicknameCharacterException
import org.signal.libsignal.usernames.BaseUsernameException
import org.signal.libsignal.usernames.CannotBeEmptyException
import org.signal.libsignal.usernames.CannotStartWithDigitException
import org.signal.libsignal.usernames.DiscriminatorCannotBeEmptyException
import org.signal.libsignal.usernames.DiscriminatorCannotBeSingleDigitException
import org.signal.libsignal.usernames.DiscriminatorCannotBeZeroException
import org.signal.libsignal.usernames.DiscriminatorCannotHaveLeadingZerosException
import org.signal.libsignal.usernames.DiscriminatorTooLargeException
import org.signal.libsignal.usernames.NicknameTooLongException
import org.signal.libsignal.usernames.NicknameTooShortException
import org.signal.libsignal.usernames.Username
import java.util.Locale
import java.util.regex.Pattern

object UsernameUtil {
  private val TAG = Log.tag(UsernameUtil::class.java)
  const val MIN_NICKNAME_LENGTH = 3
  const val MAX_NICKNAME_LENGTH = 32
  const val MIN_DISCRIMINATOR_LENGTH = 2
  const val MAX_DISCRIMINATOR_LENGTH = 9
  private val FULL_PATTERN = Pattern.compile(String.format(Locale.US, "^[a-zA-Z_][a-zA-Z0-9_]{%d,%d}$", MIN_NICKNAME_LENGTH - 1, MAX_NICKNAME_LENGTH - 1), Pattern.CASE_INSENSITIVE)
  private val DIGIT_START_PATTERN = Pattern.compile("^[0-9].*$")
  private const val BASE_URL_SCHEMELESS = "signal.me/#eu/"
  private const val BASE_URL = "https://$BASE_URL_SCHEMELESS"

  private val SEARCH_PATTERN = Pattern.compile(
    String.format(
      Locale.US,
      "^@?[a-zA-Z_][a-zA-Z0-9_]{%d,%d}(.[0-9]+)?$",
      MIN_NICKNAME_LENGTH - 1,
      MAX_NICKNAME_LENGTH - 1,
      Pattern.CASE_INSENSITIVE
    )
  )

  @JvmStatic
  fun isValidUsernameForSearch(value: String): Boolean {
    return value.isNotEmpty() && SEARCH_PATTERN.matcher(value).matches()
  }

  @JvmStatic
  fun sanitizeUsernameFromSearch(value: String): String {
    return value.replace("[^a-zA-Z0-9_.]".toRegex(), "")
  }

  @JvmStatic
  fun checkNickname(value: String?): InvalidReason? {
    if (value == null) {
      return InvalidReason.TOO_SHORT
    }

    return try {
      // We only want to check the nickname, so we pass in a known-valid discriminator
      Username.fromParts(value, "01", MIN_NICKNAME_LENGTH, MAX_NICKNAME_LENGTH)
      null
    } catch (e: BadNicknameCharacterException) {
      InvalidReason.INVALID_CHARACTERS
    } catch (e: CannotBeEmptyException) {
      InvalidReason.TOO_SHORT
    } catch (e: CannotStartWithDigitException) {
      InvalidReason.STARTS_WITH_NUMBER
    } catch (e: NicknameTooLongException) {
      InvalidReason.TOO_LONG
    } catch (e: NicknameTooShortException) {
      InvalidReason.TOO_SHORT
    } catch (e: BaseUsernameException) {
      Log.w(TAG, "Unhandled verification exception!", e)
      InvalidReason.INVALID_CHARACTERS
    }
  }

  fun checkDiscriminator(value: String?): InvalidReason? {
    if (value == null) {
      return null
    }

    if (value.length < MIN_DISCRIMINATOR_LENGTH) {
      return InvalidReason.TOO_SHORT
    }

    if (value.length > MAX_DISCRIMINATOR_LENGTH) {
      return InvalidReason.TOO_LONG
    }

    return try {
      // We only want to check the discriminator, so we pass in a known-valid nickname
      Username.fromParts("spiderman", value, MIN_NICKNAME_LENGTH, MAX_NICKNAME_LENGTH)
      null
    } catch (e: BadDiscriminatorCharacterException) {
      InvalidReason.INVALID_CHARACTERS
    } catch (e: DiscriminatorCannotBeEmptyException) {
      InvalidReason.TOO_SHORT
    } catch (e: DiscriminatorCannotBeSingleDigitException) {
      InvalidReason.TOO_SHORT
    } catch (e: DiscriminatorCannotBeZeroException) {
      if (value.length < 2) {
        InvalidReason.TOO_SHORT
      } else if (value == "00") {
        InvalidReason.INVALID_NUMBER_00
      } else {
        InvalidReason.INVALID_NUMBER_PREFIX_0
      }
    } catch (e: DiscriminatorCannotHaveLeadingZerosException) {
      if (value.length < 2) {
        InvalidReason.TOO_SHORT
      } else if (value == "00") {
        InvalidReason.INVALID_NUMBER_00
      } else {
        InvalidReason.INVALID_NUMBER_PREFIX_0
      }
    } catch (e: DiscriminatorTooLargeException) {
      InvalidReason.TOO_LONG
    } catch (e: BaseUsernameException) {
      Log.w(TAG, "Unhandled verification exception!", e)
      InvalidReason.INVALID_CHARACTERS
    }
  }

  enum class InvalidReason {
    TOO_SHORT,
    TOO_LONG,
    INVALID_CHARACTERS,
    STARTS_WITH_NUMBER,
    INVALID_NUMBER,
    INVALID_NUMBER_00,
    INVALID_NUMBER_PREFIX_0
  }
}
