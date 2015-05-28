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
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.recipients.Recipients;

import de.gdata.messaging.util.GUtil;

/**
 * The base class for all message record models.  Encapsulates basic data
 * shared between ThreadRecord and MessageRecord.
 *
 * @author Moxie Marlinspike
 *
 */

public abstract class DisplayRecord {

  protected final Context context;
  public final long type;

  private final Recipients recipients;
  private final long       dateSent;
  private final long       dateReceived;
  private final long       threadId;
  private final Body       body;

  public static final int SELF_DESTRUCTION_DISABLED   = 0;

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
    getBody();
  }

  public Body getBody() {
    body.getParsedBody();
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

  public static class Body {
    private final String body;
    private final boolean plaintext;

    private int selfDestroyDuration = SELF_DESTRUCTION_DISABLED;

    public Body(String body, boolean plaintext) {
      this.body      = body;
      this.plaintext = plaintext;
    }

    public boolean isPlaintext() {
      return plaintext;
    }

    public String getParsedBody() {
      String newBody = body == null ? "" : body;
      String[] parsed = newBody.split(GUtil.DESTROY_FLAG);
      if(parsed != null && parsed.length > 1) {
        try {
          selfDestroyDuration = Integer.parseInt(parsed[1].trim());
        } catch(NumberFormatException nfe){}
          newBody = parsed[0];
       }
      return newBody;
    }
    public String getBody() {
      return body == null ? "" : body;
    }

    public boolean isSelfDestruction() {
      return selfDestroyDuration != SELF_DESTRUCTION_DISABLED;
    }
    public int getSelfDestructionDuration() {
      return selfDestroyDuration;
    }
  }
}
