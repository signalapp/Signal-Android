package org.thoughtcrime.securesms.attachments;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.thoughtcrime.securesms.stickers.StickerLocator;

public abstract class Attachment {

  @NonNull
  private final String  contentType;
  private final int     transferState;
  private final long    size;

  @Nullable
  private final String fileName;

  @Nullable
  private final String  location;

  @Nullable
  private final String  key;

  @Nullable
  private final String relay;

  @Nullable
  private final byte[] digest;

  @Nullable
  private final String fastPreflightId;

  private final boolean voiceNote;
  private final int width;
  private final int height;

  private final boolean quote;

  @Nullable
  private final String caption;

  @Nullable
  private final StickerLocator stickerLocator;

  public Attachment(@NonNull String contentType, int transferState, long size, @Nullable String fileName,
                    @Nullable String location, @Nullable String key, @Nullable String relay,
                    @Nullable byte[] digest, @Nullable String fastPreflightId, boolean voiceNote,
                    int width, int height, boolean quote, @Nullable String caption, @Nullable StickerLocator stickerLocator)
  {
    this.contentType     = contentType;
    this.transferState   = transferState;
    this.size            = size;
    this.fileName        = fileName;
    this.location        = location;
    this.key             = key;
    this.relay           = relay;
    this.digest          = digest;
    this.fastPreflightId = fastPreflightId;
    this.voiceNote       = voiceNote;
    this.width           = width;
    this.height          = height;
    this.quote           = quote;
    this.stickerLocator  = stickerLocator;
    this.caption         = caption;
  }

  @Nullable
  public abstract Uri getDataUri();

  @Nullable
  public abstract Uri getThumbnailUri();

  public int getTransferState() {
    return transferState;
  }

  public boolean isInProgress() {
    return transferState != AttachmentDatabase.TRANSFER_PROGRESS_DONE &&
           transferState != AttachmentDatabase.TRANSFER_PROGRESS_FAILED;
  }

  public long getSize() {
    return size;
  }

  @Nullable
  public String getFileName() {
    return fileName;
  }

  @NonNull
  public String getContentType() {
    return contentType;
  }

  @Nullable
  public String getLocation() {
    return location;
  }

  @Nullable
  public String getKey() {
    return key;
  }

  @Nullable
  public String getRelay() {
    return relay;
  }

  @Nullable
  public byte[] getDigest() {
    return digest;
  }

  @Nullable
  public String getFastPreflightId() {
    return fastPreflightId;
  }

  public boolean isVoiceNote() {
    return voiceNote;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public boolean isQuote() {
    return quote;
  }

  public boolean isSticker() {
    return stickerLocator != null;
  }

  public @Nullable StickerLocator getSticker() {
    return stickerLocator;
  }

  public @Nullable String getCaption() {
    return caption;
  }
}
