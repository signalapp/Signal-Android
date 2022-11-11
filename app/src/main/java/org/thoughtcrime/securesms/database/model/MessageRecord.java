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
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.signal.core.util.StringUtil;
import org.signal.core.util.logging.Log;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.emoji.EmojiProvider;
import org.thoughtcrime.securesms.components.emoji.parsing.EmojiParser;
import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList;
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context;
import org.thoughtcrime.securesms.database.model.databaseprotos.GroupCallUpdateDetails;
import org.thoughtcrime.securesms.database.model.databaseprotos.ProfileChangeDetails;
import org.thoughtcrime.securesms.database.model.databaseprotos.ThreadMergeEvent;
import org.thoughtcrime.securesms.emoji.EmojiSource;
import org.thoughtcrime.securesms.emoji.JumboEmoji;
import org.thoughtcrime.securesms.groups.GroupMigrationMembershipChange;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.ExpirationUtil;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

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

  private final Recipient                individualRecipient;
  private final int                      recipientDeviceId;
  private final long                     id;
  private final Set<IdentityKeyMismatch> mismatches;
  private final Set<NetworkFailure>      networkFailures;
  private final int                      subscriptionId;
  private final long                     expiresIn;
  private final long                     expireStarted;
  private final boolean                  unidentified;
  private final List<ReactionRecord>     reactions;
  private final long                     serverTimestamp;
  private final boolean                  remoteDelete;
  private final long                     notifiedTimestamp;
  private final long                     receiptTimestamp;

  protected Boolean isJumboji = null;

  MessageRecord(long id, String body, Recipient conversationRecipient,
                Recipient individualRecipient, int recipientDeviceId,
                long dateSent, long dateReceived, long dateServer, long threadId,
                int deliveryStatus, int deliveryReceiptCount, long type,
                Set<IdentityKeyMismatch> mismatches,
                Set<NetworkFailure> networkFailures,
                int subscriptionId, long expiresIn, long expireStarted,
                int readReceiptCount, boolean unidentified,
                @NonNull List<ReactionRecord> reactions, boolean remoteDelete, long notifiedTimestamp,
                int viewedReceiptCount, long receiptTimestamp)
  {
    super(body, conversationRecipient, dateSent, dateReceived,
          threadId, deliveryStatus, deliveryReceiptCount, type,
          readReceiptCount, viewedReceiptCount);
    this.id                  = id;
    this.individualRecipient = individualRecipient;
    this.recipientDeviceId   = recipientDeviceId;
    this.mismatches          = mismatches;
    this.networkFailures     = networkFailures;
    this.subscriptionId      = subscriptionId;
    this.expiresIn           = expiresIn;
    this.expireStarted       = expireStarted;
    this.unidentified        = unidentified;
    this.reactions           = reactions;
    this.serverTimestamp     = dateServer;
    this.remoteDelete        = remoteDelete;
    this.notifiedTimestamp   = notifiedTimestamp;
    this.receiptTimestamp    = receiptTimestamp;
  }

  public abstract boolean isMms();
  public abstract boolean isMmsNotification();

  public boolean isSecure() {
    return MmsSmsColumns.Types.isSecureType(type);
  }

  public boolean isLegacyMessage() {
    return MmsSmsColumns.Types.isLegacyType(type);
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
      return getGv2ChangeDescription(context, getBody(), recipientClickHandler);
    } else if (isGroupUpdate() && isOutgoing()) {
      return staticUpdateDescription(context.getString(R.string.MessageRecord_you_updated_group), R.drawable.ic_update_group_16);
    } else if (isGroupUpdate()) {
      return fromRecipient(getIndividualRecipient(), r -> GroupUtil.getNonV2GroupDescription(context, getBody()).toString(r), R.drawable.ic_update_group_16);
    } else if (isGroupQuit() && isOutgoing()) {
      return staticUpdateDescription(context.getString(R.string.MessageRecord_left_group), R.drawable.ic_update_group_leave_16);
    } else if (isGroupQuit()) {
      return fromRecipient(getIndividualRecipient(), r -> context.getString(R.string.ConversationItem_group_action_left, r.getDisplayName(context)), R.drawable.ic_update_group_leave_16);
    } else if (isIncomingAudioCall()) {
      return fromRecipient(getIndividualRecipient(), r -> context.getString(R.string.MessageRecord_s_called_you_date, r.getDisplayName(context), getCallDateString(context)), R.drawable.ic_update_audio_call_incoming_16);
    } else if (isIncomingVideoCall()) {
      return fromRecipient(getIndividualRecipient(), r -> context.getString(R.string.MessageRecord_s_called_you_date, r.getDisplayName(context), getCallDateString(context)), R.drawable.ic_update_video_call_incoming_16);
    } else if (isOutgoingAudioCall()) {
      return staticUpdateDescription(context.getString(R.string.MessageRecord_you_called_date, getCallDateString(context)), R.drawable.ic_update_audio_call_outgoing_16);
    } else if (isOutgoingVideoCall()) {
      return staticUpdateDescription(context.getString(R.string.MessageRecord_you_called_date, getCallDateString(context)), R.drawable.ic_update_video_call_outgoing_16);
    } else if (isMissedAudioCall()) {
      return staticUpdateDescription(context.getString(R.string.MessageRecord_missed_audio_call_date, getCallDateString(context)), R.drawable.ic_update_audio_call_missed_16, ContextCompat.getColor(context, R.color.core_red_shade), ContextCompat.getColor(context, R.color.core_red));
    } else if (isMissedVideoCall()) {
      return staticUpdateDescription(context.getString(R.string.MessageRecord_missed_video_call_date, getCallDateString(context)), R.drawable.ic_update_video_call_missed_16, ContextCompat.getColor(context, R.color.core_red_shade), ContextCompat.getColor(context, R.color.core_red));
    } else if (isGroupCall()) {
      return getGroupCallUpdateDescription(context, getBody(), true);
    } else if (isJoined()) {
      return staticUpdateDescription(context.getString(R.string.MessageRecord_s_joined_signal, getIndividualRecipient().getDisplayName(context)), R.drawable.ic_update_group_add_16);
    } else if (isExpirationTimerUpdate()) {
      int seconds = (int)(getExpiresIn() / 1000);
      if (seconds <= 0) {
        return isOutgoing() ? staticUpdateDescription(context.getString(R.string.MessageRecord_you_disabled_disappearing_messages), R.drawable.ic_update_timer_disabled_16)
                            : fromRecipient(getIndividualRecipient(), r -> context.getString(R.string.MessageRecord_s_disabled_disappearing_messages, r.getDisplayName(context)), R.drawable.ic_update_timer_disabled_16);
      }
      String time = ExpirationUtil.getExpirationDisplayValue(context, seconds);
      return isOutgoing() ? staticUpdateDescription(context.getString(R.string.MessageRecord_you_set_disappearing_message_time_to_s, time), R.drawable.ic_update_timer_16)
                          : fromRecipient(getIndividualRecipient(), r -> context.getString(R.string.MessageRecord_s_set_disappearing_message_time_to_s, r.getDisplayName(context), time), R.drawable.ic_update_timer_16);
    } else if (isIdentityUpdate()) {
      return fromRecipient(getIndividualRecipient(), r -> context.getString(R.string.MessageRecord_your_safety_number_with_s_has_changed, r.getDisplayName(context)), R.drawable.ic_update_safety_number_16);
    } else if (isIdentityVerified()) {
      if (isOutgoing()) return fromRecipient(getIndividualRecipient(), r -> context.getString(R.string.MessageRecord_you_marked_your_safety_number_with_s_verified, r.getDisplayName(context)), R.drawable.ic_update_verified_16);
      else              return fromRecipient(getIndividualRecipient(), r -> context.getString(R.string.MessageRecord_you_marked_your_safety_number_with_s_verified_from_another_device, r.getDisplayName(context)), R.drawable.ic_update_verified_16);
    } else if (isIdentityDefault()) {
      if (isOutgoing()) return fromRecipient(getIndividualRecipient(), r -> context.getString(R.string.MessageRecord_you_marked_your_safety_number_with_s_unverified, r.getDisplayName(context)), R.drawable.ic_update_info_16);
      else              return fromRecipient(getIndividualRecipient(), r -> context.getString(R.string.MessageRecord_you_marked_your_safety_number_with_s_unverified_from_another_device, r.getDisplayName(context)), R.drawable.ic_update_info_16);
    } else if (isProfileChange()) {
      return staticUpdateDescription(getProfileChangeDescription(context), R.drawable.ic_update_profile_16);
    } else if (isChangeNumber()) {
      return fromRecipient(getIndividualRecipient(), r -> context.getString(R.string.MessageRecord_s_changed_their_phone_number, r.getDisplayName(context)), R.drawable.ic_phone_16);
    } else if (isBoostRequest()) {
      return staticUpdateDescription(context.getString(R.string.MessageRecord_like_this_new_feature_help_support_signal_with_a_one_time_donation), 0);
    } else if (isEndSession()) {
      if (isOutgoing()) return staticUpdateDescription(context.getString(R.string.SmsMessageRecord_secure_session_reset), R.drawable.ic_update_info_16);
      else              return fromRecipient(getIndividualRecipient(), r-> context.getString(R.string.SmsMessageRecord_secure_session_reset_s, r.getDisplayName(context)), R.drawable.ic_update_info_16);
    } else if (isGroupV1MigrationEvent()) {
      return getGroupMigrationEventDescription(context);
    } else if (isChatSessionRefresh()) {
      return staticUpdateDescription(context.getString(R.string.MessageRecord_chat_session_refreshed), R.drawable.ic_refresh_16);
    } else if (isBadDecryptType()) {
      return fromRecipient(getIndividualRecipient(), r -> context.getString(R.string.MessageRecord_a_message_from_s_couldnt_be_delivered, r.getDisplayName(context)), R.drawable.ic_error_outline_14);
    } else if (isThreadMergeEventType()) {
      try {
        ThreadMergeEvent event = ThreadMergeEvent.parseFrom(Base64.decodeOrThrow(getBody()));

        if (event.getPreviousE164().isEmpty()) {
          return fromRecipient(getIndividualRecipient(), r -> context.getString(R.string.MessageRecord_your_message_history_with_s_and_another_chat_has_been_merged, r.getDisplayName(context)), R.drawable.ic_thread_merge_16);
        } else {
          return fromRecipient(getIndividualRecipient(), r -> context.getString(R.string.MessageRecord_your_message_history_with_s_and_their_number_s_has_been_merged, r.getDisplayName(context), PhoneNumberFormatter.prettyPrint(event.getPreviousE164())), R.drawable.ic_thread_merge_16);
        }
      } catch (InvalidProtocolBufferException e) {
        throw new AssertionError(e);
      }
    } else if (isSmsExportType()) {
      int messageResource = SignalStore.misc().getSmsExportPhase().isSmsSupported() ? R.string.MessageRecord__you_will_no_longer_be_able_to_send_sms_messages_from_signal_soon
                                                                                    : R.string.MessageRecord__you_can_no_longer_send_sms_messages_in_signal;
      return fromRecipient(getIndividualRecipient(), r -> context.getString(messageResource, r.getDisplayName(context)), R.drawable.ic_update_info_16);
    } else if (isPaymentsRequestToActivate()) {
      return isOutgoing() ? fromRecipient(getIndividualRecipient(), r -> context.getString(R.string.MessageRecord_you_sent_request, r.getShortDisplayName(context)), R.drawable.ic_card_activate_payments)
                          : fromRecipient(getIndividualRecipient(), r -> context.getString(R.string.MessageRecord_wants_you_to_activate_payments, r.getShortDisplayName(context)), R.drawable.ic_card_activate_payments);
   } else if (isPaymentsActivated()) {
      return isOutgoing() ? staticUpdateDescription(context.getString(R.string.MessageRecord_you_activated_payments), R.drawable.ic_card_activate_payments)
                          : fromRecipient(getIndividualRecipient(), r -> context.getString(R.string.MessageRecord_can_accept_payments, r.getShortDisplayName(context)), R.drawable.ic_card_activate_payments);
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
    DecryptedGroupChange change = decryptedGroupV2Context.getChange();

    return selfCreatedGroup(change);
  }

  @VisibleForTesting
  @Nullable DecryptedGroupV2Context getDecryptedGroupV2Context() {
    if (!isGroupUpdate() || !isGroupV2()) {
      return null;
    }

    DecryptedGroupV2Context decryptedGroupV2Context;
    try {
      byte[] decoded = Base64.decode(getBody());
      decryptedGroupV2Context = DecryptedGroupV2Context.parseFrom(decoded);

    } catch (IOException e) {
      Log.w(TAG, "GV2 Message update detail could not be read", e);
      decryptedGroupV2Context = null;
    }
    return decryptedGroupV2Context;
  }

  private static boolean selfCreatedGroup(@NonNull DecryptedGroupChange change) {
    return change.getRevision() == 0 &&
           change.getEditor().equals(UuidUtil.toByteString(SignalStore.account().requireAci().uuid()));
  }

  public static @NonNull UpdateDescription getGv2ChangeDescription(@NonNull Context context, @NonNull String body, @Nullable Consumer<RecipientId> recipientClickHandler) {
    try {
      byte[]                         decoded                 = Base64.decode(body);
      DecryptedGroupV2Context        decryptedGroupV2Context = DecryptedGroupV2Context.parseFrom(decoded);
      GroupsV2UpdateMessageProducer  updateMessageProducer   = new GroupsV2UpdateMessageProducer(context, SignalStore.account().getServiceIds(), recipientClickHandler);

      if (decryptedGroupV2Context.hasChange() && (decryptedGroupV2Context.getGroupState().getRevision() != 0 || decryptedGroupV2Context.hasPreviousGroupState())) {
        return UpdateDescription.concatWithNewLines(updateMessageProducer.describeChanges(decryptedGroupV2Context.getPreviousGroupState(), decryptedGroupV2Context.getChange()));
      } else {
        List<UpdateDescription> newGroupDescriptions = new ArrayList<>();
        newGroupDescriptions.add(updateMessageProducer.describeNewGroup(decryptedGroupV2Context.getGroupState(), decryptedGroupV2Context.getChange()));

        if (decryptedGroupV2Context.getChange().hasNewTimer()) {
          updateMessageProducer.describeNewTimer(decryptedGroupV2Context.getChange(), newGroupDescriptions);
        }

        if (selfCreatedGroup(decryptedGroupV2Context.getChange())) {
          newGroupDescriptions.add(staticUpdateDescription(context.getString(R.string.MessageRecord_invite_friends_to_this_group), 0));
        }
        return UpdateDescription.concatWithNewLines(newGroupDescriptions);
      }
    } catch (IOException | IllegalArgumentException e) {
      Log.w(TAG, "GV2 Message update detail could not be read", e);
      return staticUpdateDescription(context.getString(R.string.MessageRecord_group_updated), R.drawable.ic_update_group_16);
    }
  }

  public @Nullable InviteAddState getGv2AddInviteState() {
    DecryptedGroupV2Context decryptedGroupV2Context = getDecryptedGroupV2Context();

    if (decryptedGroupV2Context == null) {
      return null;
    }

    DecryptedGroup groupState = decryptedGroupV2Context.getGroupState();
    boolean        invited    = DecryptedGroupUtil.findPendingByUuid(groupState.getPendingMembersList(), SignalStore.account().requireAci().uuid()).isPresent();

    if (decryptedGroupV2Context.hasChange()) {
      UUID changeEditor = UuidUtil.fromByteStringOrNull(decryptedGroupV2Context.getChange().getEditor());

      if (changeEditor != null) {
        return new InviteAddState(invited, changeEditor);
      }
    }

    Log.w(TAG, "GV2 Message editor could not be determined");
    return null;
  }

  private @NonNull String getCallDateString(@NonNull Context context) {
    return DateUtils.getSimpleRelativeTimeSpanString(context, Locale.getDefault(), getDateSent());
  }

  private static @NonNull UpdateDescription fromRecipient(@NonNull Recipient recipient,
                                                          @NonNull Function<Recipient, String> stringGenerator,
                                                          @DrawableRes int iconResource)
  {
    return UpdateDescription.mentioning(Collections.singletonList(recipient.getServiceId().orElse(ServiceId.UNKNOWN)),
                                        () -> new SpannableString(stringGenerator.apply(recipient.resolve())),
                                        iconResource);
  }

  private static @NonNull UpdateDescription staticUpdateDescription(@NonNull String string,
                                                                    @DrawableRes int iconResource)
  {
    return UpdateDescription.staticDescription(string, iconResource);
  }

  private static @NonNull UpdateDescription staticUpdateDescription(@NonNull String string,
                                                                    @DrawableRes int iconResource,
                                                                    @ColorInt int lightTint,
                                                                    @ColorInt int darkTint)
  {
    return UpdateDescription.staticDescription(string, iconResource, lightTint, darkTint);
  }

  private @NonNull String getProfileChangeDescription(@NonNull Context context) {
    try {
      byte[]               decoded              = Base64.decode(getBody());
      ProfileChangeDetails profileChangeDetails = ProfileChangeDetails.parseFrom(decoded);

      if (profileChangeDetails.hasProfileNameChange()) {
        String displayName  = getIndividualRecipient().getDisplayName(context);
        String newName      = StringUtil.isolateBidi(ProfileName.fromSerialized(profileChangeDetails.getProfileNameChange().getNew()).toString());
        String previousName = StringUtil.isolateBidi(ProfileName.fromSerialized(profileChangeDetails.getProfileNameChange().getPrevious()).toString());

        if (getIndividualRecipient().isSystemContact()) {
          return context.getString(R.string.MessageRecord_changed_their_profile_name_from_to, displayName, previousName, newName);
        } else {
          return context.getString(R.string.MessageRecord_changed_their_profile_name_to, previousName, newName);
        }
      }
    } catch (IOException e) {
      Log.w(TAG, "Profile name change details could not be read", e);
    }

    return context.getString(R.string.MessageRecord_changed_their_profile, getIndividualRecipient().getDisplayName(context));
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

      return UpdateDescription.concatWithNewLines(updates);
    }
  }

  public static @NonNull UpdateDescription getGroupCallUpdateDescription(@NonNull Context context, @NonNull String body, boolean withTime) {
    GroupCallUpdateDetails groupCallUpdateDetails = GroupCallUpdateDetailsUtil.parse(body);

    List<ServiceId> joinedMembers = Stream.of(groupCallUpdateDetails.getInCallUuidsList())
                                          .map(UuidUtil::parseOrNull)
                                          .withoutNulls()
                                          .map(ServiceId::from)
                                          .toList();

    UpdateDescription.SpannableFactory stringFactory = new GroupCallUpdateMessageFactory(context, joinedMembers, withTime, groupCallUpdateDetails);

    return UpdateDescription.mentioning(joinedMembers, stringFactory, R.drawable.ic_video_16);
  }

  public boolean isGroupV2DescriptionUpdate() {
    DecryptedGroupV2Context decryptedGroupV2Context = getDecryptedGroupV2Context();
    if (decryptedGroupV2Context != null) {
      return decryptedGroupV2Context.hasChange() && getDecryptedGroupV2Context().getChange().hasNewDescription();
    }
    return false;
  }

  public @NonNull String getGroupV2DescriptionUpdate() {
    DecryptedGroupV2Context decryptedGroupV2Context = getDecryptedGroupV2Context();
    if (decryptedGroupV2Context != null) {
      return decryptedGroupV2Context.getChange().hasNewDescription() ? decryptedGroupV2Context.getChange().getNewDescription().getValue() : "";
    }
    return "";
  }

  public boolean isGroupV2JoinRequest(@Nullable ServiceId serviceId) {
    if (serviceId == null) {
      return false;
    }

    return isGroupV2JoinRequest(UuidUtil.toByteString(serviceId.uuid()));
  }

  public boolean isGroupV2JoinRequest(@NonNull ByteString uuid) {
    DecryptedGroupV2Context decryptedGroupV2Context = getDecryptedGroupV2Context();
    if (decryptedGroupV2Context != null && decryptedGroupV2Context.hasChange()) {
      DecryptedGroupChange change = decryptedGroupV2Context.getChange();
      return change.getEditor().equals(uuid) && change.getNewRequestingMembersList().stream().anyMatch(r -> r.getUuid().equals(uuid));
    }
    return false;
  }

  public boolean isCollapsedGroupV2JoinUpdate() {
    return isCollapsedGroupV2JoinUpdate(null);
  }

  public boolean isCollapsedGroupV2JoinUpdate(@Nullable ServiceId serviceId) {
    DecryptedGroupV2Context decryptedGroupV2Context = getDecryptedGroupV2Context();
    if (decryptedGroupV2Context != null && decryptedGroupV2Context.hasChange()) {
      DecryptedGroupChange change = decryptedGroupV2Context.getChange();
      return change.getNewRequestingMembersCount() > 0 &&
             change.getDeleteRequestingMembersCount() > 0 &&
             (serviceId == null || change.getEditor().equals(UuidUtil.toByteString(serviceId.uuid())));
    }
    return false;
  }

  public static @NonNull String createNewContextWithAppendedDeleteJoinRequest(@NonNull MessageRecord messageRecord, int revision, @NonNull ByteString id) {
    DecryptedGroupV2Context decryptedGroupV2Context = messageRecord.getDecryptedGroupV2Context();

    if (decryptedGroupV2Context != null && decryptedGroupV2Context.hasChange()) {
      DecryptedGroupChange change = decryptedGroupV2Context.getChange();

      return Base64.encodeBytes(decryptedGroupV2Context.toBuilder()
                                                       .setChange(change.toBuilder()
                                                                        .setRevision(revision)
                                                                        .addDeleteRequestingMembers(id))
                                                       .build().toByteArray());
    }

    throw new AssertionError("Attempting to modify a message with no change");
  }

  public long getId() {
    return id;
  }

  public boolean isPush() {
    return SmsDatabase.Types.isPushType(type) && !SmsDatabase.Types.isForcedSms(type);
  }

  public long getTimestamp() {
    if ((isPush() || isCallLog()) && getDateSent() < getDateReceived()) {
      return getDateSent();
    }
    return getDateReceived();
  }

  public long getServerTimestamp() {
    return serverTimestamp;
  }

  public boolean isForcedSms() {
    return SmsDatabase.Types.isForcedSms(type);
  }

  public boolean isIdentityVerified() {
    return SmsDatabase.Types.isIdentityVerified(type);
  }

  public boolean isIdentityDefault() {
    return SmsDatabase.Types.isIdentityDefault(type);
  }

  public boolean isIdentityMismatchFailure() {
    return mismatches != null && !mismatches.isEmpty();
  }

  public boolean isBundleKeyExchange() {
    return SmsDatabase.Types.isBundleKeyExchange(type);
  }

  public boolean isContentBundleKeyExchange() {
    return SmsDatabase.Types.isContentBundleKeyExchange(type);
  }

  public boolean isRateLimited() {
    return SmsDatabase.Types.isRateLimited(type);
  }

  public boolean isIdentityUpdate() {
    return SmsDatabase.Types.isIdentityUpdate(type);
  }

  public boolean isCorruptedKeyExchange() {
    return SmsDatabase.Types.isCorruptedKeyExchange(type);
  }

  public boolean isBadDecryptType() {
    return MmsSmsColumns.Types.isBadDecryptType(type);
  }

  public boolean isThreadMergeEventType() {
    return MmsSmsColumns.Types.isThreadMergeType(type);
  }

  public boolean isSmsExportType() {
    return MmsSmsColumns.Types.isSmsExport(type);
  }

  public boolean isInvalidVersionKeyExchange() {
    return SmsDatabase.Types.isInvalidVersionKeyExchange(type);
  }

  public boolean isGroupV1MigrationEvent() {
    return SmsDatabase.Types.isGroupV1MigrationEvent(type);
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
           isChangeNumber() || isBoostRequest() || isThreadMergeEventType() || isSmsExportType() ||
           isPaymentsRequestToActivate() || isPaymentsActivated();
  }

  public boolean isMediaPending() {
    return false;
  }

  public Recipient getIndividualRecipient() {
    return individualRecipient.live().get();
  }

  public int getRecipientDeviceId() {
    return recipientDeviceId;
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
    return isFailed() && ((getRecipient().isPushGroup() && hasNetworkFailures()) || !isIdentityMismatchFailure());
  }

  public boolean isChatSessionRefresh() {
    return MmsSmsColumns.Types.isChatSessionRefresh(type);
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
    return other != null                              &&
           other instanceof MessageRecord             &&
           ((MessageRecord) other).getId() == getId() &&
           ((MessageRecord) other).isMms() == isMms();
  }

  public int hashCode() {
    return (int)getId();
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

  public boolean hasMessageRanges() {
    return false;
  }

  public @NonNull BodyRangeList requireMessageRanges() {
    throw new NullPointerException();
  }

  public static final class InviteAddState {

    private final boolean invited;
    private final UUID    addedOrInvitedBy;

    public InviteAddState(boolean invited, @NonNull UUID addedOrInvitedBy) {
      this.invited          = invited;
      this.addedOrInvitedBy = addedOrInvitedBy;
    }

    public @NonNull UUID getAddedOrInvitedBy() {
      return addedOrInvitedBy;
    }

    public boolean isInvited() {
      return invited;
    }
  }
}
