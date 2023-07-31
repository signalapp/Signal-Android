package org.thoughtcrime.securesms.database.model.databaseprotos

import com.google.protobuf.ByteString
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.storageservice.protos.groups.Member
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange
import org.signal.storageservice.protos.groups.local.DecryptedMember
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.internal.push.SignalServiceProtos
import java.util.UUID

fun groupContext(masterKey: GroupMasterKey, init: DecryptedGroupV2Context.Builder.() -> Unit): DecryptedGroupV2Context {
  val builder = DecryptedGroupV2Context.newBuilder()
  builder.context = encryptedGroupContext(masterKey)
  builder.init()
  return builder.build()
}

fun groupChange(editor: ServiceId, init: DecryptedGroupChange.Builder.() -> Unit): DecryptedGroupChange {
  val builder = DecryptedGroupChange.newBuilder()
  builder.editor = editor.toByteString()
  builder.init()
  return builder.build()
}

fun encryptedGroupContext(masterKey: GroupMasterKey): SignalServiceProtos.GroupContextV2 {
  return SignalServiceProtos.GroupContextV2.newBuilder().setMasterKey(ByteString.copyFrom(masterKey.serialize())).build()
}

fun DecryptedGroupChange.Builder.addRequestingMember(aci: ACI) {
  addNewRequestingMembers(requestingMember(aci))
}

fun DecryptedGroupChange.Builder.deleteRequestingMember(aci: ACI) {
  addDeleteRequestingMembers(aci.toByteString())
}

fun DecryptedGroupChange.Builder.addMember(aci: ACI) {
  addNewMembers(member(aci))
}

fun member(serviceId: UUID, role: Member.Role = Member.Role.DEFAULT, joinedAt: Int = 0): DecryptedMember {
  return member(ACI.from(serviceId), role, joinedAt)
}

fun member(aci: ACI, role: Member.Role = Member.Role.DEFAULT, joinedAt: Int = 0): DecryptedMember {
  return DecryptedMember.newBuilder()
    .setRole(role)
    .setUuid(aci.toByteString())
    .setJoinedAtRevision(joinedAt)
    .build()
}

fun requestingMember(serviceId: ServiceId): DecryptedRequestingMember {
  return DecryptedRequestingMember.newBuilder()
    .setUuid(serviceId.toByteString())
    .build()
}

fun pendingMember(serviceId: ServiceId): DecryptedPendingMember {
  return DecryptedPendingMember.newBuilder()
    .setServiceIdBinary(serviceId.toByteString())
    .build()
}
