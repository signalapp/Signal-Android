package org.session.libsession.utilities

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build

import androidx.annotation.RequiresApi

import java.io.FileDescriptor
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import kotlin.jvm.Throws
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Decodes the audio data and provides access to its sample data.
 * We need this to extract RMS values for waveform visualization.
 *
 * Use static [DecodedAudio.create] methods to instantiate a [DecodedAudio].
 *
 * Partially based on the old [Google's Ringdroid project]
 * (https://github.com/google/ringdroid/blob/master/app/src/main/java/com/ringdroid/soundfile/SoundFile.java).
 *
 * *NOTE:* This class instance creation might be pretty slow (depends on the source audio file size).
 * It's recommended to instantiate it in the background.
 */
@Suppress("MemberVisibilityCanBePrivate")
class DecodedAudio {

    companion object {
        @JvmStatic
        @Throws(IOException::class)
        fun create(fd: FileDescriptor, startOffset: Long, size: Long): DecodedAudio {
            val mediaExtractor = MediaExtractor().apply { setDataSource(fd, startOffset, size) }
            return DecodedAudio(mediaExtractor, size)
        }

        @JvmStatic
        @RequiresApi(api = Build.VERSION_CODES.M)
        @Throws(IOException::class)
        fun create(dataSource: MediaDataSource): DecodedAudio {
            val mediaExtractor = MediaExtractor().apply { setDataSource(dataSource) }
            return DecodedAudio(mediaExtractor, dataSource.size)
        }
    }

    val dataSize: Long

    /** Average bit rate in kbps. */
    val avgBitRate: Int

    val sampleRate: Int

    /** In microseconds. */
    val totalDuration: Long

    val channels: Int

    /** Total number of samples per channel in audio file. */
    val numSamples: Int

    val samples: ShortBuffer
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                    Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1
            ) {
                // Hack for Nougat where asReadOnlyBuffer fails to respect byte ordering.
                // See https://code.google.com/p/android/issues/detail?id=223824
                decodedSamples
            } else {
                decodedSamples.asReadOnlyBuffer()
            }
        }

    /**
     * Shared buffer with mDecodedBytes.
     * Has the following format:
     * {s1c1, s1c2, ..., s1cM, s2c1, ..., s2cM, ..., sNc1, ..., sNcM}
     * where sicj is the ith sample of the jth channel (a sample is a signed short)
     * M is the number of channels (e.g. 2 for stereo) and N is the number of samples per channel.
     */
    private val decodedSamples: ShortBuffer

    @Throws(IOException::class)
    private constructor(extractor: MediaExtractor, size: Long) {
        dataSize = size

        var mediaFormat: MediaFormat? = null
        // Find and select the first audio track present in the file.
        for (trackIndex in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(trackIndex)
            if (format.getString(MediaFormat.KEY_MIME)!!.startsWith("audio/")) {
                extractor.selectTrack(trackIndex)
                mediaFormat = format
                break
            }
        }
        if (mediaFormat == null) {
            throw IOException("No audio track found in the data source.")
        }

        channels = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        sampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        // On some old APIs (23) this field might be missing.
        totalDuration = if (mediaFormat.containsKey(MediaFormat.KEY_DURATION)) {
            mediaFormat.getLong(MediaFormat.KEY_DURATION)
        } else {
            -1L
        }

        // Expected total number of samples per channel.
        val expectedNumSamples = if (totalDuration >= 0) {
            ((totalDuration / 1000000f) * sampleRate + 0.5f).toInt()
        } else {
            Int.MAX_VALUE
        }

        val codec = MediaCodec.createDecoderByType(mediaFormat.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(mediaFormat, null, null, 0)
        codec.start()

        // Check if the track is in PCM 16 bit encoding.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val pcmEncoding = codec.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                if (pcmEncoding != AudioFormat.ENCODING_PCM_16BIT) {
                    throw IOException("Unsupported PCM encoding code: $pcmEncoding")
                }
            } catch (e: NullPointerException) {
                // If KEY_PCM_ENCODING is not specified, means it's ENCODING_PCM_16BIT.
            }
        }

        var decodedSamplesSize: Int = 0  // size of the output buffer containing decoded samples.
        var decodedSamples: ByteArray? = null
        var sampleSize: Int
        val info = MediaCodec.BufferInfo()
        var presentationTime: Long
        var totalSizeRead: Int = 0
        var doneReading = false

        // Set the size of the decoded samples buffer to 1MB (~6sec of a stereo stream at 44.1kHz).
        // For longer streams, the buffer size will be increased later on, calculating a rough
        // estimate of the total size needed to store all the samples in order to resize the buffer
        // only once.
        var decodedBytes: ByteBuffer = ByteBuffer.allocate(1 shl 20)
        var firstSampleData = true
        while (true) {
            // read data from file and feed it to the decoder input buffers.
            val inputBufferIndex: Int = codec.dequeueInputBuffer(100)
            if (!doneReading && inputBufferIndex >= 0) {
                sampleSize = extractor.readSampleData(codec.getInputBuffer(inputBufferIndex)!!, 0)
                if (firstSampleData
                        && mediaFormat.getString(MediaFormat.KEY_MIME)!! == "audio/mp4a-latm"
                        && sampleSize == 2
                ) {
                    // For some reasons on some devices (e.g. the Samsung S3) you should not
                    // provide the first two bytes of an AAC stream, otherwise the MediaCodec will
                    // crash. These two bytes do not contain music data but basic info on the
                    // stream (e.g. channel configuration and sampling frequency), and skipping them
                    // seems OK with other devices (MediaCodec has already been configured and
                    // already knows these parameters).
                    extractor.advance()
                    totalSizeRead += sampleSize
                } else if (sampleSize < 0) {
                    // All samples have been read.
                    codec.queueInputBuffer(
                            inputBufferIndex, 0, 0, -1, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    doneReading = true
                } else {
                    presentationTime = extractor.sampleTime
                    codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTime, 0)
                    extractor.advance()
                    totalSizeRead += sampleSize
                }
                firstSampleData = false
            }

            // Get decoded stream from the decoder output buffers.
            val outputBufferIndex: Int = codec.dequeueOutputBuffer(info, 100)
            if (outputBufferIndex >= 0 && info.size > 0) {
                if (decodedSamplesSize < info.size) {
                    decodedSamplesSize = info.size
                    decodedSamples = ByteArray(decodedSamplesSize)
                }
                val outputBuffer: ByteBuffer = codec.getOutputBuffer(outputBufferIndex)!!
                outputBuffer.get(decodedSamples!!, 0, info.size)
                outputBuffer.clear()
                // Check if buffer is big enough. Resize it if it's too small.
                if (decodedBytes.remaining() < info.size) {
                    // Getting a rough estimate of the total size, allocate 20% more, and
                    // make sure to allocate at least 5MB more than the initial size.
                    val position = decodedBytes.position()
                    var newSize = ((position * (1.0 * dataSize / totalSizeRead)) * 1.2).toInt()
                    if (newSize - position < info.size + 5 * (1 shl 20)) {
                        newSize = position + info.size + 5 * (1 shl 20)
                    }
                    var newDecodedBytes: ByteBuffer? = null
                    // Try to allocate memory. If we are OOM, try to run the garbage collector.
                    var retry = 10
                    while (retry > 0) {
                        try {
                            newDecodedBytes = ByteBuffer.allocate(newSize)
                            break
                        } catch (e: OutOfMemoryError) {
                            // setting android:largeHeap="true" in <application> seem to help not
                            // reaching this section.
                            retry--
                        }
                    }
                    if (retry == 0) {
                        // Failed to allocate memory... Stop reading more data and finalize the
                        // instance with the data decoded so far.
                        break
                    }
                    decodedBytes.rewind()
                    newDecodedBytes!!.put(decodedBytes)
                    decodedBytes = newDecodedBytes
                    decodedBytes.position(position)
                }
                decodedBytes.put(decodedSamples, 0, info.size)
                codec.releaseOutputBuffer(outputBufferIndex, false)
            }

            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    || (decodedBytes.position() / (2 * channels)) >= expectedNumSamples
            ) {
                // We got all the decoded data from the decoder. Stop here.
                // Theoretically dequeueOutputBuffer(info, ...) should have set info.flags to
                // MediaCodec.BUFFER_FLAG_END_OF_STREAM. However some phones (e.g. Samsung S3)
                // won't do that for some files (e.g. with mono AAC files), in which case subsequent
                // calls to dequeueOutputBuffer may result in the application crashing, without
                // even an exception being thrown... Hence the second check.
                // (for mono AAC files, the S3 will actually double each sample, as if the stream
                // was stereo. The resulting stream is half what it's supposed to be and with a much
                // lower pitch.)
                break
            }
        }
        numSamples = decodedBytes.position() / (channels * 2)  // One sample = 2 bytes.
        decodedBytes.rewind()
        decodedBytes.order(ByteOrder.LITTLE_ENDIAN)
        this.decodedSamples = decodedBytes.asShortBuffer()
        avgBitRate = ((dataSize * 8) * (sampleRate.toFloat() / numSamples) / 1000).toInt()

        extractor.release()
        codec.stop()
        codec.release()
    }

    fun calculateRms(maxFrames: Int): ByteArray {
        return calculateRms(this.samples, this.numSamples, this.channels, maxFrames)
    }
}

