package org.thoughtcrime.securesms.video.videoconverter;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.media.MediaInput;
import org.thoughtcrime.securesms.video.VideoUtil;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;

final class AudioTrackConverter {

    private static final String TAG = "media-converter";
    private static final boolean VERBOSE = false; // lots of logging

    private static final String OUTPUT_AUDIO_MIME_TYPE = VideoUtil.AUDIO_MIME_TYPE; // Advanced Audio Coding
    private static final int OUTPUT_AUDIO_AAC_PROFILE = MediaCodecInfo.CodecProfileLevel.AACObjectLC; //MediaCodecInfo.CodecProfileLevel.AACObjectHE;

    private static final int TIMEOUT_USEC = 10000;

    private final long mTimeFrom;
    private final long mTimeTo;
    private final int mAudioBitrate;

    final long mInputDuration;

    private final MediaExtractor mAudioExtractor;
    private final MediaCodec mAudioDecoder;
    private final MediaCodec mAudioEncoder;

    private final ByteBuffer[] mAudioDecoderInputBuffers;
    private ByteBuffer[] mAudioDecoderOutputBuffers;
    private final ByteBuffer[] mAudioEncoderInputBuffers;
    private ByteBuffer[] mAudioEncoderOutputBuffers;
    private final MediaCodec.BufferInfo mAudioDecoderOutputBufferInfo;
    private final MediaCodec.BufferInfo mAudioEncoderOutputBufferInfo;

    MediaFormat mEncoderOutputAudioFormat;

    boolean mAudioExtractorDone;
    private boolean mAudioDecoderDone;
    boolean mAudioEncoderDone;

    private int mOutputAudioTrack = -1;

    private int mPendingAudioDecoderOutputBufferIndex = -1;
    long mMuxingAudioPresentationTime;

    private int mAudioExtractedFrameCount;
    private int mAudioDecodedFrameCount;
    private int mAudioEncodedFrameCount;

    private Muxer mMuxer;

    static @Nullable
    AudioTrackConverter create(
            final @NonNull MediaInput input,
            final long timeFrom,
            final long timeTo,
            final int audioBitrate) throws IOException {

        final MediaExtractor audioExtractor = input.createExtractor();
        final int audioInputTrack = getAndSelectAudioTrackIndex(audioExtractor);
        if (audioInputTrack == -1) {
            audioExtractor.release();
            return null;
        }
        return new AudioTrackConverter(audioExtractor, audioInputTrack, timeFrom, timeTo, audioBitrate);
    }

