/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.signal.core.util.crypto

import android.content.Context

interface AttachmentSecretStore {
  fun getAttachmentUnencryptedSecret(context: Context): String?
  fun getAttachmentEncryptedSecret(context: Context): String?
  fun setAttachmentEncryptedSecret(context: Context, secret: String)
  fun setAttachmentUnencryptedSecret(context: Context, secret: String?)
}
