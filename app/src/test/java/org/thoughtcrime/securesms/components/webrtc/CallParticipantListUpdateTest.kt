package org.thoughtcrime.securesms.components.webrtc

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.events.CallParticipant.Companion.createRemote
import org.thoughtcrime.securesms.events.CallParticipant.DeviceOrdinal
import org.thoughtcrime.securesms.events.CallParticipantId
import org.thoughtcrime.securesms.recipients.RecipientCreator.forId
import org.thoughtcrime.securesms.recipients.RecipientId

class CallParticipantListUpdateTest {
  @Test
  fun givenEmptySets_thenExpectNoChanges() {
    // GIVEN
    val added = emptySet<CallParticipantListUpdate.Wrapper>()
    val removed = emptySet<CallParticipantListUpdate.Wrapper>()
    val update = CallParticipantListUpdate(added, removed)

    // THEN
    assertThat(update.hasNoChanges()).isTrue()
    assertThat(update.hasSingleChange()).isFalse()
  }

  @Test
  fun givenOneEmptySet_thenExpectMultipleChanges() {
    // GIVEN
    val added = createWrappers(1, 2, 3).toSet()
    val removed = emptySet<CallParticipantListUpdate.Wrapper>()
    val update = CallParticipantListUpdate(added, removed)

    // THEN
    assertThat(update.hasNoChanges()).isFalse()
    assertThat(update.hasSingleChange()).isFalse()
  }

  @Test
  fun givenNoEmptySets_thenExpectMultipleChanges() {
    // GIVEN
    val added = createWrappers(1, 2, 3).toSet()
    val removed = createWrappers(4, 5, 6).toSet()
    val update = CallParticipantListUpdate(added, removed)

    // THEN
    assertThat(update.hasNoChanges()).isFalse()
    assertThat(update.hasSingleChange()).isFalse()
  }

  @Test
  fun givenOneSetWithSingleItemAndAnEmptySet_thenExpectSingleChange() {
    // GIVEN
    val added = createWrappers(1).toSet()
    val removed = emptySet<CallParticipantListUpdate.Wrapper>()
    val update = CallParticipantListUpdate(added, removed)

    // THEN
    assertThat(update.hasNoChanges()).isFalse()
    assertThat(update.hasSingleChange()).isTrue()
  }

  @Test
  fun whenFirstListIsAdded_thenIExpectAnUpdateWithAllItemsFromListAdded() {
    // GIVEN
    val newList = createParticipants(1, 2, 3, 4, 5)

    // WHEN
    val update = CallParticipantListUpdate.computeDeltaUpdate(emptyList(), newList)

    // THEN
    assertThat(update.hasNoChanges()).isFalse()
    assertThat(update.removed.isEmpty()).isTrue()
    assertThat(update.added).containsExactlyInAnyOrder(*createWrappers(1, 2, 3, 4, 5))
  }

  @Test
  fun whenSameListIsAddedTwiceInARowWithinTimeout_thenIExpectAnEmptyUpdate() {
    // GIVEN
    val newList = createParticipants(1, 2, 3, 4, 5)

    // WHEN
    val update = CallParticipantListUpdate.computeDeltaUpdate(newList, newList)

    // THEN
    assertThat(update.hasNoChanges()).isTrue()
  }

  @Test
  fun whenPlaceholdersAreUsed_thenIExpectAnEmptyUpdate() {
    // GIVEN
    val newList = createPlaceholderParticipants(1, 2, 3, 4, 5)

    // WHEN
    val update = CallParticipantListUpdate.computeDeltaUpdate(emptyList(), newList)

    // THEN
    assertThat(update.hasNoChanges()).isTrue()
  }

  @Test
  fun whenNewListIsAdded_thenIExpectAReducedUpdate() {
    // GIVEN
    val list1 = createParticipants(1, 2, 3, 4, 5)
    val list2 = createParticipants(2, 3, 4, 5, 6)

    // WHEN
    val update = CallParticipantListUpdate.computeDeltaUpdate(list1, list2)

    // THEN
    assertThat(update.hasNoChanges()).isFalse()
    assertThat(update.added).containsExactlyInAnyOrder(*createWrappers(6))
    assertThat(update.removed).containsExactlyInAnyOrder(*createWrappers(1))
  }

  @Test
  fun whenRecipientExistsMultipleTimes_thenIExpectOneInstancePrimaryAndOthersSecondary() {
    // GIVEN
    val list = createParticipants(longArrayOf(1, 1, 1), longArrayOf(1, 2, 3))

    // WHEN
    val update = CallParticipantListUpdate.computeDeltaUpdate(emptyList(), list)

    // THEN
    val isPrimaryList = update.added.map { it.callParticipant.isPrimary }.toList()
    assertThat(isPrimaryList).containsExactlyInAnyOrder(true, false, false)
  }

  companion object {
    internal fun createWrappers(vararg recipientIds: Long): Array<CallParticipantListUpdate.Wrapper?> {
      val ids = arrayOfNulls<CallParticipantListUpdate.Wrapper>(recipientIds.size)

      for (i in recipientIds.indices) {
        val participant = createParticipant(recipientIds[i], recipientIds[i], DeviceOrdinal.PRIMARY)

        ids[i] = CallParticipantListUpdate.createWrapper(participant)
      }

      return ids
    }

    private fun createPlaceholderParticipants(
      @Suppress("SameParameterValue") vararg recipientIds: Long
    ): List<CallParticipant> {
      val deMuxIds = LongArray(recipientIds.size) { -1 }
      return createParticipants(recipientIds, deMuxIds)
    }

    private fun createParticipants(vararg recipientIds: Long): List<CallParticipant> {
      return createParticipants(recipientIds, recipientIds)
    }

    private fun createParticipants(recipientIds: LongArray, placeholderIds: LongArray): List<CallParticipant> {
      val participants = mutableListOf<CallParticipant>()
      val primaries = mutableSetOf<Long>()

      for (i in recipientIds.indices) {
        participants.add(createParticipant(recipientIds[i], placeholderIds[i], if (primaries.contains(recipientIds[i])) DeviceOrdinal.SECONDARY else DeviceOrdinal.PRIMARY))
        primaries.add(recipientIds[i])
      }

      return participants
    }

    private fun createParticipant(recipientId: Long, deMuxId: Long, deviceOrdinal: DeviceOrdinal): CallParticipant {
      val recipient = forId(RecipientId.from(recipientId), true)

      return createRemote(
        callParticipantId = CallParticipantId(deMuxId, recipient.id),
        recipient = recipient,
        identityKey = null,
        renderer = BroadcastVideoSink(),
        isForwardingVideo = false,
        audioEnabled = false,
        videoEnabled = false,
        handRaisedTimestamp = CallParticipant.HAND_LOWERED,
        lastSpoke = -1,
        mediaKeysReceived = false,
        addedToCallTime = 0,
        isScreenSharing = false,
        deviceOrdinal = deviceOrdinal
      )
    }
  }
}
