package org.thoughtcrime.securesms.conversation;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.badges.models.Badge;
import org.thoughtcrime.securesms.conversation.colors.ChatColors;
import org.thoughtcrime.securesms.conversation.v2.ConversationActivity;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.ThreadTable;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.mms.SlideFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.stickers.StickerLocator;
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper;
import org.whispersystems.signalservice.api.util.Preconditions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class ConversationIntents {
  private static final String TAG = Log.tag(ConversationIntents.class);

  private static final String BUBBLE_AUTHORITY                       = "bubble";
  private static final String NOTIFICATION_CUSTOM_SCHEME             = "custom";
  private static final String EXTRA_RECIPIENT                        = "recipient_id";
  private static final String EXTRA_THREAD_ID                        = "thread_id";
  private static final String EXTRA_TEXT                             = "draft_text";
  private static final String EXTRA_MEDIA                            = "media_list";
  private static final String EXTRA_STICKER                          = "sticker_extra";
  private static final String EXTRA_BORDERLESS                       = "borderless_extra";
  private static final String EXTRA_DISTRIBUTION_TYPE                = "distribution_type";
  private static final String EXTRA_STARTING_POSITION                = "starting_position";
  private static final String EXTRA_FIRST_TIME_IN_SELF_CREATED_GROUP = "first_time_in_group";
  private static final String EXTRA_WITH_SEARCH_OPEN                 = "with_search_open";
  private static final String EXTRA_GIFT_BADGE                       = "gift_badge";
  private static final String EXTRA_SHARE_DATA_TIMESTAMP             = "share_data_timestamp";
  private static final String EXTRA_CONVERSATION_TYPE                = "conversation_type";
  private static final String INTENT_DATA                            = "intent_data";
  private static final String INTENT_TYPE                            = "intent_type";

  private ConversationIntents() {
  }

  /**
   * Create a conversation builder for the given recipientId / threadId. Thread ids are required for CFV2,
   * so we will resolve the Recipient into a ThreadId if the threadId is invalid (below 0)
   *
   * @param context     Context for Intent creation
   * @param recipientId The RecipientId to query the thread ID for if the passed one is invalid.
   * @param threadId    The threadId, or -1L
   * @return A Single that will return a builder to create the conversation intent.
   */
  @MainThread
  public static @NonNull Single<Builder> createBuilder(@NonNull Context context, @NonNull RecipientId recipientId, long threadId) {
    if (threadId > 0L) {
      return Single.just(createBuilderSync(context, recipientId, threadId));
    } else {
      return Single.fromCallable(() -> {
        long newThreadId = SignalDatabase.threads().getOrCreateThreadIdFor(Recipient.resolved(recipientId));
        return createBuilderSync(context, recipientId, newThreadId);
      }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
    }
  }

  public static @NonNull Builder createPopUpBuilder(@NonNull Context context, @NonNull RecipientId recipientId, long threadId) {
    return new Builder(context, ConversationPopupActivity.class, recipientId, threadId, ConversationScreenType.POPUP);
  }

  public static @NonNull Intent createBubbleIntent(@NonNull Context context, @NonNull RecipientId recipientId, long threadId) {
    return new Builder(context, BubbleConversationActivity.class, recipientId, threadId, ConversationScreenType.BUBBLE).build();
  }

  /**
   * Create a Builder for a Conversation Intent. Does not perform a lookup for the thread id if the thread id is < 1. For CFV2, this is
   * considered an invalid state and will be met with an IllegalArgumentException.
   *
   * @param context     Context for Intent creation
   * @param recipientId The recipientId, only used if the threadId is not valid
   * @param threadId    The threadId, required for CFV2.
   * @return A builder that can be used to create a conversation intent.
   */
  public static @NonNull Builder createBuilderSync(@NonNull Context context, @NonNull RecipientId recipientId, long threadId) {
    Preconditions.checkArgument(threadId > 0, "threadId is invalid");
    return new Builder(context, ConversationActivity.class, recipientId, threadId, ConversationScreenType.NORMAL);
  }

  static @Nullable Uri getIntentData(@NonNull Bundle bundle) {
    return bundle.getParcelable(INTENT_DATA);
  }

  static @Nullable String getIntentType(@NonNull Bundle bundle) {
    return bundle.getString(INTENT_TYPE);
  }

  public static @NonNull Bundle createParentFragmentArguments(@NonNull Intent intent) {
    Bundle bundle = new Bundle();

    if (intent.getExtras() != null) {
      bundle.putAll(intent.getExtras());
    }

    bundle.putParcelable(INTENT_DATA, intent.getData());
    bundle.putString(INTENT_TYPE, intent.getType());

    return bundle;
  }

  public static boolean isBubbleIntentUri(@Nullable Uri uri) {
    return uri != null && Objects.equals(uri.getAuthority(), BUBBLE_AUTHORITY);
  }

  static boolean isNotificationIntentUri(@Nullable Uri uri) {
    return uri != null && Objects.equals(uri.getScheme(), NOTIFICATION_CUSTOM_SCHEME);
  }

  public final static class Args {
    private final RecipientId            recipientId;
    private final long                   threadId;
    private final String                 draftText;
    private final Uri                    draftMedia;
    private final String                 draftContentType;
    private final SlideFactory.MediaType draftMediaType;
    private final ArrayList<Media>       media;
    private final StickerLocator         stickerLocator;
    private final boolean                isBorderless;
    private final int                    distributionType;
    private final int                    startingPosition;
    private final boolean                firstTimeInSelfCreatedGroup;
    private final boolean                withSearchOpen;
    private final Badge                  giftBadge;
    private final long                   shareDataTimestamp;
    private final ConversationScreenType conversationScreenType;

    public static Args from(@NonNull Bundle arguments) {
      Uri intentDataUri = getIntentData(arguments);
      if (isBubbleIntentUri(intentDataUri)) {
        return new Args(RecipientId.from(intentDataUri.getQueryParameter(EXTRA_RECIPIENT)),
                        Long.parseLong(intentDataUri.getQueryParameter(EXTRA_THREAD_ID)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        ThreadTable.DistributionTypes.DEFAULT,
                        -1,
                        false,
                        false,
                        null,
                        -1L,
                        ConversationScreenType.BUBBLE);
      }

      return new Args(RecipientId.from(Objects.requireNonNull(arguments.getString(EXTRA_RECIPIENT))),
                      arguments.getLong(EXTRA_THREAD_ID, -1),
                      arguments.getString(EXTRA_TEXT),
                      ConversationIntents.getIntentData(arguments),
                      ConversationIntents.getIntentType(arguments),
                      arguments.getParcelableArrayList(EXTRA_MEDIA),
                      arguments.getParcelable(EXTRA_STICKER),
                      arguments.getBoolean(EXTRA_BORDERLESS, false),
                      arguments.getInt(EXTRA_DISTRIBUTION_TYPE, ThreadTable.DistributionTypes.DEFAULT),
                      arguments.getInt(EXTRA_STARTING_POSITION, -1),
                      arguments.getBoolean(EXTRA_FIRST_TIME_IN_SELF_CREATED_GROUP, false),
                      arguments.getBoolean(EXTRA_WITH_SEARCH_OPEN, false),
                      arguments.getParcelable(EXTRA_GIFT_BADGE),
                      arguments.getLong(EXTRA_SHARE_DATA_TIMESTAMP, -1L),
                      ConversationScreenType.from(arguments.getInt(EXTRA_CONVERSATION_TYPE, 0)));
    }

    private Args(@NonNull RecipientId recipientId,
                 long threadId,
                 @Nullable String draftText,
                 @Nullable Uri draftMedia,
                 @Nullable String draftContentType,
                 @Nullable ArrayList<Media> media,
                 @Nullable StickerLocator stickerLocator,
                 boolean isBorderless,
                 int distributionType,
                 int startingPosition,
                 boolean firstTimeInSelfCreatedGroup,
                 boolean withSearchOpen,
                 @Nullable Badge giftBadge,
                 long shareDataTimestamp,
                 @NonNull ConversationScreenType conversationScreenType)
    {
      this.recipientId                 = recipientId;
      this.threadId                    = threadId;
      this.draftText                   = draftText;
      this.draftMedia                  = draftMedia;
      this.draftContentType            = draftContentType;
      this.media                       = media;
      this.stickerLocator              = stickerLocator;
      this.isBorderless                = isBorderless;
      this.distributionType            = distributionType;
      this.startingPosition            = startingPosition;
      this.firstTimeInSelfCreatedGroup = firstTimeInSelfCreatedGroup;
      this.withSearchOpen              = withSearchOpen;
      this.giftBadge                   = giftBadge;
      this.shareDataTimestamp          = shareDataTimestamp;
      this.conversationScreenType      = conversationScreenType;
      this.draftMediaType              = SlideFactory.MediaType.from(draftContentType);
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

    public @Nullable Uri getDraftMedia() {
      return draftMedia;
    }

    public @Nullable String getDraftContentType() {
      return draftContentType;
    }

    public @Nullable SlideFactory.MediaType getDraftMediaType() {
      return draftMediaType;
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

    public @Nullable ChatWallpaper getWallpaper() {
      return Recipient.resolved(recipientId).getWallpaper();
    }

    public @NonNull ChatColors getChatColors() {
      return Recipient.resolved(recipientId).getChatColors();
    }

    public boolean isWithSearchOpen() {
      return withSearchOpen;
    }

    public @Nullable Badge getGiftBadge() {
      return giftBadge;
    }

    public long getShareDataTimestamp() {
      return shareDataTimestamp;
    }

    public @NonNull ConversationScreenType getConversationScreenType() {
      return conversationScreenType;
    }

    public boolean canInitializeFromDatabase() {
      return draftText == null && (draftMedia == null || ConversationIntents.isBubbleIntentUri(draftMedia) || ConversationIntents.isNotificationIntentUri(draftMedia)) && draftMediaType == null;
    }
  }

  public final static class Builder {
    private final Context                   context;
    private final Class<? extends Activity> conversationActivityClass;
    private final RecipientId               recipientId;
    private final long                      threadId;
    private final ConversationScreenType    conversationScreenType;

    private String                 draftText;
    private List<Media>            media;
    private StickerLocator         stickerLocator;
    private boolean                isBorderless;
    private int                    distributionType   = ThreadTable.DistributionTypes.DEFAULT;
    private int                    startingPosition   = -1;
    private Uri                    dataUri;
    private String                 dataType;
    private boolean                firstTimeInSelfCreatedGroup;
    private boolean                withSearchOpen;
    private Badge                  giftBadge;
    private long                   shareDataTimestamp = -1L;

    private Builder(@NonNull Context context,
                    @NonNull Class<? extends Activity> conversationActivityClass,
                    @NonNull RecipientId recipientId,
                    long threadId,
                    @NonNull ConversationScreenType conversationScreenType)
    {
      this.context                   = context;
      this.conversationActivityClass = conversationActivityClass;
      this.recipientId               = recipientId;
      this.threadId                  = checkThreadId(threadId);
      this.conversationScreenType    = conversationScreenType;
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

    public @NonNull Builder withSearchOpen(boolean withSearchOpen) {
      this.withSearchOpen = withSearchOpen;
      return this;
    }

    public Builder firstTimeInSelfCreatedGroup() {
      this.firstTimeInSelfCreatedGroup = true;
      return this;
    }

    public Builder withGiftBadge(@NonNull Badge badge) {
      this.giftBadge = badge;
      return this;
    }

    public Builder withShareDataTimestamp(long timestamp) {
      this.shareDataTimestamp = timestamp;
      return this;
    }

    public @NonNull Intent build() {
      if (stickerLocator != null && media != null) {
        throw new IllegalStateException("Cannot have both sticker and media array");
      }

      Intent intent = new Intent(context, conversationActivityClass);

      intent.setAction(Intent.ACTION_DEFAULT);

      if (conversationScreenType.isInBubble()) {
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
      intent.putExtra(EXTRA_WITH_SEARCH_OPEN, withSearchOpen);
      intent.putExtra(EXTRA_GIFT_BADGE, giftBadge);
      intent.putExtra(EXTRA_SHARE_DATA_TIMESTAMP, shareDataTimestamp);
      intent.putExtra(EXTRA_CONVERSATION_TYPE, conversationScreenType.code);

      if (draftText != null) {
        intent.putExtra(EXTRA_TEXT, draftText);
      }

      if (media != null) {
        intent.putParcelableArrayListExtra(EXTRA_MEDIA, new ArrayList<>(media));
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

      Bundle args = ConversationIntents.createParentFragmentArguments(intent);

      return intent.putExtras(args);
    }
  }

  public enum ConversationScreenType {
    NORMAL(0),
    BUBBLE(1),
    POPUP(2);

    private final int code;

    ConversationScreenType(int code) {
      this.code = code;
    }

    public boolean isInBubble() {
      return Objects.equals(this, BUBBLE);
    }

    public boolean isInPopup() {
      return Objects.equals(this, POPUP);
    }

    public boolean isNormal() {
      return Objects.equals(this, NORMAL);
    }

    private static @NonNull ConversationScreenType from(int code) {
      for (ConversationScreenType type : values()) {
        if (type.code == code) {
          return type;
        }
      }

      return NORMAL;
    }
  }

  private static long checkThreadId(long threadId) {
    if (threadId < 0) {
      throw new IllegalArgumentException("ThreadId is a required field in CFV2");
    } else {
      return threadId;
    }
  }
}
