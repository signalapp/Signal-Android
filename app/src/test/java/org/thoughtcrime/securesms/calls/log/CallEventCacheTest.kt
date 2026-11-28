package org.thoughtcrime.securesms.calls.log

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.size
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import org.junit.Before
import org.junit.Test
import org.signal.core.models.ServiceId.ACI
import org.signal.core.models.recipients.RecipientId
import org.signal.core.util.Base64
import org.thoughtcrime.securesms.database.CallTable.Direction
import org.thoughtcrime.securesms.database.CallTable.Event
import org.thoughtcrime.securesms.database.CallTable.Type
import org.thoughtcrime.securesms.database.model.databaseprotos.GroupCallUpdateDetails
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.service.webrtc.CallLinkPeekInfo
import org.thoughtcrime.securesms.service.webrtc.SignalCallManager
import java.util.UUID
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class CallEventCacheTest {

  companion object {
    private val SELF_UUID: UUID = UUID.fromString("00000000-0000-4000-8000-000000000001")
    private val OTHER_UUID: UUID = UUID.fromString("00000000-0000-4000-8000-000000000002")
  }

  @Before
  fun setUp() {
    mockkObject(Recipient.Companion)
    every { Recipient.resolved(any()) } answers {
      spyk(
        Recipient(
          id = firstArg(),
          isResolving = false
        )
      )
    }

    val signalCallManagerMock: SignalCallManager = mockk()
    every { signalCallManagerMock.peekInfoSnapshot } returns emptyMap()

    mockkStatic(AppDependencies::class)
    every { AppDependencies.signalCallManager } returns signalCallManagerMock
  }

  @Test
  fun `Given no entries, when I clusterCallEvents, then I expect nothing`() {
    val testData = emptyList<CallEventCache.CacheRecord>()

    val filterState = CallEventCache.FilterState()
    val result = CallEventCache.clusterCallEvents(testData, filterState)
    assertThat(result).isEmpty()
  }

  @Test
  fun `Given one entry, when I clusterCallEvents, then I expect one entry`() {
    val testData = listOf(
      createCacheRecord(
        callId = 1
      )
    )

    val filterState = CallEventCache.FilterState()
    val result = CallEventCache.clusterCallEvents(testData, filterState)
    assertThat(result).size().isEqualTo(1)
  }

  @Test
  fun `Given two overlapping entries, when I clusterCallEvents, then I expect one entry`() {
    val testData = listOf(
      createCacheRecord(
        callId = 1
      ),
      createCacheRecord(
        callId = 2
      )
    )

    val filterState = CallEventCache.FilterState()
    val result = CallEventCache.clusterCallEvents(testData, filterState)
    assertThat(result).size().isEqualTo(1)
  }

  @Test
  fun `Given two entries with different peers, when I clusterCallEvents, then I expect two entries`() {
    val testData = listOf(
      createCacheRecord(
        callId = 1,
        peer = 1
      ),
      createCacheRecord(
        callId = 2,
        peer = 2
      )
    )

    val filterState = CallEventCache.FilterState()
    val result = CallEventCache.clusterCallEvents(testData, filterState)
    assertThat(result).size().isEqualTo(2)
  }

  @Test
  fun `Given two entries with different directions, when I clusterCallEvents, then I expect two entries`() {
    val testData = listOf(
      createCacheRecord(
        callId = 1,
        direction = Direction.INCOMING.code
      ),
      createCacheRecord(
        callId = 1,
        direction = Direction.OUTGOING.code
      )
    )

    val filterState = CallEventCache.FilterState()
    val result = CallEventCache.clusterCallEvents(testData, filterState)
    assertThat(result).size().isEqualTo(2)
  }

  @Test
  fun `Given two entries with one missed and one not missed, when I clusterCallEvents, then I expect two entries`() {
    val testData = listOf(
      createCacheRecord(
        callId = 1,
        event = Event.MISSED.code
      ),
      createCacheRecord(
        callId = 2,
        event = Event.ACCEPTED.code
      )
    )

    val filterState = CallEventCache.FilterState()
    val result = CallEventCache.clusterCallEvents(testData, filterState)
    assertThat(result).size().isEqualTo(2)
  }

  @Test
  fun `Given two entries outside of time threshold, when I clusterCallEvents, then I expect two entries`() {
    val testData = listOf(
      createCacheRecord(
        callId = 1,
        timestamp = 0
      ),
      createCacheRecord(
        callId = 2,
        timestamp = 1.days.inWholeMilliseconds
      )
    )

    val filterState = CallEventCache.FilterState()
    val result = CallEventCache.clusterCallEvents(testData, filterState)
    assertThat(result).size().isEqualTo(2)
  }

  @Test
  fun `Given two entries in descending timestamp order outside of time threshold, when I clusterCallEvents, then I expect two entries`() {
    val testData = listOf(
      createCacheRecord(
        callId = 1,
        timestamp = 1.days.inWholeMilliseconds
      ),
      createCacheRecord(
        callId = 2,
        timestamp = 0
      )
    )

    val filterState = CallEventCache.FilterState()
    val result = CallEventCache.clusterCallEvents(testData, filterState)
    assertThat(result).size().isEqualTo(2)
  }

  @Test
  fun `Given two entries in descending timestamp order within time threshold, when I clusterCallEvents, then I expect one entry`() {
    val testData = listOf(
      createCacheRecord(
        callId = 1,
        timestamp = 1.hours.inWholeMilliseconds
      ),
      createCacheRecord(
        callId = 2,
        timestamp = 0
      )
    )

    val filterState = CallEventCache.FilterState()
    val result = CallEventCache.clusterCallEvents(testData, filterState)
    assertThat(result).size().isEqualTo(1)
  }

  @Test
  fun `Given two entries with a mismatch between them, when I clusterCallEvents, then I expect three entries`() {
    val testData = listOf(
      createCacheRecord(
        callId = 1,
        peer = 1
      ),
      createCacheRecord(
        callId = 2,
        peer = 2
      ),
      createCacheRecord(
        callId = 3,
        peer = 1
      )
    )

    val filterState = CallEventCache.FilterState()
    val result = CallEventCache.clusterCallEvents(testData, filterState)
    assertThat(result).size().isEqualTo(3)
  }

  @Test
  fun `Given two call link entries on different links, when I clusterCallEvents, then I expect two entries`() {
    val testData = listOf(
      createCacheRecord(
        callId = 1,
        peer = 1,
        type = Type.AD_HOC_CALL.code
      ),
      createCacheRecord(
        callId = 2,
        peer = 2,
        type = Type.AD_HOC_CALL.code
      )
    )

    val filterState = CallEventCache.FilterState()
    val result = CallEventCache.clusterCallEvents(testData, filterState)
    assertThat(result).size().isEqualTo(2)
  }

  @Test
  fun `Given two calls on the same call link, when I clusterCallEvents, then I expect two entries`() {
    val testData = listOf(
      createCacheRecord(
        callId = 2,
        peer = 1,
        type = Type.AD_HOC_CALL.code
      ),
      createCacheRecord(
        callId = 1,
        peer = 1,
        type = Type.AD_HOC_CALL.code
      )
    )

    val filterState = CallEventCache.FilterState()
    val result = CallEventCache.clusterCallEvents(testData, filterState)
    assertThat(result).size().isEqualTo(2)
  }

  @Test
  fun `Given multiple calls on the same call link, when I clusterCallEvents, then only the newest row keeps peek info`() {
    every { AppDependencies.signalCallManager.peekInfoSnapshot } returns mapOf(
      RecipientId.from(1) to CallLinkPeekInfo(callId = null, isActive = true, isJoined = false)
    )

    val testData = listOf(
      createCacheRecord(
        callId = 3,
        peer = 1,
        type = Type.AD_HOC_CALL.code
      ),
      createCacheRecord(
        callId = 2,
        peer = 1,
        type = Type.AD_HOC_CALL.code
      ),
      createCacheRecord(
        callId = 1,
        peer = 1,
        type = Type.AD_HOC_CALL.code
      )
    )

    val filterState = CallEventCache.FilterState()
    val result = CallEventCache.clusterCallEvents(testData, filterState)

    assertThat(result).size().isEqualTo(3)
    assertThat(result[0].callLinkPeekInfo).isNotNull()
    assertThat(result[1].callLinkPeekInfo).isNull()
    assertThat(result[2].callLinkPeekInfo).isNull()
  }

  @Test
  fun `Given an unjoined call link, when I filter by missed, then I expect nothing`() {
    val testData = listOf(
      createCacheRecord(
        callId = 1,
        type = Type.AD_HOC_CALL.code,
        event = Event.GENERIC_GROUP_CALL.code
      )
    )

    val filterState = CallEventCache.FilterState(filter = CallLogFilter.MISSED)
    val result = CallEventCache.clusterCallEvents(testData, filterState)
    assertThat(result).isEmpty()
  }

  @Test
  fun `Given an unjoined group call, when I filter by missed, then I expect one entry`() {
    val testData = listOf(
      createCacheRecord(
        callId = 1,
        type = Type.GROUP_CALL.code,
        event = Event.GENERIC_GROUP_CALL.code
      )
    )

    val filterState = CallEventCache.FilterState(filter = CallLogFilter.MISSED)
    val result = CallEventCache.clusterCallEvents(testData, filterState)
    assertThat(result).size().isEqualTo(1)
  }

  @Test
  fun `Given a missed call link ring, when I filter by missed, then I expect one entry`() {
    val testData = listOf(
      createCacheRecord(
        callId = 1,
        type = Type.AD_HOC_CALL.code,
        event = Event.MISSED.code
      )
    )

    val filterState = CallEventCache.FilterState(filter = CallLogFilter.MISSED)
    val result = CallEventCache.clusterCallEvents(testData, filterState)
    assertThat(result).size().isEqualTo(1)
  }

  @Test
  fun `Given multiple calls in the same group, when I clusterCallEvents, then only the newest row is active`() {
    givenPeersAreGroups()

    val activeBody = createGroupCallUpdateBody(inCallUuids = listOf(OTHER_UUID.toString()))

    val testData = listOf(
      createCacheRecord(
        callId = 2,
        type = Type.GROUP_CALL.code,
        timestamp = 1.days.inWholeMilliseconds,
        body = activeBody
      ),
      createCacheRecord(
        callId = 1,
        type = Type.GROUP_CALL.code,
        timestamp = 0,
        body = activeBody
      )
    )

    val filterState = CallEventCache.FilterState()
    val result = CallEventCache.clusterCallEvents(testData, filterState)

    assertThat(result).size().isEqualTo(2)
    assertThat(result[0].groupCallState).isEqualTo(CallLogRow.GroupCallState.ACTIVE)
    assertThat(result[1].groupCallState).isEqualTo(CallLogRow.GroupCallState.NONE)
  }

  @Test
  fun `Given an active group call and an older missed call with stale participants, when I filter by missed, then the older row is not active`() {
    givenPeersAreGroups()

    val activeBody = createGroupCallUpdateBody(inCallUuids = listOf(OTHER_UUID.toString()))

    val testData = listOf(
      createCacheRecord(
        callId = 2,
        type = Type.GROUP_CALL.code,
        event = Event.GENERIC_GROUP_CALL.code,
        timestamp = 1.days.inWholeMilliseconds,
        isGroupCallActive = true,
        body = activeBody
      ),
      createCacheRecord(
        callId = 1,
        type = Type.GROUP_CALL.code,
        event = Event.GENERIC_GROUP_CALL.code,
        timestamp = 0,
        body = activeBody
      )
    )

    val filterState = CallEventCache.FilterState(filter = CallLogFilter.MISSED)
    val result = CallEventCache.clusterCallEvents(testData, filterState)

    assertThat(result).size().isEqualTo(1)
    assertThat(result[0].record.callId).isEqualTo(1)
    assertThat(result[0].groupCallState).isEqualTo(CallLogRow.GroupCallState.NONE)
  }

  private fun givenPeersAreGroups() {
    every { Recipient.resolved(any()) } answers {
      spyk(Recipient(id = firstArg(), isResolving = false)).also {
        every { it.isGroup } returns true
      }
    }

    every { Recipient.self() } returns spyk(Recipient(id = RecipientId.from(2), isResolving = false)).also {
      every { it.requireAci() } returns ACI.from(SELF_UUID)
    }
  }

  private fun createGroupCallUpdateBody(inCallUuids: List<String>): String {
    return Base64.encodeWithPadding(
      GroupCallUpdateDetails(
        eraId = "era",
        inCallUuids = inCallUuids
      ).encode()
    )
  }

  private fun createCacheRecord(
    callId: Long,
    peer: Long = 1,
    type: Int = Type.AUDIO_CALL.code,
    direction: Int = Direction.INCOMING.code,
    event: Int = Event.ACCEPTED.code,
    messageId: Long = 0L,
    timestamp: Long = 0L,
    ringerRecipient: Long = 0L,
    isGroupCallActive: Boolean = false,
    didLocalUserJoin: Boolean = false,
    body: String? = null,
    decryptedGroupBytes: ByteArray? = null,
    read: Boolean = true
  ): CallEventCache.CacheRecord {
    return CallEventCache.CacheRecord(
      rowId = callId,
      callId = callId,
      peer = peer,
      type = type,
      direction = direction,
      event = event,
      messageId = messageId,
      timestamp = timestamp,
      ringerRecipient = ringerRecipient,
      isGroupCallActive = isGroupCallActive,
      didLocalUserJoin = didLocalUserJoin,
      body = body,
      decryptedGroupBytes = decryptedGroupBytes,
      read = read
    )
  }
}
