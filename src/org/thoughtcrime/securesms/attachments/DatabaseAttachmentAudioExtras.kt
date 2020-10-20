package org.thoughtcrime.securesms.attachments

data class DatabaseAttachmentAudioExtras(val attachmentId: AttachmentId, val visualSamples: ByteArray, val durationMs: Long) {

    override fun equals(other: Any?): Boolean {
        return other != null &&
                other is DatabaseAttachmentAudioExtras &&
                other.attachmentId == attachmentId
    }

    override fun hashCode(): Int {
        return attachmentId.hashCode()
    }
}