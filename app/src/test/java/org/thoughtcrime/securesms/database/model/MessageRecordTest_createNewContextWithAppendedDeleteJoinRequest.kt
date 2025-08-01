package org.thoughtcrime.securesms.database.model

import io.mockk.every
import io.mockk.mockk
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Test
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context
import org.thoughtcrime.securesms.groups.v2.ChangeBuilder
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.internal.push.GroupContextV2
import java.util.UUID
import kotlin.random.Random

@Suppress("ClassName")
class MessageRecordTest_createNewContextWithAppendedDeleteJoinRequest {
  /**
   * Given a non-gv2 message, when I append, then I expect an assertion error
   */
  @Test(expected = AssertionError::class)
  fun throwOnNonGv2() {
    val messageRecord = mockk<MessageRecord> {
      every { decryptedGroupV2Context } returns null
    }

    MessageRecord.createNewContextWithAppendedDeleteJoinRequest(messageRecord, 0, ByteString.EMPTY)
  }

  /**
   * Given a gv2 empty change, when I append, then I expect an assertion error.
   */
  @Test(expected = AssertionError::class)
  fun throwOnEmptyGv2Change() {
    val groupContext = DecryptedGroupV2Context()

    val messageRecord = mockk<MessageRecord> {
      every { decryptedGroupV2Context } returns groupContext
    }

    MessageRecord.createNewContextWithAppendedDeleteJoinRequest(messageRecord, 0, ByteString.EMPTY)
  }

  /**
   * Given a gv2 requesting member change, when I append, then I expect new group context including the change with a new delete.
   */
  @Test
  fun appendDeleteToExistingContext() {
    val alice = ACI.from(UUID.randomUUID())
    val aliceByteString = alice.toByteString()
    val change = ChangeBuilder.changeBy(alice)
      .requestJoin(alice)
      .build()
      .newBuilder()
      .revision(9)
      .build()

    val context = DecryptedGroupV2Context.Builder()
      .context(GroupContextV2.Builder().masterKey(Random.nextBytes(32).toByteString()).build())
      .change(change)
      .build()

    val messageRecord = mockk<MessageRecord> {
      every { decryptedGroupV2Context } returns context
    }

    val newContext = MessageRecord.createNewContextWithAppendedDeleteJoinRequest(messageRecord, 10, aliceByteString)

    assertEquals("revision updated to 10", newContext.change?.revision, 10)
    assertEquals("change should retain join request", newContext.change?.newRequestingMembers?.single()?.aciBytes, aliceByteString)
    assertEquals("change should add delete request", newContext.change?.deleteRequestingMembers?.single(), aliceByteString)
  }
}
