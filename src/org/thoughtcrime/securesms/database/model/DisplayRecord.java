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

import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.recipients.Recipients;

/**
 * The base class for all message record models.  Encapsulates basic data
 * shared between ThreadRecord and MessageRecord.
 *
 * @author Moxie Marlinspike
 *
 */

public abstract class DisplayRecord {

  protected final Context context;
  protected final long type;

  private final Recipients recipients;
  private final long       dateSent;
  private final long       dateReceived;
  private final long       threadId;
  private final Body       body;

  public DisplayRecord(Context context, Body body, Recipients recipients, long dateSent,
                       long dateReceived, long threadId, long type)
  {
    this.context              = context.getApplicationContext();
    this.threadId             = threadId;
    this.recipients           = recipients;
    this.dateSent             = dateSent;
    this.dateReceived         = dateReceived;
    this.type                 = type;
    this.body                 = body;
  }

  public Body getBody() {
    return body;
  }

  public abstract SpannableString getDisplayBody();

  public Recipients getRecipients() {
    return recipients;
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

  public boolean isGroupQuit() {
    return SmsDatabase.Types.isGroupQuit(type);
  }

  public boolean isGroupAction() {
    return isGroupUpdate() || isGroupQuit();
  }

  public boolean isCallLog() {
    return SmsDatabase.Types.isCallLog(type);
  }

  public boolean isIncomingCall() {
    return SmsDatabase.Types.isIncomingCall(type);
  }

  public boolean isOutgoingCall() {
    return SmsDatabase.Types.isOutgoingCall(type);
  }

  public boolean isMissedCall() {
    return SmsDatabase.Types.isMissedCall(type);
  }

  public static class Body {
    private final String body;
    private final boolean plaintext;

    public Body(String body, boolean plaintext) {
      this.body      = body;
      this.plaintext = plaintext;
    }

    public boolean isPlaintext() {
      return plaintext;
    }

    public String getBody() {
      return body == null ? "" : body;
    }
  }
}
