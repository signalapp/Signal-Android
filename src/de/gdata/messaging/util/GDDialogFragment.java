package de.gdata.messaging.util;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.Dialogs;

public class GDDialogFragment extends DialogFragment {
    private static String DIALOG_TYPE = "type";

    public static int TYPE_PHISHING_WARNING = 1;

    private static int mType = 0;
    private static String mTitle = "";
    private static String mMessage = "";
    private static String mPositiveText = "";
    private static String mNegativeTest = "";
    private static DialogInterface.OnClickListener mPositiveClickListener = null;
    private static DialogInterface.OnClickListener mNegativeClickListener = null;

    public static GDDialogFragment newInstance(int type, DialogInterface.OnClickListener positiveClickListener,
            DialogInterface.OnClickListener negativeClickListener) {
        GDDialogFragment f = new GDDialogFragment();

        mType = type;
        mPositiveClickListener = positiveClickListener;
        mNegativeClickListener = negativeClickListener;

        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (mType == TYPE_PHISHING_WARNING) {
            mTitle = getString(R.string.gd_dialog_fragment_phishing_title);
            mMessage = getString(R.string.gd_dialog_fragment_phishing_message);
            mPositiveText = getString(R.string.gd_dialog_fragment_phishing_block);
            mNegativeTest = getString(R.string.gd_dialog_fragment_phishing_continue);
        }

        return new AlertDialog.Builder(getActivity())
                .setIcon(Dialogs.resolveIcon(getActivity(), R.attr.dialog_info_icon)).setTitle(mTitle)
                .setMessage(mMessage).setPositiveButton(mPositiveText, mPositiveClickListener)
                .setNegativeButton(mNegativeTest, mNegativeClickListener).create();
    }
}
