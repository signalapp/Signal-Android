/**
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

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
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

  private static final int MAX_DISPLAY_LENGTH = 2000;

  private final Recipient                 individualRecipient;
  private final int                       recipientDeviceId;
  private final long                      id;
  private final List<IdentityKeyMismatch> mismatches;
  private final List<NetworkFailure>      networkFailures;
  private final int                       subscriptionId;
  private final long                      expiresIn;
  private final long                      expireStarted;

  MessageRecord(Context context, long id, Body body, Recipients recipients,
                Recipient individualRecipient, int recipientDeviceId,
                long dateSent, long dateReceived, long threadId,
                int deliveryStatus, int receiptCount, long type,
                List<IdentityKeyMismatch> mismatches,
                List<NetworkFailure> networkFailures,
                int subscriptionId, long expiresIn, long expireStarted)
  {
    super(context, body, recipients, dateSent, dateReceived, threadId, deliveryStatus, receiptCount,
          type);
    this.id                  = id;
    this.individualRecipient = individualRecipient;
    this.recipientDeviceId   = recipientDeviceId;
    this.mismatches          = mismatches;
    this.networkFailures     = networkFailures;
    this.subscriptionId      = subscriptionId;
    this.expiresIn           = expiresIn;
    this.expireStarted       = expireStarted;
  }

  public abstract boolean isMms();
  public abstract boolean isMmsNotification();

  public boolean isSecure() {
    return MmsSmsColumns.Types.isSecureType(type);
  }

  public boolean isLegacyMessage() {
    return MmsSmsColumns.Types.isLegacyType(type);
  }

  public boolean isAsymmetricEncryption() {
    return MmsSmsColumns.Types.isAsymmetricEncryption(type);
  }

  @Override
  public SpannableString getDisplayBody() {
    if (isGroupUpdate() && isOutgoing()) {
      return emphasisAdded(context.getString(R.string.MessageRecord_updated_group));
    } else if (isGroupUpdate()) {
      return emphasisAdded(GroupUtil.getDescription(context, getBody().getBody()).toString());
    } else if (isGroupQuit() && isOutgoing()) {
      return emphasisAdded(context.getString(R.string.MessageRecord_left_group));
    } else if (isGroupQuit()) {
      return emphasisAdded(context.getString(R.string.ConversationItem_group_action_left, getIndividualRecipient().toShortString()));
    } else if (isIncomingCall()) {
      return emphasisAdded(context.getString(R.string.MessageRecord_s_called_you, getIndividualRecipient().toShortString()));
    } else if (isOutgoingCall()) {
      return emphasisAdded(context.getString(R.string.MessageRecord_called_s, getIndividualRecipient().toShortString()));
    } else if (isMissedCall()) {
      return emphasisAdded(context.getString(R.string.MessageRecord_missed_call_from, getIndividualRecipient().toShortString()));
    } else if (isJoined()) {
      return emphasisAdded(context.getString(R.string.MessageRecord_s_is_on_signal_say_hey, getIndividualRecipient().toShortString()));
    } else if (isExpirationTimerUpdate()) {
      String sender = isOutgoing() ? context.getString(R.string.MessageRecord_you) : getIndividualRecipient().toShortString();
      String time   = ExpirationUtil.getExpirationDisplayValue(context, (int) (getExpiresIn() / 1000));
      return emphasisAdded(context.getString(R.string.MessageRecord_s_set_disappearing_message_time_to_s, sender, time));
    } else if (isIdentityUpdate()) {
      return emphasisAdded(context.getString(R.string.MessageRecord_your_safety_numbers_with_s_have_changed, getIndividualRecipient().toShortString()));
    } else if (getBody().getBody().length() > MAX_DISPLAY_LENGTH) {
      return new SpannableString(getBody().getBody().substring(0, MAX_DISPLAY_LENGTH));
    }

    return new SpannableString(getBody().getBody());
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

  public boolean isStaleKeyExchange() {
    return SmsDatabase.Types.isStaleKeyExchange(type);
  }

  public boolean isProcessedKeyExchange() {
    return SmsDatabase.Types.isProcessedKeyExchange(type);
  }

  public boolean isIdentityMismatchFailure() {
    return mismatches != null && !mismatches.isEmpty();
  }

  public boolean isBundleKeyExchange() {
    return SmsDatabase.Types.isBundleKeyExchange(type);
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
}
