/*
 * Copyright (C) 2007-2008 Esmertec AG.
 * Copyright (C) 2007-2008 The Android Open Source Project
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
 */

package ws.com.google.android.mms;

import java.util.ArrayList;

public class ContentType {
    public static final String MMS_MESSAGE       = "application/vnd.wap.mms-message";
    // The phony content type for generic PDUs (e.g. ReadOrig.ind,
    // Notification.ind, Delivery.ind).
    public static final String MMS_GENERIC       = "application/vnd.wap.mms-generic";
    public static final String MULTIPART_MIXED   = "application/vnd.wap.multipart.mixed";
    public static final String MULTIPART_RELATED = "application/vnd.wap.multipart.related";
    public static final String MULTIPART_ALTERNATIVE = "application/vnd.wap.multipart.alternative";

    public static final String TEXT_PLAIN        = "text/plain";
    public static final String TEXT_HTML         = "text/html";
    public static final String TEXT_VCALENDAR    = "text/x-vCalendar";
    public static final String TEXT_VCARD        = "text/x-vCard";

    public static final String IMAGE_UNSPECIFIED = "image/*";
    public static final String IMAGE_JPEG        = "image/jpeg";
    public static final String IMAGE_JPG         = "image/jpg";
    public static final String IMAGE_GIF         = "image/gif";
    public static final String IMAGE_WBMP        = "image/vnd.wap.wbmp";
    public static final String IMAGE_PNG         = "image/png";
    public static final String IMAGE_X_MS_BMP    = "image/x-ms-bmp";

    public static final String AUDIO_UNSPECIFIED = "audio/*";
    public static final String AUDIO_AAC         = "audio/aac";
    public static final String AUDIO_AMR         = "audio/amr";
    public static final String AUDIO_IMELODY     = "audio/imelody";
    public static final String AUDIO_MID         = "audio/mid";
    public static final String AUDIO_MIDI        = "audio/midi";
    public static final String AUDIO_MP3         = "audio/mp3";
    public static final String AUDIO_MPEG3       = "audio/mpeg3";
    public static final String AUDIO_MPEG        = "audio/mpeg";
    public static final String AUDIO_MPG         = "audio/mpg";
    public static final String AUDIO_MP4         = "audio/mp4";
    public static final String AUDIO_X_MID       = "audio/x-mid";
    public static final String AUDIO_X_MIDI      = "audio/x-midi";
    public static final String AUDIO_X_MP3       = "audio/x-mp3";
    public static final String AUDIO_X_MPEG3     = "audio/x-mpeg3";
    public static final String AUDIO_X_MPEG      = "audio/x-mpeg";
    public static final String AUDIO_X_MPG       = "audio/x-mpg";
    public static final String AUDIO_3GPP        = "audio/3gpp";
    public static final String AUDIO_X_WAV       = "audio/x-wav";
    public static final String AUDIO_OGG         = "application/ogg";

    public static final String VIDEO_UNSPECIFIED = "video/*";
    public static final String VIDEO_3GPP        = "video/3gpp";
    public static final String VIDEO_3G2         = "video/3gpp2";
    public static final String VIDEO_H263        = "video/h263";
    public static final String VIDEO_MP4         = "video/mp4";

    public static final String APP_UNSPECIFIED   = "application/*";
    public static final String APP_SMIL          = "application/smil";
    public static final String APP_WAP_XHTML     = "application/vnd.wap.xhtml+xml";
    public static final String APP_XHTML         = "application/xhtml+xml";

    public static final String APP_DRM_CONTENT   = "application/vnd.oma.drm.content";
    public static final String APP_DRM_MESSAGE   = "application/vnd.oma.drm.message";

    private static final ArrayList<String> sSupportedContentTypes = new ArrayList<String>();
    private static final ArrayList<String> sSupportedImageTypes = new ArrayList<String>();
    private static final ArrayList<String> sSupportedAudioTypes = new ArrayList<String>();
    private static final ArrayList<String> sSupportedVideoTypes = new ArrayList<String>();

