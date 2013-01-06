/**
 * Copyright (C) 2012 Moxie Marlinpsike
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

import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;

/**
 * The base class for message record models that are displayed in
 * conversations, as opposed to models that are displayed in a thread list.
 * Encapsulates the shared data between both SMS and MMS messages.
 *
 * @author Moxie Marlinspike
 *
 */
public abstract class MessageRecord extends DisplayRecord {

  private final Recipient individualRecipient;
  private final long id;
  private final GroupData groupData;

  public MessageRecord(long id, Recipients recipients,
                       Recipient individualRecipient,
                       long dateSent, long dateReceived,
                       long threadId, GroupData groupData)
  {
    super(recipients, dateSent, dateReceived, threadId);
    this.id                  = id;
    this.individualRecipient = individualRecipient;
    this.groupData           = groupData;
  }

  public abstract boolean isOutgoing();

  public abstract boolean isFailed();

  public abstract boolean isSecure();

  public abstract boolean isPending();

  public abstract boolean isMms();

  public long getId() {
    return id;
  }

  public boolean isStaleKeyExchange() {
    return this.staleKeyExchange;
  }

  public boolean isProcessedKeyExchange() {
    return this.processedKeyExchange;
  }

  public Recipient getIndividualRecipient() {
    return individualRecipient;
  }

  public GroupData getGroupData() {
    return this.groupData;
  }

  protected boolean isGroupDeliveryPending() {
    if (this.groupData != null) {
      return groupData.groupSentCount + groupData.groupSendFailedCount < groupData.groupSize;
    }

    return false;
  }

  public static class GroupData {
    public final int groupSize;
    public final int groupSentCount;
    public final int groupSendFailedCount;

    public GroupData(int groupSize, int groupSentCount, int groupSendFailedCount) {
      this.groupSize            = groupSize;
      this.groupSentCount       = groupSentCount;
      this.groupSendFailedCount = groupSendFailedCount;
    }
  }


}
