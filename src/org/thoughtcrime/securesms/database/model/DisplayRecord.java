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

import org.thoughtcrime.securesms.protocol.Prefix;
import org.thoughtcrime.securesms.recipients.Recipients;

/**
 * The base class for all message record models.  Encapsulates basic data
 * shared between ThreadRecord and MessageRecord.
 *
 * @author Moxie Marlinspike
 *
 */

public abstract class DisplayRecord {

  private final Recipients recipients;
  private final long dateSent;
  private final long dateReceived;
  private final long threadId;

  private String body;
  protected boolean emphasis;
  protected boolean keyExchange;
  protected boolean processedKeyExchange;
  protected boolean staleKeyExchange;

  public DisplayRecord(Recipients recipients, long dateSent, long dateReceived, long threadId) {
    this.threadId     = threadId;
    this.recipients   = recipients;
    this.dateSent     = dateSent;
    this.dateReceived = dateReceived;
    this.emphasis     = false;
  }

  public void setEmphasis(boolean emphasis) {
    this.emphasis = emphasis;
  }

  public boolean getEmphasis() {
    return emphasis;
  }

  public void setBody(String body) {
    if (body.startsWith(Prefix.KEY_EXCHANGE)) {
      this.keyExchange = true;
      this.emphasis    = true;
      this.body        = body;
    } else if (body.startsWith(Prefix.PROCESSED_KEY_EXCHANGE)) {
      this.processedKeyExchange = true;
      this.emphasis             = true;
      this.body                 = body;
    } else if (body.startsWith(Prefix.STALE_KEY_EXCHANGE)) {
      this.staleKeyExchange = true;
      this.emphasis         = true;
      this.body             = body;
    } else {
      this.body     = body;
      this.emphasis = false;
    }
  }

  public String getBody() {
    return body;
  }

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
    return keyExchange || processedKeyExchange || staleKeyExchange;
  }

}
