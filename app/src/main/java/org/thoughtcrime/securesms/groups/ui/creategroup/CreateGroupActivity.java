package org.thoughtcrime.securesms.groups.ui.creategroup;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.annimon.stream.Stream;

import org.thoughtcrime.securesms.ContactSelectionActivity;
import org.thoughtcrime.securesms.ContactSelectionListFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.contacts.ContactsCursorLoader;
import org.thoughtcrime.securesms.contacts.sync.DirectoryHelper;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.groups.GroupsV2CapabilityChecker;
import org.thoughtcrime.securesms.groups.ui.creategroup.details.AddGroupDetailsActivity;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CreateGroupActivity extends ContactSelectionActivity {

  private static final String TAG = Log.tag(CreateGroupActivity.class);

  private static final int   MINIMUM_GROUP_SIZE       = 1;
  private static final short REQUEST_CODE_ADD_DETAILS = 17275;

  private View next;

  public static Intent newIntent(@NonNull Context context) {
    Intent intent = new Intent(context, CreateGroupActivity.class);

    intent.putExtra(ContactSelectionListFragment.REFRESHABLE, false);
    intent.putExtra(ContactSelectionActivity.EXTRA_LAYOUT_RES_ID, R.layout.create_group_activity);

    int displayMode = TextSecurePreferences.isSmsEnabled(context) ? ContactsCursorLoader.DisplayMode.FLAG_SMS | ContactsCursorLoader.DisplayMode.FLAG_PUSH
                                                                  : ContactsCursorLoader.DisplayMode.FLAG_PUSH;

    intent.putExtra(ContactSelectionListFragment.DISPLAY_MODE, displayMode);
    intent.putExtra(ContactSelectionListFragment.SELECTION_LIMITS, FeatureFlags.groupLimits().excludingSelf());

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
  public boolean onBeforeContactSelected(Optional<RecipientId> recipientId, String number) {
    if (contactsFragment.hasQueryFilter()) {
      getToolbar().clear();
    }

    enableNext();

    return true;
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
      List<RecipientId> ids = Stream.of(contactsFragment.getSelectedContacts())
                                    .map(selectedContact -> selectedContact.getOrCreateRecipientId(this))
                                    .toList();

      List<Recipient> resolved = Recipient.resolvedList(ids);

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

      List<Recipient> recipientsAndSelf = new ArrayList<>(resolved);
      recipientsAndSelf.add(Recipient.self().resolve());

      if (!SignalStore.internalValues().gv2DoNotCreateGv2Groups()) {
        try {
          GroupsV2CapabilityChecker.refreshCapabilitiesIfNecessary(recipientsAndSelf);
        } catch (IOException e) {
          Log.w(TAG, "Failed to refresh all recipient capabilities.", e);
        }
      }

      stopwatch.split("capabilities");

      resolved = Recipient.resolvedList(ids);

      boolean gv2 = Stream.of(recipientsAndSelf).allMatch(r -> r.getGroupsV2Capability() == Recipient.Capability.SUPPORTED);
      if (!gv2 && Stream.of(resolved).anyMatch(r -> !r.hasE164()))
      {
        Log.w(TAG, "Invalid GV1 group...");
        ids = Collections.emptyList();
      }

      stopwatch.split("gv1-check");

      return ids;
    }, ids -> {
      dismissibleDialog.dismiss();

      stopwatch.stop(TAG);

      if (ids.isEmpty()) {
        new AlertDialog.Builder(this)
                       .setMessage(R.string.CreateGroupActivity_some_contacts_cannot_be_in_legacy_groups)
                       .setPositiveButton(android.R.string.ok, (d, w) -> d.dismiss())
                       .show();
      } else {
        startActivityForResult(AddGroupDetailsActivity.newIntent(this, ids), REQUEST_CODE_ADD_DETAILS);
      }
    });
  }
}
