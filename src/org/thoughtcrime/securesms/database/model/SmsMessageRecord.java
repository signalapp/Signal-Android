/**
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

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;

import java.util.LinkedList;
import java.util.List;

/**
 * The message record model which represents standard SMS messages.
 *
 * @author Moxie Marlinspike
 *
 */

public class SmsMessageRecord extends MessageRecord {

  public SmsMessageRecord(Context context, long id,
                          Body body, Recipients recipients,
                          Recipient individualRecipient,
                          int recipientDeviceId,
                          long dateSent, long dateReceived,
                          int receiptCount,
                          long type, long threadId,
                          int status, List<IdentityKeyMismatch> mismatches,
                          int subscriptionId, long expiresIn, long expireStarted)
  {
    super(context, id, body, recipients, individualRecipient, recipientDeviceId,
          dateSent, dateReceived, threadId, status, receiptCount, type,
          mismatches, new LinkedList<NetworkFailure>(), subscriptionId,
          expiresIn, expireStarted);
  }

  public long getType() {
    return type;
  }

  @Override
  public SpannableString getDisplayBody() {
    if (SmsDatabase.Types.isFailedDecryptType(type)) {
      return emphasisAdded(context.getString(R.string.MessageDisplayHelper_bad_encrypted_message));
    } else if (isProcessedKeyExchange()) {
      return new SpannableString("");
    } else if (isStaleKeyExchange()) {
      return emphasisAdded(context.getString(R.string.ConversationItem_error_received_stale_key_exchange_message));
    } else if (isCorruptedKeyExchange()) {
      return emphasisAdded(context.getString(R.string.SmsMessageRecord_received_corrupted_key_exchange_message));
    } else if (isInvalidVersionKeyExchange()) {
      return emphasisAdded(context.getString(R.string.SmsMessageRecord_received_key_exchange_message_for_invalid_protocol_version));
    } else if (MmsSmsColumns.Types.isLegacyType(type)) {
      return emphasisAdded(context.getString(R.string.MessageRecord_message_encrypted_with_a_legacy_protocol_version_that_is_no_longer_supported));
    } else if (isBundleKeyExchange()) {
      return emphasisAdded(context.getString(R.string.SmsMessageRecord_received_message_with_new_safety_numbers_tap_to_process));
    } else if (isKeyExchange() && isOutgoing()) {
      return new SpannableString("");
    } else if (isKeyExchange() && !isOutgoing()) {
      return emphasisAdded(context.getString(R.string.ConversationItem_received_key_exchange_message_tap_to_process));
    } else if (SmsDatabase.Types.isDuplicateMessageType(type)) {
      return emphasisAdded(context.getString(R.string.SmsMessageRecord_duplicate_message));
    } else if (SmsDatabase.Types.isDecryptInProgressType(type)) {
      return emphasisAdded(context.getString(R.string.MessageDisplayHelper_decrypting_please_wait));
    } else if (SmsDatabase.Types.isNoRemoteSessionType(type)) {
      return emphasisAdded(context.getString(R.string.MessageDisplayHelper_message_encrypted_for_non_existing_session));
    } else if (!getBody().isPlaintext()) {
      return emphasisAdded(context.getString(R.string.MessageNotifier_locked_message));
    } else if (isEndSession() && isOutgoing()) {
      return emphasisAdded(context.getString(R.string.SmsMessageRecord_secure_session_reset));
    } else if (isEndSession()) {
      return emphasisAdded(context.getString(R.string.SmsMessageRecord_secure_session_reset_s, getIndividualRecipient().toShortString()));
    } else {
      return super.getDisplayBody();
    }
  }

  @Override
  public boolean isMms() {
    return false;
  }

  @Override
  public boolean isMmsNotification() {
    return false;
  }
}
