package org.thoughtcrime.securesms.database.model

import androidx.annotation.WorkerThread
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.storageservice.protos.groups.AccessControl
import org.signal.storageservice.protos.groups.local.EnabledState
import org.thoughtcrime.securesms.database.GroupTable
import org.thoughtcrime.securesms.groups.GroupAccessControl
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil
import org.whispersystems.signalservice.api.push.DistributionId
import java.lang.AssertionError
import java.util.Optional

class GroupRecord(
  val id: GroupId,
  val recipientId: RecipientId,
  val title: String?,
  serializedMembers: String?,
  serializedUnmigratedV1Members: String?,
  val avatarId: Long,
  val avatarKey: ByteArray?,
  val avatarContentType: String?,
  val relay: String?,
  val isActive: Boolean,
  val avatarDigest: ByteArray?,
  val isMms: Boolean,
  groupMasterKeyBytes: ByteArray?,
  groupRevision: Int,
  decryptedGroupBytes: ByteArray?,
  val distributionId: DistributionId?,
  val lastForceUpdateTimestamp: Long
) {

  val members: List<RecipientId> by lazy {
    if (serializedMembers.isNullOrEmpty()) {
      emptyList()
    } else {
      RecipientId.fromSerializedList(serializedMembers)
    }
  }

  /** V1 members that were lost during the V1->V2 migration  */
  val unmigratedV1Members: List<RecipientId> by lazy {
    if (serializedUnmigratedV1Members.isNullOrEmpty()) {
      emptyList()
    } else {
      RecipientId.fromSerializedList(serializedUnmigratedV1Members)
    }
  }

  private val v2GroupProperties: GroupTable.V2GroupProperties? by lazy {
    if (groupMasterKeyBytes != null && decryptedGroupBytes != null) {
      val groupMasterKey = GroupMasterKey(groupMasterKeyBytes)
      GroupTable.V2GroupProperties(groupMasterKey, groupRevision, decryptedGroupBytes)
    } else {
      null
    }
  }

  val description: String
    get() = v2GroupProperties?.decryptedGroup?.description ?: ""

  val isAnnouncementGroup: Boolean
    get() = v2GroupProperties?.decryptedGroup?.isAnnouncementGroup == EnabledState.ENABLED

  val isV1Group: Boolean
    get() = !isMms && !isV2Group

  val isV2Group: Boolean
    get() = v2GroupProperties != null

  @get:WorkerThread
  val admins: List<Recipient>
    get() {
      return if (v2GroupProperties != null) {
        val resolved = members.map { Recipient.resolved(it) }
        v2GroupProperties!!.getAdmins(resolved)
      } else {
        emptyList()
      }
    }

  /** Who is allowed to add to the membership of this group. */
  val membershipAdditionAccessControl: GroupAccessControl
    get() {
      return if (isV2Group) {
        if (requireV2GroupProperties().decryptedGroup.accessControl.members == AccessControl.AccessRequired.MEMBER) {
          GroupAccessControl.ALL_MEMBERS
        } else {
          GroupAccessControl.ONLY_ADMINS
        }
      } else if (isV1Group) {
        GroupAccessControl.NO_ONE
      } else if (id.isV1) {
        GroupAccessControl.ALL_MEMBERS
      } else {
        GroupAccessControl.ONLY_ADMINS
      }
    }

  /** Who is allowed to modify the attributes of this group, name/avatar/timer etc. */
  val attributesAccessControl: GroupAccessControl
    get() {
      return if (isV2Group) {
        if (requireV2GroupProperties().decryptedGroup.accessControl.attributes == AccessControl.AccessRequired.MEMBER) {
          GroupAccessControl.ALL_MEMBERS
        } else {
          GroupAccessControl.ONLY_ADMINS
        }
      } else if (isV1Group) {
        GroupAccessControl.NO_ONE
      } else {
        GroupAccessControl.ALL_MEMBERS
      }
    }

  fun hasAvatar(): Boolean {
    return avatarId != 0L
  }

  fun requireV2GroupProperties(): GroupTable.V2GroupProperties {
    return v2GroupProperties ?: throw AssertionError()
  }

  fun isAdmin(recipient: Recipient): Boolean {
    return isV2Group && requireV2GroupProperties().isAdmin(recipient)
  }

  fun memberLevel(recipient: Recipient): GroupTable.MemberLevel {
    return if (isV2Group) {
      val memberLevel = requireV2GroupProperties().memberLevel(recipient.serviceId)
      if (recipient.isSelf && memberLevel == GroupTable.MemberLevel.NOT_A_MEMBER) {
        requireV2GroupProperties().memberLevel(Optional.ofNullable(SignalStore.account().pni))
      } else {
        memberLevel
      }
    } else if (isMms && recipient.isSelf) {
      GroupTable.MemberLevel.FULL_MEMBER
    } else if (members.contains(recipient.id)) {
      GroupTable.MemberLevel.FULL_MEMBER
    } else {
      GroupTable.MemberLevel.NOT_A_MEMBER
    }
  }

  /**
   * Whether or not the recipient is a pending member.
   */
  fun isPendingMember(recipient: Recipient): Boolean {
    if (isV2Group) {
      val serviceId = recipient.serviceId
      if (serviceId.isPresent) {
        return DecryptedGroupUtil.findPendingByUuid(requireV2GroupProperties().decryptedGroup.pendingMembersList, serviceId.get().uuid())
          .isPresent
      }
    }
    return false
  }
}
