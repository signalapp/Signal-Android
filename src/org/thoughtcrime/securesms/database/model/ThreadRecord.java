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
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.MmsSmsColumns;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.ExpirationUtil;
import org.thoughtcrime.securesms.util.GroupUtil;

/**
 * The message record model which represents thread heading messages.
 *
 * @author Moxie Marlinspike
 *
 */
public class ThreadRecord extends DisplayRecord {

  private @NonNull  final Context context;
  private @Nullable final Uri     snippetUri;
  private           final long    count;
  private           final boolean read;
  private           final int     distributionType;
  private           final boolean archived;
  private           final long    expiresIn;

  public ThreadRecord(@NonNull Context context, @NonNull Body body, @Nullable Uri snippetUri,
                      @NonNull Recipients recipients, long date, long count, boolean read,
                      long threadId, int receiptCount, int status, long snippetType,
                      int distributionType, boolean archived, long expiresIn)
  {
    super(context, body, recipients, date, date, threadId, status, receiptCount, snippetType);
    this.context          = context.getApplicationContext();
    this.snippetUri       = snippetUri;
    this.count            = count;
    this.read             = read;
    this.distributionType = distributionType;
    this.archived         = archived;
    this.expiresIn        = expiresIn;
  }

  public @Nullable Uri getSnippetUri() {
    return snippetUri;
  }

  @Override
  public SpannableString getDisplayBody() {
    if (SmsDatabase.Types.isDecryptInProgressType(type)) {
      return emphasisAdded(context.getString(R.string.MessageDisplayHelper_decrypting_please_wait));
    } else if (isGroupUpdate()) {
      return emphasisAdded(GroupUtil.getDescription(context, getBody().getBody()).toString());
    } else if (isGroupQuit()) {
      return emphasisAdded(context.getString(R.string.ThreadRecord_left_the_group));
    } else if (isKeyExchange()) {
      return emphasisAdded(context.getString(R.string.ConversationListItem_key_exchange_message));
    } else if (SmsDatabase.Types.isFailedDecryptType(type)) {
      return emphasisAdded(context.getString(R.string.MessageDisplayHelper_bad_encrypted_message));
    } else if (SmsDatabase.Types.isNoRemoteSessionType(type)) {
      return emphasisAdded(context.getString(R.string.MessageDisplayHelper_message_encrypted_for_non_existing_session));
    } else if (!getBody().isPlaintext()) {
      return emphasisAdded(context.getString(R.string.MessageNotifier_locked_message));
    } else if (SmsDatabase.Types.isEndSessionType(type)) {
      return emphasisAdded(context.getString(R.string.ThreadRecord_secure_session_reset));
    } else if (MmsSmsColumns.Types.isLegacyType(type)) {
      return emphasisAdded(context.getString(R.string.MessageRecord_message_encrypted_with_a_legacy_protocol_version_that_is_no_longer_supported));
    } else if (MmsSmsColumns.Types.isDraftMessageType(type)) {
      String draftText = context.getString(R.string.ThreadRecord_draft);
      return emphasisAdded(draftText + " " + getBody().getBody(), 0, draftText.length());
    } else if (SmsDatabase.Types.isOutgoingCall(type)) {
      return emphasisAdded(context.getString(org.thoughtcrime.securesms.R.string.ThreadRecord_called));
    } else if (SmsDatabase.Types.isIncomingCall(type)) {
      return emphasisAdded(context.getString(org.thoughtcrime.securesms.R.string.ThreadRecord_called_you));
    } else if (SmsDatabase.Types.isMissedCall(type)) {
      return emphasisAdded(context.getString(org.thoughtcrime.securesms.R.string.ThreadRecord_missed_call));
    } else if (SmsDatabase.Types.isJoinedType(type)) {
      return emphasisAdded(context.getString(R.string.ThreadRecord_s_is_on_signal_say_hey, getRecipients().getPrimaryRecipient().toShortString()));
    } else if (SmsDatabase.Types.isExpirationTimerUpdate(type)) {
      String time = ExpirationUtil.getExpirationDisplayValue(context, (int) (getExpiresIn() / 1000));
      return emphasisAdded(context.getString(R.string.ThreadRecord_disappearing_message_time_updated_to_s, time));
    } else if (SmsDatabase.Types.isIdentityUpdate(type)) {
      return emphasisAdded(context.getString(R.string.ThreadRecord_your_safety_numbers_with_s_have_changed, getRecipients().getPrimaryRecipient().toShortString()));
    } else {
      if (TextUtils.isEmpty(getBody().getBody())) {
        return new SpannableString(emphasisAdded(context.getString(R.string.ThreadRecord_media_message)));
      } else {
        return new SpannableString(getBody().getBody());
      }
    }
  }

  private SpannableString emphasisAdded(String sequence) {
    return emphasisAdded(sequence, 0, sequence.length());
  }

  private SpannableString emphasisAdded(String sequence, int start, int end) {
    SpannableString spannable = new SpannableString(sequence);
    spannable.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC),
                      start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    return spannable;
  }

  public long getCount() {
    return count;
  }

  public boolean isRead() {
    return read;
  }

  public long getDate() {
    return getDateReceived();
  }

  public boolean isArchived() {
    return archived;
  }

  public int getDistributionType() {
    return distributionType;
  }

  public long getExpiresIn() {
    return expiresIn;
  }
}
