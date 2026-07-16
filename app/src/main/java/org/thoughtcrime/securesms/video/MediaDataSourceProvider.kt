/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.video

import android.content.Context
import android.media.MediaDataSource
import android.net.Uri
import com.squareup.wire.internal.JvmStatic
import org.signal.core.util.crypto.AttachmentSecretProvider
import org.thoughtcrime.securesms.crypto.AppAttachmentSecretStore
import org.thoughtcrime.securesms.dependencies.AppDependencies
import java.io.IOException

/**
 * Utilizes blob provider to load a blob as a media data source.
 */
object MediaDataSourceProvider {
  @JvmStatic
  @Suppress("MoveLambdaOutsideParentheses")
  @Throws(IOException::class)
  fun getMediaDataSource(context: Context, uri: Uri): MediaDataSource {
    val attachmentSecret = AttachmentSecretProvider.getInstance(context, AppAttachmentSecretStore).getOrCreateAttachmentSecret()
    return AppDependencies.blobs.getBlobRepresentation(
      context,
      uri,
      ::ByteArrayMediaDataSource,
      { EncryptedMediaDataSource.createForDiskBlob(attachmentSecret, it!!) }
    )
  }
}
