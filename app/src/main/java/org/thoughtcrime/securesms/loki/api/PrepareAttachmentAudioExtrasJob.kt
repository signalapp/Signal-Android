package org.thoughtcrime.securesms.loki.api

import android.media.MediaDataSource
import android.os.Build
import org.session.libsignal.utilities.Log
import androidx.annotation.RequiresApi
import org.greenrobot.eventbus.EventBus
import org.session.libsession.messaging.utilities.Data
import org.session.libsession.messaging.sending_receiving.attachments.Attachment
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentId
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachmentAudioExtras
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobs.BaseJob
import org.thoughtcrime.securesms.loki.utilities.DecodedAudio
import org.thoughtcrime.securesms.mms.PartAuthority
import java.io.InputStream
import java.lang.IllegalStateException
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Decodes the audio content of the related attachment entry
 * and caches the result with [DatabaseAttachmentAudioExtras] data.
 *
 * It only process attachments with "audio" mime types.
 *
 * Due to [DecodedAudio] implementation limitations, it only works for API 23+.
 * For any lower targets fake data will be generated.
 *
 * You can subscribe to [AudioExtrasUpdatedEvent] to be notified about the successful result.
 */
//TODO AC: Rewrite to WorkManager API when
// https://github.com/loki-project/session-android/pull/354 is merged.
class PrepareAttachmentAudioExtrasJob : BaseJob {

    companion object {
        private const val TAG = "AttachAudioExtrasJob"

        const val KEY = "PrepareAttachmentAudioExtrasJob"
        const val DATA_ATTACH_ID = "attachment_id"

        const val VISUAL_RMS_FRAMES = 32 // The amount of values to be computed for the visualization.
    }

    private val attachmentId: AttachmentId

    constructor(attachmentId: AttachmentId) : this(Parameters.Builder()
            .setQueue(KEY)
            .setLifespan(TimeUnit.DAYS.toMillis(1))
            .build(),
            attachmentId)

    private constructor(parameters: Parameters, attachmentId: AttachmentId) : super(parameters) {
        this.attachmentId = attachmentId
    }

    override fun serialize(): Data {
        return Data.Builder().putParcelable(DATA_ATTACH_ID, attachmentId).build();
    }

    override fun getFactoryKey(): String { return KEY
    }

    override fun onShouldRetry(e: Exception): Boolean {
        return false
    }

    override fun onCanceled() { }

    override fun onRun() {
        Log.v(TAG, "Processing attachment: $attachmentId")

        val attachDb = DatabaseFactory.getAttachmentDatabase(context)
        val attachment = attachDb.getAttachment(attachmentId)

        if (attachment == null) {
            throw IllegalStateException("Cannot find attachment with the ID $attachmentId")
        }
        if (!attachment.contentType.startsWith("audio/")) {
            throw IllegalStateException("Attachment $attachmentId is not of audio type.")
        }

        // Check if the audio extras already exist.
        if (attachDb.getAttachmentAudioExtras(attachmentId) != null) return

        fun extractAttachmentRandomSeed(attachment: Attachment): Int {
            return when {
                attachment.digest != null -> attachment.digest!!.sum()
                attachment.fileName != null -> attachment.fileName.hashCode()
                else -> attachment.hashCode()
            }
        }

        fun generateFakeRms(seed: Int, frames: Int = VISUAL_RMS_FRAMES): ByteArray {
            return ByteArray(frames).apply { Random(seed.toLong()).nextBytes(this) }
        }

        var rmsValues: ByteArray
        var totalDurationMs: Long = DatabaseAttachmentAudioExtras.DURATION_UNDEFINED

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Due to API version incompatibility, we just display some random waveform for older API.
            rmsValues = generateFakeRms(extractAttachmentRandomSeed(attachment))
        } else {
            try {
                @Suppress("BlockingMethodInNonBlockingContext")
                val decodedAudio = PartAuthority.getAttachmentStream(context, attachment.dataUri!!).use {
                    DecodedAudio.create(InputStreamMediaDataSource(it))
                }
                rmsValues = decodedAudio.calculateRms(VISUAL_RMS_FRAMES)
                totalDurationMs = (decodedAudio.totalDuration / 1000.0).toLong()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decode sample values for the audio attachment \"${attachment.fileName}\".", e)
                rmsValues = generateFakeRms(extractAttachmentRandomSeed(attachment))
            }
        }

        attachDb.setAttachmentAudioExtras(DatabaseAttachmentAudioExtras(
                attachmentId,
                rmsValues,
                totalDurationMs
        ))

        EventBus.getDefault().post(AudioExtrasUpdatedEvent(attachmentId))
    }

    class Factory : Job.Factory<PrepareAttachmentAudioExtrasJob> {
        override fun create(parameters: Parameters, data: Data): PrepareAttachmentAudioExtrasJob {
            return PrepareAttachmentAudioExtrasJob(parameters, data.getParcelable(DATA_ATTACH_ID, AttachmentId.CREATOR))
        }
    }

    /** Gets dispatched once the audio extras have been updated. */
    data class AudioExtrasUpdatedEvent(val attachmentId: AttachmentId)

    @RequiresApi(Build.VERSION_CODES.M)
    private class InputStreamMediaDataSource: MediaDataSource {

        private val data: ByteArray

        constructor(inputStream: InputStream): super() {
            this.data = inputStream.readBytes()
        }

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            val length: Int = data.size
            if (position >= length) {
                return -1 // -1 indicates EOF
            }
            var actualSize = size
            if (position + size > length) {
                actualSize -= (position + size - length).toInt()
            }
            System.arraycopy(data, position.toInt(), buffer, offset, actualSize)
            return actualSize
        }

        override fun getSize(): Long {
            return data.size.toLong()
        }

        override fun close() {
            // We don't need to close the wrapped stream.
        }
    }
}