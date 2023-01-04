package org.thoughtcrime.securesms.attachments;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.audio.AudioHash;
import org.thoughtcrime.securesms.blurhash.BlurHash;
import org.thoughtcrime.securesms.database.AttachmentTable.TransformProperties;
import org.thoughtcrime.securesms.stickers.StickerLocator;

import java.util.Objects;

public class UriAttachment extends Attachment {

  private final @NonNull  Uri dataUri;

  public UriAttachment(@NonNull Uri uri,
                       @NonNull String contentType,
                       int transferState,
                       long size,
                       @Nullable String fileName,
                       boolean voiceNote,
                       boolean borderless,
                       boolean videoGif,
                       boolean quote,
                       @Nullable String caption,
                       @Nullable StickerLocator stickerLocator,
                       @Nullable BlurHash blurHash,
                       @Nullable AudioHash audioHash,
                       @Nullable TransformProperties transformProperties)
  {
    this(uri, contentType, transferState, size, 0, 0, fileName, null, voiceNote, borderless, videoGif, quote, caption, stickerLocator, blurHash, audioHash, transformProperties);
  }

  public UriAttachment(@NonNull Uri dataUri,
                       @NonNull String contentType,
                       int transferState,
                       long size,
                       int width,
                       int height,
                       @Nullable String fileName,
                       @Nullable String fastPreflightId,
                       boolean voiceNote,
                       boolean borderless,
                       boolean videoGif,
                       boolean quote,
                       @Nullable String caption,
                       @Nullable StickerLocator stickerLocator,
                       @Nullable BlurHash blurHash,
                       @Nullable AudioHash audioHash,
                       @Nullable TransformProperties transformProperties)
  {
    super(contentType, transferState, size, fileName, 0, null, null, null, null, fastPreflightId, voiceNote, borderless, videoGif, width, height, quote, 0, caption, stickerLocator, blurHash, audioHash, transformProperties);
    this.dataUri = Objects.requireNonNull(dataUri);
  }

  @Override
  @NonNull
  public Uri getUri() {
    return dataUri;
  }

  @Override
  public @Nullable Uri getPublicUri() {
    return null;
  }

  @Override
  public boolean equals(Object other) {
    return other != null && other instanceof UriAttachment && ((UriAttachment) other).dataUri.equals(this.dataUri);
  }

  @Override
  public int hashCode() {
    return dataUri.hashCode();
  }
}
