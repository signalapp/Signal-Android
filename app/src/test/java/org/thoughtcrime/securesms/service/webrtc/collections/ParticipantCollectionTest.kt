package org.thoughtcrime.securesms.service.webrtc.collections

import assertk.Assert
import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.junit.Test
import org.thoughtcrime.securesms.components.webrtc.BroadcastVideoSink
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.events.CallParticipant.Companion.createRemote
import org.thoughtcrime.securesms.events.CallParticipantId
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

class ParticipantCollectionTest {
  private val testSubject = ParticipantCollection(3)

  @Test
  fun givenAnEmptyCollection_whenIAdd3Participants_thenIExpectThemToBeOrderedByAddedToCallTime() {
    // GIVEN
    val input = listOf(
      participant(1, 1, 4),
      participant(2, 1, 2),
      participant(3, 1, 3)
    )

    // WHEN
    val result = testSubject.getNext(input)

    // THEN
    assertThat(result.gridParticipants).containsParticipantIds(2, 3, 1)
  }

  @Test
  fun givenAnEmptyCollection_whenIAdd3Participants_thenIExpectNoListParticipants() {
    // GIVEN
    val input = listOf(
      participant(1, 1, 4),
      participant(2, 1, 2),
      participant(3, 1, 3)
    )

    // WHEN
    val result = testSubject.getNext(input)

    // THEN
    assertThat(result.listParticipants).isEmpty()
  }

  @Test
  fun givenAnEmptyColletion_whenIAdd4Participants_thenIExpectThemToBeOrderedByLastSpokenThenAddedToCallTime() {
    // GIVEN
    val input = listOf(
      participant(1, 1, 2),
      participant(2, 5, 2),
      participant(3, 1, 1),
      participant(4, 1, 0)
    )

    // WHEN
    val result = testSubject.getNext(input)

    // THEN
    assertThat(result.gridParticipants).containsParticipantIds(2, 4, 3)
  }

  @Test
  fun givenACollection_whenIUpdateWithEmptyList_thenIExpectEmptyList() {
    // GIVEN
    val initial = listOf(
      participant(1, 1, 2),
      participant(2, 1, 3),
      participant(3, 1, 4)
    )
    val initialCollection = testSubject.getNext(initial)
    val next = emptyList<CallParticipant>()

    // WHEN
    val result = initialCollection.getNext(next)

    // THEN
    assertThat(result.gridParticipants).isEmpty()
  }

  @Test
  fun givenACollection_whenIUpdateWithLatestSpeakerAndSpeakerIsAlreadyInGridSection_thenIExpectTheSameGridSectionOrder() {
    // GIVEN
    val initial = listOf(
      participant(1, 1, 2),
      participant(2, 1, 3),
      participant(3, 1, 4)
    )
    val initialCollection = testSubject.getNext(initial)
    val next = listOf(
      participant(1, 1, 2),
      participant(2, 2, 3),
      participant(3, 1, 4)
    )

    // WHEN
    val result = initialCollection.getNext(next)

    // THEN
    assertThat(result.gridParticipants).containsParticipantIds(1, 2, 3)
  }

  @Test
  fun givenACollection_whenSomeoneLeaves_thenIDoNotExpectToSeeThemInTheNewList() {
    // GIVEN
    val initial = listOf(
      participant(1, 1, 2),
      participant(2, 1, 3),
      participant(3, 1, 4)
    )
    val initialCollection = testSubject.getNext(initial)
    val next = listOf(
      participant(2, 2, 3),
      participant(3, 1, 4)
    )

    // WHEN
    val result = initialCollection.getNext(next)

    // THEN
    assertThat(result.gridParticipants).containsParticipantIds(2, 3)
  }

  @Test
  fun givenACollection_whenMultipleLeave_thenIDoNotExpectToSeeThemInTheNewList() {
    // GIVEN
    val testSubject = ParticipantCollection(4)
    val initial = listOf(
      participant(1, 1, 2),
      participant(2, 1, 3),
      participant(3, 1, 4),
      participant(4, 1, 5)
    )
    val initialCollection = testSubject.getNext(initial)
    val next = listOf(
      participant(3, 1, 4),
      participant(2, 1, 3)
    )

    // WHEN
    val result = initialCollection.getNext(next)

    // THEN
    assertThat(result.gridParticipants).containsParticipantIds(2, 3)
  }

