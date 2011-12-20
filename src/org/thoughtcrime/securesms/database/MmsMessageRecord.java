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

import java.util.Iterator;
import java.util.List;

import org.thoughtcrime.securesms.ConversationItem;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;

public class MmsMessageRecord extends MessageRecord {

  private SlideDeck slideDeck;
  private byte[] contentLocation;
  private long messageSize;
  private long expiry;
  private boolean isNotification;
  private long mailbox;
  private int status;
  private byte[] transactionId;
	
  public MmsMessageRecord(MessageRecord record, SlideDeck slideDeck, long mailbox) {
    super(record);		
    this.slideDeck      = slideDeck;
    this.isNotification = false;
    this.mailbox        = mailbox;
		
    setBodyIfTextAvailable();
  }

  public MmsMessageRecord(MessageRecord record, byte[] contentLocation, long messageSize, long expiry, int status, byte[] transactionId) {
    super(record);
    this.contentLocation = contentLocation;
    this.messageSize     = messageSize;
    this.expiry          = expiry;
    this.isNotification  = true;
    this.status          = status;
    this.transactionId   = transactionId;
  }
	
  public byte[] getTransactionId() {
    return transactionId;
  }
	
  public int getStatus() {
    return this.status;
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
	
  // This is the double-dispatch pattern, don't refactor
  // this into the base class.
  public void setOnConversationItem(ConversationItem item) {
    item.setMessageRecord(this);
  }
	
  public byte[] getContentLocation() {
    return contentLocation;
  }
	
  public long getMessageSize() {
    return (messageSize + 1023) / 1024;
  }
	
  public long getExpiration() {
    return expiry * 1000;
  }
	
  public boolean isNotification() {
    return isNotification;
  }

  public SlideDeck getSlideDeck() {
    return slideDeck;
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
	
  private void setBodyIfTextAvailable() {
    switch ((int)mailbox) {
    case MmsDatabase.Types.MESSAGE_BOX_DECRYPTING_INBOX:
      setBody("Decrypting MMS, please wait...");
      setEmphasis(true);
      return;
    case MmsDatabase.Types.MESSAGE_BOX_DECRYPT_FAILED_INBOX:
      setBody("Bad encrypted MMS message...");
      setEmphasis(true);
      return;
    case MmsDatabase.Types.MESSAGE_BOX_NO_SESSION_INBOX:
      setBody("MMS message encrypted for non-existing session...");
      setEmphasis(true);
      return;
    }
		
    setBodyFromSlidesIfTextAvailable();
  }
	
  @Override
    public boolean isMms() {
    return true;
  }
	
}
