/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.usernamelinks.colorpicker

import kotlinx.coroutines.withContext
import org.signal.appsettings.usernamelinks.UsernameQrCodeColorScheme
import org.signal.core.ui.compose.QrCodeData
import org.signal.core.util.concurrent.SignalDispatchers
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.profiles.manage.UsernameRepository.toLink
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.storage.StorageSyncHelper

/**
 * Storage and QR generation behind [UsernameLinkQrColorPickerViewModel].
 */
object UsernameLinkQrColorPickerRepository {

  fun getUsername(): String = SignalStore.account.username.orEmpty()

  fun getColorScheme(): UsernameQrCodeColorScheme = SignalStore.misc.usernameQrCodeColorScheme

  /**
   * The QR code for the user's username link, or null if they don't have a link.
   */
  suspend fun getQrCodeData(): QrCodeData? {
    val usernameLink = SignalStore.account.usernameLink ?: return null

    return withContext(SignalDispatchers.Default) {
      QrCodeData.forData(usernameLink.toLink())
    }
  }

  /**
   * Saves the color and syncs it to the user's other devices.
   */
  suspend fun setColorScheme(colorScheme: UsernameQrCodeColorScheme) {
    SignalStore.misc.usernameQrCodeColorScheme = colorScheme

    withContext(SignalDispatchers.Default) {
      SignalDatabase.recipients.markNeedsSync(Recipient.self().id)
      StorageSyncHelper.scheduleSyncForDataChange()
    }
  }
}
