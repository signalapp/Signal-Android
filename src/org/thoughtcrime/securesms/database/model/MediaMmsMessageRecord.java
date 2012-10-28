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

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;

import java.util.Iterator;
import java.util.List;

/**
 * Represents the message record model for MMS messages that contain
 * media (ie: they've been downloaded).
 *
 * @author Moxie Marlinspike
 *
 */

public class MediaMmsMessageRecord extends MessageRecord {

  private final SlideDeck slideDeck;
  private final long mailbox;

  public MediaMmsMessageRecord(Context context, long id, Recipients recipients,
                               Recipient individualRecipient, long date, long threadId,
                               SlideDeck slideDeck, long mailbox)
  {
    super(id, recipients, individualRecipient, date, threadId);
    this.slideDeck = slideDeck;
    this.mailbox   = mailbox;

    setBodyIfTextAvailable(context);
  }

  @Override
  public boolean isOutgoing() {
    return MmsDatabase.Types.isOutgoingMmsBox(mailbox);
  }

  @Override
  public boolean isPending() {
    return MmsDatabase.Types.isPendingMmsBox(mailbox);
  }

  @Override
  public boolean isFailed() {
    return MmsDatabase.Types.isFailedMmsBox(mailbox);
  }

  @Override
  public boolean isSecure() {
    return MmsDatabase.Types.isSecureMmsBox(mailbox);
  }

  public SlideDeck getSlideDeck() {
    return slideDeck;
  }

  @Override
  public boolean isMms() {
    return true;
  }

  private void setBodyIfTextAvailable(Context context) {
    switch ((int)mailbox) {
    case MmsDatabase.Types.MESSAGE_BOX_DECRYPTING_INBOX:
      setBody(context.getString(R.string.MmsMessageRecord_decrypting_mms_please_wait));
      setEmphasis(true);
      return;
    case MmsDatabase.Types.MESSAGE_BOX_DECRYPT_FAILED_INBOX:
      setBody(context.getString(R.string.MmsMessageRecord_bad_encrypted_mms_message));
      setEmphasis(true);
      return;
    case MmsDatabase.Types.MESSAGE_BOX_NO_SESSION_INBOX:
      setBody(context
        .getString(R.string.MmsMessageRecord_mms_message_encrypted_for_non_existing_session));
      setEmphasis(true);
      return;
    }

    setBodyFromSlidesIfTextAvailable();
  }

  private void setBodyFromSlidesIfTextAvailable() {
    List<Slide> slides = slideDeck.getSlides();
    Iterator<Slide> i = slides.iterator();

    while (i.hasNext()) {
      Slide slide = i.next();

      if (slide.hasText())
        setBody(slide.getText());
    }
  }

}
