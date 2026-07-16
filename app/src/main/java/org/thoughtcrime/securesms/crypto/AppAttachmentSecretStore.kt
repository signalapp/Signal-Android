/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.crypto

import android.content.Context
import org.signal.core.util.crypto.AttachmentSecretStore
import org.thoughtcrime.securesms.util.TextSecurePreferences

object AppAttachmentSecretStore : AttachmentSecretStore {
  override fun getAttachmentUnencryptedSecret(context: Context): String? {
    return TextSecurePreferences.getAttachmentUnencryptedSecret(context)
  }

  override fun getAttachmentEncryptedSecret(context: Context): String? {
    return TextSecurePreferences.getAttachmentEncryptedSecret(context)
  }

  override fun setAttachmentEncryptedSecret(context: Context, secret: String) {
    TextSecurePreferences.setAttachmentEncryptedSecret(context, secret)
  }

  override fun setAttachmentUnencryptedSecret(context: Context, secret: String?) {
    TextSecurePreferences.setAttachmentUnencryptedSecret(context, secret)
  }
}
