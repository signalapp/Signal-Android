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
import androidx.core.content.ContextCompat;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedGroupChange;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context;
import org.thoughtcrime.securesms.database.model.databaseprotos.GroupCallUpdateDetails;
import org.thoughtcrime.securesms.database.model.databaseprotos.ProfileChangeDetails;
import org.thoughtcrime.securesms.groups.GroupMigrationMembershipChange;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.ExpirationUtil;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.StringUtil;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Function;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.util.UuidUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

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

  private final Recipient                 individualRecipient;
  private final int                       recipientDeviceId;
  private final long                      id;
  private final List<IdentityKeyMismatch> mismatches;
  private final List<NetworkFailure>      networkFailures;
  private final int                       subscriptionId;
  private final long                      expiresIn;
  private final long                      expireStarted;
  private final boolean                   unidentified;
  private final List<ReactionRecord>      reactions;
  private final long                      serverTimestamp;
  private final boolean                   remoteDelete;
  private final long                      notifiedTimestamp;

  MessageRecord(long id, String body, Recipient conversationRecipient,
                Recipient individualRecipient, int recipientDeviceId,
                long dateSent, long dateReceived, long dateServer, long threadId,
                int deliveryStatus, int deliveryReceiptCount, long type,
                List<IdentityKeyMismatch> mismatches,
                List<NetworkFailure> networkFailures,
                int subscriptionId, long expiresIn, long expireStarted,
                int readReceiptCount, boolean unidentified,
                @NonNull List<ReactionRecord> reactions, boolean remoteDelete, long notifiedTimestamp,
                int viewedReceiptCount)
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
  public SpannableString getDisplayBody(@NonNull Context context) {
    UpdateDescription updateDisplayBody = getUpdateDisplayBody(context);

    if (updateDisplayBody != null) {
      return new SpannableString(updateDisplayBody.getString());
    }

    return new SpannableString(getBody());
  }

  public @Nullable UpdateDescription getUpdateDisplayBody(@NonNull Context context) {
    if (isGroupUpdate() && isGroupV2()) {
      return getGv2ChangeDescription(context, getBody());
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
    } else if (isEndSession()) {
      if (isOutgoing()) return staticUpdateDescription(context.getString(R.string.SmsMessageRecord_secure_session_reset), R.drawable.ic_update_info_16);
      else              return fromRecipient(getIndividualRecipient(), r-> context.getString(R.string.SmsMessageRecord_secure_session_reset_s, r.getDisplayName(context)), R.drawable.ic_update_info_16);
    } else if (isGroupV1MigrationEvent()) {
      return getGroupMigrationEventDescription(context);
    } else if (isFailedDecryptionType()) {
      return staticUpdateDescription(context.getString(R.string.MessageRecord_chat_session_refreshed), R.drawable.ic_refresh_16);
    }

    return null;
  }

  public boolean isSelfCreatedGroup() {
    if (!isGroupUpdate() || !isGroupV2()) return false;

    try {
      byte[]               decoded = Base64.decode(getBody());
      DecryptedGroupChange change  = DecryptedGroupV2Context.parseFrom(decoded)
                                                               .getChange();

      return selfCreatedGroup(change);
    } catch (IOException e) {
      Log.w(TAG, "GV2 Message update detail could not be read", e);
      return false;
    }
  }

  private static boolean selfCreatedGroup(@NonNull DecryptedGroupChange change) {
    return change.getRevision() == 0 &&
           change.getEditor().equals(UuidUtil.toByteString(Recipient.self().requireUuid()));
  }

  public static @NonNull UpdateDescription getGv2ChangeDescription(@NonNull Context context, @NonNull String body) {
    try {
      ShortStringDescriptionStrategy descriptionStrategy     = new ShortStringDescriptionStrategy(context);
      byte[]                         decoded                 = Base64.decode(body);
      DecryptedGroupV2Context        decryptedGroupV2Context = DecryptedGroupV2Context.parseFrom(decoded);
      GroupsV2UpdateMessageProducer  updateMessageProducer   = new GroupsV2UpdateMessageProducer(context, descriptionStrategy, Recipient.self().getUuid().get());

      if (decryptedGroupV2Context.hasChange() && (decryptedGroupV2Context.getGroupState().getRevision() != 0 || decryptedGroupV2Context.hasPreviousGroupState())) {
        return UpdateDescription.concatWithNewLines(updateMessageProducer.describeChanges(decryptedGroupV2Context.getPreviousGroupState(), decryptedGroupV2Context.getChange()));
      } else if (selfCreatedGroup(decryptedGroupV2Context.getChange())) {
        return UpdateDescription.concatWithNewLines(Arrays.asList(updateMessageProducer.describeNewGroup(decryptedGroupV2Context.getGroupState(), decryptedGroupV2Context.getChange()),
                                                                  staticUpdateDescription(context.getString(R.string.MessageRecord_invite_friends_to_this_group), 0)));
      } else {
        return updateMessageProducer.describeNewGroup(decryptedGroupV2Context.getGroupState(), decryptedGroupV2Context.getChange());
      }
    } catch (IOException e) {
      Log.w(TAG, "GV2 Message update detail could not be read", e);
      return staticUpdateDescription(context.getString(R.string.MessageRecord_group_updated), R.drawable.ic_update_group_16);
    }
  }

  public @Nullable InviteAddState getGv2AddInviteState() {
    try {
      byte[]                  decoded                 = Base64.decode(getBody());
      DecryptedGroupV2Context decryptedGroupV2Context = DecryptedGroupV2Context.parseFrom(decoded);
      DecryptedGroup          groupState              = decryptedGroupV2Context.getGroupState();
      boolean                 invited                 = DecryptedGroupUtil.findPendingByUuid(groupState.getPendingMembersList(), Recipient.self().requireUuid()).isPresent();

      if (decryptedGroupV2Context.hasChange()) {
        UUID changeEditor = UuidUtil.fromByteStringOrNull(decryptedGroupV2Context.getChange().getEditor());

        if (changeEditor != null) {
          return new InviteAddState(invited, changeEditor);
        }
      }

      Log.w(TAG, "GV2 Message editor could not be determined");
      return null;
    } catch (IOException e) {
      Log.w(TAG, "GV2 Message update detail could not be read", e);
      return null;
    }
  }

  private @NonNull String getCallDateString(@NonNull Context context) {
    return DateUtils.getExtendedRelativeTimeSpanString(context, Locale.getDefault(), getDateSent());
  }

  private static @NonNull UpdateDescription fromRecipient(@NonNull Recipient recipient,
                                                          @NonNull Function<Recipient, String> stringGenerator,
                                                          @DrawableRes int iconResource)
  {
    return UpdateDescription.mentioning(Collections.singletonList(recipient.getUuid().or(UuidUtil.UNKNOWN_UUID)),
                                        () -> stringGenerator.apply(recipient.resolve()),
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

    List<UUID> joinedMembers = Stream.of(groupCallUpdateDetails.getInCallUuidsList())
                                     .map(UuidUtil::parseOrNull)
                                     .withoutNulls()
                                     .toList();

    UpdateDescription.StringFactory stringFactory = new GroupCallUpdateMessageFactory(context, joinedMembers, withTime, groupCallUpdateDetails);

    return UpdateDescription.mentioning(joinedMembers, stringFactory, R.drawable.ic_video_16);
  }

  /**
   * Describes a UUID by it's corresponding recipient's {@link Recipient#getDisplayName(Context)}.
   */
  private static class ShortStringDescriptionStrategy implements GroupsV2UpdateMessageProducer.DescribeMemberStrategy {

    private final Context context;

   ShortStringDescriptionStrategy(@NonNull Context context) {
      this.context = context;
   }

   @Override
   public @NonNull String describe(@NonNull UUID uuid) {
     if (UuidUtil.UNKNOWN_UUID.equals(uuid)) {
       return context.getString(R.string.MessageRecord_unknown);
     }
     return Recipient.resolved(RecipientId.from(uuid, null)).getDisplayName(context);
   }
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

  public boolean isIdentityUpdate() {
    return SmsDatabase.Types.isIdentityUpdate(type);
  }

  public boolean isCorruptedKeyExchange() {
    return SmsDatabase.Types.isCorruptedKeyExchange(type);
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
           isEndSession()  || isIdentityUpdate() || isIdentityVerified() || isIdentityDefault() ||
           isProfileChange() || isGroupV1MigrationEvent() || isFailedDecryptionType();
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

  public List<IdentityKeyMismatch> getIdentityKeyMismatches() {
    return mismatches;
  }

  public List<NetworkFailure> getNetworkFailures() {
    return networkFailures;
  }

  public boolean hasNetworkFailures() {
    return networkFailures != null && !networkFailures.isEmpty();
  }

  public boolean hasFailedWithNetworkFailures() {
    return isFailed() && ((getRecipient().isPushGroup() && hasNetworkFailures()) || !isIdentityMismatchFailure());
  }

  public boolean isFailedDecryptionType() {
    return MmsSmsColumns.Types.isFailedDecryptType(type);
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
