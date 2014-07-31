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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.provider.Telephony;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockListFragment;
import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.widget.SearchView;

import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.loaders.ConversationListLoader;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.service.ApplicationMigrationService;
import org.thoughtcrime.securesms.util.Dialogs;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.textsecure.crypto.MasterSecret;
import org.thoughtcrime.securesms.util.Util;

import java.util.Set;

import hugo.weaving.DebugLog;


public class ConversationListFragment extends SherlockListFragment
  implements LoaderManager.LoaderCallbacks<Cursor>, ActionMode.Callback
{

  private ConversationSelectedListener listener;
  private MasterSecret                 masterSecret;
  private ActionMode                   actionMode;
  private View reminderView;
  private String queryFilter = "";

  @DebugLog
  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    final View view = inflater.inflate(R.layout.conversation_list_fragment, container, false);
    reminderView = LayoutInflater.from(getActivity()).inflate(R.layout.reminder_header, null);
    return view;
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    getListView().setAdapter(null);
  }

  @DebugLog
  @Override
  public void onActivityCreated(Bundle bundle) {
    super.onActivityCreated(bundle);

    setHasOptionsMenu(true);
    getListView().setAdapter(null);
    getListView().addHeaderView(reminderView);
    initializeListAdapter();
    initializeBatchListener();

    getLoaderManager().initLoader(0, null, this);
  }

  @Override
  public void onResume() {
    super.onResume();

    initializeReminders();
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    this.listener = (ConversationSelectedListener)activity;
  }

  @Override
  public void onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getSherlockActivity().getSupportMenuInflater();

    if (this.masterSecret != null) {
      inflater.inflate(R.menu.conversation_list, menu);
      initializeSearch((SearchView)menu.findItem(R.id.menu_search).getActionView());
    } else {
      inflater.inflate(R.menu.conversation_list_empty, menu);
    }

    super.onPrepareOptionsMenu(menu);
  }

  @Override
  public void onListItemClick(ListView l, View v, int position, long id) {
    if (v instanceof ConversationListItem) {
      ConversationListItem headerView = (ConversationListItem) v;
      if (actionMode == null) {
        handleCreateConversation(headerView.getThreadId(), headerView.getRecipients(),
                                 headerView.getDistributionType());
      } else {
        ConversationListAdapter adapter = (ConversationListAdapter)getListAdapter();
        adapter.toggleThreadInBatchSet(headerView.getThreadId());

        if (adapter.getBatchSelections().size() == 0) {
          actionMode.finish();
        } else {
          actionMode.setSubtitle(getString(R.string.conversation_fragment_cab__batch_selection_amount,
                                           adapter.getBatchSelections().size()));
        }

        adapter.notifyDataSetChanged();
      }
    }
  }

  public void setMasterSecret(MasterSecret masterSecret) {
    if (this.masterSecret != masterSecret) {
      this.masterSecret = masterSecret;
      initializeListAdapter();
    }
  }

  private void setQueryFilter(String query) {
    this.queryFilter = query;
    getLoaderManager().restartLoader(0, null, this);
  }

  public void resetQueryFilter() {
    if (!TextUtils.isEmpty(this.queryFilter)) {
      setQueryFilter("");
    }
  }

  private void initializeSearch(SearchView searchView) {
    searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
      @Override
      public boolean onQueryTextSubmit(String query) {
        if (isAdded()) {
          setQueryFilter(query);
          return true;
        }
        return false;
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
        actionMode = getSherlockActivity().startActionMode(ConversationListFragment.this);

        adapter.initializeBatchMode(true);
        adapter.toggleThreadInBatchSet(((ConversationListItem) v).getThreadId());
        adapter.notifyDataSetChanged();

        return true;
      }
    });
  }

  private void initializeReminders() {
    final boolean isDefault = Util.isDefaultSmsProvider(getActivity());
    if (isDefault) {
      TextSecurePreferences.setPromptedDefaultSmsProvider(getActivity(), false);
    }

    if (!isDefault && !TextSecurePreferences.hasPromptedDefaultSmsProvider(getActivity())) {
      showDefaultSmsReminder();
    } else if (isDefault && !ApplicationMigrationService.isDatabaseImported(getActivity())) {
      showSystemSmsImportReminder();
    } else {
      reminderView.findViewById(R.id.container).setVisibility(View.GONE);
    }
  }

  @DebugLog
  private void initializeListAdapter() {
    this.setListAdapter(new ConversationListAdapter(getActivity(), null, masterSecret));
    getListView().setRecyclerListener((ConversationListAdapter)getListAdapter());
    getLoaderManager().restartLoader(0, null, this);
  }

  private void handleDeleteAllSelected() {
    AlertDialog.Builder alert = new AlertDialog.Builder(getActivity());
    alert.setIcon(Dialogs.resolveIcon(getActivity(), R.attr.dialog_alert_icon));
    alert.setTitle(R.string.ConversationListFragment_delete_threads_question);
    alert.setMessage(R.string.ConversationListFragment_are_you_sure_you_wish_to_delete_all_selected_conversation_threads);
    alert.setCancelable(true);

    alert.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        final Set<Long> selectedConversations = ((ConversationListAdapter)getListAdapter())
            .getBatchSelections();

        if (!selectedConversations.isEmpty()) {
          new AsyncTask<Void, Void, Void>() {
            private ProgressDialog dialog;

            @Override
            protected void onPreExecute() {
              dialog = ProgressDialog.show(getActivity(),
                                           getSherlockActivity().getString(R.string.ConversationListFragment_deleting),
                                           getSherlockActivity().getString(R.string.ConversationListFragment_deleting_selected_threads),
                                           true, false);
            }

            @Override
            protected Void doInBackground(Void... params) {
              DatabaseFactory.getThreadDatabase(getActivity()).deleteConversations(selectedConversations);
              MessageNotifier.updateNotification(getActivity(), masterSecret);
              return null;
            }

            @Override
            protected void onPostExecute(Void result) {
              dialog.dismiss();
              if (actionMode != null) {
                actionMode.finish();
                actionMode = null;
              }
            }
          }.execute();
        }
      }
    });

    alert.setNegativeButton(android.R.string.cancel, null);
    alert.show();
  }

  private void handleSelectAllThreads() {
    ((ConversationListAdapter)this.getListAdapter()).selectAllThreads();
    actionMode.setSubtitle(getString(R.string.conversation_fragment_cab__batch_selection_amount,
                           ((ConversationListAdapter)this.getListAdapter()).getBatchSelections().size()));
  }

  private void handleUnselectAllThreads() {
    ((ConversationListAdapter)this.getListAdapter()).selectAllThreads();
    actionMode.setSubtitle(getString(R.string.conversation_fragment_cab__batch_selection_amount,
                           ((ConversationListAdapter)this.getListAdapter()).getBatchSelections().size()));
  }

  private void handleCreateConversation(long threadId, Recipients recipients, int distributionType) {
    listener.onCreateConversation(threadId, recipients, distributionType);
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
    public void onCreateConversation(long threadId, Recipients recipients, int distributionType);
}

  @Override
  public boolean onCreateActionMode(ActionMode mode, Menu menu) {
    MenuInflater inflater = getSherlockActivity().getSupportMenuInflater();
    inflater.inflate(R.menu.conversation_list_batch, menu);

    mode.setTitle(R.string.conversation_fragment_cab__batch_selection_mode);
    mode.setSubtitle(null);

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
    actionMode = null;
  }

  @TargetApi(VERSION_CODES.KITKAT)
  private void showDefaultSmsReminder() {
    final ViewGroup container = (ViewGroup) reminderView.findViewById(R.id.container);

    setReminderData(R.drawable.sms_selection_icon,
                    R.string.reminder_header_sms_default_title,
                    R.string.reminder_header_sms_default_text,
                    new OnClickListener() {
                      @Override
                      public void onClick(View v) {
                        TextSecurePreferences.setPromptedDefaultSmsProvider(getActivity(), true);
                        Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
                        intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getActivity().getPackageName());
                        startActivity(intent);
                      }
                    },
                    new OnClickListener() {
                      @Override
                      public void onClick(View v) {
                        TextSecurePreferences.setPromptedDefaultSmsProvider(getActivity(), true);
                        container.setVisibility(View.GONE);
                      }
                    });
    container.setVisibility(View.VISIBLE);
  }

  private void showSystemSmsImportReminder() {
    final ViewGroup container = (ViewGroup) reminderView.findViewById(R.id.container);

    setReminderData(R.drawable.sms_system_import_icon,
                    R.string.reminder_header_sms_import_title,
                    R.string.reminder_header_sms_import_text,
                    new OnClickListener() {
                      @Override
                      public void onClick(View v) {
                        Intent intent = new Intent(getActivity(), ApplicationMigrationService.class);
                        intent.setAction(ApplicationMigrationService.MIGRATE_DATABASE);
                        intent.putExtra("master_secret", masterSecret);
                        getActivity().startService(intent);

                        Intent nextIntent = new Intent(getActivity(), ConversationListActivity.class);
                        intent.putExtra("master_secret", masterSecret);

                        Intent activityIntent = new Intent(getActivity(), DatabaseMigrationActivity.class);
                        activityIntent.putExtra("master_secret", masterSecret);
                        activityIntent.putExtra("next_intent", nextIntent);
                        getActivity().startActivity(activityIntent);
                      }
                    },
                    new OnClickListener() {
                      @Override
                      public void onClick(View v) {
                        ApplicationMigrationService.setDatabaseImported(getActivity());
                        container.setVisibility(View.GONE);
                      }
                    });
    container.setVisibility(View.VISIBLE);
  }

  private void setReminderData(int iconResId, int titleResId, int textResId, OnClickListener okListener, OnClickListener cancelListener) {
    final ImageButton cancel = (ImageButton) reminderView.findViewById(R.id.cancel);
    final Button      ok     = (Button     ) reminderView.findViewById(R.id.ok);
    final TextView    title  = (TextView   ) reminderView.findViewById(R.id.reminder_title);
    final TextView    text   = (TextView   ) reminderView.findViewById(R.id.reminder_text);
    final ImageView   icon   = (ImageView  ) reminderView.findViewById(R.id.icon);

    icon.setImageResource(iconResId);
    title.setText(titleResId);
    text.setText(textResId);
    ok.setOnClickListener(okListener);
    cancel.setOnClickListener(cancelListener);
  }

}


