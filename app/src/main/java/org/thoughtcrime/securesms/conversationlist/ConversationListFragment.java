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
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
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
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.PluralsRes;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.SimpleColorFilter;
import com.annimon.stream.Stream;
import com.google.android.material.animation.ArgbEvaluatorCompat;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.core.util.DimensionUnit;
import org.signal.core.util.Stopwatch;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.concurrent.SimpleTask;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.MainFragment;
import org.thoughtcrime.securesms.MainNavigator;
import org.thoughtcrime.securesms.MuteDialog;
import org.thoughtcrime.securesms.NewConversationActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.badges.models.Badge;
import org.thoughtcrime.securesms.badges.self.expired.CantProcessSubscriptionPaymentBottomSheetDialogFragment;
import org.thoughtcrime.securesms.badges.self.expired.ExpiredBadgeBottomSheetDialogFragment;
import org.thoughtcrime.securesms.components.Material3SearchToolbar;
import org.thoughtcrime.securesms.components.RatingManager;
import org.thoughtcrime.securesms.components.UnreadPaymentsView;
import org.thoughtcrime.securesms.components.menu.ActionItem;
import org.thoughtcrime.securesms.components.menu.SignalBottomActionBar;
import org.thoughtcrime.securesms.components.menu.SignalContextMenu;
import org.thoughtcrime.securesms.components.registration.PulsingFloatingActionButton;
import org.thoughtcrime.securesms.components.reminder.CdsPermanentErrorReminder;
import org.thoughtcrime.securesms.components.reminder.CdsTemporyErrorReminder;
import org.thoughtcrime.securesms.components.reminder.DozeReminder;
import org.thoughtcrime.securesms.components.reminder.ExpiredBuildReminder;
import org.thoughtcrime.securesms.components.reminder.OutdatedBuildReminder;
import org.thoughtcrime.securesms.components.reminder.PushRegistrationReminder;
import org.thoughtcrime.securesms.components.reminder.Reminder;
import org.thoughtcrime.securesms.components.reminder.ReminderView;
import org.thoughtcrime.securesms.components.reminder.ServiceOutageReminder;
import org.thoughtcrime.securesms.components.reminder.UnauthorizedReminder;
import org.thoughtcrime.securesms.components.settings.app.notifications.manual.NotificationProfileSelectionFragment;
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.UnexpectedSubscriptionCancellation;
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaControllerOwner;
import org.thoughtcrime.securesms.components.voice.VoiceNotePlayerView;
import org.thoughtcrime.securesms.contacts.sync.CdsPermanentErrorBottomSheet;
import org.thoughtcrime.securesms.contacts.sync.CdsTemporaryErrorBottomSheet;
import org.thoughtcrime.securesms.conversation.ConversationFragment;
import org.thoughtcrime.securesms.conversationlist.model.Conversation;
import org.thoughtcrime.securesms.conversationlist.model.UnreadPayments;
import org.thoughtcrime.securesms.database.MessageTable.MarkedMessageInfo;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.ThreadTable;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.events.ReminderUpdateEvent;
import org.thoughtcrime.securesms.exporter.flow.SmsExportDialogs;
import org.thoughtcrime.securesms.insights.InsightsLauncher;
import org.thoughtcrime.securesms.jobs.ServiceOutageDetectionJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.v2.CreateKbsPinActivity;
import org.thoughtcrime.securesms.main.Material3OnScrollHelperBinder;
import org.thoughtcrime.securesms.main.SearchBinder;
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionActivity;
import org.thoughtcrime.securesms.megaphone.Megaphone;
import org.thoughtcrime.securesms.megaphone.MegaphoneActionController;
import org.thoughtcrime.securesms.megaphone.MegaphoneViewBuilder;
import org.thoughtcrime.securesms.megaphone.Megaphones;
import org.thoughtcrime.securesms.megaphone.SmsExportMegaphoneActivity;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.notifications.MarkReadReceiver;
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile;
import org.thoughtcrime.securesms.payments.preferences.PaymentsActivity;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.ratelimit.RecaptchaProofBottomSheetFragment;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.search.MessageResult;
import org.thoughtcrime.securesms.search.SearchResult;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.stories.tabs.ConversationListTab;
import org.thoughtcrime.securesms.stories.tabs.ConversationListTabsViewModel;
import org.thoughtcrime.securesms.util.AppForegroundObserver;
import org.thoughtcrime.securesms.util.AppStartup;
import org.thoughtcrime.securesms.util.BottomSheetUtil;
import org.thoughtcrime.securesms.util.ConversationUtil;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.LifecycleDisposable;
import org.thoughtcrime.securesms.util.PlayStoreUtil;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.SignalLocalMetrics;
import org.thoughtcrime.securesms.util.SignalProxyUtil;
import org.thoughtcrime.securesms.util.SnapToTopDataObserver;
import org.thoughtcrime.securesms.util.StickyHeaderDecoration;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.WindowUtil;
import org.thoughtcrime.securesms.util.task.SnackbarAsyncTask;
import org.thoughtcrime.securesms.util.views.SimpleProgressDialog;
import org.thoughtcrime.securesms.util.views.Stub;
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper;
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static android.app.Activity.RESULT_CANCELED;
import static android.app.Activity.RESULT_OK;


