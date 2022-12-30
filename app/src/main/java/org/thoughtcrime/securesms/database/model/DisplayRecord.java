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

import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.MessageTable.Status;
import org.thoughtcrime.securesms.database.MessageTypes;
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
        MessageTypes.isFailedMessageType(type) ||
        MessageTypes.isPendingSecureSmsFallbackType(type) ||
        deliveryStatus >= MessageTable.Status.STATUS_FAILED;
  }

  public boolean isPending() {
    return MessageTypes.isPendingMessageType(type) &&
           !MessageTypes.isIdentityVerified(type) &&
           !MessageTypes.isIdentityDefault(type);
  }

  @VisibleForTesting
  public long getType() {
    return type;
  }

  public boolean isSent() {
    return MessageTypes.isSentType(type);
  }

  public boolean isOutgoing() {
    return MessageTypes.isOutgoingMessageType(type);
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
    return MessageTypes.isKeyExchangeType(type);
  }

  public boolean isEndSession() {
    return MessageTypes.isEndSessionType(type);
  }

  public boolean isGroupUpdate() {
    return MessageTypes.isGroupUpdate(type);
  }

  public boolean isGroupV2() {
    return MessageTypes.isGroupV2(type);
  }

  public boolean isGroupQuit() {
    return MessageTypes.isGroupQuit(type);
  }

  public boolean isGroupAction() {
    return isGroupUpdate() || isGroupQuit();
  }

  public boolean isExpirationTimerUpdate() {
    return MessageTypes.isExpirationTimerUpdate(type);
  }

  public boolean isCallLog() {
    return MessageTypes.isCallLog(type);
  }

  public boolean isJoined() {
    return MessageTypes.isJoinedType(type);
  }

  public boolean isIncomingAudioCall() {
    return MessageTypes.isIncomingAudioCall(type);
  }

  public boolean isIncomingVideoCall() {
    return MessageTypes.isIncomingVideoCall(type);
  }

  public boolean isOutgoingAudioCall() {
    return MessageTypes.isOutgoingAudioCall(type);
  }

  public boolean isOutgoingVideoCall() {
    return MessageTypes.isOutgoingVideoCall(type);
  }

  public final boolean isMissedAudioCall() {
    return MessageTypes.isMissedAudioCall(type);
  }

  public final boolean isMissedVideoCall() {
    return MessageTypes.isMissedVideoCall(type);
  }

  public final boolean isGroupCall() {
    return MessageTypes.isGroupCall(type);
  }

  public boolean isVerificationStatusChange() {
    return MessageTypes.isIdentityDefault(type) || MessageTypes.isIdentityVerified(type);
  }

  public boolean isProfileChange() {
    return MessageTypes.isProfileChange(type);
  }

  public boolean isChangeNumber() {
    return MessageTypes.isChangeNumber(type);
  }

  public boolean isBoostRequest() {
    return MessageTypes.isBoostRequest(type);
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
    return (deliveryStatus >= Status.STATUS_COMPLETE &&
            deliveryStatus < Status.STATUS_PENDING) || deliveryReceiptCount > 0;
  }

  public boolean isRemoteViewed() {
    return viewReceiptCount > 0;
  }

  public boolean isRemoteRead() {
    return readReceiptCount > 0;
  }

  public boolean isPendingInsecureSmsFallback() {
    return MessageTypes.isPendingInsecureSmsFallbackType(type);
  }

  public boolean isPaymentNotification() {
    return MessageTypes.isPaymentsNotification(type);
  }

  public boolean isPaymentsRequestToActivate() {
    return MessageTypes.isPaymentsRequestToActivate(type);
  }

  public boolean isPaymentsActivated() {
    return MessageTypes.isPaymentsActivated(type);
  }
}
