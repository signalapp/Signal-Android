package org.thoughtcrime.securesms.loki.utilities.audio;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * Partially exported class from the old Google's Ringdroid project.
 * https://github.com/google/ringdroid/blob/master/app/src/main/java/com/ringdroid/soundfile/SoundFile.java
 * <p/>
 * We need this one to parse audio files. Specifically extract RMS values for waveform visualization.
 * <p/>
 * NOTE: This class instance creation might be pretty slow (depends on the source audio file size).
 * It's recommended to instantiate it in the background.
 */
public class DecodedAudio {

    // Member variables representing frame data
    private final long mFileSize;
    private final int mAvgBitRate;  // Average bit rate in kbps.
    private final int mSampleRate;
    private final long mDuration; // In microseconds.
    private final int mChannels;
    private final int mNumSamples;  // total number of samples per channel in audio file
    private final ShortBuffer mDecodedSamples;  // shared buffer with mDecodedBytes.
    // mDecodedSamples has the following format:
    // {s1c1, s1c2, ..., s1cM, s2c1, ..., s2cM, ..., sNc1, ..., sNcM}
    // where sicj is the ith sample of the jth channel (a sample is a signed short)
    // M is the number of channels (e.g. 2 for stereo) and N is the number of samples per channel.

    // TODO(nfaralli): what is the real list of supported extensions? Is it device dependent?
    public static String[] getSupportedExtensions() {
        return new String[]{"mp3", "wav", "3gpp", "3gp", "amr", "aac", "m4a", "ogg"};
    }

    public static boolean isFilenameSupported(String filename) {
        String[] extensions = getSupportedExtensions();
        for (int i = 0; i < extensions.length; i++) {
            if (filename.endsWith("." + extensions[i])) {
                return true;
            }
        }
        return false;
    }

    public DecodedAudio(FileDescriptor fd, long startOffset, long size) throws IOException {
        this(createMediaExtractor(fd, startOffset, size), size);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public DecodedAudio(MediaDataSource dataSource) throws IOException {
        this(createMediaExtractor(dataSource), dataSource.getSize());
    }

    public DecodedAudio(MediaExtractor extractor, long size) throws IOException {
        mFileSize = size;

        MediaFormat mediaFormat = null;
        int numTracks = extractor.getTrackCount();
        // find and select the first audio track present in the file.
        int trackIndex;
        for (trackIndex = 0; trackIndex < numTracks; trackIndex++) {
            MediaFormat format = extractor.getTrackFormat(trackIndex);
            if (format.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                extractor.selectTrack(trackIndex);
                mediaFormat = format;
                break;
            }
        }
        if (mediaFormat == null) {
            throw new IOException("No audio track found in the data source.");
        }

        mChannels = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        mSampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        mDuration = mediaFormat.getLong(MediaFormat.KEY_DURATION);
        // Expected total number of samples per channel.
        int expectedNumSamples =
                (int) ((mDuration / 1000000.f) * mSampleRate + 0.5f);

        MediaCodec codec = MediaCodec.createDecoderByType(mediaFormat.getString(MediaFormat.KEY_MIME));
        codec.configure(mediaFormat, null, null, 0);
        codec.start();

        try {
            int pcmEncoding = codec.getOutputFormat().getInteger(MediaFormat.KEY_PCM_ENCODING);
            if (pcmEncoding != AudioFormat.ENCODING_PCM_16BIT) {
                throw new IOException("Unsupported PCM encoding code: " + pcmEncoding);
            }
        } catch (NullPointerException e) {
            // If KEY_PCM_ENCODING is not specified, means it's ENCODING_PCM_16BIT.
        }

        int decodedSamplesSize = 0;  // size of the output buffer containing decoded samples.
        byte[] decodedSamples = null;
        int sampleSize;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long presentationTime;
        int totalSizeRead = 0;
        boolean doneReading = false;

        // Set the size of the decoded samples buffer to 1MB (~6sec of a stereo stream at 44.1kHz).
        // For longer streams, the buffer size will be increased later on, calculating a rough
        // estimate of the total size needed to store all the samples in order to resize the buffer
        // only once.
        ByteBuffer decodedBytes = ByteBuffer.allocate(1 << 20);
        boolean firstSampleData = true;
        while (true) {
            // read data from file and feed it to the decoder input buffers.
            int inputBufferIndex = codec.dequeueInputBuffer(100);
            if (!doneReading && inputBufferIndex >= 0) {
                sampleSize = extractor.readSampleData(codec.getInputBuffer(inputBufferIndex), 0);
                if (firstSampleData
                        && mediaFormat.getString(MediaFormat.KEY_MIME).equals("audio/mp4a-latm")
                        && sampleSize == 2) {
                    // For some reasons on some devices (e.g. the Samsung S3) you should not
                    // provide the first two bytes of an AAC stream, otherwise the MediaCodec will
                    // crash. These two bytes do not contain music data but basic info on the
                    // stream (e.g. channel configuration and sampling frequency), and skipping them
                    // seems OK with other devices (MediaCodec has already been configured and
                    // already knows these parameters).
                    extractor.advance();
                    totalSizeRead += sampleSize;
                } else if (sampleSize < 0) {
                    // All samples have been read.
                    codec.queueInputBuffer(
                            inputBufferIndex, 0, 0, -1, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    doneReading = true;
                } else {
                    presentationTime = extractor.getSampleTime();
                    codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTime, 0);
                    extractor.advance();
                    totalSizeRead += sampleSize;
                }
                firstSampleData = false;
            }

            // Get decoded stream from the decoder output buffers.
            int outputBufferIndex = codec.dequeueOutputBuffer(info, 100);
            if (outputBufferIndex >= 0 && info.size > 0) {
                if (decodedSamplesSize < info.size) {
                    decodedSamplesSize = info.size;
                    decodedSamples = new byte[decodedSamplesSize];
                }
                ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferIndex);
                outputBuffer.get(decodedSamples, 0, info.size);
                outputBuffer.clear();
                // Check if buffer is big enough. Resize it if it's too small.
                if (decodedBytes.remaining() < info.size) {
                    // Getting a rough estimate of the total size, allocate 20% more, and
                    // make sure to allocate at least 5MB more than the initial size.
                    int position = decodedBytes.position();
                    int newSize = (int) ((position * (1.0 * mFileSize / totalSizeRead)) * 1.2);
                    if (newSize - position < info.size + 5 * (1 << 20)) {
                        newSize = position + info.size + 5 * (1 << 20);
                    }
                    ByteBuffer newDecodedBytes = null;
                    // Try to allocate memory. If we are OOM, try to run the garbage collector.
                    int retry = 10;
                    while (retry > 0) {
                        try {
                            newDecodedBytes = ByteBuffer.allocate(newSize);
                            break;
                        } catch (OutOfMemoryError oome) {
                            // setting android:largeHeap="true" in <application> seem to help not
                            // reaching this section.
                            retry--;
                        }
                    }
                    if (retry == 0) {
                        // Failed to allocate memory... Stop reading more data and finalize the
                        // instance with the data decoded so far.
                        break;
                    }
                    //ByteBuffer newDecodedBytes = ByteBuffer.allocate(newSize);
                    decodedBytes.rewind();
                    newDecodedBytes.put(decodedBytes);
                    decodedBytes = newDecodedBytes;
                    decodedBytes.position(position);
                }
                decodedBytes.put(decodedSamples, 0, info.size);
                codec.releaseOutputBuffer(outputBufferIndex, false);
            } /*else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // Subsequent data will conform to new format.
                // We could check that codec.getOutputFormat(), which is the new output format,
                // is what we expect.
            }*/

            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    || (decodedBytes.position() / (2 * mChannels)) >= expectedNumSamples) {
                // We got all the decoded data from the decoder. Stop here.
                // Theoretically dequeueOutputBuffer(info, ...) should have set info.flags to
                // MediaCodec.BUFFER_FLAG_END_OF_STREAM. However some phones (e.g. Samsung S3)
                // won't do that for some files (e.g. with mono AAC files), in which case subsequent
                // calls to dequeueOutputBuffer may result in the application crashing, without
                // even an exception being thrown... Hence the second check.
                // (for mono AAC files, the S3 will actually double each sample, as if the stream
                // was stereo. The resulting stream is half what it's supposed to be and with a much
                // lower pitch.)
                break;
            }
        }
        mNumSamples = decodedBytes.position() / (mChannels * 2);  // One sample = 2 bytes.
        decodedBytes.rewind();
        decodedBytes.order(ByteOrder.LITTLE_ENDIAN);
        mDecodedSamples = decodedBytes.asShortBuffer();
        mAvgBitRate = (int) ((mFileSize * 8) * ((float) mSampleRate / mNumSamples) / 1000);

