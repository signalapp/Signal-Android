package org.thoughtcrime.securesms.contacts;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;


import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.contacts.sync.DirectoryHelper;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.SetUtil;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public class ContactsSyncAdapter extends AbstractThreadedSyncAdapter {

  private static final String TAG = Log.tag(ContactsSyncAdapter.class);

  private static final int FULL_SYNC_THRESHOLD = 10;

  public ContactsSyncAdapter(Context context, boolean autoInitialize) {
    super(context, autoInitialize);
  }

  @Override
  public void onPerformSync(Account account, Bundle extras, String authority,
                            ContentProviderClient provider, SyncResult syncResult)
  {
    Log.i(TAG, "onPerformSync(" + authority +")");

    Context context = getContext();

    if (SignalStore.account().getE164() == null) {
      Log.i(TAG, "No local number set, skipping all sync operations.");
      return;
    }

    if (!SignalStore.account().isRegistered()) {
      Log.i(TAG, "Not push registered. Just syncing contact info.");
      DirectoryHelper.syncRecipientInfoWithSystemContacts(context);
      return;
    }

    Set<String> allSystemNumbers     = ContactAccessor.getInstance().getAllContactsWithNumbers(context);
    Set<String> knownSystemNumbers   = SignalDatabase.recipients().getAllPhoneNumbers();
    Set<String> unknownSystemNumbers = SetUtil.difference(allSystemNumbers, knownSystemNumbers);

    if (unknownSystemNumbers.size() > FULL_SYNC_THRESHOLD) {
      Log.i(TAG, "There are " + unknownSystemNumbers.size() + " unknown contacts. Doing a full sync.");
      try {
        DirectoryHelper.refreshDirectory(context, true);
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    } else if (unknownSystemNumbers.size() > 0) {
      Log.i(TAG, "There are " + unknownSystemNumbers.size() + " unknown contacts. Doing an individual sync.");
      List<Recipient> recipients = Stream.of(unknownSystemNumbers)
                                         .filter(s -> s.startsWith("+"))
                                         .map(s -> Recipient.external(getContext(), s))
                                         .toList();
      try {
        DirectoryHelper.refreshDirectoryFor(context, recipients, true);
      } catch (IOException e) {
        Log.w(TAG, "Failed to refresh! Scheduling for later.", e);
        ApplicationDependencies.getJobManager().add(new DirectoryRefreshJob(true));
      }
    } else {
      Log.i(TAG, "No new contacts. Just syncing system contact data.");
      DirectoryHelper.syncRecipientInfoWithSystemContacts(context);
    }
  }

  @Override
  public void onSyncCanceled() {
    Log.w(TAG, "onSyncCanceled()");
  }

  @Override
  public void onSyncCanceled(Thread thread) {
    Log.w(TAG, "onSyncCanceled(" + thread + ")");
  }

}
