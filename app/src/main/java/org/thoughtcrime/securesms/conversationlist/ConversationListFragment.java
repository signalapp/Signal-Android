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
import android.text.TextUtils;
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
import androidx.coordinatorlayout.widget.CoordinatorLayout;
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
import com.bumptech.glide.Glide;
import com.google.android.material.animation.ArgbEvaluatorCompat;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.core.util.DimensionUnit;
import org.signal.core.util.Stopwatch;
import org.signal.core.util.concurrent.LifecycleDisposable;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.concurrent.SimpleTask;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.MainFragment;
import org.thoughtcrime.securesms.MainNavigator;
import org.thoughtcrime.securesms.MuteDialog;
import org.thoughtcrime.securesms.NewConversationActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.backup.v2.ui.BackupAlertDelegate;
import org.thoughtcrime.securesms.badges.models.Badge;
import org.thoughtcrime.securesms.badges.self.expired.ExpiredOneTimeBadgeBottomSheetDialogFragment;
import org.thoughtcrime.securesms.badges.self.expired.MonthlyDonationCanceledBottomSheetDialogFragment;
import org.thoughtcrime.securesms.components.DeleteSyncEducationDialog;
import org.thoughtcrime.securesms.components.Material3SearchToolbar;
import org.thoughtcrime.securesms.components.RatingManager;
import org.thoughtcrime.securesms.components.SignalProgressDialog;
import org.thoughtcrime.securesms.components.menu.ActionItem;
import org.thoughtcrime.securesms.components.menu.SignalBottomActionBar;
import org.thoughtcrime.securesms.components.menu.SignalContextMenu;
import org.thoughtcrime.securesms.components.registration.PulsingFloatingActionButton;
import org.thoughtcrime.securesms.components.reminder.CdsPermanentErrorReminder;
import org.thoughtcrime.securesms.components.reminder.CdsTemporaryErrorReminder;
import org.thoughtcrime.securesms.components.reminder.DozeReminder;
import org.thoughtcrime.securesms.components.reminder.ExpiredBuildReminder;
import org.thoughtcrime.securesms.components.reminder.OutdatedBuildReminder;
import org.thoughtcrime.securesms.components.reminder.Reminder;
import org.thoughtcrime.securesms.components.reminder.ReminderView;
import org.thoughtcrime.securesms.components.reminder.ServiceOutageReminder;
import org.thoughtcrime.securesms.components.reminder.UnauthorizedReminder;
import org.thoughtcrime.securesms.components.reminder.UsernameOutOfSyncReminder;
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity;
import org.thoughtcrime.securesms.components.settings.app.notifications.manual.NotificationProfileSelectionFragment;
import org.thoughtcrime.securesms.components.settings.app.subscription.completed.TerminalDonationDelegate;
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.UnexpectedSubscriptionCancellation;
import org.thoughtcrime.securesms.components.spoiler.SpoilerAnnotation;
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaControllerOwner;
import org.thoughtcrime.securesms.components.voice.VoiceNotePlayerView;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchAdapter;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchData;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchMediator;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchState;
import org.thoughtcrime.securesms.contacts.sync.CdsPermanentErrorBottomSheet;
import org.thoughtcrime.securesms.contacts.sync.CdsTemporaryErrorBottomSheet;
import org.thoughtcrime.securesms.conversationlist.chatfilter.ConversationFilterRequest;
import org.thoughtcrime.securesms.conversationlist.chatfilter.ConversationFilterSource;
import org.thoughtcrime.securesms.conversationlist.chatfilter.ConversationListFilterPullView;
import org.thoughtcrime.securesms.conversationlist.chatfilter.FilterLerp;
import org.thoughtcrime.securesms.conversationlist.model.Conversation;
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter;
import org.thoughtcrime.securesms.database.MessageTable.MarkedMessageInfo;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.ThreadTable;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.events.ReminderUpdateEvent;
import org.thoughtcrime.securesms.groups.SelectionLimits;
import org.thoughtcrime.securesms.jobs.RefreshOwnProfileJob;
import org.thoughtcrime.securesms.jobs.ServiceOutageDetectionJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.lock.v2.CreateSvrPinActivity;
import org.thoughtcrime.securesms.main.Material3OnScrollHelperBinder;
import org.thoughtcrime.securesms.main.SearchBinder;
import org.thoughtcrime.securesms.mediasend.camerax.CameraXUtil;
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionActivity;
import org.thoughtcrime.securesms.megaphone.Megaphone;
import org.thoughtcrime.securesms.megaphone.MegaphoneActionController;
import org.thoughtcrime.securesms.megaphone.MegaphoneViewBuilder;
import org.thoughtcrime.securesms.megaphone.Megaphones;
import org.thoughtcrime.securesms.notifications.MarkReadReceiver;
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.profiles.manage.UsernameEditFragment;
import org.thoughtcrime.securesms.ratelimit.RecaptchaProofBottomSheetFragment;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.registration.ui.RegistrationActivity;
import org.thoughtcrime.securesms.search.MessageResult;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.storage.StorageSyncHelper;
import org.thoughtcrime.securesms.stories.tabs.ConversationListTab;
import org.thoughtcrime.securesms.stories.tabs.ConversationListTabsViewModel;
import org.thoughtcrime.securesms.util.AppForegroundObserver;
import org.thoughtcrime.securesms.util.AppStartup;
import org.thoughtcrime.securesms.util.CachedInflater;
import org.thoughtcrime.securesms.util.ConversationUtil;
import org.thoughtcrime.securesms.util.PlayStoreUtil;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.SignalLocalMetrics;
import org.thoughtcrime.securesms.util.SignalProxyUtil;
import org.thoughtcrime.securesms.util.SnapToTopDataObserver;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.WindowUtil;
import org.thoughtcrime.securesms.util.adapter.mapping.PagingMappingAdapter;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import kotlin.Unit;

