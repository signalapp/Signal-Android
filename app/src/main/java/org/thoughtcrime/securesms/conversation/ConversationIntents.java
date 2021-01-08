package org.thoughtcrime.securesms.conversation;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.stickers.StickerLocator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

public class ConversationIntents {

  private static final String BUBBLE_AUTHORITY                       = "bubble";
  private static final String EXTRA_RECIPIENT                        = "recipient_id";
  private static final String EXTRA_THREAD_ID                        = "thread_id";
  private static final String EXTRA_TEXT                             = "draft_text";
  private static final String EXTRA_MEDIA                            = "media_list";
  private static final String EXTRA_STICKER                          = "sticker_extra";
  private static final String EXTRA_BORDERLESS                       = "borderless_extra";
  private static final String EXTRA_DISTRIBUTION_TYPE                = "distribution_type";
  private static final String EXTRA_STARTING_POSITION                = "starting_position";
  private static final String EXTRA_FIRST_TIME_IN_SELF_CREATED_GROUP = "first_time_in_group";

  private ConversationIntents() {
  }

  public static @NonNull Builder createBuilder(@NonNull Context context, @NonNull RecipientId recipientId, long threadId) {
    return new Builder(context, recipientId, threadId);
  }

  public static @NonNull Builder createPopUpBuilder(@NonNull Context context, @NonNull RecipientId recipientId, long threadId) {
    return new Builder(context, ConversationPopupActivity.class, recipientId, threadId);
  }

  public static @NonNull Intent createBubbleIntent(@NonNull Context context, @NonNull RecipientId recipientId, long threadId) {
    return new Builder(context, BubbleConversationActivity.class, recipientId, threadId).build();
  }

  static boolean isInvalid(@NonNull Intent intent) {
    if (isBubbleIntent(intent)) {
      return intent.getData().getQueryParameter(EXTRA_RECIPIENT) == null;
    } else {
      return !intent.hasExtra(EXTRA_RECIPIENT);
    }
  }

  private static boolean isBubbleIntent(@NonNull Intent intent) {
    return intent.getData() != null && Objects.equals(intent.getData().getAuthority(), BUBBLE_AUTHORITY);
  }

  final static class Args {
    private final RecipientId      recipientId;
    private final long             threadId;
    private final String           draftText;
    private final ArrayList<Media> media;
    private final StickerLocator   stickerLocator;
    private final boolean          isBorderless;
    private final int              distributionType;
    private final int     startingPosition;
    private final boolean firstTimeInSelfCreatedGroup;

    static Args from(@NonNull Intent intent) {
      if (isBubbleIntent(intent)) {
        return new Args(RecipientId.from(intent.getData().getQueryParameter(EXTRA_RECIPIENT)),
                        Long.parseLong(intent.getData().getQueryParameter(EXTRA_THREAD_ID)),
                        null,
                        null,
                        null,
                        false,
                        ThreadDatabase.DistributionTypes.DEFAULT,
                        -1,
                        false);
      }

      return new Args(RecipientId.from(Objects.requireNonNull(intent.getStringExtra(EXTRA_RECIPIENT))),
                      intent.getLongExtra(EXTRA_THREAD_ID, -1),
                      intent.getStringExtra(EXTRA_TEXT),
                      intent.getParcelableArrayListExtra(EXTRA_MEDIA),
                      intent.getParcelableExtra(EXTRA_STICKER),
                      intent.getBooleanExtra(EXTRA_BORDERLESS, false),
                      intent.getIntExtra(EXTRA_DISTRIBUTION_TYPE, ThreadDatabase.DistributionTypes.DEFAULT),
                      intent.getIntExtra(EXTRA_STARTING_POSITION, -1),
                      intent.getBooleanExtra(EXTRA_FIRST_TIME_IN_SELF_CREATED_GROUP, false));
    }

    private Args(@NonNull RecipientId recipientId,
                 long threadId,
                 @Nullable String draftText,
                 @Nullable ArrayList<Media> media,
                 @Nullable StickerLocator stickerLocator,
                 boolean isBorderless,
                 int distributionType,
                 int startingPosition,
                 boolean firstTimeInSelfCreatedGroup)
    {
      this.recipientId                 = recipientId;
      this.threadId                    = threadId;
      this.draftText                   = draftText;
      this.media                       = media;
      this.stickerLocator              = stickerLocator;
      this.isBorderless                = isBorderless;
      this.distributionType            = distributionType;
      this.startingPosition            = startingPosition;
      this.firstTimeInSelfCreatedGroup = firstTimeInSelfCreatedGroup;
    }

