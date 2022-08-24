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
class ExportMmsPartsUseCaseTest {

  @Before
  fun setUp() {
    TestUtils.setUpMmsContentProviderAndResolver()
  }

  @Test
  fun `Given a message with a part, when I export part, then I expect a valid part row`() {
    // GIVEN
    val message = TestUtils.generateMmsMessage()
    val output = ExportMmsMessagesUseCase.Output(message, 1)

    // WHEN
    val result = ExportMmsPartsUseCase.execute(
      ApplicationProvider.getApplicationContext(),
      message.parts.first(),
      output,
      false
    )

    // THEN
    result.either(
      onSuccess = {
        validateExportedPart(message.parts.first(), output.messageId)
      },
      onFailure = {
        throw it
      }
    )
  }

  @Test
  fun `Given an already exported part, when I export part with check, then I expect a single part row`() {
    // GIVEN
    val message = TestUtils.generateMmsMessage()
    val output = ExportMmsMessagesUseCase.Output(message, 1)
    ExportMmsPartsUseCase.execute(
      ApplicationProvider.getApplicationContext(),
      message.parts.first(),
      output,
      false
    )

    // WHEN
    val result = ExportMmsPartsUseCase.execute(
      ApplicationProvider.getApplicationContext(),
      message.parts.first(),
      output,
      true
    )

    // THEN
    result.either(
      onSuccess = {
        validateExportedPart(message.parts.first(), output.messageId)
      },
      onFailure = {
        throw it
      }
    )
  }

  @Test
  fun `Given an already exported part, when I export part without check, then I expect a duplicated part row`() {
    // GIVEN
    val message = TestUtils.generateMmsMessage()
    val output = ExportMmsMessagesUseCase.Output(message, 1)
    ExportMmsPartsUseCase.execute(
      ApplicationProvider.getApplicationContext(),
      message.parts.first(),
      output,
      false
    )

    // WHEN
    val result = ExportMmsPartsUseCase.execute(
      ApplicationProvider.getApplicationContext(),
      message.parts.first(),
      output,
      false
    )

    // THEN
    result.either(
      onSuccess = {
        validateExportedPart(message.parts.first(), output.messageId, expectedRowCount = 2)
      },
      onFailure = {
        throw it
      }
    )
  }

  private fun validateExportedPart(
    part: ExportableMessage.Mms.Part,
    messageId: Long,
    expectedRowCount: Int = 1
  ) {
    val context: Context = ApplicationProvider.getApplicationContext()
    val baseUri: Uri = Telephony.Mms.CONTENT_URI.buildUpon().appendPath("part").build()
    val contentId = ExportMmsPartsUseCase.getContentId(part)

    context.contentResolver.query(
      baseUri,
      null,
      "${Telephony.Mms.Part.CONTENT_ID} = ?",
      arrayOf(contentId),
      null,
      null
    )?.use {
      it.moveToFirst()
      assertEquals(expectedRowCount, it.count)
      assertEquals(part.contentType, CursorUtil.requireString(it, Telephony.Mms.Part.CONTENT_TYPE))
      assertEquals(contentId, CursorUtil.requireString(it, Telephony.Mms.Part.CONTENT_ID))
      assertEquals(messageId, CursorUtil.requireLong(it, Telephony.Mms.Part.MSG_ID))
      assertEquals(if (part is ExportableMessage.Mms.Part.Text) part.text else null, CursorUtil.requireString(it, Telephony.Mms.Part.TEXT))
    } ?: org.junit.Assert.fail("Content Resolver returned a null cursor")
  }
}
