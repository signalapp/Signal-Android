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

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;

import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.makeramen.roundedimageview.RoundedDrawable;

import org.signal.core.util.DimensionUnit;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.BindableConversationListItem;
import org.thoughtcrime.securesms.OverlayTransformation;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.Unbindable;
import org.thoughtcrime.securesms.badges.BadgeImageView;
import org.thoughtcrime.securesms.components.AlertView;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.components.DeliveryStatusView;
import org.thoughtcrime.securesms.components.FromTextView;
import org.thoughtcrime.securesms.components.TypingIndicatorView;
import org.thoughtcrime.securesms.components.emoji.EmojiStrings;
import org.thoughtcrime.securesms.conversationlist.model.Conversation;
import org.thoughtcrime.securesms.conversationlist.model.ConversationSet;
import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.LiveUpdateMessage;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.database.model.UpdateDescription;
import org.thoughtcrime.securesms.glide.GlideLiveDataTarget;
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader;
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
import org.thoughtcrime.securesms.util.SpanUtil;
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
  private ThreadRecord        thread;
  private boolean             batchMode;
  private Locale              locale;
  private String              highlightSubstring;
  private BadgeImageView      badge;
  private View                checkContainer;
  private View                uncheckedView;
  private View                checkedView;
  private int                 thumbSize;
  private GlideLiveDataTarget thumbTarget;

  private int             unreadCount;
  private AvatarImageView contactPhotoImage;

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
    this.contactPhotoImage       = findViewById(R.id.conversation_list_item_avatar);
    this.archivedView            = findViewById(R.id.conversation_list_item_archived);
    this.unreadIndicator         = findViewById(R.id.conversation_list_item_unread_indicator);
    this.badge                   = findViewById(R.id.conversation_list_item_badge);
    this.checkContainer          = findViewById(R.id.conversation_list_item_check_container);
    this.uncheckedView           = findViewById(R.id.conversation_list_item_unchecked);
    this.checkedView             = findViewById(R.id.conversation_list_item_checked);
    this.thumbSize               = (int) DimensionUnit.SP.toPixels(20f);
    this.thumbTarget             = new GlideLiveDataTarget(thumbSize, thumbSize);

    getLayoutTransition().setDuration(150);
  }

  @Override
  public void bind(@NonNull ThreadRecord thread,
                   @NonNull GlideRequests glideRequests,
                   @NonNull Locale locale,
                   @NonNull Set<Long> typingThreads,
                   @NonNull ConversationSet selectedConversations)
  {
    bindThread(thread, glideRequests, locale, typingThreads, selectedConversations, null);
  }

  public void bindThread(@NonNull ThreadRecord thread,
                         @NonNull GlideRequests glideRequests,
                         @NonNull Locale locale,
                         @NonNull Set<Long> typingThreads,
                         @NonNull ConversationSet selectedConversations,
                         @Nullable String highlightSubstring)
  {
    observeRecipient(thread.getRecipient().live());
    observeDisplayBody(null);
    setSubjectViewText(null);

    this.threadId           = thread.getThreadId();
    this.glideRequests      = glideRequests;
    this.unreadCount        = thread.getUnreadCount();
    this.lastSeen           = thread.getLastSeen();
    this.thread             = thread;
    this.locale             = locale;
    this.highlightSubstring = highlightSubstring;

    if (highlightSubstring != null) {
      String name = recipient.get().isSelf() ? getContext().getString(R.string.note_to_self) : recipient.get().getDisplayName(getContext());

      this.fromView.setText(recipient.get(), SearchUtil.getHighlightedSpan(locale, SpanUtil::getMediumBoldSpan, name, highlightSubstring, SearchUtil.MATCH_ALL), false, null);
    } else {
      this.fromView.setText(recipient.get(), false);
    }

    this.typingThreads = typingThreads;
    updateTypingIndicator(typingThreads);

    observeDisplayBody(getThreadDisplayBody(getContext(), thread, glideRequests, thumbSize, thumbTarget));

    if (thread.getDate() > 0) {
      CharSequence date = DateUtils.getBriefRelativeTimeSpanString(getContext(), locale, thread.getDate());
      dateView.setText(date);
      dateView.setTypeface(thread.isRead() ? LIGHT_TYPEFACE : BOLD_TYPEFACE);
      dateView.setTextColor(thread.isRead() ? ContextCompat.getColor(getContext(), R.color.signal_text_secondary)
                                            : ContextCompat.getColor(getContext(), R.color.signal_text_primary));
    }

    if (thread.isArchived()) {
      this.archivedView.setVisibility(View.VISIBLE);
    } else {
      this.archivedView.setVisibility(View.GONE);
    }

    setStatusIcons(thread);
    setSelectedConversations(selectedConversations);
    setBadgeFromRecipient(recipient.get());
    setUnreadIndicator(thread);
    this.contactPhotoImage.setAvatar(glideRequests, recipient.get(), !batchMode);
  }

  private void setBadgeFromRecipient(Recipient recipient) {
    if (!recipient.isSelf()) {
      badge.setBadgeFromRecipient(recipient);
      badge.setClickable(false);
    } else {
      badge.setBadge(null);
    }
  }

  public void bindContact(@NonNull  Recipient     contact,
                          @NonNull  GlideRequests glideRequests,
                          @NonNull  Locale        locale,
                          @Nullable String        highlightSubstring)
  {
    observeRecipient(contact.live());
    observeDisplayBody(null);
    setSubjectViewText(null);

    this.glideRequests      = glideRequests;
    this.locale             = locale;
    this.highlightSubstring = highlightSubstring;

    fromView.setText(SearchUtil.getHighlightedSpan(locale, SpanUtil::getMediumBoldSpan, new SpannableString(contact.getDisplayName(getContext())), highlightSubstring, SearchUtil.MATCH_ALL));
    setSubjectViewText(SearchUtil.getHighlightedSpan(locale, SpanUtil::getBoldSpan, contact.getE164().or(""), highlightSubstring, SearchUtil.MATCH_ALL));
    dateView.setText("");
    archivedView.setVisibility(GONE);
    unreadIndicator.setVisibility(GONE);
    deliveryStatusIndicator.setNone();
    alertView.setNone();

    setSelectedConversations(new ConversationSet());
    setBadgeFromRecipient(recipient.get());
    contactPhotoImage.setAvatar(glideRequests, recipient.get(), !batchMode);
  }

  public void bindMessage(@NonNull  MessageResult messageResult,
                          @NonNull  GlideRequests glideRequests,
                          @NonNull  Locale        locale,
                          @Nullable String        highlightSubstring)
  {
    observeRecipient(messageResult.getConversationRecipient().live());
    observeDisplayBody(null);
    setSubjectViewText(null);

    this.glideRequests      = glideRequests;
    this.locale             = locale;
    this.highlightSubstring = highlightSubstring;

    fromView.setText(recipient.get(), false);
    setSubjectViewText(SearchUtil.getHighlightedSpan(locale, SpanUtil::getBoldSpan, messageResult.getBodySnippet(), highlightSubstring, SearchUtil.MATCH_ALL));
    dateView.setText(DateUtils.getBriefRelativeTimeSpanString(getContext(), locale, messageResult.getReceivedTimestampMs()));
    archivedView.setVisibility(GONE);
    unreadIndicator.setVisibility(GONE);
    deliveryStatusIndicator.setNone();
    alertView.setNone();

    setSelectedConversations(new ConversationSet());
    setBadgeFromRecipient(recipient.get());
    contactPhotoImage.setAvatar(glideRequests, recipient.get(), !batchMode);
  }

  @Override
  public void unbind() {
    if (this.recipient != null) {
      observeRecipient(null);
      setSelectedConversations(new ConversationSet());
      contactPhotoImage.setAvatar(glideRequests, null, !batchMode);
    }

    observeDisplayBody(null);
  }

  @Override
  public void setSelectedConversations(@NonNull ConversationSet conversations) {
    this.batchMode = !conversations.isEmpty();

    boolean selected = batchMode && conversations.containsThreadId(thread.getThreadId());
    setSelected(selected);

    if (recipient != null) {
      contactPhotoImage.setAvatar(glideRequests, recipient.get(), !batchMode);
    }
    
    if (batchMode && selected) {
      checkContainer.setVisibility(VISIBLE);
      uncheckedView.setVisibility(GONE);
      checkedView.setVisibility(VISIBLE);
    } else if (batchMode) {
      checkContainer.setVisibility(VISIBLE);
      uncheckedView.setVisibility(VISIBLE);
      checkedView.setVisibility(GONE);
    } else {
      checkContainer.setVisibility(GONE);
      uncheckedView.setVisibility(GONE);
      checkedView.setVisibility(GONE);
    }
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

      this.subjectView.setVisibility(VISIBLE);
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
    if (displayBody == null && glideRequests != null) {
      glideRequests.clear(thumbTarget);
    }

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
      return;
    }

    unreadIndicator.setText(unreadCount > 0 ? String.valueOf(unreadCount) : " ");
    unreadIndicator.setVisibility(View.VISIBLE);
  }

  @Override
  public void onRecipientChanged(@NonNull Recipient recipient) {
    if (this.recipient == null || !this.recipient.getId().equals(recipient.getId())) {
      Log.w(TAG, "Bad change! Local recipient doesn't match. Ignoring. Local: " + (this.recipient == null ? "null" : this.recipient.getId()) + ", Changed: " + recipient.getId());
      return;
    }

    if (highlightSubstring != null) {
      String name = recipient.isSelf() ? getContext().getString(R.string.note_to_self) : recipient.getDisplayName(getContext());
      fromView.setText(SearchUtil.getHighlightedSpan(locale, SpanUtil::getMediumBoldSpan, new SpannableString(name), highlightSubstring, SearchUtil.MATCH_ALL));
    } else {
      fromView.setText(recipient, false);
    }
    contactPhotoImage.setAvatar(glideRequests, recipient, !batchMode);
    setBadgeFromRecipient(recipient);
  }

  private static @NonNull LiveData<SpannableString> getThreadDisplayBody(@NonNull Context context,
                                                                         @NonNull ThreadRecord thread,
                                                                         @NonNull GlideRequests glideRequests,
                                                                         @Px int thumbSize,
                                                                         @NonNull GlideLiveDataTarget thumbTarget)
  {
    int defaultTint = ContextCompat.getColor(context, R.color.signal_text_secondary);

    if (!thread.isMessageRequestAccepted()) {
      return emphasisAdded(context, context.getString(R.string.ThreadRecord_message_request), defaultTint);
    } else if (SmsDatabase.Types.isGroupUpdate(thread.getType())) {
      if (thread.getRecipient().isPushV2Group()) {
        return emphasisAdded(context, MessageRecord.getGv2ChangeDescription(context, thread.getBody()), defaultTint);
      } else {
        return emphasisAdded(context, context.getString(R.string.ThreadRecord_group_updated), R.drawable.ic_update_group_16, defaultTint);
      }
    } else if (SmsDatabase.Types.isGroupQuit(thread.getType())) {
      return emphasisAdded(context, context.getString(R.string.ThreadRecord_left_the_group), R.drawable.ic_update_group_leave_16, defaultTint);
    } else if (SmsDatabase.Types.isKeyExchangeType(thread.getType())) {
      return emphasisAdded(context, context.getString(R.string.ConversationListItem_key_exchange_message), defaultTint);
    } else if (SmsDatabase.Types.isChatSessionRefresh(thread.getType())) {
      return emphasisAdded(context, context.getString(R.string.ThreadRecord_chat_session_refreshed), R.drawable.ic_refresh_16, defaultTint);
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
      return emphasisAdded(context, context.getString(R.string.ThreadRecord_called), R.drawable.ic_update_audio_call_outgoing_16, defaultTint);
    } else if (SmsDatabase.Types.isIncomingAudioCall(thread.getType()) || SmsDatabase.Types.isIncomingVideoCall(thread.getType())) {
      return emphasisAdded(context, context.getString(R.string.ThreadRecord_called_you), R.drawable.ic_update_audio_call_incoming_16, defaultTint);
    } else if (SmsDatabase.Types.isMissedAudioCall(thread.getType())) {
      return emphasisAdded(context, context.getString(R.string.ThreadRecord_missed_audio_call), R.drawable.ic_update_audio_call_missed_16, defaultTint);
    } else if (SmsDatabase.Types.isMissedVideoCall(thread.getType())) {
      return emphasisAdded(context, context.getString(R.string.ThreadRecord_missed_video_call), R.drawable.ic_update_video_call_missed_16, defaultTint);
    } else if (MmsSmsColumns.Types.isGroupCall(thread.getType())) {
      return emphasisAdded(context, MessageRecord.getGroupCallUpdateDescription(context, thread.getBody(), false), defaultTint);
    } else if (SmsDatabase.Types.isJoinedType(thread.getType())) {
      return emphasisAdded(recipientToStringAsync(thread.getRecipient().getId(), r -> new SpannableString(context.getString(R.string.ThreadRecord_s_is_on_signal, r.getDisplayName(context)))));
    } else if (SmsDatabase.Types.isExpirationTimerUpdate(thread.getType())) {
      int seconds = (int)(thread.getExpiresIn() / 1000);
      if (seconds <= 0) {
        return emphasisAdded(context, context.getString(R.string.ThreadRecord_disappearing_messages_disabled), R.drawable.ic_update_timer_disabled_16, defaultTint);
      }
      String time = ExpirationUtil.getExpirationDisplayValue(context, seconds);
      return emphasisAdded(context, context.getString(R.string.ThreadRecord_disappearing_message_time_updated_to_s, time), R.drawable.ic_update_timer_16, defaultTint);
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
    } else if (SmsDatabase.Types.isChangeNumber(thread.getType()) || SmsDatabase.Types.isBoostRequest(thread.getType())) {
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
        String                    body      = removeNewlines(thread.getBody());
        LiveData<SpannableString> finalBody = LiveDataUtil.mapAsync(createFinalBodyWithMediaIcon(context, body, thread, glideRequests, thumbSize, thumbTarget), updatedBody -> {
          if (thread.getRecipient().isGroup()) {
            RecipientId groupMessageSender = thread.getGroupMessageSender();
            if (!groupMessageSender.isUnknown()) {
              return createGroupMessageUpdateString(context, updatedBody, Recipient.resolved(groupMessageSender), thread.isRead());
            }
          }

          return new SpannableString(updatedBody);
        });

        return whileLoadingShow(body, finalBody);
      }
    }
  }

  private static LiveData<CharSequence> createFinalBodyWithMediaIcon(@NonNull Context context,
                                                                     @NonNull String body,
                                                                     @NonNull ThreadRecord thread,
                                                                     @NonNull GlideRequests glideRequests,
                                                                     @Px int thumbSize,
                                                                     @NonNull GlideLiveDataTarget thumbTarget)
  {
    if (thread.getSnippetUri() == null) {
      return LiveDataUtil.just(body);
    }

    final String bodyWithoutMediaPrefix;

    if (body.startsWith(EmojiStrings.GIF)) {
      bodyWithoutMediaPrefix = body.replaceFirst(EmojiStrings.GIF, "");
    } else if (body.startsWith(EmojiStrings.VIDEO)) {
      bodyWithoutMediaPrefix = body.replaceFirst(EmojiStrings.VIDEO, "");
    } else if (body.startsWith(EmojiStrings.PHOTO)) {
      bodyWithoutMediaPrefix = body.replaceFirst(EmojiStrings.PHOTO, "");
    } else if (thread.getExtra() != null && thread.getExtra().getStickerEmoji() != null && body.startsWith(thread.getExtra().getStickerEmoji())) {
      bodyWithoutMediaPrefix = body.replaceFirst(thread.getExtra().getStickerEmoji(), "");
    } else {
      return LiveDataUtil.just(body);
    }

    glideRequests.asBitmap()
                 .load(new DecryptableStreamUriLoader.DecryptableUri(thread.getSnippetUri()))
                 .override(thumbSize, thumbSize)
                 .transform(
                     new OverlayTransformation(ContextCompat.getColor(context, R.color.transparent_black_08)),
                     new CenterCrop()
                 )
                 .into(thumbTarget);

    return Transformations.map(thumbTarget.getLiveData(), bitmap -> {
      if (bitmap == null) {
        return body;
      }

      RoundedDrawable drawable = RoundedDrawable.fromBitmap(bitmap);
      drawable.setBounds(0, 0, thumbSize, thumbSize);
      drawable.setCornerRadius(DimensionUnit.DP.toPixels(4));
      drawable.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

      CharSequence thumbnailSpan = SpanUtil.buildCenteredImageSpan(drawable);

      return new SpannableStringBuilder()
          .append(thumbnailSpan)
          .append(bodyWithoutMediaPrefix);
    });
  }

  private static SpannableString createGroupMessageUpdateString(@NonNull Context context,
                                                                @NonNull CharSequence body,
                                                                @NonNull Recipient recipient,
                                                                boolean read)
  {
    String sender = (recipient.isSelf() ? context.getString(R.string.MessageRecord_you)
                                        : recipient.getShortDisplayName(context)) + ": ";

    SpannableStringBuilder builder = new SpannableStringBuilder(sender).append(body);
    builder.setSpan(SpanUtil.getBoldSpan(),
                    0,
                    sender.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

    return new SpannableString(builder);
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

  private static @NonNull LiveData<SpannableString> emphasisAdded(@NonNull Context context, @NonNull String string, @DrawableRes int iconResource, @ColorInt int defaultTint) {
    return emphasisAdded(context, UpdateDescription.staticDescription(string, iconResource), defaultTint);
  }

  private static @NonNull LiveData<SpannableString> emphasisAdded(@NonNull Context context, @NonNull UpdateDescription description, @ColorInt int defaultTint) {
    return emphasisAdded(LiveUpdateMessage.fromMessageDescription(context, description, defaultTint, false));
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