import static android.app.Activity.RESULT_OK;


public class ConversationListFragment extends MainFragment implements ActionMode.Callback,
                                                                      ConversationListAdapter.OnConversationClickListener,
                                                                      MegaphoneActionController,
                                                                      ClearFilterViewHolder.OnClearFilterClickListener
{
  public static final short MESSAGE_REQUESTS_REQUEST_CODE_CREATE_NAME = 32562;
  public static final short SMS_ROLE_REQUEST_CODE                     = 32563;

  private static final int LIST_SMOOTH_SCROLL_TO_TOP_THRESHOLD = 25;

  private static final String TAG = Log.tag(ConversationListFragment.class);

  private static final int MAXIMUM_PINNED_CONVERSATIONS = 4;
  private static final int MAX_CHATS_ABOVE_FOLD = 7;
  private static final int MAX_CONTACTS_ABOVE_FOLD = 5;
  private static final int MAX_GROUP_MEMBERSHIPS_ABOVE_FOLD = 5;

  private ActionMode                             actionMode;
  private View                                   coordinator;
  private RecyclerView                           list;
  private Stub<ReminderView>                     reminderView;
  private PulsingFloatingActionButton            fab;
  private PulsingFloatingActionButton            cameraFab;
  private ConversationListFilterPullView         pullView;
  private AppBarLayout                           pullViewAppBarLayout;
  private ConversationListViewModel              viewModel;
  private RecyclerView.Adapter                   activeAdapter;
  private ConversationListAdapter                defaultAdapter;
  private PagingMappingAdapter<ContactSearchKey> searchAdapter;
  private Stub<ViewGroup>                        megaphoneContainer;
  private SnapToTopDataObserver                  snapToTopDataObserver;
  private Drawable                               archiveDrawable;
  private AppForegroundObserver.Listener         appForegroundObserver;
  private VoiceNoteMediaControllerOwner          mediaControllerOwner;
  private Stub<FrameLayout>                      voiceNotePlayerViewStub;
  private VoiceNotePlayerView                    voiceNotePlayerView;
  private SignalBottomActionBar                  bottomActionBar;
  private SignalContextMenu                      activeContextMenu;
  private LifecycleDisposable                    lifecycleDisposable;

  protected ConversationListArchiveItemDecoration archiveDecoration;
  protected ConversationListItemAnimator          itemAnimator;
  private   Stopwatch                             startupStopwatch;
  private   ConversationListTabsViewModel         conversationListTabsViewModel;
  private   ContactSearchMediator                 contactSearchMediator;

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
    getViewLifecycleOwner().getLifecycle().addObserver(new TerminalDonationDelegate(getParentFragmentManager(), getViewLifecycleOwner()));
    BackupAlertDelegate.delegate(getParentFragmentManager(), getViewLifecycleOwner().getLifecycle());

    lifecycleDisposable = new LifecycleDisposable();
    lifecycleDisposable.bindTo(getViewLifecycleOwner());

    coordinator             = view.findViewById(R.id.coordinator);
    list                    = view.findViewById(R.id.list);
    bottomActionBar         = view.findViewById(R.id.conversation_list_bottom_action_bar);
    reminderView            = new Stub<>(view.findViewById(R.id.reminder));
    megaphoneContainer      = new Stub<>(view.findViewById(R.id.megaphone_container));
    voiceNotePlayerViewStub = new Stub<>(view.findViewById(R.id.voice_note_player));
    fab                     = view.findViewById(R.id.fab);
    cameraFab               = view.findViewById(R.id.camera_fab);
    pullView                = view.findViewById(R.id.pull_view);
    pullViewAppBarLayout    = view.findViewById(R.id.recycler_coordinator_app_bar);

    fab.setVisibility(View.VISIBLE);
    cameraFab.setVisibility(View.VISIBLE);

    contactSearchMediator = new ContactSearchMediator(this,
                                                      Collections.emptySet(),
                                                      SelectionLimits.NO_LIMITS,
                                                      new ContactSearchAdapter.DisplayOptions(
                                                          false,
                                                          ContactSearchAdapter.DisplaySecondaryInformation.NEVER,
                                                          false,
                                                          false
                                                      ),
                                                      this::mapSearchStateToConfiguration,
                                                      new ContactSearchMediator.SimpleCallbacks(),
                                                      false,
                                                      (context,
                                                       fixedContacts,
                                                       displayOptions,
                                                       callbacks,
                                                       longClickCallbacks,
                                                       storyContextMenuCallbacks,
                                                       callButtonClickCallbacks
                                                      ) -> {
                                                        //noinspection CodeBlock2Expr
                                                        return new ConversationListSearchAdapter(
                                                            context,
                                                            fixedContacts,
                                                            displayOptions,
                                                            new ContactSearchClickCallbacks(callbacks),
                                                            longClickCallbacks,
                                                            storyContextMenuCallbacks,
                                                            callButtonClickCallbacks,
                                                            getViewLifecycleOwner(),
                                                            Glide.with(this)
                                                        );
                                                      },
                                                      new ConversationListSearchAdapter.ChatFilterRepository()
    );

    searchAdapter = contactSearchMediator.getAdapter();

    CollapsingToolbarLayout collapsingToolbarLayout = view.findViewById(R.id.collapsing_toolbar);
    int                     openHeight              = (int) DimensionUnit.DP.toPixels(FilterLerp.FILTER_OPEN_HEIGHT);

    pullView.setOnFilterStateChanged((state, source) -> {
      switch (state) {
        case CLOSING:
          viewModel.setFiltered(false, source);
          break;
        case OPENING:
          ViewUtil.setMinimumHeight(collapsingToolbarLayout, openHeight);
          viewModel.setFiltered(true, source);
          break;
        case OPEN_APEX:
          if (source == ConversationFilterSource.DRAG) {
            SignalStore.uiHints().incrementNeverDisplayPullToFilterTip();
          }
          break;
        case CLOSE_APEX:
          ViewUtil.setMinimumHeight(collapsingToolbarLayout, 0);
          break;
      }
    });

    pullView.setOnCloseClicked(this::onClearFilterClick);

    ConversationFilterBehavior conversationFilterBehavior = Objects.requireNonNull((ConversationFilterBehavior) ((CoordinatorLayout.LayoutParams) pullViewAppBarLayout.getLayoutParams()).getBehavior());
    conversationFilterBehavior.setCallback(new ConversationFilterBehavior.Callback() {
      @Override
      public void onStopNestedScroll() {
        pullView.onUserDragFinished();
      }

      @Override
      public boolean canStartNestedScroll() {
        return !isSearchOpen() || pullView.isCloseable();
      }
    });

    pullViewAppBarLayout.addOnOffsetChangedListener((layout, verticalOffset) -> {
      float progress = 1 - ((float) verticalOffset) / (-layout.getHeight());
      pullView.onUserDrag(progress);
    });

    fab.show();
    cameraFab.show();

    archiveDecoration = new ConversationListArchiveItemDecoration(new ColorDrawable(getResources().getColor(R.color.conversation_list_archive_background_end)));
    itemAnimator      = new ConversationListItemAnimator();

    list.setLayoutManager(new LinearLayoutManager(requireActivity()));
    list.setItemAnimator(itemAnimator);
    list.addItemDecoration(archiveDecoration);
    CachedInflater.from(list.getContext()).cacheUntilLimit(R.layout.conversation_list_item_view, list, 10);

    snapToTopDataObserver = new SnapToTopDataObserver(list);

    new ItemTouchHelper(new ArchiveListenerCallback(getResources().getColor(R.color.conversation_list_archive_background_start),
                                                    getResources().getColor(R.color.conversation_list_archive_background_end))).attachToRecyclerView(list);

    fab.setOnClickListener(v -> startActivity(new Intent(getActivity(), NewConversationActivity.class)));
    cameraFab.setOnClickListener(v -> {
      if (CameraXUtil.isSupported()) {
        startActivity(MediaSelectionActivity.camera(requireContext()));
      } else {
        Permissions.with(this)
                   .request(Manifest.permission.CAMERA)
                   .ifNecessary()
                   .withRationaleDialog(getString(R.string.CameraXFragment_allow_access_camera), getString(R.string.CameraXFragment_to_capture_photos_and_video_allow_camera), R.drawable.symbol_camera_24)
                   .withPermanentDenialDialog(getString(R.string.CameraXFragment_signal_needs_camera_access_capture_photos), null, R.string.CameraXFragment_allow_access_camera, R.string.CameraXFragment_to_capture_photos_videos, getParentFragmentManager())
                   .onAllGranted(() -> startActivity(MediaSelectionActivity.camera(requireContext())))
                   .onAnyDenied(() -> Toast.makeText(requireContext(), R.string.CameraXFragment_signal_needs_camera_access_capture_photos, Toast.LENGTH_LONG).show())
                   .execute();
      }
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
    SpoilerAnnotation.resetRevealedSpoilers();

    if ((!requireCallback().getSearchToolbar().resolved() || !(requireCallback().getSearchToolbar().get().getVisibility() == View.VISIBLE)) && list.getAdapter() != defaultAdapter) {
      setAdapter(defaultAdapter);
    }

    if (activeAdapter instanceof TimestampPayloadSupport) {
      ((TimestampPayloadSupport) activeAdapter).notifyTimestampPayloadUpdate();
    }

    SignalProxyUtil.startListeningToWebsocket();

    if (SignalStore.rateLimit().needsRecaptcha()) {
      Log.i(TAG, "Recaptcha required.");
      RecaptchaProofBottomSheetFragment.show(getChildFragmentManager());
    }

    Badge                              expiredBadge                       = SignalStore.inAppPayments().getExpiredBadge();
    String                             subscriptionCancellationReason     = SignalStore.inAppPayments().getUnexpectedSubscriptionCancelationReason();
    UnexpectedSubscriptionCancellation unexpectedSubscriptionCancellation = UnexpectedSubscriptionCancellation.fromStatus(subscriptionCancellationReason);
    long                               subscriptionFailureTimestamp       = SignalStore.inAppPayments().getUnexpectedSubscriptionCancelationTimestamp();
    long                               subscriptionFailureWatermark       = SignalStore.inAppPayments().getUnexpectedSubscriptionCancelationWatermark();
    boolean                            isWatermarkPriorToTimestamp        = subscriptionFailureWatermark < subscriptionFailureTimestamp;

    if (unexpectedSubscriptionCancellation != null &&
        !SignalStore.inAppPayments().isDonationSubscriptionManuallyCancelled() &&
        SignalStore.inAppPayments().showCantProcessDialog() &&
        isWatermarkPriorToTimestamp)
    {
      Log.w(TAG, "Displaying bottom sheet for unexpected cancellation: " + unexpectedSubscriptionCancellation, true);
      MonthlyDonationCanceledBottomSheetDialogFragment.show(getChildFragmentManager());
      SignalStore.inAppPayments().setUnexpectedSubscriptionCancelationWatermark(subscriptionFailureTimestamp);
    } else if (unexpectedSubscriptionCancellation != null && SignalStore.inAppPayments().isDonationSubscriptionManuallyCancelled()) {
      Log.w(TAG, "Unexpected cancellation detected but not displaying dialog because user manually cancelled their subscription: " + unexpectedSubscriptionCancellation, true);
      SignalStore.inAppPayments().setUnexpectedSubscriptionCancelationWatermark(subscriptionFailureTimestamp);
    } else if (unexpectedSubscriptionCancellation != null && !SignalStore.inAppPayments().showCantProcessDialog()) {
      Log.w(TAG, "Unexpected cancellation detected but not displaying dialog because user has silenced it.", true);
      SignalStore.inAppPayments().setUnexpectedSubscriptionCancelationWatermark(subscriptionFailureTimestamp);
    }

    if (expiredBadge != null && expiredBadge.isBoost()) {
      SignalStore.inAppPayments().setExpiredBadge(null);

      Log.w(TAG, "Displaying bottom sheet for an expired badge", true);
      ExpiredOneTimeBadgeBottomSheetDialogFragment.show(
          expiredBadge,
          unexpectedSubscriptionCancellation,
          SignalStore.inAppPayments().getUnexpectedSubscriptionCancelationChargeFailure(),
          getParentFragmentManager()
      );
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    AppDependencies.getAppForegroundObserver().addListener(appForegroundObserver);
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
    AppDependencies.getAppForegroundObserver().removeListener(appForegroundObserver);
  }

  @Override
  public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
    menu.clear();
    inflater.inflate(R.menu.text_secure_normal, menu);
  }

  @Override
  public void onPrepareOptionsMenu(Menu menu) {
    menu.findItem(R.id.menu_clear_passphrase).setVisible(!TextSecurePreferences.isPasswordDisabled(requireContext()));

    ConversationFilterRequest request             = viewModel.getConversationFilterRequest();
    boolean                   isChatFilterEnabled = request != null && request.getFilter() == ConversationFilter.UNREAD;

    menu.findItem(R.id.menu_filter_unread_chats).setVisible(!isChatFilterEnabled);
    menu.findItem(R.id.menu_clear_unread_filter).setVisible(isChatFilterEnabled);
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    super.onOptionsItemSelected(item);

    int itemId = item.getItemId();

    if (itemId == R.id.menu_new_group) {
      handleCreateGroup();
      return true;
    } else if (itemId == R.id.menu_settings) {
      handleDisplaySettings();
      return true;
    } else if (itemId == R.id.menu_clear_passphrase) {
      handleClearPassphrase();
      return true;
    } else if (itemId == R.id.menu_mark_all_read) {
      handleMarkAllRead();
      return true;
    } else if (itemId == R.id.menu_invite) {
      handleInvite();
      return true;
    } else if (itemId == R.id.menu_notification_profile) {
      handleNotificationProfile();
      return true;
    } else if (itemId == R.id.menu_filter_unread_chats) {
      handleFilterUnreadChats();
      return true;
    } else if (itemId == R.id.menu_clear_unread_filter) {
      onClearFilterClick();
      return true;
    } else {
      return false;
    }
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    onMegaphoneChanged(viewModel.getMegaphone());
  }

  private ContactSearchConfiguration mapSearchStateToConfiguration(@NonNull ContactSearchState state) {
    if (TextUtils.isEmpty(state.getQuery())) {
      return ContactSearchConfiguration.build(b -> Unit.INSTANCE);
    } else {
      return ContactSearchConfiguration.build(builder -> {
        ConversationFilterRequest conversationFilterRequest = state.getConversationFilterRequest();
        boolean                   unreadOnly                = conversationFilterRequest != null && conversationFilterRequest.getFilter() == ConversationFilter.UNREAD;

        builder.setQuery(state.getQuery());
        builder.addSection(new ContactSearchConfiguration.Section.Chats(
            unreadOnly,
            true,
              new ContactSearchConfiguration.ExpandConfig(
                  state.getExpandedSections().contains(ContactSearchConfiguration.SectionKey.CHATS),
                  (a) -> MAX_CHATS_ABOVE_FOLD
              )
        ));

        if (!unreadOnly) {
          builder.addSection(new ContactSearchConfiguration.Section.GroupsWithMembers(
              true,
              new ContactSearchConfiguration.ExpandConfig(
                  state.getExpandedSections().contains(ContactSearchConfiguration.SectionKey.GROUPS_WITH_MEMBERS),
                  (a) -> MAX_GROUP_MEMBERSHIPS_ABOVE_FOLD
              )
          ));

          builder.addSection(new ContactSearchConfiguration.Section.ContactsWithoutThreads(
              true,
              new ContactSearchConfiguration.ExpandConfig(
                  state.getExpandedSections().contains(ContactSearchConfiguration.SectionKey.CONTACTS_WITHOUT_THREADS),
                  (a) -> MAX_CONTACTS_ABOVE_FOLD
              )
          ));

          builder.addSection(new ContactSearchConfiguration.Section.Messages(
              true,
              null
          ));

          builder.withEmptyState(emptyStateBuilder -> {
            emptyStateBuilder.addSection(ContactSearchConfiguration.Section.Empty.INSTANCE);
            return Unit.INSTANCE;
          });
        } else {
          builder.arbitrary(
              conversationFilterRequest.getSource() == ConversationFilterSource.DRAG
                ? ConversationListSearchAdapter.ChatFilterOptions.WITHOUT_TIP.getCode()
                : ConversationListSearchAdapter.ChatFilterOptions.WITH_TIP.getCode()
          );
        }

        return Unit.INSTANCE;
      });
    }
  }

  private boolean isSearchOpen() {
    return isSearchVisible() || activeAdapter == searchAdapter;
  }

  private boolean isSearchVisible() {
    return (requireCallback().getSearchToolbar().resolved() && requireCallback().getSearchToolbar().get().getVisibility() == View.VISIBLE);
  }

  private boolean closeSearchIfOpen() {
    if (isSearchOpen()) {
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
    if (resultCode == RESULT_OK && requestCode == CreateSvrPinActivity.REQUEST_NEW_PIN) {
      Snackbar.make(fab, R.string.ConfirmKbsPinFragment__pin_created, Snackbar.LENGTH_LONG).show();
      viewModel.onMegaphoneCompleted(Megaphones.Event.PINS_FOR_ALL);
    }

    if (resultCode == RESULT_OK && requestCode == UsernameEditFragment.REQUEST_CODE) {
      String snackbarString = getString(R.string.ConversationListFragment_username_recovered_toast, SignalStore.account().getUsername());
      Snackbar.make(fab, snackbarString, Snackbar.LENGTH_LONG).show();
    }
  }

  private void onConversationClicked(@NonNull ThreadRecord threadRecord) {
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

  private void onContactClicked(@NonNull Recipient contact) {
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

  private void onMessageClicked(@NonNull MessageResult message) {
    SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
      int startingPosition = SignalDatabase.messages().getMessagePositionInConversation(message.getThreadId(), message.getReceivedTimestampMs());
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
    } else if (reminderActionId == R.id.reminder_action_fix_username_and_link) {
      startActivityForResult(AppSettingsActivity.usernameRecovery(requireContext()), UsernameEditFragment.REQUEST_CODE);
    } else if (reminderActionId == R.id.reminder_action_fix_username_link) {
      startActivity(AppSettingsActivity.usernameLinkSettings(requireContext()));
    } else if (reminderActionId == R.id.reminder_action_re_register) {
      startActivity(RegistrationActivity.newIntentForReRegistration(requireContext()));
    }
  }

  private void hideKeyboard() {
    InputMethodManager imm = ServiceUtil.getInputMethodManager(requireContext());
    imm.hideSoftInputFromWindow(requireView().getWindowToken(), 0);
  }

  private void initializeSearchListener() {
    lifecycleDisposable.add(
        viewModel.getFilterRequestState().subscribe(request -> {
          updateSearchToolbarHint(request);
          contactSearchMediator.onConversationFilterRequestChanged(request);
        })
    );

    requireCallback().getSearchAction().setOnClickListener(v -> {
      fadeOutButtonsAndMegaphone(250);
      requireCallback().onSearchOpened();

      requireCallback().getSearchToolbar().get().setListener(new Material3SearchToolbar.Listener() {
        @Override
        public void onSearchTextChange(String text) {
          String trimmed = text.trim();

          contactSearchMediator.onFilterChanged(trimmed);

          if (trimmed.length() > 0) {
            if (activeAdapter != searchAdapter && list != null) {
              setAdapter(searchAdapter);
            }
          } else {
            if (activeAdapter != defaultAdapter) {
              if (list != null) {
                setAdapter(defaultAdapter);
              }
            }
          }
        }

        @Override
        public void onSearchClosed() {
          if (list != null) {
            setAdapter(defaultAdapter);
          }
          requireCallback().onSearchClosed();
          fadeInButtonsAndMegaphone(250);
        }
      });
      updateSearchToolbarHint(Objects.requireNonNull(viewModel.getConversationFilterRequest()));
    });
  }

  private void updateSearchToolbarHint(@NonNull ConversationFilterRequest conversationFilterRequest) {
    Stub<Material3SearchToolbar> searchToolbar = requireCallback().getSearchToolbar();
    if (!searchToolbar.resolved()) {
      return;
    }
    searchToolbar.get().setSearchInputHint(
        conversationFilterRequest.getFilter() == ConversationFilter.OFF ? R.string.SearchToolbar_search : R.string.SearchToolbar_search_unread_chats
    );
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
    defaultAdapter          = new ConversationListAdapter(getViewLifecycleOwner(), Glide.with(this), this, this);

    setAdapter(defaultAdapter);

    defaultAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
      @Override
      public void onItemRangeInserted(int positionStart, int itemCount) {
        startupStopwatch.split("data-set");
        SignalLocalMetrics.ColdStart.onConversationListDataLoaded();
        defaultAdapter.unregisterAdapterDataObserver(this);
        if (requireActivity() instanceof MainActivity) {
          ((MainActivity) requireActivity()).onFirstRender();
        }
        list.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
          @Override
          public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
            list.removeOnLayoutChangeListener(this);
            list.post(ConversationListFragment.this::onFirstRender);
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
      ((ConversationListAdapter) adapter).setPagingController(viewModel.getController());
    }

    list.setAdapter(adapter);

    if (adapter == defaultAdapter) {
      defaultAdapter.registerAdapterDataObserver(snapToTopDataObserver);
    } else {
      defaultAdapter.unregisterAdapterDataObserver(snapToTopDataObserver);
    }
  }

  private void initializeTypingObserver() {
    AppDependencies.getTypingStatusRepository().getTypingThreads().observe(getViewLifecycleOwner(), threadIds -> {
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
    viewModel = new ViewModelProvider(this, new ConversationListViewModel.Factory(isArchived())).get(ConversationListViewModel.class);

    lifecycleDisposable.add(viewModel.getMegaphoneState().subscribe(this::onMegaphoneChanged));
    lifecycleDisposable.add(viewModel.getConversationsState().subscribe(this::onConversationListChanged));
    lifecycleDisposable.add(viewModel.getHasNoConversations().subscribe(this::updateEmptyState));
    lifecycleDisposable.add(viewModel.getNotificationProfiles().subscribe(profiles -> requireCallback().updateNotificationProfileStatus(profiles)));
    lifecycleDisposable.add(viewModel.getWebSocketState().subscribe(pipeState -> requireCallback().updateProxyStatus(pipeState)));

    appForegroundObserver = new AppForegroundObserver.Listener() {
      @Override
      public void onForeground() {
        viewModel.onVisible();
      }

      @Override
      public void onBackground() {}
    };

    lifecycleDisposable.add(
        viewModel.getSelectedState().subscribe(conversations -> {
          defaultAdapter.setSelectedConversations(conversations);
          updateMultiSelectState();
        })
    );
  }

  private void onFirstRender() {
    AppStartup.getInstance().onCriticalRenderEventEnd();
    startupStopwatch.split("first-render");
    startupStopwatch.stop(TAG);
    mediaControllerOwner.getVoiceNoteMediaController().finishPostpone();

    if (getParentFragment() != null) {
      requireCallback().getSearchToolbar().get();
    }

    Context context = getContext();
    if (context != null) {
      FrameLayout parent = new FrameLayout(context);
      parent.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

      CachedInflater.from(context).cacheUntilLimit(R.layout.v2_conversation_item_text_only_incoming, parent, 25);
      CachedInflater.from(context).cacheUntilLimit(R.layout.v2_conversation_item_text_only_outgoing, parent, 25);
      CachedInflater.from(context).cacheUntilLimit(R.layout.conversation_item_received_multimedia, parent, 10);
      CachedInflater.from(context).cacheUntilLimit(R.layout.conversation_item_sent_multimedia, parent, 10);
      CachedInflater.from(context).cacheUntilLimit(R.layout.conversation_item_update, parent, 5);
      CachedInflater.from(context).cacheUntilLimit(R.layout.cursor_adapter_header_footer_view, parent, 2);
    }
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

  private void onMegaphoneChanged(@NonNull Megaphone megaphone) {
    if (megaphone == Megaphone.NONE || isArchived() || getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
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
      if (ExpiredBuildReminder.isEligible()) {
        return Optional.of(new ExpiredBuildReminder(context));
      } else if (UnauthorizedReminder.isEligible(context)) {
        return Optional.of(new UnauthorizedReminder());
      } else if (ServiceOutageReminder.isEligible(context)) {
        AppDependencies.getJobManager().add(new ServiceOutageDetectionJob());
        return Optional.of(new ServiceOutageReminder());
      } else if (OutdatedBuildReminder.isEligible()) {
        return Optional.of(new OutdatedBuildReminder(context));
      } else if (DozeReminder.isEligible(context)) {
        return Optional.of(new DozeReminder(context));
      } else if (CdsTemporaryErrorReminder.isEligible()) {
        return Optional.of(new CdsTemporaryErrorReminder());
      } else if (CdsPermanentErrorReminder.isEligible()) {
        return Optional.of(new CdsPermanentErrorReminder());
      } else if (UsernameOutOfSyncReminder.isEligible()) {
        AppDependencies.getJobManager().add(new RefreshOwnProfileJob());
        return Optional.of(new UsernameOutOfSyncReminder());
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

      AppDependencies.getMessageNotifier().updateNotification(context);
      MarkReadReceiver.process(messageIds);
    });
  }

  private void handleMarkAsRead(@NonNull Collection<Long> ids) {
    Context   context   = requireContext();
    Stopwatch stopwatch = new Stopwatch("mark-read");

    SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
      stopwatch.split("task-start");

      List<MarkedMessageInfo> messageIds = SignalDatabase.threads().setRead(ids, false);
      stopwatch.split("db");

      AppDependencies.getMessageNotifier().updateNotification(context);
      stopwatch.split("notification");

      MarkReadReceiver.process(messageIds);
      stopwatch.split("process");

      return null;
    }, none -> {
      endActionModeIfActive();
      stopwatch.stop(TAG);

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

  private void handleNotificationProfile() {
    NotificationProfileSelectionFragment.show(getParentFragmentManager());
  }

  private void handleFilterUnreadChats() {
    pullView.toggle();
    pullViewAppBarLayout.setExpanded(false, true);
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
    if (DeleteSyncEducationDialog.shouldShow()) {
      lifecycleDisposable.add(
          DeleteSyncEducationDialog.show(getChildFragmentManager())
                                   .subscribe(() -> handleDelete(ids))
      );

      return;
    }

    int                        conversationsCount = ids.size();
    MaterialAlertDialogBuilder alert              = new MaterialAlertDialogBuilder(requireActivity());
    Context                    context            = requireContext();

    alert.setTitle(context.getResources().getQuantityString(R.plurals.ConversationListFragment_delete_selected_conversations,
                                                            conversationsCount, conversationsCount));

    if (TextSecurePreferences.isMultiDevice(context) && Recipient.self().getDeleteSyncCapability().isSupported()) {
      alert.setMessage(context.getResources().getQuantityString(R.plurals.ConversationListFragment_this_will_permanently_delete_all_n_selected_conversations_linked_device,
                                                                conversationsCount, conversationsCount));
    } else {
      alert.setMessage(context.getResources().getQuantityString(R.plurals.ConversationListFragment_this_will_permanently_delete_all_n_selected_conversations,
                                                                conversationsCount, conversationsCount));
    }

    alert.setCancelable(true);

    alert.setPositiveButton(R.string.delete, (dialog, which) -> {
      final Set<Long> selectedConversations = new HashSet<>(ids);

      if (!selectedConversations.isEmpty()) {
        new AsyncTask<Void, Void, Void>() {
          private SignalProgressDialog dialog;

          @Override
          protected void onPreExecute() {
            dialog = SignalProgressDialog.show(requireActivity(),
                                               context.getString(R.string.ConversationListFragment_deleting),
                                               context.getResources().getQuantityString(R.plurals.ConversationListFragment_deleting_selected_conversations, conversationsCount),
                                               true,
                                               false);
          }

          @Override
          protected Void doInBackground(Void... params) {
            SignalDatabase.threads().deleteConversations(selectedConversations, true);
            AppDependencies.getMessageNotifier().updateNotification(requireActivity());
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
    if (fab != null) {
      ViewUtil.fadeOut(fab, fadeDuration);
    }
    if (cameraFab != null) {
      ViewUtil.fadeOut(cameraFab, fadeDuration);
    }
    if (megaphoneContainer != null && megaphoneContainer.resolved()) {
      ViewUtil.fadeOut(megaphoneContainer.get(), fadeDuration);
    }
  }

  private void fadeInButtonsAndMegaphone(int fadeDuration) {
    if (fab != null) {
      ViewUtil.fadeIn(fab, fadeDuration);
    }
    if (cameraFab != null) {
      ViewUtil.fadeIn(cameraFab, fadeDuration);
    }
    if (megaphoneContainer != null && megaphoneContainer.resolved()) {
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
      AppDependencies.getMegaphoneRepository().markFinished(Megaphones.Event.ONBOARDING);
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
        items.add(new ActionItem(R.drawable.symbol_chat_badge_24, getResources().getQuantityString(R.plurals.ConversationListFragment_unread_plural, 1), () -> handleMarkAsUnread(id)));
      } else {
        items.add(new ActionItem(R.drawable.symbol_chat_24, getResources().getQuantityString(R.plurals.ConversationListFragment_read_plural, 1), () -> handleMarkAsRead(id)));
      }

      if (conversation.getThreadRecord().isPinned()) {
        items.add(new ActionItem(R.drawable.symbol_pin_slash_24, getResources().getString(R.string.ConversationListFragment_unpin), () -> handleUnpin(id)));
      } else {
        items.add(new ActionItem(R.drawable.symbol_pin_24, getResources().getString(R.string.ConversationListFragment_pin), () -> handlePin(Collections.singleton(conversation))));
      }

      if (conversation.getThreadRecord().getRecipient().live().get().isMuted()) {
        items.add(new ActionItem(R.drawable.symbol_bell_24, getResources().getString(R.string.ConversationListFragment_unmute), () -> handleUnmute(Collections.singleton(conversation))));
      } else {
        items.add(new ActionItem(R.drawable.symbol_bell_slash_24, getResources().getString(R.string.ConversationListFragment_mute), () -> handleMute(Collections.singleton(conversation))));
      }
    }

    items.add(new ActionItem(R.drawable.symbol_check_circle_24, getString(R.string.ConversationListFragment_select), () -> {
      viewModel.startSelection(conversation);
      startActionMode();
    }));

    if (conversation.getThreadRecord().isArchived()) {
      items.add(new ActionItem(R.drawable.symbol_archive_up_24, getResources().getString(R.string.ConversationListFragment_unarchive), () -> handleArchive(id, false)));
    } else {
      items.add(new ActionItem(R.drawable.symbol_archive_24, getResources().getString(R.string.ConversationListFragment_archive), () -> handleArchive(id, false)));
    }

    items.add(new ActionItem(R.drawable.symbol_trash_24, getResources().getString(R.string.ConversationListFragment_delete), () -> handleDelete(id)));

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

    TypedArray color = getActivity().getTheme().obtainStyledAttributes(new int[] { android.R.attr.statusBarColor });
    WindowUtil.setStatusBarColor(getActivity().getWindow(), color.getColor(0, Color.BLACK));
    color.recycle();

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
      items.add(new ActionItem(R.drawable.symbol_chat_24, getResources().getQuantityString(R.plurals.ConversationListFragment_read_plural, count), () -> handleMarkAsRead(selectionIds)));
    } else {
      items.add(new ActionItem(R.drawable.symbol_chat_badge_24, getResources().getQuantityString(R.plurals.ConversationListFragment_unread_plural, count), () -> handleMarkAsUnread(selectionIds)));
    }

    if (!isArchived() && hasUnpinned && canPin) {
      items.add(new ActionItem(R.drawable.symbol_pin_24, getResources().getString(R.string.ConversationListFragment_pin), () -> handlePin(viewModel.currentSelectedConversations())));
    } else if (!isArchived() && !hasUnpinned) {
      items.add(new ActionItem(R.drawable.symbol_pin_slash_24, getResources().getString(R.string.ConversationListFragment_unpin), () -> handleUnpin(selectionIds)));
    }

    if (isArchived()) {
      items.add(new ActionItem(R.drawable.symbol_archive_up_24, getResources().getString(R.string.ConversationListFragment_unarchive), () -> handleArchive(selectionIds, true)));
    } else {
      items.add(new ActionItem(R.drawable.symbol_archive_24, getResources().getString(R.string.ConversationListFragment_archive), () -> handleArchive(selectionIds, true)));
    }

    items.add(new ActionItem(R.drawable.symbol_trash_24, getResources().getString(R.string.ConversationListFragment_delete), () -> handleDelete(selectionIds)));

    if (hasUnmuted) {
      items.add(new ActionItem(R.drawable.symbol_bell_slash_24, getResources().getString(R.string.ConversationListFragment_mute), () -> handleMute(viewModel.currentSelectedConversations())));
    } else {
      items.add(new ActionItem(R.drawable.symbol_bell_24, getResources().getString(R.string.ConversationListFragment_unmute), () -> handleUnmute(viewModel.currentSelectedConversations())));
    }

    items.add(new ActionItem(R.drawable.symbol_check_circle_24, getString(R.string.ConversationListFragment_select_all), viewModel::onSelectAllClick));

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
    return R.drawable.symbol_archive_24;
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
          AppDependencies.getMessageNotifier().updateNotification(context);
          MarkReadReceiver.process(messageIds);
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
          AppDependencies.getMessageNotifier().updateNotification(context);
        }

        ConversationUtil.refreshRecipientShortcuts();
      }
    }.executeOnExecutor(SignalExecutors.BOUNDED, threadId);
  }

  @Override
  public void onClearFilterClick() {
    pullView.toggle();
    pullViewAppBarLayout.setExpanded(false, true);
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
          viewHolder instanceof ClearFilterViewHolder ||
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

  private class ContactSearchClickCallbacks implements ConversationListSearchAdapter.ConversationListSearchClickCallbacks {

    private final ContactSearchAdapter.ClickCallbacks delegate;

    private ContactSearchClickCallbacks(@NonNull ContactSearchAdapter.ClickCallbacks delegate) {
      this.delegate = delegate;
    }

    @Override
    public void onThreadClicked(@NonNull View view, @NonNull ContactSearchData.Thread thread, boolean isSelected) {
      onConversationClicked(thread.getThreadRecord());
    }

    @Override
    public void onMessageClicked(@NonNull View view, @NonNull ContactSearchData.Message thread, boolean isSelected) {
      ConversationListFragment.this.onMessageClicked(thread.getMessageResult());
    }

    @Override
    public void onGroupWithMembersClicked(@NonNull View view, @NonNull ContactSearchData.GroupWithMembers groupWithMembers, boolean isSelected) {
      onContactClicked(Recipient.resolved(groupWithMembers.getGroupRecord().getRecipientId()));
    }

    @Override
    public void onClearFilterClicked() {
      onClearFilterClick();
    }

    @Override
    public void onStoryClicked(@NonNull View view, @NonNull ContactSearchData.Story story, boolean isSelected) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void onKnownRecipientClicked(@NonNull View view, @NonNull ContactSearchData.KnownRecipient knownRecipient, boolean isSelected) {
      onContactClicked(knownRecipient.getRecipient());
    }

    @Override
    public void onExpandClicked(@NonNull ContactSearchData.Expand expand) {
      delegate.onExpandClicked(expand);
    }

    @Override
    public void onUnknownRecipientClicked(@NonNull View view, @NonNull ContactSearchData.UnknownRecipient unknownRecipient, boolean isSelected) {
      throw new UnsupportedOperationException();
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


