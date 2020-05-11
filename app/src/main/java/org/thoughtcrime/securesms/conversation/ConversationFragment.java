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
package org.thoughtcrime.securesms.conversation;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.ClipboardManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.text.HtmlCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.OnScrollListener;

import com.annimon.stream.Stream;
import com.google.android.collect.Sets;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.MessageDetailsActivity;
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.sharing.ShareActivity;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.components.ConversationTypingView;
import org.thoughtcrime.securesms.components.TooltipPopup;
import org.thoughtcrime.securesms.components.recyclerview.SmoothScrollingLinearLayoutManager;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.contactshare.ContactUtil;
import org.thoughtcrime.securesms.contactshare.SharedContactDetailsActivity;
import org.thoughtcrime.securesms.conversation.ConversationAdapter.HeaderViewHolder;
import org.thoughtcrime.securesms.conversation.ConversationAdapter.ItemClickListener;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessagingDatabase;
import org.thoughtcrime.securesms.database.MmsSmsDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.loaders.ConversationLoader;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob;
import org.thoughtcrime.securesms.jobs.MultiDeviceViewOnceOpenJob;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.longmessage.LongMessageActivity;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.messagerequests.MessageRequestViewModel;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.profiles.UnknownSenderView;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.reactions.ReactionsBottomSheetDialogFragment;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.revealable.ViewOnceMessageActivity;
import org.thoughtcrime.securesms.revealable.ViewOnceUtil;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.stickers.StickerLocator;
import org.thoughtcrime.securesms.stickers.StickerPackPreviewActivity;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.HtmlUtil;
import org.thoughtcrime.securesms.util.RemoteDeleteUtil;
import org.thoughtcrime.securesms.util.SaveAttachmentTask;
import org.thoughtcrime.securesms.util.StickyHeaderDecoration;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.util.task.ProgressDialogAsyncTask;
import org.thoughtcrime.securesms.util.views.AdaptiveActionsToolbar;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@SuppressLint("StaticFieldLeak")
public class ConversationFragment extends Fragment
  implements LoaderManager.LoaderCallbacks<Cursor>
{
  private static final String TAG       = ConversationFragment.class.getSimpleName();
  private static final String KEY_LIMIT = "limit";

  private static final int PARTIAL_CONVERSATION_LIMIT = 500;
  private static final int SCROLL_ANIMATION_THRESHOLD = 50;
  private static final int CODE_ADD_EDIT_CONTACT      = 77;

  private final ActionModeCallback actionModeCallback     = new ActionModeCallback();
  private final ItemClickListener  selectionClickListener = new ConversationFragmentItemClickListener();

  private ConversationFragmentListener listener;

  private LiveRecipient               recipient;
  private long                        threadId;
  private long                        lastSeen;
  private int                         startingPosition;
  private int                         previousOffset;
  private int                         activeOffset;
  private boolean                     firstLoad;
  private boolean                     isReacting;
  private ActionMode                  actionMode;
  private Locale                      locale;
  private RecyclerView                list;
  private RecyclerView.ItemDecoration lastSeenDecoration;
  private ViewSwitcher                topLoadMoreView;
  private ViewSwitcher                bottomLoadMoreView;
  private ConversationTypingView      typingView;
  private UnknownSenderView           unknownSenderView;
  private View                        composeDivider;
  private View                        scrollToBottomButton;
  private TextView                    scrollDateHeader;
  private ConversationBannerView      conversationBanner;
  private ConversationBannerView      emptyConversationBanner;
  private MessageRequestViewModel     messageRequestViewModel;

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    this.locale = (Locale) getArguments().getSerializable(PassphraseRequiredActionBarActivity.LOCALE_EXTRA);
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    final View view = inflater.inflate(R.layout.conversation_fragment, container, false);
    list                    = ViewUtil.findById(view, android.R.id.list);
    composeDivider          = ViewUtil.findById(view, R.id.compose_divider);
    scrollToBottomButton    = ViewUtil.findById(view, R.id.scroll_to_bottom_button);
    scrollDateHeader        = ViewUtil.findById(view, R.id.scroll_date_header);
    emptyConversationBanner = ViewUtil.findById(view, R.id.empty_conversation_banner);

    scrollToBottomButton.setOnClickListener(v -> scrollToBottom());

    final LinearLayoutManager layoutManager = new SmoothScrollingLinearLayoutManager(getActivity(), true);
    list.setHasFixedSize(false);
    list.setLayoutManager(layoutManager);
    list.setItemAnimator(null);

    if (FeatureFlags.messageRequests()) {
      conversationBanner = (ConversationBannerView) inflater.inflate(R.layout.conversation_item_banner, container, false);
    }

    topLoadMoreView    = (ViewSwitcher) inflater.inflate(R.layout.load_more_header, container, false);
    bottomLoadMoreView = (ViewSwitcher) inflater.inflate(R.layout.load_more_header, container, false);
    initializeLoadMoreView(topLoadMoreView);
    initializeLoadMoreView(bottomLoadMoreView);

    typingView = (ConversationTypingView) inflater.inflate(R.layout.conversation_typing_view, container, false);

    new ConversationItemSwipeCallback(
            messageRecord -> actionMode == null &&
                             MenuState.canReplyToMessage(MenuState.isActionMessage(messageRecord), messageRecord, messageRequestViewModel.shouldShowMessageRequest()),
            this::handleReplyMessage
    ).attachToRecyclerView(list);

    setupListLayoutListeners();

    return view;
  }

  private void setupListLayoutListeners() {
    if (!FeatureFlags.messageRequests()) {
      return;
    }

    list.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> setListVerticalTranslation());

    list.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
      @Override
      public void onChildViewAttachedToWindow(@NonNull View view) {
        setListVerticalTranslation();
      }

      @Override
      public void onChildViewDetachedFromWindow(@NonNull View view) {
        setListVerticalTranslation();
      }
    });
  }

  private void setListVerticalTranslation() {
    if (list.canScrollVertically(1) || list.canScrollVertically(-1) || list.getChildCount() == 0) {
      list.setTranslationY(0);
      list.setOverScrollMode(RecyclerView.OVER_SCROLL_IF_CONTENT_SCROLLS);
    } else {
      int chTop = list.getChildAt(list.getChildCount() - 1).getTop();
      list.setTranslationY(Math.min(0, -chTop));
      list.setOverScrollMode(RecyclerView.OVER_SCROLL_NEVER);
    }
    listener.onListVerticalTranslationChanged(list.getTranslationY());
  }

  @Override
  public void onActivityCreated(Bundle bundle) {
    super.onActivityCreated(bundle);

    initializeResources();
    initializeMessageRequestViewModel();
    initializeListAdapter();
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    this.listener = (ConversationFragmentListener)activity;
  }

  @Override
  public void onStart() {
    super.onStart();
    initializeTypingObserver();
  }

  @Override
  public void onResume() {
    super.onResume();

    if (list.getAdapter() != null) {
      list.getAdapter().notifyDataSetChanged();
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    ApplicationContext.getInstance(requireContext()).getTypingStatusRepository().getTypists(threadId).removeObservers(this);
  }

  public void onNewIntent() {
    if (actionMode != null) {
      actionMode.finish();
    }

    initializeResources();
    messageRequestViewModel.setConversationInfo(recipient.getId(), threadId);
    initializeListAdapter();

    if (threadId == -1) {
      getLoaderManager().restartLoader(0, Bundle.EMPTY, this);
    }
  }

  public void reloadList() {
    getLoaderManager().restartLoader(0, Bundle.EMPTY, this);
  }

  public void moveToLastSeen() {
    if (lastSeen <= 0) {
      Log.i(TAG, "No need to move to last seen.");
      return;
    }

    if (list == null || getListAdapter() == null) {
      Log.w(TAG, "Tried to move to last seen position, but we hadn't initialized the view yet.");
      return;
    }

    int position = getListAdapter().findLastSeenPosition(lastSeen);
    scrollToLastSeenPosition(position);
  }

  private void initializeMessageRequestViewModel() {
    MessageRequestViewModel.Factory factory = new MessageRequestViewModel.Factory(requireContext());

    messageRequestViewModel = ViewModelProviders.of(requireActivity(), factory).get(MessageRequestViewModel.class);
    messageRequestViewModel.setConversationInfo(recipient.getId(), threadId);

    listener.onMessageRequest(messageRequestViewModel);

    messageRequestViewModel.getRecipientInfo().observe(getViewLifecycleOwner(), recipientInfo -> {
      presentMessageRequestProfileView(requireContext(), recipientInfo, conversationBanner);
      presentMessageRequestProfileView(requireContext(), recipientInfo, emptyConversationBanner);
    });
  }

  private static void presentMessageRequestProfileView(@NonNull Context context, @NonNull MessageRequestViewModel.RecipientInfo recipientInfo, @Nullable ConversationBannerView conversationBanner) {

    if (conversationBanner == null) {
      return;
    }

    Recipient    recipient          = recipientInfo.getRecipient();
    boolean      isSelf             = Recipient.self().equals(recipient);
    int          memberCount        = recipientInfo.getGroupMemberCount();
    int          pendingMemberCount = recipientInfo.getGroupPendingMemberCount();
    List<String> groups             = recipientInfo.getSharedGroups();

    if (recipient != null) {
      conversationBanner.setAvatar(GlideApp.with(context), recipient);

      String title = isSelf ? context.getString(R.string.note_to_self) : recipient.getDisplayName(context);
      conversationBanner.setTitle(title);

      if (recipient.isGroup()) {
        if (pendingMemberCount > 0) {
          conversationBanner.setSubtitle(context.getResources()
                                                .getQuantityString(R.plurals.MessageRequestProfileView_members_and_invited, memberCount,
                                                                   memberCount, pendingMemberCount));
        } else {
          conversationBanner.setSubtitle(context.getResources().getQuantityString(R.plurals.MessageRequestProfileView_members, memberCount,
                                                                                  memberCount));
        }
      } else if (isSelf) {
        conversationBanner.setSubtitle(context.getString(R.string.ConversationFragment__you_can_add_notes_for_yourself_in_this_conversation));
      } else {
        String subtitle = recipient.getUsername().or(recipient.getE164()).orNull();

        if (subtitle == null || subtitle.equals(title)) {
          conversationBanner.hideSubtitle();
        } else {
          conversationBanner.setSubtitle(subtitle);
        }
      }
    }

    if (groups.isEmpty() || isSelf) {
      conversationBanner.hideDescription();
    } else {
      final String description;

      switch (groups.size()) {
        case 1:
          description = context.getString(R.string.MessageRequestProfileView_member_of_one_group, HtmlUtil.bold(groups.get(0)));
          break;
        case 2:
          description = context.getString(R.string.MessageRequestProfileView_member_of_two_groups, HtmlUtil.bold(groups.get(0)), HtmlUtil.bold(groups.get(1)));
          break;
        case 3:
          description = context.getString(R.string.MessageRequestProfileView_member_of_many_groups, HtmlUtil.bold(groups.get(0)), HtmlUtil.bold(groups.get(1)), HtmlUtil.bold(groups.get(2)));
          break;
        default:
          int others = groups.size() - 2;
          description = context.getString(R.string.MessageRequestProfileView_member_of_many_groups,
                                          HtmlUtil.bold(groups.get(0)),
                                          HtmlUtil.bold(groups.get(1)),
                                          context.getResources().getQuantityString(R.plurals.MessageRequestProfileView_member_of_others, others, others));
      }

      conversationBanner.setDescription(HtmlCompat.fromHtml(description, 0));
      conversationBanner.showDescription();
    }
  }

  private void initializeResources() {
    long oldThreadId = threadId;

    this.recipient         = Recipient.live(getActivity().getIntent().getParcelableExtra(ConversationActivity.RECIPIENT_EXTRA));
    this.threadId          = this.getActivity().getIntent().getLongExtra(ConversationActivity.THREAD_ID_EXTRA, -1);
    this.lastSeen          = this.getActivity().getIntent().getLongExtra(ConversationActivity.LAST_SEEN_EXTRA, -1);
    this.startingPosition  = this.getActivity().getIntent().getIntExtra(ConversationActivity.STARTING_POSITION_EXTRA, -1);
    this.firstLoad         = true;
    this.unknownSenderView = new UnknownSenderView(getActivity(), recipient.get(), threadId);

    OnScrollListener scrollListener = new ConversationScrollListener(getActivity());
    list.addOnScrollListener(scrollListener);

    if (oldThreadId != threadId) {
      ApplicationContext.getInstance(requireContext()).getTypingStatusRepository().getTypists(oldThreadId).removeObservers(this);
    }
  }

  private void initializeListAdapter() {
    if (this.recipient != null && this.threadId != -1) {
      Log.d(TAG, "Initializing adapter for " + recipient.getId());
      ConversationAdapter adapter = new ConversationAdapter(requireContext(), GlideApp.with(this), locale, selectionClickListener, null, this.recipient.get());
      list.setAdapter(adapter);
      list.addItemDecoration(new StickyHeaderDecoration(adapter, false, false));

      setLastSeen(lastSeen);
      getLoaderManager().restartLoader(0, Bundle.EMPTY, this);

      emptyConversationBanner.setVisibility(View.GONE);
    } else if (FeatureFlags.messageRequests() && threadId == -1) {
      emptyConversationBanner.setVisibility(View.VISIBLE);
    }
  }

  private void initializeLoadMoreView(ViewSwitcher loadMoreView) {
    loadMoreView.setOnClickListener(v -> {
      Bundle args = new Bundle();
      args.putInt(KEY_LIMIT, 0);
      getLoaderManager().restartLoader(0, args, ConversationFragment.this);
      loadMoreView.showNext();
      loadMoreView.setOnClickListener(null);
    });
  }

  private void initializeTypingObserver() {
    if (!TextSecurePreferences.isTypingIndicatorsEnabled(requireContext())) {
      return;
    }

    ApplicationContext.getInstance(requireContext()).getTypingStatusRepository().getTypists(threadId).removeObservers(this);
    ApplicationContext.getInstance(requireContext()).getTypingStatusRepository().getTypists(threadId).observe(this, typingState ->  {
      List<Recipient> recipients;
      boolean         replacedByIncomingMessage;

      if (typingState != null) {
        recipients                = typingState.getTypists();
        replacedByIncomingMessage = typingState.isReplacedByIncomingMessage();
      } else {
        recipients                = Collections.emptyList();
        replacedByIncomingMessage = false;
      }

      typingView.setTypists(GlideApp.with(ConversationFragment.this), recipients, recipient.get().isGroup());

      ConversationAdapter adapter = getListAdapter();

      if (adapter.getHeaderView() != null && adapter.getHeaderView() != typingView) {
        Log.i(TAG, "Skipping typing indicator -- the header slot is occupied.");
        return;
      }

      if (recipients.size() > 0) {
        if (!isTypingIndicatorShowing() && isAtBottom()) {
          Context context = requireContext();
          list.setVerticalScrollBarEnabled(false);
          list.post(() -> {
            if (!isReacting) {
              getListLayoutManager().smoothScrollToPosition(context, 0, 250);
            }
          });
          list.postDelayed(() -> list.setVerticalScrollBarEnabled(true), 300);
          adapter.setHeaderView(typingView);
          adapter.notifyItemInserted(0);
        } else {
          if (isTypingIndicatorShowing()) {
            adapter.setHeaderView(typingView);
            adapter.notifyItemChanged(0);
          } else {
            adapter.setHeaderView(typingView);
            adapter.notifyItemInserted(0);
          }
        }
      } else {
        if (isTypingIndicatorShowing() && getListLayoutManager().findFirstCompletelyVisibleItemPosition() == 0 && getListLayoutManager().getItemCount() > 1 && !replacedByIncomingMessage) {
          if (!isReacting) {
            getListLayoutManager().smoothScrollToPosition(requireContext(), 1, 250);
          }
          list.setVerticalScrollBarEnabled(false);
          list.postDelayed(() -> {
            adapter.setHeaderView(null);
            adapter.notifyItemRemoved(0);
            list.post(() -> list.setVerticalScrollBarEnabled(true));
          }, 200);
        } else if (!replacedByIncomingMessage) {
          adapter.setHeaderView(null);
          adapter.notifyItemRemoved(0);
        } else {
          adapter.setHeaderView(null);
        }
      }
    });
  }

  private void setCorrectMenuVisibility(@NonNull Menu menu) {
    Set<MessageRecord> messageRecords = getListAdapter().getSelectedItems();

    if (actionMode != null && messageRecords.size() == 0) {
      actionMode.finish();
      return;
    }

    MenuState menuState = MenuState.getMenuState(messageRecords, messageRequestViewModel.shouldShowMessageRequest());

    menu.findItem(R.id.menu_context_forward).setVisible(menuState.shouldShowForwardAction());
    menu.findItem(R.id.menu_context_reply).setVisible(menuState.shouldShowReplyAction());
    menu.findItem(R.id.menu_context_details).setVisible(menuState.shouldShowDetailsAction());
    menu.findItem(R.id.menu_context_save_attachment).setVisible(menuState.shouldShowSaveAttachmentAction());
    menu.findItem(R.id.menu_context_resend).setVisible(menuState.shouldShowResendAction());
    menu.findItem(R.id.menu_context_copy).setVisible(menuState.shouldShowCopyAction());
  }

  private ConversationAdapter getListAdapter() {
    return (ConversationAdapter) list.getAdapter();
  }

  private SmoothScrollingLinearLayoutManager getListLayoutManager() {
    return (SmoothScrollingLinearLayoutManager) list.getLayoutManager();
  }

  private MessageRecord getSelectedMessageRecord() {
    Set<MessageRecord> messageRecords = getListAdapter().getSelectedItems();

    if (messageRecords.size() == 1) return messageRecords.iterator().next();
    else                            throw new AssertionError();
  }

  public void reload(Recipient recipient, long threadId) {
    this.recipient = recipient.live();

    if (this.threadId != threadId) {
      this.threadId = threadId;
      messageRequestViewModel.setConversationInfo(recipient.getId(), threadId);
      initializeListAdapter();
    }
  }

  public void scrollToBottom() {
    if (getListLayoutManager().findFirstVisibleItemPosition() < SCROLL_ANIMATION_THRESHOLD) {
      list.smoothScrollToPosition(0);
    } else {
      list.scrollToPosition(0);
    }
  }

  public void setLastSeen(long lastSeen) {
    this.lastSeen = lastSeen;
    if (lastSeenDecoration != null) {
      list.removeItemDecoration(lastSeenDecoration);
    }

    lastSeenDecoration = new ConversationAdapter.LastSeenHeader(getListAdapter(), lastSeen);
    list.addItemDecoration(lastSeenDecoration);
  }

  private void handleCopyMessage(final Set<MessageRecord> messageRecords) {
    List<MessageRecord> messageList = new LinkedList<>(messageRecords);
    Collections.sort(messageList, new Comparator<MessageRecord>() {
      @Override
      public int compare(MessageRecord lhs, MessageRecord rhs) {
        if      (lhs.getDateReceived() < rhs.getDateReceived())  return -1;
        else if (lhs.getDateReceived() == rhs.getDateReceived()) return 0;
        else                                                     return 1;
      }
    });

    StringBuilder    bodyBuilder = new StringBuilder();
    ClipboardManager clipboard   = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);

    for (MessageRecord messageRecord : messageList) {
      String body = messageRecord.getDisplayBody(requireContext()).toString();
      if (!TextUtils.isEmpty(body)) {
        bodyBuilder.append(body).append('\n');
      }
    }
    if (bodyBuilder.length() > 0 && bodyBuilder.charAt(bodyBuilder.length() - 1) == '\n') {
      bodyBuilder.deleteCharAt(bodyBuilder.length() - 1);
    }

    String result = bodyBuilder.toString();

    if (!TextUtils.isEmpty(result))
        clipboard.setText(result);
  }

  private void handleDeleteMessages(final Set<MessageRecord> messageRecords) {
    if (FeatureFlags.remoteDelete()) {
      buildRemoteDeleteConfirmationDialog(messageRecords).show();
    } else {
      buildLegacyDeleteConfirmationDialog(messageRecords).show();
    }
  }

  private AlertDialog.Builder buildLegacyDeleteConfirmationDialog(Set<MessageRecord> messageRecords) {
    int                 messagesCount = messageRecords.size();
    AlertDialog.Builder builder       = new AlertDialog.Builder(getActivity());

    builder.setIconAttribute(R.attr.dialog_alert_icon);
    builder.setTitle(getActivity().getResources().getQuantityString(R.plurals.ConversationFragment_delete_selected_messages, messagesCount, messagesCount));
    builder.setMessage(getActivity().getResources().getQuantityString(R.plurals.ConversationFragment_this_will_permanently_delete_all_n_selected_messages, messagesCount, messagesCount));
    builder.setCancelable(true);

    builder.setPositiveButton(R.string.delete, (dialog, which) -> {
      new ProgressDialogAsyncTask<Void, Void, Void>(getActivity(),
                                                    R.string.ConversationFragment_deleting,
                                                    R.string.ConversationFragment_deleting_messages)
      {
        @Override
        protected Void doInBackground(Void... voids) {
          for (MessageRecord messageRecord : messageRecords) {
            boolean threadDeleted;

            if (messageRecord.isMms()) {
              threadDeleted = DatabaseFactory.getMmsDatabase(getActivity()).delete(messageRecord.getId());
            } else {
              threadDeleted = DatabaseFactory.getSmsDatabase(getActivity()).deleteMessage(messageRecord.getId());
            }

            if (threadDeleted) {
              threadId = -1;
              listener.setThreadId(threadId);
            }
          }

          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    });

    builder.setNegativeButton(android.R.string.cancel, null);
    return builder;
  }

  private AlertDialog.Builder buildRemoteDeleteConfirmationDialog(Set<MessageRecord> messageRecords) {
    Context             context       = requireActivity();
    int                 messagesCount = messageRecords.size();
    AlertDialog.Builder builder       = new AlertDialog.Builder(getActivity());

    builder.setTitle(getActivity().getResources().getQuantityString(R.plurals.ConversationFragment_delete_selected_messages, messagesCount, messagesCount));
    builder.setCancelable(true);

    builder.setPositiveButton(R.string.ConversationFragment_delete_for_me, (dialog, which) -> {
      new ProgressDialogAsyncTask<Void, Void, Void>(getActivity(),
                                                    R.string.ConversationFragment_deleting,
                                                    R.string.ConversationFragment_deleting_messages)
      {
        @Override
        protected Void doInBackground(Void... voids) {
          for (MessageRecord messageRecord : messageRecords) {
            boolean threadDeleted;

            if (messageRecord.isMms()) {
              threadDeleted = DatabaseFactory.getMmsDatabase(context).delete(messageRecord.getId());
            } else {
              threadDeleted = DatabaseFactory.getSmsDatabase(context).deleteMessage(messageRecord.getId());
            }

            if (threadDeleted) {
              threadId = -1;
              listener.setThreadId(threadId);
            }
          }

          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    });

    if (RemoteDeleteUtil.isValidSend(messageRecords, System.currentTimeMillis())) {
      builder.setNeutralButton(R.string.ConversationFragment_delete_for_everyone, (dialog, which) -> {
        SignalExecutors.BOUNDED.execute(() -> {
          for (MessageRecord message : messageRecords) {
            MessageSender.sendRemoteDelete(context, message.getId(), message.isMms());
          }
        });
      });
    }

    builder.setNegativeButton(android.R.string.cancel, null);
    return builder;
  }


  private void handleDisplayDetails(MessageRecord message) {
    Intent intent = new Intent(getActivity(), MessageDetailsActivity.class);
    intent.putExtra(MessageDetailsActivity.MESSAGE_ID_EXTRA, message.getId());
    intent.putExtra(MessageDetailsActivity.THREAD_ID_EXTRA, threadId);
    intent.putExtra(MessageDetailsActivity.TYPE_EXTRA, message.isMms() ? MmsSmsDatabase.MMS_TRANSPORT : MmsSmsDatabase.SMS_TRANSPORT);
    intent.putExtra(MessageDetailsActivity.RECIPIENT_EXTRA, recipient.getId());
    intent.putExtra(MessageDetailsActivity.IS_PUSH_GROUP_EXTRA, recipient.get().isGroup() && message.isPush());
    startActivity(intent);
  }

  private void handleForwardMessage(MessageRecord message) {
    if (message.isViewOnce()) {
      throw new AssertionError("Cannot forward a view-once message.");
    }

    listener.onForwardClicked();

    SimpleTask.run(getLifecycle(), () -> {
      Intent composeIntent = new Intent(getActivity(), ShareActivity.class);
      composeIntent.putExtra(Intent.EXTRA_TEXT, message.getDisplayBody(requireContext()).toString());

      if (message.isMms()) {
        MmsMessageRecord mediaMessage = (MmsMessageRecord) message;
        boolean          isAlbum      = mediaMessage.containsMediaSlide()                      &&
                                        mediaMessage.getSlideDeck().getSlides().size() > 1     &&
                                        mediaMessage.getSlideDeck().getAudioSlide() == null    &&
                                        mediaMessage.getSlideDeck().getDocumentSlide() == null &&
                                        mediaMessage.getSlideDeck().getStickerSlide() == null;

        if (isAlbum) {
          ArrayList<Media> mediaList   = new ArrayList<>(mediaMessage.getSlideDeck().getSlides().size());
          List<Attachment> attachments = Stream.of(mediaMessage.getSlideDeck().getSlides())
                                               .filter(s -> s.hasImage() || s.hasVideo())
                                               .map(Slide::asAttachment)
                                               .toList();

          for (Attachment attachment : attachments) {
            Uri uri = attachment.getDataUri() != null ? attachment.getDataUri() : attachment.getThumbnailUri();

            if (uri != null) {
              mediaList.add(new Media(uri,
                                      attachment.getContentType(),
                                      System.currentTimeMillis(),
                                      attachment.getWidth(),
                                      attachment.getHeight(),
                                      attachment.getSize(),
                                      0,
                                      Optional.absent(),
                                      Optional.fromNullable(attachment.getCaption()),
                                      Optional.absent()));
            }
          };

          if (!mediaList.isEmpty()) {
            composeIntent.putExtra(ConversationActivity.MEDIA_EXTRA, mediaList);
          }
        } else if (mediaMessage.containsMediaSlide()) {
          Slide slide = mediaMessage.getSlideDeck().getSlides().get(0);
          composeIntent.putExtra(Intent.EXTRA_STREAM, slide.getUri());
          composeIntent.setType(slide.getContentType());

          if (slide.hasSticker()) {
            composeIntent.putExtra(ConversationActivity.STICKER_EXTRA, slide.asAttachment().getSticker());
          }
        }

        if (mediaMessage.getSlideDeck().getTextSlide() != null && mediaMessage.getSlideDeck().getTextSlide().getUri() != null) {
          try (InputStream stream = PartAuthority.getAttachmentStream(requireContext(), mediaMessage.getSlideDeck().getTextSlide().getUri())) {
            String fullBody = Util.readFullyAsString(stream);
            composeIntent.putExtra(Intent.EXTRA_TEXT, fullBody);
          } catch (IOException e) {
            Log.w(TAG, "Failed to read long message text when forwarding.");
          }
        }
      }

      return composeIntent;
    }, this::startActivity);
  }

  private void handleResendMessage(final MessageRecord message) {
    final Context context = getActivity().getApplicationContext();
    new AsyncTask<MessageRecord, Void, Void>() {
      @Override
      protected Void doInBackground(MessageRecord... messageRecords) {
        MessageSender.resend(context, messageRecords[0]);
        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, message);
  }

  private void handleReplyMessage(final MessageRecord message) {
    if (getActivity() != null) {
      //noinspection ConstantConditions
      ((AppCompatActivity) getActivity()).getSupportActionBar().collapseActionView();
    }

    listener.handleReplyMessage(message);
  }

  private void handleSaveAttachment(final MediaMmsMessageRecord message) {
    if (message.isViewOnce()) {
      throw new AssertionError("Cannot save a view-once message.");
    }

    SaveAttachmentTask.showWarningDialog(getActivity(), new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        List<SaveAttachmentTask.Attachment> attachments = Stream.of(message.getSlideDeck().getSlides())
                                                                .filter(s -> s.getUri() != null && (s.hasImage() || s.hasVideo() || s.hasAudio() || s.hasDocument()))
                                                                .map(s -> new SaveAttachmentTask.Attachment(s.getUri(), s.getContentType(), message.getDateReceived(), s.getFileName().orNull()))
                                                                .toList();
        if (!Util.isEmpty(attachments)) {
          SaveAttachmentTask saveTask = new SaveAttachmentTask(getActivity());
          saveTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, attachments.toArray(new SaveAttachmentTask.Attachment[0]));
          return;
        }

        Log.w(TAG, "No slide with attachable media found, failing nicely.");
        Toast.makeText(getActivity(),
                       getResources().getQuantityString(R.plurals.ConversationFragment_error_while_saving_attachments_to_sd_card, 1),
                       Toast.LENGTH_LONG).show();
      }
    });
  }

  @Override
  public @NonNull Loader<Cursor> onCreateLoader(int id, Bundle args) {
    Log.i(TAG, "onCreateLoader");

    int limit  = args.getInt(KEY_LIMIT, PARTIAL_CONVERSATION_LIMIT);
    int offset = 0;
    if (limit != 0 && startingPosition >= limit) {
      offset = Math.max(startingPosition - (limit / 2) + 1, 0);
      startingPosition -= offset - 1;
    }

    return new ConversationLoader(getActivity(), threadId, offset, limit, lastSeen);
  }

  @Override
  public void onLoadFinished(@NonNull Loader<Cursor> cursorLoader, Cursor cursor) {
    int                count  = cursor.getCount();
    ConversationLoader loader = (ConversationLoader) cursorLoader;

    ConversationAdapter adapter = getListAdapter();
    if (adapter == null) {
      return;
    }

    if (cursor.getCount() >= PARTIAL_CONVERSATION_LIMIT && loader.hasLimit()) {
      adapter.setFooterView(topLoadMoreView);
    } else if (FeatureFlags.messageRequests()) {
      adapter.setFooterView(conversationBanner);
    } else {
      adapter.setFooterView(null);
    }

    if (lastSeen == -1) {
      setLastSeen(loader.getLastSeen());
    }

    if (FeatureFlags.messageRequests() && !loader.hasPreMessageRequestMessages()) {
      clearHeaderIfNotTyping(adapter);
    } else {
      if (!loader.hasSent() && !recipient.get().isSystemContact() && !recipient.get().isGroup() && recipient.get().getRegistered() == RecipientDatabase.RegisteredState.REGISTERED) {
        adapter.setHeaderView(unknownSenderView);
      } else {
        clearHeaderIfNotTyping(adapter);
      }
    }

    if (loader.hasOffset()) {
      adapter.setHeaderView(bottomLoadMoreView);
    }

    if (firstLoad || loader.hasOffset()) {
      previousOffset = loader.getOffset();
    }

    activeOffset = loader.getOffset();
    adapter.changeCursor(cursor);
    listener.onCursorChanged();

    int lastSeenPosition = adapter.findLastSeenPosition(lastSeen);

    if (isTypingIndicatorShowing()) {
      lastSeenPosition = Math.max(lastSeenPosition - 1, 0);
    }

    if (firstLoad) {
      if (startingPosition >= 0) {
        scrollToStartingPosition(startingPosition);
      } else if (loader.isMessageRequestAccepted()) {
        scrollToLastSeenPosition(lastSeenPosition);
      } else if (FeatureFlags.messageRequests()) {
        list.post(() -> getListLayoutManager().scrollToPosition(adapter.getItemCount() - 1));
      }
      firstLoad = false;
    } else if (previousOffset > 0) {
      int scrollPosition = previousOffset + getListLayoutManager().findFirstVisibleItemPosition();
      scrollPosition = Math.min(scrollPosition, count - 1);

      View firstView = list.getLayoutManager().getChildAt(scrollPosition);
      int pixelOffset = (firstView == null) ? 0 : (firstView.getBottom() - list.getPaddingBottom());

      getListLayoutManager().scrollToPositionWithOffset(scrollPosition, pixelOffset);
      previousOffset = 0;
    }

    if (lastSeenPosition <= 0) {
      setLastSeen(0);
    }
  }

  private void clearHeaderIfNotTyping(ConversationAdapter adapter) {
    if (adapter.getHeaderView() != typingView) {
      adapter.setHeaderView(null);
    }
  }

  @Override
  public void onLoaderReset(@NonNull Loader<Cursor> arg0) {
    if (list.getAdapter() != null) {
      getListAdapter().changeCursor(null);
      listener.onCursorChanged();
    }
  }

  public long stageOutgoingMessage(OutgoingMediaMessage message) {
    MessageRecord messageRecord = DatabaseFactory.getMmsDatabase(getContext()).readerFor(message, threadId).getCurrent();

    if (getListAdapter() != null) {
      clearHeaderIfNotTyping(getListAdapter());
      setLastSeen(0);
      getListAdapter().addFastRecord(messageRecord);
    }

    return messageRecord.getId();
  }

  public long stageOutgoingMessage(OutgoingTextMessage message) {
    MessageRecord messageRecord = DatabaseFactory.getSmsDatabase(getContext()).readerFor(message, threadId).getCurrent();

    if (getListAdapter() != null) {
      clearHeaderIfNotTyping(getListAdapter());
      setLastSeen(0);
      getListAdapter().addFastRecord(messageRecord);
    }

    return messageRecord.getId();
  }

  public void releaseOutgoingMessage(long id) {
    if (getListAdapter() != null) {
      getListAdapter().releaseFastRecord(id);
    }
  }

  private void scrollToStartingPosition(final int startingPosition) {
    list.post(() -> {
      list.getLayoutManager().scrollToPosition(startingPosition);
      getListAdapter().pulseHighlightItem(startingPosition);
    });
  }

  private void scrollToLastSeenPosition(final int lastSeenPosition) {
    if (lastSeenPosition > 0) {
      list.post(() -> getListLayoutManager().scrollToPositionWithOffset(lastSeenPosition, list.getHeight()));
    }
  }

  private boolean isAtBottom() {
    if (list.getChildCount() == 0) return true;

    int firstVisiblePosition = getListLayoutManager().findFirstVisibleItemPosition();

    if (isTypingIndicatorShowing()) {
      RecyclerView.ViewHolder item1 = list.findViewHolderForAdapterPosition(1);
      return firstVisiblePosition <= 1 && item1 != null && item1.itemView.getBottom() <= list.getHeight();
    }

    return firstVisiblePosition == 0 && list.getChildAt(0).getBottom() <= list.getHeight();
  }

  private boolean isTypingIndicatorShowing() {
    return getListAdapter().getHeaderView() == typingView;
  }

  public void onSearchQueryUpdated(@Nullable String query) {
    if (getListAdapter() != null) {
      getListAdapter().onSearchQueryUpdated(query);
    }
  }

  public void jumpToMessage(@NonNull RecipientId author, long timestamp, @Nullable Runnable onMessageNotFound) {
    SimpleTask.run(getLifecycle(), () -> {
      return DatabaseFactory.getMmsSmsDatabase(getContext())
                            .getMessagePositionInConversation(threadId, timestamp, author);
    }, p -> moveToMessagePosition(p + (isTypingIndicatorShowing() ? 1 : 0), onMessageNotFound));
  }

  private void moveToMessagePosition(int position, @Nullable Runnable onMessageNotFound) {
    Log.d(TAG, "Moving to message position: " + position + "  activeOffset: " + activeOffset + "  cursorCount: " + getListAdapter().getCursorCount());

    if (position >= activeOffset && position >= 0 && position < getListAdapter().getCursorCount()) {
      int offset = activeOffset > 0 ? activeOffset - 1 : 0;
      list.scrollToPosition(position - offset);
      getListAdapter().pulseHighlightItem(position - offset);
    } else if (position < 0) {
      Log.w(TAG, "Tried to navigate to message, but it wasn't found.");
      if (onMessageNotFound != null) {
        onMessageNotFound.run();
      }
    } else {
      Log.i(TAG, "Message was outside of the loaded range. Need to restart the loader.");

      firstLoad        = true;
      startingPosition = position;
      getLoaderManager().restartLoader(0, Bundle.EMPTY, ConversationFragment.this);
    }
  }

  private void maybeShowSwipeToReplyTooltip() {
    if (!TextSecurePreferences.hasSeenSwipeToReplyTooltip(requireContext())) {
      int text = getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_LTR ? R.string.ConversationFragment_you_can_swipe_to_the_right_reply
                                                                                                     : R.string.ConversationFragment_you_can_swipe_to_the_left_reply;
      TooltipPopup.forTarget(requireActivity().findViewById(R.id.menu_context_reply))
                  .setText(text)
                  .setTextColor(getResources().getColor(R.color.core_white))
                  .setBackgroundTint(getResources().getColor(R.color.core_ultramarine))
                  .show(TooltipPopup.POSITION_BELOW);

      TextSecurePreferences.setHasSeenSwipeToReplyTooltip(requireContext(), true);
    }
  }

  public interface ConversationFragmentListener {
    void setThreadId(long threadId);
    void handleReplyMessage(MessageRecord messageRecord);
    void onMessageActionToolbarOpened();
    void onForwardClicked();
    void onMessageRequest(@NonNull MessageRequestViewModel viewModel);
    void handleReaction(@NonNull View maskTarget,
                        @NonNull MessageRecord messageRecord,
                        @NonNull Toolbar.OnMenuItemClickListener toolbarListener,
                        @NonNull ConversationReactionOverlay.OnHideListener onHideListener);
    void onCursorChanged();
    void onListVerticalTranslationChanged(float translationY);
  }

  private class ConversationScrollListener extends OnScrollListener {

    private final Animation              scrollButtonInAnimation;
    private final Animation              scrollButtonOutAnimation;
    private final ConversationDateHeader conversationDateHeader;

    private boolean wasAtBottom           = true;
    private boolean wasAtZoomScrollHeight = false;
    private long    lastPositionId        = -1;

    ConversationScrollListener(@NonNull Context context) {
      this.scrollButtonInAnimation  = AnimationUtils.loadAnimation(context, R.anim.fade_scale_in);
      this.scrollButtonOutAnimation = AnimationUtils.loadAnimation(context, R.anim.fade_scale_out);
      this.conversationDateHeader   = new ConversationDateHeader(context, scrollDateHeader);

      this.scrollButtonInAnimation.setDuration(100);
      this.scrollButtonOutAnimation.setDuration(50);
    }

    @Override
    public void onScrolled(@NonNull final RecyclerView rv, final int dx, final int dy) {
      boolean currentlyAtBottom           = isAtBottom();
      boolean currentlyAtZoomScrollHeight = isAtZoomScrollHeight();
      int     positionId                  = getHeaderPositionId();

      if (currentlyAtBottom && !wasAtBottom) {
        ViewUtil.fadeOut(composeDivider, 50, View.INVISIBLE);
        ViewUtil.animateOut(scrollToBottomButton, scrollButtonOutAnimation, View.INVISIBLE);
      } else if (!currentlyAtBottom && wasAtBottom) {
        ViewUtil.fadeIn(composeDivider, 500);
      }

      if (currentlyAtZoomScrollHeight && !wasAtZoomScrollHeight) {
        ViewUtil.animateIn(scrollToBottomButton, scrollButtonInAnimation);
      }

      if (positionId != lastPositionId) {
        bindScrollHeader(conversationDateHeader, positionId);
      }

      wasAtBottom           = currentlyAtBottom;
      wasAtZoomScrollHeight = currentlyAtZoomScrollHeight;
      lastPositionId        = positionId;
    }

    @Override
    public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
      if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
        conversationDateHeader.show();
      } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
        conversationDateHeader.hide();
      }
    }

    private boolean isAtZoomScrollHeight() {
      return getListLayoutManager().findFirstCompletelyVisibleItemPosition() > 4;
    }

    private int getHeaderPositionId() {
      return getListLayoutManager().findLastVisibleItemPosition();
    }

    private void bindScrollHeader(HeaderViewHolder headerViewHolder, int positionId) {
      if (((ConversationAdapter)list.getAdapter()).getHeaderId(positionId) != -1) {
        ((ConversationAdapter) list.getAdapter()).onBindHeaderViewHolder(headerViewHolder, positionId);
      }
    }
  }

  private class ConversationFragmentItemClickListener implements ItemClickListener {

    @Override
    public void onItemClick(MessageRecord messageRecord) {
      if (actionMode != null) {
        ((ConversationAdapter) list.getAdapter()).toggleSelection(messageRecord);
        list.getAdapter().notifyDataSetChanged();

        if (getListAdapter().getSelectedItems().size() == 0) {
          actionMode.finish();
        } else {
          setCorrectMenuVisibility(actionMode.getMenu());
          actionMode.setTitle(String.valueOf(getListAdapter().getSelectedItems().size()));
        }
      }
    }

    @Override
    public void onItemLongClick(View maskTarget, MessageRecord messageRecord) {

      if (actionMode != null) return;

      if (messageRecord.isSecure()                            &&
          !messageRecord.isRemoteDelete()                     &&
          !messageRecord.isUpdate()                           &&
          !recipient.get().isBlocked()                        &&
          !messageRequestViewModel.shouldShowMessageRequest() &&
          ((ConversationAdapter) list.getAdapter()).getSelectedItems().isEmpty())
      {
        isReacting = true;
        list.setLayoutFrozen(true);
        listener.handleReaction(maskTarget, messageRecord, new ReactionsToolbarListener(messageRecord), () -> {
          isReacting = false;
          list.setLayoutFrozen(false);
        });
      } else {
        ((ConversationAdapter) list.getAdapter()).toggleSelection(messageRecord);
        list.getAdapter().notifyDataSetChanged();

        actionMode = ((AppCompatActivity)getActivity()).startSupportActionMode(actionModeCallback);
      }
    }

    @Override
    public void onQuoteClicked(MmsMessageRecord messageRecord) {
      if (messageRecord.getQuote() == null) {
        Log.w(TAG, "Received a 'quote clicked' event, but there's no quote...");
        return;
      }

      if (messageRecord.getQuote().isOriginalMissing()) {
        Log.i(TAG, "Clicked on a quote whose original message we never had.");
        Toast.makeText(getContext(), R.string.ConversationFragment_quoted_message_not_found, Toast.LENGTH_SHORT).show();
        return;
      }

      SimpleTask.run(getLifecycle(), () -> {
        return DatabaseFactory.getMmsSmsDatabase(getContext())
                              .getQuotedMessagePosition(threadId,
                                                        messageRecord.getQuote().getId(),
                                                        messageRecord.getQuote().getAuthor());
      }, p -> moveToMessagePosition(p + (isTypingIndicatorShowing() ? 1 : 0), () -> {
        Toast.makeText(getContext(), R.string.ConversationFragment_quoted_message_no_longer_available, Toast.LENGTH_SHORT).show();
      }));
    }

    @Override
    public void onLinkPreviewClicked(@NonNull LinkPreview linkPreview) {
      if (getContext() != null && getActivity() != null) {
        CommunicationActions.openBrowserLink(getActivity(), linkPreview.getUrl());
      }
    }

    @Override
    public void onMoreTextClicked(@NonNull RecipientId conversationRecipientId, long messageId, boolean isMms) {
      if (getContext() != null && getActivity() != null) {
        startActivity(LongMessageActivity.getIntent(getContext(), conversationRecipientId, messageId, isMms));
      }
    }

    @Override
    public void onStickerClicked(@NonNull StickerLocator sticker) {
      if (getContext() != null && getActivity() != null) {
        startActivity(StickerPackPreviewActivity.getIntent(sticker.getPackId(), sticker.getPackKey()));
      }
    }

    @Override
    public void onViewOnceMessageClicked(@NonNull MmsMessageRecord messageRecord) {
      if (!messageRecord.isViewOnce()) {
        throw new AssertionError("Non-revealable message clicked.");
      }

      if (!ViewOnceUtil.isViewable(messageRecord)) {
        int stringRes = messageRecord.isOutgoing() ? R.string.ConversationFragment_outgoing_view_once_media_files_are_automatically_removed
                                                   : R.string.ConversationFragment_you_already_viewed_this_message;
        Toast.makeText(requireContext(), stringRes, Toast.LENGTH_SHORT).show();
        return;
      }

      SimpleTask.run(getLifecycle(), () -> {
        Log.i(TAG, "Copying the view-once photo to temp storage and deleting underlying media.");

        try {
          Slide       thumbnailSlide = messageRecord.getSlideDeck().getThumbnailSlide();
          InputStream inputStream    = PartAuthority.getAttachmentStream(requireContext(), thumbnailSlide.getUri());
          Uri         tempUri        = BlobProvider.getInstance().forData(inputStream, thumbnailSlide.getFileSize())
                                                                 .withMimeType(thumbnailSlide.getContentType())
                                                                 .createForSingleSessionOnDisk(requireContext());

          DatabaseFactory.getAttachmentDatabase(requireContext()).deleteAttachmentFilesForViewOnceMessage(messageRecord.getId());

          ApplicationContext.getInstance(requireContext())
                            .getViewOnceMessageManager()
                            .scheduleIfNecessary();

          ApplicationDependencies.getJobManager().add(new MultiDeviceViewOnceOpenJob(new MessagingDatabase.SyncMessageId(messageRecord.getIndividualRecipient().getId(), messageRecord.getDateSent())));

          return tempUri;
        } catch (IOException e) {
          return null;
        }
      }, (uri) -> {
        if (uri != null) {
          startActivity(ViewOnceMessageActivity.getIntent(requireContext(), messageRecord.getId(), uri));
        } else {
          Log.w(TAG, "Failed to open view-once photo. Showing a toast and deleting the attachments for the message just in case.");
          Toast.makeText(requireContext(), R.string.ConversationFragment_failed_to_open_message, Toast.LENGTH_SHORT).show();
          SignalExecutors.BOUNDED.execute(() -> DatabaseFactory.getAttachmentDatabase(requireContext()).deleteAttachmentFilesForViewOnceMessage(messageRecord.getId()));
        }
      });
    }

    @Override
    public void onSharedContactDetailsClicked(@NonNull Contact contact, @NonNull View avatarTransitionView) {
      if (getContext() != null && getActivity() != null) {
        Bundle bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(getActivity(), avatarTransitionView, "avatar").toBundle();
        ActivityCompat.startActivity(getActivity(), SharedContactDetailsActivity.getIntent(getContext(), contact), bundle);
      }
    }

    @Override
    public void onAddToContactsClicked(@NonNull Contact contactWithAvatar) {
      if (getContext() != null) {
        new AsyncTask<Void, Void, Intent>() {
          @Override
          protected Intent doInBackground(Void... voids) {
            return ContactUtil.buildAddToContactsIntent(getContext(), contactWithAvatar);
          }

          @Override
          protected void onPostExecute(Intent intent) {
            startActivityForResult(intent, CODE_ADD_EDIT_CONTACT);
          }
        }.execute();
      }
    }

    @Override
    public void onMessageSharedContactClicked(@NonNull List<Recipient> choices) {
      if (getContext() == null) return;

      ContactUtil.selectRecipientThroughDialog(getContext(), choices, locale, recipient -> {
        CommunicationActions.startConversation(getContext(), recipient, null);
      });
    }

    @Override
    public void onInviteSharedContactClicked(@NonNull List<Recipient> choices) {
      if (getContext() == null) return;

      ContactUtil.selectRecipientThroughDialog(getContext(), choices, locale, recipient -> {
        CommunicationActions.composeSmsThroughDefaultApp(getContext(), recipient, getString(R.string.InviteActivity_lets_switch_to_signal, getString(R.string.install_url)));
      });
    }

    @Override
    public void onReactionClicked(long messageId, boolean isMms) {
      if (getContext() == null) return;

      ReactionsBottomSheetDialogFragment.create(messageId, isMms).show(requireFragmentManager(), null);
    }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == CODE_ADD_EDIT_CONTACT && getContext() != null) {
      ApplicationDependencies.getJobManager().add(new DirectoryRefreshJob(false));
    }
  }

  private void handleEnterMultiSelect(@NonNull MessageRecord messageRecord) {
    ((ConversationAdapter) list.getAdapter()).toggleSelection(messageRecord);
    list.getAdapter().notifyDataSetChanged();

    actionMode = ((AppCompatActivity)getActivity()).startSupportActionMode(actionModeCallback);
  }

  private class ReactionsToolbarListener implements Toolbar.OnMenuItemClickListener {

    private final MessageRecord messageRecord;

    private ReactionsToolbarListener(@NonNull MessageRecord messageRecord) {
      this.messageRecord = messageRecord;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
      switch (item.getItemId()) {
        case R.id.action_info:        handleDisplayDetails(messageRecord);                         return true;
        case R.id.action_delete:      handleDeleteMessages(Sets.newHashSet(messageRecord));        return true;
        case R.id.action_copy:        handleCopyMessage(Sets.newHashSet(messageRecord));           return true;
        case R.id.action_reply:       handleReplyMessage(messageRecord);                           return true;
        case R.id.action_multiselect: handleEnterMultiSelect(messageRecord);                       return true;
        case R.id.action_forward:     handleForwardMessage(messageRecord);                         return true;
        case R.id.action_download:    handleSaveAttachment((MediaMmsMessageRecord) messageRecord); return true;
        default:                                                                                   return false;
      }
    }
  }

  private class ActionModeCallback implements ActionMode.Callback {

    private int statusBarColor;

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
      MenuInflater inflater = mode.getMenuInflater();
      inflater.inflate(R.menu.conversation_context, menu);

      mode.setTitle("1");

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        Window window = getActivity().getWindow();
        statusBarColor = window.getStatusBarColor();
        window.setStatusBarColor(getResources().getColor(R.color.action_mode_status_bar));
      }

      setCorrectMenuVisibility(menu);
      AdaptiveActionsToolbar.adjustMenuActions(menu, 10, requireActivity().getWindow().getDecorView().getMeasuredWidth());
      listener.onMessageActionToolbarOpened();
      return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
      return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
      ((ConversationAdapter)list.getAdapter()).clearSelection();
      list.getAdapter().notifyDataSetChanged();

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        getActivity().getWindow().setStatusBarColor(statusBarColor);
      }

      actionMode = null;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
      if (actionMode == null) return false;

      switch(item.getItemId()) {
        case R.id.menu_context_copy:
          handleCopyMessage(getListAdapter().getSelectedItems());
          actionMode.finish();
          return true;
        case R.id.menu_context_delete_message:
          handleDeleteMessages(getListAdapter().getSelectedItems());
          actionMode.finish();
          return true;
        case R.id.menu_context_details:
          handleDisplayDetails(getSelectedMessageRecord());
          actionMode.finish();
          return true;
        case R.id.menu_context_forward:
          handleForwardMessage(getSelectedMessageRecord());
          actionMode.finish();
          return true;
        case R.id.menu_context_resend:
          handleResendMessage(getSelectedMessageRecord());
          actionMode.finish();
          return true;
        case R.id.menu_context_save_attachment:
          handleSaveAttachment((MediaMmsMessageRecord)getSelectedMessageRecord());
          actionMode.finish();
          return true;
        case R.id.menu_context_reply:
          maybeShowSwipeToReplyTooltip();
          handleReplyMessage(getSelectedMessageRecord());
          actionMode.finish();
          return true;
      }

      return false;
    }
  }

  private static class ConversationDateHeader extends HeaderViewHolder {

    private final Animation animateIn;
    private final Animation animateOut;

    private boolean pendingHide = false;

    private ConversationDateHeader(Context context, TextView textView) {
      super(textView);
      this.animateIn  = AnimationUtils.loadAnimation(context, R.anim.slide_from_top);
      this.animateOut = AnimationUtils.loadAnimation(context, R.anim.slide_to_top);

      this.animateIn.setDuration(100);
      this.animateOut.setDuration(100);
    }

    public void show() {
      if (textView.getText() == null || textView.getText().length() == 0) {
        return;
      }

      if (pendingHide) {
        pendingHide = false;
      } else {
        ViewUtil.animateIn(textView, animateIn);
      }
    }

    public void hide() {
      pendingHide = true;

      textView.postDelayed(new Runnable() {
        @Override
        public void run() {
          if (pendingHide) {
            pendingHide = false;
            ViewUtil.animateOut(textView, animateOut, View.GONE);
          }
        }
      }, 400);
    }
  }
}
