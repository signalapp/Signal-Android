package org.signal.smsexporter.internal.mms

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.signal.core.util.CursorUtil
import org.signal.smsexporter.ExportableMessage
import org.signal.smsexporter.TestUtils

@RunWith(RobolectricTestRunner::class)
class ExportMmsMessagesUseCaseTest {

  @Before
  fun setUp() {
    TestUtils.setUpMmsContentProviderAndResolver()
  }

  @Test
  fun `Given an MMS message, when I execute, then I expect an MMS record to be created`() {
    // GIVEN
    val mmsMessage = TestUtils.generateMmsMessage()
    val threadUseCaseOutput = GetOrCreateMmsThreadIdsUseCase.Output(mmsMessage, 1)

    // WHEN
    val result = ExportMmsMessagesUseCase.execute(
      ApplicationProvider.getApplicationContext(),
      threadUseCaseOutput,
      false
    )

    // THEN
    result.either(
      onSuccess = {
        validateExportedMessage(mmsMessage)
      },
      onFailure = {
        throw it
      }
    )
  }

  @Test
  fun `Given an MMS message that already exists, when I execute and check for existence, then I expect no new MMS record to be created`() {
    // GIVEN
    val mmsMessage = TestUtils.generateMmsMessage()
    val threadUseCaseOutput = GetOrCreateMmsThreadIdsUseCase.Output(mmsMessage, 1)
    ExportMmsMessagesUseCase.execute(
      ApplicationProvider.getApplicationContext(),
      threadUseCaseOutput,
      false
    )

    // WHEN
    val result = ExportMmsMessagesUseCase.execute(
      ApplicationProvider.getApplicationContext(),
      threadUseCaseOutput,
      true
    )

    // THEN
    result.either(
      onSuccess = {
        validateExportedMessage(mmsMessage)
      },
      onFailure = {
        throw it
      }
    )
  }

  @Test
  fun `Given an MMS message that already exists, when I execute and do not check for existence, then I expect a duplicate MMS record to be created`() {
    // GIVEN
    val mmsMessage = TestUtils.generateMmsMessage()
    val threadUseCaseOutput = GetOrCreateMmsThreadIdsUseCase.Output(mmsMessage, 1)
    ExportMmsMessagesUseCase.execute(
      ApplicationProvider.getApplicationContext(),
      threadUseCaseOutput,
      false
    )

    // WHEN
    val result = ExportMmsMessagesUseCase.execute(
      ApplicationProvider.getApplicationContext(),
      threadUseCaseOutput,
      false
    )

    // THEN
    result.either(
      onSuccess = {
        validateExportedMessage(mmsMessage, expectedRowCount = 2)
      },
      onFailure = {
        throw it
      }
    )
  }

  private fun validateExportedMessage(
    mms: ExportableMessage.Mms<*>,
    expectedRowCount: Int = 1,
    threadId: Long = 1L
  ) {
    val context: Context = ApplicationProvider.getApplicationContext()
    val baseUri: Uri = Telephony.Mms.CONTENT_URI
    val transactionId = ExportMmsMessagesUseCase.getTransactionId(mms)

    context.contentResolver.query(
      baseUri,
      null,
      "${Telephony.Mms.TRANSACTION_ID} = ?",
      arrayOf(transactionId),
      null,
      null
    )?.use {
      it.moveToFirst()
      assertEquals(expectedRowCount, it.count)
      assertEquals(threadId, CursorUtil.requireLong(it, Telephony.Mms.THREAD_ID))
      assertEquals(mms.dateReceived.inWholeSeconds, CursorUtil.requireLong(it, Telephony.Mms.DATE))
      assertEquals(mms.dateSent.inWholeSeconds, CursorUtil.requireLong(it, Telephony.Mms.DATE_SENT))
      assertEquals(if (mms.isOutgoing) Telephony.Mms.MESSAGE_BOX_SENT else Telephony.Mms.MESSAGE_BOX_INBOX, CursorUtil.requireInt(it, Telephony.Mms.MESSAGE_BOX))
      assertEquals(mms.isRead, CursorUtil.requireBoolean(it, Telephony.Mms.READ))
      assertEquals(transactionId, CursorUtil.requireString(it, Telephony.Mms.TRANSACTION_ID))
    } ?: org.junit.Assert.fail("Content Resolver returned a null cursor")
  }
}
