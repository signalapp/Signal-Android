package org.thoughtcrime.securesms.conversation;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Spannable;
import android.text.SpannableString;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.google.android.material.button.MaterialButton;
import com.google.common.collect.Sets;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BindableConversationItem;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.views.AutoRounder;
import org.thoughtcrime.securesms.util.views.Stub;
import org.thoughtcrime.securesms.verify.VerifyIdentityActivity;
import org.thoughtcrime.securesms.conversation.colors.Colorizer;
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectPart;
import org.thoughtcrime.securesms.conversation.ui.error.EnableCallNotificationSettingsDialog;
import org.thoughtcrime.securesms.database.model.IdentityRecord;
import org.thoughtcrime.securesms.database.model.GroupCallUpdateDetailsUtil;
import org.thoughtcrime.securesms.database.model.InMemoryMessageRecord;
import org.thoughtcrime.securesms.database.model.LiveUpdateMessage;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.UpdateDescription;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.Projection;
import org.thoughtcrime.securesms.util.ProjectionList;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.ACI;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public final class ConversationUpdateItem extends FrameLayout
                                          implements BindableConversationItem
{
  private static final String         TAG                   = Log.tag(ConversationUpdateItem.class);
  private static final ProjectionList EMPTY_PROJECTION_LIST = new ProjectionList();


  private Set<MultiselectPart> batchSelected;

  private TextView                  body;
  private MaterialButton            actionButton;
  private Stub<CardView>            donateButtonStub;
  private View                      background;
  private ConversationMessage       conversationMessage;
  private Recipient                 conversationRecipient;
  private Optional<MessageRecord>   nextMessageRecord;
  private MessageRecord             messageRecord;
  private boolean                   isMessageRequestAccepted;
  private LiveData<SpannableString> displayBody;
  private EventListener             eventListener;
  private boolean                   hasWallpaper;

  private final UpdateObserver updateObserver = new UpdateObserver();

  private final PresentOnChange          presentOnChange = new PresentOnChange();
  private final RecipientObserverManager senderObserver  = new RecipientObserverManager(presentOnChange);
  private final RecipientObserverManager groupObserver   = new RecipientObserverManager(presentOnChange);

  public ConversationUpdateItem(Context context) {
    super(context);
  }

  public ConversationUpdateItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();
    this.body             = findViewById(R.id.conversation_update_body);
    this.actionButton     = findViewById(R.id.conversation_update_action);
    this.donateButtonStub = ViewUtil.findStubById(this, R.id.conversation_update_donate_action);
    this.background       = findViewById(R.id.conversation_update_background);

    this.setOnClickListener(new InternalClickListener(null));
  }

  @Override
  public void bind(@NonNull LifecycleOwner lifecycleOwner,
                   @NonNull ConversationMessage conversationMessage,
                   @NonNull Optional<MessageRecord> previousMessageRecord,
                   @NonNull Optional<MessageRecord> nextMessageRecord,
                   @NonNull GlideRequests glideRequests,
                   @NonNull Locale locale,
                   @NonNull Set<MultiselectPart> batchSelected,
                   @NonNull Recipient conversationRecipient,
                   @Nullable String searchQuery,
                   boolean pulseMention,
                   boolean hasWallpaper,
                   boolean isMessageRequestAccepted,
                   boolean allowedToPlayInline,
                   @NonNull Colorizer colorizer)
  {
    this.batchSelected = batchSelected;

    bind(lifecycleOwner, conversationMessage, previousMessageRecord, nextMessageRecord, conversationRecipient, hasWallpaper, isMessageRequestAccepted);
  }

  @Override
  public void setEventListener(@Nullable EventListener listener) {
    this.eventListener = listener;
  }

  @Override
  public @NonNull ConversationMessage getConversationMessage() {
    return conversationMessage;
  }

  private void bind(@NonNull LifecycleOwner lifecycleOwner,
                    @NonNull ConversationMessage conversationMessage,
                    @NonNull Optional<MessageRecord> previousMessageRecord,
                    @NonNull Optional<MessageRecord> nextMessageRecord,
                    @NonNull Recipient conversationRecipient,
                    boolean hasWallpaper,
                    boolean isMessageRequestAccepted)
  {
    this.hasWallpaper             = hasWallpaper;
    this.conversationMessage      = conversationMessage;
    this.messageRecord            = conversationMessage.getMessageRecord();
    this.nextMessageRecord        = nextMessageRecord;
    this.conversationRecipient    = conversationRecipient;
    this.isMessageRequestAccepted = isMessageRequestAccepted;

    senderObserver.observe(lifecycleOwner, messageRecord.getIndividualRecipient());

    if (conversationRecipient.isActiveGroup() && conversationMessage.getMessageRecord().isGroupCall()) {
      groupObserver.observe(lifecycleOwner, conversationRecipient);
    } else {
      groupObserver.observe(lifecycleOwner, null);
    }

    int textColor = ContextCompat.getColor(getContext(), R.color.conversation_item_update_text_color);
    if (ThemeUtil.isDarkTheme(getContext()) && hasWallpaper) {
      textColor = ContextCompat.getColor(getContext(), R.color.core_grey_15);
    }

    if (!ThemeUtil.isDarkTheme(getContext())) {
      if (hasWallpaper) {
        actionButton.setStrokeColor(ColorStateList.valueOf(getResources().getColor(R.color.core_grey_45)));
      } else {
        actionButton.setStrokeColor(ColorStateList.valueOf(getResources().getColor(R.color.signal_button_secondary_stroke)));
      }
    }

    UpdateDescription         updateDescription = Objects.requireNonNull(messageRecord.getUpdateDisplayBody(getContext()));
    LiveData<SpannableString> liveUpdateMessage = LiveUpdateMessage.fromMessageDescription(getContext(), updateDescription, textColor, true);
    LiveData<SpannableString> spannableMessage  = loading(liveUpdateMessage);

    observeDisplayBody(lifecycleOwner, spannableMessage);

    present(conversationMessage, nextMessageRecord, conversationRecipient, isMessageRequestAccepted);

    presentBackground(shouldCollapse(messageRecord, previousMessageRecord),
                      shouldCollapse(messageRecord, nextMessageRecord),
                      hasWallpaper);
  }

  private static boolean shouldCollapse(@NonNull MessageRecord current, @NonNull Optional<MessageRecord> candidate)
  {
    return candidate.isPresent()      &&
           candidate.get().isUpdate() &&
           DateUtils.isSameDay(current.getTimestamp(), candidate.get().getTimestamp()) &&
           isSameType(current, candidate.get());
  }

  /** After a short delay, if the main data hasn't shown yet, then a loading message is displayed. */
  private @NonNull LiveData<SpannableString> loading(@NonNull LiveData<SpannableString> string) {
    return LiveDataUtil.until(string, LiveDataUtil.delay(250, new SpannableString(getContext().getString(R.string.ConversationUpdateItem_loading))));
  }

  @Override
  public void unbind() {
  }

  @Override
  public void showProjectionArea() {
  }

  @Override
  public void hideProjectionArea() {
    throw new UnsupportedOperationException("Call makes no sense for a conversation update item");
  }

  @Override
  public int getAdapterPosition() {
    throw new UnsupportedOperationException("Don't delegate to this method.");
  }

  @Override
  public @NonNull Projection getGiphyMp4PlayableProjection(@NonNull ViewGroup recyclerView) {
    throw new UnsupportedOperationException("ConversationUpdateItems cannot be projected into.");
  }

  @Override
  public boolean canPlayContent() {
    return false;
  }

  @Override
  public boolean shouldProjectContent() {
    return false;
  }

  @Override
  public @NonNull ProjectionList getColorizerProjections(@NonNull ViewGroup coordinateRoot) {
    return EMPTY_PROJECTION_LIST;
  }

  @Override
  public @Nullable View getHorizontalTranslationTarget() {
    return background;
  }

  static final class RecipientObserverManager {

    private final Observer<Recipient> recipientObserver;

    private LiveRecipient recipient;

    RecipientObserverManager(@NonNull Observer<Recipient> observer){
      this.recipientObserver = observer;
    }

    public void observe(@NonNull LifecycleOwner lifecycleOwner, @Nullable Recipient recipient) {
      if (this.recipient != null) {
        this.recipient.getLiveData().removeObserver(recipientObserver);
      }

      if (recipient != null) {
        this.recipient = recipient.live();
        this.recipient.getLiveData().observe(lifecycleOwner, recipientObserver);
      } else {
        this.recipient = null;
      }
    }

    @NonNull Recipient getObservedRecipient() {
      return recipient.get();
    }
  }

  @Override
  public @NonNull MultiselectPart getMultiselectPartForLatestTouch() {
    return conversationMessage.getMultiselectCollection().asSingle().getSinglePart();
  }

  @Override
  public int getTopBoundaryOfMultiselectPart(@NonNull MultiselectPart multiselectPart) {
    return getTop();
  }

  @Override
  public int getBottomBoundaryOfMultiselectPart(@NonNull MultiselectPart multiselectPart) {
    return getBottom();
  }

  @Override
  public boolean hasNonSelectableMedia() {
    return false;
  }

  private void observeDisplayBody(@NonNull LifecycleOwner lifecycleOwner, @Nullable LiveData<SpannableString> displayBody) {
    if (this.displayBody != displayBody) {
      if (this.displayBody != null) {
        this.displayBody.removeObserver(updateObserver);
      }

      this.displayBody = displayBody;

      if (this.displayBody != null) {
        this.displayBody.observe(lifecycleOwner, updateObserver);
      }
    }
  }

  private void setBodyText(@Nullable CharSequence text) {
    if (text == null) {
      body.setVisibility(INVISIBLE);
    } else {
      body.setText(text);
      body.setVisibility(VISIBLE);
    }
  }

  private void present(@NonNull ConversationMessage conversationMessage,
                       @NonNull Optional<MessageRecord> nextMessageRecord,
                       @NonNull Recipient conversationRecipient,
                       boolean isMessageRequestAccepted)
  {
    Set<MultiselectPart> multiselectParts = conversationMessage.getMultiselectCollection().toSet();

    setSelected(!Sets.intersection(multiselectParts, batchSelected).isEmpty());

    if (conversationMessage.getMessageRecord().isGroupV1MigrationEvent() &&
        (!nextMessageRecord.isPresent() || !nextMessageRecord.get().isGroupV1MigrationEvent()))
    {
      actionButton.setText(R.string.ConversationUpdateItem_learn_more);
      actionButton.setVisibility(VISIBLE);
      actionButton.setOnClickListener(v -> {
        if (batchSelected.isEmpty() && eventListener != null) {
          eventListener.onGroupMigrationLearnMoreClicked(conversationMessage.getMessageRecord().getGroupV1MigrationMembershipChanges());
        }
      });
    } else if (conversationMessage.getMessageRecord().isChatSessionRefresh() &&
               (!nextMessageRecord.isPresent() || !nextMessageRecord.get().isChatSessionRefresh()))
    {
      actionButton.setText(R.string.ConversationUpdateItem_learn_more);
      actionButton.setVisibility(VISIBLE);
      actionButton.setOnClickListener(v -> {
        if (batchSelected.isEmpty() && eventListener != null) {
          eventListener.onChatSessionRefreshLearnMoreClicked();
        }
      });
    } else if (conversationMessage.getMessageRecord().isIdentityUpdate()) {
      actionButton.setText(R.string.ConversationUpdateItem_learn_more);
      actionButton.setVisibility(VISIBLE);
      actionButton.setOnClickListener(v -> {
        if (batchSelected.isEmpty() && eventListener != null) {
          eventListener.onSafetyNumberLearnMoreClicked(conversationMessage.getMessageRecord().getIndividualRecipient());
        }
      });
    } else if (conversationMessage.getMessageRecord().isGroupCall()) {
      UpdateDescription updateDescription = MessageRecord.getGroupCallUpdateDescription(getContext(), conversationMessage.getMessageRecord().getBody(), true);
      Collection<ACI>   acis              = updateDescription.getMentioned();

      int text = 0;
      if (Util.hasItems(acis)) {
        if (acis.contains(Recipient.self().requireAci())) {
          text = R.string.ConversationUpdateItem_return_to_call;
        } else if (GroupCallUpdateDetailsUtil.parse(conversationMessage.getMessageRecord().getBody()).getIsCallFull()) {
          text = R.string.ConversationUpdateItem_call_is_full;
        } else {
          text = R.string.ConversationUpdateItem_join_call;
        }
      }

      if (text != 0 && conversationRecipient.isGroup() && conversationRecipient.isActiveGroup()) {
        actionButton.setText(text);
        actionButton.setVisibility(VISIBLE);
        actionButton.setOnClickListener(v -> {
          if (batchSelected.isEmpty() && eventListener != null) {
            eventListener.onJoinGroupCallClicked();
          }
        });
      } else {
        actionButton.setVisibility(GONE);
        actionButton.setOnClickListener(null);
      }
    } else if (conversationMessage.getMessageRecord().isSelfCreatedGroup()) {
      actionButton.setText(R.string.ConversationUpdateItem_invite_friends);
      actionButton.setVisibility(VISIBLE);
      actionButton.setOnClickListener(v -> {
        if (batchSelected.isEmpty() && eventListener != null) {
          eventListener.onInviteFriendsToGroupClicked(conversationRecipient.requireGroupId().requireV2());
        }
      });
    } else if ((conversationMessage.getMessageRecord().isMissedAudioCall() || conversationMessage.getMessageRecord().isMissedVideoCall()) && EnableCallNotificationSettingsDialog.shouldShow(getContext())) {
      actionButton.setVisibility(VISIBLE);
      actionButton.setText(R.string.ConversationUpdateItem_enable_call_notifications);
      actionButton.setOnClickListener(v -> {
        if (eventListener != null) {
          eventListener.onEnableCallNotificationsClicked();
        }
      });
    } else if (conversationMessage.getMessageRecord().isInMemoryMessageRecord() && ((InMemoryMessageRecord) conversationMessage.getMessageRecord()).showActionButton()) {
      InMemoryMessageRecord inMemoryMessageRecord = (InMemoryMessageRecord) conversationMessage.getMessageRecord();
      actionButton.setVisibility(VISIBLE);
      actionButton.setText(inMemoryMessageRecord.getActionButtonText());
      actionButton.setOnClickListener(v -> {
        if (eventListener != null) {
          eventListener.onInMemoryMessageClicked(inMemoryMessageRecord);
        }
      });
    } else if (conversationMessage.getMessageRecord().isGroupV2DescriptionUpdate()) {
      actionButton.setVisibility(VISIBLE);
      actionButton.setText(R.string.ConversationUpdateItem_view);
      actionButton.setOnClickListener(v -> {
        if (eventListener != null) {
          eventListener.onViewGroupDescriptionChange(conversationRecipient.getGroupId().orNull(), conversationMessage.getMessageRecord().getGroupV2DescriptionUpdate(), isMessageRequestAccepted);
        }
      });
    } else if (conversationMessage.getMessageRecord().isBadDecryptType() &&
               (!nextMessageRecord.isPresent() || !nextMessageRecord.get().isBadDecryptType()))
    {
      actionButton.setText(R.string.ConversationUpdateItem_learn_more);
      actionButton.setVisibility(VISIBLE);
      actionButton.setOnClickListener(v -> {
        if (batchSelected.isEmpty() && eventListener != null) {
          eventListener.onBadDecryptLearnMoreClicked(conversationMessage.getMessageRecord().getRecipient().getId());
        }
      });
    } else if (conversationMessage.getMessageRecord().isChangeNumber() && conversationMessage.getMessageRecord().getIndividualRecipient().isSystemContact()) {
      actionButton.setText(R.string.ConversationUpdateItem_update_contact);
      actionButton.setVisibility(VISIBLE);
      actionButton.setOnClickListener(v -> {
        if (batchSelected.isEmpty() && eventListener != null) {
          eventListener.onChangeNumberUpdateContact(conversationMessage.getMessageRecord().getIndividualRecipient());
        }
      });
    } else {
      actionButton.setVisibility(GONE);
      actionButton.setOnClickListener(null);
    }

    if (conversationMessage.getMessageRecord().isBoostRequest()) {
      actionButton.setVisibility(GONE);

      CardView donateButton = donateButtonStub.get();
      TextView buttonText   = donateButton.findViewById(R.id.conversation_update_donate_action_button);
      boolean  isSustainer  = SignalStore.donationsValues().isLikelyASustainer();

      donateButton.setVisibility(VISIBLE);
      donateButton.setOnClickListener(v -> {
        if (batchSelected.isEmpty() && eventListener != null) {
          eventListener.onDonateClicked();
        }
      });

      if (isSustainer) {
        buttonText.setText(R.string.ConversationUpdateItem_signal_boost);
        buttonText.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_boost_outline_16, 0, 0, 0);
      } else {
        buttonText.setText(R.string.ConversationUpdateItem_become_a_sustainer);
        buttonText.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
      }

      AutoRounder.autoSetCorners(donateButton, donateButton::setRadius);

    } else if (donateButtonStub.resolved()) {
      donateButtonStub.get().setVisibility(GONE);
    }
  }

  private void presentBackground(boolean collapseAbove, boolean collapseBelow, boolean hasWallpaper) {
    int marginDefault    = getContext().getResources().getDimensionPixelOffset(R.dimen.conversation_update_vertical_margin);
    int marginCollapsed  = 0;
    int paddingDefault   = getContext().getResources().getDimensionPixelOffset(R.dimen.conversation_update_vertical_padding);
    int paddingCollapsed = getContext().getResources().getDimensionPixelOffset(R.dimen.conversation_update_vertical_padding_collapsed);

    if (collapseAbove && collapseBelow) {
      ViewUtil.setTopMargin(background, marginCollapsed);
      ViewUtil.setBottomMargin(background, marginCollapsed);

      ViewUtil.setPaddingTop(background, paddingCollapsed);
      ViewUtil.setPaddingBottom(background, paddingCollapsed);

      ViewUtil.updateLayoutParams(background, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

      if (hasWallpaper) {
        background.setBackgroundResource(R.drawable.conversation_update_wallpaper_background_middle);
      } else {
        background.setBackground(null);
      }
    } else if (collapseAbove) {
      ViewUtil.setTopMargin(background, marginCollapsed);
      ViewUtil.setBottomMargin(background, marginDefault);

      ViewUtil.setPaddingTop(background, paddingDefault);
      ViewUtil.setPaddingBottom(background, paddingDefault);

      ViewUtil.updateLayoutParams(background, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

      if (hasWallpaper) {
        background.setBackgroundResource(R.drawable.conversation_update_wallpaper_background_bottom);
      } else {
        background.setBackground(null);
      }
    } else if (collapseBelow) {
      ViewUtil.setTopMargin(background, marginDefault);
      ViewUtil.setBottomMargin(background, marginCollapsed);

      ViewUtil.setPaddingTop(background, paddingDefault);
      ViewUtil.setPaddingBottom(background, paddingCollapsed);

      ViewUtil.updateLayoutParams(background, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

      if (hasWallpaper) {
        background.setBackgroundResource(R.drawable.conversation_update_wallpaper_background_top);
      } else {
        background.setBackground(null);
      }
    } else {
      ViewUtil.setTopMargin(background, marginDefault);
      ViewUtil.setBottomMargin(background, marginDefault);

      ViewUtil.setPaddingTop(background, paddingDefault);
      ViewUtil.setPaddingBottom(background, paddingDefault);

      ViewUtil.updateLayoutParams(background, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

      if (hasWallpaper) {
        background.setBackgroundResource(R.drawable.conversation_update_wallpaper_background_singular);
      } else {
        background.setBackground(null);
      }
    }
  }

  private static boolean isSameType(@NonNull MessageRecord current, @NonNull MessageRecord candidate) {
    return (current.isGroupUpdate()           && candidate.isGroupUpdate())           ||
           (current.isProfileChange()         && candidate.isProfileChange())         ||
           (current.isGroupCall()             && candidate.isGroupCall())             ||
           (current.isExpirationTimerUpdate() && candidate.isExpirationTimerUpdate()) ||
           (current.isChangeNumber()          && candidate.isChangeNumber());
  }

  @Override
  public void setOnClickListener(View.OnClickListener l) {
    super.setOnClickListener(new InternalClickListener(l));
  }

  private final class PresentOnChange implements Observer<Recipient> {

    @Override
    public void onChanged(Recipient recipient) {
      if (recipient.getId() == conversationRecipient.getId() && (conversationRecipient == null || !conversationRecipient.hasSameContent(recipient))) {
        conversationRecipient = recipient;
        present(conversationMessage, nextMessageRecord, conversationRecipient, isMessageRequestAccepted);
      }
    }
  }

  private final class UpdateObserver implements Observer<Spannable> {

    @Override
    public void onChanged(Spannable update) {
      setBodyText(update);
    }
  }

  private class InternalClickListener implements View.OnClickListener {

    @Nullable private final View.OnClickListener parent;

    InternalClickListener(@Nullable View.OnClickListener parent) {
      this.parent = parent;
    }

    @Override
    public void onClick(View v) {
      if ((!messageRecord.isIdentityUpdate()  &&
           !messageRecord.isIdentityDefault() &&
           !messageRecord.isIdentityVerified()) ||
          !batchSelected.isEmpty())
      {
        if (parent != null) parent.onClick(v);
        return;
      }

      final Recipient sender = ConversationUpdateItem.this.senderObserver.getObservedRecipient();

      IdentityUtil.getRemoteIdentityKey(getContext(), sender).addListener(new ListenableFuture.Listener<Optional<IdentityRecord>>() {
        @Override
        public void onSuccess(Optional<IdentityRecord> result) {
          if (result.isPresent()) {
            getContext().startActivity(VerifyIdentityActivity.newIntent(getContext(), result.get()));
          }
        }

        @Override
        public void onFailure(ExecutionException e) {
          Log.w(TAG, e);
        }
      });
    }
  }
}
