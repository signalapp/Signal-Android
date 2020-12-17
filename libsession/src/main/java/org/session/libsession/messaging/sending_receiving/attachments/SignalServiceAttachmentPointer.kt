/*
 * Copyright (C) 2014-2017 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */
package org.session.libsession.messaging.sending_receiving.attachments

import org.session.libsignal.libsignal.util.guava.Optional
import org.session.libsignal.service.api.messages.SignalServiceAttachment

/**
 * Represents a received SignalServiceAttachment "handle."  This
 * is a pointer to the actual attachment content, which needs to be
 * retrieved using [SignalServiceMessageReceiver.retrieveAttachment]
 *
 * @author Moxie Marlinspike
 */
class SignalServiceAttachmentPointer(val id: Long, contentType: String?, val key: ByteArray?,
                                     val size: Optional<Int>, val preview: Optional<ByteArray>,
                                     val width: Int, val height: Int,
                                     val digest: Optional<ByteArray>, val fileName: Optional<String>,
                                     val voiceNote: Boolean, val caption: Optional<String>, val url: String) : SignalServiceAttachment(contentType) {
    override fun isStream(): Boolean {
        return false
    }

    override fun isPointer(): Boolean {
        return true
    }
}