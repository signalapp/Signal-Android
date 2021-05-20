/*
 * Copyright (C) 2014-2017 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.session.libsession.messaging.sending_receiving.attachments

import org.session.libsignal.utilities.guava.Optional

/**
 * Represents a received SignalServiceAttachment "handle."  This
 * is a pointer to the actual attachment content, which needs to be
 * retrieved using SignalServiceMessageReceiver.retrieveAttachment
 *
 * @author Moxie Marlinspike
 */
class SessionServiceAttachmentPointer(val id: Long, contentType: String?, key: ByteArray?,
                                      val size: Optional<Int>, val preview: Optional<ByteArray>,
                                      val width: Int, val height: Int,
                                      val digest: Optional<ByteArray>, val fileName: Optional<String>,
                                      val voiceNote: Boolean, val caption: Optional<String>, url: String) : SessionServiceAttachment(contentType) {
    override fun isStream(): Boolean {
        return false
    }

    override fun isPointer(): Boolean {
        return true
    }
}