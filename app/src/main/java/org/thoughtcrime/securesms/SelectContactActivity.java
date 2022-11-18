package org.thoughtcrime.securesms;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader.DisplayMode;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.libsignal.util.guava.Optional;

public class SelectContactActivity extends ContactSelectionActivity
        implements ContactSelectionListFragment.RefreshCallback, ContactSelectionListFragment.SearchCallBack {

    public static final String MODE = "contact_mode";
    public static final String MODE_CALL = "call";
    public static final String MODE_MESSAGE = "message";

    @SuppressWarnings("unused")
    private static final String TAG = NewConversationActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle bundle, boolean ready) {
        String mode = getIntent().getStringExtra(MODE);
        if (!TextUtils.isEmpty(mode)) {
            if (mode.equals(MODE_CALL)) {
                int displayMode = DisplayMode.FLAG_PUSH | DisplayMode.FLAG_BLOCK_SELF | DisplayMode.FLAG_BLOCK_UNKNOWN;
                getIntent().putExtra(ContactSelectionListFragment.DISPLAY_MODE, displayMode);
            } else if (mode.equals(MODE_MESSAGE)) {
                int displayMode = DisplayMode.FLAG_PUSH | DisplayMode.FLAG_BLOCK_SELF | DisplayMode.FLAG_BLOCK_UNKNOWN;
                getIntent().putExtra(ContactSelectionListFragment.DISPLAY_MODE, displayMode);
            }
        }
        super.onCreate(bundle, ready);
    }

//    @Override
//    public void onContactSelected(Optional<RecipientId> recipientId, String number) {
//        Recipient recipient;
//        if (recipientId.isPresent()) {
//            recipient = Recipient.resolved(recipientId.get());
//        } else {
//            Log.i(TAG, "[onContactSelected] Maybe creating a new recipient.");
//            recipient = Recipient.external(this, number);
//        }
//
//        Intent intent = new Intent();
//        intent.putExtra("signal_contact_name", recipient.getDisplayName(this));
//        intent.putExtra("signal_contact_number", number);
//        setResult(RESULT_OK, intent);
//        finish();
//    }

    private void handleManualRefresh() {
        contactsFragment.setRefreshing(false);
        onRefresh();
        Toast.makeText(getApplicationContext(), "Refreshed", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onReFresh() {
        handleManualRefresh();
    }

    @Override
    public void onSearch(View view) {
    }

    @Override
    public void onSelectionChanged() {

    }
}
