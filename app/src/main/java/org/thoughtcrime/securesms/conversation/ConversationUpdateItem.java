package org.thoughtcrime.securesms.conversation;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BindableConversationItem;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.VerifyIdentityActivity;
import org.thoughtcrime.securesms.database.IdentityDatabase.IdentityRecord;
import org.thoughtcrime.securesms.database.model.GroupCallUpdateDetailsUtil;
import org.thoughtcrime.securesms.database.model.LiveUpdateMessage;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.UpdateDescription;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public final class ConversationUpdateItem extends LinearLayout
                                          implements BindableConversationItem
{
  private static final String TAG = ConversationUpdateItem.class.getSimpleName();

  private Set<ConversationMessage> batchSelected;

  private TextView                body;
  private TextView                actionButton;
  private ConversationMessage     conversationMessage;
  private Recipient               conversationRecipient;
  private Optional<MessageRecord> nextMessageRecord;
  private MessageRecord           messageRecord;
  private LiveData<Spannable>     displayBody;
  private EventListener           eventListener;

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
    this.body         = findViewById(R.id.conversation_update_body);
    this.actionButton = findViewById(R.id.conversation_update_action);

    this.setOnClickListener(new InternalClickListener(null));
  }

  @Override
  public void bind(@NonNull LifecycleOwner lifecycleOwner,
                   @NonNull ConversationMessage conversationMessage,
                   @NonNull Optional<MessageRecord> previousMessageRecord,
                   @NonNull Optional<MessageRecord> nextMessageRecord,
                   @NonNull GlideRequests glideRequests,
                   @NonNull Locale locale,
                   @NonNull Set<ConversationMessage> batchSelected,
                   @NonNull Recipient conversationRecipient,
                   @Nullable String searchQuery,
                   boolean pulseMention)
  {
    this.batchSelected = batchSelected;

    bind(lifecycleOwner, conversationMessage, nextMessageRecord, conversationRecipient);
  }

  @Override
  public void setEventListener(@Nullable EventListener listener) {
    this.eventListener = listener;
  }

  @Override
  public ConversationMessage getConversationMessage() {
    return conversationMessage;
  }

  private void bind(@NonNull LifecycleOwner lifecycleOwner,
                    @NonNull ConversationMessage conversationMessage,
                    @NonNull Optional<MessageRecord> nextMessageRecord,
                    @NonNull Recipient conversationRecipient)
  {
    this.conversationMessage   = conversationMessage;
    this.messageRecord         = conversationMessage.getMessageRecord();
    this.nextMessageRecord     = nextMessageRecord;
    this.conversationRecipient = conversationRecipient;

    senderObserver.observe(lifecycleOwner, messageRecord.getIndividualRecipient());

    if (conversationRecipient.isActiveGroup() && conversationMessage.getMessageRecord().isGroupCall()) {
      groupObserver.observe(lifecycleOwner, conversationRecipient);
    } else {
      groupObserver.observe(lifecycleOwner, null);
    }

    UpdateDescription   updateDescription = Objects.requireNonNull(messageRecord.getUpdateDisplayBody(getContext()));
    LiveData<Spannable> liveUpdateMessage = LiveUpdateMessage.fromMessageDescription(getContext(), updateDescription, ContextCompat.getColor(getContext(), R.color.conversation_item_update_text_color));
    LiveData<Spannable> spannableMessage  = loading(liveUpdateMessage);

    observeDisplayBody(lifecycleOwner, spannableMessage);
  }

  /** After a short delay, if the main data hasn't shown yet, then a loading message is displayed. */
  private @NonNull LiveData<Spannable> loading(@NonNull LiveData<Spannable> string) {
    return LiveDataUtil.until(string, LiveDataUtil.delay(250, new SpannableString(getContext().getString(R.string.ConversationUpdateItem_loading))));
  }

  @Override
  public void unbind() {
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

  private void observeDisplayBody(@NonNull LifecycleOwner lifecycleOwner, @Nullable LiveData<Spannable> displayBody) {
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

  private void present(ConversationMessage conversationMessage, @NonNull Optional<MessageRecord> nextMessageRecord, @NonNull Recipient conversationRecipient) {
    if (batchSelected.contains(conversationMessage)) setSelected(true);
    else                                             setSelected(false);

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
    } else if (conversationMessage.getMessageRecord().isFailedDecryptionType() &&
              (!nextMessageRecord.isPresent() || !nextMessageRecord.get().isFailedDecryptionType()))
    {
      actionButton.setText(R.string.ConversationUpdateItem_learn_more);
      actionButton.setVisibility(VISIBLE);
      actionButton.setOnClickListener(v -> {
        if (batchSelected.isEmpty() && eventListener != null) {
          eventListener.onDecryptionFailedLearnMoreClicked();
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
      Collection<UUID>  uuids             = updateDescription.getMentioned();

      int text = 0;
      if (Util.hasItems(uuids)) {
        if (uuids.contains(TextSecurePreferences.getLocalUuid(getContext()))) {
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
    } else {
      actionButton.setVisibility(GONE);
      actionButton.setOnClickListener(null);
    }
  }

  @Override
  public void setOnClickListener(View.OnClickListener l) {
    super.setOnClickListener(new InternalClickListener(l));
  }

  private final class PresentOnChange implements Observer<Recipient> {

    @Override
    public void onChanged(Recipient recipient) {
      if (recipient.getId() == conversationRecipient.getId()) {
        conversationRecipient = recipient;
      }
      present(conversationMessage, nextMessageRecord, conversationRecipient);
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
