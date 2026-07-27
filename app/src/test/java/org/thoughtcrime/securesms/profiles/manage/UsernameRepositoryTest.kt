package org.thoughtcrime.securesms.profiles.manage

import assertk.assertThat
import assertk.assertions.isNull
import org.junit.Test

class UsernameRepositoryTest {
  @Test
  fun parseLink_one_character_base64_ref() {
    val url = "https://signal.me/#eu/A"
    assertThat(UsernameRepository.parseLink(url)).isNull()
  }
}
