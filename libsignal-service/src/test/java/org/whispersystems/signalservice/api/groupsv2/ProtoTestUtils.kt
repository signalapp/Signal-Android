package org.whispersystems.signalservice.api.groupsv2

import okio.Buffer
import okio.ByteString
import org.signal.libsignal.zkgroup.InvalidInputException
import org.signal.libsignal.zkgroup.profiles.ProfileKey
import org.signal.storageservice.protos.groups.Member
import org.signal.storageservice.protos.groups.RequestingMember
import org.signal.storageservice.protos.groups.local.DecryptedApproveMember
import org.signal.storageservice.protos.groups.local.DecryptedBannedMember
import org.signal.storageservice.protos.groups.local.DecryptedMember
import org.signal.storageservice.protos.groups.local.DecryptedModifyMemberRole
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember
import org.signal.storageservice.protos.groups.local.DecryptedPendingMemberRemoval
import org.signal.storageservice.protos.groups.local.DecryptedRequestingMember
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.internal.util.Util
import java.security.SecureRandom
import java.util.UUID

internal object ProtoTestUtils {
  fun randomProfileKey(): ProfileKey {
    val contents = ByteArray(32)
    SecureRandom().nextBytes(contents)
    try {
      return ProfileKey(contents)
    } catch (e: InvalidInputException) {
      throw AssertionError()
    }
  }

  /**
   * Emulates encryption by creating a unique [ByteString] that won't equal a byte string created from the [UUID].
   */
  fun encrypt(uuid: UUID): ByteString {
    val uuidBytes = UuidUtil.toByteArray(uuid)
    return ByteString.of(*uuidBytes.copyOf(uuidBytes.size + 1))
  }

  /**
   * Emulates a presentation by concatenating the uuid and profile key which makes it suitable for
   * equality assertions in these tests.
   */
  fun presentation(uuid: UUID, profileKey: ProfileKey): ByteString {
    val uuidBytes = UuidUtil.toByteArray(uuid)
    val profileKeyBytes = profileKey.serialize()
    val concat = ByteArray(uuidBytes.size + profileKeyBytes.size)

    System.arraycopy(uuidBytes, 0, concat, 0, uuidBytes.size)
    System.arraycopy(profileKeyBytes, 0, concat, uuidBytes.size, profileKeyBytes.size)

    return ByteString.of(*concat)
  }

  /**
   * Emulates a presentation by concatenating the uuid and profile key which makes it suitable for
   * equality assertions in these tests.
   */
  fun presentation(uuid: ByteString, profileKey: ByteString): ByteString {
    Buffer().use { buffer ->
      buffer.write(uuid)
      buffer.write(profileKey)
      return buffer.readByteString()
    }
  }

  fun promoteAdmin(member: UUID): DecryptedModifyMemberRole {
    return DecryptedModifyMemberRole.Builder()
      .aciBytes(UuidUtil.toByteString(member))
      .role(Member.Role.ADMINISTRATOR)
      .build()
  }

  fun demoteAdmin(member: UUID): DecryptedModifyMemberRole {
    return DecryptedModifyMemberRole.Builder()
      .aciBytes(UuidUtil.toByteString(member))
      .role(Member.Role.DEFAULT)
      .build()
  }

  fun encryptedMember(uuid: UUID, profileKey: ProfileKey): Member {
    return Member.Builder()
      .presentation(presentation(uuid, profileKey))
      .build()
  }

  fun encryptedRequestingMember(uuid: UUID, profileKey: ProfileKey): RequestingMember {
    return RequestingMember.Builder()
      .presentation(presentation(uuid, profileKey))
      .build()
  }

  fun member(uuid: UUID): DecryptedMember {
    return DecryptedMember.Builder()
      .aciBytes(UuidUtil.toByteString(uuid))
      .role(Member.Role.DEFAULT)
      .build()
  }

