package org.thoughtcrime.securesms.video.videoconverter;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.video.interfaces.MediaInput;
import org.thoughtcrime.securesms.video.interfaces.Muxer;
import org.thoughtcrime.securesms.video.videoconverter.exceptions.CodecUnavailableException;
import org.thoughtcrime.securesms.video.videoconverter.exceptions.HdrDecoderUnavailableException;
import org.thoughtcrime.securesms.video.videoconverter.utils.Extensions;
import org.thoughtcrime.securesms.video.videoconverter.utils.MediaCodecCompat;
import org.thoughtcrime.securesms.video.videoconverter.utils.Preconditions;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import kotlin.Pair;

final class VideoTrackConverter {

    private static final String TAG = "media-converter";
    private static final boolean VERBOSE = false; // lots of logging

    private static final int OUTPUT_VIDEO_IFRAME_INTERVAL = 1; // 1 second between I-frames
    private static final int OUTPUT_VIDEO_FRAME_RATE = 30; // needed only for MediaFormat.KEY_I_FRAME_INTERVAL to work; the actual frame rate matches the source

    private static final int TIMEOUT_USEC = 10000;

    private static final String MEDIA_FORMAT_KEY_DISPLAY_WIDTH  = "display-width";
    private static final String MEDIA_FORMAT_KEY_DISPLAY_HEIGHT = "display-height";

    private static final float FRAME_RATE_TOLERANCE = 0.05f; // tolerance for transcoding VFR -> CFR

    private boolean mIsHdrInput;
    private boolean mToneMapApplied;
    private String  mDecoderName;
    private String  mEncoderName;

    private final long mTimeFrom;
    private final long mTimeTo;

    final long mInputDuration;

    private final MediaExtractor mVideoExtractor;
    private final MediaCodec mVideoDecoder;
    private MediaCodec mVideoEncoder;

    private InputSurface mInputSurface;
    private final OutputSurface mOutputSurface;

    private final ByteBuffer[] mVideoDecoderInputBuffers;
    private ByteBuffer[] mVideoEncoderOutputBuffers;
    private final MediaCodec.BufferInfo mVideoDecoderOutputBufferInfo;
    private final MediaCodec.BufferInfo mVideoEncoderOutputBufferInfo;

    MediaFormat mEncoderOutputVideoFormat;

    boolean mVideoExtractorDone;
    private boolean mVideoDecoderDone;
    boolean mVideoEncoderDone;

    private int mOutputVideoTrack = -1;

    long mMuxingVideoPresentationTime;

    private int mVideoExtractedFrameCount;
    private int mVideoDecodedFrameCount;
    private int mVideoEncodedFrameCount;

    private Muxer mMuxer;

    static @Nullable VideoTrackConverter create(
            final @NonNull MediaInput input,
            final long timeFrom,
            final long timeTo,
            final int videoResolution,
            final int videoBitrate,
            final @NonNull String videoCodec,
            final @NonNull Set<String> excludedDecoders) throws IOException, TranscodingException {

        final MediaExtractor videoExtractor = input.createExtractor();
        final int videoInputTrack = getAndSelectVideoTrackIndex(videoExtractor);
        if (videoInputTrack == -1) {
            videoExtractor.release();
            return null;
        }
        return new VideoTrackConverter(videoExtractor, videoInputTrack, timeFrom, timeTo, videoResolution, videoBitrate, videoCodec, excludedDecoders);
    }


