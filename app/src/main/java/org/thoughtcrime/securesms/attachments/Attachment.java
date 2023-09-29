package org.thoughtcrime.securesms.attachments;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.ParcelCompat;

import org.thoughtcrime.securesms.audio.AudioHash;
import org.thoughtcrime.securesms.blurhash.BlurHash;
import org.thoughtcrime.securesms.database.AttachmentTable;
import org.thoughtcrime.securesms.database.AttachmentTable.TransformProperties;
import org.thoughtcrime.securesms.stickers.StickerLocator;
import org.thoughtcrime.securesms.util.ParcelUtil;

import java.util.Objects;

public abstract class Attachment implements Parcelable {

  @NonNull
  private final String  contentType;
  private final int     transferState;
  private final long    size;

  @Nullable
  private final String fileName;

  private final int    cdnNumber;

  @Nullable
  private final String  location;

  @Nullable
  private final String  key;

  @Nullable
  private final String relay;

  @Nullable
  private final byte[] digest;

  @Nullable
  private final byte[] incrementalDigest;

  @Nullable
  private final String fastPreflightId;

  private final boolean voiceNote;
  private final boolean borderless;
  private final boolean videoGif;
  private final int     width;
  private final int     height;
  private final boolean quote;
  private final long    uploadTimestamp;
  private final int     incrementalMacChunkSize;

  @Nullable
  private final String caption;

  @Nullable
  private final StickerLocator stickerLocator;

  @Nullable
  private final BlurHash blurHash;

  @Nullable
  private final AudioHash audioHash;

  @NonNull
  private final TransformProperties transformProperties;

  public Attachment(@NonNull String contentType,
                    int transferState,
                    long size,
                    @Nullable String fileName,
                    int cdnNumber,
                    @Nullable String location,
                    @Nullable String key,
                    @Nullable String relay,
                    @Nullable byte[] digest,
                    @Nullable byte[] incrementalDigest,
                    @Nullable String fastPreflightId,
                    boolean voiceNote,
                    boolean borderless,
                    boolean videoGif,
                    int width,
                    int height,
                    int incrementalMacChunkSize,
                    boolean quote,
                    long uploadTimestamp,
                    @Nullable String caption,
                    @Nullable StickerLocator stickerLocator,
                    @Nullable BlurHash blurHash,
                    @Nullable AudioHash audioHash,
                    @Nullable TransformProperties transformProperties)
  {
    this.contentType             = contentType;
    this.transferState           = transferState;
    this.size                    = size;
    this.fileName                = fileName;
    this.cdnNumber               = cdnNumber;
    this.location                = location;
    this.key                     = key;
    this.relay                   = relay;
    this.digest                  = digest;
    this.incrementalDigest       = incrementalDigest;
    this.fastPreflightId         = fastPreflightId;
    this.voiceNote               = voiceNote;
    this.borderless              = borderless;
    this.videoGif                = videoGif;
    this.width                   = width;
    this.height                  = height;
    this.incrementalMacChunkSize = incrementalMacChunkSize;
    this.quote                   = quote;
    this.uploadTimestamp         = uploadTimestamp;
    this.stickerLocator          = stickerLocator;
    this.caption                 = caption;
    this.blurHash                = blurHash;
    this.audioHash               = audioHash;
    this.transformProperties     = transformProperties != null ? transformProperties : TransformProperties.empty();
  }

