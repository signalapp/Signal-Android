package org.session.libsession.messaging.sending_receiving.attachments

enum class AttachmentTransferProgress(val value: Int) {
    TRANSFER_PROGRESS_DONE(0),
    TRANSFER_PROGRESS_STARTED(1),
    TRANSFER_PROGRESS_PENDING(2),
    TRANSFER_PROGRESS_FAILED(3)
}