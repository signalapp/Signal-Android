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
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.PluralsRes;
import androidx.annotation.WorkerThread;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.res.ResourcesCompat;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.TransitionManager;
import org.thoughtcrime.securesms.search.MessageResult;
import org.thoughtcrime.securesms.search.SearchResult;
import org.thoughtcrime.securesms.components.UnreadPaymentsView;
import org.thoughtcrime.securesms.conversationlist.model.UnreadPayments;
import org.thoughtcrime.securesms.payments.preferences.PaymentsActivity;
import org.thoughtcrime.securesms.payments.preferences.details.PaymentDetailsFragmentArgs;
import org.thoughtcrime.securesms.payments.preferences.details.PaymentDetailsParcelable;

import com.google.android.material.snackbar.Snackbar;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.MainFragment;
import org.thoughtcrime.securesms.MainNavigator;
import org.thoughtcrime.securesms.NewConversationActivity;
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.RatingManager;
import org.thoughtcrime.securesms.components.recyclerview.DeleteItemAnimator;
import org.thoughtcrime.securesms.components.settings.CustomizableSingleSelectSetting;
import org.thoughtcrime.securesms.conversation.ConversationFragment;
import org.thoughtcrime.securesms.conversationlist.model.Conversation;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessageDatabase.MarkedMessageInfo;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.events.ReminderUpdateEvent;
import org.thoughtcrime.securesms.insights.InsightsLauncher;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.megaphone.Megaphones;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.notifications.MarkReadReceiver;
import org.thoughtcrime.securesms.ratelimit.RecaptchaProofBottomSheetFragment;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.sms.MessageSender;

import org.thoughtcrime.securesms.util.AppForegroundObserver;
import org.thoughtcrime.securesms.util.AppStartup;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.SignalLocalMetrics;
import org.thoughtcrime.securesms.util.SnapToTopDataObserver;
import org.thoughtcrime.securesms.util.Stopwatch;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.task.SnackbarAsyncTask;
import org.thoughtcrime.securesms.util.views.Stub;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static android.app.Activity.RESULT_OK;