  protected Attachment(Parcel in) {
    this.contentType             = Objects.requireNonNull(in.readString());
    this.transferState           = in.readInt();
    this.size                    = in.readLong();
    this.fileName                = in.readString();
    this.cdnNumber               = in.readInt();
    this.location                = in.readString();
    this.key                     = in.readString();
    this.relay                   = in.readString();
    this.digest                  = ParcelUtil.readByteArray(in);
    this.incrementalDigest       = ParcelUtil.readByteArray(in);
    this.fastPreflightId         = in.readString();
    this.voiceNote               = ParcelUtil.readBoolean(in);
    this.borderless              = ParcelUtil.readBoolean(in);
    this.videoGif                = ParcelUtil.readBoolean(in);
    this.width                   = in.readInt();
    this.height                  = in.readInt();
    this.incrementalMacChunkSize = in.readInt();
    this.quote                   = ParcelUtil.readBoolean(in);
    this.uploadTimestamp         = in.readLong();
    this.stickerLocator          = ParcelCompat.readParcelable(in, StickerLocator.class.getClassLoader(), StickerLocator.class);
    this.caption                 = in.readString();
    this.blurHash                = ParcelCompat.readParcelable(in, BlurHash.class.getClassLoader(), BlurHash.class);
    this.audioHash               = ParcelCompat.readParcelable(in, AudioHash.class.getClassLoader(), AudioHash.class);
    this.transformProperties     = Objects.requireNonNull(ParcelCompat.readParcelable(in, TransformProperties.class.getClassLoader(), TransformProperties.class));
  }

  @Override
  public void writeToParcel(@NonNull Parcel dest, int flags) {
    AttachmentCreator.writeSubclass(dest, this);
    dest.writeString(contentType);
    dest.writeInt(transferState);
    dest.writeLong(size);
    dest.writeString(fileName);
    dest.writeInt(cdnNumber);
    dest.writeString(location);
    dest.writeString(key);
    dest.writeString(relay);
    ParcelUtil.writeByteArray(dest, digest);
    ParcelUtil.writeByteArray(dest, incrementalDigest);
    dest.writeString(fastPreflightId);
    ParcelUtil.writeBoolean(dest, voiceNote);
    ParcelUtil.writeBoolean(dest, borderless);
    ParcelUtil.writeBoolean(dest, videoGif);
    dest.writeInt(width);
    dest.writeInt(height);
    dest.writeInt(incrementalMacChunkSize);
    ParcelUtil.writeBoolean(dest, quote);
    dest.writeLong(uploadTimestamp);
    dest.writeParcelable(stickerLocator, 0);
    dest.writeString(caption);
    dest.writeParcelable(blurHash, 0);
    dest.writeParcelable(audioHash, 0);
    dest.writeParcelable(transformProperties, 0);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Creator<Attachment> CREATOR = AttachmentCreator.INSTANCE;

  @Nullable
  public abstract Uri getUri();

  public abstract @Nullable Uri getPublicUri();

  public int getTransferState() {
    return transferState;
  }

  public boolean isInProgress() {
    return transferState != AttachmentTable.TRANSFER_PROGRESS_DONE &&
           transferState != AttachmentTable.TRANSFER_PROGRESS_FAILED &&
           transferState != AttachmentTable.TRANSFER_PROGRESS_PERMANENT_FAILURE;
  }

  public boolean isPermanentlyFailed() {
    return transferState == AttachmentTable.TRANSFER_PROGRESS_PERMANENT_FAILURE;
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

  public int getCdnNumber() {
    return cdnNumber;
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
  public byte[] getIncrementalDigest() {
    return incrementalDigest;
  }

  @Nullable
  public String getFastPreflightId() {
    return fastPreflightId;
  }

  public boolean isVoiceNote() {
    return voiceNote;
  }

  public boolean isBorderless() {
    return borderless;
  }

  public boolean isVideoGif() {
    return videoGif;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public int getIncrementalMacChunkSize() {
    return incrementalMacChunkSize;
  }

  public boolean isQuote() {
    return quote;
  }

  public long getUploadTimestamp() {
    return uploadTimestamp;
  }

  public boolean isSticker() {
    return stickerLocator != null;
  }

  public @Nullable StickerLocator getSticker() {
    return stickerLocator;
  }

  public @Nullable BlurHash getBlurHash() {
    return blurHash;
  }

  public @Nullable AudioHash getAudioHash() {
    return audioHash;
  }

  public @Nullable String getCaption() {
    return caption;
  }

  public @NonNull TransformProperties getTransformProperties() {
    return transformProperties;
  }
}
