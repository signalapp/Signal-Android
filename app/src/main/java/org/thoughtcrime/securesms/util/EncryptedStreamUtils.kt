package org.thoughtcrime.securesms.util

import android.content.Context
import androidx.annotation.WorkerThread
import org.thoughtcrime.securesms.crypto.AttachmentSecretProvider
import org.thoughtcrime.securesms.crypto.ModernDecryptingPartInputStream
import org.thoughtcrime.securesms.crypto.ModernEncryptingPartOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Utilities for reading and writing to disk in an encrypted manner.
 */
object EncryptedStreamUtils {
  @WorkerThread
  fun getOutputStream(context: Context, outputFile: File): OutputStream {
    val attachmentSecret = AttachmentSecretProvider.getInstance(context).orCreateAttachmentSecret
    return ModernEncryptingPartOutputStream.createFor(attachmentSecret, outputFile, true).second
  }

  @WorkerThread
  fun getInputStream(context: Context, inputFile: File): InputStream {
    val attachmentSecret = AttachmentSecretProvider.getInstance(context).orCreateAttachmentSecret
    return ModernDecryptingPartInputStream.createFor(attachmentSecret, inputFile, 0)
  }
}