public class ConversationListFragment extends MainFragment implements ConversationListAdapter.OnConversationClickListener,
                                                                      MainNavigator.BackHandler, View.OnKeyListener
{
  public static final short MESSAGE_REQUESTS_REQUEST_CODE_CREATE_NAME = 32562;
  public static final short SMS_ROLE_REQUEST_CODE                     = 32563;

  private static final String TAG = Log.tag(ConversationListFragment.class);

  private static final int MAXIMUM_PINNED_CONVERSATIONS = 4;

  public static int longClickItemPosition = -1;

//  private ConstraintLayout                  constraintLayout;
//  private Stub<UnreadPaymentsView>          paymentNotificationView;
//  private View                              unreadPaymentsDot;

  private RecyclerView                      list;
  private ConversationListViewModel         viewModel;
  private RecyclerView.Adapter              activeAdapter;
  private ConversationListAdapter           defaultAdapter;
  private SnapToTopDataObserver             snapToTopDataObserver;
  private Drawable                          archiveDrawable;
//  private AppForegroundObserver.Listener    appForegroundObserver;

  private Stopwatch startupStopwatch;
  private boolean isFromLauncher = false;
  private boolean isFirstEnter = false;

  public static ConversationListFragment newInstance() {
    return new ConversationListFragment();
  }

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    Intent intent = getActivity().getIntent();
    isFromLauncher = intent.getBooleanExtra("fromLauncher",false);
    startupStopwatch = new Stopwatch("startup");
    isFirstEnter = true;
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    return inflater.inflate(R.layout.conversation_list_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    list                    = view.findViewById(R.id.list);
//    constraintLayout        = view.findViewById(R.id.constraint_layout);

    list.setLayoutManager(new LinearLayoutManager(requireActivity()));
    list.setItemAnimator(new DeleteItemAnimator());
    list.setClipToPadding(false);
    list.setClipChildren(false);
    list.setPadding(0, 76, 0, 200);

    snapToTopDataObserver = new SnapToTopDataObserver(list);

    new ItemTouchHelper(new ArchiveListenerCallback()).attachToRecyclerView(list);

    initializeViewModel();
    initializeListAdapters();
    initializeTypingObserver();

    if (isFromLauncher){
      list.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver
              .OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            list.post(new Runnable() {
              @Override
              public void run() {
                handleCreateConversation(getDefaultAdapter().getConversation().getThreadRecord().getThreadId(),getDefaultAdapter().getConversation().getThreadRecord().getRecipient(),getDefaultAdapter().getConversation().getThreadRecord().getDistributionType());
              }
            });
            defaultAdapter.setFromLauncher(true);
            isFromLauncher = false;
          list.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        }
      });
    }

    RatingManager.showRatingDialogIfNecessary(requireContext());
  }

  public ConversationListAdapter getDefaultAdapter(){
    return defaultAdapter;
  }

  @Override
  public void onResume() {
    super.onResume();
    //Add for MP02 Notification
    Intent intent = new Intent();
    intent.setAction("clear.notification.from.signal");
    requireActivity().sendBroadcast(intent);

    EventBus.getDefault().register(this);

    if (Util.isDefaultSmsProvider(requireContext())) {
      InsightsLauncher.showInsightsModal(requireContext(), requireFragmentManager());
    }

    if (list.getAdapter() != defaultAdapter) {
      setAdapter(defaultAdapter);
    }

//    if (activeAdapter != null) {
//      activeAdapter.notifyDataSetChanged();
//    }

    if (SignalStore.rateLimit().needsRecaptcha()) {
      Log.i(TAG, "Recaptcha required.");
      RecaptchaProofBottomSheetFragment.show(getChildFragmentManager());
    }
    if(getNavigator().getFromSearch()) {
      getNavigator().setFromSearch(false);
      list.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver
              .OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
          View view = list.getLayoutManager().findViewByPosition(4); //Search
          if (view != null) {
            view.requestFocus();
          }
          list.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        }
      });
    }

    if(getNavigator().getFromArchive()) {
      getNavigator().setFromArchive(false);
      list.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver
              .OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
          View view = list.getLayoutManager().findViewByPosition(list.getLayoutManager().getItemCount() - 1);//Archive
          if (view != null) {
            view.requestFocus();
          }
          list.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        }
      });
    }

    if(getNavigator().getFromOptions()) {
      getNavigator().setFromOptions(false);
      list.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver
              .OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
          View view = list.getLayoutManager().findViewByPosition(longClickItemPosition);
          if (view != null) {
            view.requestFocus();
            longClickItemPosition = -1;
          }else{
            View view1 = list.getLayoutManager().findViewByPosition(list.getLayoutManager().getItemCount() - 1);//Archive
            if (view1 != null) {
              view1.requestFocus();
            }
          }
          list.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        }
      });
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    ConversationFragment.prepare(requireContext());
//    ApplicationDependencies.getAppForegroundObserver().addListener(appForegroundObserver);
  }

  @Override
  public void onPause() {
    super.onPause();

    EventBus.getDefault().unregister(this);
  }

  @Override
  public void onStop() {
    super.onStop();
//    ApplicationDependencies.getAppForegroundObserver().removeListener(appForegroundObserver);
  }

  @Override
  public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
    menu.clear();
    inflater.inflate(R.menu.text_secure_normal, menu);
  }

  @Override
  public boolean onBackPressed() {
    return closeSearchIfOpen();
  }

  private boolean closeSearchIfOpen() {
    return false;
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (resultCode != RESULT_OK) {
      return;
    }
  }

  @Override
  public void onShowArchiveClick() {
    getNavigator().goToArchiveList();
  }

  private void handleCreateSearch() {
    getNavigator().goToSearch();
  }

  private void initializeListAdapters() {
    defaultAdapter          = new ConversationListAdapter(requireContext(), GlideApp.with(this), this, this);

    defaultAdapter.setData(getDatas());
    defaultAdapter.setHasDivider(true, getDatas().size() - 1);
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
    viewModel = ViewModelProviders.of(this, new ConversationListViewModel.Factory(isArchived())).get(ConversationListViewModel.class);

    viewModel.getConversationList().observe(getViewLifecycleOwner(), this::onSubmitList);

//    appForegroundObserver = new AppForegroundObserver.Listener() {
//      @Override
//      public void onForeground() {
//        viewModel.onVisible();
//      }
//
//      @Override
//      public void onBackground() { }

//    viewModel.getUnreadPaymentsLiveData().observe(getViewLifecycleOwner(), this::onUnreadPaymentsChanged);

  }

  /*private void onUnreadPaymentsChanged(@NonNull Optional<UnreadPayments> unreadPayments) {
    if (unreadPayments.isPresent()) {
      paymentNotificationView.get().setListener(new PaymentNotificationListener(unreadPayments.get()));
      paymentNotificationView.get().setUnreadPayments(unreadPayments.get());
      animatePaymentUnreadStatusIn();
    } else {
      animatePaymentUnreadStatusOut();
    }
  }

  private void animatePaymentUnreadStatusIn() {
    animatePaymentUnreadStatus(ConstraintSet.VISIBLE);
    unreadPaymentsDot.animate().alpha(1);
  }

  private void animatePaymentUnreadStatusOut() {
    if (paymentNotificationView.resolved()) {
      animatePaymentUnreadStatus(ConstraintSet.GONE);
    }

    unreadPaymentsDot.animate().alpha(0);
  }

  private void animatePaymentUnreadStatus(int constraintSetVisibility) {
    paymentNotificationView.get();

    TransitionManager.beginDelayedTransition(constraintLayout);

    ConstraintSet currentLayout = new ConstraintSet();
    currentLayout.clone(constraintLayout);

    currentLayout.setVisibility(R.id.payments_notification, constraintSetVisibility);
    currentLayout.applyTo(constraintLayout);
  }*/

  /*private class PaymentNotificationListener implements UnreadPaymentsView.Listener {

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
      Intent intent = new Intent(requireContext(), PaymentsActivity.class);

      intent.putExtra(PaymentsActivity.EXTRA_PAYMENTS_STARTING_ACTION, R.id.action_directly_to_paymentDetails);
      intent.putExtra(PaymentsActivity.EXTRA_STARTING_ARGUMENTS, new PaymentDetailsFragmentArgs.Builder(PaymentDetailsParcelable.forUuid(paymentId)).build().toBundle());

      startActivity(intent);
    }
  }*/

  private void handleMarkSelectedAsRead() {
    Context context = requireContext();

    SignalExecutors.BOUNDED.execute(() -> {
      List<MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context).setAllThreadsRead();
      ApplicationDependencies.getMessageNotifier().updateNotification(context);
      MarkReadReceiver.process(context, messageIds);
    });
  }

