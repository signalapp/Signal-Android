package org.thoughtcrime.securesms.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class UsernameUtilTest {

  @Test
  public void checkUsername_tooShort() {
    assertEquals(UsernameUtil.InvalidReason.TOO_SHORT, UsernameUtil.checkUsername(null).get());
    assertEquals(UsernameUtil.InvalidReason.TOO_SHORT, UsernameUtil.checkUsername("").get());
    assertEquals(UsernameUtil.InvalidReason.TOO_SHORT, UsernameUtil.checkUsername("ab").get());
  }

  @Test
  public void checkUsername_tooLong() {
    assertEquals(UsernameUtil.InvalidReason.TOO_LONG, UsernameUtil.checkUsername("abcdefghijklmnopqrstuvwxyz1234567").get());
  }

  @Test
  public void checkUsername_startsWithNumber() {
    assertEquals(UsernameUtil.InvalidReason.STARTS_WITH_NUMBER, UsernameUtil.checkUsername("0abcdefg").get());
    assertEquals(UsernameUtil.InvalidReason.STARTS_WITH_NUMBER, UsernameUtil.checkUsername("9abcdefg").get());
    assertEquals(UsernameUtil.InvalidReason.STARTS_WITH_NUMBER, UsernameUtil.checkUsername("8675309").get());
  }

  @Test
  public void checkUsername_invalidCharacters() {
    assertEquals(UsernameUtil.InvalidReason.INVALID_CHARACTERS, UsernameUtil.checkUsername("$abcd").get());
    assertEquals(UsernameUtil.InvalidReason.INVALID_CHARACTERS, UsernameUtil.checkUsername(" abcd").get());
    assertEquals(UsernameUtil.InvalidReason.INVALID_CHARACTERS, UsernameUtil.checkUsername("ab cde").get());
    assertEquals(UsernameUtil.InvalidReason.INVALID_CHARACTERS, UsernameUtil.checkUsername("%%%%%").get());
    assertEquals(UsernameUtil.InvalidReason.INVALID_CHARACTERS, UsernameUtil.checkUsername("-----").get());
    assertEquals(UsernameUtil.InvalidReason.INVALID_CHARACTERS, UsernameUtil.checkUsername("asĸ_me").get());
    assertEquals(UsernameUtil.InvalidReason.INVALID_CHARACTERS, UsernameUtil.checkUsername("+18675309").get());
  }

  @Test
  public void checkUsername_validUsernames() {
    assertFalse(UsernameUtil.checkUsername("abcd").isPresent());
    assertFalse(UsernameUtil.checkUsername("abcdefghijklmnopqrstuvwxyz").isPresent());
    assertFalse(UsernameUtil.checkUsername("ABCDEFGHIJKLMNOPQRSTUVWXYZ").isPresent());
    assertFalse(UsernameUtil.checkUsername("web_head").isPresent());
    assertFalse(UsernameUtil.checkUsername("Spider_Fan_1991").isPresent());
  }
}
