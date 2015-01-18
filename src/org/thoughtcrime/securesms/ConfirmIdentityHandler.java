package org.thoughtcrime.securesms;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.documents.NetworkFailure;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.model.MessageRecord;

import java.util.LinkedList;
import java.util.List;

public class ConfirmIdentityHandler implements View.OnClickListener, Dialog.OnClickListener {

  private final Context       context;
  private final MasterSecret  masterSecret;
  private final MessageRecord messageRecord;

  private final List<IdentityKeyMismatch> identityFailures = new LinkedList<>();
  private final List<NetworkFailure>      networkFailures  = new LinkedList<>();

  private int mismatchIndex = 0;

  public ConfirmIdentityHandler(Context context, MasterSecret masterSecret, MessageRecord messageRecord)
  {
    this.context       = context;
    this.masterSecret  = masterSecret;
    this.messageRecord = messageRecord;

    if (messageRecord.hasNetworkFailures()) {
      networkFailures.addAll(messageRecord.getNetworkFailures());
    }

    if (messageRecord.isIdentityMismatchFailure()) {
      identityFailures.addAll(messageRecord.getIdentityKeyMismatches());
    }
  }

  @Override
  public void onClick(View v) {
    mismatchIndex = 0;
    onClick(null, 0);
  }

  @Override
  public void onClick(DialogInterface dialog, int which) {
    if (mismatchIndex < identityFailures.size()) {
      showConfirmIdentityDialogFor(mismatchIndex++);
    } else if (mismatchIndex < identityFailures.size() + networkFailures.size()) {
      showRetryNetworkDialogFor(mismatchIndex++ - identityFailures.size());
    }
  }

  private void showConfirmIdentityDialogFor(int index) {
    IdentityKeyMismatch   mismatch = identityFailures.get(index);
    ConfirmIdentityDialog dialog   = new ConfirmIdentityDialog(context, masterSecret, messageRecord, mismatch);
    dialog.setCallback(this);

    dialog.show();
  }

  private void showRetryNetworkDialogFor(int index) {
    NetworkFailure failure = networkFailures.get(index);
    NetworkFailureRetryDialog dialog = new NetworkFailureRetryDialog(context, masterSecret, messageRecord, failure);
    dialog.setCallback(this);

    dialog.show();
  }

}
