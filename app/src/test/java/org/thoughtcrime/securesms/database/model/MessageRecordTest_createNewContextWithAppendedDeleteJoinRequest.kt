package org.thoughtcrime.securesms.database.model

import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.signal.core.util.Base64
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context
import org.thoughtcrime.securesms.groups.v2.ChangeBuilder
import org.whispersystems.signalservice.api.push.ServiceId.ACI
import org.whispersystems.signalservice.internal.push.GroupContextV2
import java.util.Random
import java.util.UUID

@Suppress("ClassName")
class MessageRecordTest_createNewContextWithAppendedDeleteJoinRequest {

  /**
   * Given a non-gv2 message, when I append, then I expect an assertion error
   */
  @Test(expected = AssertionError::class)
  fun throwOnNonGv2() {
    val messageRecord = mock<MessageRecord> {
      on { decryptedGroupV2Context } doReturn null
    }

    MessageRecord.createNewContextWithAppendedDeleteJoinRequest(messageRecord, 0, ByteString.EMPTY)
  }

  /**
   * Given a gv2 empty change, when I append, then I expect an assertion error.
   */
  @Test(expected = AssertionError::class)
  fun throwOnEmptyGv2Change() {
    val groupContext = DecryptedGroupV2Context()

    val messageRecord = mock<MessageRecord> {
      on { decryptedGroupV2Context } doReturn groupContext
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
      .context(GroupContextV2.Builder().masterKey(randomBytes().toByteString()).build())
      .change(change)
      .build()

    val messageRecord = mock<MessageRecord> {
      on { decryptedGroupV2Context } doReturn context
    }

    val newEncodedBody = MessageRecord.createNewContextWithAppendedDeleteJoinRequest(messageRecord, 10, aliceByteString)

    val newContext = DecryptedGroupV2Context.ADAPTER.decode(Base64.decode(newEncodedBody))

    assertThat("revision updated to 10", newContext.change!!.revision, `is`(10))
    assertThat("change should retain join request", newContext.change!!.newRequestingMembers[0].aciBytes, `is`(aliceByteString))
    assertThat("change should add delete request", newContext.change!!.deleteRequestingMembers[0], `is`(aliceByteString))
  }

  private fun randomBytes(): ByteArray {
    val bytes = ByteArray(32)
    Random().nextBytes(bytes)
    return bytes
  }
}
