/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.crypto

import android.content.Context
import org.signal.core.util.crypto.AttachmentSecretStore
import org.thoughtcrime.securesms.keyvalue.PlainTextKeyValueStore

object AppAttachmentSecretStore : AttachmentSecretStore {
  override fun getAttachmentUnencryptedSecret(context: Context): String? {
    return PlainTextKeyValueStore.attachmentLegacyUnencryptedSecret
  }

  override fun getAttachmentEncryptedSecret(context: Context): String? {
    return PlainTextKeyValueStore.attachmentEncryptedSecret
  }

  override fun setAttachmentEncryptedSecret(context: Context, secret: String) {
    PlainTextKeyValueStore.attachmentEncryptedSecret = secret
  }

  override fun setAttachmentUnencryptedSecret(context: Context, secret: String?) {
    PlainTextKeyValueStore.attachmentLegacyUnencryptedSecret = secret
  }
}
