package org.thoughtcrime.securesms.conversation;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.model.MessageRecord;

/**
 * A dialog fragment that shows when you click 'learn more' on a {@link MessageRecord#isBadDecryptType()}.
 */
public final class BadDecryptLearnMoreDialog extends DialogFragment {

  private static final String TAG          = Log.tag(BadDecryptLearnMoreDialog.class);
  private static final String FRAGMENT_TAG = "BadDecryptLearnMoreDialog";

  private static final String KEY_DISPLAY_NAME = "display_name";
  private static final String KEY_GROUP_CHAT   = "group_chat";

  public static void show(@NonNull FragmentManager fragmentManager, @NonNull String displayName, boolean isGroupChat) {
    if (fragmentManager.findFragmentByTag(FRAGMENT_TAG) != null) {
      Log.i(TAG, "Already shown!");
      return;
    }

    Bundle args = new Bundle();
    args.putString(KEY_DISPLAY_NAME, displayName);
    args.putBoolean(KEY_GROUP_CHAT, isGroupChat);

    BadDecryptLearnMoreDialog fragment = new BadDecryptLearnMoreDialog();
    fragment.setArguments(args);
    fragment.show(fragmentManager, FRAGMENT_TAG);
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(requireContext());

    View     view = LayoutInflater.from(requireContext()).inflate(R.layout.bad_decrypt_learn_more_dialog_fragment, null);
    TextView body = view.findViewById(R.id.bad_decrypt_dialog_body);

    String  displayName = requireArguments().getString(KEY_DISPLAY_NAME);
    boolean isGroup     = requireArguments().getBoolean(KEY_GROUP_CHAT);

    if (isGroup) {
      body.setText(getString(R.string.BadDecryptLearnMoreDialog_couldnt_be_delivered_group, displayName));
    } else {
      body.setText(getString(R.string.BadDecryptLearnMoreDialog_couldnt_be_delivered_individual, displayName));
    }

    dialogBuilder.setView(view)
                 .setPositiveButton(android.R.string.ok, null);

    return dialogBuilder.create();
  }
}
