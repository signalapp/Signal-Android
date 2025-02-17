package org.thoughtcrime.securesms.profiles.manage

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class UsernameEditStateMachineTest {
  @Test
  fun `Given NoUserEntry, when user clears the username field, then I expect NoUserEntry with empty username and copied discriminator`() {
    val given = UsernameEditStateMachine.NoUserEntry(nickname = "MilesMorales", discriminator = "07", stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val expected = UsernameEditStateMachine.NoUserEntry(nickname = "", discriminator = "07", stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val actual = given.onUserChangedNickname("")

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given NoUserEntry, when user enters text into the username field, then I expect UserEnteredNickname with given username and copied discriminator`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.NoUserEntry(nickname = "MilesMorales", discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val expected = UsernameEditStateMachine.UserEnteredNickname(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val actual = given.onUserChangedNickname(nickname)

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given NoUserEntry, when user clears the discriminator field, then I expect NoUserEntry with given username and empty discriminator`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.NoUserEntry(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val expected = UsernameEditStateMachine.NoUserEntry(nickname = nickname, discriminator = "", stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val actual = given.onUserChangedDiscriminator("")

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given NoUserEntry, when user enters text into the discriminator field, then I expect UserEnteredDiscriminator with given discriminator and copied nickname`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.NoUserEntry(nickname = nickname, discriminator = "", stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val expected = UsernameEditStateMachine.UserEnteredDiscriminator(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val actual = given.onUserChangedDiscriminator(discriminator)

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given NoUserEntry, when system clears the username field, then I expect NoUserEntry with empty username and copied discriminator`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.NoUserEntry(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val expected = UsernameEditStateMachine.NoUserEntry(nickname = "", discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val actual = given.onSystemChangedNickname("")

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given NoUserEntry, when system enters text into the username field, then I expect NoUserEntry with given username and copied discriminator`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.NoUserEntry(nickname = "MilesMorales", discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val expected = UsernameEditStateMachine.NoUserEntry(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val actual = given.onSystemChangedNickname(nickname)

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given NoUserEntry, when system clears the discriminator field, then I expect NoUserEntry with given username and empty discriminator`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.NoUserEntry(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val expected = UsernameEditStateMachine.NoUserEntry(nickname = nickname, discriminator = "", stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val actual = given.onSystemChangedDiscriminator("")

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given NoUserEntry, when system enters text into the discriminator field, then I expect UserEnteredDiscriminator with given discriminator and copied nickname`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.NoUserEntry(nickname = nickname, discriminator = "", stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val expected = UsernameEditStateMachine.NoUserEntry(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val actual = given.onSystemChangedDiscriminator(discriminator)

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given UserEnteredNickname, when user clears the username field, then I expect NoUserEntry with empty username and copied discriminator`() {
    val given = UsernameEditStateMachine.UserEnteredNickname(nickname = "MilesMorales", discriminator = "07", stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val expected = UsernameEditStateMachine.NoUserEntry(nickname = "", discriminator = "07", stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val actual = given.onUserChangedNickname("")

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given UserEnteredNickname, when user enters text into the username field, then I expect UserEnteredNickname with given username and copied discriminator`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.UserEnteredNickname(nickname = "MilesMorales", discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val expected = UsernameEditStateMachine.UserEnteredNickname(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val actual = given.onUserChangedNickname(nickname)

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given UserEnteredNickname, when user clears the discriminator field, then I expect UserEnteredNickname with given username and empty discriminator`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.UserEnteredNickname(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val expected = UsernameEditStateMachine.UserEnteredNickname(nickname = nickname, discriminator = "", stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val actual = given.onUserChangedDiscriminator("")

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given UserEnteredNickname, when user enters text into the discriminator field, then I expect UserEnteredNicknameAndDiscriminator with given discriminator and copied nickname`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.UserEnteredNickname(nickname = nickname, discriminator = "", stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val expected = UsernameEditStateMachine.UserEnteredNicknameAndDiscriminator(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val actual = given.onUserChangedDiscriminator(discriminator)

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given UserEnteredNickname, when system clears the username field, then I expect NoUserEntry with empty username and copied discriminator`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.UserEnteredNickname(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val expected = UsernameEditStateMachine.NoUserEntry(nickname = "", discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val actual = given.onSystemChangedNickname("")

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given UserEnteredNickname, when system enters text into the username field, then I expect NoUserEntry with given username and copied discriminator`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.UserEnteredNickname(nickname = "MilesMorales", discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val expected = UsernameEditStateMachine.NoUserEntry(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val actual = given.onSystemChangedNickname(nickname)

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given UserEnteredNickname, when system clears the discriminator field, then I expect UserEnteredNickname with given username and empty discriminator`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.UserEnteredNickname(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val expected = UsernameEditStateMachine.UserEnteredNickname(nickname = nickname, discriminator = "", stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val actual = given.onSystemChangedDiscriminator("")

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given UserEnteredNickname, when system enters text into the discriminator field, then I expect UserEnteredDiscriminator with given discriminator and copied nickname`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.UserEnteredNickname(nickname = nickname, discriminator = "", stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val expected = UsernameEditStateMachine.UserEnteredNickname(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val actual = given.onSystemChangedDiscriminator(discriminator)

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given UserEnteredDiscriminator, when user clears the username field, then I expect NoUserEntry with empty username and copied discriminator`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.UserEnteredDiscriminator(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val expected = UsernameEditStateMachine.UserEnteredDiscriminator(nickname = "", discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val actual = given.onUserChangedNickname("")

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given UserEnteredDiscriminator, when user enters text into the username field, then I expect UserEnteredNicknameAndDiscriminator with given username and copied discriminator`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.UserEnteredDiscriminator(nickname = "MilesMorales", discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val expected = UsernameEditStateMachine.UserEnteredNicknameAndDiscriminator(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val actual = given.onUserChangedNickname(nickname)

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given UserEnteredDiscriminator with an empty discriminator, when user enters text into the username field, then I expect UserEnteredNickname with given username and empty discriminator`() {
    val nickname = "Nick"
    val discriminator = ""
    val given = UsernameEditStateMachine.UserEnteredDiscriminator(nickname = "MilesMorales", discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val expected = UsernameEditStateMachine.UserEnteredNickname(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val actual = given.onUserChangedNickname(nickname)

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given UserEnteredDiscriminator, when user clears the discriminator field, then I expect UserEnteredDiscriminator with given username and empty discriminator`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.UserEnteredDiscriminator(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val expected = UsernameEditStateMachine.UserEnteredDiscriminator(nickname = nickname, discriminator = "", stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val actual = given.onUserChangedDiscriminator("")

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given UserEnteredDiscriminator, when user enters text into the discriminator field, then I expect UserEnteredDiscriminator with given discriminator and copied nickname`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.UserEnteredDiscriminator(nickname = nickname, discriminator = "", stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val expected = UsernameEditStateMachine.UserEnteredDiscriminator(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val actual = given.onUserChangedDiscriminator(discriminator)

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given UserEnteredDiscriminator, when system clears the username field, then I expect UserEnteredDiscriminator with empty username and copied discriminator`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.UserEnteredDiscriminator(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val expected = UsernameEditStateMachine.UserEnteredDiscriminator(nickname = "", discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val actual = given.onSystemChangedNickname("")

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given UserEnteredDiscriminator, when system enters text into the username field, then I expect UserEnteredDiscriminator with given username and copied discriminator`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.UserEnteredDiscriminator(nickname = "MilesMorales", discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val expected = UsernameEditStateMachine.UserEnteredDiscriminator(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val actual = given.onSystemChangedNickname(nickname)

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given UserEnteredDiscriminator, when system clears the discriminator field, then I expect NoUserEntry with given username and empty discriminator`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.UserEnteredDiscriminator(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val expected = UsernameEditStateMachine.NoUserEntry(nickname = nickname, discriminator = "", stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val actual = given.onSystemChangedDiscriminator("")

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given UserEnteredDiscriminator, when system enters text into the discriminator field, then I expect NoUserEntry with given discriminator and copied nickname`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.UserEnteredDiscriminator(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val expected = UsernameEditStateMachine.NoUserEntry(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val actual = given.onSystemChangedDiscriminator(discriminator)

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given UserEnteredNicknameAndDiscriminator, when user clears the username field, then I expect UserEnteredDiscriminator with empty username and copied discriminator`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.UserEnteredNicknameAndDiscriminator(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val expected = UsernameEditStateMachine.UserEnteredDiscriminator(nickname = "", discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val actual = given.onUserChangedNickname("")

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given UserEnteredNicknameAndDiscriminator, when user enters text into the username field, then I expect UserEnteredNicknameAndDiscriminator with given username and copied discriminator`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.UserEnteredNicknameAndDiscriminator(nickname = "MilesMorales", discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val expected = UsernameEditStateMachine.UserEnteredNicknameAndDiscriminator(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val actual = given.onUserChangedNickname(nickname)

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given UserEnteredNicknameAndDiscriminator with empty discriminator, when user enters text into the username field, then I expect UserEnteredNickname with given username and empty discriminator`() {
    val nickname = "Nick"
    val discriminator = ""
    val given = UsernameEditStateMachine.UserEnteredNicknameAndDiscriminator(nickname = "MilesMorales", discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val expected = UsernameEditStateMachine.UserEnteredNickname(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val actual = given.onUserChangedNickname(nickname)

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given UserEnteredNicknameAndDiscriminator, when user clears the discriminator field, then I expect UserEnteredNicknameAndDiscriminator with given username and empty discriminator`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.UserEnteredNicknameAndDiscriminator(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val expected = UsernameEditStateMachine.UserEnteredNicknameAndDiscriminator(nickname = nickname, discriminator = "", stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val actual = given.onUserChangedDiscriminator("")

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given UserEnteredNicknameAndDiscriminator, when user enters text into the discriminator field, then I expect UserEnteredNicknameAndDiscriminator with given discriminator and copied nickname`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.UserEnteredNicknameAndDiscriminator(nickname = nickname, discriminator = "", stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val expected = UsernameEditStateMachine.UserEnteredNicknameAndDiscriminator(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val actual = given.onUserChangedDiscriminator(discriminator)

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given UserEnteredNicknameAndDiscriminator, when system clears the username field, then I expect UserEnteredDiscriminator with empty username and copied discriminator`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.UserEnteredNicknameAndDiscriminator(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val expected = UsernameEditStateMachine.UserEnteredDiscriminator(nickname = "", discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val actual = given.onSystemChangedNickname("")

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given UserEnteredNicknameAndDiscriminator, when system enters text into the username field, then I expect NoUserEntry with given username and copied discriminator`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.UserEnteredNicknameAndDiscriminator(nickname = "MilesMorales", discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val expected = UsernameEditStateMachine.UserEnteredDiscriminator(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val actual = given.onSystemChangedNickname(nickname)

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given UserEnteredNicknameAndDiscriminator, when system clears the discriminator field, then I expect UserEnteredNickname with given username and empty discriminator`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.UserEnteredNicknameAndDiscriminator(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val expected = UsernameEditStateMachine.UserEnteredNickname(nickname = nickname, discriminator = "", stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val actual = given.onSystemChangedDiscriminator("")

    assertThat(actual).isEqualTo(expected)
  }

  @Test
  fun `Given UserEnteredNicknameAndDiscriminator, when system enters text into the discriminator field, then I expect NoUserEntry with given discriminator and copied nickname`() {
    val nickname = "Nick"
    val discriminator = "07"
    val given = UsernameEditStateMachine.UserEnteredNicknameAndDiscriminator(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.USER)
    val expected = UsernameEditStateMachine.UserEnteredNickname(nickname = nickname, discriminator = discriminator, stateModifier = UsernameEditStateMachine.StateModifier.SYSTEM)
    val actual = given.onSystemChangedDiscriminator(discriminator)

    assertThat(actual).isEqualTo(expected)
  }
}
