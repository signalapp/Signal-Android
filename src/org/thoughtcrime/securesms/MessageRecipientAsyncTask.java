package org.thoughtcrime.securesms;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;
import android.util.Pair;

import org.thoughtcrime.securesms.MessageRecipientAsyncTask.Result;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.GroupUtil;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.LinkedList;

public abstract class MessageRecipientAsyncTask extends AsyncTask<Void,Void,Result> {
  private static final String TAG = MessageRecipientAsyncTask.class.getSimpleName();

  private WeakReference<Context> weakContext;
  private MasterSecret           masterSecret;
  private String                 type;
  private long                   messageId;

  public MessageRecipientAsyncTask(Context context, MasterSecret masterSecret, String type, long messageId) {
    this.weakContext  = new WeakReference<>(context);
    this.masterSecret = masterSecret;
    this.type         = type;
    this.messageId    = messageId;
  }

  protected Context getContext() {
    return weakContext.get();
  }

  private Cursor getCursor(Context context, String type, long messageId) {
    switch (type) {
      case MmsSmsDatabase.SMS_TRANSPORT:
        return DatabaseFactory.getEncryptingSmsDatabase(context).getMessage(messageId);
      case MmsSmsDatabase.MMS_TRANSPORT:
        return DatabaseFactory.getMmsDatabase(context).getMessage(messageId);
      default:
        throw new AssertionError("no valid message type specified");
    }
  }

  private MessageRecord getMessageRecord(Context context, Cursor cursor, String type) {
    switch (type) {
      case MmsSmsDatabase.SMS_TRANSPORT:
        EncryptingSmsDatabase smsDatabase = DatabaseFactory.getEncryptingSmsDatabase(context);
        SmsDatabase.Reader    reader      = smsDatabase.readerFor(masterSecret, cursor);
        return reader.getNext();
      case MmsSmsDatabase.MMS_TRANSPORT:
        MmsDatabase        mmsDatabase = DatabaseFactory.getMmsDatabase(context);
        MmsDatabase.Reader mmsReader   = mmsDatabase.readerFor(masterSecret, cursor);
        return mmsReader.getNext();
      default:
        throw new AssertionError("no valid message type specified");
    }
  }

  @Override
  public Result doInBackground(Void... voids) {
    Context context = getContext();
    if (context == null) {
      Log.w(TAG, "associated context is destroyed, finishing early");
    }

    Cursor        cursor        = getCursor(context, type, messageId);
    MessageRecord messageRecord = getMessageRecord(context, cursor, type);
    Recipients    recipients;

    final Recipients intermediaryRecipients;
    if (messageRecord.isMms()) {
      intermediaryRecipients = DatabaseFactory.getMmsAddressDatabase(context).getRecipientsForId(messageRecord.getId());
    } else {
      intermediaryRecipients = messageRecord.getRecipients();
    }

    if (!intermediaryRecipients.isGroupRecipient()) {
      Log.w(TAG, "Recipient is not a group, resolving members immediately.");
      recipients = intermediaryRecipients;
    } else {
      try {
        String groupId = intermediaryRecipients.getPrimaryRecipient().getNumber();
        recipients = DatabaseFactory.getGroupDatabase(context)
                                    .getGroupMembers(GroupUtil.getDecodedId(groupId), false);
      } catch (IOException e) {
        Log.w(TAG, e);
       recipients = new Recipients(new LinkedList<Recipient>());
      }
    }

    return new Result(messageRecord, recipients, cursor);
  }

  public static class Result {
    public MessageRecord messageRecord;
    public Recipients    recipients;
    public Cursor        cursor;

    public Result(MessageRecord messageRecord, Recipients recipients, Cursor cursor) {
      this.messageRecord = messageRecord;
      this.recipients = recipients;
      this.cursor = cursor;
    }
  }
}
