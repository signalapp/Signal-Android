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

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import com.afollestad.materialdialogs.AlertDialogWrapper;
import com.melnykov.fab.FloatingActionButton;

import org.thoughtcrime.securesms.ConversationListAdapter.ItemClickListener;
import org.thoughtcrime.securesms.ConversationListAdapter.ItemSwipeListener;
import org.thoughtcrime.securesms.components.reminder.DefaultSmsReminder;
import org.thoughtcrime.securesms.components.reminder.ExpiredBuildReminder;
import org.thoughtcrime.securesms.components.reminder.PushRegistrationReminder;
import org.thoughtcrime.securesms.components.reminder.Reminder;
import org.thoughtcrime.securesms.components.reminder.ReminderView;
import org.thoughtcrime.securesms.components.reminder.ReminderView.OnDismissListener;
import org.thoughtcrime.securesms.components.reminder.SystemSmsImportReminder;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.loaders.ConversationListLoader;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.recipients.Recipients;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libaxolotl.util.guava.Optional;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


public class ConversationListFragment extends Fragment
  implements LoaderManager.LoaderCallbacks<Cursor>, ActionMode.Callback,
             ItemClickListener, ItemSwipeListener
{
  private MasterSecret         masterSecret;
  private ActionMode           actionMode;
  private RecyclerView         list;
  private ReminderView         reminderView;
  private FloatingActionButton fab;
  private Locale               locale;
  private String               queryFilter  = "";

  private final Handler        deleteHandler = new Handler(Looper.getMainLooper());
  private final Map<ConversationListItem.UndoToken, Runnable> pendingDeletes =
          Collections.synchronizedMap(new HashMap<ConversationListItem.UndoToken, Runnable>());

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    masterSecret = getArguments().getParcelable("master_secret");
    locale       = (Locale) getArguments().getSerializable(PassphraseRequiredActionBarActivity.LOCALE_EXTRA);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    final View view = inflater.inflate(R.layout.conversation_list_fragment, container, false);
    reminderView = (ReminderView) view.findViewById(R.id.reminder);
    list         = (RecyclerView) view.findViewById(R.id.list);
    fab          = (FloatingActionButton) view.findViewById(R.id.fab);
    reminderView.setOnDismissListener(new OnDismissListener() {
      @Override
      public void onDismiss() {
        updateReminders();
      }
    });
    list.setHasFixedSize(true);
    list.setLayoutManager(new LinearLayoutManager(getActivity()));
    return view;
  }

  @Override
  public void onActivityCreated(Bundle bundle) {
    super.onActivityCreated(bundle);

    setHasOptionsMenu(true);
    fab.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        startActivity(new Intent(getActivity(), NewConversationActivity.class));
      }
    });
    initializeListAdapter();
  }

  @Override
  public void onResume() {
    super.onResume();

    updateReminders();
    list.getAdapter().notifyDataSetChanged();
  }

  public ConversationListAdapter getListAdapter() {
    return (ConversationListAdapter) list.getAdapter();
  }

  public void setQueryFilter(String query) {
    this.queryFilter = query;
    getLoaderManager().restartLoader(0, null, this);
  }

  public void resetQueryFilter() {
    if (!TextUtils.isEmpty(this.queryFilter)) {
      setQueryFilter("");
    }
  }

  private void updateReminders() {
    reminderView.hide();
    new AsyncTask<Context, Void, Optional<? extends Reminder>>() {
      @Override protected Optional<? extends Reminder> doInBackground(Context... params) {
        final Context context = params[0];
        if (ExpiredBuildReminder.isEligible(context)) {
          return Optional.of(new ExpiredBuildReminder(context));
        } else if (DefaultSmsReminder.isEligible(context)) {
          return Optional.of(new DefaultSmsReminder(context));
        } else if (Util.isDefaultSmsProvider(context) && SystemSmsImportReminder.isEligible(context)) {
          return Optional.of((new SystemSmsImportReminder(context, masterSecret)));
        } else if (PushRegistrationReminder.isEligible(context)) {
          return Optional.of((new PushRegistrationReminder(context, masterSecret)));
        } else {
          return Optional.absent();
        }
      }

      @Override protected void onPostExecute(Optional<? extends Reminder> reminder) {
        if (reminder.isPresent() && getActivity() != null && !isRemoving()) {
          reminderView.showReminder(reminder.get());
        }
      }
    }.execute(getActivity());
  }

  private void initializeListAdapter() {
    list.setAdapter(new ConversationListAdapter(getActivity(), masterSecret,
            locale, null, this, this));
    getLoaderManager().restartLoader(0, null, this);
  }

  private void handleDeleteAllSelected() {
    AlertDialogWrapper.Builder alert = new AlertDialogWrapper.Builder(getActivity());
    alert.setIconAttribute(R.attr.dialog_alert_icon);
    alert.setTitle(R.string.ConversationListFragment_delete_threads_question);
    alert.setMessage(R.string.ConversationListFragment_are_you_sure_you_wish_to_delete_all_selected_conversation_threads);
    alert.setCancelable(true);

    alert.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        final Set<Long> selectedConversations = (getListAdapter())
            .getBatchSelections();

        if (!selectedConversations.isEmpty()) {
          new AsyncTask<Void, Void, Void>() {
            private ProgressDialog dialog;

            @Override
            protected void onPreExecute() {
              dialog = ProgressDialog.show(getActivity(),
                                           getActivity().getString(R.string.ConversationListFragment_deleting),
                                           getActivity().getString(R.string.ConversationListFragment_deleting_selected_threads),
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
    getListAdapter().selectAllThreads();
    actionMode.setSubtitle(getString(R.string.conversation_fragment_cab__batch_selection_amount,
                                     this.getListAdapter().getBatchSelections().size()));
  }

  private void handleCreateConversation(long threadId, Recipients recipients, int distributionType) {
    ((ConversationSelectedListener)getActivity()).onCreateConversation(threadId, recipients, distributionType);
  }

  @Override
  public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
    return new ConversationListLoader(getActivity(), queryFilter);
  }

  @Override
  public void onLoadFinished(Loader<Cursor> arg0, Cursor cursor) {
    getListAdapter().changeCursor(cursor);
  }

  @Override
  public void onLoaderReset(Loader<Cursor> arg0) {
    getListAdapter().changeCursor(null);
  }

  @Override
  public void onItemClick(ConversationListItem item) {
    if (actionMode == null) {
      handleCreateConversation(item.getThreadId(), item.getRecipients(),
                               item.getDistributionType());
    } else {
      ConversationListAdapter adapter = (ConversationListAdapter)list.getAdapter();
      adapter.toggleThreadInBatchSet(item.getThreadId());

      if (adapter.getBatchSelections().size() == 0) {
        actionMode.finish();
      } else {
        actionMode.setSubtitle(getString(R.string.conversation_fragment_cab__batch_selection_amount,
                                         adapter.getBatchSelections().size()));
      }

      adapter.notifyDataSetChanged();
    }
  }

  @Override
  public void onItemLongClick(ConversationListItem item) {
    actionMode = ((AppCompatActivity)getActivity()).startSupportActionMode(ConversationListFragment.this);

    getListAdapter().initializeBatchMode(true);
    getListAdapter().toggleThreadInBatchSet(item.getThreadId());
    getListAdapter().notifyDataSetChanged();
  }

  @Override
  public void onItemSwipeLeft(ConversationListItem item) {
    handleDelayedDeleteItem(item);
  }

  @Override
  public void onItemSwipeRight(ConversationListItem item) {
    handleDelayedDeleteItem(item);
  }

  private void handleDelayedDeleteItem(ConversationListItem item) {
    // Use an undo-token to track the undo-status on this item;
    // the Handler should run inside the UI thread, so there should be
    // no concurrency issue (user presses UNDO -> callback is removed), but
    // checking is safer in case a deleteAction is run after UNDO is pressed
    // (and more flexible, as it leaves the door open to a multi-thread approach; just
    // synchronize UndoToken getter/setters)
    final ConversationListItem.UndoToken token = item.getUndoToken();
    Runnable deleteAction = new Runnable() {
      @Override
      public void run() {
        if (!token.isCancelled()) {
          Log.d("Swiping", "Deleting item!");
          DatabaseFactory.getThreadDatabase(getActivity()).deleteConversation(token.threadId);
          MessageNotifier.updateNotification(getActivity(), masterSecret);
        }
      }
    };

    // Save this callback in a map, for retrieval (and cancellation)
    pendingDeletes.put(token, deleteAction);
    deleteHandler.postDelayed(deleteAction, 5000);
  }

  @Override
  public void onItemSwipeUndo(ConversationListItem itemView, ConversationListItem.UndoToken undoToken) {
    if (pendingDeletes.containsKey(undoToken)) {
      deleteHandler.removeCallbacks(pendingDeletes.get(undoToken));
      pendingDeletes.remove(undoToken);
    }
  }

  public interface ConversationSelectedListener {
    void onCreateConversation(long threadId, Recipients recipients, int distributionType);
}

  @Override
  public boolean onCreateActionMode(ActionMode mode, Menu menu) {
    MenuInflater inflater = getActivity().getMenuInflater();
    inflater.inflate(R.menu.conversation_list_batch, menu);

    mode.setTitle(R.string.conversation_fragment_cab__batch_selection_mode);
    mode.setSubtitle(null);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      getActivity().getWindow()
        .setStatusBarColor(getResources().getColor(R.color.action_mode_status_bar));
    }

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
    getListAdapter().initializeBatchMode(false);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      TypedArray color = getActivity().getTheme()
        .obtainStyledAttributes(new int[] { android.R.attr.statusBarColor });
      getActivity().getWindow().setStatusBarColor(color.getColor(0, Color.BLACK));
      color.recycle();
    }

    actionMode = null;
  }

}


