package org.thoughtcrime.securesms.groups.v2

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import org.junit.Test
import org.signal.core.util.logging.Log.initialize
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil
import org.thoughtcrime.securesms.testutil.LogRecorder
import org.whispersystems.signalservice.api.push.ServiceId
import java.util.UUID

class ProfileKeySetTest {
  @Test
  fun empty_change() {
    val editor = randomACI()
    val profileKeySet = ProfileKeySet()

    profileKeySet.addKeysFromGroupChange(ChangeBuilder.changeBy(editor).build())

    assertThat(profileKeySet.profileKeys).isEmpty()
    assertThat(profileKeySet.authoritativeProfileKeys).isEmpty()
  }

  @Test
  fun new_member_is_not_authoritative() {
    val editor = randomACI()
    val newMember = randomACI()
    val profileKey = ProfileKeyUtil.createNew()
    val profileKeySet = ProfileKeySet()

    profileKeySet.addKeysFromGroupChange(ChangeBuilder.changeBy(editor).addMember(newMember, profileKey).build())

    assertThat(profileKeySet.authoritativeProfileKeys).isEmpty()
    assertThat(profileKeySet.profileKeys).containsOnly(newMember to profileKey)
  }

  @Test
  fun new_member_by_self_is_authoritative() {
    val newMember = randomACI()
    val profileKey = ProfileKeyUtil.createNew()
    val profileKeySet = ProfileKeySet()

    profileKeySet.addKeysFromGroupChange(ChangeBuilder.changeBy(newMember).addMember(newMember, profileKey).build())

    assertThat(profileKeySet.profileKeys).isEmpty()
    assertThat(profileKeySet.authoritativeProfileKeys).containsOnly(newMember to profileKey)
  }

  @Test
  fun new_member_by_self_promote_is_authoritative() {
    val newMember = randomACI()
    val profileKey = ProfileKeyUtil.createNew()
    val profileKeySet = ProfileKeySet()

    profileKeySet.addKeysFromGroupChange(ChangeBuilder.changeBy(newMember).promote(newMember, profileKey).build())

    assertThat(profileKeySet.profileKeys).isEmpty()
    assertThat(profileKeySet.authoritativeProfileKeys).containsOnly(newMember to profileKey)
  }

  @Test
  fun new_member_by_promote_by_other_editor_is_not_authoritative() {
    val editor = randomACI()
    val newMember = randomACI()
    val profileKey = ProfileKeyUtil.createNew()
    val profileKeySet = ProfileKeySet()

    profileKeySet.addKeysFromGroupChange(ChangeBuilder.changeBy(editor).promote(newMember, profileKey).build())

    assertThat(profileKeySet.authoritativeProfileKeys).isEmpty()
    assertThat(profileKeySet.profileKeys).containsOnly(newMember to profileKey)
  }

  @Test
  fun new_member_by_promote_by_unknown_editor_is_not_authoritative() {
    val newMember = randomACI()
    val profileKey = ProfileKeyUtil.createNew()
    val profileKeySet = ProfileKeySet()

    profileKeySet.addKeysFromGroupChange(ChangeBuilder.changeByUnknown().promote(newMember, profileKey).build())

    assertThat(profileKeySet.authoritativeProfileKeys).isEmpty()
    assertThat(profileKeySet.profileKeys).containsOnly(newMember to profileKey)
  }

  @Test
  fun profile_key_update_by_self_is_authoritative() {
    val member = randomACI()
    val profileKey = ProfileKeyUtil.createNew()
    val profileKeySet = ProfileKeySet()

    profileKeySet.addKeysFromGroupChange(ChangeBuilder.changeBy(member).profileKeyUpdate(member, profileKey).build())

    assertThat(profileKeySet.profileKeys).isEmpty()
    assertThat(profileKeySet.authoritativeProfileKeys).containsOnly(member to profileKey)
  }

  @Test
  fun profile_key_update_by_another_is_not_authoritative() {
    val editor = randomACI()
    val member = randomACI()
    val profileKey = ProfileKeyUtil.createNew()
    val profileKeySet = ProfileKeySet()

    profileKeySet.addKeysFromGroupChange(ChangeBuilder.changeBy(editor).profileKeyUpdate(member, profileKey).build())

    assertThat(profileKeySet.authoritativeProfileKeys).isEmpty()
    assertThat(profileKeySet.profileKeys).containsOnly(member to profileKey)
  }

