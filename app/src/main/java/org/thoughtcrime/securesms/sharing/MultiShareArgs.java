package org.thoughtcrime.securesms.sharing;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.MessageId;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.stickers.StickerLocator;
import org.thoughtcrime.securesms.util.MediaUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class MultiShareArgs implements Parcelable {

  private final Set<ShareContactAndThread> shareContactAndThreads;
  private final List<Media>                media;
  private final String                     draftText;
  private final StickerLocator             stickerLocator;
  private final boolean                    borderless;
  private final Uri                        dataUri;
  private final String                     dataType;
  private final boolean                    viewOnce;
  private final LinkPreview                linkPreview;
  private final List<Mention>              mentions;
  private final long                       timestamp;
  private final long                       expiresAt;

  private MultiShareArgs(@NonNull Builder builder) {
    shareContactAndThreads = builder.shareContactAndThreads;
    media                  = builder.media == null ? new ArrayList<>() : new ArrayList<>(builder.media);
    draftText              = builder.draftText;
    stickerLocator         = builder.stickerLocator;
    borderless             = builder.borderless;
    dataUri                = builder.dataUri;
    dataType               = builder.dataType;
    viewOnce               = builder.viewOnce;
    linkPreview            = builder.linkPreview;
    mentions               = builder.mentions == null ? new ArrayList<>() : new ArrayList<>(builder.mentions);
    timestamp              = builder.timestamp;
    expiresAt              = builder.expiresAt;
  }

  protected MultiShareArgs(Parcel in) {
    shareContactAndThreads = new HashSet<>(Objects.requireNonNull(in.createTypedArrayList(ShareContactAndThread.CREATOR)));
    media                  = in.createTypedArrayList(Media.CREATOR);
    draftText              = in.readString();
    stickerLocator         = in.readParcelable(StickerLocator.class.getClassLoader());
    borderless             = in.readByte() != 0;
    dataUri                = in.readParcelable(Uri.class.getClassLoader());
    dataType               = in.readString();
    viewOnce               = in.readByte() != 0;
    mentions               = in.createTypedArrayList(Mention.CREATOR);
    timestamp              = in.readLong();
    expiresAt              = in.readLong();

    String      linkedPreviewString = in.readString();
    LinkPreview preview;
    try {
      preview = linkedPreviewString != null ? LinkPreview.deserialize(linkedPreviewString) : null;
    } catch (IOException e) {
      preview = null;
    }

    linkPreview = preview;
  }

  public Set<ShareContactAndThread> getShareContactAndThreads() {
    return shareContactAndThreads;
  }

  public @NonNull List<Media> getMedia() {
    return media;
  }

  public StickerLocator getStickerLocator() {
    return stickerLocator;
  }

  public String getDataType() {
    return dataType;
  }

  public @Nullable String getDraftText() {
    return draftText;
  }

  public Uri getDataUri() {
    return dataUri;
  }

  public boolean isBorderless() {
    return borderless;
  }

  public boolean isViewOnce() {
    return viewOnce;
  }

  public @Nullable LinkPreview getLinkPreview() {
    return linkPreview;
  }

  public @NonNull List<Mention> getMentions() {
    return mentions;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public long getExpiresAt() {
    return expiresAt;
  }

  public boolean isValidForStories() {
    return !media.isEmpty() && media.stream().allMatch(m -> MediaUtil.isImageOrVideoType(m.getMimeType()) && !MediaUtil.isGif(m.getMimeType()));
  }

  public @NonNull InterstitialContentType getInterstitialContentType() {
    if (!requiresInterstitial()) {
      return InterstitialContentType.NONE;
    } else if (!this.getMedia().isEmpty() ||
               (this.getDataUri() != null && this.getDataUri() != Uri.EMPTY && this.getDataType() != null && MediaUtil.isImageOrVideoType(this.getDataType())))
    {
      return InterstitialContentType.MEDIA;
    } else if (!TextUtils.isEmpty(this.getDraftText())) {
      return InterstitialContentType.TEXT;
    } else {
      return InterstitialContentType.NONE;
    }
  }


  public static final Creator<MultiShareArgs> CREATOR = new Creator<MultiShareArgs>() {
    @Override
    public MultiShareArgs createFromParcel(Parcel in) {
      return new MultiShareArgs(in);
    }

    @Override
    public MultiShareArgs[] newArray(int size) {
      return new MultiShareArgs[size];
    }
  };

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeTypedList(Stream.of(shareContactAndThreads).toList());
    dest.writeTypedList(media);
    dest.writeString(draftText);
    dest.writeParcelable(stickerLocator, flags);
    dest.writeByte((byte) (borderless ? 1 : 0));
    dest.writeParcelable(dataUri, flags);
    dest.writeString(dataType);
    dest.writeByte((byte) (viewOnce ? 1 : 0));
    dest.writeTypedList(mentions);
    dest.writeLong(timestamp);
    dest.writeLong(expiresAt);

    if (linkPreview != null) {
      try {
        dest.writeString(linkPreview.serialize());
      } catch (IOException e) {
        dest.writeString("");
      }
    } else {
      dest.writeString("");
    }
  }

  public Builder buildUpon() {
    return buildUpon(shareContactAndThreads);
  }

  public Builder buildUpon(@NonNull Set<ShareContactAndThread> shareContactAndThreads) {
    return new Builder(shareContactAndThreads).asBorderless(borderless)
                                              .asViewOnce(viewOnce)
                                              .withDataType(dataType)
                                              .withDataUri(dataUri)
                                              .withDraftText(draftText)
                                              .withLinkPreview(linkPreview)
                                              .withMedia(media)
                                              .withStickerLocator(stickerLocator)
                                              .withMentions(mentions)
                                              .withTimestamp(timestamp)
                                              .withExpiration(expiresAt);
  }

  private boolean requiresInterstitial() {
    return stickerLocator == null &&
           (!media.isEmpty() || !TextUtils.isEmpty(draftText) || MediaUtil.isImageOrVideoType(dataType));
  }

  public static final class Builder {

    private final Set<ShareContactAndThread> shareContactAndThreads;

    private List<Media>    media;
    private String         draftText;
    private StickerLocator stickerLocator;
    private boolean        borderless;
    private Uri            dataUri;
    private String         dataType;
    private LinkPreview    linkPreview;
    private boolean        viewOnce;
    private List<Mention>  mentions;
    private long           timestamp;
    private long           expiresAt;

    public Builder(@NonNull Set<ShareContactAndThread> shareContactAndThreads) {
      this.shareContactAndThreads = shareContactAndThreads;
    }

    public @NonNull Builder withMedia(@Nullable List<Media> media) {
      this.media = media != null ? new ArrayList<>(media) : null;
      return this;
    }

    public @NonNull Builder withDraftText(@Nullable String draftText) {
      this.draftText = draftText;
      return this;
    }

    public @NonNull Builder withStickerLocator(@Nullable StickerLocator stickerLocator) {
      this.stickerLocator = stickerLocator;
      return this;
    }

    public @NonNull Builder asBorderless(boolean borderless) {
      this.borderless = borderless;
      return this;
    }

    public @NonNull Builder withDataUri(@Nullable Uri dataUri) {
      this.dataUri = dataUri;
      return this;
    }

    public @NonNull Builder withDataType(@Nullable String dataType) {
      this.dataType = dataType;
      return this;
    }

    public @NonNull Builder withLinkPreview(@Nullable LinkPreview linkPreview) {
      this.linkPreview = linkPreview;
      return this;
    }

    public @NonNull Builder asViewOnce(boolean viewOnce) {
      this.viewOnce = viewOnce;
      return this;
    }

    public @NonNull Builder withMentions(@Nullable List<Mention> mentions) {
      this.mentions = mentions != null ? new ArrayList<>(mentions) : null;
      return this;
    }

    public @NonNull Builder withTimestamp(long timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public @NonNull Builder withExpiration(long expiresAt) {
      this.expiresAt = expiresAt;
      return this;
    }

    public @NonNull MultiShareArgs build() {
      return new MultiShareArgs(this);
    }
  }
}