  fun member(uuid: UUID, profileKey: ByteString, joinedAtRevision: Int): DecryptedMember {
    return DecryptedMember.Builder()
      .aciBytes(UuidUtil.toByteString(uuid))
      .role(Member.Role.DEFAULT)
      .joinedAtRevision(joinedAtRevision)
      .profileKey(profileKey)
      .build()
  }

  fun pendingMemberRemoval(uuid: UUID): DecryptedPendingMemberRemoval {
    return DecryptedPendingMemberRemoval.Builder()
      .serviceIdBytes(UuidUtil.toByteString(uuid))
      .serviceIdCipherText(encrypt(uuid))
      .build()
  }

  fun pendingMember(uuid: UUID): DecryptedPendingMember {
    return DecryptedPendingMember.Builder()
      .serviceIdBytes(UuidUtil.toByteString(uuid))
      .serviceIdCipherText(encrypt(uuid))
      .role(Member.Role.DEFAULT)
      .build()
  }

  @JvmOverloads
  fun requestingMember(uuid: UUID, profileKey: ProfileKey = newProfileKey()): DecryptedRequestingMember {
    return DecryptedRequestingMember.Builder()
      .aciBytes(UuidUtil.toByteString(uuid))
      .profileKey(ByteString.of(*profileKey.serialize()))
      .build()
  }

  fun bannedMember(uuid: UUID): DecryptedBannedMember {
    return DecryptedBannedMember.Builder()
      .serviceIdBytes(UuidUtil.toByteString(uuid))
      .build()
  }

  fun approveMember(uuid: UUID): DecryptedApproveMember {
    return approve(uuid, Member.Role.DEFAULT)
  }

  fun approveAdmin(uuid: UUID): DecryptedApproveMember {
    return approve(uuid, Member.Role.ADMINISTRATOR)
  }

  private fun approve(uuid: UUID, role: Member.Role): DecryptedApproveMember {
    return DecryptedApproveMember.Builder()
      .aciBytes(UuidUtil.toByteString(uuid))
      .role(role)
      .build()
  }

  fun member(uuid: UUID, profileKey: ProfileKey): DecryptedMember {
    return withProfileKey(member(uuid), profileKey)
  }

  fun pendingPniAciMember(uuid: UUID, pni: UUID, profileKey: ProfileKey): DecryptedMember {
    return DecryptedMember.Builder()
      .aciBytes(UuidUtil.toByteString(uuid))
      .pniBytes(UuidUtil.toByteString(pni))
      .profileKey(ByteString.of(*profileKey.serialize()))
      .build()
  }

  fun pendingPniAciMember(uuid: ByteString, pni: ByteString, profileKey: ByteString): DecryptedMember {
    return DecryptedMember.Builder()
      .aciBytes(uuid)
      .pniBytes(pni)
      .profileKey(profileKey)
      .build()
  }

  fun admin(uuid: UUID, profileKey: ProfileKey): DecryptedMember {
    return withProfileKey(admin(uuid), profileKey)
  }

  fun admin(uuid: UUID): DecryptedMember {
    return DecryptedMember.Builder()
      .aciBytes(UuidUtil.toByteString(uuid))
      .role(Member.Role.ADMINISTRATOR)
      .build()
  }

  fun withProfileKey(member: DecryptedMember, profileKey: ProfileKey): DecryptedMember {
    return member.newBuilder()
      .profileKey(ByteString.of(*profileKey.serialize()))
      .build()
  }

  fun asAdmin(member: DecryptedMember): DecryptedMember {
    return DecryptedMember.Builder()
      .aciBytes(member.aciBytes)
      .role(Member.Role.ADMINISTRATOR)
      .build()
  }

  fun asMember(member: DecryptedMember): DecryptedMember {
    return DecryptedMember.Builder()
      .aciBytes(member.aciBytes)
      .role(Member.Role.DEFAULT)
      .build()
  }

  fun newProfileKey(): ProfileKey {
    try {
      return ProfileKey(Util.getSecretBytes(32))
    } catch (e: InvalidInputException) {
      throw AssertionError(e)
    }
  }
}
