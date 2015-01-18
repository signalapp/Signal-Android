package org.thoughtcrime.securesms;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.sms.MessageSender;

public class NetworkFailureRetryDialog extends AlertDialog {

  private OnClickListener callback;

  public NetworkFailureRetryDialog(Context context, MasterSecret masterSecret,
                                   MessageRecord messageRecord, NetworkFailure failure)
  {
    super(context);

    Recipient recipient = RecipientFactory.getRecipientForId(context, failure.getRecipientId(), false);

    setTitle(recipient.toShortString());
    setMessage("A network failure occurred while delivering your message to this contact.  Retry?");

    setButton(AlertDialog.BUTTON_POSITIVE, "Accept", new AcceptListener(masterSecret, messageRecord, failure));
    setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel", new CancelListener());
  }

  public void setCallback(OnClickListener callback) {
    this.callback = callback;
  }

  private class AcceptListener implements OnClickListener {

    private final MasterSecret      masterSecret;
    private final MessageRecord     messageRecord;
    private final NetworkFailure failure;

    private AcceptListener(MasterSecret masterSecret, MessageRecord messageRecord, NetworkFailure failure) {
      this.masterSecret  = masterSecret;
      this.messageRecord = messageRecord;
      this.failure       = failure;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          MmsDatabase mmsDatabase = DatabaseFactory.getMmsDatabase(getContext());
          mmsDatabase.removeFailure(messageRecord.getId(), failure);

          MessageSender.resendGroupMessage(getContext(), masterSecret, messageRecord, failure.getRecipientId());
          return null;
        }
      }.execute();


      if (callback != null) callback.onClick(null, 0);
    }
  }

  private class CancelListener implements OnClickListener {
    @Override
    public void onClick(DialogInterface dialog, int which) {
      if (callback != null) callback.onClick(null, 0);
    }
  }

}
