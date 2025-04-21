/*
 * Copyright (C) 2012 Moxie Marlinpsike
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
package org.thoughtcrime.securesms.database.model;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;

import com.annimon.stream.Stream;

import org.signal.core.util.Base64;
import org.signal.core.util.BidiUtil;
import org.signal.core.util.logging.Log;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiProvider;
import org.thoughtcrime.securesms.components.emoji.parsing.EmojiParser;
import org.thoughtcrime.securesms.database.MessageTypes;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList;
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context;
import org.thoughtcrime.securesms.database.model.databaseprotos.GroupCallUpdateDetails;
import org.thoughtcrime.securesms.database.model.databaseprotos.MessageExtras;
import org.thoughtcrime.securesms.database.model.databaseprotos.ProfileChangeDetails;
import org.thoughtcrime.securesms.database.model.databaseprotos.SessionSwitchoverEvent;
import org.thoughtcrime.securesms.database.model.databaseprotos.ThreadMergeEvent;
import org.thoughtcrime.securesms.emoji.EmojiSource;
import org.thoughtcrime.securesms.emoji.JumboEmoji;
import org.thoughtcrime.securesms.groups.GroupMigrationMembershipChange;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.ExpirationUtil;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.SignalE164Util;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import okio.ByteString;

/**
 * The base class for message record models that are displayed in
 * conversations, as opposed to models that are displayed in a thread list.
 * Encapsulates the shared data between both SMS and MMS messages.
 *
 * @author Moxie Marlinspike
 *
 */
public abstract class MessageRecord extends DisplayRecord {

  private static final String TAG = Log.tag(MessageRecord.class);

  private final long                     id;
  private final int                      authorDeviceId;
  private final Set<IdentityKeyMismatch> mismatches;
  private final Set<NetworkFailure>      networkFailures;
  private final int                      subscriptionId;
  private final long                     expiresIn;
  private final long                     expireStarted;
  private final int                      expireTimerVersion;
  private final boolean                  unidentified;
  private final List<ReactionRecord>     reactions;
  private final long                     serverTimestamp;
  private final boolean                  remoteDelete;
  private final long                     notifiedTimestamp;
  private final long                     receiptTimestamp;
  private final MessageId                originalMessageId;
  private final int                      revisionNumber;
  private final MessageExtras            messageExtras;

  protected Boolean isJumboji = null;

  MessageRecord(long id, String body, Recipient fromRecipient, int fromDeviceId, Recipient toRecipient,
                long dateSent, long dateReceived, long dateServer, long threadId,
                int deliveryStatus, boolean hasDeliveryReceipt, long type,
                Set<IdentityKeyMismatch> mismatches,
                Set<NetworkFailure> networkFailures,
                int subscriptionId,
                long expiresIn,
                long expireStarted,
                int expireTimerVersion,
                boolean hasReadReceipt,
                boolean unidentified,
                @NonNull List<ReactionRecord> reactions,
                boolean remoteDelete,
                long notifiedTimestamp,
                boolean viewed,
                long receiptTimestamp,
                @Nullable MessageId originalMessageId,
                int revisionNumber,
                @Nullable MessageExtras messageExtras)
  {
    super(body, fromRecipient, toRecipient, dateSent, dateReceived,
          threadId, deliveryStatus, hasDeliveryReceipt, type,
          hasReadReceipt, viewed);
    this.id                  = id;
    this.authorDeviceId      = fromDeviceId;
    this.mismatches          = mismatches;
    this.networkFailures     = networkFailures;
    this.subscriptionId      = subscriptionId;
    this.expiresIn           = expiresIn;
    this.expireStarted       = expireStarted;
    this.expireTimerVersion  = expireTimerVersion;
    this.unidentified        = unidentified;
    this.reactions           = reactions;
    this.serverTimestamp     = dateServer;
    this.remoteDelete        = remoteDelete;
    this.notifiedTimestamp   = notifiedTimestamp;
    this.receiptTimestamp    = receiptTimestamp;
    this.originalMessageId   = originalMessageId;
    this.revisionNumber      = revisionNumber;
    this.messageExtras       = messageExtras;
  }

  public abstract boolean isMms();
  public abstract boolean isMmsNotification();

  public boolean isSecure() {
    return MessageTypes.isSecureType(type);
  }

  public boolean isLegacyMessage() {
    return MessageTypes.isLegacyType(type);
  }


  @Override
  @WorkerThread
  public SpannableString getDisplayBody(@NonNull Context context) {
    return getDisplayBody(context, null);
  }

  @WorkerThread
  public SpannableString getDisplayBody(@NonNull Context context, @Nullable Consumer<RecipientId> recipientClickHandler) {
    UpdateDescription updateDisplayBody = getUpdateDisplayBody(context, recipientClickHandler);

    if (updateDisplayBody != null) {
      return new SpannableString(updateDisplayBody.getSpannable());
    }

    return new SpannableString(getBody());
  }

