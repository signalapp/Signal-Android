package org.thoughtcrime.securesms.components.webrtc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.appcompat.app.AlertDialog;

import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.R;

public final class MobileCallNotAllowedDialog {
    public static Intent getCallPreferencesIntent(Context context) {
        Intent intent = new Intent(context, ApplicationPreferencesActivity.class);
        intent.putExtra(ApplicationPreferencesActivity.LAUNCH_TO_DATA_AND_STORAGE_FRAGMENT, true);
        return intent;
    }

    public static void show(Activity activity) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.MobileCallNotAllowedDialog_title)
                .setMessage(R.string.MobileCallNotAllowedDialog_message)
                .setPositiveButton(R.string.MobileCallNotAllowedDialog_positive_button, (dialog1, which) -> {
                    Intent intent = getCallPreferencesIntent(activity);
                    activity.startActivity(intent);
                    activity.finish();
                })
                .setNegativeButton(android.R.string.cancel, (dialog12, which) -> {
                    dialog12.dismiss();
                    activity.finish();
                })
                .setOnDismissListener(dialog13 -> activity.finish())
                .create();

        dialog.setIcon(activity.getResources().getDrawable(R.drawable.icon_dialog));
        dialog.show();
    }
}