  @Test
  fun bigTest() {
    // Welcome to the Thunder dome. 10 people enter...

    val testSubject = ParticipantCollection(6)
    val init = listOf(
      participant(1, 1, 1), // Alice
      participant(2, 1, 1), // Bob
      participant(3, 1, 1), // Charlie
      participant(4, 1, 1), // Diane
      participant(5, 1, 1), // Ethel
      participant(6, 1, 1), // Francis
      participant(7, 1, 1), // Georgina
      participant(8, 1, 1), // Henry
      participant(9, 1, 1), // Ignace
      participant(10, 1, 1) // Jericho
    )

    val initialCollection = testSubject.getNext(init)

    assertThat(initialCollection.gridParticipants).containsParticipantIds(1, 2, 3, 4, 5, 6)
    assertThat(initialCollection.listParticipants).containsParticipantIds(7, 8, 9, 10)

    // Bob speaks about his trip to antigua...
    val bobSpoke = listOf(
      participant(1, 1, 1),
      participant(2, 2, 1),
      participant(3, 1, 1),
      participant(4, 1, 1),
      participant(5, 1, 1),
      participant(6, 1, 1),
      participant(7, 1, 1),
      participant(8, 1, 1),
      participant(9, 1, 1),
      participant(10, 1, 1)
    )

    val afterBobSpoke = initialCollection.getNext(bobSpoke)

    assertThat(afterBobSpoke.gridParticipants).containsParticipantIds(1, 2, 3, 4, 5, 6)
    assertThat(afterBobSpoke.listParticipants).containsParticipantIds(7, 8, 9, 10)

    // Henry interjects and says now is not the time, this is the thunderdome.
    val henrySpoke = listOf(
      participant(1, 1, 1),
      participant(2, 2, 1),
      participant(3, 1, 1),
      participant(4, 1, 1),
      participant(5, 1, 1),
      participant(6, 1, 1),
      participant(7, 1, 1),
      participant(8, 3, 1),
      participant(9, 1, 1),
      participant(10, 1, 1)
    )

    val afterHenrySpoke = afterBobSpoke.getNext(henrySpoke)

    assertThat(afterHenrySpoke.gridParticipants).containsParticipantIds(1, 2, 3, 4, 5, 8)
    assertThat(afterHenrySpoke.listParticipants).containsParticipantIds(6, 7, 9, 10)

    // Ignace asks how everyone's holidays were
    val ignaceSpoke = listOf(
      participant(1, 1, 1),
      participant(2, 2, 1),
      participant(3, 1, 1),
      participant(4, 1, 1),
      participant(5, 1, 1),
      participant(6, 1, 1),
      participant(7, 1, 1),
      participant(8, 3, 1),
      participant(9, 4, 1),
      participant(10, 1, 1)
    )

    val afterIgnaceSpoke = afterHenrySpoke.getNext(ignaceSpoke)

    assertThat(afterIgnaceSpoke.gridParticipants).containsParticipantIds(1, 2, 3, 4, 9, 8)
    assertThat(afterIgnaceSpoke.listParticipants).containsParticipantIds(5, 6, 7, 10)

    // Alice is the first to fall
    val aliceLeft = listOf(
      participant(2, 2, 1),
      participant(3, 1, 1),
      participant(4, 1, 1),
      participant(5, 1, 1),
      participant(6, 1, 1),
      participant(7, 1, 1),
      participant(8, 3, 1),
      participant(9, 4, 1),
      participant(10, 1, 1)
    )

    val afterAliceLeft = afterIgnaceSpoke.getNext(aliceLeft)

    assertThat(afterAliceLeft.gridParticipants).containsParticipantIds(5, 2, 3, 4, 9, 8)
    assertThat(afterAliceLeft.listParticipants).containsParticipantIds(6, 7, 10)

    // Just kidding, Alice is back. Georgina and Charlie gasp!
    val mixUp = listOf(
      participant(1, 1, 5),
      participant(2, 2, 1),
      participant(3, 6, 1),
      participant(4, 1, 1),
      participant(5, 1, 1),
      participant(6, 1, 1),
      participant(7, 5, 1),
      participant(8, 3, 1),
      participant(9, 4, 1),
      participant(10, 1, 1)
    )

    val afterMixUp = afterAliceLeft.getNext(mixUp)

    assertThat(afterMixUp.gridParticipants).containsParticipantIds(7, 2, 3, 4, 9, 8)
    assertThat(afterMixUp.listParticipants).containsParticipantIds(5, 6, 10, 1)
  }

  companion object {
    private fun Assert<List<CallParticipant>>.containsParticipantIds(vararg expectedParticipantIds: Long) {
      transform("Same sizes") { it.size }.isEqualTo(expectedParticipantIds.size)

      transform { it.zip(expectedParticipantIds.asList()) }
        .each { assertionPair ->
          assertionPair.transform { (actualCallParticipant, expectedParticipantId) ->
            assertk.assertThat(actualCallParticipant.callParticipantId)
              .isEqualTo(CallParticipantId(expectedParticipantId, RecipientId.from(expectedParticipantId)))
          }
        }
    }

    private fun participant(serializedId: Long, lastSpoke: Long, added: Long): CallParticipant {
      return createRemote(
        callParticipantId = CallParticipantId(serializedId, RecipientId.from(serializedId)),
        recipient = Recipient.UNKNOWN,
        identityKey = null,
        renderer = BroadcastVideoSink(),
        isForwardingVideo = false,
        audioEnabled = false,
        videoEnabled = false,
        handRaisedTimestamp = CallParticipant.HAND_LOWERED,
        lastSpoke = lastSpoke,
        mediaKeysReceived = false,
        addedToCallTime = added,
        isScreenSharing = false,
        deviceOrdinal = CallParticipant.DeviceOrdinal.PRIMARY
      )
    }
  }
}
