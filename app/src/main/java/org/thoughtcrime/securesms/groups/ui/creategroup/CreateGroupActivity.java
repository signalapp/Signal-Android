package org.thoughtcrime.securesms.groups.ui.creategroup;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.ContactSelectionActivity;
import org.thoughtcrime.securesms.ContactSelectionListFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader;
import org.thoughtcrime.securesms.contacts.sync.DirectoryHelper;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.groups.GroupsV2CapabilityChecker;
import org.thoughtcrime.securesms.groups.ui.creategroup.details.AddGroupDetailsActivity;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.Stopwatch;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.util.List;

public class CreateGroupActivity extends ContactSelectionActivity {

  private static final String TAG = Log.tag(CreateGroupActivity.class);

  private static final int   MINIMUM_GROUP_SIZE       = 1;
  private static final short REQUEST_CODE_ADD_DETAILS = 17275;

  private View next;

  public static Intent newIntent(@NonNull Context context) {
    Intent intent = new Intent(context, CreateGroupActivity.class);

    intent.putExtra(ContactSelectionListFragment.MULTI_SELECT, true);
    intent.putExtra(ContactSelectionListFragment.REFRESHABLE, false);
    intent.putExtra(ContactSelectionActivity.EXTRA_LAYOUT_RES_ID, R.layout.create_group_activity);

    int displayMode = TextSecurePreferences.isSmsEnabled(context) ? ContactsCursorLoader.DisplayMode.FLAG_SMS | ContactsCursorLoader.DisplayMode.FLAG_PUSH
                                                                  : ContactsCursorLoader.DisplayMode.FLAG_PUSH;

    intent.putExtra(ContactSelectionListFragment.DISPLAY_MODE, displayMode);
    intent.putExtra(ContactSelectionListFragment.TOTAL_CAPACITY, FeatureFlags.groupsV2create() ? FeatureFlags.gv2GroupCapacity() - 1
                                                                                               : ContactSelectionListFragment.NO_LIMIT);

    return intent;
  }

  @Override
  public void onCreate(Bundle bundle, boolean ready) {
    super.onCreate(bundle, ready);
    assert getSupportActionBar() != null;
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    next = findViewById(R.id.next);

    disableNext();
    next.setOnClickListener(v -> handleNextPressed());
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == REQUEST_CODE_ADD_DETAILS && resultCode == RESULT_OK) {
      finish();
    } else {
      super.onActivityResult(requestCode, resultCode, data);
    }
  }

  @Override
  public void onContactSelected(Optional<RecipientId> recipientId, String number) {
    if (contactsFragment.hasQueryFilter()) {
      getToolbar().clear();
    }

    if (contactsFragment.getSelectedContactsCount() >= MINIMUM_GROUP_SIZE) {
      enableNext();
    }
  }

  @Override
  public void onContactDeselected(Optional<RecipientId> recipientId, String number) {
    if (contactsFragment.hasQueryFilter()) {
      getToolbar().clear();
    }

    if (contactsFragment.getSelectedContactsCount() < MINIMUM_GROUP_SIZE) {
      disableNext();
    }
  }

  private void enableNext() {
    next.setEnabled(true);
    next.animate().alpha(1f);
  }

  private void disableNext() {
    next.setEnabled(false);
    next.animate().alpha(0.5f);
  }

  private void handleNextPressed() {
    Stopwatch                              stopwatch         = new Stopwatch("Recipient Refresh");
    SimpleProgressDialog.DismissibleDialog dismissibleDialog = SimpleProgressDialog.showDelayed(this);

    SimpleTask.run(getLifecycle(), () -> {
      RecipientId[] ids = Stream.of(contactsFragment.getSelectedContacts())
                                .map(selectedContact -> selectedContact.getOrCreateRecipientId(this))
                                .toArray(RecipientId[]::new);

      List<Recipient> resolved = Stream.of(ids)
                                       .map(Recipient::resolved)
                                       .toList();

      stopwatch.split("resolve");

      List<Recipient> registeredChecks = Stream.of(resolved)
                                               .filter(r -> r.getRegistered() == RecipientDatabase.RegisteredState.UNKNOWN)
                                               .toList();

      Log.i(TAG, "Need to do " + registeredChecks.size() + " registration checks.");

      for (Recipient recipient : registeredChecks) {
        try {
          DirectoryHelper.refreshDirectoryFor(this, recipient, false);
        } catch (IOException e) {
          Log.w(TAG, "Failed to refresh registered status for " + recipient.getId(), e);
        }
      }

      stopwatch.split("registered");

      if (FeatureFlags.groupsV2()) {
        try {
          new GroupsV2CapabilityChecker().refreshCapabilitiesIfNecessary(resolved);
        } catch (IOException e) {
          Log.w(TAG, "Failed to refresh all recipient capabilities.", e);
        }
      }

      stopwatch.split("capabilities");

      return ids;
    }, ids -> {
      dismissibleDialog.dismiss();

      stopwatch.stop(TAG);

      startActivityForResult(AddGroupDetailsActivity.newIntent(this, ids), REQUEST_CODE_ADD_DETAILS);
    });
  }
}
