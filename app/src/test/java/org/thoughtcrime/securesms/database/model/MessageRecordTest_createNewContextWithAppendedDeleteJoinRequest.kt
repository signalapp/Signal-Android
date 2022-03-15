package org.thoughtcrime.securesms.database.model

import com.google.protobuf.ByteString
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context
import org.thoughtcrime.securesms.groups.v2.ChangeBuilder
import org.thoughtcrime.securesms.util.Base64
import org.whispersystems.signalservice.api.util.UuidUtil
import org.whispersystems.signalservice.internal.push.SignalServiceProtos
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
    val groupContext = DecryptedGroupV2Context.getDefaultInstance()

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
    val alice = UUID.randomUUID()
    val aliceByteString = UuidUtil.toByteString(alice)
    val change = ChangeBuilder.changeBy(alice)
      .requestJoin(alice)
      .build()
      .toBuilder()
      .setRevision(9)
      .build()

    val context = DecryptedGroupV2Context.newBuilder()
      .setContext(SignalServiceProtos.GroupContextV2.newBuilder().setMasterKey(ByteString.copyFrom(randomBytes())))
      .setChange(change)
      .build()

    val messageRecord = mock<MessageRecord> {
      on { decryptedGroupV2Context } doReturn context
    }

    val newEncodedBody = MessageRecord.createNewContextWithAppendedDeleteJoinRequest(messageRecord, 10, aliceByteString)

    val newContext = DecryptedGroupV2Context.parseFrom(Base64.decode(newEncodedBody))

    assertThat("revision updated to 10", newContext.change.revision, `is`(10))
    assertThat("change should retain join request", newContext.change.newRequestingMembersList[0].uuid, `is`(aliceByteString))
    assertThat("change should add delete request", newContext.change.deleteRequestingMembersList[0], `is`(aliceByteString))
  }

  private fun randomBytes(): ByteArray {
    val bytes = ByteArray(32)
    Random().nextBytes(bytes)
    return bytes
  }
}
