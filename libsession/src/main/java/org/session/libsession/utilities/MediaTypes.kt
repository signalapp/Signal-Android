package org.session.libsession.utilities

enum class MediaTypes(val value: String) {
    IMAGE_PNG("image/png"),
    IMAGE_JPEG("image/jpeg"),
    IMAGE_WEBP("image/webp"),
    IMAGE_GIF("image/gif"),
    AUDIO_AAC("audio/aac"),
    AUDIO_UNSPECIFIED("audio/*"),
    VIDEO_UNSPECIFIED("video/*"),
    VCARD("text/x-vcard"),
    LONG_TEXT("text/x-signal-plain")
}