/**
 * Computes audio RMS values for the first channel only.
 *
 * A typical RMS calculation algorithm is:
 * 1. Square each sample
 * 2. Sum the squared samples
 * 3. Divide the sum of the squared samples by the number of samples
 * 4. Take the square root of step 3., the mean of the squared samples
 *
 * @param maxFrames Defines amount of output RMS frames.
 * If number of samples per channel is less than "maxFrames",
 * the result array will match the source sample size instead.
 *
 * @return normalized RMS values as a signed byte array.
 */
private fun calculateRms(samples: ShortBuffer, numSamples: Int, channels: Int, maxFrames: Int): ByteArray {
    val numFrames: Int
    val frameStep: Float

    val samplesPerChannel = numSamples / channels
    if (samplesPerChannel <= maxFrames) {
        frameStep = 1f
        numFrames = samplesPerChannel
    } else {
        frameStep = numSamples / maxFrames.toFloat()
        numFrames = maxFrames
    }

    val rmsValues = FloatArray(numFrames)

    var squaredFrameSum = 0.0
    var currentFrameIdx = 0

    fun calculateFrameRms(nextFrameIdx: Int) {
        rmsValues[currentFrameIdx] = sqrt(squaredFrameSum.toFloat())

        // Advance to the next frame.
        squaredFrameSum = 0.0
        currentFrameIdx = nextFrameIdx
    }

    (0 until numSamples * channels step channels).forEach { sampleIdx ->
        val channelSampleIdx = sampleIdx / channels
        val frameIdx = (channelSampleIdx / frameStep).toInt()

        if (currentFrameIdx != frameIdx) {
            // Calculate RMS value for the previous frame.
            calculateFrameRms(frameIdx)
        }

        val samplesInCurrentFrame = ceil((currentFrameIdx + 1) * frameStep) - ceil(currentFrameIdx * frameStep)
        squaredFrameSum += (samples[sampleIdx] * samples[sampleIdx]) / samplesInCurrentFrame
    }
    // Calculate RMS value for the last frame.
    calculateFrameRms(-1)

//    smoothArray(rmsValues, 1.0f)
    normalizeArray(rmsValues)

    // Convert normalized result to a signed byte array.
    return rmsValues.map { value -> normalizedFloatToByte(value) }.toByteArray()
}

