package org.signal.smsexporter.internal.mms

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import androidx.test.core.app.ApplicationProvider
import com.google.android.mms.pdu_alt.PduHeaders
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.signal.core.util.CursorUtil
import org.signal.smsexporter.TestUtils

@RunWith(RobolectricTestRunner::class)
class ExportMmsRecipientsUseCaseTest {

  @Before
  fun setUp() {
    TestUtils.setUpMmsContentProviderAndResolver()
  }

  @Test
  fun `When I export recipient, then I expect a valid exported recipient`() {
    // GIVEN
    val address = "+15065550177"
    val sender = "+15065550123"
    val messageId = 1L

    // WHEN
    val result = ExportMmsRecipientsUseCase.execute(
      ApplicationProvider.getApplicationContext(),
      messageId,
      address,
      sender,
      false
    )

    // THEN
    result.either(
      onSuccess = {
        validateExportedRecipient(address, sender, messageId)
      },
      onFailure = {
        throw it
      }
    )
  }

  @Test
  fun `Given recipient already exported, When I export recipient with check, then I expect a single exported recipient`() {
    // GIVEN
    val address = "+15065550177"
    val sender = "+15065550123"
    val messageId = 1L
    ExportMmsRecipientsUseCase.execute(
      ApplicationProvider.getApplicationContext(),
      messageId,
      address,
      sender,
      false
    )

    // WHEN
    val result = ExportMmsRecipientsUseCase.execute(
      ApplicationProvider.getApplicationContext(),
      messageId,
      address,
      sender,
      true
    )

    // THEN
    result.either(
      onSuccess = {
        validateExportedRecipient(address, sender, messageId)
      },
      onFailure = {
        throw it
      }
    )
  }

  @Test
  fun `Given recipient already exported, When I export recipient with check, then I expect a duplicate exported recipient`() {
    // GIVEN
    val address = "+15065550177"
    val sender = "+15065550123"
    val messageId = 1L
    ExportMmsRecipientsUseCase.execute(
      ApplicationProvider.getApplicationContext(),
      messageId,
      address,
      sender,
      false
    )

    // WHEN
    val result = ExportMmsRecipientsUseCase.execute(
      ApplicationProvider.getApplicationContext(),
      messageId,
      address,
      sender,
      false
    )

    // THEN
    result.either(
      onSuccess = {
        validateExportedRecipient(address, sender, messageId, expectedRowCount = 2)
      },
      onFailure = {
        throw it
      }
    )
  }

  private fun validateExportedRecipient(address: String, sender: String, messageId: Long, expectedRowCount: Int = 1) {
    val context: Context = ApplicationProvider.getApplicationContext()
    val baseUri: Uri = Telephony.Mms.CONTENT_URI.buildUpon().appendPath(messageId.toString()).appendPath("addr").build()

    context.contentResolver.query(
      baseUri,
      null,
      "${Telephony.Mms.Addr.ADDRESS} = ?",
      arrayOf(address),
      null,
      null
    )?.use {
      it.moveToFirst()
      assertEquals(expectedRowCount, it.count)
      assertEquals(address, CursorUtil.requireString(it, Telephony.Mms.Addr.ADDRESS))
      assertEquals(if (address == sender) PduHeaders.FROM else PduHeaders.TO, CursorUtil.requireInt(it, Telephony.Mms.Addr.TYPE))
    } ?: fail("Content Resolver returned a null cursor")
  }
}
