/*
 * Copyright (C) 2014-2017 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.conversationlist;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.text.style.TextAppearanceSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.interpolator.view.animation.FastOutLinearInInterpolator;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.recyclerview.widget.RecyclerView;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BindableConversationListItem;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.Unbindable;
import org.thoughtcrime.securesms.components.AlertView;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.DeliveryStatusView;
import org.thoughtcrime.securesms.components.FromTextView;
import org.thoughtcrime.securesms.components.ThumbnailView;
import org.thoughtcrime.securesms.components.TypingIndicatorView;
import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.LiveUpdateMessage;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.database.model.UpdateDescription;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientForeverObserver;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.search.MessageResult;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.Debouncer;
import org.thoughtcrime.securesms.util.ExpirationUtil;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.SearchUtil;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;

import static org.thoughtcrime.securesms.database.model.LiveUpdateMessage.recipientToStringAsync;

public final class ConversationListItem extends ConstraintLayout
                                        implements RecipientForeverObserver,
                                                   BindableConversationListItem,
                                                   Unbindable,
                                                   Observer<SpannableString>
{
  @SuppressWarnings("unused")
  private final static String TAG = Log.tag(ConversationListItem.class);

  private final static Typeface  BOLD_TYPEFACE  = Typeface.create("sans-serif-medium", Typeface.NORMAL);
  private final static Typeface  LIGHT_TYPEFACE = Typeface.create("sans-serif", Typeface.NORMAL);

  private Set<Long>           selectedThreads;
  private Set<Long>           typingThreads;
  private LiveRecipient       recipient;
  private long                threadId;
  private GlideRequests       glideRequests;
  private TextView            subjectView;
  private TypingIndicatorView typingView;
  private FromTextView        fromView;
  private TextView            dateView;
  private TextView            archivedView;
  private DeliveryStatusView  deliveryStatusIndicator;
  private AlertView           alertView;
  private TextView            unreadIndicator;
  private long                lastSeen;
  private View                divider;
  private ThreadRecord        thread;
  private boolean             batchMode;

  private enum ITEM_TYPE {TYPE_MANAGER_VIEW, TYPE_DIVIDER, TYPE_NORMAL_VIEW}

  private int defaultHeight;
  private int focusDefaultHeight;
  private int normalHeight;
  private int focusNormalHeight;
  private int normalPaddingX;
  private int focusPaddingX;
  private int normalTextSize;
  private int focusTextSize;
  private int normalColor;
  private int focusedColor;

  private int             unreadCount;
  private ThumbnailView   thumbnailView;

  private int viewType = 0;

  private final Debouncer subjectViewClearDebouncer = new Debouncer(150);

  private LiveData<SpannableString> displayBody;

  public ConversationListItem(Context context) {
    this(context, null);
  }

  public ConversationListItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    this.subjectView             = findViewById(R.id.conversation_list_item_summary);
    this.typingView              = findViewById(R.id.conversation_list_item_typing_indicator);
    this.fromView                = findViewById(R.id.conversation_list_item_name);
    this.dateView                = findViewById(R.id.conversation_list_item_date);
    this.deliveryStatusIndicator = findViewById(R.id.conversation_list_item_status);
    this.alertView               = findViewById(R.id.conversation_list_item_alert);
    this.thumbnailView           = findViewById(R.id.conversation_list_item_thumbnail);
    this.archivedView            = findViewById(R.id.conversation_list_item_archived);
    this.unreadIndicator         = findViewById(R.id.conversation_list_item_unread_indicator);
    this.divider                 = findViewById(R.id.dividerview);
    thumbnailView.setClickable(false);
    this.normalHeight            = (int)getResources().getDimension(R.dimen.conversation_list_item_normal_height);
    this.focusNormalHeight       = (int)getResources().getDimension(R.dimen.conversation_list_item_normal_height_focused);
    this.defaultHeight           = (int)getResources().getDimension(R.dimen.conversation_list_item_default_height);
    this.focusDefaultHeight      = (int)getResources().getDimension(R.dimen.conversation_list_item_default_height_focused);
    this.normalPaddingX          = (int)getResources().getDimension(R.dimen.conversation_list_item_padding_start_unfocused);
    this.focusPaddingX           = (int)getResources().getDimension(R.dimen.conversation_list_item_padding_start_focused);
    this.normalTextSize          = (int)getResources().getDimension(R.dimen.conversation_list_item_text_size_unfocused);
    this.focusTextSize           = (int)getResources().getDimension(R.dimen.conversation_list_item_text_size_focused);
    this.normalColor             = getResources().getColor(R.color.white_not_focus);
    this.focusedColor            = getResources().getColor(R.color.white_focus);
  }

  @Override
  public void bind(@NonNull ThreadRecord thread,
                   @NonNull GlideRequests glideRequests,
                   @NonNull Locale locale,
                   @NonNull Set<Long> typingThreads,
                   @NonNull Set<Long> selectedThreads,
                   boolean batchMode)
  {
    bind(thread, glideRequests, locale, typingThreads, selectedThreads, batchMode, null);
  }

  public void bind(@NonNull ThreadRecord thread,
                   @NonNull GlideRequests glideRequests,
                   @NonNull Locale locale,
                   @NonNull Set<Long> typingThreads,
                   @NonNull Set<Long> selectedThreads,
                   boolean batchMode,
                   @Nullable String highlightSubstring)
  {
    observeRecipient(thread.getRecipient().live());
    observeDisplayBody(null);
    setSubjectViewText(null);

    this.selectedThreads = selectedThreads;
    this.threadId        = thread.getThreadId();
    this.glideRequests   = glideRequests;
    this.unreadCount     = thread.getUnreadCount();
    this.lastSeen        = thread.getLastSeen();
    this.thread          = thread;

    if (highlightSubstring != null) {
      String name = recipient.get().isSelf() ? getContext().getString(R.string.note_to_self) : recipient.get().getDisplayName(getContext());

      this.fromView.setText(SearchUtil.getHighlightedSpan(locale, () -> new StyleSpan(Typeface.BOLD), name, highlightSubstring));
    } else {
      this.fromView.setText(recipient.get(), thread.isRead());
    }

    this.typingThreads = typingThreads;
    updateTypingIndicator(typingThreads);

    observeDisplayBody(getThreadDisplayBody(getContext(), thread));

    this.subjectView.setTypeface(thread.isRead() ? LIGHT_TYPEFACE : BOLD_TYPEFACE);
    this.subjectView.setTextColor(thread.isRead() ? ContextCompat.getColor(getContext(), R.color.signal_text_secondary)
                                                  : ContextCompat.getColor(getContext(), R.color.signal_text_primary));

    if (thread.getDate() > 0) {
      CharSequence date = DateUtils.getBriefRelativeTimeSpanString(getContext(), locale, thread.getDate());
      dateView.setText(date);
      dateView.setTypeface(thread.isRead() ? LIGHT_TYPEFACE : BOLD_TYPEFACE);
      dateView.setTextColor(thread.isRead() ? ContextCompat.getColor(getContext(), R.color.signal_text_secondary)
                                            : ContextCompat.getColor(getContext(), R.color.signal_text_primary));
    }

    if (thread.isArchived()) {
      this.archivedView.setVisibility(View.VISIBLE);
//      LayoutParams a = (LayoutParams) dateView.getLayoutParams();
//      a.addRule(RelativeLayout.CENTER_VERTICAL,0);
//      this.alertView.setLayoutParams(a);
    } else {
      this.archivedView.setVisibility(View.GONE);
    }

    this.divider.setVisibility(View.GONE);
    setStatusIcons(thread);
    setThumbnailSnippet(thread);
    setBatchMode(batchMode);
    setUnreadIndicator(thread);
    updateHeight(ITEM_TYPE.TYPE_NORMAL_VIEW);
    setFocusable(true);
  }

  public void bind(@NonNull  Recipient     contact,
                   @NonNull  GlideRequests glideRequests,
                   @NonNull  Locale        locale,
                   @Nullable String        highlightSubstring)
  {
    observeRecipient(contact.live());
    observeDisplayBody(null);
    setSubjectViewText(null);

    this.selectedThreads = Collections.emptySet();
    this.glideRequests   = glideRequests;


    fromView.setText(contact);
    fromView.setText(SearchUtil.getHighlightedSpan(locale, () -> new StyleSpan(Typeface.BOLD), new SpannableString(fromView.getText()), highlightSubstring));
    setSubjectViewText(SearchUtil.getHighlightedSpan(locale, () -> new StyleSpan(Typeface.BOLD), contact.getE164().or(""), highlightSubstring));
    dateView.setText("");
    archivedView.setVisibility(GONE);
    unreadIndicator.setVisibility(GONE);
    deliveryStatusIndicator.setNone();
    alertView.setNone();
    thumbnailView.setVisibility(GONE);
    this.divider.setVisibility(View.GONE);

    setBatchMode(false);
    updateHeight(ITEM_TYPE.TYPE_NORMAL_VIEW);
    setFocusable(true);
  }

  public void bind(@NonNull  MessageResult messageResult,
                   @NonNull  GlideRequests glideRequests,
                   @NonNull  Locale        locale,
                   @Nullable String        highlightSubstring)
  {
    observeRecipient(messageResult.getConversationRecipient().live());
    observeDisplayBody(null);
    setSubjectViewText(null);

    this.selectedThreads = Collections.emptySet();
    this.glideRequests   = glideRequests;

    fromView.setText(recipient.get(), true);
    setSubjectViewText(SearchUtil.getHighlightedSpan(locale, () -> new StyleSpan(Typeface.BOLD), messageResult.getBodySnippet(), highlightSubstring));
    dateView.setText(DateUtils.getBriefRelativeTimeSpanString(getContext(), locale, messageResult.getReceivedTimestampMs()));
    archivedView.setVisibility(GONE);
    unreadIndicator.setVisibility(GONE);
    deliveryStatusIndicator.setNone();
    alertView.setNone();
    thumbnailView.setVisibility(GONE);

    setBatchMode(false);
    updateHeight(ITEM_TYPE.TYPE_NORMAL_VIEW);
    setFocusable(true);
  }

  public void bind(@NonNull String text, boolean isDivider) {
    if (this.recipient != null) this.recipient.removeForeverObserver(this);

    archivedView.setVisibility(GONE);
    unreadIndicator.setVisibility(GONE);
    deliveryStatusIndicator.setNone();
    alertView.setNone();
    thumbnailView.setVisibility(GONE);
    if (isDivider) {
      this.divider.setVisibility(View.VISIBLE);
      fromView.setVisibility(View.GONE);
      updateHeight(ITEM_TYPE.TYPE_DIVIDER);
      setFocusable(false);
    } else {
      fromView.setText(text);
      this.divider.setVisibility(View.GONE);
      updateHeight(ITEM_TYPE.TYPE_MANAGER_VIEW);
      setFocusable(true);
    }

  }

  private void updateHeight(ITEM_TYPE type) {
    RecyclerView.LayoutParams params = (RecyclerView.LayoutParams)getLayoutParams();
    if (type == ITEM_TYPE.TYPE_MANAGER_VIEW) {
      params.height = defaultHeight;
    } else if (type == ITEM_TYPE.TYPE_DIVIDER) {
      params.height = (int)getResources().getDimension(R.dimen.conversation_list_item_divider_height);
    } else {
      params.height = normalHeight;
    }
    setLayoutParams(params);
  }

  public void updateItemParas(boolean hasFocus) {
    int paddingStart;
    int textSize;
    int itemHeight;
    int textColor;
    if(hasFocus) {
      paddingStart = focusPaddingX;
      textSize = focusTextSize;
      if (viewType >= 10) {
        itemHeight = focusDefaultHeight;
      } else {
        itemHeight = focusNormalHeight;
      }
      textColor = focusedColor;
    } else {
      paddingStart = normalPaddingX;
      textSize = normalTextSize;
      if (viewType >= 10) {
        itemHeight = defaultHeight;
      } else {
        itemHeight = normalHeight;
      }
      textColor = normalColor;
    }
    fromView.setTextSize(textSize);
    fromView.setTextColor(textColor);
    fromView.setFocused(hasFocus);
    setPadding(paddingStart, getPaddingTop(),getPaddingRight(),getPaddingBottom());
    RecyclerView.LayoutParams params = (RecyclerView.LayoutParams)getLayoutParams();
    params.height = itemHeight;
    setLayoutParams(params);
  }

  public void updateSearchItemParas(boolean hasFocus) {
    int paddingStart;
    int textSize;
    int contactNumTextSize;
    int itemHeight;
    int textColor;
    if(hasFocus) {
      paddingStart = focusPaddingX;
      textSize = focusTextSize;
      itemHeight = focusNormalHeight;
      textColor = focusedColor;
      contactNumTextSize = (int)getResources().getDimension(R.dimen.conversation_list_item_search_contact_number_text_size_focused);
      if (subjectView.getVisibility() == VISIBLE) {
        itemHeight = (int)getResources().getDimension(R.dimen.conversation_list_item_search_contact_height_focused);
      }

    } else {
      paddingStart = normalPaddingX;
      textSize = normalTextSize;
      itemHeight = normalHeight;
      textColor = normalColor;
      contactNumTextSize = (int)getResources().getDimension(R.dimen.conversation_list_item_search_contact_number_text_size_unfocused);
    }
    fromView.setTextSize(textSize);
    fromView.setTextColor(textColor);
    fromView.setFocused(hasFocus);
    if (subjectView.getVisibility() == VISIBLE) {
      subjectView.setTextSize(contactNumTextSize);
      subjectView.setTextColor(textColor);
    }
    setPadding(paddingStart, getPaddingTop(),getPaddingRight(),getPaddingBottom());
    RecyclerView.LayoutParams params = (RecyclerView.LayoutParams)getLayoutParams();
    params.height = itemHeight;
    setLayoutParams(params);
  }

  @Override
  public void unbind() {
    if (this.recipient != null) {
      observeRecipient(null);
      setBatchMode(false);
    }

    observeDisplayBody(null);
  }

  @Override
  public void setBatchMode(boolean batchMode) {
    this.batchMode = batchMode;
    setSelected(batchMode && selectedThreads.contains(thread.getThreadId()));
  }

  @Override
  public void updateTypingIndicator(@NonNull Set<Long> typingThreads) {
    if (typingThreads.contains(threadId)) {
      this.subjectView.setVisibility(INVISIBLE);

      this.typingView.setVisibility(VISIBLE);
      this.typingView.startAnimation();
    } else {
      this.typingView.setVisibility(GONE);
      this.typingView.stopAnimation();

//      this.subjectView.setVisibility(VISIBLE);
      this.subjectView.setVisibility(GONE);
    }
  }

  public Recipient getRecipient() {
    return recipient.get();
  }

  public long getThreadId() {
    return threadId;
  }

  public @NonNull ThreadRecord getThread() {
    return thread;
  }

  public int getUnreadCount() {
    return unreadCount;
  }

  public long getLastSeen() {
    return lastSeen;
  }

  public void setViewType(int type) {
    viewType = type;
  }

  public int getViewType() {
    return viewType;
  }
  public String getFromText() {
    return fromView.getText().toString();
  }


  private void observeRecipient(@Nullable LiveRecipient newRecipient) {
    if (this.recipient != null) {
      this.recipient.removeForeverObserver(this);
    }

    this.recipient = newRecipient;

    if (this.recipient != null) {
      this.recipient.observeForever(this);
    }
  }

  private void observeDisplayBody(@Nullable LiveData<SpannableString> displayBody) {
    if (this.displayBody != null) {
      this.displayBody.removeObserver(this);
    }

    this.displayBody = displayBody;

    if (this.displayBody != null) {
      this.displayBody.observeForever(this);
    }
  }

  private void setSubjectViewText(@Nullable CharSequence text) {
    if (text == null) {
      subjectViewClearDebouncer.publish(() -> subjectView.setText(null));
    } else {
      subjectViewClearDebouncer.clear();
      subjectView.setText(text);
      subjectView.setVisibility(VISIBLE);
    }
  }

  private void setThumbnailSnippet(ThreadRecord thread) {
    if (thread.getSnippetUri() != null) {
      this.thumbnailView.setVisibility(View.VISIBLE);
      this.thumbnailView.setImageResource(glideRequests, thread.getSnippetUri());
    } else {
      this.thumbnailView.setVisibility(View.GONE);
    }
  }

  private void setStatusIcons(ThreadRecord thread) {
    if (MmsSmsColumns.Types.isBadDecryptType(thread.getType())) {
      deliveryStatusIndicator.setNone();
      alertView.setFailed();
    } else if (!thread.isOutgoing()         ||
               thread.isOutgoingAudioCall() ||
               thread.isOutgoingVideoCall() ||
               thread.isVerificationStatusChange())
    {
      deliveryStatusIndicator.setNone();
      alertView.setNone();
    } else if (thread.isFailed()) {
      deliveryStatusIndicator.setNone();
      alertView.setFailed();
    } else if (thread.isPendingInsecureSmsFallback()) {
      deliveryStatusIndicator.setNone();
      alertView.setPendingApproval();
    } else {
      alertView.setNone();

      if (thread.getExtra() != null && thread.getExtra().isRemoteDelete()) {
        if (thread.isPending()) {
          deliveryStatusIndicator.setPending();
        } else {
          deliveryStatusIndicator.setNone();
        }
      } else {
        if (thread.isPending()) {
          deliveryStatusIndicator.setPending();
        } else if (thread.isRemoteRead()) {
          deliveryStatusIndicator.setRead();
        } else if (thread.isDelivered()) {
          deliveryStatusIndicator.setDelivered();
        } else {
          deliveryStatusIndicator.setSent();
        }
      }
    }
  }

  private void setUnreadIndicator(ThreadRecord thread) {
    if ((thread.isOutgoing() && !thread.isForcedUnread()) || thread.isRead()) {
      unreadIndicator.setVisibility(View.GONE);
      dateView.setVisibility(VISIBLE);
      return;
    }

    unreadIndicator.setText(unreadCount > 0 ? String.valueOf(unreadCount) : " ");
    unreadIndicator.setVisibility(View.VISIBLE);
    dateView.setVisibility(GONE);
  }

  @Override
  public void onRecipientChanged(@NonNull Recipient recipient) {
    if (this.recipient == null || !this.recipient.getId().equals(recipient.getId())) {
      Log.w(TAG, "Bad change! Local recipient doesn't match. Ignoring. Local: " + (this.recipient == null ? "null" : this.recipient.getId()) + ", Changed: " + recipient.getId());
      return;
    }

    fromView.setText(recipient, unreadCount == 0);
  }

  private static @NonNull LiveData<SpannableString> getThreadDisplayBody(@NonNull Context context, @NonNull ThreadRecord thread) {
    int defaultTint = thread.isRead() ? ContextCompat.getColor(context, R.color.signal_text_secondary)
                                      : ContextCompat.getColor(context, R.color.signal_text_primary);

    if (!thread.isMessageRequestAccepted()) {
      return emphasisAdded(context, context.getString(R.string.ThreadRecord_message_request), defaultTint);
    } else if (SmsDatabase.Types.isGroupUpdate(thread.getType())) {
      if (thread.getRecipient().isPushV2Group()) {
        return emphasisAdded(context, MessageRecord.getGv2ChangeDescription(context, thread.getBody()), defaultTint);
      } else {
        return emphasisAdded(context, context.getString(R.string.ThreadRecord_group_updated), defaultTint);
      }
    } else if (SmsDatabase.Types.isGroupQuit(thread.getType())) {
      return emphasisAdded(context, context.getString(R.string.ThreadRecord_left_the_group), defaultTint);
    } else if (SmsDatabase.Types.isKeyExchangeType(thread.getType())) {
      return emphasisAdded(context, context.getString(R.string.ConversationListItem_key_exchange_message), defaultTint);
    } else if (SmsDatabase.Types.isChatSessionRefresh(thread.getType())) {
      UpdateDescription description = UpdateDescription.staticDescription(context.getString(R.string.ThreadRecord_chat_session_refreshed), R.drawable.ic_refresh_16);
      return emphasisAdded(context, description, defaultTint);
    } else if (SmsDatabase.Types.isNoRemoteSessionType(thread.getType())) {
      return emphasisAdded(context, context.getString(R.string.MessageDisplayHelper_message_encrypted_for_non_existing_session), defaultTint);
    } else if (SmsDatabase.Types.isEndSessionType(thread.getType())) {
      return emphasisAdded(context, context.getString(R.string.ThreadRecord_secure_session_reset), defaultTint);
    } else if (MmsSmsColumns.Types.isLegacyType(thread.getType())) {
      return emphasisAdded(context, context.getString(R.string.MessageRecord_message_encrypted_with_a_legacy_protocol_version_that_is_no_longer_supported), defaultTint);
    } else if (MmsSmsColumns.Types.isDraftMessageType(thread.getType())) {
      String draftText = context.getString(R.string.ThreadRecord_draft);
      return emphasisAdded(context, draftText + " " + thread.getBody(), defaultTint);
    } else if (SmsDatabase.Types.isOutgoingAudioCall(thread.getType()) || SmsDatabase.Types.isOutgoingVideoCall(thread.getType())) {
      return emphasisAdded(context, context.getString(R.string.ThreadRecord_called), defaultTint);
    } else if (SmsDatabase.Types.isIncomingAudioCall(thread.getType()) || SmsDatabase.Types.isIncomingVideoCall(thread.getType())) {
      return emphasisAdded(context, context.getString(R.string.ThreadRecord_called_you), defaultTint);
    } else if (SmsDatabase.Types.isMissedAudioCall(thread.getType())) {
      return emphasisAdded(context, context.getString(R.string.ThreadRecord_missed_audio_call), defaultTint);
    } else if (SmsDatabase.Types.isMissedVideoCall(thread.getType())) {
      return emphasisAdded(context, context.getString(R.string.ThreadRecord_missed_video_call), defaultTint);
    } else if (MmsSmsColumns.Types.isGroupCall(thread.getType())) {
      return emphasisAdded(context, MessageRecord.getGroupCallUpdateDescription(context, thread.getBody(), false), defaultTint);
    } else if (SmsDatabase.Types.isJoinedType(thread.getType())) {
      return emphasisAdded(recipientToStringAsync(thread.getRecipient().getId(), r -> new SpannableString(context.getString(R.string.ThreadRecord_s_is_on_signal, r.getDisplayName(context)))));
    } else if (SmsDatabase.Types.isExpirationTimerUpdate(thread.getType())) {
      int seconds = (int)(thread.getExpiresIn() / 1000);
      if (seconds <= 0) {
        return emphasisAdded(context, context.getString(R.string.ThreadRecord_disappearing_messages_disabled), defaultTint);
      }
      String time = ExpirationUtil.getExpirationDisplayValue(context, seconds);
      return emphasisAdded(context, context.getString(R.string.ThreadRecord_disappearing_message_time_updated_to_s, time), defaultTint);
    } else if (SmsDatabase.Types.isIdentityUpdate(thread.getType())) {
      return emphasisAdded(recipientToStringAsync(thread.getRecipient().getId(), r -> {
        if (r.isGroup()) {
          return new SpannableString(context.getString(R.string.ThreadRecord_safety_number_changed));
        } else {
          return new SpannableString(context.getString(R.string.ThreadRecord_your_safety_number_with_s_has_changed, r.getDisplayName(context)));
        }
      }));
    } else if (SmsDatabase.Types.isIdentityVerified(thread.getType())) {
      return emphasisAdded(context, context.getString(R.string.ThreadRecord_you_marked_verified), defaultTint);
    } else if (SmsDatabase.Types.isIdentityDefault(thread.getType())) {
      return emphasisAdded(context, context.getString(R.string.ThreadRecord_you_marked_unverified), defaultTint);
    } else if (SmsDatabase.Types.isUnsupportedMessageType(thread.getType())) {
      return emphasisAdded(context, context.getString(R.string.ThreadRecord_message_could_not_be_processed), defaultTint);
    } else if (SmsDatabase.Types.isProfileChange(thread.getType())) {
      return emphasisAdded(context, "", defaultTint);
    } else if (MmsSmsColumns.Types.isBadDecryptType(thread.getType())) {
      return emphasisAdded(context, context.getString(R.string.ThreadRecord_delivery_issue), defaultTint);
    } else {
      ThreadDatabase.Extra extra = thread.getExtra();
      if (extra != null && extra.isViewOnce()) {
        return emphasisAdded(context, getViewOnceDescription(context, thread.getContentType()), defaultTint);
      } else if (extra != null && extra.isRemoteDelete()) {
        return emphasisAdded(context, context.getString(thread.isOutgoing() ? R.string.ThreadRecord_you_deleted_this_message : R.string.ThreadRecord_this_message_was_deleted), defaultTint);
      } else {
        String body = removeNewlines(thread.getBody());

        LiveData<SpannableString> finalBody = recipientToStringAsync(thread.getRecipient().getId(), threadRecipient -> {
          if (threadRecipient.isGroup()) {
            RecipientId groupMessageSender = thread.getGroupMessageSender();
            if (!groupMessageSender.isUnknown()) {
              return createGroupMessageUpdateString(context, body, Recipient.resolved(groupMessageSender), thread.isRead());
            }
          }
          return new SpannableString(body);
        });

        return whileLoadingShow(body, finalBody);
      }
    }
  }

  private static SpannableString createGroupMessageUpdateString(@NonNull Context context,
                                                                @NonNull String body,
                                                                @NonNull Recipient recipient,
                                                                boolean read)
  {
    String sender = (recipient.isSelf() ? context.getString(R.string.MessageRecord_you)
                                        : recipient.getShortDisplayName(context)) + ": ";

    SpannableString spannable = new SpannableString(sender + body);
    spannable.setSpan(new TextAppearanceSpan(context, read ? R.style.Signal_Text_Preview_Medium_Secondary
                                                           : R.style.Signal_Text_Preview_Medium_Primary),
                      0,
                      sender.length(),
                      Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

    return spannable;
  }

  /** After a short delay, if the main data hasn't shown yet, then a loading message is displayed. */
  private static @NonNull LiveData<SpannableString> whileLoadingShow(@NonNull String loading, @NonNull LiveData<SpannableString> string) {
    return LiveDataUtil.until(string, LiveDataUtil.delay(250, new SpannableString(loading)));
  }

  private static @NonNull String removeNewlines(@Nullable String text) {
    if (text == null) {
      return "";
    }

    if (text.indexOf('\n') >= 0) {
      return text.replaceAll("\n", " ");
    } else {
      return text;
    }
  }

  private static @NonNull LiveData<SpannableString> emphasisAdded(@NonNull Context context, @NonNull String string, @ColorInt int defaultTint) {
    return emphasisAdded(context, UpdateDescription.staticDescription(string, 0), defaultTint);
  }

  private static @NonNull LiveData<SpannableString> emphasisAdded(@NonNull Context context, @NonNull UpdateDescription description, @ColorInt int defaultTint) {
    return emphasisAdded(LiveUpdateMessage.fromMessageDescription(context, description, defaultTint));
  }

  private static @NonNull LiveData<SpannableString> emphasisAdded(@NonNull LiveData<SpannableString> description) {
    return Transformations.map(description, sequence -> {
      SpannableString spannable = new SpannableString(sequence);
      spannable.setSpan(new StyleSpan(Typeface.ITALIC),
                                      0,
                                      sequence.length(),
                                      Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      return spannable;
    });
  }

  private static String getViewOnceDescription(@NonNull Context context, @Nullable String contentType) {
    if (MediaUtil.isViewOnceType(contentType)) {
      return context.getString(R.string.ThreadRecord_view_once_media);
    } else if (MediaUtil.isVideoType(contentType)) {
      return context.getString(R.string.ThreadRecord_view_once_video);
    } else {
      return context.getString(R.string.ThreadRecord_view_once_photo);
    }
  }

  @Override
  public void onChanged(SpannableString spannableString) {
    setSubjectViewText(spannableString);

    if (typingThreads != null) {
      updateTypingIndicator(typingThreads);
    }
  }
}
