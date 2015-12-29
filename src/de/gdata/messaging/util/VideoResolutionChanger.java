package de.gdata.messaging.util;

import android.annotation.TargetApi;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;

import org.thoughtcrime.securesms.mms.AttachmentManager;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

@TargetApi(19)
public class VideoResolutionChanger {

    private static final int TIMEOUT_USEC = 10000;

    public static final String OUTPUT_VIDEO_MIME_TYPE = "video/avc";

    private static final int OUTPUT_VIDEO_BIT_RATE = 2000 * 1024;
    private static final int OUTPUT_VIDEO_FRAME_RATE = 24;

    private static final int OUTPUT_VIDEO_IFRAME_INTERVAL = 10;
    private static final int OUTPUT_VIDEO_COLOR_FORMAT =
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;

    private static final String OUTPUT_AUDIO_MIME_TYPE = "audio/mp4a-latm";
    private static final int OUTPUT_AUDIO_CHANNEL_COUNT = 2;
    private static final int OUTPUT_AUDIO_BIT_RATE = 128 * 1024;
    private static final int OUTPUT_AUDIO_AAC_PROFILE =
            MediaCodecInfo.CodecProfileLevel.AACObjectHE;
    private static final int OUTPUT_AUDIO_SAMPLE_RATE_HZ = 44100;

    private boolean compressingSuccessful = true;

    private int mWidth = 640;
    private int mHeight = 360;
    private String mOutputFile, mInputFile;

    public static String COMPRESSING_ERROR = "error";

    public String changeResolution(Context context, Uri f)
            throws Throwable {

        mInputFile = getPath(context, f);

        final File outFile = AttachmentManager.getOutputMediaFile(context, AttachmentManager.MEDIA_TYPE_VIDEO);
        if(!outFile.exists())
            outFile.createNewFile();

        mOutputFile=outFile.getAbsolutePath();

        ChangerWrapper.changeResolutionInSeparatedThread(this);
        if(!compressingSuccessful) {
        mOutputFile = COMPRESSING_ERROR;
        }
        return mOutputFile;
    }

    private static class ChangerWrapper implements Runnable {

        private Throwable mThrowable;
        private VideoResolutionChanger mChanger;

        private ChangerWrapper(VideoResolutionChanger changer) {
            mChanger = changer;
        }

        @Override
        public void run() {
            try {
                mChanger.prepareAndChangeResolution();
            } catch (Throwable th) {
                mThrowable = th;
            }
        }

        public static void changeResolutionInSeparatedThread(VideoResolutionChanger changer)
                throws Throwable {
            ChangerWrapper wrapper = new ChangerWrapper(changer);
            Thread th = new Thread(wrapper, ChangerWrapper.class.getSimpleName());
            th.start();
            th.join();
            if (wrapper.mThrowable != null)
                throw wrapper.mThrowable;
        }
    }

