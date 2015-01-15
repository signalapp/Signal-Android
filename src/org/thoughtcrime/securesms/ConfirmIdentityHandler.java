package org.thoughtcrime.securesms;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.model.MessageRecord;

import java.util.List;

public class ConfirmIdentityHandler implements View.OnClickListener, Dialog.OnClickListener {

  private final Context       context;
  private final MasterSecret  masterSecret;
  private final MessageRecord messageRecord;

  private int mismatchIndex = 0;

  public ConfirmIdentityHandler(Context context, MasterSecret masterSecret, MessageRecord messageRecord)
  {
    this.context      = context;
    this.masterSecret = masterSecret;
    this.messageRecord = messageRecord;
  }

  @Override
  public void onClick(View v) {
    mismatchIndex = 0;
    onClick(null, 0);
  }

  @Override
  public void onClick(DialogInterface dialog, int which) {
    if (mismatchIndex < messageRecord.getIdentityKeyMismatches().size()) {
      showConfirmIdentityDialogFor(mismatchIndex++);
    }
  }

  private void showConfirmIdentityDialogFor(int index) {
    IdentityKeyMismatch   mismatch = messageRecord.getIdentityKeyMismatches().get(index);
    ConfirmIdentityDialog dialog   = new ConfirmIdentityDialog(context, masterSecret, messageRecord, mismatch);
    dialog.setCallback(this);

    dialog.show();
  }

}