    private VideoTrackConverter(
            final @NonNull MediaExtractor videoExtractor,
            final int videoInputTrack,
            final long timeFrom,
            final long timeTo,
            final int videoResolution,
            final int videoBitrate,
            final @NonNull String videoCodec,
            final @NonNull Set<String> excludedDecoders) throws IOException, TranscodingException {

        mTimeFrom = timeFrom;
        mTimeTo = timeTo;
        mVideoExtractor = videoExtractor;

        final List<MediaCodecInfo> videoCodecCandidates = MediaConverter.selectCodecs(videoCodec);
        if (videoCodecCandidates.isEmpty()) {
            // Don't fail CTS if they don't have an AVC codec (not here, anyway).
            Log.e(TAG, "Unable to find an appropriate codec for " + videoCodec);
            throw new FileNotFoundException();
        }
        if (VERBOSE) Log.d(TAG, "video found codecs: " + videoCodecCandidates.size());

        final MediaFormat inputVideoFormat = mVideoExtractor.getTrackFormat(videoInputTrack);

        mInputDuration = inputVideoFormat.containsKey(MediaFormat.KEY_DURATION) ? inputVideoFormat.getLong(MediaFormat.KEY_DURATION) : 0;

        final int rotation = inputVideoFormat.containsKey(MediaFormat.KEY_ROTATION) ? inputVideoFormat.getInteger(MediaFormat.KEY_ROTATION) : 0;
        final int width = inputVideoFormat.containsKey(MEDIA_FORMAT_KEY_DISPLAY_WIDTH)
                          ? inputVideoFormat.getInteger(MEDIA_FORMAT_KEY_DISPLAY_WIDTH)
                          : inputVideoFormat.getInteger(MediaFormat.KEY_WIDTH);
        final int height = inputVideoFormat.containsKey(MEDIA_FORMAT_KEY_DISPLAY_HEIGHT)
                           ? inputVideoFormat.getInteger(MEDIA_FORMAT_KEY_DISPLAY_HEIGHT)
                           : inputVideoFormat.getInteger(MediaFormat.KEY_HEIGHT);
        int outputWidth = width;
        int outputHeight = height;
        if (outputWidth < outputHeight) {
            outputWidth = videoResolution;
            outputHeight = height * outputWidth / width;
        } else {
            outputHeight = videoResolution;
            outputWidth = width * outputHeight / height;
        }
        // many encoders do not work when height and width are not multiple of 16 (also, some iPhones do not play some heights)
        outputHeight = (outputHeight + 7) & ~0xF;
        outputWidth = (outputWidth + 7) & ~0xF;

        final int outputWidthRotated;
        final int outputHeightRotated;
        if ((rotation % 180 == 90)) {
            //noinspection SuspiciousNameCombination
            outputWidthRotated = outputHeight;
            //noinspection SuspiciousNameCombination
            outputHeightRotated = outputWidth;
        } else {
            outputWidthRotated = outputWidth;
            outputHeightRotated = outputHeight;
        }

        final MediaFormat outputVideoFormat = MediaFormat.createVideoFormat(videoCodec, outputWidthRotated, outputHeightRotated);

        // Set some properties. Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        outputVideoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        outputVideoFormat.setInteger(MediaFormat.KEY_BIT_RATE, videoBitrate);
        outputVideoFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
        outputVideoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, OUTPUT_VIDEO_FRAME_RATE);
        outputVideoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, OUTPUT_VIDEO_IFRAME_INTERVAL);
        if (VERBOSE) Log.d(TAG, "video format: " + outputVideoFormat);

        final String fragmentShader = createFragmentShader(
                inputVideoFormat.getInteger(MediaFormat.KEY_WIDTH), inputVideoFormat.getInteger(MediaFormat.KEY_HEIGHT),
                outputWidth, outputHeight);

        // Create encoder, decoder, and surfaces. The encoder's start() is deferred
        // until after the decoder is created, so that the decoder gets first access to
        // hardware codec resources on memory-constrained devices. If start() fails
        // (e.g. NO_MEMORY on a resource-constrained device), we try the next encoder
        // candidate while keeping the same decoder and OutputSurface.
        mVideoEncoder = createVideoEncoder(videoCodecCandidates, outputVideoFormat);
        mInputSurface = new InputSurface(mVideoEncoder.createInputSurface());
        mInputSurface.makeCurrent();
        mOutputSurface = new OutputSurface();
        mOutputSurface.changeFragmentShader(fragmentShader);
        mVideoDecoder = createVideoDecoder(inputVideoFormat, mOutputSurface.getSurface(), excludedDecoders);
        startEncoderWithFallback(videoCodecCandidates, outputVideoFormat);

        mVideoDecoderInputBuffers = mVideoDecoder.getInputBuffers();
        mVideoEncoderOutputBuffers = mVideoEncoder.getOutputBuffers();
        mVideoDecoderOutputBufferInfo = new MediaCodec.BufferInfo();
        mVideoEncoderOutputBufferInfo = new MediaCodec.BufferInfo();

        if (mTimeFrom > 0) {
            mVideoExtractor.seekTo(mTimeFrom * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            Log.i(TAG, "Seek video:" + mTimeFrom + " " + mVideoExtractor.getSampleTime());
        }
    }

    void setMuxer(final @NonNull Muxer muxer) throws IOException {
        mMuxer = muxer;
        if (mEncoderOutputVideoFormat != null) {
            Log.d(TAG, "muxer: adding video track.");
            mOutputVideoTrack = muxer.addTrack(mEncoderOutputVideoFormat);
        }
    }

    void step() throws IOException, TranscodingException {
        // Extract video from file and feed to decoder.
        // Do not extract video if we have determined the output format but we are not yet
        // ready to mux the frames.
        while (!mVideoExtractorDone
                && (mEncoderOutputVideoFormat == null || mMuxer != null)) {
            int decoderInputBufferIndex = mVideoDecoder.dequeueInputBuffer(TIMEOUT_USEC);
            if (decoderInputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (VERBOSE) Log.d(TAG, "no video decoder input buffer");
                break;
            }
            if (VERBOSE) {
                Log.d(TAG, "video decoder: returned input buffer: " + decoderInputBufferIndex);
            }
            final ByteBuffer decoderInputBuffer = mVideoDecoderInputBuffers[decoderInputBufferIndex];
            final int size = mVideoExtractor.readSampleData(decoderInputBuffer, 0);
            final long presentationTime = mVideoExtractor.getSampleTime();
            if (VERBOSE) {
                Log.d(TAG, "video extractor: returned buffer of size " + size);
                Log.d(TAG, "video extractor: returned buffer for time " + presentationTime);
            }
            mVideoExtractorDone = size < 0 || (mTimeTo > 0 && presentationTime > mTimeTo * 1000);

            if (mVideoExtractorDone) {
                if (VERBOSE) Log.d(TAG, "video extractor: EOS");
                mVideoDecoder.queueInputBuffer(
                        decoderInputBufferIndex,
                        0,
                        0,
                        0,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            } else {
                mVideoDecoder.queueInputBuffer(
                        decoderInputBufferIndex,
                        0,
                        size,
                        presentationTime,
                        mVideoExtractor.getSampleFlags());
            }
            mVideoExtractor.advance();
            mVideoExtractedFrameCount++;
            // We extracted a frame, let's try something else next.
            break;
        }

        // Poll output frames from the video decoder and feed the encoder.
        while (!mVideoDecoderDone && (mEncoderOutputVideoFormat == null || mMuxer != null)) {
            final int decoderOutputBufferIndex =
                    mVideoDecoder.dequeueOutputBuffer(
                            mVideoDecoderOutputBufferInfo, TIMEOUT_USEC);
            if (decoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (VERBOSE) Log.d(TAG, "no video decoder output buffer");
                break;
            }
            if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                if (VERBOSE) Log.d(TAG, "video decoder: output buffers changed");
                break;
            }
            if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (VERBOSE) {
                    Log.d(TAG, "video decoder: output format changed: " + mVideoDecoder.getOutputFormat());
                }
                break;
            }
            if (VERBOSE) {
                Log.d(TAG, "video decoder: returned output buffer: "
                        + decoderOutputBufferIndex);
                Log.d(TAG, "video decoder: returned buffer of size "
                        + mVideoDecoderOutputBufferInfo.size);
            }
            if ((mVideoDecoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                if (VERBOSE) Log.d(TAG, "video decoder: codec config buffer");
                mVideoDecoder.releaseOutputBuffer(decoderOutputBufferIndex, false);
                break;
            }
            if (mVideoDecoderOutputBufferInfo.presentationTimeUs < mTimeFrom * 1000 &&
                    (mVideoDecoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0) {
                if (VERBOSE) Log.d(TAG, "video decoder: frame prior to " + mVideoDecoderOutputBufferInfo.presentationTimeUs);
                mVideoDecoder.releaseOutputBuffer(decoderOutputBufferIndex, false);
                break;
            }
            if (VERBOSE) {
                Log.d(TAG, "video decoder: returned buffer for time " + mVideoDecoderOutputBufferInfo.presentationTimeUs);
            }
            boolean render = mVideoDecoderOutputBufferInfo.size != 0;
            mVideoDecoder.releaseOutputBuffer(decoderOutputBufferIndex, render);
            if (render) {
                if (VERBOSE) Log.d(TAG, "output surface: await new image");
                mOutputSurface.awaitNewImage();
                // Edit the frame and send it to the encoder.
                if (VERBOSE) Log.d(TAG, "output surface: draw image");
                mOutputSurface.drawImage();
                mInputSurface.setPresentationTime(mVideoDecoderOutputBufferInfo.presentationTimeUs * 1000);
                if (VERBOSE) Log.d(TAG, "input surface: swap buffers");
                mInputSurface.swapBuffers();
                if (VERBOSE) Log.d(TAG, "video encoder: notified of new frame");
            }
            if ((mVideoDecoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                if (VERBOSE) Log.d(TAG, "video decoder: EOS");
                mVideoDecoderDone = true;
                mVideoEncoder.signalEndOfInputStream();
            }
            mVideoDecodedFrameCount++;
            // We extracted a pending frame, let's try something else next.
            break;
        }

        // Poll frames from the video encoder and send them to the muxer.
        while (!mVideoEncoderDone && (mEncoderOutputVideoFormat == null || mMuxer != null)) {
            final int encoderOutputBufferIndex = mVideoEncoder.dequeueOutputBuffer(mVideoEncoderOutputBufferInfo, TIMEOUT_USEC);
            if (encoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (VERBOSE) Log.d(TAG, "no video encoder output buffer");
                if (mVideoDecoderDone) {
                    // on some devices and encoder stops after signalEndOfInputStream
                    Log.w(TAG, "mVideoDecoderDone, but didn't get BUFFER_FLAG_END_OF_STREAM");
                    mVideoEncodedFrameCount = mVideoDecodedFrameCount;
                    mVideoEncoderDone = true;
                }
                break;
            }
            if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                if (VERBOSE) Log.d(TAG, "video encoder: output buffers changed");
                mVideoEncoderOutputBuffers = mVideoEncoder.getOutputBuffers();
                break;
            }
            if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (VERBOSE) Log.d(TAG, "video encoder: output format changed");
                Preconditions.checkState("video encoder changed its output format again?", mOutputVideoTrack < 0);
                mEncoderOutputVideoFormat = mVideoEncoder.getOutputFormat();
                break;
            }
            Preconditions.checkState("should have added track before processing output", mMuxer != null);
            if (VERBOSE) {
                Log.d(TAG, "video encoder: returned output buffer: " + encoderOutputBufferIndex);
                Log.d(TAG, "video encoder: returned buffer of size " + mVideoEncoderOutputBufferInfo.size);
            }
            final ByteBuffer encoderOutputBuffer = mVideoEncoderOutputBuffers[encoderOutputBufferIndex];
            if ((mVideoEncoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                if (VERBOSE) Log.d(TAG, "video encoder: codec config buffer");
                // Simply ignore codec config buffers.
                mVideoEncoder.releaseOutputBuffer(encoderOutputBufferIndex, false);
                break;
            }
            if (VERBOSE) {
                Log.d(TAG, "video encoder: returned buffer for time " + mVideoEncoderOutputBufferInfo.presentationTimeUs);
            }
            if (mVideoEncoderOutputBufferInfo.size != 0) {
                mMuxer.writeSampleData(mOutputVideoTrack, encoderOutputBuffer, mVideoEncoderOutputBufferInfo);
                mMuxingVideoPresentationTime = Math.max(mMuxingVideoPresentationTime, mVideoEncoderOutputBufferInfo.presentationTimeUs);
            }
            if ((mVideoEncoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                if (VERBOSE) Log.d(TAG, "video encoder: EOS");
                mVideoEncoderDone = true;
            }
            mVideoEncoder.releaseOutputBuffer(encoderOutputBufferIndex, false);
            mVideoEncodedFrameCount++;
            // We enqueued an encoded frame, let's try something else next.
            break;
        }
    }

    void release() throws Exception {
        Exception exception = null;
        try {
            if (mVideoExtractor != null) {
                mVideoExtractor.release();
            }
        } catch (Exception e) {
            Log.e(TAG, "error while releasing mVideoExtractor", e);
            exception = e;
        }
        try {
            if (mVideoDecoder != null) {
                mVideoDecoder.stop();
                mVideoDecoder.release();
            }
        } catch (Exception e) {
            Log.e(TAG, "error while releasing mVideoDecoder", e);
            if (exception == null) {
                exception = e;
            }
        }
        try {
            if (mOutputSurface != null) {
                mOutputSurface.release();
            }
        } catch (Exception e) {
            Log.e(TAG, "error while releasing mOutputSurface", e);
            if (exception == null) {
                exception = e;
            }
        }
        try {
            if (mInputSurface != null) {
                mInputSurface.release();
            }
        } catch (Exception e) {
            Log.e(TAG, "error while releasing mInputSurface", e);
            if (exception == null) {
                exception = e;
            }
        }
        try {
            if (mVideoEncoder != null) {
                mVideoEncoder.stop();
                mVideoEncoder.release();
            }
        } catch (Exception e) {
            Log.e(TAG, "error while releasing mVideoEncoder", e);
            if (exception == null) {
                exception = e;
            }
        }
        if (exception != null) {
            throw exception;
        }
    }

    VideoTrackConverterState dumpState() {
        return new VideoTrackConverterState(
            mVideoExtractedFrameCount, mVideoExtractorDone,
            mVideoDecodedFrameCount, mVideoDecoderDone,
            mVideoEncodedFrameCount, mVideoEncoderDone,
            mMuxer != null, mOutputVideoTrack);
    }

    void verifyEndState() {
        Preconditions.checkState("encoded (" + mVideoEncodedFrameCount + ") and decoded (" + mVideoDecodedFrameCount + ") video frame counts should match", Extensions.isWithin(mVideoDecodedFrameCount, mVideoEncodedFrameCount, FRAME_RATE_TOLERANCE));
        Preconditions.checkState("decoded frame count should be less than extracted frame count", mVideoDecodedFrameCount <= mVideoExtractedFrameCount);
    }

    boolean isHdrInput() { return mIsHdrInput; }
    boolean isToneMapApplied() { return mToneMapApplied; }
    String getDecoderName() { return mDecoderName; }
    String getEncoderName() { return mEncoderName; }

    private static String createFragmentShader(
            final int srcWidth,
            final int srcHeight,
            final int dstWidth,
            final int dstHeight) {
        final float kernelSizeX = (float) srcWidth / (float) dstWidth;
        final float kernelSizeY = (float) srcHeight / (float) dstHeight;
        Log.i(TAG, "kernel " + kernelSizeX + "x" + kernelSizeY);
        final String shader;
        if (kernelSizeX <= 2 && kernelSizeY <= 2) {
            shader =
                    "#extension GL_OES_EGL_image_external : require\n" +
                            "precision mediump float;\n" +      // highp here doesn't seem to matter
                            "varying vec2 vTextureCoord;\n" +
                            "uniform samplerExternalOES sTexture;\n" +
                            "void main() {\n" +
                            "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                            "}\n";
        } else {
            final int kernelRadiusX = (int) Math.ceil(kernelSizeX - .1f) / 2;
            final int kernelRadiusY = (int) Math.ceil(kernelSizeY - .1f) / 2;
            final float stepX = kernelSizeX / (1 + 2 * kernelRadiusX) * (1f / srcWidth);
            final float stepY = kernelSizeY / (1 + 2 * kernelRadiusY) * (1f / srcHeight);
            final float sum = (1 + 2 * kernelRadiusX) * (1 + 2 * kernelRadiusY);
            final StringBuilder colorLoop = new StringBuilder();
            for (int i = -kernelRadiusX; i <=kernelRadiusX; i++) {
                for (int j = -kernelRadiusY; j <=kernelRadiusY; j++) {
                    if (i != 0 || j != 0) {
                        colorLoop.append("      + texture2D(sTexture, vTextureCoord.xy + vec2(")
                                .append(i * stepX).append(", ").append(j * stepY).append("))\n");
                    }
                }
            }
            shader =
                "#extension GL_OES_EGL_image_external : require\n" +
                "precision mediump float;\n" +      // highp here doesn't seem to matter
                "varying vec2 vTextureCoord;\n" +
                "uniform samplerExternalOES sTexture;\n" +
                "void main() {\n" +
                "    gl_FragColor = (texture2D(sTexture, vTextureCoord)\n" +
                colorLoop +
                "    ) / " + sum + ";\n" +
                "}\n";
        }
        Log.i(TAG, shader);
        return shader;
    }

    private @NonNull
    MediaCodec createVideoDecoder(
            final @NonNull MediaFormat inputFormat,
            final @NonNull Surface surface,
            final @NonNull Set<String> excludedDecoders) throws IOException {
        final boolean               isHdr              = MediaCodecCompat.isHdrVideo(inputFormat);
        final boolean               requestToneMapping = Build.VERSION.SDK_INT >= 31 && isHdr;
        final List<Pair<String, MediaFormat>> allCandidates = MediaCodecCompat.findDecoderCandidates(inputFormat);
        final List<Pair<String, MediaFormat>> candidates    = new ArrayList<>();
        for (Pair<String, MediaFormat> c : allCandidates) {
            if (!excludedDecoders.contains(c.getFirst())) {
                candidates.add(c);
            }
        }

        mIsHdrInput = isHdr;
        Exception lastException = null;

        for (int i = 0; i < candidates.size(); i++) {
            final Pair<String, MediaFormat> candidate = candidates.get(i);
            final String      codecName  = candidate.getFirst();
            final MediaFormat baseFormat = candidate.getSecond();
            MediaCodec decoder = null;

            try {
                decoder = MediaCodec.createByCodecName(codecName);

                // For HDR video on API 31+, try requesting SDR tone-mapping.
                // Some codecs reject this key, so we catch the error and retry without it.
                if (requestToneMapping) {
                    try {
                        final MediaFormat toneMapFormat = new MediaFormat(baseFormat);
                        toneMapFormat.setInteger(MediaFormat.KEY_COLOR_TRANSFER_REQUEST, MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
                        decoder.configure(toneMapFormat, surface, null, 0);
                        decoder.start();

                        mToneMapApplied = isToneMapEffective(decoder, codecName);
                        mDecoderName = codecName;
                        if (i > 0) {
                            Log.w(TAG, "Video decoder: succeeded with fallback codec " + codecName + " (attempt " + (i + 1) + " of " + candidates.size() + ")");
                        }
                        return decoder;
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        Log.w(TAG, "Video decoder: codec " + codecName + " rejected tone-mapping request, retrying without (attempt " + (i + 1) + " of " + candidates.size() + ")", e);
                        decoder.release();
                        decoder = MediaCodec.createByCodecName(codecName);
                    }
                }

                decoder.configure(baseFormat, surface, null, 0);
                decoder.start();

                mDecoderName = codecName;
                if (i > 0 || requestToneMapping) {
                    Log.w(TAG, "Video decoder: succeeded with codec " + codecName + (requestToneMapping ? " (no tone-mapping)" : "") + " (attempt " + (i + 1) + " of " + candidates.size() + ")");
                }
                return decoder;
            } catch (IllegalArgumentException | IllegalStateException e) {
                Log.w(TAG, "Video decoder: codec " + codecName + " failed (attempt " + (i + 1) + " of " + candidates.size() + ")", e);
                lastException = e;
                if (decoder != null) {
                    decoder.release();
                }
            } catch (IOException e) {
                Log.w(TAG, "Video decoder: codec " + codecName + " failed to create (attempt " + (i + 1) + " of " + candidates.size() + ")", e);
                lastException = e;
            }
        }

        if (mIsHdrInput) {
            throw new HdrDecoderUnavailableException("All video decoder codecs failed for HDR video", lastException);
        }
        throw new CodecUnavailableException("All video decoder codecs failed", lastException);
    }

    /**
     * Creates and configures a video encoder but does NOT start it. The caller must call
     * {@link MediaCodec#createInputSurface()} (between configure and start) and then
     * {@link MediaCodec#start()} after the decoder has been created.
     */
    private @NonNull
    MediaCodec createVideoEncoder(
            final @NonNull List<MediaCodecInfo> codecCandidates,
            final @NonNull MediaFormat format) throws IOException {
        Exception lastException = null;

        for (int i = 0; i < codecCandidates.size(); i++) {
            final MediaCodecInfo codecInfo = codecCandidates.get(i);
            MediaCodec encoder = null;

            try {
                encoder = MediaCodec.createByCodecName(codecInfo.getName());
                encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                mEncoderName = codecInfo.getName();
                if (i > 0) {
                    Log.w(TAG, "Video encoder: succeeded with fallback codec " + codecInfo.getName() + " (attempt " + (i + 1) + " of " + codecCandidates.size() + ")");
                }
                return encoder;
            } catch (IllegalArgumentException | IllegalStateException e) {
                Log.w(TAG, "Video encoder: codec " + codecInfo.getName() + " failed (attempt " + (i + 1) + " of " + codecCandidates.size() + ")", e);
                lastException = e;
                if (encoder != null) {
                    encoder.release();
                }
            }
        }

        throw new CodecUnavailableException("All video encoder codecs failed", lastException);
    }

    /**
     * Attempts to start the current encoder ({@link #mVideoEncoder}). If start() fails,
     * iterates through the remaining encoder candidates from {@code codecCandidates},
     * replacing the encoder and its {@link InputSurface} on each attempt. The decoder
     * and {@link OutputSurface} are independent of the encoder and remain unchanged.
     */
    private void startEncoderWithFallback(
            final @NonNull List<MediaCodecInfo> codecCandidates,
            final @NonNull MediaFormat format) throws IOException {
        Exception lastException = null;

        for (int i = 0; i < codecCandidates.size(); i++) {
            final MediaCodecInfo codecInfo = codecCandidates.get(i);

            if (i > 0) {
                // Replace the encoder with the next candidate.
                mVideoEncoder.release();
                mInputSurface.release();

                try {
                    mVideoEncoder = MediaCodec.createByCodecName(codecInfo.getName());
                    mVideoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                    mInputSurface = new InputSurface(mVideoEncoder.createInputSurface());
                    mInputSurface.makeCurrent();
                    mEncoderName = codecInfo.getName();
                } catch (IllegalArgumentException | IllegalStateException | TranscodingException e) {
                    Log.w(TAG, "Video encoder: codec " + codecInfo.getName() + " failed to configure (attempt " + (i + 1) + " of " + codecCandidates.size() + ")", e);
                    lastException = e;
                    continue;
                }
            } else if (!codecInfo.getName().equals(mEncoderName)) {
                // First iteration but createVideoEncoder selected a different codec
                // (i.e. the first candidate failed to configure). Skip until we reach
                // the one that was actually configured.
                continue;
            }

            try {
                mVideoEncoder.start();
                if (i > 0) {
                    Log.w(TAG, "Video encoder: succeeded with fallback codec " + codecInfo.getName() + " (attempt " + (i + 1) + " of " + codecCandidates.size() + ")");
                }
                return;
            } catch (IllegalStateException e) {
                Log.w(TAG, "Video encoder: codec " + codecInfo.getName() + " failed to start (attempt " + (i + 1) + " of " + codecCandidates.size() + ")", e);
                lastException = e;
            }
        }

        throw new CodecUnavailableException("All video encoder codecs failed to start", lastException);
    }

    private static int getAndSelectVideoTrackIndex(@NonNull MediaExtractor extractor) {
        for (int index = 0; index < extractor.getTrackCount(); ++index) {
            if (VERBOSE) {
                Log.d(TAG, "format for track " + index + " is " + MediaConverter.getMimeTypeFor(extractor.getTrackFormat(index)));
            }
            if (isVideoFormat(extractor.getTrackFormat(index))) {
                extractor.selectTrack(index);
                return index;
            }
        }
        return -1;
    }

    private static boolean isVideoFormat(final @NonNull MediaFormat format) {
        return MediaConverter.getMimeTypeFor(format).startsWith("video/");
    }

    /**
     * Checks whether HDR-to-SDR tone-mapping is effective after the decoder has been configured
     * and started with {@link MediaFormat#KEY_COLOR_TRANSFER_REQUEST}. Some codecs (especially
     * software decoders and some hardware decoders) accept the tone-mapping key without error
     * but don't actually perform the conversion.
     */
    private static boolean isToneMapEffective(final @NonNull MediaCodec decoder, final @NonNull String codecName) {
        // Software codecs never perform HDR→SDR tone-mapping.
        String lower = codecName.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("omx.google.") || lower.startsWith("c2.android.")) {
            Log.w(TAG, "Video decoder: software codec " + codecName + " cannot perform HDR tone-mapping");
            return false;
        }

        // For hardware codecs, verify the output format. If the output transfer function
        // is still HDR (ST2084 or HLG), the decoder accepted the request but isn't honoring it.
        try {
            MediaFormat outputFormat = decoder.getOutputFormat();
            if (outputFormat.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                int transfer = outputFormat.getInteger(MediaFormat.KEY_COLOR_TRANSFER);
                if (transfer == MediaFormat.COLOR_TRANSFER_ST2084 || transfer == MediaFormat.COLOR_TRANSFER_HLG) {
                    Log.w(TAG, "Video decoder: codec " + codecName + " accepted tone-mapping but output transfer is " + transfer + " (still HDR)");
                    return false;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Video decoder: could not verify tone-mapping for codec " + codecName, e);
        }

        return true;
    }

}

