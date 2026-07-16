package org.thoughtcrime.securesms.util

import android.content.Context
import androidx.annotation.WorkerThread
import org.signal.core.util.crypto.AttachmentSecretProvider
import org.signal.core.util.crypto.ModernDecryptingPartInputStream
import org.signal.core.util.crypto.ModernEncryptingPartOutputStream
import org.thoughtcrime.securesms.crypto.AppAttachmentSecretStore
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Utilities for reading and writing to disk in an encrypted manner.
 */
object EncryptedStreamUtils {
  @WorkerThread
  fun getOutputStream(context: Context, outputFile: File): OutputStream {
    val attachmentSecret = AttachmentSecretProvider.getInstance(context, AppAttachmentSecretStore).orCreateAttachmentSecret
    return ModernEncryptingPartOutputStream.createFor(attachmentSecret, outputFile, true).second
  }

  @WorkerThread
  fun getInputStream(context: Context, inputFile: File): InputStream {
    val attachmentSecret = AttachmentSecretProvider.getInstance(context, AppAttachmentSecretStore).orCreateAttachmentSecret
    return ModernDecryptingPartInputStream.createFor(attachmentSecret, inputFile, 0)
  }
}
