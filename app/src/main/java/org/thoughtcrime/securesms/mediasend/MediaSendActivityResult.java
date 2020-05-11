package org.thoughtcrime.securesms.mediasend;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.TransportOption;
import org.thoughtcrime.securesms.conversation.ConversationActivity;
import org.thoughtcrime.securesms.sms.MessageSender.PreUploadResult;
import org.thoughtcrime.securesms.util.ParcelUtil;
import org.whispersystems.libsignal.util.guava.Preconditions;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A class that lets us nicely format data that we'll send back to {@link ConversationActivity}.
 */
public class MediaSendActivityResult implements Parcelable {
  private final Collection<PreUploadResult> uploadResults;
  private final Collection<Media>           nonUploadedMedia;
  private final String                      body;
  private final TransportOption             transport;
  private final boolean                     viewOnce;

  static @NonNull MediaSendActivityResult forPreUpload(@NonNull Collection<PreUploadResult> uploadResults,
                                                       @NonNull String body,
                                                       @NonNull TransportOption transport,
                                                       boolean viewOnce)
  {
    Preconditions.checkArgument(uploadResults.size() > 0, "Must supply uploadResults!");
    return new MediaSendActivityResult(uploadResults, Collections.emptyList(), body, transport, viewOnce);
  }

  static @NonNull MediaSendActivityResult forTraditionalSend(@NonNull List<Media> nonUploadedMedia,
                                                             @NonNull String body,
                                                             @NonNull TransportOption transport,
                                                             boolean viewOnce)
  {
    Preconditions.checkArgument(nonUploadedMedia.size() > 0, "Must supply media!");
    return new MediaSendActivityResult(Collections.emptyList(), nonUploadedMedia, body, transport, viewOnce);
  }

  private MediaSendActivityResult(@NonNull Collection<PreUploadResult> uploadResults,
                                  @NonNull List<Media> nonUploadedMedia,
                                  @NonNull String body,
                                  @NonNull TransportOption transport,
                                  boolean viewOnce)
  {
    this.uploadResults    = uploadResults;
    this.nonUploadedMedia = nonUploadedMedia;
    this.body             = body;
    this.transport        = transport;
    this.viewOnce         = viewOnce;
  }

  private MediaSendActivityResult(Parcel in) {
    this.uploadResults    = ParcelUtil.readParcelableCollection(in, PreUploadResult.class);
    this.nonUploadedMedia = ParcelUtil.readParcelableCollection(in, Media.class);
    this.body             = in.readString();
    this.transport        = in.readParcelable(TransportOption.class.getClassLoader());
    this.viewOnce         = ParcelUtil.readBoolean(in);
  }

  public boolean isPushPreUpload() {
    return uploadResults.size() > 0;
  }

  public @NonNull Collection<PreUploadResult> getPreUploadResults() {
    return uploadResults;
  }

  public @NonNull Collection<Media> getNonUploadedMedia() {
    return nonUploadedMedia;
  }

  public @NonNull String getBody() {
    return body;
  }

  public @NonNull TransportOption getTransport() {
    return transport;
  }

  public boolean isViewOnce() {
    return viewOnce;
  }

  public static final Creator<MediaSendActivityResult> CREATOR = new Creator<MediaSendActivityResult>() {
    @Override
    public MediaSendActivityResult createFromParcel(Parcel in) {
      return new MediaSendActivityResult(in);
    }

    @Override
    public MediaSendActivityResult[] newArray(int size) {
      return new MediaSendActivityResult[size];
    }
  };

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    ParcelUtil.writeParcelableCollection(dest, uploadResults);
    ParcelUtil.writeParcelableCollection(dest, nonUploadedMedia);
    dest.writeString(body);
    dest.writeParcelable(transport, 0);
    ParcelUtil.writeBoolean(dest, viewOnce);
  }
}
