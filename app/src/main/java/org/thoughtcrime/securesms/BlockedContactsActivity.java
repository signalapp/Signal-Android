package org.thoughtcrime.securesms;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cursoradapter.widget.CursorAdapter;
import androidx.fragment.app.ListFragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.loaders.BlockedContactsLoader;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.preferences.BlockedContactListItem;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.util.DynamicTheme;

public class BlockedContactsActivity extends PassphraseRequiredActionBarActivity {

  private final DynamicTheme dynamicTheme = new DynamicTheme();

  @Override
  public void onPreCreate() {
    dynamicTheme.onCreate(this);
  }

  @Override
  public void onCreate(Bundle bundle, boolean ready) {
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setTitle(R.string.BlockedContactsActivity_blocked_contacts);
    initFragment(android.R.id.content, new BlockedContactsFragment());
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  @Override
  public boolean onSupportNavigateUp() {
    onBackPressed();
    return true;
  }

  public static class BlockedContactsFragment
      extends ListFragment
      implements LoaderManager.LoaderCallbacks<Cursor>, ListView.OnItemClickListener
  {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
      return inflater.inflate(R.layout.blocked_contacts_fragment, container, false);
    }

    @Override
    public void onCreate(Bundle bundle) {
      super.onCreate(bundle);
      setListAdapter(new BlockedContactAdapter(requireActivity(), GlideApp.with(this), null));
      LoaderManager.getInstance(this).initLoader(0, null, this);
    }

    @Override
    public void onStart() {
      super.onStart();
      LoaderManager.getInstance(this).restartLoader(0, null, this);
    }

    @Override
    public void onActivityCreated(Bundle bundle) {
      super.onActivityCreated(bundle);
      getListView().setOnItemClickListener(this);
    }

    @Override
    public @NonNull Loader<Cursor> onCreateLoader(int id, Bundle args) {
      return new BlockedContactsLoader(getActivity());
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
      if (getListAdapter() != null) {
        ((CursorAdapter) getListAdapter()).changeCursor(data);
      }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
      if (getListAdapter() != null) {
        ((CursorAdapter) getListAdapter()).changeCursor(null);
      }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
      Recipient recipient = ((BlockedContactListItem)view).getRecipient();
      BlockUnblockDialog.showUnblockFor(requireContext(), getLifecycle(), recipient, () -> {
        RecipientUtil.unblock(requireContext(), recipient);
        LoaderManager.getInstance(this).restartLoader(0, null, this);
      });
    }

    private static class BlockedContactAdapter extends CursorAdapter {

      private final GlideRequests glideRequests;

      BlockedContactAdapter(@NonNull Context context, @NonNull GlideRequests glideRequests, @Nullable Cursor c) {
        super(context, c);
        this.glideRequests = glideRequests;
      }

      @Override
      public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return LayoutInflater.from(context)
                             .inflate(R.layout.blocked_contact_list_item, parent, false);
      }

      @Override
      public void bindView(View view, Context context, Cursor cursor) {
        RecipientId   recipientId = RecipientId.from(cursor.getLong(cursor.getColumnIndexOrThrow(RecipientDatabase.ID)));
        LiveRecipient recipient   = Recipient.live(recipientId);

        ((BlockedContactListItem) view).set(glideRequests, recipient);
      }
    }
  }
}
