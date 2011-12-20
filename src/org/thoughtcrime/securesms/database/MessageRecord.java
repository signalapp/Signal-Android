/** 
 * Copyright (C) 2011 Whisper Systems
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
package org.thoughtcrime.securesms.database;

import org.thoughtcrime.securesms.ConversationItem;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;

public class MessageRecord {
	
  private long       id;
  private long       threadId;
  private Recipient  messageRecipient;
  private Recipients recipients;
  private String     body;
  private long       date;
  private long       count;
  private boolean    read;
  private long       type;
  private boolean    emphasis;
  private boolean    keyExchange;
  private boolean    processedKeyExchange;
  private boolean    staleKeyExchange;
	
  public MessageRecord(MessageRecord copy) {
    this.id                   = copy.id;
    this.threadId             = copy.threadId;
    this.messageRecipient     = copy.messageRecipient;
    this.recipients           = copy.recipients;
    this.body                 = copy.body;
    this.date                 = copy.date;
    this.count                = copy.count;
    this.read                 = copy.read;
    this.type                 = copy.type;
    this.emphasis             = copy.emphasis;
    this.keyExchange          = copy.keyExchange;
    this.processedKeyExchange = copy.processedKeyExchange;
  }
	
  public MessageRecord(long id, Recipients recipients, long date, long type, long threadId) {
    this.id          = id;
    this.date        = date;
    this.type        = type;
    this.recipients  = recipients;
    this.threadId    = threadId;
  }
	
  public MessageRecord(long id, Recipients recipients, long date, long count, boolean read, long threadId) {
    this.id         = id;
    this.threadId   = threadId;
    this.recipients  = recipients;
    this.date       = date;
    this.count      = count;
    this.read       = read;
  }
	
  public void setOnConversationItem(ConversationItem item) {
    item.setMessageRecord(this);
  }
	
  public boolean isMms() {
    return false;
  }
	
  public long getType() {
    return type;
  }
	
  public void setMessageRecipient(Recipient recipient) {
    this.messageRecipient = recipient;
  }
	
  public Recipient getMessageRecipient() {
    return this.messageRecipient;
  }
	
  public void setEmphasis(boolean emphasis) {
    this.emphasis = emphasis;
  }
	
  public boolean getEmphasis() {
    return this.emphasis;
  }
	
  public void setId(long id) {
    this.id = id;
  }
	
  public void setBody(String body) {
    this.body = body;
  }
	
  public long getThreadId() {
    return threadId;
  }
	
  public long getId() {
    return id;
  }
	
  public Recipients getRecipients() {
    return recipients;
  }
	
  public String getBody() {
    return body;
  }
	
  public long getDate() {
    return date;
  }
	
  public long getCount() {
    return count;
  }
	
  public boolean getRead() {
    return read;
  }
	
  public boolean isStaleKeyExchange() {
    return this.staleKeyExchange;
  }
	
  public void setStaleKeyExchange(boolean staleKeyExchange) {
    this.staleKeyExchange = staleKeyExchange;
  }
	
  public boolean isProcessedKeyExchange() {
    return processedKeyExchange;
  }
	
  public void setProcessedKeyExchange(boolean processedKeyExchange) {
    this.processedKeyExchange = processedKeyExchange;
  }
	
  public boolean isKeyExchange() {
    return keyExchange || processedKeyExchange || staleKeyExchange;
  }
	
  public void setKeyExchange(boolean keyExchange) {
    this.keyExchange = keyExchange;
  }
	
  public boolean isFailedDecryptType() {
    return type == SmsDatabase.Types.FAILED_DECRYPT_TYPE;
  }
	
  public boolean isFailed() {
    return SmsDatabase.Types.isFailedMessageType(type);
  }
	
  public boolean isOutgoing() {
    return SmsDatabase.Types.isOutgoingMessageType(type);
  }
	
  public boolean isPending() {
    return SmsDatabase.Types.isPendingMessageType(type);
  }
	
  public boolean isSecure() {
    return SmsDatabase.Types.isSecureType(type);
  }

	
	
}