  @Test
  fun multiple_updates_overwrite() {
    val editor = randomACI()
    val member = randomACI()
    val profileKey1 = ProfileKeyUtil.createNew()
    val profileKey2 = ProfileKeyUtil.createNew()
    val profileKeySet = ProfileKeySet()

    profileKeySet.addKeysFromGroupChange(ChangeBuilder.changeBy(editor).profileKeyUpdate(member, profileKey1).build())
    profileKeySet.addKeysFromGroupChange(ChangeBuilder.changeBy(editor).profileKeyUpdate(member, profileKey2).build())

    assertThat(profileKeySet.authoritativeProfileKeys).isEmpty()
    assertThat(profileKeySet.profileKeys).containsOnly(member to profileKey2)
  }

  @Test
  fun authoritative_takes_priority_when_seen_first() {
    val editor = randomACI()
    val member = randomACI()
    val profileKey1 = ProfileKeyUtil.createNew()
    val profileKey2 = ProfileKeyUtil.createNew()
    val profileKeySet = ProfileKeySet()

    profileKeySet.addKeysFromGroupChange(ChangeBuilder.changeBy(member).profileKeyUpdate(member, profileKey1).build())
    profileKeySet.addKeysFromGroupChange(ChangeBuilder.changeBy(editor).profileKeyUpdate(member, profileKey2).build())

    assertThat(profileKeySet.profileKeys).isEmpty()
    assertThat(profileKeySet.authoritativeProfileKeys).containsOnly(member to profileKey1)
  }

  @Test
  fun authoritative_takes_priority_when_seen_second() {
    val editor = randomACI()
    val member = randomACI()
    val profileKey1 = ProfileKeyUtil.createNew()
    val profileKey2 = ProfileKeyUtil.createNew()
    val profileKeySet = ProfileKeySet()

    profileKeySet.addKeysFromGroupChange(ChangeBuilder.changeBy(editor).profileKeyUpdate(member, profileKey1).build())
    profileKeySet.addKeysFromGroupChange(ChangeBuilder.changeBy(member).profileKeyUpdate(member, profileKey2).build())

    assertThat(profileKeySet.profileKeys).isEmpty()
    assertThat(profileKeySet.authoritativeProfileKeys).containsOnly(member to profileKey2)
  }

  @Test
  fun bad_profile_key() {
    val logRecorder = LogRecorder()
    val editor = randomACI()
    val member = randomACI()
    val badProfileKey = ByteArray(10)
    val profileKeySet = ProfileKeySet()

    initialize(logRecorder)
    profileKeySet.addKeysFromGroupChange(ChangeBuilder.changeBy(editor).profileKeyUpdate(member, badProfileKey).build())

    assertThat(profileKeySet.profileKeys).isEmpty()
    assertThat(profileKeySet.authoritativeProfileKeys).isEmpty()
    assertThat(logRecorder.warnings)
      .transform { lines ->
        lines.map { line ->
          line.message
        }
      }
      .containsOnly("Bad profile key in group")
  }

  @Test
  fun new_requesting_member_if_editor_is_authoritative() {
    val editor = randomACI()
    val profileKey = ProfileKeyUtil.createNew()
    val profileKeySet = ProfileKeySet()

    profileKeySet.addKeysFromGroupChange(ChangeBuilder.changeBy(editor).requestJoin(profileKey).build())

    assertThat(profileKeySet.authoritativeProfileKeys).containsOnly(editor to profileKey)
    assertThat(profileKeySet.profileKeys).isEmpty()
  }

  @Test
  fun new_requesting_member_if_not_editor_is_not_authoritative() {
    val editor = randomACI()
    val requesting = randomACI()
    val profileKey = ProfileKeyUtil.createNew()
    val profileKeySet = ProfileKeySet()

    profileKeySet.addKeysFromGroupChange(ChangeBuilder.changeBy(editor).requestJoin(requesting, profileKey).build())

    assertThat(profileKeySet.authoritativeProfileKeys).isEmpty()
    assertThat(profileKeySet.profileKeys).containsOnly(requesting to profileKey)
  }

  private fun randomACI(): ServiceId.ACI = ServiceId.ACI.from(UUID.randomUUID())
}