    public @NonNull RecipientId getRecipientId() {
      return recipientId;
    }

    public long getThreadId() {
      return threadId;
    }

    public @Nullable String getDraftText() {
      return draftText;
    }

    public @Nullable ArrayList<Media> getMedia() {
      return media;
    }

    public @Nullable StickerLocator getStickerLocator() {
      return stickerLocator;
    }

    public int getDistributionType() {
      return distributionType;
    }

    public int getStartingPosition() {
      return startingPosition;
    }

    public boolean isBorderless() {
      return isBorderless;
    }

    public boolean isFirstTimeInSelfCreatedGroup() {
      return firstTimeInSelfCreatedGroup;
    }
  }

  public final static class Builder {
    private final Context                               context;
    private final Class<? extends ConversationActivity> conversationActivityClass;
    private final RecipientId                           recipientId;
    private final long                                  threadId;

    private String           draftText;
    private ArrayList<Media> media;
    private StickerLocator   stickerLocator;
    private boolean          isBorderless;
    private int              distributionType = ThreadDatabase.DistributionTypes.DEFAULT;
    private int              startingPosition = -1;
    private Uri              dataUri;
    private String           dataType;
    private boolean          firstTimeInSelfCreatedGroup;

    private Builder(@NonNull Context context,
                    @NonNull RecipientId recipientId,
                    long threadId)
    {
      this(context, ConversationActivity.class, recipientId, threadId);
    }

    private Builder(@NonNull Context context,
                    @NonNull Class<? extends ConversationActivity> conversationActivityClass,
                    @NonNull RecipientId recipientId,
                    long threadId)
    {
      this.context                   = context;
      this.conversationActivityClass = conversationActivityClass;
      this.recipientId               = recipientId;
      this.threadId                  = threadId;
    }

    public @NonNull Builder withDraftText(@Nullable String draftText) {
      this.draftText = draftText;
      return this;
    }

    public @NonNull Builder withMedia(@Nullable Collection<Media> media) {
      this.media = media != null ? new ArrayList<>(media) : null;
      return this;
    }

    public @NonNull Builder withStickerLocator(@Nullable StickerLocator stickerLocator) {
      this.stickerLocator = stickerLocator;
      return this;
    }

    public @NonNull Builder asBorderless(boolean isBorderless) {
      this.isBorderless = isBorderless;
      return this;
    }

    public @NonNull Builder withDistributionType(int distributionType) {
      this.distributionType = distributionType;
      return this;
    }

    public @NonNull Builder withStartingPosition(int startingPosition) {
      this.startingPosition = startingPosition;
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

    public Builder firstTimeInSelfCreatedGroup() {
      this.firstTimeInSelfCreatedGroup = true;
      return this;
    }

    public @NonNull Intent build() {
      if (stickerLocator != null && media != null) {
        throw new IllegalStateException("Cannot have both sticker and media array");
      }

      Intent intent = new Intent(context, conversationActivityClass);

      intent.setAction(Intent.ACTION_DEFAULT);

      if (Objects.equals(conversationActivityClass, BubbleConversationActivity.class)) {
        intent.setData(new Uri.Builder().authority(BUBBLE_AUTHORITY)
                                        .appendQueryParameter(EXTRA_RECIPIENT, recipientId.serialize())
                                        .appendQueryParameter(EXTRA_THREAD_ID, String.valueOf(threadId))
                                        .build());

        return intent;
      }

      intent.putExtra(EXTRA_RECIPIENT, recipientId.serialize());
      intent.putExtra(EXTRA_THREAD_ID, threadId);
      intent.putExtra(EXTRA_DISTRIBUTION_TYPE, distributionType);
      intent.putExtra(EXTRA_STARTING_POSITION, startingPosition);
      intent.putExtra(EXTRA_BORDERLESS, isBorderless);
      intent.putExtra(EXTRA_FIRST_TIME_IN_SELF_CREATED_GROUP, firstTimeInSelfCreatedGroup);

      if (draftText != null) {
        intent.putExtra(EXTRA_TEXT, draftText);
      }

      if (media != null) {
        intent.putParcelableArrayListExtra(EXTRA_MEDIA, media);
      }

      if (stickerLocator != null) {
        intent.putExtra(EXTRA_STICKER, stickerLocator);
      }

      if (dataUri != null && dataType != null) {
        intent.setDataAndType(dataUri, dataType);
      } else if (dataUri != null) {
        intent.setData(dataUri);
      } else if (dataType != null) {
        intent.setType(dataType);
      }

      return intent;
    }
  }
}
