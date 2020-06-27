package org.thoughtcrime.securesms.mediasend;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.database.AttachmentDatabase;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.internal.util.JsonUtil;

import java.io.IOException;

/**
 * Represents a piece of media that the user has on their device.
 */
public class Media implements Parcelable {

  public static final String ALL_MEDIA_BUCKET_ID = "org.thoughtcrime.securesms.ALL_MEDIA";

  private final Uri    uri;
  private final String mimeType;
  private final long   date;
  private final int    width;
  private final int    height;
  private final long   size;
  private final long   duration;

  private Optional<String>                                 bucketId;
  private Optional<String>                                 caption;
  private Optional<AttachmentDatabase.TransformProperties> transformProperties;

  public Media(@NonNull Uri uri,
               @NonNull String mimeType,
               long date,
               int width,
               int height,
               long size,
               long duration,
               Optional<String> bucketId,
               Optional<String> caption,
               Optional<AttachmentDatabase.TransformProperties> transformProperties)
  {
    this.uri                 = uri;
    this.mimeType            = mimeType;
    this.date                = date;
    this.width               = width;
    this.height              = height;
    this.size                = size;
    this.duration            = duration;
    this.bucketId            = bucketId;
    this.caption             = caption;
    this.transformProperties = transformProperties;
  }

  protected Media(Parcel in) {
    uri      = in.readParcelable(Uri.class.getClassLoader());
    mimeType = in.readString();
    date     = in.readLong();
    width    = in.readInt();
    height   = in.readInt();
    size     = in.readLong();
    duration = in.readLong();
    bucketId = Optional.fromNullable(in.readString());
    caption  = Optional.fromNullable(in.readString());
    try {
      String json = in.readString();
      transformProperties = json == null ? Optional.absent() : Optional.fromNullable(JsonUtil.fromJson(json, AttachmentDatabase.TransformProperties.class));
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  public Uri getUri() {
    return uri;
  }

  public String getMimeType() {
    return mimeType;
  }

  public long getDate() {
    return date;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public long getSize() {
    return size;
  }

  public long getDuration() {
    return duration;
  }

  public Optional<String> getBucketId() {
    return bucketId;
  }

  public Optional<String> getCaption() {
    return caption;
  }

  public void setCaption(String caption) {
    this.caption = Optional.fromNullable(caption);
  }

  public Optional<AttachmentDatabase.TransformProperties> getTransformProperties() {
    return transformProperties;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeParcelable(uri, flags);
    dest.writeString(mimeType);
    dest.writeLong(date);
    dest.writeInt(width);
    dest.writeInt(height);
    dest.writeLong(size);
    dest.writeLong(duration);
    dest.writeString(bucketId.orNull());
    dest.writeString(caption.orNull());
    dest.writeString(transformProperties.transform(JsonUtil::toJson).orNull());
  }

  public static final Creator<Media> CREATOR = new Creator<Media>() {
    @Override
    public Media createFromParcel(Parcel in) {
      return new Media(in);
    }

    @Override
    public Media[] newArray(int size) {
      return new Media[size];
    }
  };

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Media media = (Media) o;

    return uri.equals(media.uri);
  }

  @Override
  public int hashCode() {
    return uri.hashCode();
  }
}
