package org.signal.smsexporter.internal.sms

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.signal.core.util.CursorUtil
import org.signal.smsexporter.ExportableMessage
import org.signal.smsexporter.TestUtils

@RunWith(RobolectricTestRunner::class)
class ExportSmsMessagesUseCaseTest {

  @Before
  fun setUp() {
    TestUtils.setUpSmsContentProviderAndResolver()
  }

  @Test
  fun `Given an SMS message, when I execute, then I expect a record to be inserted into the SMS database`() {
    // GIVEN
    val exportableSmsMessage = TestUtils.generateSmsMessage()

    // WHEN
    val result = ExportSmsMessagesUseCase.execute(
      context = ApplicationProvider.getApplicationContext(),
      sms = exportableSmsMessage,
      checkForExistence = false
    )

    // THEN
    result.either(
      onSuccess = {
        validateExportedMessage(exportableSmsMessage)
      },
      onFailure = {
        throw it
      }
    )
  }

  @Test
  fun `Given an SMS message that already exists, when I execute and check for existence, then I expect only a single record to be inserted into the SMS database`() {
    // GIVEN
    val exportableSmsMessage = TestUtils.generateSmsMessage()
    ExportSmsMessagesUseCase.execute(
      context = ApplicationProvider.getApplicationContext(),
      sms = exportableSmsMessage,
      checkForExistence = false
    )

    // WHEN
    val result = ExportSmsMessagesUseCase.execute(
      context = ApplicationProvider.getApplicationContext(),
      sms = exportableSmsMessage,
      checkForExistence = true
    )

    // THEN
    result.either(
      onSuccess = {
        validateExportedMessage(exportableSmsMessage)
      },
      onFailure = {
        throw it
      }
    )
  }

  @Test
  fun `Given an SMS message that already exists, when I execute and do not check for existence, then I expect only a duplicate record to be inserted into the SMS database`() {
    // GIVEN
    val exportableSmsMessage = TestUtils.generateSmsMessage()
    ExportSmsMessagesUseCase.execute(
      context = ApplicationProvider.getApplicationContext(),
      sms = exportableSmsMessage,
      checkForExistence = false
    )

    // WHEN
    val result = ExportSmsMessagesUseCase.execute(
      context = ApplicationProvider.getApplicationContext(),
      sms = exportableSmsMessage,
      checkForExistence = false
    )

    // THEN
    result.either(
      onSuccess = {
        validateExportedMessage(exportableSmsMessage, expectedRowCount = 2)
      },
      onFailure = {
        throw it
      }
    )
  }

  private fun validateExportedMessage(sms: ExportableMessage.Sms<*>, expectedRowCount: Int = 1) {
    // 1. Grab the SMS record from the content resolver
    val context: Context = ApplicationProvider.getApplicationContext()
    val baseUri: Uri = Telephony.Sms.CONTENT_URI

    context.contentResolver.query(
      baseUri,
      null,
      "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.DATE_SENT} = ?",
      arrayOf(sms.address, sms.dateSent.inWholeMilliseconds.toString()),
      null,
      null
    )?.use {
      it.moveToFirst()
      assertEquals(expectedRowCount, it.count)
      assertEquals(sms.address, CursorUtil.requireString(it, Telephony.Sms.ADDRESS))
      assertEquals(sms.dateSent.inWholeMilliseconds, CursorUtil.requireLong(it, Telephony.Sms.DATE_SENT))
      assertEquals(sms.dateReceived.inWholeMilliseconds, CursorUtil.requireLong(it, Telephony.Sms.DATE))
      assertEquals(sms.isRead, CursorUtil.requireBoolean(it, Telephony.Sms.READ))
      assertEquals(sms.body, CursorUtil.requireString(it, Telephony.Sms.BODY))
      assertEquals(if (sms.isOutgoing) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_INBOX, CursorUtil.requireInt(it, Telephony.Sms.TYPE))
    } ?: Assert.fail("Content Resolver returned a null cursor")
  }
}
