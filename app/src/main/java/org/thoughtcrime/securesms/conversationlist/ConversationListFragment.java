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

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.PluralsRes;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.annimon.stream.Stream;
import com.google.android.material.snackbar.Snackbar;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.MainFragment;
import org.thoughtcrime.securesms.MainNavigator;
import org.thoughtcrime.securesms.NewConversationActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.RatingManager;
import org.thoughtcrime.securesms.components.SearchToolbar;
import org.thoughtcrime.securesms.components.recyclerview.DeleteItemAnimator;
import org.thoughtcrime.securesms.components.registration.PulsingFloatingActionButton;
import org.thoughtcrime.securesms.components.reminder.DozeReminder;
import org.thoughtcrime.securesms.components.reminder.ExpiredBuildReminder;
import org.thoughtcrime.securesms.components.reminder.OutdatedBuildReminder;
import org.thoughtcrime.securesms.components.reminder.PushRegistrationReminder;
import org.thoughtcrime.securesms.components.reminder.Reminder;
import org.thoughtcrime.securesms.components.reminder.ReminderView;
import org.thoughtcrime.securesms.components.reminder.ServiceOutageReminder;
import org.thoughtcrime.securesms.components.reminder.UnauthorizedReminder;
import org.thoughtcrime.securesms.conversation.ConversationFragment;
import org.thoughtcrime.securesms.conversationlist.model.Conversation;
import org.thoughtcrime.securesms.conversationlist.model.MessageResult;
import org.thoughtcrime.securesms.conversationlist.model.SearchResult;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessageDatabase.MarkedMessageInfo;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.events.ReminderUpdateEvent;
import org.thoughtcrime.securesms.insights.InsightsLauncher;
import org.thoughtcrime.securesms.jobs.ServiceOutageDetectionJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.v2.CreateKbsPinActivity;
import org.thoughtcrime.securesms.mediasend.MediaSendActivity;
import org.thoughtcrime.securesms.megaphone.Megaphone;
import org.thoughtcrime.securesms.megaphone.MegaphoneActionController;
import org.thoughtcrime.securesms.megaphone.MegaphoneViewBuilder;
import org.thoughtcrime.securesms.megaphone.Megaphones;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.notifications.MarkReadReceiver;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.util.AppStartup;
import org.thoughtcrime.securesms.util.AvatarUtil;
import org.thoughtcrime.securesms.util.PlayStoreUtil;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.SnapToTopDataObserver;
import org.thoughtcrime.securesms.util.StickyHeaderDecoration;
import org.thoughtcrime.securesms.util.Stopwatch;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.WindowUtil;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.util.task.SnackbarAsyncTask;
import org.thoughtcrime.securesms.util.views.Stub;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static android.app.Activity.RESULT_OK;


