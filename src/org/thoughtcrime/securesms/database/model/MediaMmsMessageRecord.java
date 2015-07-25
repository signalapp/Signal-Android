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
import android.util.Log;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.mms.MediaNotFoundException;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.FutureTaskListener;
import org.thoughtcrime.securesms.util.ListenableFutureTask;

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Represents the message record model for MMS messages that contain
 * media (ie: they've been downloaded).
 *
 * @author Moxie Marlinspike
 *
 */

public class MediaMmsMessageRecord extends MessageRecord {
  private final static String TAG = MediaMmsMessageRecord.class.getSimpleName();

  private final Context context;
  private final int partCount;
  private final ListenableFutureTask<SlideDeck> slideDeckFutureTask;

  public MediaMmsMessageRecord(Context context, long id, Recipients recipients,
                               Recipient individualRecipient, int recipientDeviceId,
                               long dateSent, long dateReceived, int deliveredCount,
                               long threadId, Body body,
                               ListenableFutureTask<SlideDeck> slideDeck,
                               int partCount, long mailbox,
                               List<IdentityKeyMismatch> mismatches,
                               List<NetworkFailure> failures)
  {
    super(context, id, body, recipients, individualRecipient, recipientDeviceId,
          dateSent, dateReceived, threadId, DELIVERY_STATUS_NONE, deliveredCount, mailbox,
          mismatches, failures);

    this.context             = context.getApplicationContext();
    this.partCount           = partCount;
    this.slideDeckFutureTask = slideDeck;
  }

  public ListenableFutureTask<SlideDeck> getSlideDeckFuture() {
    return slideDeckFutureTask;
  }

  private SlideDeck getSlideDeckSync() {
    try {
      return slideDeckFutureTask.get();
    } catch (InterruptedException e) {
      Log.w(TAG, e);
      return null;
    } catch (ExecutionException e) {
      Log.w(TAG, e);
      return null;
    }
  }

  public boolean containsMediaSlide() {
    SlideDeck deck = getSlideDeckSync();
    return deck != null && deck.containsMediaSlide();
  }


  public void fetchMediaSlide(final FutureTaskListener<Slide> listener) {
    slideDeckFutureTask.addListener(new FutureTaskListener<SlideDeck>() {
      @Override
      public void onSuccess(SlideDeck deck) {
        for (Slide slide : deck.getSlides()) {
          if (slide.hasImage() || slide.hasVideo() || slide.hasAudio()) {
            listener.onSuccess(slide);
            return;
          }
        }
        listener.onFailure(new MediaNotFoundException("no media slide found"));
      }

      @Override
      public void onFailure(Throwable error) {
        listener.onFailure(error);
      }
    });
  }

  public int getPartCount() {
    return partCount;
  }

  @Override
  public boolean isMms() {
    return true;
  }

  @Override
  public boolean isMmsNotification() {
    return false;
  }

  @Override
  public SpannableString getDisplayBody() {
    if (MmsDatabase.Types.isDecryptInProgressType(type)) {
      return emphasisAdded(context.getString(R.string.MmsMessageRecord_decrypting_mms_please_wait));
    } else if (MmsDatabase.Types.isFailedDecryptType(type)) {
      return emphasisAdded(context.getString(R.string.MmsMessageRecord_bad_encrypted_mms_message));
    } else if (MmsDatabase.Types.isDuplicateMessageType(type)) {
      return emphasisAdded(context.getString(R.string.SmsMessageRecord_duplicate_message));
    } else if (MmsDatabase.Types.isNoRemoteSessionType(type)) {
      return emphasisAdded(context.getString(R.string.MmsMessageRecord_mms_message_encrypted_for_non_existing_session));
    } else if (isLegacyMessage()) {
      return emphasisAdded(context.getString(R.string.MessageRecord_message_encrypted_with_a_legacy_protocol_version_that_is_no_longer_supported));
    } else if (!getBody().isPlaintext()) {
      return emphasisAdded(context.getString(R.string.MessageNotifier_locked_message));
    }

    return super.getDisplayBody();
  }
}
