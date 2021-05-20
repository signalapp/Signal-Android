package org.session.libsignal.streams

import java.io.OutputStream

/**
 * An `OutputStreamFactory` that copies the input directly to the output without modification.
 *
 * For encrypted attachments, see `AttachmentCipherOutputStreamFactory`.
 * For encrypted profiles, see `ProfileCipherOutputStreamFactory`.
 */
class PlaintextOutputStreamFactory : OutputStreamFactory {

  override fun createFor(outputStream: OutputStream?): DigestingOutputStream {
    return object : DigestingOutputStream(outputStream) { }
  }
}
