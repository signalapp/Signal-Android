package org.thoughtcrime.securesms;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.thoughtcrime.securesms.crypto.IdentityKeyParcelable;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.PushDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.jobs.PushDecryptJob;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.Base64;
import org.whispersystems.textsecure.api.messages.TextSecureEnvelope;
import org.whispersystems.textsecure.internal.push.PushMessageProtos;

import java.io.IOException;

public class ConfirmIdentityDialog extends AlertDialog {

  private static final String TAG = ConfirmIdentityDialog.class.getSimpleName();

  private OnClickListener callback;

  public ConfirmIdentityDialog(Context context,
                               MasterSecret masterSecret,
                               MessageRecord messageRecord,
                               IdentityKeyMismatch mismatch)
  {
    super(context);
    Recipient       recipient       = RecipientFactory.getRecipientForId(context, mismatch.getRecipientId(), false);
    String          name            = recipient.toShortString();
    String          introduction    = String.format(context.getString(R.string.ConfirmIdentityDialog_the_signature_on_this_key_exchange_is_different), name, name);
    SpannableString spannableString = new SpannableString(introduction + " " +
                                                          context.getString(R.string.ConfirmIdentityDialog_you_may_wish_to_verify_this_contact));

    spannableString.setSpan(new VerifySpan(context, masterSecret, mismatch),
                            introduction.length()+1, spannableString.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

    setTitle(name);
    setMessage(spannableString);

    setButton(AlertDialog.BUTTON_POSITIVE, "Accept", new AcceptListener(masterSecret, messageRecord, mismatch));
    setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel", new CancelListener());
  }

  @Override
  public void show() {
    super.show();
    ((TextView)this.findViewById(android.R.id.message))
                   .setMovementMethod(LinkMovementMethod.getInstance());
  }

  public void setCallback(OnClickListener callback) {
    this.callback = callback;
  }

  private class AcceptListener implements OnClickListener {

    private final MasterSecret        masterSecret;
    private final MessageRecord       messageRecord;
    private final IdentityKeyMismatch mismatch;

    private AcceptListener(MasterSecret masterSecret, MessageRecord messageRecord, IdentityKeyMismatch mismatch) {
      this.masterSecret  = masterSecret;
      this.messageRecord = messageRecord;
      this.mismatch      = mismatch;
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {

          if (messageRecord.isOutgoing()) processOutgoingMessage(messageRecord);
          else                            Log.w(TAG, "Process incoming message hasn't moved over yet!");

          return null;
        }
      }.execute();

      if (callback != null) callback.onClick(null, 0);
    }

    private void processOutgoingMessage(MessageRecord messageRecord) {
      SmsDatabase smsDatabase = DatabaseFactory.getSmsDatabase(getContext());
      MmsDatabase mmsDatabase = DatabaseFactory.getMmsDatabase(getContext());

      if (messageRecord.isMms()) {
        mmsDatabase.removeMismatchedIdentity(messageRecord.getId(),
                                             mismatch.getRecipientId(),
                                             mismatch.getIdentityKey());
      } else {
        smsDatabase.removeMismatchedIdentity(messageRecord.getId(),
                                             mismatch.getRecipientId(),
                                             mismatch.getIdentityKey());
      }

      MessageSender.resend(getContext(), masterSecret, messageRecord);
    }
  }

  private class CancelListener implements OnClickListener {
    @Override
    public void onClick(DialogInterface dialog, int which) {
      if (callback != null) callback.onClick(null, 0);
    }
  }

  private static class VerifySpan extends ClickableSpan {
    private final Context             context;
    private final MasterSecret        masterSecret;
    private final IdentityKeyMismatch mismatch;

    private VerifySpan(Context context, MasterSecret masterSecret, IdentityKeyMismatch mismatch) {
      this.context      = context;
      this.masterSecret = masterSecret;
      this.mismatch     = mismatch;
    }

    @Override
    public void onClick(View widget) {
      Intent intent = new Intent(context, VerifyIdentityActivity.class);
      intent.putExtra("recipient", mismatch.getRecipientId());
      intent.putExtra("master_secret", masterSecret);
      intent.putExtra("remote_identity", new IdentityKeyParcelable(mismatch.getIdentityKey()));
      context.startActivity(intent);
    }
  }

}
