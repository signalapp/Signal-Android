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
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import org.thoughtcrime.securesms.BindableConversationItem;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.VerifyIdentityActivity;
import org.thoughtcrime.securesms.database.IdentityDatabase.IdentityRecord;
import org.thoughtcrime.securesms.database.model.LiveUpdateMessage;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.UpdateDescription;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public final class ConversationUpdateItem extends LinearLayout
                                          implements BindableConversationItem
{
  private static final String TAG = ConversationUpdateItem.class.getSimpleName();

  private Set<ConversationMessage> batchSelected;

  private TextView                  body;
  private LiveRecipient             sender;
  private ConversationMessage       conversationMessage;
  private MessageRecord             messageRecord;
  private Locale                    locale;
  private LiveData<Spannable> displayBody;

  private final UpdateObserver updateObserver = new UpdateObserver();
  private final SenderObserver senderObserver = new SenderObserver();

  public ConversationUpdateItem(Context context) {
    super(context);
  }

  public ConversationUpdateItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();
    this.body = findViewById(R.id.conversation_update_body);

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

    bind(lifecycleOwner, conversationMessage, locale);
  }

  @Override
  public void setEventListener(@Nullable EventListener listener) {
    // No events to report yet
  }

  @Override
  public ConversationMessage getConversationMessage() {
    return conversationMessage;
  }

  private void bind(@NonNull LifecycleOwner lifecycleOwner, @NonNull ConversationMessage conversationMessage, @NonNull Locale locale) {
    this.conversationMessage = conversationMessage;
    this.messageRecord       = conversationMessage.getMessageRecord();
    this.locale              = locale;

    observeSender(lifecycleOwner, messageRecord.getIndividualRecipient());

    UpdateDescription   updateDescription = Objects.requireNonNull(messageRecord.getUpdateDisplayBody(getContext()));
    LiveData<Spannable> liveUpdateMessage = LiveUpdateMessage.fromMessageDescription(getContext(), updateDescription);
    LiveData<Spannable> spannableMessage  = loading(liveUpdateMessage);

    present(conversationMessage);

    observeDisplayBody(lifecycleOwner, spannableMessage);
  }

  /** After a short delay, if the main data hasn't shown yet, then a loading message is displayed. */
  private @NonNull LiveData<Spannable> loading(@NonNull LiveData<Spannable> string) {
    return LiveDataUtil.until(string, LiveDataUtil.delay(250, new SpannableString(getContext().getString(R.string.ConversationUpdateItem_loading))));
  }

  @Override
  public void unbind() {
  }

  private void observeSender(@NonNull LifecycleOwner lifecycleOwner, @Nullable Recipient recipient) {
    if (sender != null) {
      sender.getLiveData().removeObserver(senderObserver);
    }

    if (recipient != null) {
      sender = recipient.live();
      sender.getLiveData().observe(lifecycleOwner, senderObserver);
    } else {
      sender = null;
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

  private void present(ConversationMessage conversationMessage) {
    if (batchSelected.contains(conversationMessage)) setSelected(true);
    else                                             setSelected(false);
  }

  @Override
  public void setOnClickListener(View.OnClickListener l) {
    super.setOnClickListener(new InternalClickListener(l));
  }

  private final class SenderObserver implements Observer<Recipient> {

    @Override
    public void onChanged(Recipient recipient) {
      present(conversationMessage);
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

      final Recipient sender = ConversationUpdateItem.this.sender.get();

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
