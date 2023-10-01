package org.thoughtcrime.securesms.database.model.databaseprotos

import okio.ByteString.Companion.toByteString
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.storageservice.protos.groups.Member
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange
import org.signal.storageservice.protos.groups.local.DecryptedMember
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.internal.push.GroupContextV2
import java.util.UUID

fun groupContext(masterKey: GroupMasterKey, init: DecryptedGroupV2Context.Builder.() -> Unit): DecryptedGroupV2Context {
  val builder = DecryptedGroupV2Context.Builder()
  builder.context = encryptedGroupContext(masterKey)
  builder.init()
  return builder.build()
}

fun groupChange(editor: ServiceId, init: DecryptedGroupChange.Builder.() -> Unit): DecryptedGroupChange {
  val builder = DecryptedGroupChange.Builder()
  builder.editorServiceIdBytes = editor.toByteString()
  builder.init()
  return builder.build()
}

fun encryptedGroupContext(masterKey: GroupMasterKey): GroupContextV2 {
  return GroupContextV2.Builder().masterKey(masterKey.serialize().toByteString()).build()
}

fun DecryptedGroupChange.Builder.addRequestingMember(aci: ACI) {
  newRequestingMembers += requestingMember(aci)
}

fun DecryptedGroupChange.Builder.deleteRequestingMember(aci: ACI) {
  deleteRequestingMembers += aci.toByteString()
}

fun DecryptedGroupChange.Builder.addMember(aci: ACI) {
  newMembers += member(aci)
}

fun member(serviceId: UUID, role: Member.Role = Member.Role.DEFAULT, joinedAt: Int = 0): DecryptedMember {
  return member(ACI.from(serviceId), role, joinedAt)
}

fun member(aci: ACI, role: Member.Role = Member.Role.DEFAULT, joinedAt: Int = 0): DecryptedMember {
  return DecryptedMember.Builder()
    .role(role)
    .aciBytes(aci.toByteString())
    .joinedAtRevision(joinedAt)
    .build()
}

fun requestingMember(serviceId: ServiceId): DecryptedRequestingMember {
  return DecryptedRequestingMember.Builder()
    .aciBytes(serviceId.toByteString())
    .build()
}

fun pendingMember(serviceId: ServiceId): DecryptedPendingMember {
  return DecryptedPendingMember.Builder()
    .serviceIdBytes(serviceId.toByteString())
    .build()
}
