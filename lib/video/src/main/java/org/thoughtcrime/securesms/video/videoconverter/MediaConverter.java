/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file has been modified by Signal.
 */

package org.thoughtcrime.securesms.video.videoconverter;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringDef;
import androidx.annotation.WorkerThread;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.video.interfaces.MediaInput;
import org.thoughtcrime.securesms.video.interfaces.Muxer;
import org.thoughtcrime.securesms.video.videoconverter.exceptions.EncodingException;
import org.thoughtcrime.securesms.video.videoconverter.muxer.StreamingMuxer;
import org.thoughtcrime.securesms.video.videoconverter.utils.MediaCodecCompat;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("WeakerAccess")
public final class MediaConverter {
    private static final String TAG = "media-converter";
    private static final boolean VERBOSE = false; // lots of logging

    private static final int STUCK_FRAME_THRESHOLD = 100;

    // Describes when the annotation will be discarded
    @Retention(RetentionPolicy.SOURCE)
    @StringDef({VIDEO_CODEC_H264, VIDEO_CODEC_H265})
    public @interface VideoCodec {}
    public static final String VIDEO_CODEC_H264 = "video/avc";
    public static final String VIDEO_CODEC_H265 = "video/hevc";

    private MediaInput mInput;
    private Output     mOutput;

    private long mTimeFrom;
    private long mTimeTo;
    private int mVideoResolution;
    private int mVideoBitrate = 2000000; // 2Mbps
    private @VideoCodec String mVideoCodec = VIDEO_CODEC_H264;
    private int mAudioBitrate = 128000; // 128Kbps
    private boolean mAllowAudioRemux = false;

    private Listener mListener;
    private boolean mCancelled;

    public interface Listener {
        boolean onProgress(int percent);
    }

    public MediaConverter() {
    }

    public void setInput(final @NonNull MediaInput videoInput) {
        mInput = videoInput;
    }

    @SuppressWarnings("unused")
    public void setOutput(final @NonNull File file) {
        mOutput = new FileOutput(file);
    }

    @SuppressWarnings("unused")
    @RequiresApi(26)
    public void setOutput(final @NonNull FileDescriptor fileDescriptor) {
        mOutput = new FileDescriptorOutput(fileDescriptor);
    }

    public void setOutput(final @NonNull OutputStream stream) {
        mOutput = new StreamOutput(stream);
    }

    @SuppressWarnings("unused")
    public void setTimeRange(long timeFrom, long timeTo) {
        mTimeFrom = timeFrom;
        mTimeTo = timeTo;

        if (timeTo > 0 && timeFrom >= timeTo) {
            throw new IllegalArgumentException("timeFrom:" + timeFrom + " timeTo:" + timeTo);
        }
    }

    @SuppressWarnings("unused")
    public void setVideoResolution(int videoResolution) {
        mVideoResolution = videoResolution;
    }

    @SuppressWarnings("unused")
    public void setVideoCodec(final @VideoCodec String videoCodec) throws FileNotFoundException {
        if (selectCodec(videoCodec) == null) {
            throw new FileNotFoundException();
        }
        mVideoCodec = videoCodec;
    }

    @SuppressWarnings("unused")
    public void setVideoBitrate(final int videoBitrate) {
        mVideoBitrate = videoBitrate;
    }

    @SuppressWarnings("unused")
    public void setAudioBitrate(final int audioBitrate) {
        mAudioBitrate = audioBitrate;
    }

    @SuppressWarnings("unused")
    public void setListener(final Listener listener) {
        mListener = listener;
    }

    public void setAllowAudioRemux(boolean allow) {
        mAllowAudioRemux = allow;
    }

    /**
     * @return The total content size of the MP4 mdat box.
     */
    @WorkerThread
    public long convert() throws EncodingException, IOException {
        final Set<String> excludedDecoders = new HashSet<>();

        while (true) {
            mCancelled = false;
            try {
                return doConvert(excludedDecoders);
            } catch (EncodingException e) {
                if (e.decoderName != null
                        && isRetryableMidStreamFailure(e)
                        && excludedDecoders.add(e.decoderName)) {
                    Log.w(TAG, "Mid-stream codec failure with decoder " + e.decoderName
                            + ", retrying with it excluded (" + excludedDecoders.size()
                            + " decoder(s) excluded)");
                    continue;
                }
                throw e;
            }
        }
    }

