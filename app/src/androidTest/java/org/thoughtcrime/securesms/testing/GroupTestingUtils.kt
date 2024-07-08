package org.thoughtcrime.securesms.testing

import okio.ByteString.Companion.toByteString
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.storageservice.protos.groups.Member
import org.signal.storageservice.protos.groups.local.DecryptedGroup
import org.signal.storageservice.protos.groups.local.DecryptedMember
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.internal.push.GroupContextV2
import kotlin.random.Random

/**
 * Helper methods for creating groups for message processing tests et al.
 */
object GroupTestingUtils {
  fun member(aci: ACI, revision: Int = 0, role: Member.Role = Member.Role.ADMINISTRATOR): DecryptedMember {
    return DecryptedMember.Builder()
      .aciBytes(aci.toByteString())
      .joinedAtRevision(revision)
      .role(role)
      .build()
  }

  fun insertGroup(revision: Int = 0, vararg members: DecryptedMember): TestGroupInfo {
    val groupMasterKey = GroupMasterKey(Random.nextBytes(GroupMasterKey.SIZE))
    val decryptedGroupState = DecryptedGroup.Builder()
      .members(members.toList())
      .revision(revision)
      .title(MessageContentFuzzer.string())
      .build()

    val groupId = SignalDatabase.groups.create(groupMasterKey, decryptedGroupState, null)!!
    val groupRecipientId = SignalDatabase.recipients.getOrInsertFromGroupId(groupId)
    SignalDatabase.recipients.setProfileSharing(groupRecipientId, true)

    return TestGroupInfo(groupId, groupMasterKey, groupRecipientId)
  }

  fun RecipientId.asMember(): DecryptedMember {
    return Recipient.resolved(this).asMember()
  }

  fun Recipient.asMember(): DecryptedMember {
    return member(aci = requireAci())
  }

  data class TestGroupInfo(val groupId: GroupId.V2, val masterKey: GroupMasterKey, val recipientId: RecipientId) {
    val groupV2Context: GroupContextV2
      get() = GroupContextV2(masterKey = masterKey.serialize().toByteString(), revision = 0)
  }
}
