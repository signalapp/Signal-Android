/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.loaders.ConversationListLoader;
import org.thoughtcrime.securesms.recipients.Recipients;

import java.util.Set;


public class ConversationListFragment extends SherlockListFragment
  implements LoaderManager.LoaderCallbacks<Cursor>, ActionMode.Callback
{

  private ConversationSelectedListener listener;
  private MasterSecret masterSecret;
  private String queryFilter = "";

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    return inflater.inflate(R.layout.conversation_list_fragment, container, false);
  }

  @Override
  public void onActivityCreated(Bundle bundle) {
    super.onActivityCreated(bundle);

    setHasOptionsMenu(true);
    initializeListAdapter();
    initializeBatchListener();

    getLoaderManager().initLoader(0, null, this);
  }

  @Override
  public void onAttach(Activity activity) {
      super.onAttach(activity);
      this.listener = (ConversationSelectedListener)activity;
  }

  @Override
  public void onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getSherlockActivity().getSupportMenuInflater();

    if (this.masterSecret != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
      inflater.inflate(R.menu.conversation_list, menu);
      initializeSearch((android.widget.SearchView)menu.findItem(R.id.menu_search).getActionView());
    } else {
      inflater.inflate(R.menu.conversation_list_empty, menu);
    }

    super.onPrepareOptionsMenu(menu);
  }

  @Override
  public void onListItemClick(ListView l, View v, int position, long id) {
    if (v instanceof ConversationListItem) {
      ConversationListItem headerView = (ConversationListItem) v;
      handleCreateConversation(headerView.getThreadId(), headerView.getRecipients());
    }
  }

  public void setMasterSecret(MasterSecret masterSecret) {
    this.masterSecret = masterSecret;
    initializeListAdapter();
  }

  @SuppressLint({ "NewApi", "NewApi" })
private void initializeSearch(android.widget.SearchView searchView) {
    searchView.setOnQueryTextListener(new android.widget.SearchView.OnQueryTextListener() {
      @Override
      public boolean onQueryTextSubmit(String query) {
        ConversationListFragment.this.queryFilter = query;
        ConversationListFragment.this.getLoaderManager().restartLoader(0, null, ConversationListFragment.this);
        return true;
      }
      @Override
      public boolean onQueryTextChange(String newText) {
        return onQueryTextSubmit(newText);
      }
    });
  }

  private void initializeBatchListener() {
    getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
      @Override
      public boolean onItemLongClick(AdapterView<?> arg0, View v, int position, long id) {
        ConversationListAdapter adapter = (ConversationListAdapter)getListAdapter();
        getSherlockActivity().startActionMode(ConversationListFragment.this);

        adapter.initializeBatchMode(true);
        adapter.addToBatchSet(((ConversationListItem)v).getThreadId());
        adapter.notifyDataSetChanged();

        return true;
      }
    });
  }

  private void initializeListAdapter() {
    if (this.masterSecret == null) {
      this.setListAdapter(new ConversationListAdapter(getActivity(), null));
    } else {
      this.setListAdapter(new DecryptingConversationListAdapter(getActivity(), null, masterSecret));
    }

    getLoaderManager().restartLoader(0, null, this);
  }

  private void handleDeleteAllSelected() {
    AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
    alert.setIcon(android.R.drawable.ic_dialog_alert);
    alert.setTitle(R.string.delete_threads_question);
    alert.setMessage(R.string.are_you_sure_you_wish_to_delete_all_selected_conversation_threads);
    alert.setCancelable(true);

    alert.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        Set<Long> selectedConversations = ((ConversationListAdapter)getListAdapter())
            .getBatchSelections();

        if (!selectedConversations.isEmpty()) {
          DatabaseFactory.getThreadDatabase(getActivity())
            .deleteConversations(selectedConversations);
        }
      }
    });

    alert.setNegativeButton(R.string.cancel, null);
    alert.show();
  }

  private void handleSelectAllThreads() {
    ((ConversationListAdapter)this.getListAdapter()).selectAllThreads();
  }

  private void handleUnselectAllThreads() {
    ((ConversationListAdapter)this.getListAdapter()).selectAllThreads();
  }

  private void handleCreateConversation(long threadId, Recipients recipients) {
    listener.onCreateConversation(threadId, recipients);
  }

  @Override
  public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
    return new ConversationListLoader(getActivity(), queryFilter);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> arg0, Cursor cursor) {
    ((CursorAdapter)getListAdapter()).changeCursor(cursor);
  }

  @Override
  public void onLoaderReset(Loader<Cursor> arg0) {
    ((CursorAdapter)getListAdapter()).changeCursor(null);
  }

  public interface ConversationSelectedListener {
    public void onCreateConversation(long threadId, Recipients recipients);
}

  @Override
  public boolean onCreateActionMode(ActionMode mode, Menu menu) {
    MenuInflater inflater = getSherlockActivity().getSupportMenuInflater();
    inflater.inflate(R.menu.conversation_list_batch, menu);

    LayoutInflater layoutInflater = getSherlockActivity().getLayoutInflater();
    View actionModeView = layoutInflater.inflate(R.layout.conversation_fragment_cab, null);

    mode.setCustomView(actionModeView);

    return true;
  }

  @Override
  public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
    return false;
  }

  @Override
  public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
    switch (item.getItemId()) {
    case R.id.menu_select_all:      handleSelectAllThreads(); return true;
    case R.id.menu_delete_selected: handleDeleteAllSelected(); return true;
    }

    return false;
  }

  @Override
  public void onDestroyActionMode(ActionMode mode) {
    ((ConversationListAdapter)getListAdapter()).initializeBatchMode(false);
  }

}


