/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.video.videoconverter;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.video.interfaces.MediaInput;
import org.thoughtcrime.securesms.video.interfaces.Muxer;
import org.thoughtcrime.securesms.video.videoconverter.utils.Extensions;
import org.thoughtcrime.securesms.video.videoconverter.utils.MediaCodecCompat;
import org.thoughtcrime.securesms.video.videoconverter.utils.Preconditions;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

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

    private final long mTimeFrom;
    private final long mTimeTo;

    final long mInputDuration;

    private final MediaExtractor mVideoExtractor;
    private final MediaCodec mVideoDecoder;
    private final MediaCodec mVideoEncoder;

    private final InputSurface mInputSurface;
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

    @RequiresApi(23)
    static @Nullable VideoTrackConverter create(
            final @NonNull MediaInput input,
            final long timeFrom,
            final long timeTo,
            final int videoResolution,
            final int videoBitrate,
            final @NonNull String videoCodec) throws IOException, TranscodingException {

        final MediaExtractor videoExtractor = input.createExtractor();
        final int videoInputTrack = getAndSelectVideoTrackIndex(videoExtractor);
        if (videoInputTrack == -1) {
            videoExtractor.release();
            return null;
        }
        return new VideoTrackConverter(videoExtractor, videoInputTrack, timeFrom, timeTo, videoResolution, videoBitrate, videoCodec);
    }


    @RequiresApi(23)
    private VideoTrackConverter(
            final @NonNull MediaExtractor videoExtractor,
            final int videoInputTrack,
            final long timeFrom,
            final long timeTo,
            final int videoResolution,
            final int videoBitrate,
            final @NonNull String videoCodec) throws IOException, TranscodingException {

        mTimeFrom = timeFrom;
        mTimeTo = timeTo;
        mVideoExtractor = videoExtractor;

        final MediaCodecInfo videoCodecInfo = MediaConverter.selectCodec(videoCodec);
        if (videoCodecInfo == null) {
            // Don't fail CTS if they don't have an AVC codec (not here, anyway).
            Log.e(TAG, "Unable to find an appropriate codec for " + videoCodec);
            throw new FileNotFoundException();
        }
        if (VERBOSE) Log.d(TAG, "video found codec: " + videoCodecInfo.getName());

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
        if (Build.VERSION.SDK_INT >= 31 && isHdr(inputVideoFormat)) {
            outputVideoFormat.setInteger(MediaFormat.KEY_COLOR_TRANSFER_REQUEST, MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
        }
        if (VERBOSE) Log.d(TAG, "video format: " + outputVideoFormat);

        // Create a MediaCodec for the desired codec, then configure it as an encoder with
        // our desired properties. Request a Surface to use for input.
        final AtomicReference<Surface> inputSurfaceReference = new AtomicReference<>();
        mVideoEncoder = createVideoEncoder(videoCodecInfo, outputVideoFormat, inputSurfaceReference);
        mInputSurface = new InputSurface(inputSurfaceReference.get());
        mInputSurface.makeCurrent();
        // Create a MediaCodec for the decoder, based on the extractor's format.
        mOutputSurface = new OutputSurface();

        mOutputSurface.changeFragmentShader(createFragmentShader(
                inputVideoFormat.getInteger(MediaFormat.KEY_WIDTH), inputVideoFormat.getInteger(MediaFormat.KEY_HEIGHT),
                outputWidth, outputHeight));

        mVideoDecoder = createVideoDecoder(inputVideoFormat, mOutputSurface.getSurface());

        mVideoDecoderInputBuffers = mVideoDecoder.getInputBuffers();
        mVideoEncoderOutputBuffers = mVideoEncoder.getOutputBuffers();
        mVideoDecoderOutputBufferInfo = new MediaCodec.BufferInfo();
        mVideoEncoderOutputBufferInfo = new MediaCodec.BufferInfo();

        if (mTimeFrom > 0) {
            mVideoExtractor.seekTo(mTimeFrom * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            Log.i(TAG, "Seek video:" + mTimeFrom + " " + mVideoExtractor.getSampleTime());
        }
    }

    private boolean isHdr(MediaFormat inputVideoFormat) {
        if (Build.VERSION.SDK_INT < 24) {
            return false;
        }
        try {
            final int colorInfo = inputVideoFormat.getInteger(MediaFormat.KEY_COLOR_TRANSFER);
            return colorInfo == MediaFormat.COLOR_TRANSFER_ST2084 || colorInfo == MediaFormat.COLOR_TRANSFER_HLG;
        } catch (NullPointerException npe) {
            // color transfer key does not exist, no color data supplied
            return false;
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
            final @NonNull Surface surface) {
        final Pair<MediaCodec, MediaFormat> decoderPair = MediaCodecCompat.findDecoder(inputFormat);
        final MediaCodec                    decoder     = decoderPair.getFirst();
        decoder.configure(decoderPair.getSecond(), surface, null, 0);
        decoder.start();
        return decoder;
    }

    private @NonNull
    MediaCodec createVideoEncoder(
            final @NonNull MediaCodecInfo codecInfo,
            final @NonNull MediaFormat format,
            final @NonNull AtomicReference<Surface> surfaceReference) throws IOException {
        boolean tonemapRequested = isTonemapEnabled(format);
        final MediaCodec encoder = MediaCodec.createByCodecName(codecInfo.getName());
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        if (tonemapRequested && !isTonemapEnabled(format)) {
            Log.d(TAG, "HDR tone-mapping requested but not supported by the decoder.");
        }
        // Must be called before start()
        surfaceReference.set(encoder.createInputSurface());
        encoder.start();
        return encoder;
    }

    private static boolean isTonemapEnabled(@NonNull MediaFormat format) {
        if (Build.VERSION.SDK_INT < 31) {
            return false;
        }
        try {
            int request = format.getInteger(MediaFormat.KEY_COLOR_TRANSFER_REQUEST);
            return request == MediaFormat.COLOR_TRANSFER_SDR_VIDEO;
        } catch (NullPointerException npe) {
            // transfer request key does not exist, tone mapping not requested
            return false;
        }
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
}
