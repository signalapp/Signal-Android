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

import androidx.annotation.NonNull;

import org.session.libsession.messaging.sending_receiving.data_extraction.DataExtractionNotificationInfoMessage;
import org.session.libsession.messaging.utilities.UpdateMessageBuilder;
import org.session.libsession.messaging.utilities.UpdateMessageData;
import org.session.libsession.utilities.IdentityKeyMismatch;
import org.session.libsession.utilities.NetworkFailure;
import org.session.libsession.utilities.recipients.Recipient;

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
  private final List<IdentityKeyMismatch> mismatches;
  private final List<NetworkFailure>      networkFailures;
  private final long                      expiresIn;
  private final long                      expireStarted;
  private final boolean                   unidentified;
  public  final long                      id;

  public abstract boolean isMms();
  public abstract boolean isMmsNotification();

  MessageRecord(long id, String body, Recipient conversationRecipient,
    Recipient individualRecipient,
    long dateSent, long dateReceived, long threadId,
    int deliveryStatus, int deliveryReceiptCount, long type,
    List<IdentityKeyMismatch> mismatches,
    List<NetworkFailure> networkFailures,
    long expiresIn, long expireStarted,
    int readReceiptCount, boolean unidentified)
  {
    super(body, conversationRecipient, dateSent, dateReceived,
      threadId, deliveryStatus, deliveryReceiptCount, type, readReceiptCount);
    this.id                  = id;
    this.individualRecipient = individualRecipient;
    this.mismatches          = mismatches;
    this.networkFailures     = networkFailures;
    this.expiresIn           = expiresIn;
    this.expireStarted       = expireStarted;
    this.unidentified        = unidentified;
  }

  public long getId() {
    return id;
  }
  public long getTimestamp() {
    return getDateSent();
  }
  public Recipient getIndividualRecipient() {
    return individualRecipient;
  }
  public long getType() {
    return type;
  }
  public List<NetworkFailure> getNetworkFailures() {
    return networkFailures;
  }
  public long getExpiresIn() {
    return expiresIn;
  }
  public long getExpireStarted() { return expireStarted; }

  public boolean isMediaPending() {
    return false;
  }

  public boolean isUpdate() {
    return isExpirationTimerUpdate() || isCallLog() || isDataExtractionNotification();
  }

  @Override
  public SpannableString getDisplayBody(@NonNull Context context) {
    if (isGroupUpdateMessage()) {
      UpdateMessageData updateMessageData = UpdateMessageData.Companion.fromJSON(getBody());
      return new SpannableString(UpdateMessageBuilder.INSTANCE.buildGroupUpdateMessage(context, updateMessageData, getIndividualRecipient().getAddress().serialize(), isOutgoing()));
    } else if (isExpirationTimerUpdate()) {
      int seconds = (int) (getExpiresIn() / 1000);
      return new SpannableString(UpdateMessageBuilder.INSTANCE.buildExpirationTimerMessage(context, seconds, getIndividualRecipient().getAddress().serialize(), isOutgoing()));
    } else if (isDataExtractionNotification()) {
      if (isScreenshotNotification()) return new SpannableString((UpdateMessageBuilder.INSTANCE.buildDataExtractionMessage(context, DataExtractionNotificationInfoMessage.Kind.SCREENSHOT, getIndividualRecipient().getAddress().serialize())));
      else if (isMediaSavedNotification()) return new SpannableString((UpdateMessageBuilder.INSTANCE.buildDataExtractionMessage(context, DataExtractionNotificationInfoMessage.Kind.MEDIA_SAVED, getIndividualRecipient().getAddress().serialize())));
    }

    return new SpannableString(getBody());
  }

  protected SpannableString emphasisAdded(String sequence) {
    SpannableString spannable = new SpannableString(sequence);
    spannable.setSpan(new RelativeSizeSpan(0.9f), 0, sequence.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    spannable.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC), 0, sequence.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

    return spannable;
  }

  public boolean equals(Object other) {
    return other instanceof MessageRecord
      && ((MessageRecord) other).getId() == getId()
      && ((MessageRecord) other).isMms() == isMms();
  }

  public int hashCode() {
    return (int)getId();
  }
}
