/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend.v3

import kotlinx.coroutines.rx3.await
import org.signal.core.util.logging.Log
import org.signal.mediasend.MediaRecipientId
import org.signal.mediasend.MediaSendQrRepository
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.registration.data.QuickRegistrationRepository

object MediaSendV3QrRepository : MediaSendQrRepository {

  private val TAG = Log.tag(MediaSendV3QrRepository::class)

  override suspend fun checkQrData(qrData: String): MediaSendQrRepository.QrCheckResult {
    return when {
      UsernameRepository.isValidLink(qrData) -> handleUsernameLink(qrData)
      qrData.startsWith("sgnl://linkdevice") -> handleLinkDevice()
      qrData.startsWith("sgnl://rereg") && QuickRegistrationRepository.isValidReRegistrationQr(qrData) -> handleReReg(qrData)
      else -> MediaSendQrRepository.QrCheckResult.None
    }
  }

  private suspend fun handleUsernameLink(qrData: String): MediaSendQrRepository.QrCheckResult {
    return when (val result = UsernameRepository.fetchUsernameAndAciFromLink(qrData).await()) {
      is UsernameRepository.UsernameLinkConversionResult.Success -> {
        val username = result.username.toString()
        val recipient = Recipient.externalUsername(result.aci, result.username.toString())

        MediaSendQrRepository.QrCheckResult.Username(
          recipientId = MediaRecipientId(recipient.id.toLong()),
          username = username
        )
      }
      else -> {
        Log.w(TAG, "Failed to scan QR code")
        MediaSendQrRepository.QrCheckResult.None
      }
    }
  }

  private fun handleLinkDevice(): MediaSendQrRepository.QrCheckResult {
    return MediaSendQrRepository.QrCheckResult.LinkDevice
  }

  private fun handleReReg(qrData: String): MediaSendQrRepository.QrCheckResult {
    return MediaSendQrRepository.QrCheckResult.ReRegistration(qrData)
  }
}