    private void prepareAndChangeResolution() throws Exception {
        Exception exception = null;

        MediaCodecInfo videoCodecInfo = selectCodec(OUTPUT_VIDEO_MIME_TYPE);
        if (videoCodecInfo == null)
            return;
        MediaCodecInfo audioCodecInfo = selectCodec(OUTPUT_AUDIO_MIME_TYPE);
        if (audioCodecInfo == null)
            return;

        MediaExtractor videoExtractor = null;
        MediaExtractor audioExtractor = null;
        OutputSurface outputSurface = null;
        MediaCodec videoDecoder = null;
        MediaCodec audioDecoder = null;
        MediaCodec videoEncoder = null;
        MediaCodec audioEncoder = null;
        MediaMuxer muxer = null;
        InputSurface inputSurface = null;
        try {
            videoExtractor = createExtractor();
            int videoInputTrack = getAndSelectVideoTrackIndex(videoExtractor);
            MediaFormat inputFormat = videoExtractor.getTrackFormat(videoInputTrack);

            MediaMetadataRetriever m = new MediaMetadataRetriever();
            m.setDataSource(mInputFile);
            Bitmap thumbnail = m.getFrameAtTime();
            int inputWidth = thumbnail.getWidth(),
                    inputHeight = thumbnail.getHeight();

            if(inputWidth>inputHeight){
                if(mWidth<mHeight){
                    int w = mWidth;
                    mWidth=mHeight;
                    mHeight=w;
                }
            }
            else{
                if(mWidth>mHeight){
                    int w = mWidth;
                    mWidth=mHeight;
                    mHeight=w;
                }
            }

            MediaFormat outputVideoFormat =
                    MediaFormat.createVideoFormat(OUTPUT_VIDEO_MIME_TYPE, mWidth, mHeight);
            outputVideoFormat.setInteger(
                    MediaFormat.KEY_COLOR_FORMAT, OUTPUT_VIDEO_COLOR_FORMAT);
            outputVideoFormat.setInteger(MediaFormat.KEY_BIT_RATE, OUTPUT_VIDEO_BIT_RATE);
            outputVideoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, OUTPUT_VIDEO_FRAME_RATE);
            outputVideoFormat.setInteger(
                    MediaFormat.KEY_I_FRAME_INTERVAL, OUTPUT_VIDEO_IFRAME_INTERVAL);

            AtomicReference<Surface> inputSurfaceReference = new AtomicReference<Surface>();
            videoEncoder = createVideoEncoder(
                    videoCodecInfo, outputVideoFormat, inputSurfaceReference);
            inputSurface = new InputSurface(inputSurfaceReference.get());
            inputSurface.makeCurrent();

            outputSurface = new OutputSurface();
            videoDecoder = createVideoDecoder(inputFormat, outputSurface.getSurface());

            audioExtractor = createExtractor();
            int audioInputTrack = getAndSelectAudioTrackIndex(audioExtractor);
            MediaFormat inputAudioFormat = audioExtractor.getTrackFormat(audioInputTrack);
            MediaFormat outputAudioFormat =
                    MediaFormat.createAudioFormat(
                            OUTPUT_AUDIO_MIME_TYPE, OUTPUT_AUDIO_SAMPLE_RATE_HZ,
                            OUTPUT_AUDIO_CHANNEL_COUNT);
            outputAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, OUTPUT_AUDIO_BIT_RATE);
            outputAudioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, OUTPUT_AUDIO_AAC_PROFILE);

            audioEncoder = createAudioEncoder(audioCodecInfo, outputAudioFormat);
            audioDecoder = createAudioDecoder(inputAudioFormat);

            muxer = new MediaMuxer(mOutputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            changeResolution(videoExtractor, audioExtractor,
                    videoDecoder, videoEncoder,
                    audioDecoder, audioEncoder,
                    muxer, inputSurface, outputSurface);
        } finally {
            try {
                if (videoExtractor != null)
                    videoExtractor.release();
            } catch(Exception e) {
                if (exception == null)
                    exception = e;
            }
            try {
                if (audioExtractor != null)
                    audioExtractor.release();
            } catch(Exception e) {
                if (exception == null)
                    exception = e;
            }
            try {
                if (videoDecoder != null) {
                    videoDecoder.stop();
                    videoDecoder.release();
                }
            } catch(Exception e) {
                if (exception == null)
                    exception = e;
            }
            try {
                if (outputSurface != null) {
                    outputSurface.release();
                }
            } catch(Exception e) {
                if (exception == null)
                    exception = e;
            }
            try {
                if (videoEncoder != null) {
                    videoEncoder.stop();
                    videoEncoder.release();
                }
            } catch(Exception e) {
                if (exception == null)
                    exception = e;
            }
            try {
                if (audioDecoder != null) {
                    audioDecoder.stop();
                    audioDecoder.release();
                }
            } catch(Exception e) {
                if (exception == null)
                    exception = e;
            }
            try {
                if (audioEncoder != null) {
                    audioEncoder.stop();
                    audioEncoder.release();
                }
            } catch(Exception e) {
                if (exception == null)
                    exception = e;
            }
            try {
                if (muxer != null) {
                    muxer.stop();
                    muxer.release();
                }
            } catch(Exception e) {
                if (exception == null)
                    exception = e;
            }
            try {
                if (inputSurface != null)
                    inputSurface.release();
            } catch(Exception e) {
                if (exception == null)
                    exception = e;
            }
        }
        if (exception != null) {
            compressingSuccessful = false;
            throw exception;
        }
    }

    private MediaExtractor createExtractor() throws IOException {
        MediaExtractor extractor;
        extractor = new MediaExtractor();
        extractor.setDataSource(mInputFile);
        return extractor;
    }

    private MediaCodec createVideoDecoder(MediaFormat inputFormat, Surface surface) throws IOException {
        MediaCodec decoder = MediaCodec.createDecoderByType(getMimeTypeFor(inputFormat));
        decoder.configure(inputFormat, surface, null, 0);
        decoder.start();
        return decoder;
    }

    private MediaCodec createVideoEncoder(MediaCodecInfo codecInfo, MediaFormat format,
                                          AtomicReference<Surface> surfaceReference) throws IOException {
        MediaCodec encoder = MediaCodec.createByCodecName(codecInfo.getName());
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        surfaceReference.set(encoder.createInputSurface());
        encoder.start();
        return encoder;
    }

    private MediaCodec createAudioDecoder(MediaFormat inputFormat) throws IOException {
        MediaCodec decoder = MediaCodec.createDecoderByType(getMimeTypeFor(inputFormat));
        decoder.configure(inputFormat, null, null, 0);
        decoder.start();
        return decoder;
    }

    private MediaCodec createAudioEncoder(MediaCodecInfo codecInfo, MediaFormat format) throws IOException {
        MediaCodec encoder = MediaCodec.createByCodecName(codecInfo.getName());
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();
        return encoder;
    }

    private int getAndSelectVideoTrackIndex(MediaExtractor extractor) {
        for (int index = 0; index < extractor.getTrackCount(); ++index) {
            if (isVideoFormat(extractor.getTrackFormat(index))) {
                extractor.selectTrack(index);
                return index;
            }
        }
        return -1;
    }
    private int getAndSelectAudioTrackIndex(MediaExtractor extractor) {
        for (int index = 0; index < extractor.getTrackCount(); ++index) {
            if (isAudioFormat(extractor.getTrackFormat(index))) {
                extractor.selectTrack(index);
                return index;
            }
        }
        return -1;
    }

    private void changeResolution(MediaExtractor videoExtractor, MediaExtractor audioExtractor,
                                  MediaCodec videoDecoder, MediaCodec videoEncoder,
                                  MediaCodec audioDecoder, MediaCodec audioEncoder,
                                  MediaMuxer muxer,
                                  InputSurface inputSurface, OutputSurface outputSurface) {
        ByteBuffer[] videoDecoderInputBuffers = null;
        ByteBuffer[] videoDecoderOutputBuffers = null;
        ByteBuffer[] videoEncoderOutputBuffers = null;
        MediaCodec.BufferInfo videoDecoderOutputBufferInfo = null;
        MediaCodec.BufferInfo videoEncoderOutputBufferInfo = null;

        videoDecoderInputBuffers = videoDecoder.getInputBuffers();
        videoDecoderOutputBuffers = videoDecoder.getOutputBuffers();
        videoEncoderOutputBuffers = videoEncoder.getOutputBuffers();
        videoDecoderOutputBufferInfo = new MediaCodec.BufferInfo();
        videoEncoderOutputBufferInfo = new MediaCodec.BufferInfo();

        ByteBuffer[] audioDecoderInputBuffers = null;
        ByteBuffer[] audioDecoderOutputBuffers = null;
        ByteBuffer[] audioEncoderInputBuffers = null;
        ByteBuffer[] audioEncoderOutputBuffers = null;
        MediaCodec.BufferInfo audioDecoderOutputBufferInfo = null;
        MediaCodec.BufferInfo audioEncoderOutputBufferInfo = null;

        audioDecoderInputBuffers = audioDecoder.getInputBuffers();
        audioDecoderOutputBuffers =  audioDecoder.getOutputBuffers();
        audioEncoderInputBuffers = audioEncoder.getInputBuffers();
        audioEncoderOutputBuffers = audioEncoder.getOutputBuffers();
        audioDecoderOutputBufferInfo = new MediaCodec.BufferInfo();
        audioEncoderOutputBufferInfo = new MediaCodec.BufferInfo();

        MediaFormat decoderOutputVideoFormat = null;
        MediaFormat decoderOutputAudioFormat = null;
        MediaFormat encoderOutputVideoFormat = null;
        MediaFormat encoderOutputAudioFormat = null;
        int outputVideoTrack = -1;
        int outputAudioTrack = -1;

        boolean videoExtractorDone = false;
        boolean videoDecoderDone = false;
        boolean videoEncoderDone = false;

        boolean audioExtractorDone = false;
        boolean audioDecoderDone = false;
        boolean audioEncoderDone = false;

        int pendingAudioDecoderOutputBufferIndex = -1;
        boolean muxing = false;
        while ((!videoEncoderDone) || (!audioEncoderDone)) {
            while (!videoExtractorDone
                    && (encoderOutputVideoFormat == null || muxing)) {
                int decoderInputBufferIndex = videoDecoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (decoderInputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER)
                    break;

                ByteBuffer decoderInputBuffer = videoDecoderInputBuffers[decoderInputBufferIndex];
                int size = videoExtractor.readSampleData(decoderInputBuffer, 0);
                long presentationTime = videoExtractor.getSampleTime();

                if (size >= 0) {
                    videoDecoder.queueInputBuffer(
                            decoderInputBufferIndex,
                            0,
                            size,
                            presentationTime,
                            videoExtractor.getSampleFlags());
                }
                videoExtractorDone = !videoExtractor.advance();
                if (videoExtractorDone)
                    videoDecoder.queueInputBuffer(decoderInputBufferIndex,
                            0, 0, 0,  MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                break;
            }

            while (!audioExtractorDone
                    && (encoderOutputAudioFormat == null || muxing)) {
                int decoderInputBufferIndex = audioDecoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (decoderInputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER)
                    break;

                ByteBuffer decoderInputBuffer = audioDecoderInputBuffers[decoderInputBufferIndex];
                int size = audioExtractor.readSampleData(decoderInputBuffer, 0);
                long presentationTime = audioExtractor.getSampleTime();

                if (size >= 0)
                    audioDecoder.queueInputBuffer(decoderInputBufferIndex, 0, size,
                            presentationTime, audioExtractor.getSampleFlags());

                audioExtractorDone = !audioExtractor.advance();
                if (audioExtractorDone)
                    audioDecoder.queueInputBuffer(decoderInputBufferIndex, 0, 0,
                            0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);

                break;
            }

            while (!videoDecoderDone
                    && (encoderOutputVideoFormat == null || muxing)) {
                int decoderOutputBufferIndex =
                        videoDecoder.dequeueOutputBuffer(
                                videoDecoderOutputBufferInfo, TIMEOUT_USEC);
                if (decoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER)
                    break;

                if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    videoDecoderOutputBuffers = videoDecoder.getOutputBuffers();
                    break;
                }
                if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    decoderOutputVideoFormat = videoDecoder.getOutputFormat();
                    break;
                }

                ByteBuffer decoderOutputBuffer =
                        videoDecoderOutputBuffers[decoderOutputBufferIndex];
                if ((videoDecoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
                        != 0) {
                    videoDecoder.releaseOutputBuffer(decoderOutputBufferIndex, false);
                    break;
                }

                boolean render = videoDecoderOutputBufferInfo.size != 0;
                videoDecoder.releaseOutputBuffer(decoderOutputBufferIndex, render);
                if (render) {
                    outputSurface.awaitNewImage();
                    outputSurface.drawImage();
                    inputSurface.setPresentationTime(
                            videoDecoderOutputBufferInfo.presentationTimeUs * 1000);
                    inputSurface.swapBuffers();
                }
                if ((videoDecoderOutputBufferInfo.flags
                        & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    videoDecoderDone = true;
                    videoEncoder.signalEndOfInputStream();
                }
                break;
            }

            while (!audioDecoderDone && pendingAudioDecoderOutputBufferIndex == -1
                    && (encoderOutputAudioFormat == null || muxing)) {
                int decoderOutputBufferIndex =
                        audioDecoder.dequeueOutputBuffer(
                                audioDecoderOutputBufferInfo, TIMEOUT_USEC);
                if (decoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER)
                    break;

                if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    audioDecoderOutputBuffers = audioDecoder.getOutputBuffers();
                    break;
                }
                if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    decoderOutputAudioFormat = audioDecoder.getOutputFormat();
                    break;
                }
                ByteBuffer decoderOutputBuffer =
                        audioDecoderOutputBuffers[decoderOutputBufferIndex];
                if ((audioDecoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
                        != 0) {
                    audioDecoder.releaseOutputBuffer(decoderOutputBufferIndex, false);
                    break;
                }
                pendingAudioDecoderOutputBufferIndex = decoderOutputBufferIndex;
                break;
            }

            while (pendingAudioDecoderOutputBufferIndex != -1) {
                int encoderInputBufferIndex = audioEncoder.dequeueInputBuffer(TIMEOUT_USEC);
                ByteBuffer encoderInputBuffer = audioEncoderInputBuffers[encoderInputBufferIndex];
                int size = audioDecoderOutputBufferInfo.size;
                long presentationTime = audioDecoderOutputBufferInfo.presentationTimeUs;

                if (size >= 0) {
                    ByteBuffer decoderOutputBuffer =
                            audioDecoderOutputBuffers[pendingAudioDecoderOutputBufferIndex]
                                    .duplicate();
                    decoderOutputBuffer.position(audioDecoderOutputBufferInfo.offset);
                    decoderOutputBuffer.limit(audioDecoderOutputBufferInfo.offset + size);
                    encoderInputBuffer.position(0);
                    encoderInputBuffer.put(decoderOutputBuffer);
                    audioEncoder.queueInputBuffer(
                            encoderInputBufferIndex,
                            0,
                            size,
                            presentationTime,
                            audioDecoderOutputBufferInfo.flags);
                }
                audioDecoder.releaseOutputBuffer(pendingAudioDecoderOutputBufferIndex, false);
                pendingAudioDecoderOutputBufferIndex = -1;
                if ((audioDecoderOutputBufferInfo.flags
                        & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0)
                    audioDecoderDone = true;

                break;
            }

            while (!videoEncoderDone
                    && (encoderOutputVideoFormat == null || muxing)) {
                int encoderOutputBufferIndex = videoEncoder.dequeueOutputBuffer(
                        videoEncoderOutputBufferInfo, TIMEOUT_USEC);
                if (encoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER)
                    break;
                if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    videoEncoderOutputBuffers = videoEncoder.getOutputBuffers();
                    break;
                }
                if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    encoderOutputVideoFormat = videoEncoder.getOutputFormat();
                    break;
                }

                ByteBuffer encoderOutputBuffer =
                        videoEncoderOutputBuffers[encoderOutputBufferIndex];
                if ((videoEncoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
                        != 0) {
                    videoEncoder.releaseOutputBuffer(encoderOutputBufferIndex, false);
                    break;
                }
                if (videoEncoderOutputBufferInfo.size != 0) {
                    muxer.writeSampleData(
                            outputVideoTrack, encoderOutputBuffer, videoEncoderOutputBufferInfo);
                }
                if ((videoEncoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        != 0) {
                    videoEncoderDone = true;
                }
                videoEncoder.releaseOutputBuffer(encoderOutputBufferIndex, false);
                break;
            }

            while (!audioEncoderDone
                    && (encoderOutputAudioFormat == null || muxing)) {
                int encoderOutputBufferIndex = audioEncoder.dequeueOutputBuffer(
                        audioEncoderOutputBufferInfo, TIMEOUT_USEC);
                if (encoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    break;
                }
                if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    audioEncoderOutputBuffers = audioEncoder.getOutputBuffers();
                    break;
                }
                if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    encoderOutputAudioFormat = audioEncoder.getOutputFormat();
                    break;
                }

                ByteBuffer encoderOutputBuffer =
                        audioEncoderOutputBuffers[encoderOutputBufferIndex];
                if ((audioEncoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
                        != 0) {
                    audioEncoder.releaseOutputBuffer(encoderOutputBufferIndex, false);
                    break;
                }
                if (audioEncoderOutputBufferInfo.size != 0)
                    muxer.writeSampleData(
                            outputAudioTrack, encoderOutputBuffer, audioEncoderOutputBufferInfo);
                if ((audioEncoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        != 0)
                    audioEncoderDone = true;

                audioEncoder.releaseOutputBuffer(encoderOutputBufferIndex, false);

                break;
            }
            if (!muxing && (encoderOutputAudioFormat != null)
                    && (encoderOutputVideoFormat != null)) {
                outputVideoTrack = muxer.addTrack(encoderOutputVideoFormat);
                outputAudioTrack = muxer.addTrack(encoderOutputAudioFormat);
                muxer.start();
                muxing = true;
            }
        }
    }

    private static boolean isVideoFormat(MediaFormat format) {
        return getMimeTypeFor(format).startsWith("video/");
    }
    private static boolean isAudioFormat(MediaFormat format) {
        return getMimeTypeFor(format).startsWith("audio/");
    }
    private static String getMimeTypeFor(MediaFormat format) {
        return format.getString(MediaFormat.KEY_MIME);
    }

    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }
    /**
     * Get a file path from a Uri. This will get the the path for Storage Access
     * Framework Documents, as well as the _data field for the MediaStore and
     * other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @author paulburke
     */
    public static String getPath(final Context context, final Uri uri) {

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }

                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[] {
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {

            // Return the remote address
            if (isGooglePhotosUri(uri))
                return uri.getLastPathSegment();

            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }
    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @param selection (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    public static String getDataColumn(Context context, Uri uri, String selection,
                                       String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }


    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    public static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }
}