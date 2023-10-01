package org.thoughtcrime.securesms.util

import org.junit.Test
import org.thoughtcrime.securesms.assertIs
import org.thoughtcrime.securesms.assertIsNull
import org.thoughtcrime.securesms.util.UsernameUtil.checkUsername

class UsernameUtilTest {
  @Test
  fun checkUsername_tooShort() {
    checkUsername(null) assertIs UsernameUtil.InvalidReason.TOO_SHORT
    checkUsername("") assertIs UsernameUtil.InvalidReason.TOO_SHORT
    checkUsername("ab") assertIs UsernameUtil.InvalidReason.TOO_SHORT
  }

  @Test
  fun checkUsername_tooLong() {
    checkUsername("abcdefghijklmnopqrstuvwxyz1234567") assertIs UsernameUtil.InvalidReason.TOO_LONG
  }

  @Test
  fun checkUsername_startsWithNumber() {
    checkUsername("0abcdefg") assertIs UsernameUtil.InvalidReason.STARTS_WITH_NUMBER
    checkUsername("9abcdefg") assertIs UsernameUtil.InvalidReason.STARTS_WITH_NUMBER
    checkUsername("8675309") assertIs UsernameUtil.InvalidReason.STARTS_WITH_NUMBER
  }

  @Test
  fun checkUsername_invalidCharacters() {
    checkUsername("\$abcd") assertIs UsernameUtil.InvalidReason.INVALID_CHARACTERS
    checkUsername(" abcd") assertIs UsernameUtil.InvalidReason.INVALID_CHARACTERS
    checkUsername("ab cde") assertIs UsernameUtil.InvalidReason.INVALID_CHARACTERS
    checkUsername("%%%%%") assertIs UsernameUtil.InvalidReason.INVALID_CHARACTERS
    checkUsername("-----") assertIs UsernameUtil.InvalidReason.INVALID_CHARACTERS
    checkUsername("asĸ_me") assertIs UsernameUtil.InvalidReason.INVALID_CHARACTERS
    checkUsername("+18675309") assertIs UsernameUtil.InvalidReason.INVALID_CHARACTERS
  }

  @Test
  fun checkUsername_validUsernames() {
    checkUsername("abcd").assertIsNull()
    checkUsername("abcdefghijklmnopqrstuvwxyz").assertIsNull()
    checkUsername("ABCDEFGHIJKLMNOPQRSTUVWXYZ").assertIsNull()
    checkUsername("web_head").assertIsNull()
    checkUsername("Spider_Fan_1991").assertIsNull()
  }
}
