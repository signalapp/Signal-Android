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
import android.os.Bundle;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.WorkerThread;
import androidx.appcompat.view.ActionMode;
import androidx.compose.material3.SnackbarDuration;
import androidx.recyclerview.widget.RecyclerView;

import org.signal.core.util.concurrent.LifecycleDisposable;
import org.signal.core.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.main.SnackbarState;
import org.thoughtcrime.securesms.util.ConversationUtil;
import org.thoughtcrime.securesms.util.views.Stub;

import java.util.Set;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import kotlin.Unit;


public class ConversationListArchiveFragment extends ConversationListFragment implements ActionMode.Callback
{
  private View                        coordinator;
  private RecyclerView                list;
  private RecyclerView                foldersList;
  private Stub<View>                  emptyState;
  private LifecycleDisposable         lifecycleDisposable = new LifecycleDisposable();

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

    lifecycleDisposable.bindTo(getViewLifecycleOwner());

    coordinator = view.findViewById(R.id.coordinator);
    list        = view.findViewById(R.id.list);
    emptyState  = new Stub<>(view.findViewById(R.id.empty_state));
    foldersList = view.findViewById(R.id.chat_folder_list);

    foldersList.setVisibility(View.GONE);
  }

  @Override
  protected void onPostSubmitList(int conversationCount) {
    list.setVisibility(View.VISIBLE);

    if (emptyState.resolved()) {
      emptyState.get().setVisibility(View.GONE);
    }
  }

  @Override
  protected boolean isArchived() {
    return true;
  }

  @Override
  protected @StringRes int getArchivedSnackbarTitleRes() {
    return R.plurals.ConversationListFragment_moved_conversations_to_inbox;
  }

  @Override
  protected @DrawableRes int getArchiveIconRes() {
    return R.drawable.symbol_archive_up_24;
  }

  @Override
  @WorkerThread
  protected void archiveThreads(Set<Long> threadIds) {
    SignalDatabase.threads().setArchived(threadIds, false);
  }

  @Override
  @WorkerThread
  protected void reverseArchiveThreads(Set<Long> threadIds) {
    SignalDatabase.threads().setArchived(threadIds, true);
  }

  @SuppressLint("StaticFieldLeak")
  @Override
  protected void onItemSwiped(long threadId, int unreadCount, int unreadSelfMentionsCount) {
    archiveDecoration.onArchiveStarted();
    itemAnimator.enable();

    lifecycleDisposable.add(
        Completable
            .fromAction(() -> {
              SignalDatabase.threads().unarchiveConversation(threadId);
              ConversationUtil.refreshRecipientShortcuts();
            })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(() -> {
              getNavigator().getViewModel().setSnackbar(new SnackbarState(
                  getResources().getQuantityString(R.plurals.ConversationListFragment_moved_conversations_to_inbox, 1, 1),
                  new SnackbarState.ActionState(
                      getString(R.string.ConversationListFragment_undo),
                      R.color.amber_500,
                      () -> {
                        SignalExecutors.BOUNDED_IO.execute(() -> {
                          SignalDatabase.threads().archiveConversation(threadId);
                          ConversationUtil.refreshRecipientShortcuts();
                        });

                        return Unit.INSTANCE;
                      }
                  ),
                  false,
                  SnackbarDuration.Long
              ));
            })
    );
  }

  @Override
  void updateEmptyState(boolean isConversationEmpty) {
    // Do nothing
  }
}


