package org.thoughtcrime.securesms;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.content.AsyncTaskLoader;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.GroupUtil;

import java.io.IOException;
import java.util.LinkedList;

public class MessageDetailsRecipientLoader extends AsyncTaskLoader<Pair<MessageRecord, Recipients>> {
  private static final String TAG = MessageDetailsRecipientLoader.class.getSimpleName();

  private MasterSecret                    masterSecret;
  private String                          type;
  private long                            messageId;
  private Pair<MessageRecord, Recipients> data;

  public MessageDetailsRecipientLoader(Context context, MasterSecret masterSecret, String type, long messageId) {
    super(context);
    this.masterSecret = masterSecret;
    this.type         = type;
    this.messageId    = messageId;
  }

  @Override
  public void deliverResult(Pair<MessageRecord, Recipients> recipients) {
    if (isReset()) {
      return;
    }

    if (isStarted()) {
      super.deliverResult(recipients);
    }
  }

  @Override
  protected void onStartLoading() {
    if (data != null) {
      deliverResult(data);
    }

    if (takeContentChanged() || data == null) {
      forceLoad();
    }
  }

  @Override
  protected void onStopLoading() {
    cancelLoad();
  }

  @Override
  protected void onReset() {
    onStopLoading();

    if (data != null) data = null;
    super.onReset();
  }

  @Override
  public Pair<MessageRecord, Recipients> loadInBackground() {
    try {
      Log.w(TAG, "loadInBackground()");
      Recipients recipients;
      MessageRecord messageRecord;

      switch (type) {
        case MmsSmsDatabase.SMS_TRANSPORT:
          EncryptingSmsDatabase smsDatabase = DatabaseFactory.getEncryptingSmsDatabase(getContext());
          messageRecord = smsDatabase.getMessage(masterSecret, messageId);
          break;
        case MmsSmsDatabase.MMS_TRANSPORT:
          MmsDatabase mmsDatabase = DatabaseFactory.getMmsDatabase(getContext());
          MmsDatabase.Reader mmsReader = mmsDatabase.readerFor(masterSecret, mmsDatabase.getMessage(messageId));
          messageRecord = mmsReader.getNext();
          break;
        default:
          throw new AssertionError("no valid message type specified");
      }

      final Recipients intermediaryRecipients;
      if (messageRecord.isMms()) {
        intermediaryRecipients = DatabaseFactory.getMmsAddressDatabase(getContext()).getRecipientsForId(messageRecord.getId());
      } else {
        intermediaryRecipients = messageRecord.getRecipients();
      }

      if (!intermediaryRecipients.isGroupRecipient()) {
        Log.w(TAG, "Recipient is not a group, resolving members immediately.");
        recipients = intermediaryRecipients;
      } else {
        try {
          String groupId = intermediaryRecipients.getPrimaryRecipient().getNumber();
          recipients = DatabaseFactory.getGroupDatabase(getContext())
                                      .getGroupMembers(GroupUtil.getDecodedId(groupId), false);
        } catch (IOException e) {
          Log.w(TAG, e);
         recipients = new Recipients(new LinkedList<Recipient>());
        }
      }

      return new Pair<>(messageRecord, recipients);
    } catch (NoSuchMessageException nsme) {
      Log.w(TAG, nsme);
      return null;
    }
  }
}