  public @Nullable UpdateDescription getUpdateDisplayBody(@NonNull Context context, @Nullable Consumer<RecipientId> recipientClickHandler) {
    if (isGroupUpdate() && isGroupV2()) {
      if (messageExtras != null) {
        return getGv2ChangeDescription(context, messageExtras, recipientClickHandler);
      } else {
        return getGv2ChangeDescription(context, getBody(), recipientClickHandler);
      }
    } else if (isGroupUpdate() && isOutgoing()) {
      return staticUpdateDescription(context.getString(R.string.MessageRecord_you_updated_group), R.drawable.ic_update_group_16);
    } else if (isGroupUpdate()) {
      return fromRecipient(getFromRecipient(), r -> GroupUtil.getNonV2GroupDescription(context, getBody()).toString(r), R.drawable.ic_update_group_16);
    } else if (isGroupQuit() && isOutgoing()) {
      return staticUpdateDescription(context.getString(R.string.MessageRecord_left_group), R.drawable.ic_update_group_leave_16);
    } else if (isGroupQuit()) {
      return fromRecipient(getFromRecipient(), r -> context.getString(R.string.ConversationItem_group_action_left, r.getDisplayName(context)), R.drawable.ic_update_group_leave_16);
    } else if (isIncomingAudioCall()) {
      return staticUpdateDescription(context.getString(R.string.MessageRecord_call_message_with_date, context.getString(R.string.MessageRecord_incoming_voice_call), getCallDateString(context)), R.drawable.ic_update_audio_call_incoming_16);
    } else if (isIncomingVideoCall()) {
      return staticUpdateDescription(context.getString(R.string.MessageRecord_call_message_with_date, context.getString(R.string.MessageRecord_incoming_video_call), getCallDateString(context)), R.drawable.ic_update_video_call_incoming_16);
    } else if (isOutgoingAudioCall()) {
      return staticUpdateDescription(context.getString(R.string.MessageRecord_call_message_with_date, context.getString(R.string.MessageRecord_outgoing_voice_call), getCallDateString(context)), R.drawable.ic_update_audio_call_outgoing_16);
    } else if (isOutgoingVideoCall()) {
      return staticUpdateDescription(context.getString(R.string.MessageRecord_call_message_with_date, context.getString(R.string.MessageRecord_outgoing_video_call), getCallDateString(context)), R.drawable.ic_update_video_call_outgoing_16);
    } else if (isMissedAudioCall()) {
      return staticUpdateDescription(context.getString(R.string.MessageRecord_call_message_with_date, context.getString(R.string.MessageRecord_missed_voice_call), getCallDateString(context)), R.drawable.ic_update_audio_call_missed_16, ContextCompat.getColor(context, R.color.core_red_shade), ContextCompat.getColor(context, R.color.core_red));
    } else if (isMissedVideoCall()) {
      return staticUpdateDescription(context.getString(R.string.MessageRecord_call_message_with_date, context.getString(R.string.MessageRecord_missed_video_call), getCallDateString(context)), R.drawable.ic_update_video_call_missed_16, ContextCompat.getColor(context, R.color.core_red_shade), ContextCompat.getColor(context, R.color.core_red));
    } else if (isGroupCall()) {
      return getGroupCallUpdateDescription(context, getBody(), true);
    } else if (isJoined()) {
      return staticUpdateDescription(context.getString(R.string.MessageRecord_s_joined_signal, getFromRecipient().getDisplayName(context)), R.drawable.ic_update_group_add_16);
    } else if (isExpirationTimerUpdate()) {
      int seconds = (int)(getExpiresIn() / 1000);
      if (seconds <= 0) {
        return isOutgoing() ? staticUpdateDescription(context.getString(R.string.MessageRecord_you_disabled_disappearing_messages), R.drawable.ic_update_timer_disabled_16)
                            : fromRecipient(getFromRecipient(), r -> context.getString(R.string.MessageRecord_s_disabled_disappearing_messages, r.getDisplayName(context)), R.drawable.ic_update_timer_disabled_16);
      }
      String time = ExpirationUtil.getExpirationDisplayValue(context, seconds);
      return isOutgoing() ? staticUpdateDescription(context.getString(R.string.MessageRecord_you_set_disappearing_message_time_to_s, time), R.drawable.ic_update_timer_16)
                          : fromRecipient(getFromRecipient(), r -> context.getString(R.string.MessageRecord_s_set_disappearing_message_time_to_s, r.getDisplayName(context), time), R.drawable.ic_update_timer_16);
    } else if (isIdentityUpdate()) {
      return fromRecipient(getFromRecipient(), r -> context.getString(R.string.MessageRecord_your_safety_number_with_s_has_changed, r.getDisplayName(context)), R.drawable.ic_update_safety_number_16);
    } else if (isIdentityVerified()) {
      if (isOutgoing()) return fromRecipient(getToRecipient(), r -> context.getString(R.string.MessageRecord_you_marked_your_safety_number_with_s_verified, r.getDisplayName(context)), R.drawable.ic_safety_number_16);
      else              return fromRecipient(getFromRecipient(), r -> context.getString(R.string.MessageRecord_you_marked_your_safety_number_with_s_verified_from_another_device, r.getDisplayName(context)), R.drawable.ic_safety_number_16);
    } else if (isIdentityDefault()) {
      if (isOutgoing()) return fromRecipient(getToRecipient(), r -> context.getString(R.string.MessageRecord_you_marked_your_safety_number_with_s_unverified, r.getDisplayName(context)), R.drawable.ic_update_info_16);
      else              return fromRecipient(getFromRecipient(), r -> context.getString(R.string.MessageRecord_you_marked_your_safety_number_with_s_unverified_from_another_device, r.getDisplayName(context)), R.drawable.ic_update_info_16);
    } else if (isProfileChange()) {
      return getProfileChangeDescription(context);
    } else if (isChangeNumber()) {
      return fromRecipient(getFromRecipient(), r -> context.getString(R.string.MessageRecord_s_changed_their_phone_number, r.getDisplayName(context)), R.drawable.ic_phone_16);
    } else if (isReleaseChannelDonationRequest()) {
      return staticUpdateDescription(context.getString(R.string.MessageRecord_like_this_new_feature_help_support_signal_with_a_one_time_donation), 0);
    } else if (isEndSession()) {
      if (isOutgoing()) return staticUpdateDescription(context.getString(R.string.SmsMessageRecord_secure_session_reset), R.drawable.ic_update_info_16);
      else              return fromRecipient(getFromRecipient(), r-> context.getString(R.string.SmsMessageRecord_secure_session_reset_s, r.getDisplayName(context)), R.drawable.ic_update_info_16);
    } else if (isGroupV1MigrationEvent()) {
      return getGroupMigrationEventDescription(context);
    } else if (isChatSessionRefresh()) {
      return staticUpdateDescription(context.getString(R.string.MessageRecord_chat_session_refreshed), R.drawable.ic_refresh_16);
    } else if (isBadDecryptType()) {
      return fromRecipient(getFromRecipient(), r -> context.getString(R.string.MessageRecord_a_message_from_s_couldnt_be_delivered, r.getDisplayName(context)), R.drawable.ic_error_outline_14);
    } else if (isThreadMergeEventType()) {
      try {
        ThreadMergeEvent event = ThreadMergeEvent.ADAPTER.decode(Base64.decodeOrThrow(getBody()));

        if (event.previousE164.isEmpty()) {
          return fromRecipient(getFromRecipient(), r -> context.getString(R.string.MessageRecord_your_message_history_with_s_and_another_chat_has_been_merged, r.getDisplayName(context)), R.drawable.ic_thread_merge_16);
        } else {
          return fromRecipient(getFromRecipient(), r -> context.getString(R.string.MessageRecord_your_message_history_with_s_and_their_number_s_has_been_merged, r.getDisplayName(context), SignalE164Util.prettyPrint(event.previousE164)), R.drawable.ic_thread_merge_16);
        }
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    } else if (isSessionSwitchoverEventType()) {
      try {
        SessionSwitchoverEvent event = SessionSwitchoverEvent.ADAPTER.decode(Base64.decodeOrThrow(getBody()));

        if (event.e164.isEmpty()) {
          return fromRecipient(getFromRecipient(), r -> context.getString(R.string.MessageRecord_your_safety_number_with_s_has_changed, r.getDisplayName(context)), R.drawable.ic_update_safety_number_16);
        } else {
          return fromRecipient(getFromRecipient(), r -> context.getString(R.string.MessageRecord_s_belongs_to_s, SignalE164Util.prettyPrint(event.e164), r.getDisplayName(context)), R.drawable.ic_update_info_16);
        }
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    } else if (isSmsExportType()) {
      int messageResource = R.string.MessageRecord__you_can_no_longer_send_sms_messages_in_signal;
      return fromRecipient(getFromRecipient(), r -> context.getString(messageResource, r.getDisplayName(context)), R.drawable.ic_update_info_16);
    } else if (isPaymentsRequestToActivate()) {
      return isOutgoing() ? fromRecipient(getToRecipient(), r -> context.getString(R.string.MessageRecord_you_sent_request, r.getShortDisplayName(context)), R.drawable.ic_card_activate_payments)
                          : fromRecipient(getFromRecipient(), r -> context.getString(R.string.MessageRecord_wants_you_to_activate_payments, r.getShortDisplayName(context)), R.drawable.ic_card_activate_payments);
   } else if (isPaymentsActivated()) {
      return isOutgoing() ? staticUpdateDescription(context.getString(R.string.MessageRecord_you_activated_payments), R.drawable.ic_card_activate_payments)
                          : fromRecipient(getFromRecipient(), r -> context.getString(R.string.MessageRecord_can_accept_payments, r.getShortDisplayName(context)), R.drawable.ic_card_activate_payments);
    } else if (isReportedSpam()) {
      return staticUpdateDescription(context.getString(R.string.MessageRecord_reported_as_spam), R.drawable.symbol_spam_16);
    } else if (isMessageRequestAccepted()) {
      return staticUpdateDescription(context.getString(R.string.MessageRecord_you_accepted_the_message_request), R.drawable.symbol_thread_16);
    } else if (isBlocked()) {
      return staticUpdateDescription(context.getString(isGroupV2() ? R.string.MessageRecord_you_blocked_this_group : R.string.MessageRecord_you_blocked_this_person), R.drawable.symbol_block_16);
    } else if (isUnblocked()) {
      return staticUpdateDescription(context.getString(isGroupV2() ? R.string.MessageRecord_you_unblocked_this_group : R.string.MessageRecord_you_unblocked_this_person) , R.drawable.symbol_thread_16);
    }

    return null;
  }

  public boolean isDisplayBodyEmpty(@NonNull Context context) {
    return getUpdateDisplayBody(context, null) == null && getBody().isEmpty();
  }

  public boolean isSelfCreatedGroup() {
    DecryptedGroupV2Context decryptedGroupV2Context = getDecryptedGroupV2Context();

    if (decryptedGroupV2Context == null) {
      return false;
    }
    DecryptedGroupChange change = decryptedGroupV2Context.change;

    return selfCreatedGroup(change);
  }

  public @Nullable MessageExtras getMessageExtras() {
    return messageExtras;
  }

  @VisibleForTesting
  @Nullable DecryptedGroupV2Context getDecryptedGroupV2Context() {
    if (!isGroupUpdate() || !isGroupV2()) {
      return null;
    }

    if (messageExtras != null && messageExtras.gv2UpdateDescription != null) {
      return messageExtras.gv2UpdateDescription.gv2ChangeDescription;
    }

    if (TextUtils.isEmpty(getBody())) {
      return null;
    }

    DecryptedGroupV2Context decryptedGroupV2Context;
    try {
      byte[] decoded = Base64.decode(getBody());
      decryptedGroupV2Context = DecryptedGroupV2Context.ADAPTER.decode(decoded);

    } catch (IOException e) {
      Log.w(TAG, "GV2 Message update detail could not be read", e);
      decryptedGroupV2Context = null;
    }
    return decryptedGroupV2Context;
  }

  private static boolean selfCreatedGroup(@Nullable DecryptedGroupChange change) {
    return change != null &&
           change.revision == 0 &&
           change.editorServiceIdBytes.equals(SignalStore.account().requireAci().toByteString());
  }

  public static @NonNull UpdateDescription getGv2ChangeDescription(@NonNull Context context, @NonNull String body, @Nullable Consumer<RecipientId> recipientClickHandler) {
    try {
      byte[]                         decoded                 = Base64.decode(body);
      DecryptedGroupV2Context        decryptedGroupV2Context = DecryptedGroupV2Context.ADAPTER.decode(decoded);
      return getGv2ChangeDescription(context, decryptedGroupV2Context, recipientClickHandler);
    } catch (IOException | IllegalArgumentException | IllegalStateException e) {
      Log.w(TAG, "GV2 Message update detail could not be read", e);
      return staticUpdateDescription(context.getString(R.string.MessageRecord_group_updated), R.drawable.ic_update_group_16);
    }
  }

  public static @NonNull UpdateDescription getGv2ChangeDescription(@NonNull Context context, @NonNull MessageExtras messageExtras, @Nullable Consumer<RecipientId> recipientClickHandler) {
    if (messageExtras.gv2UpdateDescription != null) {
      if (messageExtras.gv2UpdateDescription.groupChangeUpdate != null) {
        GroupsV2UpdateMessageProducer updateMessageProducer = new GroupsV2UpdateMessageProducer(context, SignalStore.account().getServiceIds(), recipientClickHandler);

        return concatWithNewLinesCapped(context, updateMessageProducer.describeChanges(messageExtras.gv2UpdateDescription.groupChangeUpdate.updates));
      } else if (messageExtras.gv2UpdateDescription.gv2ChangeDescription != null) {
        return getGv2ChangeDescription(context, messageExtras.gv2UpdateDescription.gv2ChangeDescription, recipientClickHandler);
      } else {
        Log.w(TAG, "GV2 Update Description missing group change update!");
      }
    }
    return staticUpdateDescription(context.getString(R.string.MessageRecord_group_updated), R.drawable.ic_update_group_16);
  }

  public static @NonNull UpdateDescription getGv2ChangeDescription(@NonNull Context context, @NonNull DecryptedGroupV2Context decryptedGroupV2Context, @Nullable Consumer<RecipientId> recipientClickHandler) {
    try {
      GroupsV2UpdateMessageProducer  updateMessageProducer   = new GroupsV2UpdateMessageProducer(context, SignalStore.account().getServiceIds(), recipientClickHandler);

      if (decryptedGroupV2Context.change != null && ((decryptedGroupV2Context.groupState != null && decryptedGroupV2Context.groupState.revision != 0) || decryptedGroupV2Context.previousGroupState != null)) {
        return concatWithNewLinesCapped(context, updateMessageProducer.describeChanges(decryptedGroupV2Context.previousGroupState, decryptedGroupV2Context.change));
      } else {
        List<UpdateDescription> newGroupDescriptions = new ArrayList<>();
        newGroupDescriptions.add(updateMessageProducer.describeNewGroup(decryptedGroupV2Context.groupState, decryptedGroupV2Context.change));

        if (decryptedGroupV2Context.change != null && decryptedGroupV2Context.change.newTimer != null) {
          updateMessageProducer.describeNewTimer(decryptedGroupV2Context.change, newGroupDescriptions);
        }

        if (selfCreatedGroup(decryptedGroupV2Context.change)) {
          newGroupDescriptions.add(staticUpdateDescription(context.getString(R.string.MessageRecord_invite_friends_to_this_group), 0));
        }
        return concatWithNewLinesCapped(context, newGroupDescriptions);
      }
    } catch (IllegalArgumentException | IllegalStateException e) {
      Log.w(TAG, "GV2 Message update detail could not be read", e);
      return staticUpdateDescription(context.getString(R.string.MessageRecord_group_updated), R.drawable.ic_update_group_16);
    }
  }

  private static @NonNull UpdateDescription concatWithNewLinesCapped(@NonNull Context context, @NonNull List<UpdateDescription> updateDescriptions) {
    if (updateDescriptions.size() > 100) {
      // Arbitrary update description collapse cap, otherwise the long string can cause issues
      return staticUpdateDescription(context.getString(R.string.MessageRecord_group_updated), R.drawable.ic_update_group_16);
    }
    return UpdateDescription.concatWithNewLines(updateDescriptions);
  }

  public @Nullable InviteAddState getGv2AddInviteState() {
    DecryptedGroupV2Context decryptedGroupV2Context = getDecryptedGroupV2Context();

    if (decryptedGroupV2Context == null) {
      return null;
    }

    DecryptedGroup groupState = decryptedGroupV2Context.groupState;
    boolean        invited    = groupState != null && DecryptedGroupUtil.findPendingByServiceId(groupState.pendingMembers, SignalStore.account().requireAci()).isPresent();

    if (decryptedGroupV2Context.change != null) {
      ServiceId changeEditor = ServiceId.parseOrNull(decryptedGroupV2Context.change.editorServiceIdBytes);

      if (changeEditor != null) {
        if (changeEditor instanceof ACI) {
          return new InviteAddState(invited, (ACI) changeEditor);
        } else {
          Log.w(TAG, "Adder was a PNI! This should not happen.");
          return null;
        }
      }
    }

    Log.w(TAG, "GV2 Message editor could not be determined");
    return null;
  }

  protected @NonNull String getCallDateString(@NonNull Context context) {
    return DateUtils.getDatelessRelativeTimeSpanString(context, Locale.getDefault(), getDateSent());
  }

  protected static @NonNull UpdateDescription fromRecipient(@NonNull Recipient recipient,
                                                            @NonNull Function<Recipient, String> stringGenerator,
                                                            @DrawableRes int iconResource)
  {
    return UpdateDescription.mentioning(Collections.singletonList(recipient.getAci().orElse(ACI.UNKNOWN)),
                                        () -> new SpannableString(stringGenerator.apply(recipient.resolve())),
                                        iconResource);
  }

  protected static @NonNull UpdateDescription staticUpdateDescription(@NonNull String string,
                                                                      @DrawableRes int iconResource)
  {
    return UpdateDescription.staticDescription(string, iconResource);
  }

  protected static @NonNull UpdateDescription staticUpdateDescription(@NonNull String string,
                                                                      @DrawableRes int iconResource,
                                                                      @ColorInt int lightTint,
                                                                      @ColorInt int darkTint)
  {
    return UpdateDescription.staticDescription(string, iconResource, lightTint, darkTint);
  }

  private @NonNull UpdateDescription getProfileChangeDescription(@NonNull Context context) {
    ProfileChangeDetails profileChangeDetails = null;

    MessageExtras extras = getMessageExtras();
    if (extras != null) {
      profileChangeDetails = extras.profileChangeDetails;
    } else {
      try {
        byte[] decoded = Base64.decode(getBody());
        profileChangeDetails = ProfileChangeDetails.ADAPTER.decode(decoded);
      } catch (IOException e) {
        Log.w(TAG, "Profile name change details could not be read", e);
      }
    }

    if (profileChangeDetails != null) {
      if (profileChangeDetails.profileNameChange != null) {
        String displayName  = getFromRecipient().getDisplayName(context);
        String newName      = BidiUtil.isolateBidi(ProfileName.fromSerialized(profileChangeDetails.profileNameChange.newValue).toString());
        String previousName = BidiUtil.isolateBidi(ProfileName.fromSerialized(profileChangeDetails.profileNameChange.previous).toString());

        String updateMessage;
        if (getFromRecipient().isSystemContact()) {
          updateMessage = context.getString(R.string.MessageRecord_changed_their_profile_name_from_to, displayName, previousName, newName);
        } else {
          updateMessage = context.getString(R.string.MessageRecord_changed_their_profile_name_to, previousName, newName);
        }

        return staticUpdateDescription(updateMessage, R.drawable.ic_update_profile_16);
      } else if (profileChangeDetails.deprecatedLearnedProfileName != null) {
        return staticUpdateDescription(context.getString(R.string.MessageRecord_started_this_chat, profileChangeDetails.deprecatedLearnedProfileName.previous), R.drawable.symbol_thread_16);
      } else if (profileChangeDetails.learnedProfileName != null) {
        String previouslyKnownAs;
        if (!Util.isEmpty(profileChangeDetails.learnedProfileName.e164)) {
          previouslyKnownAs = SignalE164Util.prettyPrint(profileChangeDetails.learnedProfileName.e164);
        } else {
          previouslyKnownAs = profileChangeDetails.learnedProfileName.username;
        }

        if (!Util.isEmpty(previouslyKnownAs)) {
          return staticUpdateDescription(context.getString(R.string.MessageRecord_started_this_chat, previouslyKnownAs), R.drawable.symbol_thread_16);
        }
      }
    }

    return staticUpdateDescription(context.getString(R.string.MessageRecord_changed_their_profile, getFromRecipient().getDisplayName(context)), R.drawable.ic_update_profile_16);
  }

  private UpdateDescription getGroupMigrationEventDescription(@NonNull Context context) {
    if (Util.isEmpty(getBody())) {
      return staticUpdateDescription(context.getString(R.string.MessageRecord_this_group_was_updated_to_a_new_group), R.drawable.ic_update_group_role_16);
    } else {
      GroupMigrationMembershipChange change  = getGroupV1MigrationMembershipChanges();
      List<UpdateDescription>        updates = new ArrayList<>(2);

      if (change.getPending().size() == 1 && change.getPending().get(0).equals(Recipient.self().getId())) {
        updates.add(staticUpdateDescription(context.getString(R.string.MessageRecord_you_couldnt_be_added_to_the_new_group_and_have_been_invited_to_join), R.drawable.ic_update_group_add_16));
      } else if (change.getPending().size() > 0) {
        int count = change.getPending().size();
        updates.add(staticUpdateDescription(context.getResources().getQuantityString(R.plurals.MessageRecord_members_couldnt_be_added_to_the_new_group_and_have_been_invited, count, count), R.drawable.ic_update_group_add_16));
      }

      if (change.getDropped().size() > 0) {
        int count = change.getDropped().size();
        updates.add(staticUpdateDescription(context.getResources().getQuantityString(R.plurals.MessageRecord_members_couldnt_be_added_to_the_new_group_and_have_been_removed, count, count), R.drawable.ic_update_group_remove_16));
      }

      return concatWithNewLinesCapped(context, updates);
    }
  }

  public static @NonNull UpdateDescription getGroupCallUpdateDescription(@NonNull Context context, @NonNull String body, boolean withTime) {
    GroupCallUpdateDetails groupCallUpdateDetails = GroupCallUpdateDetailsUtil.parse(body);

    List<ServiceId> joinedMembers = Stream.of(groupCallUpdateDetails.inCallUuids)
                                          .map(UuidUtil::parseOrNull)
                                          .withoutNulls()
                                          .<ServiceId>map(ACI::from)
                                          .toList();

    UpdateDescription.SpannableFactory stringFactory = new GroupCallUpdateMessageFactory(context, joinedMembers, withTime, groupCallUpdateDetails);

    return UpdateDescription.mentioning(joinedMembers, stringFactory, R.drawable.ic_video_16);
  }

  public boolean isGroupV2DescriptionUpdate() {
    DecryptedGroupV2Context decryptedGroupV2Context = getDecryptedGroupV2Context();
    if (decryptedGroupV2Context != null) {
      return decryptedGroupV2Context.change != null && decryptedGroupV2Context.change.newDescription != null;
    }
    return false;
  }

  public @NonNull String getGroupV2DescriptionUpdate() {
    DecryptedGroupV2Context decryptedGroupV2Context = getDecryptedGroupV2Context();
    if (decryptedGroupV2Context != null && decryptedGroupV2Context.change != null) {
      return decryptedGroupV2Context.change.newDescription != null ? decryptedGroupV2Context.change.newDescription.value_ : "";
    }
    return "";
  }

  public boolean isGroupV2JoinRequest(@Nullable ServiceId serviceId) {
    if (serviceId == null) {
      return false;
    }

    DecryptedGroupV2Context decryptedGroupV2Context = getDecryptedGroupV2Context();

    if (decryptedGroupV2Context != null && decryptedGroupV2Context.change != null) {
      DecryptedGroupChange change              = decryptedGroupV2Context.change;
      ByteString           serviceIdByteString = serviceId.toByteString();

      return change.editorServiceIdBytes.equals(serviceIdByteString) && change.newRequestingMembers.stream().anyMatch(r -> r.aciBytes.equals(serviceIdByteString));
    }
    return false;
  }

  public boolean isCollapsedGroupV2JoinUpdate() {
    return isCollapsedGroupV2JoinUpdate(null);
  }

  public boolean isCollapsedGroupV2JoinUpdate(@Nullable ServiceId serviceId) {
    DecryptedGroupV2Context decryptedGroupV2Context = getDecryptedGroupV2Context();
    if (decryptedGroupV2Context != null && decryptedGroupV2Context.change != null) {
      DecryptedGroupChange change = decryptedGroupV2Context.change;
      return change.newRequestingMembers.size() > 0 &&
             change.deleteRequestingMembers.size() > 0 &&
             (serviceId == null || change.editorServiceIdBytes.equals(serviceId.toByteString()));
    }
    return false;
  }

  public static @NonNull String createNewContextWithAppendedDeleteJoinRequest(@NonNull MessageRecord messageRecord, int revision, @NonNull ByteString id) {
    DecryptedGroupV2Context decryptedGroupV2Context = messageRecord.getDecryptedGroupV2Context();

    if (decryptedGroupV2Context != null && decryptedGroupV2Context.change != null) {
      DecryptedGroupChange change = decryptedGroupV2Context.change;

      List<ByteString> deleteRequestingMembers = new ArrayList<>(change.deleteRequestingMembers);
      deleteRequestingMembers.add(id);

      return Base64.encodeWithPadding(decryptedGroupV2Context.newBuilder()
                                                       .change(change.newBuilder()
                                                                     .revision(revision)
                                                                     .deleteRequestingMembers(deleteRequestingMembers)
                                                                     .build())
                                                       .build()
                                                       .encode());
    }

    throw new AssertionError("Attempting to modify a message with no change");
  }

  public long getId() {
    return id;
  }

  public boolean isPush() {
    return MessageTypes.isPushType(type) && !MessageTypes.isForcedSms(type);
  }

  public long getTimestamp() {
    if ((isPush() || isCallLog()) && getDateSent() < getDateReceived()) {
      return getDateSent();
    }
    if (isEditMessage()) {
      return getDateSent();
    }
    return getDateReceived();
  }

  public long getServerTimestamp() {
    return serverTimestamp;
  }

  public boolean isForcedSms() {
    return MessageTypes.isForcedSms(type);
  }

  public boolean isIdentityVerified() {
    return MessageTypes.isIdentityVerified(type);
  }

  public boolean isIdentityDefault() {
    return MessageTypes.isIdentityDefault(type);
  }

  public boolean isIdentityMismatchFailure() {
    return mismatches != null && !mismatches.isEmpty();
  }

  public boolean isBundleKeyExchange() {
    return MessageTypes.isBundleKeyExchange(type);
  }

  public boolean isRateLimited() {
    return MessageTypes.isRateLimited(type);
  }

  public boolean isIdentityUpdate() {
    return MessageTypes.isIdentityUpdate(type);
  }

  public boolean isBadDecryptType() {
    return MessageTypes.isBadDecryptType(type);
  }

  public boolean isThreadMergeEventType() {
    return MessageTypes.isThreadMergeType(type);
  }

  public boolean isSessionSwitchoverEventType() {
    return MessageTypes.isSessionSwitchoverType(type);
  }

  public boolean isSmsExportType() {
    return MessageTypes.isSmsExport(type);
  }

  public boolean isGroupV1MigrationEvent() {
    return MessageTypes.isGroupV1MigrationEvent(type);
  }

  public @NonNull GroupMigrationMembershipChange getGroupV1MigrationMembershipChanges() {
    if (isGroupV1MigrationEvent()) {
      return GroupMigrationMembershipChange.deserialize(getBody());
    } else {
      return GroupMigrationMembershipChange.empty();
    }
  }

  public boolean isUpdate() {
    return isGroupAction() || isJoined() || isExpirationTimerUpdate() || isCallLog() ||
           isEndSession() || isIdentityUpdate() || isIdentityVerified() || isIdentityDefault() ||
           isProfileChange() || isGroupV1MigrationEvent() || isChatSessionRefresh() || isBadDecryptType() ||
           isChangeNumber() || isReleaseChannelDonationRequest() || isThreadMergeEventType() || isSmsExportType() || isSessionSwitchoverEventType() ||
           isPaymentsRequestToActivate() || isPaymentsActivated() || isReportedSpam() || isMessageRequestAccepted() ||
           isBlocked() || isUnblocked();
  }

  public boolean isMediaPending() {
    return false;
  }

  public int getFromDeviceId() {
    return authorDeviceId;
  }

  public long getType() {
    return type;
  }

  public Set<IdentityKeyMismatch> getIdentityKeyMismatches() {
    return mismatches;
  }

  public Set<NetworkFailure> getNetworkFailures() {
    return networkFailures;
  }

  public boolean hasNetworkFailures() {
    return networkFailures != null && !networkFailures.isEmpty();
  }

  public boolean hasFailedWithNetworkFailures() {
    return isFailed() && ((getToRecipient().isPushGroup() && hasNetworkFailures()) || !isIdentityMismatchFailure());
  }

  public boolean isChatSessionRefresh() {
    return MessageTypes.isChatSessionRefresh(type);
  }

  public boolean isInMemoryMessageRecord() {
    return false;
  }

  protected static SpannableString emphasisAdded(String sequence) {
    SpannableString spannable = new SpannableString(sequence);
    spannable.setSpan(new RelativeSizeSpan(0.9f), 0, sequence.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    spannable.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC), 0, sequence.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

    return spannable;
  }

  public boolean equals(Object other) {
    return other instanceof MessageRecord             &&
           ((MessageRecord) other).getId() == getId() &&
           ((MessageRecord) other).isMms() == isMms();
  }

  public int hashCode() {
    return Objects.hash(id, isMms());
  }

  public int getSubscriptionId() {
    return subscriptionId;
  }

  public long getExpiresIn() {
    return expiresIn;
  }

  public long getExpireStarted() {
    return expireStarted;
  }

  public int getExpireTimerVersion() {
    return expireTimerVersion;
  }

  public boolean isUnidentified() {
    return unidentified;
  }

  public boolean isViewOnce() {
    return false;
  }

  public boolean isRemoteDelete() {
    return remoteDelete;
  }

  public @NonNull List<ReactionRecord> getReactions() {
    return reactions;
  }

  public boolean hasSelfMention() {
    return false;
  }

  public long getNotifiedTimestamp() {
    return notifiedTimestamp;
  }

  @VisibleForTesting
  public long getIncomingStoryViewedAtTimestamp() {
    if (isOutgoing()) {
      return -1L;
    } else {
      return receiptTimestamp;
    }
  }

  public long getReceiptTimestamp() {
    if (!isOutgoing()) {
      return getDateSent();
    } else {
      return receiptTimestamp;
    }
  }

  public boolean isJumbomoji(Context context) {
    if (isJumboji == null) {
      if (getBody().length() <= EmojiSource.getLatest().getMaxEmojiLength() * JumboEmoji.MAX_JUMBOJI_COUNT) {
        EmojiParser.CandidateList candidates = EmojiProvider.getCandidates(getDisplayBody(context));
        isJumboji = candidates != null && candidates.allEmojis && candidates.size() <= JumboEmoji.MAX_JUMBOJI_COUNT && (candidates.hasJumboForAll() || JumboEmoji.canDownloadJumbo(context));
      } else {
        isJumboji = false;
      }
    }
    return isJumboji;
  }

  public @Nullable BodyRangeList getMessageRanges() {
    return null;
  }

  public @NonNull BodyRangeList requireMessageRanges() {
    throw new NullPointerException();
  }

  public boolean isEditMessage() {
    return originalMessageId != null;
  }

  public boolean isLatestRevision() {
    if (this instanceof MmsMessageRecord) {
      return ((MmsMessageRecord) this).getLatestRevisionId() == null;
    }
    return true;
  }

  public @Nullable MessageId getOriginalMessageId() {
    return originalMessageId;
  }

  public int getRevisionNumber() {
    return revisionNumber;
  }

  /**
   * A message that can be correctly identified and delete sync'd across devices.
   */
  public boolean canDeleteSync() {
    return false;
  }

  public static final class InviteAddState {

    private final boolean invited;
    private final ACI     addedOrInvitedBy;

    public InviteAddState(boolean invited, @NonNull ACI addedOrInvitedBy) {
      this.invited          = invited;
      this.addedOrInvitedBy = addedOrInvitedBy;
    }

    public @NonNull ACI getAddedOrInvitedBy() {
      return addedOrInvitedBy;
    }

    public boolean isInvited() {
      return invited;
    }
  }
}
