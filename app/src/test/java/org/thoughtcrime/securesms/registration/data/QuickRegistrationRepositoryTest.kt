/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.data

import android.app.Application
import android.net.Uri
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.Base64
import org.signal.libsignal.protocol.InvalidKeyException

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class QuickRegistrationRepositoryTest {

  companion object {
    private const val STANDARD_B64 = "BdDF6+/UPrbtdNGGRtLqDie8khmxlEEMrN8t/mNF1m5V"
    private const val URL_SAFE_B64 = "BdDF6-_UPrbtdNGGRtLqDie8khmxlEEMrN8t_mNF1m5V"
    private const val STANDARD_B64_PERCENT_ENCODED = "BdDF6%2B%2FUPrbtdNGGRtLqDie8khmxlEEMrN8t%2FmNF1m5V"
    private const val EPHEMERAL_ID_PERCENT_ENCODED = "nw_QVJRauc7Y-g39lB_3fA%3D%3D"

    private val EXPECTED_KEY: ByteArray = Base64.decode(STANDARD_B64)
  }

  @Test
  fun `standard base64 percent-encoded - parses`() {
    val data = "sgnl://rereg?uuid=$EPHEMERAL_ID_PERCENT_ENCODED&pub_key=$STANDARD_B64_PERCENT_ENCODED&capabilities="

    assertTrue(QuickRegistrationRepository.isValidReRegistrationQr(data))
    assertArrayEquals(EXPECTED_KEY, QuickRegistrationRepository.parsePublicKey(Uri.parse(data))!!.serialize())
  }

  @Test
  fun `url-safe base64 unescaped - parses`() {
    val data = "sgnl://rereg?uuid=$EPHEMERAL_ID_PERCENT_ENCODED&pub_key=$URL_SAFE_B64&capabilities="

    assertTrue(QuickRegistrationRepository.isValidReRegistrationQr(data))
    assertArrayEquals(EXPECTED_KEY, QuickRegistrationRepository.parsePublicKey(Uri.parse(data))!!.serialize())
  }

  @Test
  fun `url-safe base64 with percent-encoded padding - parses`() {
    val bytes = ByteArray(33) { if (it == 0) 0x05 else it.toByte() }
    val encoded = Base64.encodeUrlSafeWithPadding(bytes).replace("=", "%3D")
    val data = "sgnl://rereg?uuid=$EPHEMERAL_ID_PERCENT_ENCODED&pub_key=$encoded"

    assertArrayEquals(bytes, QuickRegistrationRepository.parsePublicKey(Uri.parse(data))!!.serialize())
  }

  @Test
  fun `standard base64 with unescaped plus - does not parse`() {
    val data = "sgnl://rereg?uuid=$EPHEMERAL_ID_PERCENT_ENCODED&pub_key=$STANDARD_B64&capabilities="

    val key = try {
      QuickRegistrationRepository.parsePublicKey(Uri.parse(data))?.serialize()
    } catch (e: InvalidKeyException) {
      null
    }

    assertFalse(EXPECTED_KEY.contentEquals(key))
  }

  @Test
  fun `missing pub_key - returns null`() {
    val data = "sgnl://rereg?uuid=$EPHEMERAL_ID_PERCENT_ENCODED"

    assertFalse(QuickRegistrationRepository.isValidReRegistrationQr(data))
    assertNull(QuickRegistrationRepository.parsePublicKey(Uri.parse(data)))
  }
}
