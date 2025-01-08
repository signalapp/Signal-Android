package org.whispersystems.signalservice.api.groupsv2

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import okio.ByteString
import org.junit.Test
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.internal.util.Util
import java.util.UUID

class DecryptedGroupUtilTest {
  @Test
  fun can_extract_editor_uuid_from_decrypted_group_change() {
    val aci = randomACI()
    val editor = aci.toByteString()
    val groupChange = DecryptedGroupChange.Builder()
      .editorServiceIdBytes(editor)
      .build()

    val parsed = DecryptedGroupUtil.editorServiceId(groupChange).get()

    assertThat(parsed).isEqualTo(aci)
  }

  @Test
  fun can_extract_uuid_from_decrypted_pending_member() {
    val aci = randomACI()
    val decryptedMember = DecryptedPendingMember.Builder()
      .serviceIdBytes(aci.toByteString())
      .build()

    val parsed = ServiceId.parseOrNull(decryptedMember.serviceIdBytes)

    assertThat(parsed).isEqualTo(aci)
  }

  @Test
  fun can_extract_uuid_from_bad_decrypted_pending_member() {
    val decryptedMember = DecryptedPendingMember.Builder()
      .serviceIdBytes(ByteString.of(*Util.getSecretBytes(18)))
      .build()

    val parsed = ServiceId.parseOrNull(decryptedMember.serviceIdBytes)

    assertThat(parsed).isNull()
  }

  @Test
  fun can_extract_uuids_for_all_pending_including_bad_entries() {
    val aci1 = randomACI()
    val aci2 = randomACI()
    val decryptedMember1 = DecryptedPendingMember.Builder()
      .serviceIdBytes(aci1.toByteString())
      .build()
    val decryptedMember2 = DecryptedPendingMember.Builder()
      .serviceIdBytes(aci2.toByteString())
      .build()
    val decryptedMember3 = DecryptedPendingMember.Builder()
      .serviceIdBytes(ByteString.of(*Util.getSecretBytes(18)))
      .build()

    val groupChange = DecryptedGroupChange.Builder()
      .newPendingMembers(listOf(decryptedMember1, decryptedMember2, decryptedMember3))
      .build()

    val pendingUuids = DecryptedGroupUtil.pendingToServiceIdList(groupChange.newPendingMembers)

    assertThat(pendingUuids).containsExactly(aci1, aci2, ServiceId.ACI.UNKNOWN)
  }

  @Test
  fun can_extract_uuids_for_all_deleted_pending_excluding_bad_entries() {
    val aci1 = randomACI()
    val aci2 = randomACI()
    val decryptedMember1 = DecryptedPendingMemberRemoval.Builder()
      .serviceIdBytes(aci1.toByteString())
      .build()
    val decryptedMember2 = DecryptedPendingMemberRemoval.Builder()
      .serviceIdBytes(aci2.toByteString())
      .build()
    val decryptedMember3 = DecryptedPendingMemberRemoval.Builder()
      .serviceIdBytes(ByteString.of(*Util.getSecretBytes(18)))
      .build()

    val groupChange = DecryptedGroupChange.Builder()
      .deletePendingMembers(listOf(decryptedMember1, decryptedMember2, decryptedMember3))
      .build()

    val removedUuids = DecryptedGroupUtil.removedPendingMembersServiceIdList(groupChange)

    assertThat(removedUuids).containsExactly(aci1, aci2)
  }

  @Test
  fun can_extract_uuids_for_all_deleted_members_excluding_bad_entries() {
    val aci1 = randomACI()
    val aci2 = randomACI()
    val groupChange = DecryptedGroupChange.Builder()
      .deleteMembers(listOf(aci1.toByteString(), aci2.toByteString(), ByteString.of(*Util.getSecretBytes(18))))
      .build()

    val removedServiceIds = DecryptedGroupUtil.removedMembersServiceIdList(groupChange)

    assertThat(removedServiceIds).containsExactly(aci1, aci2)
  }

  private fun randomACI() = ServiceId.ACI.from(UUID.randomUUID())
}
