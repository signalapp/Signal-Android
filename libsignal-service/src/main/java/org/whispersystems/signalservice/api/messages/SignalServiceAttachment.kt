/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.whispersystems.signalservice.api.messages

import org.whispersystems.signalservice.internal.push.http.CancelationSignal
import org.whispersystems.signalservice.internal.push.http.ResumableUploadSpec
import java.io.InputStream
import java.util.Optional
import java.util.UUID

abstract class SignalServiceAttachment protected constructor(val contentType: String?) {
  abstract fun isStream(): Boolean
  abstract fun isPointer(): Boolean

  fun asStream(): SignalServiceAttachmentStream {
    return this as SignalServiceAttachmentStream
  }

  fun asPointer(): SignalServiceAttachmentPointer {
    return this as SignalServiceAttachmentPointer
  }

  class Builder {
    private var inputStream: InputStream? = null
    private var contentType: String? = null
    private var fileName: String? = null
    private var length: Long = 0
    private var listener: ProgressListener? = null
    private var cancelationSignal: CancelationSignal? = null
    private var voiceNote = false
    private var borderless = false
    private var gif = false
    private var faststart = false
    private var width = 0
    private var height = 0
    private var caption: String? = null
    private var blurHash: String? = null
    private var uploadTimestamp: Long = 0
    private var resumableUploadSpec: ResumableUploadSpec? = null
    private var uuid: UUID? = null

    fun withStream(inputStream: InputStream?): Builder {
      this.inputStream = inputStream
      return this
    }

    fun withContentType(contentType: String?): Builder {
      this.contentType = contentType
      return this
    }

    fun withLength(length: Long): Builder {
      this.length = length
      return this
    }

    fun withFileName(fileName: String?): Builder {
      this.fileName = fileName
      return this
    }

    fun withListener(listener: ProgressListener?): Builder {
      this.listener = listener
      return this
    }

    fun withCancelationSignal(cancelationSignal: CancelationSignal?): Builder {
      this.cancelationSignal = cancelationSignal
      return this
    }

    fun withVoiceNote(voiceNote: Boolean): Builder {
      this.voiceNote = voiceNote
      return this
    }

    fun withBorderless(borderless: Boolean): Builder {
      this.borderless = borderless
      return this
    }

    fun withGif(gif: Boolean): Builder {
      this.gif = gif
      return this
    }

    fun withFaststart(faststart: Boolean): Builder {
      this.faststart = faststart
      return this
    }

    fun withWidth(width: Int): Builder {
      this.width = width
      return this
    }

    fun withHeight(height: Int): Builder {
      this.height = height
      return this
    }

    fun withCaption(caption: String?): Builder {
      this.caption = caption
      return this
    }

    fun withBlurHash(blurHash: String?): Builder {
      this.blurHash = blurHash
      return this
    }

    fun withUploadTimestamp(uploadTimestamp: Long): Builder {
      this.uploadTimestamp = uploadTimestamp
      return this
    }

    fun withResumableUploadSpec(resumableUploadSpec: ResumableUploadSpec?): Builder {
      this.resumableUploadSpec = resumableUploadSpec
      return this
    }

    fun withUuid(uuid: UUID?): Builder {
      this.uuid = uuid
      return this
    }

    fun build(): SignalServiceAttachmentStream {
      requireNotNull(inputStream) { "Must specify stream!" }
      require(length != 0L) { "No length specified!" }

      return SignalServiceAttachmentStream(
        inputStream = inputStream!!,
        contentType = contentType,
        length = length,
        fileName = Optional.ofNullable(fileName),
        voiceNote = voiceNote,
        isBorderless = borderless,
        isGif = gif,
        isFaststart = faststart,
        preview = Optional.empty(),
        width = width,
        height = height,
        uploadTimestamp = uploadTimestamp,
        caption = Optional.ofNullable(caption),
        blurHash = Optional.ofNullable(blurHash),
        listener = listener,
        cancelationSignal = cancelationSignal,
        resumableUploadSpec = Optional.ofNullable(resumableUploadSpec),
        uuid = uuid
      )
    }
  }

  /**
   * An interface to receive progress information on upload/download of
   * an attachment.
   */
  interface ProgressListener {
    /**
     * Called on a progress change event.
     *
     * @param total    The total amount to transmit/receive in bytes.
     * @param progress The amount that has been transmitted/received in bytes thus far
     */
    fun onAttachmentProgress(total: Long, progress: Long)

    fun shouldCancel(): Boolean
  }

  companion object {
    @JvmStatic
    fun newStreamBuilder(): Builder {
      return Builder()
    }
  }
}
