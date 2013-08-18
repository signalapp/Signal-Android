package org.thoughtcrime.securesms;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockListFragment;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.loaders.IdentityLoader;

public class ReviewIdentitiesFragment extends SherlockListFragment
    implements LoaderManager.LoaderCallbacks<Cursor>
{
  private MasterSecret masterSecret;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    return inflater.inflate(R.layout.review_identities_fragment, container, false);
  }

  @Override
  public void onActivityCreated(Bundle bundle) {
    super.onActivityCreated(bundle);
    this.masterSecret = getSherlockActivity().getIntent().getParcelableExtra("master_secret");

    initializeListAdapter();
    getLoaderManager().initLoader(0, null, this);
  }

  @Override
  public void onListItemClick(ListView listView, View view, int position, long id) {
    Intent viewIntent = new Intent(getActivity(), ViewIdentityActivity.class);
    viewIntent.putExtra("identity_key", ((IdentityKeyView)view).getIdentityKey());
    viewIntent.putExtra("title", ((IdentityKeyView)view).getRecipient().toShortString() + " " +
                                 getString(R.string.ViewIdentityActivity_identity_fingerprint));
    startActivity(viewIntent);
  }

  private void initializeListAdapter() {
    this.setListAdapter(new IdentitiesListAdapter(getActivity(), null, masterSecret));
    getLoaderManager().restartLoader(0, null, this);
  }

  @Override
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    return new IdentityLoader(getActivity());
  }

  @Override
  public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
    ((CursorAdapter)getListAdapter()).changeCursor(cursor);
  }

  @Override
  public void onLoaderReset(Loader<Cursor> loader) {
    ((CursorAdapter)getListAdapter()).changeCursor(null);
  }

  private class IdentitiesListAdapter extends CursorAdapter {
    private final MasterSecret masterSecret;
    private final LayoutInflater inflater;

    public IdentitiesListAdapter(Context context, Cursor cursor, MasterSecret masterSecret) {
      super(context, cursor);
      this.masterSecret = masterSecret;
      this.inflater     = LayoutInflater.from(context);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
      IdentityDatabase.Reader reader = DatabaseFactory.getIdentityDatabase(context)
                                                      .readerFor(masterSecret, cursor);

      ((IdentityKeyView)view).set(reader.getCurrent());
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
      return inflater.inflate(R.layout.identity_key_view, parent, false);
    }
  }
}