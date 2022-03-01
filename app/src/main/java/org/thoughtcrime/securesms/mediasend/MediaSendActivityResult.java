package org.thoughtcrime.securesms.mediasend;

import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.TransportOption;
import org.thoughtcrime.securesms.conversation.ConversationActivity;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.StoryType;
import org.thoughtcrime.securesms.recipients.RecipientId;
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

  public static final String EXTRA_RESULT    = "result";

  private final RecipientId                 recipientId;
  private final Collection<PreUploadResult> uploadResults;
  private final Collection<Media>           nonUploadedMedia;
  private final String                      body;
  private final TransportOption             transport;
  private final boolean                     viewOnce;
  private final Collection<Mention>         mentions;
  private final StoryType                   storyType;

  public static @NonNull MediaSendActivityResult fromData(@NonNull Intent data) {
    MediaSendActivityResult result = data.getParcelableExtra(MediaSendActivityResult.EXTRA_RESULT);
    if (result == null) {
      throw new IllegalArgumentException();
    }

    return result;
  }

  public static @NonNull MediaSendActivityResult forPreUpload(@NonNull RecipientId recipientId,
                                                              @NonNull Collection<PreUploadResult> uploadResults,
                                                              @NonNull String body,
                                                              @NonNull TransportOption transport,
                                                              boolean viewOnce,
                                                              @NonNull List<Mention> mentions,
                                                              @NonNull StoryType storyType)
  {
    Preconditions.checkArgument(uploadResults.size() > 0, "Must supply uploadResults!");
    return new MediaSendActivityResult(recipientId, uploadResults, Collections.emptyList(), body, transport, viewOnce, mentions, storyType);
  }

  public static @NonNull MediaSendActivityResult forTraditionalSend(@NonNull RecipientId recipientId,
                                                                    @NonNull List<Media> nonUploadedMedia,
                                                                    @NonNull String body,
                                                                    @NonNull TransportOption transport,
                                                                    boolean viewOnce,
                                                                    @NonNull List<Mention> mentions,
                                                                    @NonNull StoryType storyType)
  {
    Preconditions.checkArgument(nonUploadedMedia.size() > 0, "Must supply media!");
    return new MediaSendActivityResult(recipientId, Collections.emptyList(), nonUploadedMedia, body, transport, viewOnce, mentions, storyType);
  }

  private MediaSendActivityResult(@NonNull RecipientId recipientId,
                                  @NonNull Collection<PreUploadResult> uploadResults,
                                  @NonNull List<Media> nonUploadedMedia,
                                  @NonNull String body,
                                  @NonNull TransportOption transport,
                                  boolean viewOnce,
                                  @NonNull List<Mention> mentions,
                                  @NonNull StoryType storyType)
  {
    this.recipientId      = recipientId;
    this.uploadResults    = uploadResults;
    this.nonUploadedMedia = nonUploadedMedia;
    this.body             = body;
    this.transport        = transport;
    this.viewOnce         = viewOnce;
    this.mentions         = mentions;
    this.storyType        = storyType;
  }

  private MediaSendActivityResult(Parcel in) {
    this.recipientId      = in.readParcelable(RecipientId.class.getClassLoader());
    this.uploadResults    = ParcelUtil.readParcelableCollection(in, PreUploadResult.class);
    this.nonUploadedMedia = ParcelUtil.readParcelableCollection(in, Media.class);
    this.body             = in.readString();
    this.transport        = in.readParcelable(TransportOption.class.getClassLoader());
    this.viewOnce         = ParcelUtil.readBoolean(in);
    this.mentions         = ParcelUtil.readParcelableCollection(in, Mention.class);
    this.storyType        = StoryType.fromCode(in.readInt());
  }

  public @NonNull RecipientId getRecipientId() {
    return recipientId;
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

  public @NonNull Collection<Mention> getMentions() {
    return mentions;
  }

  public @NonNull StoryType getStoryType() {
    return storyType;
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
    dest.writeParcelable(recipientId, 0);
    ParcelUtil.writeParcelableCollection(dest, uploadResults);
    ParcelUtil.writeParcelableCollection(dest, nonUploadedMedia);
    dest.writeString(body);
    dest.writeParcelable(transport, 0);
    ParcelUtil.writeBoolean(dest, viewOnce);
    ParcelUtil.writeParcelableCollection(dest, mentions);
    dest.writeInt(storyType.getCode());
  }
}