        extractor.release();
        codec.stop();
        codec.release();

//        // Temporary hack to make it work with the old version.
//        int numFrames = mNumSamples / getSamplesPerFrame();
//        if (mNumSamples % getSamplesPerFrame() != 0) {
//            numFrames++;
//        }
//        mFrameGains = new int[numFrames];
//        mFrameLens = new int[numFrames];
//        mFrameOffsets = new int[numFrames];
//        int j;
//        int gain, value;
//        int frameLens = (int) ((1000 * mAvgBitRate / 8) *
//                ((float) getSamplesPerFrame() / mSampleRate));
//        for (trackIndex = 0; trackIndex < numFrames; trackIndex++) {
//            gain = -1;
//            for (j = 0; j < getSamplesPerFrame(); j++) {
//                value = 0;
//                for (int k = 0; k < mChannels; k++) {
//                    if (mDecodedSamples.remaining() > 0) {
//                        value += java.lang.Math.abs(mDecodedSamples.get());
//                    }
//                }
//                value /= mChannels;
//                if (gain < value) {
//                    gain = value;
//                }
//            }
//            mFrameGains[trackIndex] = (int) Math.sqrt(gain);  // here gain = sqrt(max value of 1st channel)...
//            mFrameLens[trackIndex] = frameLens;  // totally not accurate...
//            mFrameOffsets[trackIndex] = (int) (trackIndex * (1000 * mAvgBitRate / 8) *  //  = i * frameLens
//                    ((float) getSamplesPerFrame() / mSampleRate));
//        }
//        mDecodedSamples.rewind();
//        mNumFrames = numFrames;
    }

    public long getFileSizeBytes() {
        return mFileSize;
    }

    public int getAvgBitrateKbps() {
        return mAvgBitRate;
    }

    public int getSampleRate() {
        return mSampleRate;
    }

    public int getChannels() {
        return mChannels;
    }

    /** @return Total duration in milliseconds. */
    public long getDuration() {
        return mDuration;
    }

    public int getNumSamples() {
        return mNumSamples;  // Number of samples per channel.
    }

    public ShortBuffer getSamples() {
        if (mDecodedSamples != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                    Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
                // Hack for Nougat where asReadOnlyBuffer fails to respect byte ordering.
                // See https://code.google.com/p/android/issues/detail?id=223824
                return mDecodedSamples;
            } else {
                return mDecodedSamples.asReadOnlyBuffer();
            }
        } else {
            return null;
        }
    }

    private static MediaExtractor createMediaExtractor(FileDescriptor fd, long startOffset, long size) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(fd, startOffset, size);
        return extractor;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private static MediaExtractor createMediaExtractor(MediaDataSource dataSource) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(dataSource);
        return extractor;
    }
}