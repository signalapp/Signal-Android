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
import androidx.annotation.NonNull;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.ExpirationUtil;
import org.thoughtcrime.securesms.util.GroupUtil;

import java.util.List;

/**
 * The base class for message record models that are displayed in
 * conversations, as opposed to models that are displayed in a thread list.
 * Encapsulates the shared data between both SMS and MMS messages.
 *
 * @author Moxie Marlinspike
 *
 */
public abstract class MessageRecord extends DisplayRecord {

  private final Recipient                 individualRecipient;
  private final int                       recipientDeviceId;
  private final long                      id;
  private final List<IdentityKeyMismatch> mismatches;
  private final List<NetworkFailure>      networkFailures;
  private final int                       subscriptionId;
  private final long                      expiresIn;
  private final long                      expireStarted;
  private final boolean                   unidentified;

  MessageRecord(long id, String body, Recipient conversationRecipient,
                Recipient individualRecipient, int recipientDeviceId,
                long dateSent, long dateReceived, long threadId,
                int deliveryStatus, int deliveryReceiptCount, long type,
                List<IdentityKeyMismatch> mismatches,
                List<NetworkFailure> networkFailures,
                int subscriptionId, long expiresIn, long expireStarted,
                int readReceiptCount, boolean unidentified)
  {
    super(body, conversationRecipient, dateSent, dateReceived,
          threadId, deliveryStatus, deliveryReceiptCount, type, readReceiptCount);
    this.id                  = id;
    this.individualRecipient = individualRecipient;
    this.recipientDeviceId   = recipientDeviceId;
    this.mismatches          = mismatches;
    this.networkFailures     = networkFailures;
    this.subscriptionId      = subscriptionId;
    this.expiresIn           = expiresIn;
    this.expireStarted       = expireStarted;
    this.unidentified        = unidentified;
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
    if (isGroupUpdate() && isOutgoing()) {
      return new SpannableString(context.getString(R.string.MessageRecord_you_updated_group));
    } else if (isGroupUpdate()) {
      return new SpannableString(GroupUtil.getDescription(context, getBody()).toString(getIndividualRecipient()));
    } else if (isGroupQuit() && isOutgoing()) {
      return new SpannableString(context.getString(R.string.MessageRecord_left_group));
    } else if (isGroupQuit()) {
      return new SpannableString(context.getString(R.string.ConversationItem_group_action_left, getIndividualRecipient().toShortString()));
    } else if (isIncomingCall()) {
      return new SpannableString(context.getString(R.string.MessageRecord_s_called_you, getIndividualRecipient().toShortString()));
    } else if (isOutgoingCall()) {
      return new SpannableString(context.getString(R.string.MessageRecord_you_called));
    } else if (isMissedCall()) {
      return new SpannableString(context.getString(R.string.MessageRecord_missed_call));
    } else if (isJoined()) {
      return new SpannableString(context.getString(R.string.MessageRecord_s_joined_signal, getIndividualRecipient().toShortString()));
    } else if (isExpirationTimerUpdate()) {
      int seconds = (int)(getExpiresIn() / 1000);
      if (seconds <= 0) {
        return isOutgoing() ? new SpannableString(context.getString(R.string.MessageRecord_you_disabled_disappearing_messages))
                            : new SpannableString(context.getString(R.string.MessageRecord_s_disabled_disappearing_messages, getIndividualRecipient().toShortString()));
      }
      String time = ExpirationUtil.getExpirationDisplayValue(context, seconds);
      return isOutgoing() ? new SpannableString(context.getString(R.string.MessageRecord_you_set_disappearing_message_time_to_s, time))
                          : new SpannableString(context.getString(R.string.MessageRecord_s_set_disappearing_message_time_to_s, getIndividualRecipient().toShortString(), time));
    } else if (isIdentityUpdate()) {
      return new SpannableString(context.getString(R.string.MessageRecord_your_safety_number_with_s_has_changed, getIndividualRecipient().toShortString()));
    } else if (isIdentityVerified()) {
      if (isOutgoing()) return new SpannableString(context.getString(R.string.MessageRecord_you_marked_your_safety_number_with_s_verified, getIndividualRecipient().toShortString()));
      else              return new SpannableString(context.getString(R.string.MessageRecord_you_marked_your_safety_number_with_s_verified_from_another_device, getIndividualRecipient().toShortString()));
    } else if (isIdentityDefault()) {
      if (isOutgoing()) return new SpannableString(context.getString(R.string.MessageRecord_you_marked_your_safety_number_with_s_unverified, getIndividualRecipient().toShortString()));
      else              return new SpannableString(context.getString(R.string.MessageRecord_you_marked_your_safety_number_with_s_unverified_from_another_device, getIndividualRecipient().toShortString()));
    }

    return new SpannableString(getBody());
  }

  public long getId() {
    return id;
  }

  public boolean isPush() {
    return SmsDatabase.Types.isPushType(type) && !SmsDatabase.Types.isForcedSms(type);
  }

  public long getTimestamp() {
    if (isPush() && getDateSent() < getDateReceived()) {
      return getDateSent();
    }
    return getDateReceived();
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

  public boolean isUpdate() {
    return isGroupAction() || isJoined() || isExpirationTimerUpdate() || isCallLog() ||
           isEndSession()  || isIdentityUpdate() || isIdentityVerified() || isIdentityDefault();
  }

  public boolean isMediaPending() {
    return false;
  }

  public Recipient getIndividualRecipient() {
    return individualRecipient;
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

  protected SpannableString emphasisAdded(String sequence) {
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
}
