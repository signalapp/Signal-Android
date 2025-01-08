package org.thoughtcrime.securesms.util

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test
import org.thoughtcrime.securesms.util.UsernameUtil.checkDiscriminator
import org.thoughtcrime.securesms.util.UsernameUtil.checkNickname

class UsernameUtilTest {
  @Test
  fun checkUsername_tooShort() {
    assertThat(checkNickname(null)).isEqualTo(UsernameUtil.InvalidReason.TOO_SHORT)
    assertThat(checkNickname("")).isEqualTo(UsernameUtil.InvalidReason.TOO_SHORT)
    assertThat(checkNickname("ab")).isEqualTo(UsernameUtil.InvalidReason.TOO_SHORT)
  }

  @Test
  fun checkUsername_tooLong() {
    assertThat(checkNickname("abcdefghijklmnopqrstuvwxyz1234567")).isEqualTo(UsernameUtil.InvalidReason.TOO_LONG)
  }

  @Test
  fun checkUsername_startsWithNumber() {
    assertThat(checkNickname("0abcdefg")).isEqualTo(UsernameUtil.InvalidReason.STARTS_WITH_NUMBER)
    assertThat(checkNickname("9abcdefg")).isEqualTo(UsernameUtil.InvalidReason.STARTS_WITH_NUMBER)
    assertThat(checkNickname("8675309")).isEqualTo(UsernameUtil.InvalidReason.STARTS_WITH_NUMBER)
  }

  @Test
  fun checkUsername_invalidCharacters() {
    assertThat(checkNickname("\$abcd")).isEqualTo(UsernameUtil.InvalidReason.INVALID_CHARACTERS)
    assertThat(checkNickname(" abcd")).isEqualTo(UsernameUtil.InvalidReason.INVALID_CHARACTERS)
    assertThat(checkNickname("ab cde")).isEqualTo(UsernameUtil.InvalidReason.INVALID_CHARACTERS)
    assertThat(checkNickname("%%%%%")).isEqualTo(UsernameUtil.InvalidReason.INVALID_CHARACTERS)
    assertThat(checkNickname("-----")).isEqualTo(UsernameUtil.InvalidReason.INVALID_CHARACTERS)
    assertThat(checkNickname("asĸ_me")).isEqualTo(UsernameUtil.InvalidReason.INVALID_CHARACTERS)
    assertThat(checkNickname("+18675309")).isEqualTo(UsernameUtil.InvalidReason.INVALID_CHARACTERS)
  }

  @Test
  fun checkUsername_validUsernames() {
    assertThat(checkNickname("abcd")).isNull()
    assertThat(checkNickname("abcdefghijklmnopqrstuvwxyz")).isNull()
    assertThat(checkNickname("ABCDEFGHIJKLMNOPQRSTUVWXYZ")).isNull()
    assertThat(checkNickname("web_head")).isNull()
    assertThat(checkNickname("Spider_Fan_1991")).isNull()
  }

  @Test
  fun checkDiscriminator_valid() {
    assertThat(checkDiscriminator(null)).isNull()
    assertThat(checkDiscriminator("01")).isNull()
    assertThat(checkDiscriminator("111111111")).isNull()
  }

  @Test
  fun checkDiscriminator_tooShort() {
    assertThat(checkDiscriminator("0")).isEqualTo(UsernameUtil.InvalidReason.TOO_SHORT)
    assertThat(checkDiscriminator("")).isEqualTo(UsernameUtil.InvalidReason.TOO_SHORT)
  }

  @Test
  fun checkDiscriminator_tooLong() {
    assertThat(checkDiscriminator("1111111111")).isEqualTo(UsernameUtil.InvalidReason.TOO_LONG)
  }

  @Test
  fun checkDiscriminator_00() {
    assertThat(checkDiscriminator("00")).isEqualTo(UsernameUtil.InvalidReason.INVALID_NUMBER_00)
  }

  @Test
  fun checkDiscriminator_prefixZero() {
    assertThat(checkDiscriminator("001")).isEqualTo(UsernameUtil.InvalidReason.INVALID_NUMBER_PREFIX_0)
    assertThat(checkDiscriminator("0001")).isEqualTo(UsernameUtil.InvalidReason.INVALID_NUMBER_PREFIX_0)
    assertThat(checkDiscriminator("011")).isEqualTo(UsernameUtil.InvalidReason.INVALID_NUMBER_PREFIX_0)
  }

  @Test
  fun checkDiscriminator_invalidChars() {
    assertThat(checkDiscriminator("a1")).isEqualTo(UsernameUtil.InvalidReason.INVALID_CHARACTERS)
    assertThat(checkDiscriminator("1x")).isEqualTo(UsernameUtil.InvalidReason.INVALID_CHARACTERS)
  }
}