public class ConversationListFragment extends MainFragment implements ActionMode.Callback,
                                                                      ConversationListAdapter.OnConversationClickListener,
                                                                      ConversationListSearchAdapter.EventListener,
                                                                      MainNavigator.BackHandler,
                                                                      MegaphoneActionController
{
  public static final short MESSAGE_REQUESTS_REQUEST_CODE_CREATE_NAME = 32562;
  public static final short SMS_ROLE_REQUEST_CODE                     = 32563;

  private static final String TAG = Log.tag(ConversationListFragment.class);

  private static final int MAXIMUM_PINNED_CONVERSATIONS = 4;

  private ActionMode                        actionMode;
  private RecyclerView                      list;
  private Stub<ReminderView>                reminderView;
  private Stub<ViewGroup>                   emptyState;
  private TextView                          searchEmptyState;
  private PulsingFloatingActionButton       fab;
  private PulsingFloatingActionButton       cameraFab;
  private Stub<SearchToolbar>               searchToolbar;
  private ImageView                         searchAction;
  private View                              toolbarShadow;
  private ConversationListViewModel         viewModel;
  private RecyclerView.Adapter              activeAdapter;
  private ConversationListAdapter           defaultAdapter;
  private ConversationListSearchAdapter     searchAdapter;
  private StickyHeaderDecoration            searchAdapterDecoration;
  private Stub<ViewGroup>                   megaphoneContainer;
  private SnapToTopDataObserver             snapToTopDataObserver;
  private Drawable                          archiveDrawable;
  private LifecycleObserver                 visibilityLifecycleObserver;

  private Stopwatch startupStopwatch;

  public static ConversationListFragment newInstance() {
    return new ConversationListFragment();
  }

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setHasOptionsMenu(true);
    startupStopwatch = new Stopwatch("startup");
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    return inflater.inflate(R.layout.conversation_list_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    list               = view.findViewById(R.id.list);
    fab                = view.findViewById(R.id.fab);
    cameraFab          = view.findViewById(R.id.camera_fab);
    searchEmptyState   = view.findViewById(R.id.search_no_results);
    searchAction       = view.findViewById(R.id.search_action);
    toolbarShadow      = view.findViewById(R.id.conversation_list_toolbar_shadow);
    reminderView       = new Stub<>(view.findViewById(R.id.reminder));
    emptyState         = new Stub<>(view.findViewById(R.id.empty_state));
    searchToolbar      = new Stub<>(view.findViewById(R.id.search_toolbar));
    megaphoneContainer = new Stub<>(view.findViewById(R.id.megaphone_container));

    Toolbar toolbar = getToolbar(view);
    toolbar.setVisibility(View.VISIBLE);
    ((AppCompatActivity) requireActivity()).setSupportActionBar(toolbar);

    fab.show();
    cameraFab.show();

    list.setLayoutManager(new LinearLayoutManager(requireActivity()));
    list.setItemAnimator(new DeleteItemAnimator());
    list.addOnScrollListener(new ScrollListener());

    snapToTopDataObserver = new SnapToTopDataObserver(list);

    new ItemTouchHelper(new ArchiveListenerCallback()).attachToRecyclerView(list);

    fab.setOnClickListener(v -> startActivity(new Intent(getActivity(), NewConversationActivity.class)));
    cameraFab.setOnClickListener(v -> {
      Permissions.with(requireActivity())
                 .request(Manifest.permission.CAMERA)
                 .ifNecessary()
                 .withRationaleDialog(getString(R.string.ConversationActivity_to_capture_photos_and_video_allow_signal_access_to_the_camera), R.drawable.ic_camera_24)
                 .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_needs_the_camera_permission_to_take_photos_or_video))
                 .onAllGranted(() -> startActivity(MediaSendActivity.buildCameraFirstIntent(requireActivity())))
                 .onAnyDenied(() -> Toast.makeText(requireContext(), R.string.ConversationActivity_signal_needs_camera_permissions_to_take_photos_or_video, Toast.LENGTH_LONG).show())
                 .execute();
    });

    initializeViewModel();
    initializeListAdapters();
    initializeTypingObserver();
    initializeSearchListener();

    RatingManager.showRatingDialogIfNecessary(requireContext());

    TooltipCompat.setTooltipText(searchAction, getText(R.string.SearchToolbar_search_for_conversations_contacts_and_messages));
  }

  @Override
  public void onResume() {
    super.onResume();

    updateReminders();
    EventBus.getDefault().register(this);

    if (TextSecurePreferences.isSmsEnabled(requireContext())) {
      InsightsLauncher.showInsightsModal(requireContext(), requireFragmentManager());
    }

    SimpleTask.run(getViewLifecycleOwner().getLifecycle(), Recipient::self, this::initializeProfileIcon);

    if ((!searchToolbar.resolved() || !searchToolbar.get().isVisible()) && list.getAdapter() != defaultAdapter) {
      list.removeItemDecoration(searchAdapterDecoration);
      setAdapter(defaultAdapter);
    }

    if (activeAdapter != null) {
      activeAdapter.notifyDataSetChanged();
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    ConversationFragment.prepare(requireContext());
    ProcessLifecycleOwner.get().getLifecycle().addObserver(visibilityLifecycleObserver);
  }

  @Override
  public void onPause() {
    super.onPause();

    fab.stopPulse();
    cameraFab.stopPulse();
    EventBus.getDefault().unregister(this);
  }

  @Override
  public void onStop() {
    super.onStop();
    ProcessLifecycleOwner.get().getLifecycle().removeObserver(visibilityLifecycleObserver);
  }

  @Override
  public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
    menu.clear();
    inflater.inflate(R.menu.text_secure_normal, menu);
  }

  @Override
  public void onPrepareOptionsMenu(Menu menu) {
    menu.findItem(R.id.menu_insights).setVisible(TextSecurePreferences.isSmsEnabled(requireContext()));
    menu.findItem(R.id.menu_clear_passphrase).setVisible(!TextSecurePreferences.isPasswordDisabled(requireContext()));
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
      case R.id.menu_new_group:         handleCreateGroup();     return true;
      case R.id.menu_settings:          handleDisplaySettings(); return true;
      case R.id.menu_clear_passphrase:  handleClearPassphrase(); return true;
      case R.id.menu_mark_all_read:     handleMarkAllRead();     return true;
      case R.id.menu_invite:            handleInvite();          return true;
      case R.id.menu_insights:          handleInsights();        return true;
    }

    return false;
  }

  @Override
  public boolean onBackPressed() {
    return closeSearchIfOpen();
  }

  private boolean closeSearchIfOpen() {
    if ((searchToolbar.resolved() && searchToolbar.get().isVisible()) || activeAdapter == searchAdapter) {
      list.removeItemDecoration(searchAdapterDecoration);
      setAdapter(defaultAdapter);
      searchToolbar.get().collapse();
      return true;
    }

    return false;
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (resultCode != RESULT_OK) {
      return;
    }

    if (requestCode == CreateKbsPinActivity.REQUEST_NEW_PIN) {
      Snackbar.make(fab, R.string.ConfirmKbsPinFragment__pin_created, Snackbar.LENGTH_LONG).setTextColor(Color.WHITE).show();
      viewModel.onMegaphoneCompleted(Megaphones.Event.PINS_FOR_ALL);
    }
  }

  @Override
  public void onConversationClicked(@NonNull ThreadRecord threadRecord) {
    hideKeyboard();
    getNavigator().goToConversation(threadRecord.getRecipient().getId(),
                                    threadRecord.getThreadId(),
                                    threadRecord.getDistributionType(),
                                    -1);
  }

  @Override
  public void onShowArchiveClick() {
    getNavigator().goToArchiveList();
  }

  @Override
  public void onContactClicked(@NonNull Recipient contact) {
    SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
      return DatabaseFactory.getThreadDatabase(getContext()).getThreadIdIfExistsFor(contact.getId());
    }, threadId -> {
      hideKeyboard();
      getNavigator().goToConversation(contact.getId(),
                                      threadId,
                                      ThreadDatabase.DistributionTypes.DEFAULT,
                                      -1);
    });
  }

  @Override
  public void onMessageClicked(@NonNull MessageResult message) {
    SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
      int startingPosition = DatabaseFactory.getMmsSmsDatabase(getContext()).getMessagePositionInConversation(message.threadId, message.receivedTimestampMs);
      return Math.max(0, startingPosition);
    }, startingPosition -> {
      hideKeyboard();
      getNavigator().goToConversation(message.conversationRecipient.getId(),
                                      message.threadId,
                                      ThreadDatabase.DistributionTypes.DEFAULT,
                                      startingPosition);
    });
  }

  @Override
  public void onMegaphoneNavigationRequested(@NonNull Intent intent) {
    startActivity(intent);
  }

  @Override
  public void onMegaphoneNavigationRequested(@NonNull Intent intent, int requestCode) {
    startActivityForResult(intent, requestCode);
  }

  @Override
  public void onMegaphoneToastRequested(@NonNull String string) {
    Snackbar.make(fab, string, Snackbar.LENGTH_LONG)
            .setTextColor(Color.WHITE)
            .show();
  }

  @Override
  public @NonNull Activity getMegaphoneActivity() {
    return requireActivity();
  }

  @Override
  public void onMegaphoneSnooze(@NonNull Megaphones.Event event) {
    viewModel.onMegaphoneSnoozed(event);
  }

  @Override
  public void onMegaphoneCompleted(@NonNull Megaphones.Event event) {
    viewModel.onMegaphoneCompleted(event);
  }

  @Override
  public void onMegaphoneDialogFragmentRequested(@NonNull DialogFragment dialogFragment) {
    dialogFragment.show(getChildFragmentManager(), "megaphone_dialog");
  }

  private void initializeReminderView() {
    reminderView.get().setOnDismissListener(this::updateReminders);
    reminderView.get().setOnActionClickListener(this::onReminderAction);
  }

  private void onReminderAction(@IdRes int reminderActionId) {
    if (reminderActionId == R.id.reminder_action_update_now) {
      PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(requireContext());
    }
  }

  private void hideKeyboard() {
    InputMethodManager imm = ServiceUtil.getInputMethodManager(requireContext());
    imm.hideSoftInputFromWindow(requireView().getWindowToken(), 0);
  }

  private void initializeProfileIcon(@NonNull Recipient recipient) {
    ImageView icon = requireView().findViewById(R.id.toolbar_icon);

    AvatarUtil.loadIconIntoImageView(recipient, icon);
    icon.setOnClickListener(v -> getNavigator().goToAppSettings());
  }

  private void initializeSearchListener() {
    searchAction.setOnClickListener(v -> {
      searchToolbar.get().display(searchAction.getX() + (searchAction.getWidth() / 2.0f),
                                  searchAction.getY() + (searchAction.getHeight() / 2.0f));

      searchToolbar.get().setListener(new SearchToolbar.SearchListener() {
        @Override
        public void onSearchTextChange(String text) {
          String trimmed = text.trim();

          viewModel.updateQuery(trimmed);

          if (trimmed.length() > 0) {
            if (activeAdapter != searchAdapter) {
              setAdapter(searchAdapter);
              list.removeItemDecoration(searchAdapterDecoration);
              list.addItemDecoration(searchAdapterDecoration);
            }
          } else {
            if (activeAdapter != defaultAdapter) {
              list.removeItemDecoration(searchAdapterDecoration);
              setAdapter(defaultAdapter);
            }
          }
        }

        @Override
        public void onSearchClosed() {
          list.removeItemDecoration(searchAdapterDecoration);
          setAdapter(defaultAdapter);
        }
      });
    });
  }

  private void initializeListAdapters() {
    defaultAdapter          = new ConversationListAdapter(GlideApp.with(this), this);
    searchAdapter           = new ConversationListSearchAdapter(GlideApp.with(this), this, Locale.getDefault());
    searchAdapterDecoration = new StickyHeaderDecoration(searchAdapter, false, false);

    setAdapter(defaultAdapter);

    defaultAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
      @Override
      public void onItemRangeInserted(int positionStart, int itemCount) {
        startupStopwatch.split("data-set");
        defaultAdapter.unregisterAdapterDataObserver(this);
        list.post(() -> {
          AppStartup.getInstance().onCriticalRenderEventEnd();
          startupStopwatch.split("first-render");
          startupStopwatch.stop(TAG);
        });
      }
    });
  }

  @SuppressWarnings("rawtypes")
  private void setAdapter(@NonNull RecyclerView.Adapter adapter) {
    RecyclerView.Adapter oldAdapter = activeAdapter;

    activeAdapter = adapter;

    if (oldAdapter == activeAdapter) {
      return;
    }

    if (adapter instanceof ConversationListAdapter) {
      ((ConversationListAdapter) adapter).setPagingController(viewModel.getPagingController());
    }

    list.setAdapter(adapter);

    if (adapter == defaultAdapter) {
      defaultAdapter.registerAdapterDataObserver(snapToTopDataObserver);
    } else {
      defaultAdapter.unregisterAdapterDataObserver(snapToTopDataObserver);
    }
  }

  private void initializeTypingObserver() {
    ApplicationDependencies.getTypingStatusRepository().getTypingThreads().observe(this, threadIds -> {
      if (threadIds == null) {
        threadIds = Collections.emptySet();
      }

      defaultAdapter.setTypingThreads(threadIds);
    });
  }

  protected boolean isArchived() {
    return false;
  }

  private void initializeViewModel() {
    viewModel = ViewModelProviders.of(this, new ConversationListViewModel.Factory(isArchived())).get(ConversationListViewModel.class);

    viewModel.getSearchResult().observe(getViewLifecycleOwner(), this::onSearchResultChanged);
    viewModel.getMegaphone().observe(getViewLifecycleOwner(), this::onMegaphoneChanged);
    viewModel.getConversationList().observe(getViewLifecycleOwner(), this::onSubmitList);
    viewModel.hasNoConversations().observe(getViewLifecycleOwner(), this::updateEmptyState);

    visibilityLifecycleObserver = new DefaultLifecycleObserver() {
      @Override
      public void onStart(@NonNull LifecycleOwner owner) {
        viewModel.onVisible();
      }
    };
  }

  private void onSearchResultChanged(@Nullable SearchResult result) {
    result = result != null ? result : SearchResult.EMPTY;
    searchAdapter.updateResults(result);

    if (result.isEmpty() && activeAdapter == searchAdapter) {
      searchEmptyState.setText(getString(R.string.SearchFragment_no_results, result.getQuery()));
      searchEmptyState.setVisibility(View.VISIBLE);
    } else {
      searchEmptyState.setVisibility(View.GONE);
    }
  }

  private void onMegaphoneChanged(@Nullable Megaphone megaphone) {
    if (megaphone == null) {
      if (megaphoneContainer.resolved()) {
        megaphoneContainer.get().setVisibility(View.GONE);
        megaphoneContainer.get().removeAllViews();
      }
      return;
    }

    View view = MegaphoneViewBuilder.build(requireContext(), megaphone, this);

    megaphoneContainer.get().removeAllViews();

    if (view != null) {
      megaphoneContainer.get().addView(view);
      megaphoneContainer.get().setVisibility(View.VISIBLE);
    } else {
      megaphoneContainer.get().setVisibility(View.GONE);

      if (megaphone.getOnVisibleListener() != null) {
        megaphone.getOnVisibleListener().onEvent(megaphone, this);
      }
    }

    viewModel.onMegaphoneVisible(megaphone);
  }

  private void updateReminders() {
    Context context = requireContext();

    SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
      if (UnauthorizedReminder.isEligible(context)) {
        return Optional.of(new UnauthorizedReminder(context));
      } else if (ExpiredBuildReminder.isEligible()) {
        return Optional.of(new ExpiredBuildReminder(context));
      } else if (ServiceOutageReminder.isEligible(context)) {
        ApplicationDependencies.getJobManager().add(new ServiceOutageDetectionJob());
        return Optional.of(new ServiceOutageReminder(context));
      } else if (OutdatedBuildReminder.isEligible()) {
        return Optional.of(new OutdatedBuildReminder(context));
      } else if (PushRegistrationReminder.isEligible(context)) {
        return Optional.of((new PushRegistrationReminder(context)));
      } else if (DozeReminder.isEligible(context)) {
        return Optional.of(new DozeReminder(context));
      } else {
        return Optional.<Reminder>absent();
      }
    }, reminder -> {
      if (reminder.isPresent() && getActivity() != null && !isRemoving()) {
        if (!reminderView.resolved()) {
          initializeReminderView();
        }
        reminderView.get().showReminder(reminder.get());
      } else if (reminderView.resolved() && !reminder.isPresent()) {
        reminderView.get().hide();
      }
    });
  }

  private void handleCreateGroup() {
    getNavigator().goToGroupCreation();
  }

  private void handleDisplaySettings() {
    getNavigator().goToAppSettings();
  }

  private void handleClearPassphrase() {
    Intent intent = new Intent(requireActivity(), KeyCachingService.class);
    intent.setAction(KeyCachingService.CLEAR_KEY_ACTION);
    requireActivity().startService(intent);
  }

  private void handleMarkAllRead() {
    Context context = requireContext();

    SignalExecutors.BOUNDED.execute(() -> {
      List<MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context).setAllThreadsRead();

      ApplicationDependencies.getMessageNotifier().updateNotification(context);
      MarkReadReceiver.process(context, messageIds);
    });
  }

  private void handleMarkSelectedAsRead() {
    Context   context               = requireContext();
    Set<Long> selectedConversations = new HashSet<>(defaultAdapter.getBatchSelectionIds());

    SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
      List<MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context).setRead(selectedConversations, false);

      ApplicationDependencies.getMessageNotifier().updateNotification(context);
      MarkReadReceiver.process(context, messageIds);

      return null;
    }, none -> {
      if (actionMode != null) {
        actionMode.finish();
        actionMode = null;
      }
    });
  }

  private void handleMarkSelectedAsUnread() {
    Context   context               = requireContext();
    Set<Long> selectedConversations = new HashSet<>(defaultAdapter.getBatchSelectionIds());

    SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
      DatabaseFactory.getThreadDatabase(context).setForcedUnread(selectedConversations);
      StorageSyncHelper.scheduleSyncForDataChange();
      return null;
    }, none -> {
      if (actionMode != null) {
        actionMode.finish();
        actionMode = null;
      }
    });
  }

  private void handleInvite() {
    getNavigator().goToInvite();
  }

  private void handleInsights() {
    getNavigator().goToInsights();
  }

  @SuppressLint("StaticFieldLeak")
  private void handleArchiveAllSelected() {
    Set<Long> selectedConversations = new HashSet<>(defaultAdapter.getBatchSelectionIds());
    int       count                 = selectedConversations.size();
    String    snackBarTitle         = getResources().getQuantityString(getArchivedSnackbarTitleRes(), count, count);

    new SnackbarAsyncTask<Void>(getViewLifecycleOwner().getLifecycle(),
                                requireView(),
                                snackBarTitle,
                                getString(R.string.ConversationListFragment_undo),
                                getResources().getColor(R.color.amber_500),
                                Snackbar.LENGTH_LONG, true)
    {

      @Override
      protected void onPostExecute(Void result) {
        super.onPostExecute(result);

        if (actionMode != null) {
          actionMode.finish();
          actionMode = null;
        }
      }

      @Override
      protected void executeAction(@Nullable Void parameter) {
        archiveThreads(selectedConversations);
      }

      @Override
      protected void reverseAction(@Nullable Void parameter) {
        reverseArchiveThreads(selectedConversations);
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  @SuppressLint("StaticFieldLeak")
  private void handleDeleteAllSelected() {
    int                 conversationsCount = defaultAdapter.getBatchSelectionIds().size();
    AlertDialog.Builder alert              = new AlertDialog.Builder(getActivity());
    alert.setIcon(R.drawable.ic_warning);
    alert.setTitle(getActivity().getResources().getQuantityString(R.plurals.ConversationListFragment_delete_selected_conversations,
                                                                  conversationsCount, conversationsCount));
    alert.setMessage(getActivity().getResources().getQuantityString(R.plurals.ConversationListFragment_this_will_permanently_delete_all_n_selected_conversations,
                                                                    conversationsCount, conversationsCount));
    alert.setCancelable(true);

    alert.setPositiveButton(R.string.delete, (dialog, which) -> {
      final Set<Long> selectedConversations = defaultAdapter.getBatchSelectionIds();

      if (!selectedConversations.isEmpty()) {
        new AsyncTask<Void, Void, Void>() {
          private ProgressDialog dialog;

          @Override
          protected void onPreExecute() {
            dialog = ProgressDialog.show(getActivity(),
                                         getActivity().getString(R.string.ConversationListFragment_deleting),
                                         getActivity().getString(R.string.ConversationListFragment_deleting_selected_conversations),
                                         true, false);
          }

          @Override
          protected Void doInBackground(Void... params) {
            DatabaseFactory.getThreadDatabase(getActivity()).deleteConversations(selectedConversations);
            ApplicationDependencies.getMessageNotifier().updateNotification(getActivity());
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
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
      }
    });

    alert.setNegativeButton(android.R.string.cancel, null);
    alert.show();
  }

  private void handlePinAllSelected() {
    final Set<Long> toPin = new LinkedHashSet<>(Stream.of(defaultAdapter.getBatchSelection())
                                                      .filterNot(conversation -> conversation.getThreadRecord().isPinned())
                                                      .map(conversation -> conversation.getThreadRecord().getThreadId())
                                                      .toList());

    if (toPin.size() + viewModel.getPinnedCount() > MAXIMUM_PINNED_CONVERSATIONS) {
      Snackbar.make(fab,
                    getString(R.string.conversation_list__you_can_only_pin_up_to_d_chats, MAXIMUM_PINNED_CONVERSATIONS),
                    Snackbar.LENGTH_LONG)
              .setTextColor(Color.WHITE)
              .show();
      actionMode.finish();
      return;
    }

    SimpleTask.run(SignalExecutors.BOUNDED, () -> {
      ThreadDatabase db = DatabaseFactory.getThreadDatabase(ApplicationDependencies.getApplication());

      db.pinConversations(toPin);

      return null;
    }, unused -> {
      if (actionMode != null) {
        actionMode.finish();
      }
    });
  }

  private void handleUnpinAllSelected() {
    final Set<Long> toPin = new HashSet<>(defaultAdapter.getBatchSelectionIds());

    SimpleTask.run(SignalExecutors.BOUNDED, () -> {
      ThreadDatabase db = DatabaseFactory.getThreadDatabase(ApplicationDependencies.getApplication());

      db.unpinConversations(toPin);

      return null;
    }, unused -> {
      if (actionMode != null) {
        actionMode.finish();
      }
    });
  }

  private void handleSelectAllThreads() {
    defaultAdapter.selectAllThreads();
    actionMode.setTitle(String.valueOf(defaultAdapter.getBatchSelectionIds().size()));
  }

  private void handleCreateConversation(long threadId, Recipient recipient, int distributionType) {
    getNavigator().goToConversation(recipient.getId(), threadId, distributionType, -1);
  }

  private void onSubmitList(@NonNull List<Conversation> conversationList) {
    defaultAdapter.submitList(conversationList);
    onPostSubmitList(conversationList.size());
  }

  void updateEmptyState(boolean isConversationEmpty) {
    if (isConversationEmpty) {
      Log.i(TAG, "Received an empty data set.");
      list.setVisibility(View.INVISIBLE);
      emptyState.get().setVisibility(View.VISIBLE);
      fab.startPulse(3 * 1000);
      cameraFab.startPulse(3 * 1000);

      SignalStore.onboarding().setShowNewGroup(true);
      SignalStore.onboarding().setShowInviteFriends(true);
    } else {
      list.setVisibility(View.VISIBLE);
      fab.stopPulse();
      cameraFab.stopPulse();

      if (emptyState.resolved()) {
        emptyState.get().setVisibility(View.GONE);
      }
    }
  }

  protected void onPostSubmitList(int conversationCount) {
    if (conversationCount >= 6 && (SignalStore.onboarding().shouldShowInviteFriends() || SignalStore.onboarding().shouldShowNewGroup())) {
      SignalStore.onboarding().clearAll();
      ApplicationDependencies.getMegaphoneRepository().markFinished(Megaphones.Event.ONBOARDING);
    }
  }

  @Override
  public void onConversationClick(Conversation conversation) {
    if (actionMode == null) {
      handleCreateConversation(conversation.getThreadRecord().getThreadId(), conversation.getThreadRecord().getRecipient(), conversation.getThreadRecord().getDistributionType());
    } else {
      defaultAdapter.toggleConversationInBatchSet(conversation);

      if (defaultAdapter.getBatchSelectionIds().size() == 0) {
        actionMode.finish();
      } else {
        actionMode.setTitle(String.valueOf(defaultAdapter.getBatchSelectionIds().size()));
        setCorrectMenuVisibility(actionMode.getMenu());
      }
    }
  }

  @Override
  public boolean onConversationLongClick(Conversation conversation) {
    if (actionMode != null) {
      onConversationClick(conversation);
      return true;
    }

    defaultAdapter.initializeBatchMode(true);
    defaultAdapter.toggleConversationInBatchSet(conversation);

    actionMode = ((AppCompatActivity) getActivity()).startSupportActionMode(ConversationListFragment.this);

    return true;
  }

  @Override
  public boolean onCreateActionMode(ActionMode mode, Menu menu) {
    MenuInflater inflater = getActivity().getMenuInflater();

    inflater.inflate(R.menu.conversation_list_batch_pin, menu);
    inflater.inflate(getActionModeMenuRes(), menu);
    inflater.inflate(R.menu.conversation_list_batch, menu);

    mode.setTitle("1");

    WindowUtil.setStatusBarColor(requireActivity().getWindow(), getResources().getColor(R.color.action_mode_status_bar));

    return true;
  }

  @Override
  public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
    setCorrectMenuVisibility(menu);
    return false;
  }

  @Override
  public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
    switch (item.getItemId()) {
      case R.id.menu_select_all:       handleSelectAllThreads();     return true;
      case R.id.menu_delete_selected:  handleDeleteAllSelected();    return true;
      case R.id.menu_pin_selected:     handlePinAllSelected();       return true;
      case R.id.menu_unpin_selected:   handleUnpinAllSelected();     return true;
      case R.id.menu_archive_selected: handleArchiveAllSelected();   return true;
      case R.id.menu_mark_as_read:     handleMarkSelectedAsRead();   return true;
      case R.id.menu_mark_as_unread:   handleMarkSelectedAsUnread(); return true;
    }

    return false;
  }

  @Override
  public void onDestroyActionMode(ActionMode mode) {
    defaultAdapter.initializeBatchMode(false);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      TypedArray color = getActivity().getTheme().obtainStyledAttributes(new int[] {android.R.attr.statusBarColor});
      WindowUtil.setStatusBarColor(getActivity().getWindow(), color.getColor(0, Color.BLACK));
      color.recycle();
    }

    if (Build.VERSION.SDK_INT >= 23) {
      TypedArray lightStatusBarAttr = getActivity().getTheme().obtainStyledAttributes(new int[] {android.R.attr.windowLightStatusBar});
      int        current            = getActivity().getWindow().getDecorView().getSystemUiVisibility();
      int        statusBarMode      = lightStatusBarAttr.getBoolean(0, false) ? current | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                                                                              : current & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;

      getActivity().getWindow().getDecorView().setSystemUiVisibility(statusBarMode);

      lightStatusBarAttr.recycle();
    }

    actionMode = null;
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onEvent(ReminderUpdateEvent event) {
    updateReminders();
  }

  @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
  public void onEvent(MessageSender.MessageSentEvent event) {
    EventBus.getDefault().removeStickyEvent(event);
    closeSearchIfOpen();
  }

  private void setCorrectMenuVisibility(@NonNull Menu menu) {
    boolean hasUnread   = Stream.of(defaultAdapter.getBatchSelection()).anyMatch(conversation -> !conversation.getThreadRecord().isRead());
    boolean hasUnpinned = Stream.of(defaultAdapter.getBatchSelection()).anyMatch(conversation -> !conversation.getThreadRecord().isPinned());
    boolean canPin      = viewModel.getPinnedCount() < MAXIMUM_PINNED_CONVERSATIONS;

    if (hasUnread) {
      menu.findItem(R.id.menu_mark_as_unread).setVisible(false);
      menu.findItem(R.id.menu_mark_as_read).setVisible(true);
    } else {
      menu.findItem(R.id.menu_mark_as_unread).setVisible(true);
      menu.findItem(R.id.menu_mark_as_read).setVisible(false);
    }

    if (!isArchived() && hasUnpinned && canPin) {
      menu.findItem(R.id.menu_pin_selected).setVisible(true);
      menu.findItem(R.id.menu_unpin_selected).setVisible(false);
    } else if (!isArchived() && !hasUnpinned) {
      menu.findItem(R.id.menu_pin_selected).setVisible(false);
      menu.findItem(R.id.menu_unpin_selected).setVisible(true);
    } else {
      menu.findItem(R.id.menu_pin_selected).setVisible(false);
      menu.findItem(R.id.menu_unpin_selected).setVisible(false);
    }
  }

  protected Toolbar getToolbar(@NonNull View rootView) {
    return rootView.findViewById(R.id.toolbar);
  }

  protected @PluralsRes int getArchivedSnackbarTitleRes() {
    return R.plurals.ConversationListFragment_conversations_archived;
  }

  protected @MenuRes int getActionModeMenuRes() {
    return R.menu.conversation_list_batch_archive;
  }

  protected @DrawableRes int getArchiveIconRes() {
    return R.drawable.ic_archive_white_36dp;
  }

  @WorkerThread
  protected void archiveThreads(Set<Long> threadIds) {
    DatabaseFactory.getThreadDatabase(getActivity()).setArchived(threadIds, true);
  }

  @WorkerThread
  protected void reverseArchiveThreads(Set<Long> threadIds) {
    DatabaseFactory.getThreadDatabase(getActivity()).setArchived(threadIds, false);
  }

  @SuppressLint("StaticFieldLeak")
  protected void onItemSwiped(long threadId, int unreadCount) {
    new SnackbarAsyncTask<Long>(getViewLifecycleOwner().getLifecycle(),
                                requireView(),
                                getResources().getQuantityString(R.plurals.ConversationListFragment_conversations_archived, 1, 1),
                                getString(R.string.ConversationListFragment_undo),
                                getResources().getColor(R.color.amber_500),
                                Snackbar.LENGTH_LONG,
                                false)
    {
      @Override
      protected void executeAction(@Nullable Long parameter) {
        DatabaseFactory.getThreadDatabase(getActivity()).archiveConversation(threadId);

        if (unreadCount > 0) {
          List<MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(getActivity()).setRead(threadId, false);
          ApplicationDependencies.getMessageNotifier().updateNotification(getActivity());
          MarkReadReceiver.process(getActivity(), messageIds);
        }
      }

      @Override
      protected void reverseAction(@Nullable Long parameter) {
        DatabaseFactory.getThreadDatabase(getActivity()).unarchiveConversation(threadId);

        if (unreadCount > 0) {
          DatabaseFactory.getThreadDatabase(getActivity()).incrementUnread(threadId, unreadCount);
          ApplicationDependencies.getMessageNotifier().updateNotification(getActivity());
        }
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, threadId);
  }

  private class ArchiveListenerCallback extends ItemTouchHelper.SimpleCallback {

    ArchiveListenerCallback() {
      super(0, ItemTouchHelper.RIGHT);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder,
                          @NonNull RecyclerView.ViewHolder target)
    {
      return false;
    }

    @Override
    public int getSwipeDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
      if (viewHolder.itemView instanceof ConversationListItemAction      ||
          viewHolder instanceof ConversationListAdapter.HeaderViewHolder ||
          actionMode != null                                             ||
          activeAdapter == searchAdapter)
      {
        return 0;
      }

      return super.getSwipeDirs(recyclerView, viewHolder);
    }

    @SuppressLint("StaticFieldLeak")
    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
      if (viewHolder.itemView instanceof ConversationListItemInboxZero) return;
      final long threadId    = ((ConversationListItem)viewHolder.itemView).getThreadId();
      final int  unreadCount = ((ConversationListItem)viewHolder.itemView).getUnreadCount();

      onItemSwiped(threadId, unreadCount);
    }

    @Override
    public void onChildDraw(@NonNull Canvas canvas, @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder,
                            float dX, float dY, int actionState,
                            boolean isCurrentlyActive)
    {
      if (viewHolder.itemView instanceof ConversationListItemInboxZero) return;
      if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
        View  itemView = viewHolder.itemView;
        float alpha    = 1.0f - Math.abs(dX) / (float) viewHolder.itemView.getWidth();

        if (dX > 0) {
          Resources resources = getResources();

          if (archiveDrawable == null) {
            archiveDrawable = ResourcesCompat.getDrawable(resources, getArchiveIconRes(), requireActivity().getTheme());
            Objects.requireNonNull(archiveDrawable).setBounds(0, 0, archiveDrawable.getIntrinsicWidth(), archiveDrawable.getIntrinsicHeight());
          }

          canvas.save();
          canvas.clipRect(itemView.getLeft(), itemView.getTop(), dX, itemView.getBottom());

          canvas.drawColor(alpha > 0 ? resources.getColor(R.color.green_500) : Color.WHITE);

          canvas.translate(itemView.getLeft() + resources.getDimension(R.dimen.conversation_list_fragment_archive_padding),
                           itemView.getTop() + (itemView.getBottom() - itemView.getTop() - archiveDrawable.getIntrinsicHeight()) / 2f);

          archiveDrawable.draw(canvas);
          canvas.restore();
        }

        viewHolder.itemView.setAlpha(alpha);
        viewHolder.itemView.setTranslationX(dX);
      } else {
        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
      }
    }
  }

  private class ScrollListener extends RecyclerView.OnScrollListener {
    @Override
    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
      if (recyclerView.canScrollVertically(-1)) {
        if (toolbarShadow.getVisibility() != View.VISIBLE) {
          ViewUtil.fadeIn(toolbarShadow, 250);
        }
      } else {
        if (toolbarShadow.getVisibility() != View.GONE) {
          ViewUtil.fadeOut(toolbarShadow, 250);
        }
      }
    }
  }
}