//  @SuppressLint("StaticFieldLeak")
//  private void handleArchiveAllSelected() {
//    Set<Long> selectedConversations = new HashSet<>(defaultAdapter.getBatchSelectionIds());
//    int       count                 = selectedConversations.size();
//    String    snackBarTitle         = getResources().getQuantityString(getArchivedSnackbarTitleRes(), count, count);
//
//    new SnackbarAsyncTask<Void>(getViewLifecycleOwner().getLifecycle(),
//                                requireView(),
//                                snackBarTitle,
//                                getString(R.string.ConversationListFragment_undo),
//                                getResources().getColor(R.color.amber_500),
//                                Snackbar.LENGTH_LONG, true)
//    {
//
//      @Override
//      protected void onPostExecute(Void result) {
//        super.onPostExecute(result);
//
//        if (actionMode != null) {
//          actionMode.finish();
//          actionMode = null;
//        }
//      }
//
//      @Override
//      protected void executeAction(@Nullable Void parameter) {
//        archiveThreads(selectedConversations);
//      }
//
//      @Override
//      protected void reverseAction(@Nullable Void parameter) {
//        reverseArchiveThreads(selectedConversations);
//      }
//    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
//  }

//  private void handlePinAllSelected() {
//    final Set<Long> toPin = new LinkedHashSet<>(Stream.of(defaultAdapter.getBatchSelection())
//                                                      .filterNot(conversation -> conversation.getThreadRecord().isPinned())
//                                                      .map(conversation -> conversation.getThreadRecord().getThreadId())
//                                                      .toList());
//
//    if (toPin.size() + viewModel.getPinnedCount() > MAXIMUM_PINNED_CONVERSATIONS) {
//      actionMode.finish();
//      return;
//    }
//
//    SimpleTask.run(SignalExecutors.BOUNDED, () -> {
//      ThreadDatabase db = DatabaseFactory.getThreadDatabase(ApplicationDependencies.getApplication());
//
//      db.pinConversations(toPin);
//
//      return null;
//    }, unused -> {
//      if (actionMode != null) {
//        actionMode.finish();
//      }
//    });
//  }
//
//  private void handleUnpinAllSelected() {
//    final Set<Long> toPin = new HashSet<>(defaultAdapter.getBatchSelectionIds());
//
//    SimpleTask.run(SignalExecutors.BOUNDED, () -> {
//      ThreadDatabase db = DatabaseFactory.getThreadDatabase(ApplicationDependencies.getApplication());
//
//      db.unpinConversations(toPin);
//
//      return null;
//    }, unused -> {
//      if (actionMode != null) {
//        actionMode.finish();
//      }
//    });
//  }

  private void handleCreateConversation(long threadId, Recipient recipient, int distributionType) {
    getNavigator().goToConversation(recipient.getId(), threadId, distributionType, -1);
  }

  private void onSubmitList(@NonNull List<Conversation> conversationList) {
    List<Conversation> myList = new ArrayList<>(conversationList);
    if (defaultAdapter.getDataSize() > 0) {
      for (int position = 0; position < defaultAdapter.getDataSize(); position++) {
        myList.add(position, new Conversation());
      }
    }
    defaultAdapter.submitList(myList);
    if (isFirstEnter) {
      defaultAdapter.notifyDataSetChanged();
      isFirstEnter = false;
    }
    onPostSubmitList(conversationList.size());
  }

  protected void onPostSubmitList(int conversationCount) {
    if (conversationCount >= 6 && (SignalStore.onboarding().shouldShowInviteFriends() || SignalStore.onboarding().shouldShowNewGroup())) {
      SignalStore.onboarding().clearAll();
      ApplicationDependencies.getMegaphoneRepository().markFinished(Megaphones.Event.ONBOARDING);
    }
  }

  @Override
  public void onConversationClick(ConversationListItem item, Conversation conversation) {
    int viewType = item.getViewType();
    if (viewType == ConversationListAdapter.MENU_OPTIONS_TYPE) {
      String menuName = item.getFromText();
      List<String> data = getDatas();
      if (menuName.equals(data.get(0))) { //New Conversaton
        startActivity(new Intent(getActivity(), NewConversationActivity.class));
      } else if (menuName.equals(data.get(1))) { //New Group
        getNavigator().goToGroupCreation();
      } else if (menuName.equals(data.get(2))) {// Mark all read
        handleMarkSelectedAsRead();
      } else if (menuName.equals(data.get(3))) {// Setting
        getNavigator().goToAppSettings();
      } else if (menuName.equals(data.get(4))) {//Search
        getNavigator().goToSearch();
      }
    } else {
      handleCreateConversation(conversation.getThreadRecord().getThreadId(), conversation.getThreadRecord().getRecipient(), conversation.getThreadRecord().getDistributionType());
    }
  }

  @Override
  public boolean onConversationLongClick(ConversationListItem item) {
//    if (actionMode != null) {
////      onConversationClick(conversation);
//      return true;
//    }
//
//    defaultAdapter.initializeBatchMode(true);
//    defaultAdapter.toggleConversationInBatchSet(conversation);
//
    Set<Long> batchSet = Collections.synchronizedSet(new HashSet<Long>());
    batchSet.add(item.getThreadId());
    getNavigator().setCurrentConversation(item.getThreadId(), batchSet);
    getNavigator().goToOptionsList();
    return true;
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onEvent(ReminderUpdateEvent event) {

  }

  @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
  public void onEvent(MessageSender.MessageSentEvent event) {
    EventBus.getDefault().removeStickyEvent(event);
    closeSearchIfOpen();
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
      private final ThreadDatabase threadDatabase= DatabaseFactory.getThreadDatabase(getActivity());

      private List<Long> pinnedThreadIds;
      @Override
      protected void executeAction(@Nullable Long parameter) {
        Context context = requireActivity();

        pinnedThreadIds = threadDatabase.getPinnedThreadIds();
        threadDatabase.archiveConversation(threadId);

        if (unreadCount > 0) {
          List<MarkedMessageInfo> messageIds = threadDatabase.setRead(threadId, false);
          ApplicationDependencies.getMessageNotifier().updateNotification(context);
          MarkReadReceiver.process(context, messageIds);
        }
      }

      @Override
      protected void reverseAction(@Nullable Long parameter) {
        Context context = requireActivity();

        threadDatabase.unarchiveConversation(threadId);
        threadDatabase.restorePins(pinnedThreadIds);

        if (unreadCount > 0) {
          threadDatabase.incrementUnread(threadId, unreadCount);
          ApplicationDependencies.getMessageNotifier().updateNotification(context);
        }
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, threadId);
  }

  @Override
  public boolean onKey(View v, int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_CALL){
      List<String> data = getDatas();
      String fromText = defaultAdapter.getFromText().getFromText();
      if (fromText.equals(data.get(0))) { //New Conversaton
      } else if (fromText.equals(data.get(1))) { //New Group
      } else if (fromText.equals(data.get(2))) {// Mark all read
      } else if (fromText.equals(data.get(3))) {// Setting
      } else if (fromText.equals(data.get(4))) {//Search
      }else if (TextUtils.isEmpty(fromText)){
      }else {
        handleDial(defaultAdapter.getFromText().getRecipient());
      }
      return true;
    }
    return false;
  }

  private void handleDial(final Recipient recipient) {
    if (recipient == null) return;
    Log.d(TAG, "MENGNAN handleDial");
    CommunicationActions.startVoiceCall(getActivity(), recipient);
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
          viewHolder instanceof ConversationListAdapter.HeaderViewHolder)
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

  private List<String> getDatas() {
    List<String> datas = Arrays.asList(getResources().getStringArray(R.array.conversation_list_item_menu));
    return datas;
  }
}