/**
 * Normalizes the array's values to [0..1] range.
 */
private fun normalizeArray(values: FloatArray) {
    var maxValue = -Float.MAX_VALUE
    var minValue = +Float.MAX_VALUE
    values.forEach { value ->
        if (value > maxValue) maxValue = value
        if (value < minValue) minValue = value
    }
    val span = maxValue - minValue

    if (span == 0f) {
        values.indices.forEach { i -> values[i] = 0f }
        return
    }

    values.indices.forEach { i -> values[i] = (values[i] - minValue) / span }
}

private fun smoothArray(values: FloatArray, neighborWeight: Float = 1f): FloatArray {
    if (values.size < 3) return values

    val result = FloatArray(values.size)
    result[0] = values[0]
    result[values.size - 1] == values[values.size - 1]
    for (i in 1 until values.size - 1) {
        result[i] = (values[i] + values[i - 1] * neighborWeight +
                values[i + 1] * neighborWeight) / (1f + neighborWeight * 2f)
    }
    return result
}

/** Turns a signed byte into a [0..1] float. */
inline fun byteToNormalizedFloat(value: Byte): Float {
    return (value + 128f) / 255f
}

/** Turns a [0..1] float into a signed byte. */
inline fun normalizedFloatToByte(value: Float): Byte {
    return (255f * value - 128f).roundToInt().toByte()
}

class InputStreamMediaDataSource: MediaDataSource {

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