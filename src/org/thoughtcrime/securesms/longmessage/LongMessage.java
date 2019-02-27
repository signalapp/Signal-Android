package org.thoughtcrime.securesms.longmessage;

import org.thoughtcrime.securesms.database.model.MessageRecord;

/**
 * A wrapper around a {@link MessageRecord} and its extra text attachment expanded into a string
 * held in memory.
 */
class LongMessage {

  private final MessageRecord messageRecord;
  private final String        extraBody;

  LongMessage(MessageRecord messageRecord, String extraBody) {
    this.messageRecord = messageRecord;
    this.extraBody     = extraBody;
  }

  MessageRecord getMessageRecord() {
    return messageRecord;
  }

  String getFullBody() {
    return messageRecord.getBody() + extraBody;
  }
}
