package org.thoughtcrime.securesms.contacts;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;

import com.annimon.stream.Stream;

import org.signal.contacts.SystemContactsRepository;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.contacts.sync.ContactDiscovery;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.signal.core.util.SetUtil;
import org.thoughtcrime.securesms.util.SignalE164Util;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
      ContactDiscovery.syncRecipientInfoWithSystemContacts(context);
      return;
    }

    Set<String> allSystemE164s     = SystemContactsRepository.getAllDisplayNumbers(context)
                                                             .stream()
                                                             .map(number -> SignalE164Util.formatAsE164(number))
                                                             .filter(it -> it != null)
                                                             .collect(Collectors.toSet());
    Set<String> knownSystemE164s   = SignalDatabase.recipients().getAllE164s();
    Set<String> unknownSystemE164s = SetUtil.difference(allSystemE164s, knownSystemE164s);

    if (unknownSystemE164s.size() > FULL_SYNC_THRESHOLD) {
      Log.i(TAG, "There are " + unknownSystemE164s.size() + " unknown contacts. Doing a full sync.");
      try {
        ContactDiscovery.refreshAll(context, true);
      } catch (IOException e) {
        Log.w(TAG, e);
      }
    } else if (unknownSystemE164s.size() > 0) {
      List<Recipient> recipients = Stream.of(unknownSystemE164s)
                                         .filter(s -> s.startsWith("+"))
                                         .map(s -> Recipient.external(s))
                                         .filter(it -> it != null)
                                         .toList();

      Log.i(TAG, "There are " + unknownSystemE164s.size() + " unknown E164s, which are now " + recipients.size() + " recipients. Only syncing these specific contacts.");

      try {
        ContactDiscovery.refresh(context, recipients, true);
      } catch (IOException e) {
        Log.w(TAG, "Failed to refresh! Scheduling for later.", e);
        AppDependencies.getJobManager().add(new DirectoryRefreshJob(true));
      }
    } else {
      Log.i(TAG, "No new contacts. Just syncing system contact data.");
      ContactDiscovery.syncRecipientInfoWithSystemContacts(context);
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
