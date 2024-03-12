package org.thoughtcrime.securesms.util

import org.junit.Test
import org.thoughtcrime.securesms.assertIs
import org.thoughtcrime.securesms.assertIsNull
import org.thoughtcrime.securesms.util.UsernameUtil.checkDiscriminator
import org.thoughtcrime.securesms.util.UsernameUtil.checkNickname

class UsernameUtilTest {
  @Test
  fun checkUsername_tooShort() {
    checkNickname(null) assertIs UsernameUtil.InvalidReason.TOO_SHORT
    checkNickname("") assertIs UsernameUtil.InvalidReason.TOO_SHORT
    checkNickname("ab") assertIs UsernameUtil.InvalidReason.TOO_SHORT
  }

  @Test
  fun checkUsername_tooLong() {
    checkNickname("abcdefghijklmnopqrstuvwxyz1234567") assertIs UsernameUtil.InvalidReason.TOO_LONG
  }

  @Test
  fun checkUsername_startsWithNumber() {
    checkNickname("0abcdefg") assertIs UsernameUtil.InvalidReason.STARTS_WITH_NUMBER
    checkNickname("9abcdefg") assertIs UsernameUtil.InvalidReason.STARTS_WITH_NUMBER
    checkNickname("8675309") assertIs UsernameUtil.InvalidReason.STARTS_WITH_NUMBER
  }

  @Test
  fun checkUsername_invalidCharacters() {
    checkNickname("\$abcd") assertIs UsernameUtil.InvalidReason.INVALID_CHARACTERS
    checkNickname(" abcd") assertIs UsernameUtil.InvalidReason.INVALID_CHARACTERS
    checkNickname("ab cde") assertIs UsernameUtil.InvalidReason.INVALID_CHARACTERS
    checkNickname("%%%%%") assertIs UsernameUtil.InvalidReason.INVALID_CHARACTERS
    checkNickname("-----") assertIs UsernameUtil.InvalidReason.INVALID_CHARACTERS
    checkNickname("asĸ_me") assertIs UsernameUtil.InvalidReason.INVALID_CHARACTERS
    checkNickname("+18675309") assertIs UsernameUtil.InvalidReason.INVALID_CHARACTERS
  }

  @Test
  fun checkUsername_validUsernames() {
    checkNickname("abcd").assertIsNull()
    checkNickname("abcdefghijklmnopqrstuvwxyz").assertIsNull()
    checkNickname("ABCDEFGHIJKLMNOPQRSTUVWXYZ").assertIsNull()
    checkNickname("web_head").assertIsNull()
    checkNickname("Spider_Fan_1991").assertIsNull()
  }

  @Test
  fun checkDiscriminator_valid() {
    checkDiscriminator(null).assertIsNull()
    checkDiscriminator("01").assertIsNull()
    checkDiscriminator("111111111").assertIsNull()
  }

  @Test
  fun checkDiscriminator_tooShort() {
    checkDiscriminator("0") assertIs UsernameUtil.InvalidReason.TOO_SHORT
    checkDiscriminator("") assertIs UsernameUtil.InvalidReason.TOO_SHORT
  }

  @Test
  fun checkDiscriminator_tooLong() {
    checkDiscriminator("1111111111") assertIs UsernameUtil.InvalidReason.TOO_LONG
  }

  @Test
  fun checkDiscriminator_00() {
    checkDiscriminator("00") assertIs UsernameUtil.InvalidReason.INVALID_NUMBER_00
  }

  @Test
  fun checkDiscriminator_prefixZero() {
    checkDiscriminator("001") assertIs UsernameUtil.InvalidReason.INVALID_NUMBER_PREFIX_0
    checkDiscriminator("0001") assertIs UsernameUtil.InvalidReason.INVALID_NUMBER_PREFIX_0
    checkDiscriminator("011") assertIs UsernameUtil.InvalidReason.INVALID_NUMBER_PREFIX_0
  }

  fun checkDiscriminator_invalidChars() {
    checkDiscriminator("a1") assertIs UsernameUtil.InvalidReason.INVALID_CHARACTERS
    checkDiscriminator("1x") assertIs UsernameUtil.InvalidReason.INVALID_CHARACTERS
  }
}