    private AudioTrackConverter(
            final @NonNull MediaExtractor audioExtractor,
            final int audioInputTrack,
            long timeFrom,
            long timeTo,
            int audioBitrate) throws IOException {

        mTimeFrom = timeFrom;
        mTimeTo = timeTo;
        mAudioExtractor = audioExtractor;
        mAudioBitrate = audioBitrate;

        final MediaCodecInfo audioCodecInfo = MediaConverter.selectCodec(OUTPUT_AUDIO_MIME_TYPE);
        if (audioCodecInfo == null) {
            // Don't fail CTS if they don't have an AAC codec (not here, anyway).
            Log.e(TAG, "Unable to find an appropriate codec for " + OUTPUT_AUDIO_MIME_TYPE);
            throw new FileNotFoundException();
        }
        if (VERBOSE) Log.d(TAG, "audio found codec: " + audioCodecInfo.getName());

        final MediaFormat inputAudioFormat = mAudioExtractor.getTrackFormat(audioInputTrack);
        mInputDuration = inputAudioFormat.containsKey(MediaFormat.KEY_DURATION) ? inputAudioFormat.getLong(MediaFormat.KEY_DURATION) : 0;

        final MediaFormat outputAudioFormat =
                MediaFormat.createAudioFormat(
                        OUTPUT_AUDIO_MIME_TYPE,
                        inputAudioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                        inputAudioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
        outputAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, audioBitrate);
        outputAudioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, OUTPUT_AUDIO_AAC_PROFILE);
        outputAudioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024);

        // Create a MediaCodec for the desired codec, then configure it as an encoder with
        // our desired properties. Request a Surface to use for input.
        mAudioEncoder = createAudioEncoder(audioCodecInfo, outputAudioFormat);
        // Create a MediaCodec for the decoder, based on the extractor's format.
        mAudioDecoder = createAudioDecoder(inputAudioFormat);

        mAudioDecoderInputBuffers = mAudioDecoder.getInputBuffers();
        mAudioDecoderOutputBuffers = mAudioDecoder.getOutputBuffers();
        mAudioEncoderInputBuffers = mAudioEncoder.getInputBuffers();
        mAudioEncoderOutputBuffers = mAudioEncoder.getOutputBuffers();
        mAudioDecoderOutputBufferInfo = new MediaCodec.BufferInfo();
        mAudioEncoderOutputBufferInfo = new MediaCodec.BufferInfo();

        if (mTimeFrom > 0) {
            mAudioExtractor.seekTo(mTimeFrom * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            Log.i(TAG, "Seek audio:" + mTimeFrom + " " + mAudioExtractor.getSampleTime());
        }
    }

    void setMuxer(final @NonNull Muxer muxer) throws IOException {
        mMuxer = muxer;
        if (mEncoderOutputAudioFormat != null) {
            Log.d(TAG, "muxer: adding audio track.");
            if (!mEncoderOutputAudioFormat.containsKey(MediaFormat.KEY_BIT_RATE)) {
                mEncoderOutputAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, mAudioBitrate);
            }
            if (!mEncoderOutputAudioFormat.containsKey(MediaFormat.KEY_AAC_PROFILE)) {
                mEncoderOutputAudioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, OUTPUT_AUDIO_AAC_PROFILE);
            }
            mOutputAudioTrack = muxer.addTrack(mEncoderOutputAudioFormat);
        }
    }

    void step() throws IOException {
        // Extract audio from file and feed to decoder.
        // Do not extract audio if we have determined the output format but we are not yet
        // ready to mux the frames.
        while (!mAudioExtractorDone && (mEncoderOutputAudioFormat == null || mMuxer != null)) {
            int decoderInputBufferIndex = mAudioDecoder.dequeueInputBuffer(TIMEOUT_USEC);
            if (decoderInputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (VERBOSE) Log.d(TAG, "no audio decoder input buffer");
                break;
            }
            if (VERBOSE) {
                Log.d(TAG, "audio decoder: returned input buffer: " + decoderInputBufferIndex);
            }
            final ByteBuffer decoderInputBuffer = mAudioDecoderInputBuffers[decoderInputBufferIndex];
            final int size = mAudioExtractor.readSampleData(decoderInputBuffer, 0);
            final long presentationTime = mAudioExtractor.getSampleTime();
            if (VERBOSE) {
                Log.d(TAG, "audio extractor: returned buffer of size " + size);
                Log.d(TAG, "audio extractor: returned buffer for time " + presentationTime);
            }
            mAudioExtractorDone = size < 0 || (mTimeTo > 0 && presentationTime > mTimeTo * 1000);
            if (mAudioExtractorDone) {
                if (VERBOSE) Log.d(TAG, "audio extractor: EOS");
                mAudioDecoder.queueInputBuffer(
                        decoderInputBufferIndex,
                        0,
                        0,
                        0,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            } else {
                mAudioDecoder.queueInputBuffer(
                        decoderInputBufferIndex,
                        0,
                        size,
                        presentationTime,
                        mAudioExtractor.getSampleFlags());
            }
            mAudioExtractor.advance();
            mAudioExtractedFrameCount++;
            // We extracted a frame, let's try something else next.
            break;
        }

        // Poll output frames from the audio decoder.
        // Do not poll if we already have a pending buffer to feed to the encoder.
        while (!mAudioDecoderDone && mPendingAudioDecoderOutputBufferIndex == -1
                && (mEncoderOutputAudioFormat == null || mMuxer != null)) {
            final int decoderOutputBufferIndex =
                    mAudioDecoder.dequeueOutputBuffer(
                            mAudioDecoderOutputBufferInfo, TIMEOUT_USEC);
            if (decoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (VERBOSE) Log.d(TAG, "no audio decoder output buffer");
                break;
            }
            if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                if (VERBOSE) Log.d(TAG, "audio decoder: output buffers changed");
                mAudioDecoderOutputBuffers = mAudioDecoder.getOutputBuffers();
                break;
            }
            if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (VERBOSE) {
                    MediaFormat decoderOutputAudioFormat = mAudioDecoder.getOutputFormat();
                    Log.d(TAG, "audio decoder: output format changed: " + decoderOutputAudioFormat);
                }
                break;
            }
            if (VERBOSE) {
                Log.d(TAG, "audio decoder: returned output buffer: " + decoderOutputBufferIndex);
                Log.d(TAG, "audio decoder: returned buffer of size " + mAudioDecoderOutputBufferInfo.size);
            }
            if ((mAudioDecoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                if (VERBOSE) Log.d(TAG, "audio decoder: codec config buffer");
                mAudioDecoder.releaseOutputBuffer(decoderOutputBufferIndex, false);
                break;
            }
            if (mAudioDecoderOutputBufferInfo.presentationTimeUs < mTimeFrom * 1000 &&
                    (mAudioDecoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0) {
                if (VERBOSE)
                    Log.d(TAG, "audio decoder: frame prior to " + mAudioDecoderOutputBufferInfo.presentationTimeUs);
                mAudioDecoder.releaseOutputBuffer(decoderOutputBufferIndex, false);
                break;
            }
            if (VERBOSE) {
                Log.d(TAG, "audio decoder: returned buffer for time " + mAudioDecoderOutputBufferInfo.presentationTimeUs);
                Log.d(TAG, "audio decoder: output buffer is now pending: " + mPendingAudioDecoderOutputBufferIndex);
            }
            mPendingAudioDecoderOutputBufferIndex = decoderOutputBufferIndex;
            mAudioDecodedFrameCount++;
            // We extracted a pending frame, let's try something else next.
            break;
        }

        // Feed the pending decoded audio buffer to the audio encoder.
        while (mPendingAudioDecoderOutputBufferIndex != -1) {
            if (VERBOSE) {
                Log.d(TAG, "audio decoder: attempting to process pending buffer: " + mPendingAudioDecoderOutputBufferIndex);
            }
            final int encoderInputBufferIndex = mAudioEncoder.dequeueInputBuffer(TIMEOUT_USEC);
            if (encoderInputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (VERBOSE) Log.d(TAG, "no audio encoder input buffer");
                break;
            }
            if (VERBOSE) {
                Log.d(TAG, "audio encoder: returned input buffer: " + encoderInputBufferIndex);
            }
            final ByteBuffer encoderInputBuffer = mAudioEncoderInputBuffers[encoderInputBufferIndex];
            final int size = mAudioDecoderOutputBufferInfo.size;
            final long presentationTime = mAudioDecoderOutputBufferInfo.presentationTimeUs;
            if (VERBOSE) {
                Log.d(TAG, "audio decoder: processing pending buffer: " + mPendingAudioDecoderOutputBufferIndex);
            }
            if (VERBOSE) {
                Log.d(TAG, "audio decoder: pending buffer of size " + size);
                Log.d(TAG, "audio decoder: pending buffer for time " + presentationTime);
            }
            if (size >= 0) {
                final ByteBuffer decoderOutputBuffer = mAudioDecoderOutputBuffers[mPendingAudioDecoderOutputBufferIndex].duplicate();
                decoderOutputBuffer.position(mAudioDecoderOutputBufferInfo.offset);
                decoderOutputBuffer.limit(mAudioDecoderOutputBufferInfo.offset + size);
                encoderInputBuffer.position(0);
                encoderInputBuffer.put(decoderOutputBuffer);

                mAudioEncoder.queueInputBuffer(
                        encoderInputBufferIndex,
                        0,
                        size,
                        presentationTime,
                        mAudioDecoderOutputBufferInfo.flags);
            }
            mAudioDecoder.releaseOutputBuffer(mPendingAudioDecoderOutputBufferIndex, false);
            mPendingAudioDecoderOutputBufferIndex = -1;
            if ((mAudioDecoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                if (VERBOSE) Log.d(TAG, "audio decoder: EOS");
                mAudioDecoderDone = true;
            }
            // We enqueued a pending frame, let's try something else next.
            break;
        }

        // Poll frames from the audio encoder and send them to the muxer.
        while (!mAudioEncoderDone && (mEncoderOutputAudioFormat == null || mMuxer != null)) {
            final int encoderOutputBufferIndex = mAudioEncoder.dequeueOutputBuffer(mAudioEncoderOutputBufferInfo, TIMEOUT_USEC);
            if (encoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (VERBOSE) Log.d(TAG, "no audio encoder output buffer");
                break;
            }
            if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                if (VERBOSE) Log.d(TAG, "audio encoder: output buffers changed");
                mAudioEncoderOutputBuffers = mAudioEncoder.getOutputBuffers();
                break;
            }
            if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (VERBOSE) Log.d(TAG, "audio encoder: output format changed");
                Preconditions.checkState("audio encoder changed its output format again?", mOutputAudioTrack < 0);

                mEncoderOutputAudioFormat = mAudioEncoder.getOutputFormat();
                break;
            }
            Preconditions.checkState("should have added track before processing output", mMuxer != null);
            if (VERBOSE) {
                Log.d(TAG, "audio encoder: returned output buffer: " + encoderOutputBufferIndex);
                Log.d(TAG, "audio encoder: returned buffer of size " + mAudioEncoderOutputBufferInfo.size);
            }
            final ByteBuffer encoderOutputBuffer = mAudioEncoderOutputBuffers[encoderOutputBufferIndex];
            if ((mAudioEncoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                if (VERBOSE) Log.d(TAG, "audio encoder: codec config buffer");
                // Simply ignore codec config buffers.
                mAudioEncoder.releaseOutputBuffer(encoderOutputBufferIndex, false);
                break;
            }
            if (VERBOSE) {
                Log.d(TAG, "audio encoder: returned buffer for time " + mAudioEncoderOutputBufferInfo.presentationTimeUs);
            }
            if (mAudioEncoderOutputBufferInfo.size != 0) {
                mMuxer.writeSampleData(mOutputAudioTrack, encoderOutputBuffer, mAudioEncoderOutputBufferInfo);
                mMuxingAudioPresentationTime = Math.max(mMuxingAudioPresentationTime, mAudioEncoderOutputBufferInfo.presentationTimeUs);
            }
            if ((mAudioEncoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                if (VERBOSE) Log.d(TAG, "audio encoder: EOS");
                mAudioEncoderDone = true;
            }
            mAudioEncoder.releaseOutputBuffer(encoderOutputBufferIndex, false);
            mAudioEncodedFrameCount++;
            // We enqueued an encoded frame, let's try something else next.
            break;
        }
    }

    void release() throws Exception {
        Exception exception = null;
        try {
            if (mAudioExtractor != null) {
                mAudioExtractor.release();
            }
        } catch (Exception e) {
            Log.e(TAG, "error while releasing mAudioExtractor", e);
            exception = e;
        }
        try {
            if (mAudioDecoder != null) {
                mAudioDecoder.stop();
                mAudioDecoder.release();
            }
        } catch (Exception e) {
            Log.e(TAG, "error while releasing mAudioDecoder", e);
            if (exception == null) {
                exception = e;
            }
        }
        try {
            if (mAudioEncoder != null) {
                mAudioEncoder.stop();
                mAudioEncoder.release();
            }
        } catch (Exception e) {
            Log.e(TAG, "error while releasing mAudioEncoder", e);
            if (exception == null) {
                exception = e;
            }
        }
        if (exception != null) {
            throw exception;
        }
    }

    String dumpState() {
        return String.format(Locale.US,
                "A{"
                        + "extracted:%d(done:%b) "
                        + "decoded:%d(done:%b) "
                        + "encoded:%d(done:%b) "
                        + "pending:%d "
                        + "muxing:%b(track:%d} )",
                mAudioExtractedFrameCount, mAudioExtractorDone,
                mAudioDecodedFrameCount, mAudioDecoderDone,
                mAudioEncodedFrameCount, mAudioEncoderDone,
                mPendingAudioDecoderOutputBufferIndex,
                mMuxer != null, mOutputAudioTrack);
    }

    void verifyEndState() {
        Preconditions.checkState("no frame should be pending", -1 == mPendingAudioDecoderOutputBufferIndex);
    }

    private static @NonNull
    MediaCodec createAudioDecoder(final @NonNull MediaFormat inputFormat) throws IOException {
        final MediaCodec decoder = MediaCodec.createDecoderByType(MediaConverter.getMimeTypeFor(inputFormat));
        decoder.configure(inputFormat, null, null, 0);
        decoder.start();
        return decoder;
    }

    private static @NonNull
    MediaCodec createAudioEncoder(final @NonNull MediaCodecInfo codecInfo, final @NonNull MediaFormat format) throws IOException {
        final MediaCodec encoder = MediaCodec.createByCodecName(codecInfo.getName());
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();
        return encoder;
    }

    private static int getAndSelectAudioTrackIndex(MediaExtractor extractor) {
        for (int index = 0; index < extractor.getTrackCount(); ++index) {
            if (VERBOSE) {
                Log.d(TAG, "format for track " + index + " is " + MediaConverter.getMimeTypeFor(extractor.getTrackFormat(index)));
            }
            if (isAudioFormat(extractor.getTrackFormat(index))) {
                extractor.selectTrack(index);
                return index;
            }
        }
        return -1;
    }

    private static boolean isAudioFormat(final @NonNull MediaFormat format) {
        return MediaConverter.getMimeTypeFor(format).startsWith("audio/");
    }
}
