/*
 * Copyright (C) 2015 Open Whisper Systems
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
package org.thoughtcrime.securesms.conversationlist;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.DrawableRes;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.Toolbar;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.registration.PulsingFloatingActionButton;
import org.thoughtcrime.securesms.conversationlist.ConversationListAdapter.ItemClickListener;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.loaders.ConversationListLoader;
import org.thoughtcrime.securesms.util.task.SnackbarAsyncTask;


public class ConversationListArchiveFragment extends ConversationListFragment
  implements LoaderManager.LoaderCallbacks<Cursor>, ActionMode.Callback, ItemClickListener
{
  private RecyclerView                list;
  private View                        emptyState;
  private PulsingFloatingActionButton fab;
  private PulsingFloatingActionButton cameraFab;

  public static ConversationListArchiveFragment newInstance() {
    return new ConversationListArchiveFragment();
  }

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setHasOptionsMenu(false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    list          = view.findViewById(R.id.list);
    fab           = view.findViewById(R.id.fab);
    cameraFab     = view.findViewById(R.id.camera_fab);
    emptyState    = view.findViewById(R.id.empty_state);

    ((AppCompatActivity) requireActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    Toolbar toolbar = view.findViewById(R.id.toolbar_basic);
    toolbar.setNavigationOnClickListener(v -> requireActivity().onBackPressed());
    toolbar.setTitle(R.string.AndroidManifest_archived_conversations);

    fab.hide();
    cameraFab.hide();
  }

  @Override
  public @NonNull Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
    return new ConversationListLoader(getActivity(), null, true);
  }

  @Override
  public void onLoadFinished(@NonNull Loader<Cursor> arg0, Cursor cursor) {
    super.onLoadFinished(arg0, cursor);

    list.setVisibility(View.VISIBLE);
    emptyState.setVisibility(View.GONE);
  }

  @Override
  protected int getToolbarRes() {
    return R.id.toolbar_basic;
  }

  @Override
  protected @StringRes int getArchivedSnackbarTitleRes() {
    return R.plurals.ConversationListFragment_moved_conversations_to_inbox;
  }

  @Override
  protected @MenuRes int getActionModeMenuRes() {
    return R.menu.conversation_list_batch_unarchive;
  }

  @Override
  protected @DrawableRes int getArchiveIconRes() {
    return R.drawable.ic_unarchive_white_36dp;
  }

  @Override
  protected void archiveThread(long threadId) {
    DatabaseFactory.getThreadDatabase(getActivity()).unarchiveConversation(threadId);
  }

  @WorkerThread
  protected void reverseArchiveThread(long threadId) {
    DatabaseFactory.getThreadDatabase(getActivity()).archiveConversation(threadId);
  }

  @SuppressLint("StaticFieldLeak")
  @Override
  protected void onItemSwiped(long threadId, int unreadCount) {
    new SnackbarAsyncTask<Long>(getView(),
        getResources().getQuantityString(R.plurals.ConversationListFragment_moved_conversations_to_inbox, 1, 1),
        getString(R.string.ConversationListFragment_undo),
        getResources().getColor(R.color.amber_500),
        Snackbar.LENGTH_LONG, false)
    {
      @Override
      protected void executeAction(@Nullable Long parameter) {
        DatabaseFactory.getThreadDatabase(getActivity()).unarchiveConversation(threadId);
      }

      @Override
      protected void reverseAction(@Nullable Long parameter) {
        DatabaseFactory.getThreadDatabase(getActivity()).archiveConversation(threadId);
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, threadId);
  }
}