public class ConversationListFragment extends MainFragment implements ActionMode.Callback,
                                                                      ConversationListAdapter.OnConversationClickListener,
                                                                      ConversationListSearchAdapter.EventListener,
                                                                      MegaphoneActionController, ConversationListAdapter.OnClearFilterClickListener
{
  public static final short MESSAGE_REQUESTS_REQUEST_CODE_CREATE_NAME = 32562;
  public static final short SMS_ROLE_REQUEST_CODE                     = 32563;

  private static final int LIST_SMOOTH_SCROLL_TO_TOP_THRESHOLD = 25;

  private static final String TAG = Log.tag(ConversationListFragment.class);

  private static final int MAXIMUM_PINNED_CONVERSATIONS = 4;

  private ActionMode                     actionMode;
  private View                           coordinator;
  private RecyclerView                   list;
  private Stub<ReminderView>             reminderView;
  private Stub<UnreadPaymentsView>       paymentNotificationView;
  private PulsingFloatingActionButton    fab;
  private PulsingFloatingActionButton    cameraFab;
  private ConversationListViewModel      viewModel;
  private RecyclerView.Adapter           activeAdapter;
  private ConversationListAdapter        defaultAdapter;
  private ConversationListSearchAdapter  searchAdapter;
  private StickyHeaderDecoration         searchAdapterDecoration;
  private Stub<ViewGroup>                megaphoneContainer;
  private SnapToTopDataObserver          snapToTopDataObserver;
  private Drawable                       archiveDrawable;
  private AppForegroundObserver.Listener appForegroundObserver;
  private VoiceNoteMediaControllerOwner  mediaControllerOwner;
  private Stub<FrameLayout>              voiceNotePlayerViewStub;
  private VoiceNotePlayerView            voiceNotePlayerView;
  private SignalBottomActionBar          bottomActionBar;
  private SignalContextMenu              activeContextMenu;
  private LifecycleDisposable            lifecycleDisposable;

  protected ConversationListArchiveItemDecoration archiveDecoration;
  protected ConversationListItemAnimator          itemAnimator;
  private   Stopwatch                             startupStopwatch;
  private   ConversationListTabsViewModel         conversationListTabsViewModel;

  public static ConversationListFragment newInstance() {
    return new ConversationListFragment();
  }

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);

    if (context instanceof VoiceNoteMediaControllerOwner) {
      mediaControllerOwner = (VoiceNoteMediaControllerOwner) context;
    } else {
      throw new ClassCastException("Expected context to be a Listener");
    }
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
    coordinator             = view.findViewById(R.id.coordinator);
    list                    = view.findViewById(R.id.list);
    bottomActionBar         = view.findViewById(R.id.conversation_list_bottom_action_bar);
    reminderView            = new Stub<>(view.findViewById(R.id.reminder));
    megaphoneContainer      = new Stub<>(view.findViewById(R.id.megaphone_container));
    paymentNotificationView = new Stub<>(view.findViewById(R.id.payments_notification));
    voiceNotePlayerViewStub = new Stub<>(view.findViewById(R.id.voice_note_player));
    fab                     = view.findViewById(R.id.fab);
    cameraFab               = view.findViewById(R.id.camera_fab);

    fab.setVisibility(View.VISIBLE);
    cameraFab.setVisibility(View.VISIBLE);

    ConversationListFilterPullView pullView = view.findViewById(R.id.pull_view);

    AppBarLayout appBarLayout = view.findViewById(R.id.recycler_coordinator_app_bar);
    appBarLayout.addOnOffsetChangedListener((layout, verticalOffset) -> {
      if (verticalOffset == 0) {
        viewModel.setConversationFilterLatch(ConversationFilterLatch.SET);
        pullView.setToRelease();
      } else if (verticalOffset == -layout.getHeight()) {
        viewModel.setConversationFilterLatch(ConversationFilterLatch.RESET);
        pullView.setToPull();
      }
    });

    fab.show();
    cameraFab.show();

    archiveDecoration = new ConversationListArchiveItemDecoration(new ColorDrawable(getResources().getColor(R.color.conversation_list_archive_background_end)));
    itemAnimator      = new ConversationListItemAnimator();

    list.setLayoutManager(new LinearLayoutManager(requireActivity()));
    list.setItemAnimator(itemAnimator);
    list.addItemDecoration(archiveDecoration);

    snapToTopDataObserver = new SnapToTopDataObserver(list);

    new ItemTouchHelper(new ArchiveListenerCallback(getResources().getColor(R.color.conversation_list_archive_background_start),
                                                    getResources().getColor(R.color.conversation_list_archive_background_end))).attachToRecyclerView(list);

    fab.setOnClickListener(v -> startActivity(new Intent(getActivity(), NewConversationActivity.class)));
    cameraFab.setOnClickListener(v -> {
      Permissions.with(this)
                 .request(Manifest.permission.CAMERA)
                 .ifNecessary()
                 .withRationaleDialog(getString(R.string.ConversationActivity_to_capture_photos_and_video_allow_signal_access_to_the_camera), R.drawable.ic_camera_24)
                 .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_needs_the_camera_permission_to_take_photos_or_video))
                 .onAllGranted(() -> startActivity(MediaSelectionActivity.camera(requireContext())))
                 .onAnyDenied(() -> Toast.makeText(requireContext(), R.string.ConversationActivity_signal_needs_camera_permissions_to_take_photos_or_video, Toast.LENGTH_LONG).show())
                 .execute();
    });

    initializeViewModel();
    initializeListAdapters();
    initializeTypingObserver();
    initializeVoiceNotePlayer();

    RatingManager.showRatingDialogIfNecessary(requireContext());

    TooltipCompat.setTooltipText(requireCallback().getSearchAction(), getText(R.string.SearchToolbar_search_for_conversations_contacts_and_messages));

    requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
      @Override
      public void handleOnBackPressed() {
        if (!closeSearchIfOpen()) {
          if (!NavHostFragment.findNavController(ConversationListFragment.this).popBackStack()) {
            requireActivity().finish();
          }
        }
      }
    });

    lifecycleDisposable           = new LifecycleDisposable();
    conversationListTabsViewModel = new ViewModelProvider(requireActivity()).get(ConversationListTabsViewModel.class);

    lifecycleDisposable.bindTo(getViewLifecycleOwner());
    lifecycleDisposable.add(conversationListTabsViewModel.getTabClickEvents().filter(tab -> tab == ConversationListTab.CHATS)
                                                         .subscribe(unused -> {
                                                           LinearLayoutManager layoutManager            = (LinearLayoutManager) list.getLayoutManager();
                                                           int                 firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                                                           if (firstVisibleItemPosition <= LIST_SMOOTH_SCROLL_TO_TOP_THRESHOLD) {
                                                             list.smoothScrollToPosition(0);
                                                           } else {
                                                             list.scrollToPosition(0);
                                                           }
                                                         }));

    requireCallback().bindScrollHelper(list);
  }

  @Override
  public void onDestroyView() {
    coordinator             = null;
    list                    = null;
    bottomActionBar         = null;
    reminderView            = null;
    megaphoneContainer      = null;
    paymentNotificationView = null;
    voiceNotePlayerViewStub = null;
    fab                     = null;
    cameraFab               = null;
    snapToTopDataObserver   = null;
    itemAnimator            = null;

    activeAdapter  = null;
    defaultAdapter = null;
    searchAdapter  = null;

    super.onDestroyView();
  }

  @Override
  public void onResume() {
    super.onResume();

    initializeSearchListener();
    updateReminders();
    EventBus.getDefault().register(this);
    itemAnimator.disable();

    if (Util.isDefaultSmsProvider(requireContext())) {
      InsightsLauncher.showInsightsModal(requireContext(), requireFragmentManager());
    }

    if ((!requireCallback().getSearchToolbar().resolved() || !(requireCallback().getSearchToolbar().get().getVisibility() == View.VISIBLE)) && list.getAdapter() != defaultAdapter) {
      list.removeItemDecoration(searchAdapterDecoration);
      setAdapter(defaultAdapter);
    }

    if (activeAdapter != null) {
      activeAdapter.notifyItemRangeChanged(0, activeAdapter.getItemCount());
    }

    SignalProxyUtil.startListeningToWebsocket();

    if (SignalStore.rateLimit().needsRecaptcha()) {
      Log.i(TAG, "Recaptcha required.");
      RecaptchaProofBottomSheetFragment.show(getChildFragmentManager());
    }

    Badge                              expiredBadge                       = SignalStore.donationsValues().getExpiredBadge();
    String                             subscriptionCancellationReason     = SignalStore.donationsValues().getUnexpectedSubscriptionCancelationReason();
    UnexpectedSubscriptionCancellation unexpectedSubscriptionCancellation = UnexpectedSubscriptionCancellation.fromStatus(subscriptionCancellationReason);
    boolean                            isDisplayingSubscriptionFailure    = false;
    long                               subscriptionFailureTimestamp       = SignalStore.donationsValues().getUnexpectedSubscriptionCancelationTimestamp();
    long                               subscriptionFailureWatermark       = SignalStore.donationsValues().getUnexpectedSubscriptionCancelationWatermark();
    boolean                            isWatermarkPriorToTimestamp        = subscriptionFailureWatermark < subscriptionFailureTimestamp;

    if (unexpectedSubscriptionCancellation != null &&
        !SignalStore.donationsValues().isUserManuallyCancelled() &&
        SignalStore.donationsValues().showCantProcessDialog() &&
        isWatermarkPriorToTimestamp)
    {
      Log.w(TAG, "Displaying bottom sheet for unexpected cancellation: " + unexpectedSubscriptionCancellation, true);
      new CantProcessSubscriptionPaymentBottomSheetDialogFragment().show(getChildFragmentManager(), BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG);
      SignalStore.donationsValues().setUnexpectedSubscriptionCancelationWatermark(subscriptionFailureTimestamp);
      isDisplayingSubscriptionFailure = true;
    } else if (unexpectedSubscriptionCancellation != null && SignalStore.donationsValues().isUserManuallyCancelled()) {
      Log.w(TAG, "Unexpected cancellation detected but not displaying dialog because user manually cancelled their subscription: " + unexpectedSubscriptionCancellation, true);
      SignalStore.donationsValues().setUnexpectedSubscriptionCancelationWatermark(subscriptionFailureTimestamp);
    } else if (unexpectedSubscriptionCancellation != null && !SignalStore.donationsValues().showCantProcessDialog()) {
      Log.w(TAG, "Unexpected cancellation detected but not displaying dialog because user has silenced it.", true);
      SignalStore.donationsValues().setUnexpectedSubscriptionCancelationWatermark(subscriptionFailureTimestamp);
    }

    if (expiredBadge != null && !isDisplayingSubscriptionFailure) {
      SignalStore.donationsValues().setExpiredBadge(null);

      if (expiredBadge.isBoost() || !SignalStore.donationsValues().isUserManuallyCancelled()) {
        Log.w(TAG, "Displaying bottom sheet for an expired badge", true);
        ExpiredBadgeBottomSheetDialogFragment.show(
            expiredBadge,
            unexpectedSubscriptionCancellation,
            SignalStore.donationsValues().getUnexpectedSubscriptionCancelationChargeFailure(),
            getParentFragmentManager());
      }
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    ApplicationDependencies.getAppForegroundObserver().addListener(appForegroundObserver);
    itemAnimator.disable();
  }

  @Override
  public void onPause() {
    super.onPause();

    requireCallback().getSearchAction().setOnClickListener(null);
    fab.stopPulse();
    cameraFab.stopPulse();
    EventBus.getDefault().unregister(this);
  }

  @Override
  public void onStop() {
    super.onStop();
    ApplicationDependencies.getAppForegroundObserver().removeListener(appForegroundObserver);
  }

  @Override
  public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
    menu.clear();
    inflater.inflate(R.menu.text_secure_normal, menu);
  }

  @Override
  public void onPrepareOptionsMenu(Menu menu) {
    menu.findItem(R.id.menu_insights).setVisible(Util.isDefaultSmsProvider(requireContext()));
    menu.findItem(R.id.menu_clear_passphrase).setVisible(!TextSecurePreferences.isPasswordDisabled(requireContext()));
    menu.findItem(R.id.menu_filter_unread_chats).setVisible(FeatureFlags.chatFilters());
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    super.onOptionsItemSelected(item);

    switch (item.getItemId()) {
      case R.id.menu_new_group:
        handleCreateGroup(); return true;
      case R.id.menu_settings:
        handleDisplaySettings(); return true;
      case R.id.menu_clear_passphrase:
        handleClearPassphrase(); return true;
      case R.id.menu_mark_all_read:
        handleMarkAllRead(); return true;
      case R.id.menu_invite:
        handleInvite(); return true;
      case R.id.menu_insights:
        handleInsights(); return true;
      case R.id.menu_notification_profile:
        handleNotificationProfile(); return true;
      case R.id.menu_filter_unread_chats:
        handleFilterUnreadChats(); return true;
    }

    return false;
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    onMegaphoneChanged(viewModel.getMegaphone().getValue());
  }

  private boolean isSearchOpen() {
    return isSearchVisible() || activeAdapter == searchAdapter;
  }

  private boolean isSearchVisible() {
    return (requireCallback().getSearchToolbar().resolved() && requireCallback().getSearchToolbar().get().getVisibility() == View.VISIBLE);
  }

  private boolean closeSearchIfOpen() {
    if (isSearchOpen()) {
      list.removeItemDecoration(searchAdapterDecoration);
      setAdapter(defaultAdapter);
      requireCallback().getSearchToolbar().get().collapse();
      requireCallback().onSearchClosed();
      return true;
    }

    return false;
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == SmsExportMegaphoneActivity.REQUEST_CODE && SignalStore.misc().getSmsExportPhase().isFullscreen()) {
      ApplicationDependencies.getMegaphoneRepository().markSeen(Megaphones.Event.SMS_EXPORT);
      if (resultCode == RESULT_CANCELED) {
        Snackbar.make(fab, R.string.ConversationActivity__you_will_be_reminded_again_soon, Snackbar.LENGTH_LONG).show();
      } else {
        SmsExportDialogs.showSmsRemovalDialog(requireContext(), fab);
      }
    }

    if (resultCode == RESULT_OK && requestCode == CreateKbsPinActivity.REQUEST_NEW_PIN) {
      Snackbar.make(fab, R.string.ConfirmKbsPinFragment__pin_created, Snackbar.LENGTH_LONG).show();
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
    if (viewModel.currentSelectedConversations().isEmpty()) {
      NavHostFragment.findNavController(this)
                     .navigate(ConversationListFragmentDirections.actionConversationListFragmentToConversationListArchiveFragment());
    }
  }

  @Override
  public void onContactClicked(@NonNull Recipient contact) {
    SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
      return SignalDatabase.threads().getThreadIdIfExistsFor(contact.getId());
    }, threadId -> {
      hideKeyboard();
      getNavigator().goToConversation(contact.getId(),
                                      threadId,
                                      ThreadTable.DistributionTypes.DEFAULT,
                                      -1);
    });
  }

  @Override
  public void onMessageClicked(@NonNull MessageResult message) {
    SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
      int startingPosition = SignalDatabase.mmsSms().getMessagePositionInConversation(message.getThreadId(), message.getReceivedTimestampMs());
      return Math.max(0, startingPosition);
    }, startingPosition -> {
      hideKeyboard();
      getNavigator().goToConversation(message.getConversationRecipient().getId(),
                                      message.getThreadId(),
                                      ThreadTable.DistributionTypes.DEFAULT,
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
    Snackbar.make(fab, string, Snackbar.LENGTH_LONG).show();
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
    } else if (reminderActionId == R.id.reminder_action_cds_temporary_error_learn_more) {
      CdsTemporaryErrorBottomSheet.show(getChildFragmentManager());
    } else if (reminderActionId == R.id.reminder_action_cds_permanent_error_learn_more) {
      CdsPermanentErrorBottomSheet.show(getChildFragmentManager());
    }
  }

  private void hideKeyboard() {
    InputMethodManager imm = ServiceUtil.getInputMethodManager(requireContext());
    imm.hideSoftInputFromWindow(requireView().getWindowToken(), 0);
  }

  private void initializeSearchListener() {
    requireCallback().getSearchAction().setOnClickListener(v -> {
      fadeOutButtonsAndMegaphone(250);
      requireCallback().onSearchOpened();

      requireCallback().getSearchToolbar().get().setListener(new Material3SearchToolbar.Listener() {
        @Override
        public void onSearchTextChange(String text) {
          String trimmed = text.trim();

          viewModel.onSearchQueryUpdated(trimmed);

          if (trimmed.length() > 0) {
            if (activeAdapter != searchAdapter && list != null) {
              setAdapter(searchAdapter);
              list.removeItemDecoration(searchAdapterDecoration);
              list.addItemDecoration(searchAdapterDecoration);
            }
          } else {
            if (activeAdapter != defaultAdapter) {
              if (list != null) {
                list.removeItemDecoration(searchAdapterDecoration);
                setAdapter(defaultAdapter);
              }
            }
          }
        }

        @Override
        public void onSearchClosed() {
          if (list != null) {
            list.removeItemDecoration(searchAdapterDecoration);
            setAdapter(defaultAdapter);
          }
          requireCallback().onSearchClosed();
          fadeInButtonsAndMegaphone(250);
        }
      });
    });
  }

  private void initializeVoiceNotePlayer() {
    mediaControllerOwner.getVoiceNoteMediaController().getVoiceNotePlayerViewState().observe(getViewLifecycleOwner(), state -> {
      if (state.isPresent()) {
        requireVoiceNotePlayerView().setState(state.get());
        requireVoiceNotePlayerView().show();
      } else if (voiceNotePlayerViewStub.resolved()) {
        requireVoiceNotePlayerView().hide();
      }
    });
  }

  private @NonNull VoiceNotePlayerView requireVoiceNotePlayerView() {
    if (voiceNotePlayerView == null) {
      voiceNotePlayerView = voiceNotePlayerViewStub.get().findViewById(R.id.voice_note_player_view);
      voiceNotePlayerView.setListener(new VoiceNotePlayerViewListener());
    }

    return voiceNotePlayerView;
  }


  private void initializeListAdapters() {
    defaultAdapter          = new ConversationListAdapter(getViewLifecycleOwner(), GlideApp.with(this), this, this);
    searchAdapter           = new ConversationListSearchAdapter(getViewLifecycleOwner(), GlideApp.with(this), this, Locale.getDefault());
    searchAdapterDecoration = new StickyHeaderDecoration(searchAdapter, false, false, 0);

    setAdapter(defaultAdapter);

    defaultAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
      @Override
      public void onItemRangeInserted(int positionStart, int itemCount) {
        startupStopwatch.split("data-set");
        SignalLocalMetrics.ColdStart.onConversationListDataLoaded();
        defaultAdapter.unregisterAdapterDataObserver(this);
        list.post(() -> {
          AppStartup.getInstance().onCriticalRenderEventEnd();
          startupStopwatch.split("first-render");
          startupStopwatch.stop(TAG);

          if (getContext() != null) {
            ConversationFragment.prepare(getContext());
          }
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
      viewModel.getPagingController()
               .observe(getViewLifecycleOwner(),
                        controller -> ((ConversationListAdapter) adapter).setPagingController(controller));
    }

    list.setAdapter(adapter);

    if (adapter == defaultAdapter) {
      defaultAdapter.registerAdapterDataObserver(snapToTopDataObserver);
    } else {
      defaultAdapter.unregisterAdapterDataObserver(snapToTopDataObserver);
    }
  }

  private void initializeTypingObserver() {
    ApplicationDependencies.getTypingStatusRepository().getTypingThreads().observe(getViewLifecycleOwner(), threadIds -> {
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
    ConversationListViewModel.Factory viewModelFactory = new ConversationListViewModel.Factory(isArchived(),
                                                                                               getString(R.string.note_to_self));

    viewModel = new ViewModelProvider(this, (ViewModelProvider.Factory) viewModelFactory).get(ConversationListViewModel.class);

    viewModel.getSearchResult().observe(getViewLifecycleOwner(), this::onSearchResultChanged);
    viewModel.getMegaphone().observe(getViewLifecycleOwner(), this::onMegaphoneChanged);
    viewModel.getConversationList().observe(getViewLifecycleOwner(), this::onConversationListChanged);
    viewModel.hasNoConversations().observe(getViewLifecycleOwner(), this::updateEmptyState);
    viewModel.getNotificationProfiles().observe(getViewLifecycleOwner(), profiles -> requireCallback().updateNotificationProfileStatus(profiles));
    viewModel.getPipeState().observe(getViewLifecycleOwner(), pipeState -> requireCallback().updateProxyStatus(pipeState));

    appForegroundObserver = new AppForegroundObserver.Listener() {
      @Override
      public void onForeground() {
        viewModel.onVisible();
      }

      @Override
      public void onBackground() {}
    };

    viewModel.getUnreadPaymentsLiveData().observe(getViewLifecycleOwner(), this::onUnreadPaymentsChanged);

    viewModel.getSelectedConversations().observe(getViewLifecycleOwner(), conversations -> {
      defaultAdapter.setSelectedConversations(conversations);
      updateMultiSelectState();
    });
  }

  private void onConversationListChanged(@NonNull List<Conversation> conversations) {
    LinearLayoutManager layoutManager    = (LinearLayoutManager) list.getLayoutManager();
    int                 firstVisibleItem = layoutManager != null ? layoutManager.findFirstCompletelyVisibleItemPosition() : -1;

    defaultAdapter.submitList(conversations, () -> {
      if (list == null) {
        return;
      }

      if (firstVisibleItem == 0) {
        list.scrollToPosition(0);
      }
      onPostSubmitList(conversations.size());
    });
  }

  private void onUnreadPaymentsChanged(@NonNull Optional<UnreadPayments> unreadPayments) {
    if (unreadPayments.isPresent()) {
      paymentNotificationView.get().setListener(new PaymentNotificationListener(unreadPayments.get()));
      paymentNotificationView.get().setUnreadPayments(unreadPayments.get());
      animatePaymentUnreadStatusIn();
    } else {
      animatePaymentUnreadStatusOut();
    }
  }

  private void animatePaymentUnreadStatusIn() {
    paymentNotificationView.get().setVisibility(View.VISIBLE);
    requireCallback().getUnreadPaymentsDot().animate().alpha(1);
  }

  private void animatePaymentUnreadStatusOut() {
    if (paymentNotificationView.resolved()) {
      paymentNotificationView.get().setVisibility(View.GONE);
    }

    requireCallback().getUnreadPaymentsDot().animate().alpha(0);
  }

  private void onSearchResultChanged(@Nullable SearchResult result) {
    result = result != null ? result : SearchResult.EMPTY;
    searchAdapter.updateResults(result);
  }

  private void onMegaphoneChanged(@Nullable Megaphone megaphone) {
    if (megaphone == null || isArchived() || getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
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
      if (isSearchOpen() || actionMode != null) {
        megaphoneContainer.get().setVisibility(View.GONE);
      } else {
        megaphoneContainer.get().setVisibility(View.VISIBLE);
      }
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
      } else if (CdsTemporyErrorReminder.isEligible()) {
        return Optional.of(new CdsTemporyErrorReminder(context));
      } else if (CdsPermanentErrorReminder.isEligible()) {
        return Optional.of(new CdsPermanentErrorReminder(context));
      } else {
        return Optional.<Reminder>empty();
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
      List<MarkedMessageInfo> messageIds = SignalDatabase.threads().setAllThreadsRead();

      ApplicationDependencies.getMessageNotifier().updateNotification(context);
      MarkReadReceiver.process(context, messageIds);
    });
  }

  private void handleMarkAsRead(@NonNull Collection<Long> ids) {
    Context context = requireContext();

    SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
      List<MarkedMessageInfo> messageIds = SignalDatabase.threads().setRead(ids, false);

      ApplicationDependencies.getMessageNotifier().updateNotification(context);
      MarkReadReceiver.process(context, messageIds);

      return null;
    }, none -> {
      endActionModeIfActive();
    });
  }

  private void handleMarkAsUnread(@NonNull Collection<Long> ids) {
    Context context = requireContext();

    SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
      SignalDatabase.threads().setForcedUnread(ids);
      StorageSyncHelper.scheduleSyncForDataChange();
      return null;
    }, none -> {
      endActionModeIfActive();
    });
  }

  private void handleInvite() {
    getNavigator().goToInvite();
  }

  private void handleInsights() {
    getNavigator().goToInsights();
  }

  private void handleNotificationProfile() {
    NotificationProfileSelectionFragment.show(getParentFragmentManager());
  }

  private void handleFilterUnreadChats() {
    viewModel.toggleUnreadChatsFilter();
  }

  @SuppressLint("StaticFieldLeak")
  private void handleArchive(@NonNull Collection<Long> ids, boolean showProgress) {
    Set<Long> selectedConversations = new HashSet<>(ids);
    int       count                 = selectedConversations.size();
    String    snackBarTitle         = getResources().getQuantityString(getArchivedSnackbarTitleRes(), count, count);

    new SnackbarAsyncTask<Void>(getViewLifecycleOwner().getLifecycle(),
                                coordinator,
                                snackBarTitle,
                                getString(R.string.ConversationListFragment_undo),
                                getResources().getColor(R.color.amber_500),
                                Snackbar.LENGTH_LONG,
                                showProgress)
    {

      @Override
      protected void onPostExecute(Void result) {
        super.onPostExecute(result);
        endActionModeIfActive();
      }

      @Override
      protected void executeAction(@Nullable Void parameter) {
        archiveThreads(selectedConversations);
      }

      @Override
      protected void reverseAction(@Nullable Void parameter) {
        reverseArchiveThreads(selectedConversations);
      }
    }.executeOnExecutor(SignalExecutors.BOUNDED);
  }

  @SuppressLint("StaticFieldLeak")
  private void handleDelete(@NonNull Collection<Long> ids) {
    int                        conversationsCount = ids.size();
    MaterialAlertDialogBuilder alert              = new MaterialAlertDialogBuilder(requireActivity());
    Context                    context            = requireContext();

    alert.setTitle(context.getResources().getQuantityString(R.plurals.ConversationListFragment_delete_selected_conversations,
                                                            conversationsCount, conversationsCount));
    alert.setMessage(context.getResources().getQuantityString(R.plurals.ConversationListFragment_this_will_permanently_delete_all_n_selected_conversations,
                                                              conversationsCount, conversationsCount));
    alert.setCancelable(true);

    alert.setPositiveButton(R.string.delete, (dialog, which) -> {
      final Set<Long> selectedConversations = new HashSet<>(ids);

      if (!selectedConversations.isEmpty()) {
        new AsyncTask<Void, Void, Void>() {
          private ProgressDialog dialog;

          @Override
          protected void onPreExecute() {
            dialog = ProgressDialog.show(requireActivity(),
                                         context.getString(R.string.ConversationListFragment_deleting),
                                         context.getString(R.string.ConversationListFragment_deleting_selected_conversations),
                                         true, false);
          }

          @Override
          protected Void doInBackground(Void... params) {
            SignalDatabase.threads().deleteConversations(selectedConversations);
            ApplicationDependencies.getMessageNotifier().updateNotification(requireActivity());
            return null;
          }

          @Override
          protected void onPostExecute(Void result) {
            dialog.dismiss();
            endActionModeIfActive();
          }
        }.executeOnExecutor(SignalExecutors.BOUNDED);
      }
    });

    alert.setNegativeButton(android.R.string.cancel, null);
    alert.show();
  }

  private void handlePin(@NonNull Collection<Conversation> conversations) {
    final Set<Long> toPin = new LinkedHashSet<>(Stream.of(conversations)
                                                      .filterNot(conversation -> conversation.getThreadRecord().isPinned())
                                                      .map(conversation -> conversation.getThreadRecord().getThreadId())
                                                      .toList());

    if (toPin.size() + viewModel.getPinnedCount() > MAXIMUM_PINNED_CONVERSATIONS) {
      Snackbar.make(fab,
                    getString(R.string.conversation_list__you_can_only_pin_up_to_d_chats, MAXIMUM_PINNED_CONVERSATIONS),
                    Snackbar.LENGTH_LONG)
              .show();
      endActionModeIfActive();
      return;
    }

    SimpleTask.run(SignalExecutors.BOUNDED, () -> {
      ThreadTable db = SignalDatabase.threads();

      db.pinConversations(toPin);
      ConversationUtil.refreshRecipientShortcuts();

      return null;
    }, unused -> {
      endActionModeIfActive();
    });
  }

  private void handleUnpin(@NonNull Collection<Long> ids) {
    SimpleTask.run(SignalExecutors.BOUNDED, () -> {
      ThreadTable db = SignalDatabase.threads();

      db.unpinConversations(ids);
      ConversationUtil.refreshRecipientShortcuts();

      return null;
    }, unused -> {
      endActionModeIfActive();
    });
  }

  private void handleMute(@NonNull Collection<Conversation> conversations) {
    MuteDialog.show(requireContext(), until -> {
      updateMute(conversations, until);
    });
  }

  private void handleUnmute(@NonNull Collection<Conversation> conversations) {
    updateMute(conversations, 0);
  }

  private void updateMute(@NonNull Collection<Conversation> conversations, long until) {
    SimpleProgressDialog.DismissibleDialog dialog = SimpleProgressDialog.showDelayed(requireContext(), 250, 250);

    SimpleTask.run(SignalExecutors.BOUNDED, () -> {
      List<RecipientId> recipientIds = conversations.stream()
                                                    .map(conversation -> conversation.getThreadRecord().getRecipient().live().get())
                                                    .filter(r -> r.getMuteUntil() != until)
                                                    .map(Recipient::getId)
                                                    .collect(Collectors.toList());
      SignalDatabase.recipients().setMuted(recipientIds, until);
      return null;
    }, unused -> {
      endActionModeIfActive();
      dialog.dismiss();
    });
  }

  private void handleCreateConversation(long threadId, Recipient recipient, int distributionType) {
    SimpleTask.run(getLifecycle(), () -> {
      ChatWallpaper wallpaper = recipient.resolve().getWallpaper();
      if (wallpaper != null && !wallpaper.prefetch(requireContext(), 250)) {
        Log.w(TAG, "Failed to prefetch wallpaper.");
      }
      return null;
    }, (nothing) -> {
      getNavigator().goToConversation(recipient.getId(), threadId, distributionType, -1);
    });
  }

  private void fadeOutButtonsAndMegaphone(int fadeDuration) {
    ViewUtil.fadeOut(fab, fadeDuration);
    ViewUtil.fadeOut(cameraFab, fadeDuration);
    if (megaphoneContainer.resolved()) {
      ViewUtil.fadeOut(megaphoneContainer.get(), fadeDuration);
    }
  }

  private void fadeInButtonsAndMegaphone(int fadeDuration) {
    ViewUtil.fadeIn(fab, fadeDuration);
    ViewUtil.fadeIn(cameraFab, fadeDuration);
    if (megaphoneContainer.resolved()) {
      ViewUtil.fadeIn(megaphoneContainer.get(), fadeDuration);
    }
  }

  private void startActionMode() {
    actionMode = ((AppCompatActivity) getActivity()).startSupportActionMode(ConversationListFragment.this);
    ViewUtil.animateIn(bottomActionBar, bottomActionBar.getEnterAnimation());
    ViewUtil.fadeOut(fab, 250);
    ViewUtil.fadeOut(cameraFab, 250);
    if (megaphoneContainer.resolved()) {
      ViewUtil.fadeOut(megaphoneContainer.get(), 250);
    }
    requireCallback().onMultiSelectStarted();
  }

  private void endActionModeIfActive() {
    if (actionMode != null) {
      endActionMode();
    }
  }

  private void endActionMode() {
    actionMode.finish();
    actionMode = null;
    ViewUtil.animateOut(bottomActionBar, bottomActionBar.getExitAnimation());
    ViewUtil.fadeIn(fab, 250);
    ViewUtil.fadeIn(cameraFab, 250);
    if (megaphoneContainer.resolved()) {
      ViewUtil.fadeIn(megaphoneContainer.get(), 250);
    }
    requireCallback().onMultiSelectFinished();
  }

  void updateEmptyState(boolean isConversationEmpty) {
    if (isConversationEmpty) {
      Log.i(TAG, "Received an empty data set.");
      fab.startPulse(3 * 1000);
      cameraFab.startPulse(3 * 1000);

      SignalStore.onboarding().setShowNewGroup(true);
      SignalStore.onboarding().setShowInviteFriends(true);
    } else {
      fab.stopPulse();
      cameraFab.stopPulse();
    }
  }

  protected void onPostSubmitList(int conversationCount) {
    if (conversationCount >= 6 && (SignalStore.onboarding().shouldShowInviteFriends() || SignalStore.onboarding().shouldShowNewGroup())) {
      SignalStore.onboarding().clearAll();
      ApplicationDependencies.getMegaphoneRepository().markFinished(Megaphones.Event.ONBOARDING);
    }
  }

  @Override
  public void onConversationClick(@NonNull Conversation conversation) {
    if (actionMode == null) {
      handleCreateConversation(conversation.getThreadRecord().getThreadId(), conversation.getThreadRecord().getRecipient(), conversation.getThreadRecord().getDistributionType());
    } else {
      viewModel.toggleConversationSelected(conversation);

      if (viewModel.currentSelectedConversations().isEmpty()) {
        endActionModeIfActive();
      } else {
        updateMultiSelectState();
      }
    }
  }

  @Override
  public boolean onConversationLongClick(@NonNull Conversation conversation, @NonNull View view) {
    if (actionMode != null) {
      onConversationClick(conversation);
      return true;
    }

    if (activeContextMenu != null) {
      Log.w(TAG, "Already showing a context menu.");
      return true;
    }

    view.setSelected(true);

    Collection<Long> id = Collections.singleton(conversation.getThreadRecord().getThreadId());

    List<ActionItem> items = new ArrayList<>();

    if (!conversation.getThreadRecord().isArchived()) {
      if (conversation.getThreadRecord().isRead()) {
        items.add(new ActionItem(R.drawable.ic_unread_24, getResources().getQuantityString(R.plurals.ConversationListFragment_unread_plural, 1), () -> handleMarkAsUnread(id)));
      } else {
        items.add(new ActionItem(R.drawable.ic_read_24, getResources().getQuantityString(R.plurals.ConversationListFragment_read_plural, 1), () -> handleMarkAsRead(id)));
      }

      if (conversation.getThreadRecord().isPinned()) {
        items.add(new ActionItem(R.drawable.ic_unpin_24, getResources().getQuantityString(R.plurals.ConversationListFragment_unpin_plural, 1), () -> handleUnpin(id)));
      } else {
        items.add(new ActionItem(R.drawable.ic_pin_24, getResources().getQuantityString(R.plurals.ConversationListFragment_pin_plural, 1), () -> handlePin(Collections.singleton(conversation))));
      }

      if (conversation.getThreadRecord().getRecipient().live().get().isMuted()) {
        items.add(new ActionItem(R.drawable.ic_unmute_24, getResources().getQuantityString(R.plurals.ConversationListFragment_unmute_plural, 1), () -> handleUnmute(Collections.singleton(conversation))));
      } else {
        items.add(new ActionItem(R.drawable.ic_mute_24, getResources().getQuantityString(R.plurals.ConversationListFragment_mute_plural, 1), () -> handleMute(Collections.singleton(conversation))));
      }
    }

    items.add(new ActionItem(R.drawable.ic_select_24, getString(R.string.ConversationListFragment_select), () -> {
      viewModel.startSelection(conversation);
      startActionMode();
    }));

    if (conversation.getThreadRecord().isArchived()) {
      items.add(new ActionItem(R.drawable.ic_unarchive_24, getResources().getQuantityString(R.plurals.ConversationListFragment_unarchive_plural, 1), () -> handleArchive(id, false)));
    } else {
      items.add(new ActionItem(R.drawable.ic_archive_24, getResources().getQuantityString(R.plurals.ConversationListFragment_archive_plural, 1), () -> handleArchive(id, false)));
    }

    items.add(new ActionItem(R.drawable.ic_delete_24, getResources().getQuantityString(R.plurals.ConversationListFragment_delete_plural, 1), () -> handleDelete(id)));

    activeContextMenu = new SignalContextMenu.Builder(view, list)
        .offsetX(ViewUtil.dpToPx(12))
        .offsetY(ViewUtil.dpToPx(12))
        .onDismiss(() -> {
          activeContextMenu = null;
          view.setSelected(false);
          list.suppressLayout(false);
        })
        .show(items);

    list.suppressLayout(true);

    return true;
  }

  @Override
  public boolean onCreateActionMode(ActionMode mode, Menu menu) {
    mode.setTitle(requireContext().getResources().getQuantityString(R.plurals.ConversationListFragment_s_selected, 1, 1));
    return true;
  }

  @Override
  public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
    updateMultiSelectState();
    return false;
  }

  @Override
  public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
    return true;
  }

  @Override
  public void onDestroyActionMode(ActionMode mode) {
    viewModel.endSelection();

    if (Build.VERSION.SDK_INT >= 21) {
      TypedArray color = getActivity().getTheme().obtainStyledAttributes(new int[] { android.R.attr.statusBarColor });
      WindowUtil.setStatusBarColor(getActivity().getWindow(), color.getColor(0, Color.BLACK));
      color.recycle();
    }

    if (Build.VERSION.SDK_INT >= 23) {
      TypedArray lightStatusBarAttr = getActivity().getTheme().obtainStyledAttributes(new int[] { android.R.attr.windowLightStatusBar });
      int        current            = getActivity().getWindow().getDecorView().getSystemUiVisibility();
      int statusBarMode = lightStatusBarAttr.getBoolean(0, false) ? current | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                                                                  : current & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;

      getActivity().getWindow().getDecorView().setSystemUiVisibility(statusBarMode);

      lightStatusBarAttr.recycle();
    }

    endActionModeIfActive();
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

  private void updateMultiSelectState() {
    int     count       = viewModel.currentSelectedConversations().size();
    boolean hasUnread   = Stream.of(viewModel.currentSelectedConversations()).anyMatch(conversation -> !conversation.getThreadRecord().isRead());
    boolean hasUnpinned = Stream.of(viewModel.currentSelectedConversations()).anyMatch(conversation -> !conversation.getThreadRecord().isPinned());
    boolean hasUnmuted  = Stream.of(viewModel.currentSelectedConversations()).anyMatch(conversation -> !conversation.getThreadRecord().getRecipient().live().get().isMuted());
    boolean canPin      = viewModel.getPinnedCount() < MAXIMUM_PINNED_CONVERSATIONS;

    if (actionMode != null) {
      actionMode.setTitle(requireContext().getResources().getQuantityString(R.plurals.ConversationListFragment_s_selected, count, count));
    }

    List<ActionItem> items = new ArrayList<>();

    Set<Long> selectionIds = viewModel.currentSelectedConversations()
                                      .stream()
                                      .map(conversation -> conversation.getThreadRecord().getThreadId())
                                      .collect(Collectors.toSet());

    if (hasUnread) {
      items.add(new ActionItem(R.drawable.ic_read_24, getResources().getQuantityString(R.plurals.ConversationListFragment_read_plural, count), () -> handleMarkAsRead(selectionIds)));
    } else {
      items.add(new ActionItem(R.drawable.ic_unread_24, getResources().getQuantityString(R.plurals.ConversationListFragment_unread_plural, count), () -> handleMarkAsUnread(selectionIds)));
    }

    if (!isArchived() && hasUnpinned && canPin) {
      items.add(new ActionItem(R.drawable.ic_pin_24, getResources().getQuantityString(R.plurals.ConversationListFragment_pin_plural, count), () -> handlePin(viewModel.currentSelectedConversations())));
    } else if (!isArchived() && !hasUnpinned) {
      items.add(new ActionItem(R.drawable.ic_unpin_24, getResources().getQuantityString(R.plurals.ConversationListFragment_unpin_plural, count), () -> handleUnpin(selectionIds)));
    }

    if (isArchived()) {
      items.add(new ActionItem(R.drawable.ic_unarchive_24, getResources().getQuantityString(R.plurals.ConversationListFragment_unarchive_plural, count), () -> handleArchive(selectionIds, true)));
    } else {
      items.add(new ActionItem(R.drawable.ic_archive_24, getResources().getQuantityString(R.plurals.ConversationListFragment_archive_plural, count), () -> handleArchive(selectionIds, true)));
    }

    items.add(new ActionItem(R.drawable.ic_delete_24, getResources().getQuantityString(R.plurals.ConversationListFragment_delete_plural, count), () -> handleDelete(selectionIds)));

    if (hasUnmuted) {
      items.add(new ActionItem(R.drawable.ic_mute_24, getResources().getQuantityString(R.plurals.ConversationListFragment_mute_plural, count), () -> handleMute(viewModel.currentSelectedConversations())));
    } else {
      items.add(new ActionItem(R.drawable.ic_unmute_24, getResources().getQuantityString(R.plurals.ConversationListFragment_unmute_plural, count), () -> handleUnmute(viewModel.currentSelectedConversations())));
    }

    items.add(new ActionItem(R.drawable.ic_select_24, getString(R.string.ConversationListFragment_select_all), viewModel::onSelectAllClick));

    bottomActionBar.setItems(items);
  }

  protected Callback requireCallback() {
    return ((Callback) getParentFragment().getParentFragment());
  }

  protected Toolbar getToolbar(@NonNull View rootView) {
    return requireCallback().getToolbar();
  }

  protected @PluralsRes int getArchivedSnackbarTitleRes() {
    return R.plurals.ConversationListFragment_conversations_archived;
  }

  protected @DrawableRes int getArchiveIconRes() {
    return R.drawable.ic_archive_24;
  }

  @WorkerThread
  protected void archiveThreads(Set<Long> threadIds) {
    SignalDatabase.threads().setArchived(threadIds, true);
  }

  @WorkerThread
  protected void reverseArchiveThreads(Set<Long> threadIds) {
    SignalDatabase.threads().setArchived(threadIds, false);
  }

  @SuppressLint("StaticFieldLeak")
  protected void onItemSwiped(long threadId, int unreadCount, int unreadSelfMentionsCount) {
    archiveDecoration.onArchiveStarted();
    itemAnimator.enable();

    new SnackbarAsyncTask<Long>(getViewLifecycleOwner().getLifecycle(),
                                coordinator,
                                getResources().getQuantityString(R.plurals.ConversationListFragment_conversations_archived, 1, 1),
                                getString(R.string.ConversationListFragment_undo),
                                getResources().getColor(R.color.amber_500),
                                Snackbar.LENGTH_LONG,
                                false)
    {
      private final ThreadTable threadTable = SignalDatabase.threads();

      private List<Long> pinnedThreadIds;

      @Override
      protected void executeAction(@Nullable Long parameter) {
        Context context = requireActivity();

        pinnedThreadIds = threadTable.getPinnedThreadIds();
        threadTable.archiveConversation(threadId);

        if (unreadCount > 0) {
          List<MarkedMessageInfo> messageIds = threadTable.setRead(threadId, false);
          ApplicationDependencies.getMessageNotifier().updateNotification(context);
          MarkReadReceiver.process(context, messageIds);
        }

        ConversationUtil.refreshRecipientShortcuts();
      }

      @Override
      protected void reverseAction(@Nullable Long parameter) {
        Context context = requireActivity();

        threadTable.unarchiveConversation(threadId);
        threadTable.restorePins(pinnedThreadIds);

        if (unreadCount > 0) {
          threadTable.incrementUnread(threadId, unreadCount, unreadSelfMentionsCount);
          ApplicationDependencies.getMessageNotifier().updateNotification(context);
        }

        ConversationUtil.refreshRecipientShortcuts();
      }
    }.executeOnExecutor(SignalExecutors.BOUNDED, threadId);
  }

  @Override
  public void onClearFilterClick() {
    viewModel.toggleUnreadChatsFilter();
  }

  private class PaymentNotificationListener implements UnreadPaymentsView.Listener {

    private final UnreadPayments unreadPayments;

    private PaymentNotificationListener(@NonNull UnreadPayments unreadPayments) {
      this.unreadPayments = unreadPayments;
    }

    @Override
    public void onOpenPaymentsNotificationClicked() {
      UUID paymentId = unreadPayments.getPaymentUuid();

      if (paymentId == null) {
        goToPaymentsHome();
      } else {
        goToSinglePayment(paymentId);
      }
    }

    @Override
    public void onClosePaymentsNotificationClicked() {
      viewModel.onUnreadPaymentsClosed();
    }

    private void goToPaymentsHome() {
      startActivity(new Intent(requireContext(), PaymentsActivity.class));
    }

    private void goToSinglePayment(@NonNull UUID paymentId) {
      startActivity(PaymentsActivity.navigateToPaymentDetails(requireContext(), paymentId));
    }
  }

  private class ArchiveListenerCallback extends ItemTouchHelper.SimpleCallback {

    private static final long SWIPE_ANIMATION_DURATION = 175;

    private static final float MIN_ICON_SCALE = 0.85f;
    private static final float MAX_ICON_SCALE = 1f;

    private final int archiveColorStart;
    private final int archiveColorEnd;

    private final float ESCAPE_VELOCITY    = ViewUtil.dpToPx(1000);
    private final float VELOCITY_THRESHOLD = ViewUtil.dpToPx(1000);

    private WeakReference<RecyclerView.ViewHolder> lastTouched;

    ArchiveListenerCallback(@ColorInt int archiveColorStart, @ColorInt int archiveColorEnd) {
      super(0, ItemTouchHelper.END);
      this.archiveColorStart = archiveColorStart;
      this.archiveColorEnd   = archiveColorEnd;
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder,
                          @NonNull RecyclerView.ViewHolder target)
    {
      return false;
    }

    @Override
    public float getSwipeEscapeVelocity(float defaultValue) {
      return Math.min(ESCAPE_VELOCITY, VELOCITY_THRESHOLD);
    }

    @Override
    public float getSwipeVelocityThreshold(float defaultValue) {
      return VELOCITY_THRESHOLD;
    }

    @Override
    public int getSwipeDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
      if (viewHolder.itemView instanceof ConversationListItemAction ||
          viewHolder instanceof ConversationListAdapter.HeaderViewHolder ||
          actionMode != null ||
          viewHolder.itemView.isSelected() ||
          activeAdapter == searchAdapter)
      {
        return 0;
      }

      lastTouched = new WeakReference<>(viewHolder);

      return super.getSwipeDirs(recyclerView, viewHolder);
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
      if (lastTouched != null) {
        Log.w(TAG, "Falling back to slower onSwiped() event.");
        onTrueSwipe(viewHolder);
        lastTouched = null;
      }
    }

    @Override
    public long getAnimationDuration(@NonNull RecyclerView recyclerView, int animationType, float animateDx, float animateDy) {
      if (animationType == ItemTouchHelper.ANIMATION_TYPE_SWIPE_SUCCESS && lastTouched != null && lastTouched.get() != null) {
        onTrueSwipe(lastTouched.get());
        lastTouched = null;
      } else if (animationType == ItemTouchHelper.ANIMATION_TYPE_SWIPE_CANCEL) {
        lastTouched = null;
      }

      return SWIPE_ANIMATION_DURATION;
    }

    private void onTrueSwipe(RecyclerView.ViewHolder viewHolder) {
      ThreadRecord thread = ((ConversationListItem) viewHolder.itemView).getThread();

      onItemSwiped(thread.getThreadId(), thread.getUnreadCount(), thread.getUnreadSelfMentionsCount());
    }

    @Override
    public void onChildDraw(@NonNull Canvas canvas, @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder,
                            float dX, float dY, int actionState,
                            boolean isCurrentlyActive)
    {
      float absoluteDx = Math.abs(dX);

      if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
        Resources resources       = getResources();
        View      itemView        = viewHolder.itemView;
        float     percentDx       = absoluteDx / viewHolder.itemView.getWidth();
        int       color           = ArgbEvaluatorCompat.getInstance().evaluate(Math.min(1f, percentDx * (1 / 0.25f)), archiveColorStart, archiveColorEnd);
        float     scaleStartPoint = DimensionUnit.DP.toPixels(48f);
        float     scaleEndPoint   = DimensionUnit.DP.toPixels(96f);

        float scale;
        if (absoluteDx < scaleStartPoint) {
          scale = MIN_ICON_SCALE;
        } else if (absoluteDx > scaleEndPoint) {
          scale = MAX_ICON_SCALE;
        } else {
          scale = Math.min(MAX_ICON_SCALE, MIN_ICON_SCALE + ((absoluteDx - scaleStartPoint) / (scaleEndPoint - scaleStartPoint)) * (MAX_ICON_SCALE - MIN_ICON_SCALE));
        }

        if (absoluteDx > 0) {
          if (archiveDrawable == null) {
            archiveDrawable = Objects.requireNonNull(AppCompatResources.getDrawable(requireContext(), getArchiveIconRes()));
            archiveDrawable.setColorFilter(new SimpleColorFilter(ContextCompat.getColor(requireContext(), R.color.signal_colorOnPrimary)));
            archiveDrawable.setBounds(0, 0, archiveDrawable.getIntrinsicWidth(), archiveDrawable.getIntrinsicHeight());
          }

          canvas.save();
          canvas.clipRect(itemView.getLeft(), itemView.getTop(), itemView.getRight(), itemView.getBottom());

          canvas.drawColor(color);

          float gutter = resources.getDimension(R.dimen.dsl_settings_gutter);
          float extra  = resources.getDimension(R.dimen.conversation_list_fragment_archive_padding);

          if (ViewUtil.isLtr(requireContext())) {
            canvas.translate(itemView.getLeft() + gutter + extra,
                             itemView.getTop() + (itemView.getBottom() - itemView.getTop() - archiveDrawable.getIntrinsicHeight()) / 2f);
          } else {
            canvas.translate(itemView.getRight() - gutter - extra,
                             itemView.getTop() + (itemView.getBottom() - itemView.getTop() - archiveDrawable.getIntrinsicHeight()) / 2f);
          }

          canvas.scale(scale, scale, archiveDrawable.getIntrinsicWidth() / 2f, archiveDrawable.getIntrinsicHeight() / 2f);

          archiveDrawable.draw(canvas);
          canvas.restore();

          ViewCompat.setElevation(viewHolder.itemView, DimensionUnit.DP.toPixels(4f));
        } else if (absoluteDx == 0) {
          ViewCompat.setElevation(viewHolder.itemView, DimensionUnit.DP.toPixels(0f));
        }

        viewHolder.itemView.setTranslationX(dX);
      } else {
        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
      }
    }

    @Override
    public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
      super.clearView(recyclerView, viewHolder);

      if (itemAnimator == null) {
        return;
      }

      ViewCompat.setElevation(viewHolder.itemView, 0);
      lastTouched = null;

      View view = getView();
      if (view != null) {
        itemAnimator.postDisable(view.getHandler());
      } else {
        itemAnimator.disable();
      }
    }
  }

  private final class VoiceNotePlayerViewListener implements VoiceNotePlayerView.Listener {

    @Override
    public void onCloseRequested(@NonNull Uri uri) {
      if (voiceNotePlayerViewStub.resolved()) {
        mediaControllerOwner.getVoiceNoteMediaController().stopPlaybackAndReset(uri);
      }
    }

    @Override
    public void onSpeedChangeRequested(@NonNull Uri uri, float speed) {
      mediaControllerOwner.getVoiceNoteMediaController().setPlaybackSpeed(uri, speed);
    }

    @Override
    public void onPlay(@NonNull Uri uri, long messageId, double position) {
      mediaControllerOwner.getVoiceNoteMediaController().startSinglePlayback(uri, messageId, position);
    }

    @Override
    public void onPause(@NonNull Uri uri) {
      mediaControllerOwner.getVoiceNoteMediaController().pausePlayback(uri);
    }

    @Override
    public void onNavigateToMessage(long threadId, @NonNull RecipientId threadRecipientId, @NonNull RecipientId senderId, long messageSentAt, long messagePositionInThread) {
      MainNavigator.get(requireActivity()).goToConversation(threadRecipientId, threadId, ThreadTable.DistributionTypes.DEFAULT, (int) messagePositionInThread);
    }
  }

  public interface Callback extends Material3OnScrollHelperBinder, SearchBinder {
    @NonNull Toolbar getToolbar();

    @NonNull View getUnreadPaymentsDot();

    @NonNull Stub<Toolbar> getBasicToolbar();

    void updateNotificationProfileStatus(@NonNull List<NotificationProfile> notificationProfiles);

    void updateProxyStatus(@NonNull WebSocketConnectionState state);

    void onMultiSelectStarted();

    void onMultiSelectFinished();
  }
}


