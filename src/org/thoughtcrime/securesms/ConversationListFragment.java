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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.loaders.ConversationListLoader;
import org.thoughtcrime.securesms.recipients.RecipientFactory;
import org.thoughtcrime.securesms.recipients.Recipients;

import java.util.Set;


public class ConversationListFragment extends SherlockListFragment
  implements LoaderManager.LoaderCallbacks<Cursor>
{

  private ConversationSelectedListener listener;
  private MasterSecret masterSecret;
  private boolean isBatchMode = false;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    return inflater.inflate(R.layout.conversation_list_fragment, container, false);
  }

  @Override
  public void onActivityCreated(Bundle bundle) {
    super.onActivityCreated(bundle);

    setHasOptionsMenu(true);
    initializeListAdapter();
    registerForContextMenu(getListView());

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

    if      (this.isBatchMode)          inflater.inflate(R.menu.conversation_list_batch, menu);
    else if (this.masterSecret == null) inflater.inflate(R.menu.conversation_list_locked, menu);
    else                                inflater.inflate(R.menu.conversation_list, menu);

    super.onPrepareOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
    case R.id.menu_batch_mode:      handleSwitchBatchMode(true);  return true;
    case R.id.menu_delete_selected: handleDeleteAllSelected();    return true;
    case R.id.menu_select_all:      handleSelectAllThreads();     return true;
    case R.id.menu_unselect_all:    handleUnselectAllThreads();   return true;
    case R.id.menu_normal_mode:     handleSwitchBatchMode(false); return true;
    }

    return false;
  }

  @Override
  public void onCreateContextMenu (ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
    android.view.MenuInflater inflater = this.getSherlockActivity().getMenuInflater();
    menu.clear();

    inflater.inflate(R.menu.conversation_list_context, menu);
  }

  @Override
  public boolean onContextItemSelected(android.view.MenuItem item) {
    Cursor cursor         = ((CursorAdapter)this.getListAdapter()).getCursor();
    long threadId         = cursor.getLong(cursor.getColumnIndexOrThrow(ThreadDatabase.ID));
    String recipientId    = cursor.getString(cursor.getColumnIndexOrThrow(ThreadDatabase.RECIPIENT_IDS));
    Recipients recipients = RecipientFactory.getRecipientsForIds(getActivity(), recipientId);

    switch(item.getItemId()) {
    case R.id.menu_context_view:   handleCreateConversation(threadId, recipients); return true;
    case R.id.menu_context_delete: handleDeleteThread(threadId);                   return true;
    }

    return false;
  }

  @Override
  public void onListItemClick(ListView l, View v, int position, long id) {
    if (v instanceof ConversationHeaderView) {
      ConversationHeaderView headerView = (ConversationHeaderView) v;
      handleCreateConversation(headerView.getThreadId(), headerView.getRecipients());
    }
  }

  public void setMasterSecret(MasterSecret masterSecret) {
    this.masterSecret = masterSecret;
    initializeListAdapter();
  }

  private void initializeListAdapter() {
    if (this.masterSecret == null) {
      this.setListAdapter(new ConversationListAdapter(getActivity(), null));
    } else {
      this.setListAdapter(new DecryptingConversationListAdapter(getActivity(), null, masterSecret));
    }

    getLoaderManager().restartLoader(0, null, this);
  }

  private void handleSwitchBatchMode(boolean batchMode) {
    this.isBatchMode = batchMode;
    ((ConversationListAdapter)this.getListAdapter()).initializeBatchMode(batchMode);
    this.getSherlockActivity().invalidateOptionsMenu();
  }

  private void handleDeleteAllSelected() {
    AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
    alert.setIcon(android.R.drawable.ic_dialog_alert);
    alert.setTitle("Delete threads?");
    alert.setMessage("Are you sure you wish to delete ALL selected conversation threads?");
    alert.setCancelable(true);

    alert.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        Set<Long> selectedConversations = ((ConversationListAdapter)getListAdapter())
            .getBatchSelections();

        if (!selectedConversations.isEmpty()) {
          DatabaseFactory.getThreadDatabase(getActivity())
            .deleteConversations(selectedConversations);
        }
      }
    });

    alert.setNegativeButton("Cancel", null);
    alert.show();
  }

  private void handleDeleteThread(final long threadId) {
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setTitle("Delete Thread Confirmation");
    builder.setIcon(android.R.drawable.ic_dialog_alert);
    builder.setCancelable(true);
    builder.setMessage("Are you sure that you want to permanently delete this conversation?");
    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        if (threadId > 0) {
          DatabaseFactory.getThreadDatabase(getActivity()).deleteConversation(threadId);
        }
      }
    });
    builder.setNegativeButton(R.string.no, null);
    builder.show();
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
    return new ConversationListLoader(getActivity(), null);
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

}


