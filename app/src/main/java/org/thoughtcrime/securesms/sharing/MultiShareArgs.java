package org.thoughtcrime.securesms.sharing;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;
import com.google.protobuf.InvalidProtocolBufferException;

import org.signal.core.util.BreakIteratorCompat;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.database.model.Mention;
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.stickers.StickerLocator;
import org.thoughtcrime.securesms.stories.Stories;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.ParcelUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class MultiShareArgs implements Parcelable {

  private static final String TAG = Log.tag(MultiShareArgs.class);

  private final Set<ContactSearchKey> contactSearchKeys;
  private final List<Media>           media;
  private final String                draftText;
  private final StickerLocator        stickerLocator;
  private final boolean               borderless;
  private final Uri                   dataUri;
  private final String                dataType;
  private final boolean               viewOnce;
  private final LinkPreview           linkPreview;
  private final List<Mention>         mentions;
  private final long                  timestamp;
  private final long                  expiresAt;
  private final boolean               isTextStory;
  private final BodyRangeList         bodyRanges;
  private final List<Contact>         sharedContacts;

  private MultiShareArgs(@NonNull Builder builder) {
    contactSearchKeys = builder.contactSearchKeys;
    media             = builder.media == null ? new ArrayList<>() : new ArrayList<>(builder.media);
    draftText         = builder.draftText;
    stickerLocator    = builder.stickerLocator;
    borderless        = builder.borderless;
    dataUri           = builder.dataUri;
    dataType          = builder.dataType;
    viewOnce          = builder.viewOnce;
    linkPreview       = builder.linkPreview;
    mentions          = builder.mentions == null ? new ArrayList<>() : new ArrayList<>(builder.mentions);
    timestamp         = builder.timestamp;
    expiresAt         = builder.expiresAt;
    isTextStory       = builder.isTextStory;
    bodyRanges        = builder.bodyRanges;
    sharedContacts    = builder.sharedContacts == null ? new ArrayList<>() : new ArrayList<>(builder.sharedContacts);
  }

  protected MultiShareArgs(Parcel in) {
    List<ContactSearchKey.RecipientSearchKey> parcelableRecipientSearchKeys = in.createTypedArrayList(ContactSearchKey.RecipientSearchKey.CREATOR);

    contactSearchKeys = new HashSet<>(parcelableRecipientSearchKeys);
    media             = in.createTypedArrayList(Media.CREATOR);
    draftText         = in.readString();
    stickerLocator    = in.readParcelable(StickerLocator.class.getClassLoader());
    borderless        = in.readByte() != 0;
    dataUri           = in.readParcelable(Uri.class.getClassLoader());
    dataType          = in.readString();
    viewOnce          = in.readByte() != 0;
    mentions          = in.createTypedArrayList(Mention.CREATOR);
    timestamp         = in.readLong();
    expiresAt         = in.readLong();
    isTextStory       = ParcelUtil.readBoolean(in);

    String      linkedPreviewString = in.readString();
    LinkPreview preview;
    try {
      preview = linkedPreviewString != null ? LinkPreview.deserialize(linkedPreviewString) : null;
    } catch (IOException e) {
      preview = null;
    }

    linkPreview = preview;

    BodyRangeList bodyRanges = null;
    try {
      byte[] data = ParcelUtil.readByteArray(in);
      if (data != null) {
        bodyRanges = BodyRangeList.parseFrom(data);
      }
    } catch (InvalidProtocolBufferException e) {
      Log.w(TAG, "Invalid body range", e);
    }
    this.bodyRanges = bodyRanges;
    sharedContacts  = in.createTypedArrayList(Contact.CREATOR);
  }

  public Set<ContactSearchKey> getContactSearchKeys() {
    return contactSearchKeys;
  }

  public Set<ContactSearchKey.RecipientSearchKey> getRecipientSearchKeys() {
    return contactSearchKeys.stream()
                            .filter(key -> key instanceof ContactSearchKey.RecipientSearchKey)
                            .map(key -> (ContactSearchKey.RecipientSearchKey) key)
                            .collect(Collectors.toSet());
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

  public boolean isTextStory() {
    return isTextStory;
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

  public @Nullable BodyRangeList getBodyRanges() {
    return bodyRanges;
  }

  public @NonNull List<Contact> getSharedContacts() {
    return sharedContacts;
  }

  public boolean isValidForStories() {
    if (isViewOnce()) {
      return false;
    }

    return isTextStory ||
           (!media.isEmpty() && media.stream().allMatch(m -> MediaUtil.isStorySupportedType(m.getMimeType()))) ||
           MediaUtil.isStorySupportedType(dataType) ||
           isValidForTextStoryGeneration();
  }

  public boolean isValidForNonStories() {
    return !isTextStory;
  }

  public boolean isValidForTextStoryGeneration() {
    if (isTextStory || !media.isEmpty()) {
      return false;
    }

    if (!Util.isEmpty(getDraftText())) {
      BreakIteratorCompat breakIteratorCompat = BreakIteratorCompat.getInstance();
      breakIteratorCompat.setText(getDraftText());

      if (breakIteratorCompat.countBreaks() > Stories.MAX_TEXT_STORY_SIZE) {
        return false;
      }
    }

    return linkPreview != null || !Util.isEmpty(draftText);
  }

  public @NonNull InterstitialContentType getInterstitialContentType() {
    if (!requiresInterstitial()) {
      return InterstitialContentType.NONE;
    } else if (!this.getMedia().isEmpty() ||
               (this.getDataUri() != null && this.getDataUri() != Uri.EMPTY && this.getDataType() != null && MediaUtil.isImageOrVideoType(this.getDataType())))
    {
      return InterstitialContentType.MEDIA;
    } else if (!TextUtils.isEmpty(this.getDraftText()) && allRecipientsAreStories()) {
      return InterstitialContentType.MEDIA;
    } else if (!TextUtils.isEmpty(this.getDraftText())) {
      return InterstitialContentType.TEXT;
    } else {
      return InterstitialContentType.NONE;
    }
  }

  public boolean allRecipientsAreStories() {
    return !contactSearchKeys.isEmpty() && contactSearchKeys.stream()
                                                            .allMatch(key -> key.requireRecipientSearchKey().isStory());
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
    dest.writeTypedList(Stream.of(contactSearchKeys).map(ContactSearchKey::requireRecipientSearchKey).toList());
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
    ParcelUtil.writeBoolean(dest, isTextStory);

    if (linkPreview != null) {
      try {
        dest.writeString(linkPreview.serialize());
      } catch (IOException e) {
        dest.writeString("");
      }
    } else {
      dest.writeString("");
    }

    if (bodyRanges != null) {
      ParcelUtil.writeByteArray(dest, bodyRanges.toByteArray());
    } else {
      ParcelUtil.writeByteArray(dest, null);
    }

    dest.writeTypedList(sharedContacts);
  }

  public Builder buildUpon() {
    return buildUpon(contactSearchKeys);
  }

  public Builder buildUpon(@NonNull Set<ContactSearchKey> recipientSearchKeys) {
    return new Builder(recipientSearchKeys).asBorderless(borderless)
                                           .asViewOnce(viewOnce)
                                           .withDataType(dataType)
                                           .withDataUri(dataUri)
                                           .withDraftText(draftText)
                                           .withLinkPreview(linkPreview)
                                           .withMedia(media)
                                           .withStickerLocator(stickerLocator)
                                           .withMentions(mentions)
                                           .withTimestamp(timestamp)
                                           .withExpiration(expiresAt)
                                           .asTextStory(isTextStory)
                                           .withBodyRanges(bodyRanges)
                                           .withSharedContacts(sharedContacts);
  }

  private boolean requiresInterstitial() {
    return stickerLocator == null &&
           (!media.isEmpty() ||
            !TextUtils.isEmpty(draftText) ||
            MediaUtil.isImageOrVideoType(dataType) ||
            (!contactSearchKeys.isEmpty() && contactSearchKeys.stream().anyMatch(key -> key.requireRecipientSearchKey().isStory())));
  }

  public static final class Builder {

    private final Set<ContactSearchKey> contactSearchKeys;

    private List<Media>         media;
    private String              draftText;
    private StickerLocator      stickerLocator;
    private boolean             borderless;
    private Uri                 dataUri;
    private String              dataType;
    private LinkPreview         linkPreview;
    private boolean             viewOnce;
    private List<Mention>       mentions;
    private long                timestamp;
    private long                expiresAt;
    private boolean             isTextStory;
    private BodyRangeList       bodyRanges;
    private List<Contact>       sharedContacts;

    public Builder() {
      this(Collections.emptySet());
    }

    public Builder(@NonNull Set<ContactSearchKey> contactSearchKeys) {
      this.contactSearchKeys = contactSearchKeys;
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

    public @NonNull Builder asTextStory(boolean isTextStory) {
      this.isTextStory = isTextStory;
      return this;
    }

    public @NonNull Builder withBodyRanges(@Nullable BodyRangeList bodyRanges) {
      this.bodyRanges = bodyRanges;
      return this;
    }

    public @NonNull Builder withSharedContacts(List<Contact> sharedContacts) {
      this.sharedContacts = new ArrayList<>(sharedContacts);
      return this;
    }

    public @NonNull MultiShareArgs build() {
      return new MultiShareArgs(this);
    }
  }
}
