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
import org.thoughtcrime.securesms.database.CallTable.Direction
import org.thoughtcrime.securesms.database.CallTable.Event
import org.thoughtcrime.securesms.database.CallTable.Type
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.service.webrtc.CallLinkPeekInfo
import org.thoughtcrime.securesms.service.webrtc.SignalCallManager
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class CallEventCacheTest {

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
