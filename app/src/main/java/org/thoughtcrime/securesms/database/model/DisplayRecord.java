/*
 * Copyright (C) 2012 Moxie Marlinspike
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
import android.text.SpannableString;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.recipients.Recipient;

/**
 * The base class for all message record models.  Encapsulates basic data
 * shared between ThreadRecord and MessageRecord.
 *
 * @author Moxie Marlinspike
 *
 */

public abstract class DisplayRecord {

  protected final long type;

  private final Recipient  recipient;
  private final long       dateSent;
  private final long       dateReceived;
  private final long       threadId;
  private final String     body;
  private final int        deliveryStatus;
  private final int        deliveryReceiptCount;
  private final int        readReceiptCount;
  private final int        viewReceiptCount;

  DisplayRecord(String body, Recipient recipient, long dateSent,
                long dateReceived, long threadId, int deliveryStatus, int deliveryReceiptCount,
                long type, int readReceiptCount, int viewReceiptCount)
  {
    this.threadId             = threadId;
    this.recipient            = recipient;
    this.dateSent             = dateSent;
    this.dateReceived         = dateReceived;
    this.type                 = type;
    this.body                 = body;
    this.deliveryReceiptCount = deliveryReceiptCount;
    this.readReceiptCount     = readReceiptCount;
    this.deliveryStatus       = deliveryStatus;
    this.viewReceiptCount     = viewReceiptCount;
  }

  public @NonNull String getBody() {
    return body == null ? "" : body;
  }

  public boolean isFailed() {
    return
        MmsSmsColumns.Types.isFailedMessageType(type)            ||
        MmsSmsColumns.Types.isPendingSecureSmsFallbackType(type) ||
        deliveryStatus >= SmsDatabase.Status.STATUS_FAILED;
  }

  public boolean isPending() {
    return MmsSmsColumns.Types.isPendingMessageType(type) &&
           !MmsSmsColumns.Types.isIdentityVerified(type)  &&
           !MmsSmsColumns.Types.isIdentityDefault(type);
  }

  @VisibleForTesting
  public long getType() {
    return type;
  }

  public boolean isSent() {
    return MmsSmsColumns.Types.isSentType(type);
  }

  public boolean isOutgoing() {
    return MmsSmsColumns.Types.isOutgoingMessageType(type);
  }

  public abstract SpannableString getDisplayBody(@NonNull Context context);

  public Recipient getRecipient() {
    return recipient.live().get();
  }

  public long getDateSent() {
    return dateSent;
  }

  public long getDateReceived() {
    return dateReceived;
  }

  public long getThreadId() {
    return threadId;
  }

  public boolean isKeyExchange() {
    return SmsDatabase.Types.isKeyExchangeType(type);
  }

  public boolean isEndSession() {
    return SmsDatabase.Types.isEndSessionType(type);
  }

  public boolean isGroupUpdate() {
    return SmsDatabase.Types.isGroupUpdate(type);
  }

  public boolean isGroupV2() {
    return SmsDatabase.Types.isGroupV2(type);
  }

  public boolean isGroupQuit() {
    return SmsDatabase.Types.isGroupQuit(type);
  }

  public boolean isGroupAction() {
    return isGroupUpdate() || isGroupQuit();
  }

  public boolean isExpirationTimerUpdate() {
    return SmsDatabase.Types.isExpirationTimerUpdate(type);
  }

  public boolean isCallLog() {
    return SmsDatabase.Types.isCallLog(type);
  }

  public boolean isJoined() {
    return SmsDatabase.Types.isJoinedType(type);
  }

  public boolean isIncomingAudioCall() {
    return SmsDatabase.Types.isIncomingAudioCall(type);
  }

  public boolean isIncomingVideoCall() {
    return SmsDatabase.Types.isIncomingVideoCall(type);
  }

  public boolean isOutgoingAudioCall() {
    return SmsDatabase.Types.isOutgoingAudioCall(type);
  }

  public boolean isOutgoingVideoCall() {
    return SmsDatabase.Types.isOutgoingVideoCall(type);
  }

  public final boolean isMissedAudioCall() {
    return SmsDatabase.Types.isMissedAudioCall(type);
  }

  public final boolean isMissedVideoCall() {
    return SmsDatabase.Types.isMissedVideoCall(type);
  }

  public final boolean isGroupCall() {
    return SmsDatabase.Types.isGroupCall(type);
  }

  public boolean isVerificationStatusChange() {
    return SmsDatabase.Types.isIdentityDefault(type) || SmsDatabase.Types.isIdentityVerified(type);
  }

  public boolean isProfileChange() {
    return SmsDatabase.Types.isProfileChange(type);
  }

  public boolean isChangeNumber() {
    return SmsDatabase.Types.isChangeNumber(type);
  }

  public boolean isBoostRequest() {
    return MmsSmsColumns.Types.isBoostRequest(type);
  }

  public int getDeliveryStatus() {
    return deliveryStatus;
  }

  public int getDeliveryReceiptCount() {
    return deliveryReceiptCount;
  }

  public int getReadReceiptCount() {
    return readReceiptCount;
  }

  /**
   * For outgoing messages, this is incremented whenever a remote recipient has viewed our message
   * and sends us a VIEWED receipt. For incoming messages, this is an indication of whether local
   * user has viewed a piece of content.
   *
   * @return the number of times this has been viewed.
   */
  public int getViewedReceiptCount() {
    return viewReceiptCount;
  }

  public boolean isDelivered() {
    return (deliveryStatus >= SmsDatabase.Status.STATUS_COMPLETE &&
            deliveryStatus < SmsDatabase.Status.STATUS_PENDING) || deliveryReceiptCount > 0;
  }

  public boolean isRemoteViewed() {
    return viewReceiptCount > 0;
  }

  public boolean isRemoteRead() {
    return readReceiptCount > 0;
  }

  public boolean isPendingInsecureSmsFallback() {
    return SmsDatabase.Types.isPendingInsecureSmsFallbackType(type);
  }

  public boolean isPaymentNotification() {
    return MmsSmsColumns.Types.isPaymentsNotification(type);
  }

  public boolean isPaymentsRequestToActivate() {
    return MmsSmsColumns.Types.isPaymentsRequestToActivate(type);
  }

  public boolean isPaymentsActivated() {
    return MmsSmsColumns.Types.isPaymentsActivated(type);
  }
}
