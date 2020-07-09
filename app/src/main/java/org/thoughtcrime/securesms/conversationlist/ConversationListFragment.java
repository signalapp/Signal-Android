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
import androidx.lifecycle.DefaultLifecycleObserver;
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
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.MainFragment;
import org.thoughtcrime.securesms.MainNavigator;
import org.thoughtcrime.securesms.NewConversationActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.RatingManager;
import org.thoughtcrime.securesms.components.SearchToolbar;
import org.thoughtcrime.securesms.components.recyclerview.DeleteItemAnimator;
import org.thoughtcrime.securesms.components.registration.PulsingFloatingActionButton;
import org.thoughtcrime.securesms.components.reminder.DefaultSmsReminder;
import org.thoughtcrime.securesms.components.reminder.DozeReminder;
import org.thoughtcrime.securesms.components.reminder.ExpiredBuildReminder;
import org.thoughtcrime.securesms.components.reminder.OutdatedBuildReminder;
import org.thoughtcrime.securesms.components.reminder.PushRegistrationReminder;
import org.thoughtcrime.securesms.components.reminder.Reminder;
import org.thoughtcrime.securesms.components.reminder.ReminderView;
import org.thoughtcrime.securesms.components.reminder.ServiceOutageReminder;
import org.thoughtcrime.securesms.components.reminder.ShareReminder;
import org.thoughtcrime.securesms.components.reminder.SystemSmsImportReminder;
import org.thoughtcrime.securesms.components.reminder.UnauthorizedReminder;
import org.thoughtcrime.securesms.conversation.ConversationFragment;
import org.thoughtcrime.securesms.conversationlist.model.Conversation;
import org.thoughtcrime.securesms.conversationlist.model.MessageResult;
import org.thoughtcrime.securesms.conversationlist.model.SearchResult;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessagingDatabase.MarkedMessageInfo;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.events.ReminderUpdateEvent;
import org.thoughtcrime.securesms.insights.InsightsLauncher;
import org.thoughtcrime.securesms.jobs.ServiceOutageDetectionJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.v2.CreateKbsPinActivity;
import org.thoughtcrime.securesms.logging.Log;
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
import org.thoughtcrime.securesms.util.AvatarUtil;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.SnapToTopDataObserver;
import org.thoughtcrime.securesms.util.StickyHeaderDecoration;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.util.task.SnackbarAsyncTask;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Collections;
import java.util.HashSet;
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

  private static final String TAG = Log.tag(ConversationListFragment.class);

  private static final int[] EMPTY_IMAGES = new int[] { R.drawable.empty_inbox_1,
                                                        R.drawable.empty_inbox_2,
                                                        R.drawable.empty_inbox_3,
                                                        R.drawable.empty_inbox_4,
                                                        R.drawable.empty_inbox_5 };

  private ActionMode                        actionMode;
  private RecyclerView                      list;
  private ReminderView                      reminderView;
  private View                              emptyState;
  private ImageView                         emptyImage;
  private TextView                          searchEmptyState;
  private PulsingFloatingActionButton       fab;
  private PulsingFloatingActionButton       cameraFab;
  private SearchToolbar                     searchToolbar;
  private ImageView                         searchAction;
  private View                              toolbarShadow;
  private ConversationListViewModel         viewModel;
  private RecyclerView.Adapter              activeAdapter;
  private ConversationListAdapter           defaultAdapter;
  private ConversationListSearchAdapter     searchAdapter;
  private StickyHeaderDecoration            searchAdapterDecoration;
  private ViewGroup                         megaphoneContainer;
  private SnapToTopDataObserver             snapToTopDataObserver;
  private Drawable                          archiveDrawable;

  public static ConversationListFragment newInstance() {
    return new ConversationListFragment();
  }

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setHasOptionsMenu(true);
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    return inflater.inflate(R.layout.conversation_list_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    reminderView       = view.findViewById(R.id.reminder);
    list               = view.findViewById(R.id.list);
    fab                = view.findViewById(R.id.fab);
    cameraFab          = view.findViewById(R.id.camera_fab);
    emptyState         = view.findViewById(R.id.empty_state);
    emptyImage         = view.findViewById(R.id.empty);
    searchEmptyState   = view.findViewById(R.id.search_no_results);
    searchToolbar      = view.findViewById(R.id.search_toolbar);
    searchAction       = view.findViewById(R.id.search_action);
    toolbarShadow      = view.findViewById(R.id.conversation_list_toolbar_shadow);
    megaphoneContainer = view.findViewById(R.id.megaphone_container);

    Toolbar toolbar = view.findViewById(getToolbarRes());
    toolbar.setVisibility(View.VISIBLE);
    ((AppCompatActivity) requireActivity()).setSupportActionBar(toolbar);

    fab.show();
    cameraFab.show();

    reminderView.setOnDismissListener(this::updateReminders);

    list.setHasFixedSize(true);
    list.setLayoutManager(new LinearLayoutManager(requireActivity()));
    list.setItemAnimator(new DeleteItemAnimator());
    list.addOnScrollListener(new ScrollListener());

    snapToTopDataObserver = new SnapToTopDataObserver(list, null);

    new ItemTouchHelper(new ArchiveListenerCallback()).attachToRecyclerView(list);

    fab.setOnClickListener(v -> startActivity(new Intent(getActivity(), NewConversationActivity.class)));
    cameraFab.setOnClickListener(v -> {
      Permissions.with(requireActivity())
                 .request(Manifest.permission.CAMERA)
                 .ifNecessary()
                 .withRationaleDialog(getString(R.string.ConversationActivity_to_capture_photos_and_video_allow_signal_access_to_the_camera), R.drawable.ic_camera_solid_24)
                 .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_needs_the_camera_permission_to_take_photos_or_video))
                 .onAllGranted(() -> startActivity(MediaSendActivity.buildCameraFirstIntent(requireActivity())))
                 .onAnyDenied(() -> Toast.makeText(requireContext(), R.string.ConversationActivity_signal_needs_camera_permissions_to_take_photos_or_video, Toast.LENGTH_LONG).show())
                 .execute();
    });

    initializeListAdapters();
    initializeViewModel();
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

    SimpleTask.run(getLifecycle(), Recipient::self, this::initializeProfileIcon);

    if (!searchToolbar.isVisible() && list.getAdapter() != defaultAdapter) {
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
  }

  @Override
  public void onPause() {
    super.onPause();

    fab.stopPulse();
    cameraFab.stopPulse();
    EventBus.getDefault().unregister(this);
  }


  @Override
  public void onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = requireActivity().getMenuInflater();
    menu.clear();

    inflater.inflate(R.menu.text_secure_normal, menu);

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
    if (searchToolbar.isVisible() || activeAdapter == searchAdapter) {
      list.removeItemDecoration(searchAdapterDecoration);
      setAdapter(defaultAdapter);
      searchToolbar.collapse();
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
      return DatabaseFactory.getThreadDatabase(getContext()).getThreadIdIfExistsFor(contact);
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
      searchToolbar.display(searchAction.getX() + (searchAction.getWidth() / 2.0f),
                            searchAction.getY() + (searchAction.getHeight() / 2.0f));
    });

    searchToolbar.setListener(new SearchToolbar.SearchListener() {
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
  }

  private void initializeListAdapters() {
    defaultAdapter          = new ConversationListAdapter(GlideApp.with(this), this);
    searchAdapter           = new ConversationListSearchAdapter(GlideApp.with(this), this, Locale.getDefault());
    searchAdapterDecoration = new StickyHeaderDecoration(searchAdapter, false, false);

    setAdapter(defaultAdapter);
  }

  @SuppressWarnings("rawtypes")
  private void setAdapter(@NonNull RecyclerView.Adapter adapter) {
    RecyclerView.Adapter oldAdapter = activeAdapter;

    activeAdapter = adapter;

    if (oldAdapter == activeAdapter) {
      return;
    }

    list.setAdapter(adapter);

    if (adapter == defaultAdapter) {
      defaultAdapter.registerAdapterDataObserver(snapToTopDataObserver);
    } else {
      defaultAdapter.unregisterAdapterDataObserver(snapToTopDataObserver);
    }
  }

  private void initializeTypingObserver() {
    ApplicationContext.getInstance(requireContext()).getTypingStatusRepository().getTypingThreads().observe(this, threadIds -> {
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

    viewModel.getSearchResult().observe(this, this::onSearchResultChanged);
    viewModel.getMegaphone().observe(this, this::onMegaphoneChanged);
    viewModel.getConversationList().observe(this, this::onSubmitList);

    ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
      @Override
      public void onStart(@NonNull LifecycleOwner owner) {
        viewModel.onVisible();
      }
    });
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
      megaphoneContainer.setVisibility(View.GONE);
      megaphoneContainer.removeAllViews();
      return;
    }

    View view = MegaphoneViewBuilder.build(requireContext(), megaphone, this);

    megaphoneContainer.removeAllViews();

    if (view != null) {
      megaphoneContainer.addView(view);
      megaphoneContainer.setVisibility(View.VISIBLE);
    } else {
      megaphoneContainer.setVisibility(View.GONE);

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
      } else if (DefaultSmsReminder.isEligible(context)) {
        return Optional.of(new DefaultSmsReminder(context));
      } else if (Util.isDefaultSmsProvider(context) && SystemSmsImportReminder.isEligible(context)) {
        return Optional.of((new SystemSmsImportReminder(context)));
      } else if (PushRegistrationReminder.isEligible(context)) {
        return Optional.of((new PushRegistrationReminder(context)));
      } else if (ShareReminder.isEligible(context)) {
        return Optional.of(new ShareReminder(context));
      } else if (DozeReminder.isEligible(context)) {
        return Optional.of(new DozeReminder(context));
      } else {
        return Optional.<Reminder>absent();
      }
    }, reminder -> {
      if (reminder.isPresent() && getActivity() != null && !isRemoving()) {
        reminderView.showReminder(reminder.get());
      } else if (!reminder.isPresent()) {
        reminderView.hide();
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

    new SnackbarAsyncTask<Void>(getView(),
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
        for (long threadId : selectedConversations) {
          archiveThread(threadId);
        }
      }

      @Override
      protected void reverseAction(@Nullable Void parameter) {
        for (long threadId : selectedConversations) {
          reverseArchiveThread(threadId);
        }
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  @SuppressLint("StaticFieldLeak")
  private void handleDeleteAllSelected() {
    int                 conversationsCount = defaultAdapter.getBatchSelectionIds().size();
    AlertDialog.Builder alert              = new AlertDialog.Builder(getActivity());
    alert.setIconAttribute(R.attr.dialog_alert_icon);
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

  private void handleSelectAllThreads() {
    defaultAdapter.selectAllThreads();
    actionMode.setTitle(String.valueOf(defaultAdapter.getBatchSelectionIds().size()));
  }

  private void handleCreateConversation(long threadId, Recipient recipient, int distributionType) {
    getNavigator().goToConversation(recipient.getId(), threadId, distributionType, -1);
  }

  private void onSubmitList(@NonNull ConversationListViewModel.ConversationList conversationList) {
    if (conversationList.isEmpty()) {
      list.setVisibility(View.INVISIBLE);
      emptyState.setVisibility(View.VISIBLE);
      emptyImage.setImageResource(EMPTY_IMAGES[(int) (Math.random() * EMPTY_IMAGES.length)]);
      fab.startPulse(3 * 1000);
      cameraFab.startPulse(3 * 1000);
    } else {
      list.setVisibility(View.VISIBLE);
      emptyState.setVisibility(View.GONE);
      fab.stopPulse();
      cameraFab.stopPulse();
    }

    defaultAdapter.submitList(conversationList.getConversations());
    defaultAdapter.updateArchived(conversationList.getArchivedCount());

    onPostSubmitList();
  }

  protected void onPostSubmitList() {
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
    actionMode = ((AppCompatActivity) getActivity()).startSupportActionMode(ConversationListFragment.this);

    defaultAdapter.initializeBatchMode(true);
    defaultAdapter.toggleConversationInBatchSet(conversation);

    return true;
  }

  @Override
  public boolean onCreateActionMode(ActionMode mode, Menu menu) {
    MenuInflater inflater = getActivity().getMenuInflater();

    inflater.inflate(getActionModeMenuRes(), menu);
    inflater.inflate(R.menu.conversation_list_batch, menu);

    mode.setTitle("1");

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      getActivity().getWindow().setStatusBarColor(getResources().getColor(R.color.action_mode_status_bar));
    }

    if (Build.VERSION.SDK_INT >= 23) {
      int current = getActivity().getWindow().getDecorView().getSystemUiVisibility();
      getActivity().getWindow().getDecorView().setSystemUiVisibility(current & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

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
      getActivity().getWindow().setStatusBarColor(color.getColor(0, Color.BLACK));
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
    boolean hasUnread = Stream.of(defaultAdapter.getBatchSelection()).anyMatch(conversation -> !conversation.getThreadRecord().isRead());

    if (hasUnread) {
      menu.findItem(R.id.menu_mark_as_unread).setVisible(false);
      menu.findItem(R.id.menu_mark_as_read).setVisible(true);
    } else {
      menu.findItem(R.id.menu_mark_as_unread).setVisible(true);
      menu.findItem(R.id.menu_mark_as_read).setVisible(false);
    }
  }

  protected @IdRes int getToolbarRes() {
    return R.id.toolbar;
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
  protected void archiveThread(long threadId) {
    DatabaseFactory.getThreadDatabase(getActivity()).archiveConversation(threadId);
  }

  @WorkerThread
  protected void reverseArchiveThread(long threadId) {
    DatabaseFactory.getThreadDatabase(getActivity()).unarchiveConversation(threadId);
  }

  @SuppressLint("StaticFieldLeak")
  protected void onItemSwiped(long threadId, int unreadCount) {
    new SnackbarAsyncTask<Long>(getView(),
        getResources().getQuantityString(R.plurals.ConversationListFragment_conversations_archived, 1, 1),
        getString(R.string.ConversationListFragment_undo),
        getResources().getColor(R.color.amber_500),
        Snackbar.LENGTH_LONG, false)
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
      if (viewHolder.itemView instanceof ConversationListItemAction ||
          actionMode != null                                        ||
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


