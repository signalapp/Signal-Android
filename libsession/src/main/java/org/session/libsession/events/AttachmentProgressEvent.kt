package org.session.libsession.events

import org.session.libsession.messaging.sending_receiving.attachments.SessionServiceAttachment

class AttachmentProgressEvent(attachment: SessionServiceAttachment, total: Long, progress: Long) {
}