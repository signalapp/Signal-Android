package org.whispersystems.signalservice.api.groupsv2

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.single
import org.junit.Before
import org.junit.Test
import org.signal.libsignal.zkgroup.groups.GroupMasterKey
import org.signal.libsignal.zkgroup.groups.GroupSecretParams
import org.whispersystems.signalservice.api.groupsv2.GroupsV2Operations.GroupOperations
import org.whispersystems.signalservice.api.push.ServiceId
import org.whispersystems.signalservice.internal.util.Util
import org.whispersystems.signalservice.testutil.LibSignalLibraryUtil
import java.util.UUID

@Suppress("ClassName")
class GroupsV2Operations_ban_Test {
  private lateinit var groupOperations: GroupOperations

  @Before
  fun setup() {
    LibSignalLibraryUtil.assumeLibSignalSupportedOnOS()

    val server = TestZkGroupServer()
    val groupSecretParams = GroupSecretParams.deriveFromMasterKey(GroupMasterKey(Util.getSecretBytes(32)))
    val clientZkOperations = ClientZkOperations(server.serverPublicParams)

    groupOperations = GroupsV2Operations(clientZkOperations, 10).forGroup(groupSecretParams)
  }

  @Test
  fun addBanToEmptyList() {
    val ban = randomACI()

    val banUuidsChange = groupOperations.createBanServiceIdsChange(
      /* banServiceIds = */
      setOf(ban),
      /* rejectJoinRequest = */
      false,
      /* bannedMembersList = */
      emptyList()
    )

    assertThat(banUuidsChange.addBannedMembers)
      .single()
      .transform { it.added?.userId }
      .isEqualTo(groupOperations.encryptServiceId(ban))
  }

  @Test
  fun addBanToPartialFullList() {
    val toBan = randomACI()
    val alreadyBanned = (0 until 5).map { ProtoTestUtils.bannedMember(UUID.randomUUID()) }

    val banUuidsChange = groupOperations.createBanServiceIdsChange(
      /* banServiceIds = */
      setOf(toBan),
      /* rejectJoinRequest = */
      false,
      /* bannedMembersList = */
      alreadyBanned
    )

    assertThat(banUuidsChange.addBannedMembers)
      .single()
      .transform { it.added?.userId }
      .isEqualTo(groupOperations.encryptServiceId(toBan))
  }

  @Test
  fun addBanToFullList() {
    val toBan = ServiceId.ACI.from(UUID.randomUUID())

    val alreadyBanned = (0 until 10).map { i ->
      ProtoTestUtils.bannedMember(UUID.randomUUID())
        .newBuilder()
        .timestamp(100L + i)
        .build()
    }.shuffled()

    val banUuidsChange = groupOperations.createBanServiceIdsChange(
      /* banServiceIds = */
      setOf(toBan),
      /* rejectJoinRequest = */
      false,
      /* bannedMembersList = */
      alreadyBanned
    )

    val oldest = alreadyBanned.minBy { it.timestamp }
    assertThat(banUuidsChange.deleteBannedMembers)
      .single()
      .transform { it.deletedUserId }
      .isEqualTo(groupOperations.encryptServiceId(ServiceId.parseOrThrow(oldest.serviceIdBytes)))

    assertThat(banUuidsChange.addBannedMembers)
      .single()
      .transform { it.added?.userId }
      .isEqualTo(groupOperations.encryptServiceId(toBan))
  }

  @Test
  fun addMultipleBanToFullList() {
    val toBan = (0 until 2).map { ServiceId.ACI.from(UUID.randomUUID()) }

    val alreadyBanned = (0 until 10).map { i ->
      ProtoTestUtils.bannedMember(UUID.randomUUID())
        .newBuilder()
        .timestamp(100L + i)
        .build()
    }.shuffled()

    val banUuidsChange = groupOperations.createBanServiceIdsChange(
      /* banServiceIds = */
      toBan.toMutableSet(),
      /* rejectJoinRequest = */
      false,
      /* bannedMembersList = */
      alreadyBanned
    )

    val oldestTwo = alreadyBanned
      .sortedBy { it.timestamp }
      .subList(0, 2)
      .map { groupOperations.encryptServiceId(ServiceId.parseOrThrow(it.serviceIdBytes)) }
      .toTypedArray()
    assertThat(banUuidsChange.deleteBannedMembers)
      .transform { members ->
        members.map { member ->
          member.deletedUserId
        }
      }
      .containsExactly(*oldestTwo)

    val newBans = (0..1).map { i ->
      groupOperations.encryptServiceId(toBan[i])
    }.toTypedArray()
    assertThat(banUuidsChange.addBannedMembers)
      .transform { members ->
        members.map { member ->
          member.added?.userId
        }
      }
      .containsExactly(*newBans)
  }

  private fun randomACI(): ServiceId.ACI = ServiceId.ACI.from(UUID.randomUUID())
}