    static {
        sSupportedContentTypes.add(TEXT_PLAIN);
        sSupportedContentTypes.add(TEXT_HTML);
        sSupportedContentTypes.add(TEXT_VCALENDAR);
        sSupportedContentTypes.add(TEXT_VCARD);

        sSupportedContentTypes.add(IMAGE_JPEG);
        sSupportedContentTypes.add(IMAGE_GIF);
        sSupportedContentTypes.add(IMAGE_WBMP);
        sSupportedContentTypes.add(IMAGE_PNG);
        sSupportedContentTypes.add(IMAGE_JPG);
        sSupportedContentTypes.add(IMAGE_X_MS_BMP);
        //supportedContentTypes.add(IMAGE_SVG); not yet supported.

        sSupportedContentTypes.add(AUDIO_AAC);
        sSupportedContentTypes.add(AUDIO_AMR);
        sSupportedContentTypes.add(AUDIO_IMELODY);
        sSupportedContentTypes.add(AUDIO_MID);
        sSupportedContentTypes.add(AUDIO_MIDI);
        sSupportedContentTypes.add(AUDIO_MP3);
        sSupportedContentTypes.add(AUDIO_MPEG3);
        sSupportedContentTypes.add(AUDIO_MPEG);
        sSupportedContentTypes.add(AUDIO_MPG);
        sSupportedContentTypes.add(AUDIO_X_MID);
        sSupportedContentTypes.add(AUDIO_X_MIDI);
        sSupportedContentTypes.add(AUDIO_X_MP3);
        sSupportedContentTypes.add(AUDIO_X_MPEG3);
        sSupportedContentTypes.add(AUDIO_X_MPEG);
        sSupportedContentTypes.add(AUDIO_X_MPG);
        sSupportedContentTypes.add(AUDIO_X_WAV);
        sSupportedContentTypes.add(AUDIO_3GPP);
        sSupportedContentTypes.add(AUDIO_OGG);

        sSupportedContentTypes.add(VIDEO_3GPP);
        sSupportedContentTypes.add(VIDEO_3G2);
        sSupportedContentTypes.add(VIDEO_H263);
        sSupportedContentTypes.add(VIDEO_MP4);

        sSupportedContentTypes.add(APP_SMIL);
        sSupportedContentTypes.add(APP_WAP_XHTML);
        sSupportedContentTypes.add(APP_XHTML);

        sSupportedContentTypes.add(APP_DRM_CONTENT);
        sSupportedContentTypes.add(APP_DRM_MESSAGE);

        // add supported image types
        sSupportedImageTypes.add(IMAGE_JPEG);
        sSupportedImageTypes.add(IMAGE_GIF);
        sSupportedImageTypes.add(IMAGE_WBMP);
        sSupportedImageTypes.add(IMAGE_PNG);
        sSupportedImageTypes.add(IMAGE_JPG);
        sSupportedImageTypes.add(IMAGE_X_MS_BMP);

        // add supported audio types
        sSupportedAudioTypes.add(AUDIO_AAC);
        sSupportedAudioTypes.add(AUDIO_AMR);
        sSupportedAudioTypes.add(AUDIO_IMELODY);
        sSupportedAudioTypes.add(AUDIO_MID);
        sSupportedAudioTypes.add(AUDIO_MIDI);
        sSupportedAudioTypes.add(AUDIO_MP3);
        sSupportedAudioTypes.add(AUDIO_MPEG3);
        sSupportedAudioTypes.add(AUDIO_MPEG);
        sSupportedAudioTypes.add(AUDIO_MPG);
        sSupportedAudioTypes.add(AUDIO_MP4);
        sSupportedAudioTypes.add(AUDIO_X_MID);
        sSupportedAudioTypes.add(AUDIO_X_MIDI);
        sSupportedAudioTypes.add(AUDIO_X_MP3);
        sSupportedAudioTypes.add(AUDIO_X_MPEG3);
        sSupportedAudioTypes.add(AUDIO_X_MPEG);
        sSupportedAudioTypes.add(AUDIO_X_MPG);
        sSupportedAudioTypes.add(AUDIO_X_WAV);
        sSupportedAudioTypes.add(AUDIO_3GPP);
        sSupportedAudioTypes.add(AUDIO_OGG);

        // add supported video types
        sSupportedVideoTypes.add(VIDEO_3GPP);
        sSupportedVideoTypes.add(VIDEO_3G2);
        sSupportedVideoTypes.add(VIDEO_H263);
        sSupportedVideoTypes.add(VIDEO_MP4);
    }

    // This class should never be instantiated.
    private ContentType() {
    }

    public static boolean isSupportedType(String contentType) {
        return (null != contentType) && sSupportedContentTypes.contains(contentType);
    }

    public static boolean isSupportedImageType(String contentType) {
        return isImageType(contentType) && isSupportedType(contentType);
    }

    public static boolean isSupportedAudioType(String contentType) {
        return isAudioType(contentType) && isSupportedType(contentType);
    }

    public static boolean isSupportedVideoType(String contentType) {
        return isVideoType(contentType) && isSupportedType(contentType);
    }

    public static boolean isTextType(String contentType) {
        return (null != contentType) && contentType.startsWith("text/");
    }

    public static boolean isImageType(String contentType) {
        return (null != contentType) && contentType.startsWith("image/");
    }

    public static boolean isAudioType(String contentType) {
        return (null != contentType) && contentType.startsWith("audio/");
    }

    public static boolean isVideoType(String contentType) {
        return (null != contentType) && contentType.startsWith("video/");
    }

    public static boolean isFileType(String contentType) {
        return (null != contentType) && (contentType.startsWith("application/") ||contentType.startsWith("text/" ));
    }

    public static boolean isDrmType(String contentType) {
        return (null != contentType)
                && (contentType.equals(APP_DRM_CONTENT)
                        || contentType.equals(APP_DRM_MESSAGE));
    }

    public static boolean isUnspecified(String contentType) {
        return (null != contentType) && contentType.endsWith("*");
    }

    @SuppressWarnings("unchecked")
    public static ArrayList<String> getImageTypes() {
        return (ArrayList<String>) sSupportedImageTypes.clone();
    }

    @SuppressWarnings("unchecked")
    public static ArrayList<String> getAudioTypes() {
        return (ArrayList<String>) sSupportedAudioTypes.clone();
    }

    @SuppressWarnings("unchecked")
    public static ArrayList<String> getVideoTypes() {
        return (ArrayList<String>) sSupportedVideoTypes.clone();
    }

    @SuppressWarnings("unchecked")
    public static ArrayList<String> getSupportedTypes() {
        return (ArrayList<String>) sSupportedContentTypes.clone();
    }
}
