package org.session.libsession.messaging.sending_receiving.attachments

data class DatabaseAttachmentAudioExtras(
    val attachmentId: AttachmentId,
    /** Small amount of normalized audio byte samples to visualise the content (e.g. draw waveform). */
    val visualSamples: ByteArray,
    /** Duration of the audio track in milliseconds. May be [DURATION_UNDEFINED] when it is not known. */
    val durationMs: Long = DURATION_UNDEFINED) {

    companion object {
        const val DURATION_UNDEFINED = -1L
    }

    override fun equals(other: Any?): Boolean {
        return other != null &&
            other is DatabaseAttachmentAudioExtras &&
            other.attachmentId == attachmentId
    }

    override fun hashCode(): Int {
        return attachmentId.hashCode()
    }
}