/**
 * Copyright (C) 2013-2014 Open WhisperSystems
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
package org.thoughtcrime.securesms.sms;

import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;

public class OutgoingTextMessage {

  private final Recipients recipients;
  private final String     message;

  public OutgoingTextMessage(Recipient recipient, String message) {
    this(new Recipients(recipient), message);
  }

  public OutgoingTextMessage(Recipients recipients, String message) {
    this.recipients = recipients;
    this.message    = message;
  }

  protected OutgoingTextMessage(OutgoingTextMessage base, String body) {
    this.recipients = base.getRecipients();
    this.message    = body;
  }

  public String getMessageBody() {
    return message;
  }

  public Recipients getRecipients() {
    return recipients;
  }

  public boolean isKeyExchange() {
    return false;
  }

  public boolean isSecureMessage() {
    return false;
  }

  public boolean isEndSession() {
    return false;
  }

  public boolean isPreKeyBundle() {
    return false;
  }

  public static OutgoingTextMessage from(SmsMessageRecord record) {
    if (record.isSecure()) {
      return new OutgoingEncryptedMessage(record.getIndividualRecipient(), record.getBody().getBody());
    } else if (record.isKeyExchange()) {
      return new OutgoingKeyExchangeMessage(record.getIndividualRecipient(), record.getBody().getBody());
    } else if (record.isEndSession()) {
      return new OutgoingEndSessionMessage(new OutgoingTextMessage(record.getIndividualRecipient(), record.getBody().getBody()));
    } else {
      return new OutgoingTextMessage(record.getIndividualRecipient(), record.getBody().getBody());
    }
  }

  public OutgoingTextMessage withBody(String body) {
    return new OutgoingTextMessage(this, body);
  }
}