    /**
     * Checks whether the given {@link EncodingException} represents a mid-stream codec failure
     * that may succeed with a different decoder. This covers:
     * <ul>
     *     <li>{@link IllegalStateException} from {@link android.media.MediaCodec} native methods
     *         (hardware codec crash during encoding/decoding)</li>
     *     <li>Frame count mismatches from {@link VideoTrackConverter#verifyEndState()} (unusual
     *         video formats like spatial video that some decoders handle differently)</li>
     * </ul>
     */
    private static boolean isRetryableMidStreamFailure(final @NonNull EncodingException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof IllegalStateException) {
                for (StackTraceElement frame : cause.getStackTrace()) {
                    if ("android.media.MediaCodec".equals(frame.getClassName())) {
                        return true;
                    }
                }
                String message = cause.getMessage();
                if (message != null && message.contains("frame counts should match")) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    private long doConvert(final @NonNull Set<String> excludedDecoders) throws EncodingException, IOException {
        // Exception that may be thrown during release.
        Exception           exception           = null;
        Muxer               muxer               = null;
        VideoTrackConverter videoTrackConverter = null;
        AudioTrackConverter audioTrackConverter = null;

        long mdatContentLength = 0;
        boolean muxerStarted = false;
        boolean muxerStopped = false;

        try {
            muxer = mOutput.createMuxer();

            videoTrackConverter = VideoTrackConverter.create(mInput, mTimeFrom, mTimeTo, mVideoResolution, mVideoBitrate, mVideoCodec, excludedDecoders);
            audioTrackConverter = AudioTrackConverter.create(mInput, mTimeFrom, mTimeTo, mAudioBitrate, mAllowAudioRemux && muxer.supportsAudioRemux());

            if (videoTrackConverter == null && audioTrackConverter == null) {
                throw new EncodingException("No video and audio tracks");
            }

            muxerStarted = doExtractDecodeEditEncodeMux(
                    videoTrackConverter,
                    audioTrackConverter,
                    muxer);

            if (muxerStarted) {
                mdatContentLength = muxer.stop();
                muxerStopped = true;
            } else if (mCancelled) {
                throw new EncodingException("Conversion cancelled before muxing started");
            }

        } catch (EncodingException | IOException e) {
            Log.e(TAG, "error converting", e);
            exception = e;
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "error converting", e);
            exception = e;
        } finally {
            if (VERBOSE) Log.d(TAG, "releasing extractor, decoder, encoder, and muxer");
            // Try to release everything we acquired, even if one of the releases fails, in which
            // case we save the first exception we got and re-throw at the end (unless something
            // other exception has already been thrown). This guarantees the first exception thrown
            // is reported as the cause of the error, everything is (attempted) to be released, and
            // all other exceptions appear in the logs.
            try {
                if (videoTrackConverter != null) {
                    videoTrackConverter.release();
                }
            } catch (Exception e) {
                if (exception == null) {
                    exception = e;
                }
            }
            try {
                if (audioTrackConverter != null) {
                    audioTrackConverter.release();
                }
            } catch (Exception e) {
                if (exception == null) {
                    exception = e;
                }
            }
            try {
                if (muxer != null) {
                    if (!muxerStopped && muxerStarted) {
                        muxer.stop();
                    }
                    muxer.release();
                }
            } catch (Exception e) {
                Log.e(TAG, "error while releasing muxer", e);
                if (exception == null) {
                    exception = e;
                }
            }
        }
        if (exception != null) {
            EncodingException encodingException = new EncodingException("Transcode failed", exception);
            if (videoTrackConverter != null) {
                encodingException.isHdrInput     = videoTrackConverter.isHdrInput();
                encodingException.toneMapApplied = videoTrackConverter.isToneMapApplied();
                encodingException.decoderName    = videoTrackConverter.getDecoderName();
                encodingException.encoderName    = videoTrackConverter.getEncoderName();
            } else {
                // VideoTrackConverter failed during creation. Try to determine HDR
                // status independently so the caller can classify the failure.
                try {
                    final MediaExtractor extractor = mInput.createExtractor();
                    try {
                        for (int i = 0; i < extractor.getTrackCount(); i++) {
                            final MediaFormat format = extractor.getTrackFormat(i);
                            if (getMimeTypeFor(format).startsWith("video/")) {
                                encodingException.isHdrInput = MediaCodecCompat.isHdrVideo(format);
                                break;
                            }
                        }
                    } finally {
                        extractor.release();
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Could not determine HDR status for failed transcode", e);
                }
            }
            throw encodingException;
        }

        return mdatContentLength;
    }

    /**
     * Does the actual work for extracting, decoding, encoding and muxing.
     *
     * @return true if the muxer was started, false otherwise.
     */
    private boolean doExtractDecodeEditEncodeMux(
            final @Nullable VideoTrackConverter videoTrackConverter,
            final @Nullable AudioTrackConverter audioTrackConverter,
            final @NonNull Muxer muxer) throws IOException, TranscodingException {

        MediaConverterState oldState = null;
        int stuckFrames = 0;
        boolean muxing = false;
        int percentProcessed = 0;
        long inputDuration = Math.max(
                videoTrackConverter == null ? 0 : videoTrackConverter.mInputDuration,
                audioTrackConverter == null ? 0 : audioTrackConverter.mInputDuration);

        while (!mCancelled &&
                ((videoTrackConverter != null && !videoTrackConverter.mVideoEncoderDone) ||
                 (audioTrackConverter != null &&!audioTrackConverter.mAudioEncoderDone))) {

            final MediaConverterState currentState = new MediaConverterState(videoTrackConverter != null ? videoTrackConverter.dumpState() : null, audioTrackConverter != null ? audioTrackConverter.dumpState() : null, muxing);

            if (VERBOSE) {
                Log.d(TAG, "loop: " + currentState);
            }

            if (muxing && currentState.equals(oldState)) {
                if (++stuckFrames >= STUCK_FRAME_THRESHOLD) {
                    mCancelled = true;
                }
            } else {
                oldState = currentState;
                stuckFrames = 0;
            }

            if (videoTrackConverter != null && (audioTrackConverter == null || audioTrackConverter.mAudioExtractorDone || videoTrackConverter.mMuxingVideoPresentationTime <= audioTrackConverter.mMuxingAudioPresentationTime)) {
                videoTrackConverter.step();
            }

            if (audioTrackConverter != null && (videoTrackConverter == null || videoTrackConverter.mVideoExtractorDone || videoTrackConverter.mMuxingVideoPresentationTime >= audioTrackConverter.mMuxingAudioPresentationTime)) {
                audioTrackConverter.step();
            }

            if (inputDuration != 0 && mListener != null) {
                final long timeFromUs = mTimeFrom <= 0 ? 0 : mTimeFrom * 1000;
                final long timeToUs = mTimeTo <= 0 ? inputDuration : mTimeTo * 1000;
                final int curPercentProcessed = (int) (100 *
                        (Math.max(
                                videoTrackConverter == null ? 0 : videoTrackConverter.mMuxingVideoPresentationTime,
                                audioTrackConverter == null ? 0 : audioTrackConverter.mMuxingAudioPresentationTime)
                         - timeFromUs) / (timeToUs - timeFromUs));

                if (curPercentProcessed != percentProcessed) {
                    percentProcessed = curPercentProcessed;
                    mCancelled = mCancelled || mListener.onProgress(percentProcessed);
                }
            }

            if (!muxing
                    && (videoTrackConverter == null || videoTrackConverter.mEncoderOutputVideoFormat != null)
                    && (audioTrackConverter == null || audioTrackConverter.mEncoderOutputAudioFormat != null)) {
                if (videoTrackConverter != null) {
                    videoTrackConverter.setMuxer(muxer);
                }
                if (audioTrackConverter != null) {
                    audioTrackConverter.setMuxer(muxer);
                }
                Log.d(TAG, "muxer: starting");
                muxer.start();
                muxing = true;
            }
        }

        // Basic sanity checks.
        if (videoTrackConverter != null) {
            videoTrackConverter.verifyEndState();
        }
        if (audioTrackConverter != null) {
            audioTrackConverter.verifyEndState();
        }

        // TODO: Check the generated output file.

        return muxing;
    }

    static String getMimeTypeFor(MediaFormat format) {
        return format.getString(MediaFormat.KEY_MIME);
    }

    /**
     * Returns the first codec capable of encoding the specified MIME type, or null if no match was
     * found.
     */
    static MediaCodecInfo selectCodec(final String mimeType) {
        final List<MediaCodecInfo> codecs = selectCodecs(mimeType);
        return codecs.isEmpty() ? null : codecs.get(0);
    }

    /**
     * Returns all codecs capable of encoding the specified MIME type, ordered with hardware-preferred
     * codecs ({@link MediaCodecList#REGULAR_CODECS}) first, then additional codecs from
     * {@link MediaCodecList#ALL_CODECS} (includes software), deduplicated by name.
     */
    static List<MediaCodecInfo> selectCodecs(final String mimeType) {
        final List<MediaCodecInfo> candidates = new ArrayList<>();
        final Set<String> seen = new HashSet<>();

        for (MediaCodecInfo codecInfo : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (!codecInfo.isEncoder()) continue;
            for (String type : codecInfo.getSupportedTypes()) {
                if (type.equalsIgnoreCase(mimeType) && seen.add(codecInfo.getName())) {
                    candidates.add(codecInfo);
                }
            }
        }

        for (MediaCodecInfo codecInfo : new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()) {
            if (!codecInfo.isEncoder()) continue;
            for (String type : codecInfo.getSupportedTypes()) {
                if (type.equalsIgnoreCase(mimeType) && seen.add(codecInfo.getName())) {
                    candidates.add(codecInfo);
                }
            }
        }

        return candidates;
    }

    interface Output {
        @NonNull
        Muxer createMuxer() throws IOException;
    }

    private static class FileOutput implements Output {

        final File file;

        FileOutput(final @NonNull File file) {
            this.file = file;
        }

        @Override
        public @NonNull
        Muxer createMuxer() throws IOException {
            return new AndroidMuxer(file);
        }
    }

    @RequiresApi(26)
    private static class FileDescriptorOutput implements Output {

        final FileDescriptor fileDescriptor;

        FileDescriptorOutput(final @NonNull FileDescriptor fileDescriptor) {
            this.fileDescriptor = fileDescriptor;
        }

        @Override
        public @NonNull
        Muxer createMuxer() throws IOException {
            return new AndroidMuxer(fileDescriptor);
        }
    }

     private static class StreamOutput implements Output {

        final OutputStream outputStream;

        StreamOutput(final @NonNull OutputStream outputStream) {
            this.outputStream = outputStream;
        }

        @Override
        public @NonNull Muxer createMuxer() {
            return new StreamingMuxer(outputStream);
        }
    }
}
