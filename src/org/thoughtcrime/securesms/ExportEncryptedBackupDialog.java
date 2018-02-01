package org.thoughtcrime.securesms;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

class ExportEncryptedBackupDialog extends AlertDialog {
    ExportEncryptedBackupDialog(Context context, OnClickListener positiveListener) {
        super(context);
        setTitle("encrypted Backup information");
        setMessage("Please note that this backup can only be imported at registration time. It will only be useful for transferring your data to another phone or in case you lost your phone.");
        setButton(AlertDialog.BUTTON_POSITIVE, "OK", positiveListener);
        setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel", new NegativeListener());
    }

    private class NegativeListener implements OnClickListener {
        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
        }
    }
}
