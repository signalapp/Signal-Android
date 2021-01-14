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

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
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
import android.widget.FrameLayout;
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
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.OnScrollListener;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import org.signal.core.util.StreamUtil;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.ApplicationPreferencesActivity;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.VerifyIdentityActivity;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.components.ConversationScrollToView;
import org.thoughtcrime.securesms.components.ConversationTypingView;
import org.thoughtcrime.securesms.components.TooltipPopup;
import org.thoughtcrime.securesms.components.TypingStatusRepository;
import org.thoughtcrime.securesms.components.recyclerview.SmoothScrollingLinearLayoutManager;
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaController;
import org.thoughtcrime.securesms.components.voice.VoiceNotePlaybackState;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.contactshare.ContactUtil;
import org.thoughtcrime.securesms.contactshare.SharedContactDetailsActivity;
import org.thoughtcrime.securesms.conversation.ConversationAdapter.ItemClickListener;
import org.thoughtcrime.securesms.conversation.ConversationAdapter.StickyHeaderViewHolder;
import org.thoughtcrime.securesms.conversation.ConversationMessage.ConversationMessageFactory;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.MessageDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupMigrationMembershipChange;
import org.thoughtcrime.securesms.groups.ui.invitesandrequests.invite.GroupLinkInviteFriendsBottomSheetDialogFragment;
import org.thoughtcrime.securesms.groups.ui.migration.GroupsV1MigrationInfoBottomSheetDialogFragment;
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob;
import org.thoughtcrime.securesms.jobs.MultiDeviceViewOnceOpenJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.longmessage.LongMessageActivity;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.messagedetails.MessageDetailsActivity;
import org.thoughtcrime.securesms.messagerequests.MessageRequestViewModel;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.reactions.ReactionsBottomSheetDialogFragment;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment;
import org.thoughtcrime.securesms.revealable.ViewOnceMessageActivity;
import org.thoughtcrime.securesms.revealable.ViewOnceUtil;
import org.thoughtcrime.securesms.sharing.ShareIntents;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.stickers.StickerLocator;
import org.thoughtcrime.securesms.stickers.StickerPackPreviewActivity;
import org.thoughtcrime.securesms.util.CachedInflater;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.HtmlUtil;
import org.thoughtcrime.securesms.util.RemoteDeleteUtil;
import org.thoughtcrime.securesms.util.SaveAttachmentTask;
import org.thoughtcrime.securesms.util.SetUtil;
import org.thoughtcrime.securesms.util.SnapToTopDataObserver;
import org.thoughtcrime.securesms.util.StickyHeaderDecoration;
import org.thoughtcrime.securesms.util.StorageUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ThemeUtil;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.WindowUtil;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.util.task.ProgressDialogAsyncTask;
import org.thoughtcrime.securesms.util.views.AdaptiveActionsToolbar;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@SuppressLint("StaticFieldLeak")
public class ConversationFragment extends LoggingFragment {
  private static final String TAG = ConversationFragment.class.getSimpleName();

  private static final int SCROLL_ANIMATION_THRESHOLD = 50;
  private static final int CODE_ADD_EDIT_CONTACT      = 77;

  private final ActionModeCallback actionModeCallback     = new ActionModeCallback();
  private final ItemClickListener  selectionClickListener = new ConversationFragmentItemClickListener();

  private ConversationFragmentListener listener;

  private LiveRecipient               recipient;
  private long                        threadId;
  private boolean                     isReacting;
  private ActionMode                  actionMode;
  private Locale                      locale;
  private RecyclerView                list;
  private RecyclerView.ItemDecoration lastSeenDecoration;
  private RecyclerView.ItemDecoration stickyHeaderDecoration;
  private ViewSwitcher                topLoadMoreView;
  private ViewSwitcher                bottomLoadMoreView;
  private ConversationTypingView      typingView;
  private View                        composeDivider;
  private ConversationScrollToView    scrollToBottomButton;
  private ConversationScrollToView    scrollToMentionButton;
  private TextView                    scrollDateHeader;
  private ConversationBannerView      conversationBanner;
  private ConversationBannerView      emptyConversationBanner;
  private MessageRequestViewModel     messageRequestViewModel;
  private MessageCountsViewModel      messageCountsViewModel;
  private ConversationViewModel       conversationViewModel;
  private SnapToTopDataObserver       snapToTopDataObserver;
  private MarkReadHelper              markReadHelper;
  private Animation                   scrollButtonInAnimation;
  private Animation                   mentionButtonInAnimation;
  private Animation                   scrollButtonOutAnimation;
  private Animation                   mentionButtonOutAnimation;
  private OnScrollListener            conversationScrollListener;
  private int                         pulsePosition = -1;
  private VoiceNoteMediaController    voiceNoteMediaController;

  public static void prepare(@NonNull Context context) {
    FrameLayout parent = new FrameLayout(context);
    parent.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

    CachedInflater.from(context).cacheUntilLimit(R.layout.conversation_item_received_text_only, parent, 15);
    CachedInflater.from(context).cacheUntilLimit(R.layout.conversation_item_sent_text_only, parent, 15);
    CachedInflater.from(context).cacheUntilLimit(R.layout.conversation_item_received_multimedia, parent, 10);
    CachedInflater.from(context).cacheUntilLimit(R.layout.conversation_item_sent_multimedia, parent, 10);
    CachedInflater.from(context).cacheUntilLimit(R.layout.conversation_item_update, parent, 5);
    CachedInflater.from(context).cacheUntilLimit(R.layout.cursor_adapter_header_footer_view, parent, 2);
  }

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    this.locale = (Locale) getArguments().getSerializable(PassphraseRequiredActivity.LOCALE_EXTRA);
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    final View view = inflater.inflate(R.layout.conversation_fragment, container, false);
    list                    = view.findViewById(android.R.id.list);
    composeDivider          = view.findViewById(R.id.compose_divider);

    scrollToBottomButton    = view.findViewById(R.id.scroll_to_bottom);
    scrollToMentionButton   = view.findViewById(R.id.scroll_to_mention);
    scrollDateHeader        = view.findViewById(R.id.scroll_date_header);
    emptyConversationBanner = view.findViewById(R.id.empty_conversation_banner);

    final LinearLayoutManager layoutManager = new SmoothScrollingLinearLayoutManager(getActivity(), true);
    list.setHasFixedSize(false);
    list.setLayoutManager(layoutManager);
    list.setItemAnimator(null);

    snapToTopDataObserver = new ConversationSnapToTopDataObserver(list, new ConversationScrollRequestValidator());
    conversationBanner    = (ConversationBannerView) inflater.inflate(R.layout.conversation_item_banner, container, false);
    topLoadMoreView       = (ViewSwitcher) inflater.inflate(R.layout.load_more_header, container, false);
    bottomLoadMoreView    = (ViewSwitcher) inflater.inflate(R.layout.load_more_header, container, false);

    initializeLoadMoreView(topLoadMoreView);
    initializeLoadMoreView(bottomLoadMoreView);

    typingView = (ConversationTypingView) inflater.inflate(R.layout.conversation_typing_view, container, false);

    new ConversationItemSwipeCallback(
            conversationMessage -> actionMode == null &&
                                   MenuState.canReplyToMessage(recipient.get(),
                                                               MenuState.isActionMessage(conversationMessage.getMessageRecord()),
                                                               conversationMessage.getMessageRecord(),
                                                               messageRequestViewModel.shouldShowMessageRequest()),
            this::handleReplyMessage
    ).attachToRecyclerView(list);

    setupListLayoutListeners();

    this.messageCountsViewModel = ViewModelProviders.of(requireActivity()).get(MessageCountsViewModel.class);
    this.conversationViewModel  = ViewModelProviders.of(requireActivity(), new ConversationViewModel.Factory()).get(ConversationViewModel.class);

    conversationViewModel.getMessages().observe(this, messages -> {
      ConversationAdapter adapter = getListAdapter();
      if (adapter != null) {
        getListAdapter().submitList(messages);
      }
    });

    conversationViewModel.getConversationMetadata().observe(this, this::presentConversationMetadata);

    conversationViewModel.getShowMentionsButton().observe(this, shouldShow -> {
      if (shouldShow) {
        ViewUtil.animateIn(scrollToMentionButton, mentionButtonInAnimation);
      } else {
        ViewUtil.animateOut(scrollToMentionButton, mentionButtonOutAnimation, View.INVISIBLE);
      }
    });

    conversationViewModel.getShowScrollToBottom().observe(this, shouldShow -> {
      if (shouldShow) {
        ViewUtil.animateIn(scrollToBottomButton, scrollButtonInAnimation);
      } else {
        ViewUtil.animateOut(scrollToBottomButton, scrollButtonOutAnimation, View.INVISIBLE);
      }
    });

    scrollToBottomButton.setOnClickListener(v -> scrollToBottom());
    scrollToMentionButton.setOnClickListener(v -> scrollToNextMention());

    return view;
  }

  private void setupListLayoutListeners() {
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

    initializeScrollButtonAnimations();
    initializeResources();
    initializeMessageRequestViewModel();
    initializeListAdapter();
    voiceNoteMediaController = new VoiceNoteMediaController((AppCompatActivity) requireActivity());
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
  public void onPause() {
    super.onPause();
    int lastVisiblePosition  = getListLayoutManager().findLastVisibleItemPosition();
    int firstVisiblePosition = getListLayoutManager().findFirstCompletelyVisibleItemPosition();

    final long lastVisibleMessageTimestamp;
    if (firstVisiblePosition > 0 && lastVisiblePosition != RecyclerView.NO_POSITION) {
      ConversationMessage message = getListAdapter().getLastVisibleConversationMessage(lastVisiblePosition);

      lastVisibleMessageTimestamp = message != null ? message.getMessageRecord().getDateReceived() : 0;
    } else {
      lastVisibleMessageTimestamp = 0;
    }
    SignalExecutors.BOUNDED.submit(() -> DatabaseFactory.getThreadDatabase(requireContext()).setLastScrolled(threadId, lastVisibleMessageTimestamp));
  }

  @Override
  public void onStop() {
    super.onStop();
    ApplicationDependencies.getTypingStatusRepository().getTypists(threadId).removeObservers(this);
  }

  public void onNewIntent() {
    if (actionMode != null) {
      actionMode.finish();
    }

    long oldThreadId = threadId;

    initializeResources();
    messageRequestViewModel.setConversationInfo(recipient.getId(), threadId);

    int startingPosition = getStartPosition();
    if (startingPosition != -1 && oldThreadId == threadId) {
      list.post(() -> moveToPosition(startingPosition, () -> Log.w(TAG, "Could not scroll to requested message.")));
    } else {
      initializeListAdapter();
    }
  }

  public void moveToLastSeen() {
    if (conversationViewModel.getLastSeenPosition() <= 0) {
      Log.i(TAG, "No need to move to last seen.");
      return;
    }

    if (list == null || getListAdapter() == null) {
      Log.w(TAG, "Tried to move to last seen position, but we hadn't initialized the view yet.");
      return;
    }

    int position = getListAdapter().getAdapterPositionForMessagePosition(conversationViewModel.getLastSeenPosition());
    snapToTopDataObserver.requestScrollPosition(position);
  }

  private int getStartPosition() {
    return conversationViewModel.getArgs().getStartingPosition();
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

      String title = isSelf ? context.getString(R.string.note_to_self) : recipient.getDisplayNameOrUsername(context);
      conversationBanner.setTitle(title);

      if (recipient.isGroup()) {
        if (pendingMemberCount > 0) {
          conversationBanner.setSubtitle(context.getResources()
                                                .getQuantityString(R.plurals.MessageRequestProfileView_members_and_invited, memberCount,
                                                                   memberCount, pendingMemberCount));
        } else if (memberCount > 0) {
          conversationBanner.setSubtitle(context.getResources().getQuantityString(R.plurals.MessageRequestProfileView_members, memberCount,
                                                                                  memberCount));
        } else {
          conversationBanner.setSubtitle(null);
        }
      } else if (isSelf) {
        conversationBanner.setSubtitle(context.getString(R.string.ConversationFragment__you_can_add_notes_for_yourself_in_this_conversation));
      } else {
        String subtitle = recipient.getE164().transform(PhoneNumberFormatter::prettyPrint).orNull();

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
                                          context.getResources().getQuantityString(R.plurals.MessageRequestProfileView_member_of_d_additional_groups, others, others));
      }

      conversationBanner.setDescription(HtmlCompat.fromHtml(description, 0));
      conversationBanner.showDescription();
    }
  }

  private void initializeResources() {
    long oldThreadId = threadId;

    int startingPosition  = getStartPosition();

    this.recipient      = Recipient.live(conversationViewModel.getArgs().getRecipientId());
    this.threadId       = conversationViewModel.getArgs().getThreadId();
    this.markReadHelper = new MarkReadHelper(threadId, requireContext());

    conversationViewModel.onConversationDataAvailable(threadId, startingPosition);
    messageCountsViewModel.setThreadId(threadId);

    messageCountsViewModel.getUnreadMessagesCount().observe(getViewLifecycleOwner(), scrollToBottomButton::setUnreadCount);
    messageCountsViewModel.getUnreadMentionsCount().observe(getViewLifecycleOwner(), count -> {
      scrollToMentionButton.setUnreadCount(count);
      conversationViewModel.setHasUnreadMentions(count > 0);
    });

    conversationScrollListener = new ConversationScrollListener(requireContext());
    list.addOnScrollListener(conversationScrollListener);

    if (oldThreadId != threadId) {
      ApplicationDependencies.getTypingStatusRepository().getTypists(oldThreadId).removeObservers(this);
    }
  }

  private void initializeListAdapter() {
    if (this.recipient != null && this.threadId != -1) {
      Log.d(TAG, "Initializing adapter for " + recipient.getId());
      ConversationAdapter adapter = new ConversationAdapter(this, GlideApp.with(this), locale, selectionClickListener, this.recipient.get());
      adapter.setPagingController(conversationViewModel.getPagingController());
      list.setAdapter(adapter);
      setStickyHeaderDecoration(adapter);
      ConversationAdapter.initializePool(list.getRecycledViewPool());

      adapter.registerAdapterDataObserver(snapToTopDataObserver);

      setLastSeen(conversationViewModel.getLastSeen());

      emptyConversationBanner.setVisibility(View.GONE);
    } else if (threadId == -1) {
      emptyConversationBanner.setVisibility(View.VISIBLE);
    }
  }

  private void initializeLoadMoreView(ViewSwitcher loadMoreView) {
    loadMoreView.setOnClickListener(v -> {
      loadMoreView.showNext();
      loadMoreView.setOnClickListener(null);
    });
  }

  private void initializeTypingObserver() {
    if (!TextSecurePreferences.isTypingIndicatorsEnabled(requireContext())) {
      return;
    }

    LiveData<TypingStatusRepository.TypingState> typists = ApplicationDependencies.getTypingStatusRepository().getTypists(threadId);

    typists.removeObservers(this);
    typists.observe(this, typingState ->  {
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
        } else {
          if (isTypingIndicatorShowing()) {
            adapter.setHeaderView(typingView);
          } else {
            adapter.setHeaderView(typingView);
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
            list.post(() -> list.setVerticalScrollBarEnabled(true));
          }, 200);
        } else if (!replacedByIncomingMessage) {
          adapter.setHeaderView(null);
        } else {
          adapter.setHeaderView(null);
        }
      }
    });
  }

  private void setCorrectMenuVisibility(@NonNull Menu menu) {
    Set<ConversationMessage> messages = getListAdapter().getSelectedItems();

    if (actionMode != null && messages.size() == 0) {
      actionMode.finish();
      return;
    }

    MenuState menuState = MenuState.getMenuState(recipient.get(), Stream.of(messages).map(ConversationMessage::getMessageRecord).collect(Collectors.toSet()), messageRequestViewModel.shouldShowMessageRequest());

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

  private ConversationMessage getSelectedConversationMessage() {
    Set<ConversationMessage> messageRecords = getListAdapter().getSelectedItems();

    if (messageRecords.size() == 1) return messageRecords.iterator().next();
    else                            throw new AssertionError();
  }

  public void reload(Recipient recipient, long threadId) {
    this.recipient = recipient.live();

    if (this.threadId != threadId) {
      this.threadId = threadId;
      messageRequestViewModel.setConversationInfo(recipient.getId(), threadId);

      snapToTopDataObserver.requestScrollPosition(0);
      conversationViewModel.onConversationDataAvailable(threadId, -1);
      messageCountsViewModel.setThreadId(threadId);
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

  public void setStickyHeaderDecoration(@NonNull ConversationAdapter adapter) {
    if (stickyHeaderDecoration != null) {
      list.removeItemDecoration(stickyHeaderDecoration);
    }

    stickyHeaderDecoration = new StickyHeaderDecoration(adapter, false, false);
    list.addItemDecoration(stickyHeaderDecoration);
  }

  public void setLastSeen(long lastSeen) {
    if (lastSeenDecoration != null) {
      list.removeItemDecoration(lastSeenDecoration);
    }

    lastSeenDecoration = new LastSeenHeader(getListAdapter(), lastSeen);
    list.addItemDecoration(lastSeenDecoration);
  }

  private void handleCopyMessage(final Set<ConversationMessage> conversationMessages) {
    List<ConversationMessage> messageList = new ArrayList<>(conversationMessages);
    Collections.sort(messageList, (lhs, rhs) -> Long.compare(lhs.getMessageRecord().getDateReceived(), rhs.getMessageRecord().getDateReceived()));

    SpannableStringBuilder bodyBuilder = new SpannableStringBuilder();
    ClipboardManager       clipboard   = (ClipboardManager) requireActivity().getSystemService(Context.CLIPBOARD_SERVICE);

    for (ConversationMessage message : messageList) {
      CharSequence body = message.getDisplayBody(requireContext());
      if (!TextUtils.isEmpty(body)) {
        if (bodyBuilder.length() > 0) {
          bodyBuilder.append('\n');
        }
        bodyBuilder.append(body);
      }
    }

    if (!TextUtils.isEmpty(bodyBuilder)) {
      clipboard.setPrimaryClip(ClipData.newPlainText(null, bodyBuilder));
    }
  }

  private void handleDeleteMessages(final Set<ConversationMessage> conversationMessages) {
    Set<MessageRecord> messageRecords = Stream.of(conversationMessages).map(ConversationMessage::getMessageRecord).collect(Collectors.toSet());
    buildRemoteDeleteConfirmationDialog(messageRecords).show();
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
              threadDeleted = DatabaseFactory.getMmsDatabase(context).deleteMessage(messageRecord.getId());
            } else {
              threadDeleted = DatabaseFactory.getSmsDatabase(context).deleteMessage(messageRecord.getId());
            }

            if (threadDeleted) {
              threadId = -1;
              conversationViewModel.clearThreadId();
              messageCountsViewModel.clearThreadId();
              listener.setThreadId(threadId);
            }
          }

          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    });

    if (RemoteDeleteUtil.isValidSend(messageRecords, System.currentTimeMillis())) {
      builder.setNeutralButton(R.string.ConversationFragment_delete_for_everyone, (dialog, which) -> handleDeleteForEveryone(messageRecords));
    }

    builder.setNegativeButton(android.R.string.cancel, null);
    return builder;
  }

  private void handleDeleteForEveryone(Set<MessageRecord> messageRecords) {
    Runnable deleteForEveryone = () -> {
      SignalExecutors.BOUNDED.execute(() -> {
        for (MessageRecord message : messageRecords) {
          MessageSender.sendRemoteDelete(ApplicationDependencies.getApplication(), message.getId(), message.isMms());
        }
      });
    };

    if (SignalStore.uiHints().hasConfirmedDeleteForEveryoneOnce()) {
      deleteForEveryone.run();
    } else {
      new AlertDialog.Builder(requireActivity())
                     .setMessage(R.string.ConversationFragment_this_message_will_be_deleted_for_everyone_in_the_conversation)
                     .setPositiveButton(R.string.ConversationFragment_delete_for_everyone, (dialog, which) -> {
                       SignalStore.uiHints().markHasConfirmedDeleteForEveryoneOnce();
                       deleteForEveryone.run();
                     })
                     .setNegativeButton(android.R.string.cancel, null)
                     .show();
    }
  }

  private void handleDisplayDetails(ConversationMessage message) {
    startActivity(MessageDetailsActivity.getIntentForMessageDetails(requireContext(), message.getMessageRecord(), recipient.getId(), threadId));
  }

  private void handleForwardMessage(ConversationMessage conversationMessage) {
    if (conversationMessage.getMessageRecord().isViewOnce()) {
      throw new AssertionError("Cannot forward a view-once message.");
    }

    listener.onForwardClicked();

    SimpleTask.run(getLifecycle(), () -> {
      ShareIntents.Builder shareIntentBuilder = new ShareIntents.Builder(requireActivity());
      shareIntentBuilder.setText(conversationMessage.getDisplayBody(requireContext()));

      if (conversationMessage.getMessageRecord().isMms()) {
        MmsMessageRecord mediaMessage = (MmsMessageRecord) conversationMessage.getMessageRecord();
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
            Uri uri = attachment.getUri();

            if (uri != null) {
              mediaList.add(new Media(uri,
                                      attachment.getContentType(),
                                      System.currentTimeMillis(),
                                      attachment.getWidth(),
                                      attachment.getHeight(),
                                      attachment.getSize(),
                                      0,
                                      attachment.isBorderless(),
                                      Optional.absent(),
                                      Optional.fromNullable(attachment.getCaption()),
                                      Optional.absent()));
            }
          }

          if (!mediaList.isEmpty()) {
            shareIntentBuilder.setMedia(mediaList);
          }
        } else if (mediaMessage.containsMediaSlide()) {
          Slide slide = mediaMessage.getSlideDeck().getSlides().get(0);
          shareIntentBuilder.setSlide(slide);
        }

        if (mediaMessage.getSlideDeck().getTextSlide() != null && mediaMessage.getSlideDeck().getTextSlide().getUri() != null) {
          try (InputStream stream = PartAuthority.getAttachmentStream(requireContext(), mediaMessage.getSlideDeck().getTextSlide().getUri())) {
            String fullBody = StreamUtil.readFullyAsString(stream);
            shareIntentBuilder.setText(fullBody);
          } catch (IOException e) {
            Log.w(TAG, "Failed to read long message text when forwarding.");
          }
        }
      }

      return shareIntentBuilder.build();
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

  private void handleReplyMessage(final ConversationMessage message) {
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

    SaveAttachmentTask.showWarningDialog(getActivity(), (dialog, which) -> {
      if (StorageUtil.canWriteToMediaStore()) {
        performSave(message);
        return;
      }

      Permissions.with(this)
                 .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                 .ifNecessary()
                 .withPermanentDenialDialog(getString(R.string.MediaPreviewActivity_signal_needs_the_storage_permission_in_order_to_write_to_external_storage_but_it_has_been_permanently_denied))
                 .onAnyDenied(() -> Toast.makeText(requireContext(), R.string.MediaPreviewActivity_unable_to_write_to_external_storage_without_permission, Toast.LENGTH_LONG).show())
                 .onAllGranted(() -> performSave(message))
                 .execute();
    });
  }

  private void performSave(final MediaMmsMessageRecord message) {
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

  private void clearHeaderIfNotTyping(ConversationAdapter adapter) {
    if (adapter.getHeaderView() != typingView) {
      adapter.setHeaderView(null);
    }
  }

  public long stageOutgoingMessage(OutgoingMediaMessage message) {
    MessageRecord messageRecord = MmsDatabase.readerFor(message, threadId).getCurrent();

    if (getListAdapter() != null) {
      clearHeaderIfNotTyping(getListAdapter());
      setLastSeen(0);
      getListAdapter().addFastRecord(ConversationMessageFactory.createWithResolvedData(messageRecord, messageRecord.getDisplayBody(requireContext()), message.getMentions()));
      list.post(() -> list.scrollToPosition(0));
    }

    return messageRecord.getId();
  }

  public long stageOutgoingMessage(OutgoingTextMessage message) {
    MessageRecord messageRecord = SmsDatabase.readerFor(message, threadId).getCurrent();

    if (getListAdapter() != null) {
      clearHeaderIfNotTyping(getListAdapter());
      setLastSeen(0);
      getListAdapter().addFastRecord(ConversationMessageFactory.createWithResolvedData(messageRecord));
      list.post(() -> list.scrollToPosition(0));
    }

    return messageRecord.getId();
  }

  public void releaseOutgoingMessage(long id) {
    if (getListAdapter() != null) {
      getListAdapter().releaseFastRecord(id);
    }
  }

  private void presentConversationMetadata(@NonNull ConversationData conversation) {
    ConversationAdapter adapter = getListAdapter();
    if (adapter == null) {
      return;
    }

    adapter.setFooterView(conversationBanner);

    Runnable afterScroll = () -> {
      if (!conversation.isMessageRequestAccepted()) {
        snapToTopDataObserver.requestScrollPosition(adapter.getItemCount() - 1);
      }

      setLastSeen(conversation.getLastSeen());

      clearHeaderIfNotTyping(adapter);

      listener.onCursorChanged();

      conversationScrollListener.onScrolled(list, 0, 0);
    };

    int lastSeenPosition     = adapter.getAdapterPositionForMessagePosition(conversation.getLastSeenPosition());
    int lastScrolledPosition = adapter.getAdapterPositionForMessagePosition(conversation.getLastScrolledPosition());

    if (conversation.getThreadSize() == 0) {
      afterScroll.run();
    } else if (conversation.shouldJumpToMessage()) {
      snapToTopDataObserver.buildScrollPosition(conversation.getJumpToPosition())
                           .withOnScrollRequestComplete(() -> {
                             afterScroll.run();
                             getListAdapter().pulseAtPosition(conversation.getJumpToPosition());
                           })
                           .submit();
    } else if (conversation.isMessageRequestAccepted()) {
      snapToTopDataObserver.buildScrollPosition(conversation.shouldScrollToLastSeen() ? lastSeenPosition : lastScrolledPosition)
                           .withOnPerformScroll((layoutManager, position) -> layoutManager.scrollToPositionWithOffset(position, list.getHeight()))
                           .withOnScrollRequestComplete(afterScroll)
                           .submit();
    } else {
      snapToTopDataObserver.buildScrollPosition(adapter.getItemCount() - 1)
                           .withOnScrollRequestComplete(afterScroll)
                           .submit();
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

  @SuppressWarnings("CodeBlock2Expr")
  public void jumpToMessage(@NonNull RecipientId author, long timestamp, @Nullable Runnable onMessageNotFound) {
    SimpleTask.run(getLifecycle(), () -> {
      return DatabaseFactory.getMmsSmsDatabase(getContext())
                            .getMessagePositionInConversation(threadId, timestamp, author);
    }, p -> moveToPosition(p + (isTypingIndicatorShowing() ? 1 : 0), onMessageNotFound));
  }

  private void moveToPosition(int position, @Nullable Runnable onMessageNotFound) {
    Log.d(TAG, "moveToPosition(" + position + ")");
    conversationViewModel.getPagingController().onDataNeededAroundIndex(position);
    snapToTopDataObserver.buildScrollPosition(position)
                         .withOnPerformScroll(((layoutManager, p) ->
                             list.post(() -> {
                               if (Math.abs(layoutManager.findFirstVisibleItemPosition() - p) < SCROLL_ANIMATION_THRESHOLD) {
                                 View child = layoutManager.findViewByPosition(position);

                                 if (child != null && layoutManager.isViewPartiallyVisible(child, true, false)) {
                                   getListAdapter().pulseAtPosition(position);
                                 } else {
                                   pulsePosition = position;
                                 }

                                 list.smoothScrollToPosition(p);
                               } else {
                                 layoutManager.scrollToPosition(p);
                                 getListAdapter().pulseAtPosition(position);
                               }
                             })
                         ))
                         .withOnInvalidPosition(() -> {
                           if (onMessageNotFound != null) {
                             onMessageNotFound.run();
                           }
                           Log.w(TAG, "[moveToMentionPosition] Tried to navigate to mention, but it wasn't found.");
                         })
                         .submit();
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

  private void initializeScrollButtonAnimations() {
    scrollButtonInAnimation  = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_scale_in);
    scrollButtonOutAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_scale_out);

    mentionButtonInAnimation  = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_scale_in);
    mentionButtonOutAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.fade_scale_out);

    scrollButtonInAnimation.setDuration(100);
    scrollButtonOutAnimation.setDuration(50);

    mentionButtonInAnimation.setDuration(100);
    mentionButtonOutAnimation.setDuration(50);
  }

  private void scrollToNextMention() {
    SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
      MessageDatabase mmsDatabase = DatabaseFactory.getMmsDatabase(ApplicationDependencies.getApplication());
      return mmsDatabase.getOldestUnreadMentionDetails(threadId);
    }, (pair) -> {
      if (pair != null) {
        jumpToMessage(pair.first(), pair.second(), () -> {});
      }
    });
  }

  private void postMarkAsReadRequest() {
    if (getListAdapter().hasNoConversationMessages()) {
      return;
    }

    int position = getListLayoutManager().findFirstVisibleItemPosition();
    if (position == getListAdapter().getItemCount() - 1) {
      return;
    }

    if (position >= (isTypingIndicatorShowing() ? 1 : 0)) {
      ConversationMessage item = getListAdapter().getItem(position);
      if (item != null) {
        long timestamp = item.getMessageRecord()
                             .getDateReceived();

        markReadHelper.onViewsRevealed(timestamp);
      }
    }
  }

  public interface ConversationFragmentListener {
    void setThreadId(long threadId);
    void handleReplyMessage(ConversationMessage conversationMessage);
    void onMessageActionToolbarOpened();
    void onForwardClicked();
    void onMessageRequest(@NonNull MessageRequestViewModel viewModel);
    void handleReaction(@NonNull View maskTarget,
                        @NonNull MessageRecord messageRecord,
                        @NonNull Toolbar.OnMenuItemClickListener toolbarListener,
                        @NonNull ConversationReactionOverlay.OnHideListener onHideListener);
    void onCursorChanged();
    void onListVerticalTranslationChanged(float translationY);
    void onMessageWithErrorClicked(@NonNull MessageRecord messageRecord);
    void handleReactionDetails(@NonNull View maskTarget);
  }

  private class ConversationScrollListener extends OnScrollListener {

    private final ConversationDateHeader conversationDateHeader;

    private boolean wasAtBottom           = true;
    private long    lastPositionId        = -1;

    ConversationScrollListener(@NonNull Context context) {
      this.conversationDateHeader   = new ConversationDateHeader(context, scrollDateHeader);

    }

    @Override
    public void onScrolled(@NonNull final RecyclerView rv, final int dx, final int dy) {
      boolean currentlyAtBottom           = !rv.canScrollVertically(1);
      boolean currentlyAtZoomScrollHeight = isAtZoomScrollHeight();
      int     positionId                  = getHeaderPositionId();

      if (currentlyAtBottom && !wasAtBottom) {
        ViewUtil.fadeOut(composeDivider, 50, View.INVISIBLE);
      } else if (!currentlyAtBottom && wasAtBottom) {
        ViewUtil.fadeIn(composeDivider, 500);
      }

      if (currentlyAtBottom) {
        conversationViewModel.setShowScrollButtons(false);
      } else if (currentlyAtZoomScrollHeight) {
        conversationViewModel.setShowScrollButtons(true);
      }

      if (positionId != lastPositionId) {
        bindScrollHeader(conversationDateHeader, positionId);
      }

      wasAtBottom    = currentlyAtBottom;
      lastPositionId = positionId;

      postMarkAsReadRequest();
    }

    @Override
    public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
      if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
        conversationDateHeader.show();
      } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
        conversationDateHeader.hide();

        if (pulsePosition != -1) {
          getListAdapter().pulseAtPosition(pulsePosition);
          pulsePosition = -1;
        }
      }
    }

    private boolean isAtZoomScrollHeight() {
      return getListLayoutManager().findFirstCompletelyVisibleItemPosition() > 4;
    }

    private int getHeaderPositionId() {
      return getListLayoutManager().findLastVisibleItemPosition();
    }

    private void bindScrollHeader(StickyHeaderViewHolder headerViewHolder, int positionId) {
      if (((ConversationAdapter)list.getAdapter()).getHeaderId(positionId) != -1) {
        ((ConversationAdapter) list.getAdapter()).onBindHeaderViewHolder(headerViewHolder, positionId);
      }
    }
  }

  private class ConversationFragmentItemClickListener implements ItemClickListener {

    @Override
    public void onItemClick(ConversationMessage conversationMessage) {
      if (actionMode != null) {
        ((ConversationAdapter) list.getAdapter()).toggleSelection(conversationMessage);
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
    public void onItemLongClick(View maskTarget, ConversationMessage conversationMessage) {

      if (actionMode != null) return;

      MessageRecord messageRecord = conversationMessage.getMessageRecord();

      if (messageRecord.isSecure()                                        &&
          !messageRecord.isRemoteDelete()                                 &&
          !messageRecord.isUpdate()                                       &&
          !recipient.get().isBlocked()                                    &&
          !messageRequestViewModel.shouldShowMessageRequest()             &&
          (!recipient.get().isGroup() || recipient.get().isActiveGroup()) &&
          ((ConversationAdapter) list.getAdapter()).getSelectedItems().isEmpty())
      {
        isReacting = true;
        list.setLayoutFrozen(true);
        listener.handleReaction(maskTarget, messageRecord, new ReactionsToolbarListener(conversationMessage), () -> {
          isReacting = false;
          list.setLayoutFrozen(false);
        });
      } else {
        ((ConversationAdapter) list.getAdapter()).toggleSelection(conversationMessage);
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
      }, p -> moveToPosition(p + (isTypingIndicatorShowing() ? 1 : 0), () -> {
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

          ApplicationDependencies.getJobManager().add(new MultiDeviceViewOnceOpenJob(new MessageDatabase.SyncMessageId(messageRecord.getIndividualRecipient().getId(), messageRecord.getDateSent())));

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
    public void onReactionClicked(@NonNull View reactionTarget, long messageId, boolean isMms) {
      if (getContext() == null) return;

      listener.handleReactionDetails(reactionTarget);
      ReactionsBottomSheetDialogFragment.create(messageId, isMms).show(requireFragmentManager(), null);
    }

    @Override
    public void onGroupMemberClicked(@NonNull RecipientId recipientId, @NonNull GroupId groupId) {
      if (getContext() == null) return;

      RecipientBottomSheetDialogFragment.create(recipientId, groupId).show(requireFragmentManager(), "BOTTOM");
    }

    @Override
    public void onMessageWithErrorClicked(@NonNull MessageRecord messageRecord) {
      listener.onMessageWithErrorClicked(messageRecord);
    }

    @Override
    public void onVoiceNotePause(@NonNull Uri uri) {
      voiceNoteMediaController.pausePlayback(uri);
    }

    @Override
    public void onVoiceNotePlay(@NonNull Uri uri, long messageId, double progress) {
      voiceNoteMediaController.startConsecutivePlayback(uri, messageId, progress);
    }

    @Override
    public void onVoiceNoteSeekTo(@NonNull Uri uri, double progress) {
      voiceNoteMediaController.seekToPosition(uri, progress);
    }

    @Override
    public void onRegisterVoiceNoteCallbacks(@NonNull Observer<VoiceNotePlaybackState> onPlaybackStartObserver) {
      voiceNoteMediaController.getVoiceNotePlaybackState().observe(getViewLifecycleOwner(), onPlaybackStartObserver);
    }

    @Override
    public void onUnregisterVoiceNoteCallbacks(@NonNull Observer<VoiceNotePlaybackState> onPlaybackStartObserver) {
      voiceNoteMediaController.getVoiceNotePlaybackState().removeObserver(onPlaybackStartObserver);
    }

    @Override
    public boolean onUrlClicked(@NonNull String url) {
      return CommunicationActions.handlePotentialGroupLinkUrl(requireActivity(), url);
    }

    @Override
    public void onGroupMigrationLearnMoreClicked(@NonNull GroupMigrationMembershipChange membershipChange) {
      GroupsV1MigrationInfoBottomSheetDialogFragment.show(requireFragmentManager(), membershipChange);
    }

    @Override
    public void onDecryptionFailedLearnMoreClicked() {
      new AlertDialog.Builder(requireContext())
          .setView(R.layout.decryption_failed_dialog)
          .setPositiveButton(android.R.string.ok, (d, w) -> {
            d.dismiss();
          })
          .setNeutralButton(R.string.ConversationFragment_contact_us, (d, w) -> {
            Intent intent = new Intent(requireContext(), ApplicationPreferencesActivity.class);
            intent.putExtra(ApplicationPreferencesActivity.LAUNCH_TO_HELP_FRAGMENT, true);

            startActivity(intent);
            d.dismiss();
          })
          .show();
    }

    @Override
    public void onSafetyNumberLearnMoreClicked(@NonNull Recipient recipient) {
      if (recipient.isGroup()) {
        throw new AssertionError("Must be individual");
      }

      AlertDialog dialog = new AlertDialog.Builder(requireContext())
                                          .setView(R.layout.safety_number_changed_learn_more_dialog)
                                          .setPositiveButton(R.string.ConversationFragment_verify, (d, w) -> {
                                            SimpleTask.run(getLifecycle(), () -> {
                                              return DatabaseFactory.getIdentityDatabase(requireContext()).getIdentity(recipient.getId());
                                            }, identityRecord -> {
                                              if (identityRecord.isPresent()) {
                                                startActivity(VerifyIdentityActivity.newIntent(requireContext(), identityRecord.get()));
                                              }});
                                            d.dismiss();
                                          })
                                          .setNegativeButton(R.string.ConversationFragment_not_now, (d, w) -> {
                                            d.dismiss();
                                          })
                                          .create();
      dialog.setOnShowListener(d -> {
        TextView title = Objects.requireNonNull(dialog.findViewById(R.id.safety_number_learn_more_title));
        TextView body  = Objects.requireNonNull(dialog.findViewById(R.id.safety_number_learn_more_body));

        title.setText(getString(R.string.ConversationFragment_your_safety_number_with_s_changed, recipient.getDisplayName(requireContext())));
        body.setText(getString(R.string.ConversationFragment_your_safety_number_with_s_changed_likey_because_they_reinstalled_signal, recipient.getDisplayName(requireContext())));
      });

      dialog.show();
    }
    @Override
    public void onJoinGroupCallClicked() {
      CommunicationActions.startVideoCall(requireActivity(), recipient.get());
    }

    @Override
    public void onInviteFriendsToGroupClicked(@NonNull GroupId.V2 groupId) {
      GroupLinkInviteFriendsBottomSheetDialogFragment.show(requireActivity().getSupportFragmentManager(), groupId);
    }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == CODE_ADD_EDIT_CONTACT && getContext() != null) {
      ApplicationDependencies.getJobManager().add(new DirectoryRefreshJob(false));
    }
  }

  private void handleEnterMultiSelect(@NonNull ConversationMessage conversationMessage) {
    ((ConversationAdapter) list.getAdapter()).toggleSelection(conversationMessage);
    list.getAdapter().notifyDataSetChanged();

    actionMode = ((AppCompatActivity)getActivity()).startSupportActionMode(actionModeCallback);
  }

  private final class ConversationSnapToTopDataObserver extends SnapToTopDataObserver {

    public ConversationSnapToTopDataObserver(@NonNull RecyclerView recyclerView,
                                             @Nullable ScrollRequestValidator scrollRequestValidator)
    {
      super(recyclerView, scrollRequestValidator, () -> {
        list.scrollToPosition(0);
        list.post(ConversationFragment.this::postMarkAsReadRequest);
      });
    }

    @Override
    public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
      // Do nothing.
    }

    @Override
    public void onItemRangeInserted(int positionStart, int itemCount) {
      if (positionStart == 0 && itemCount == 1 && isTypingIndicatorShowing()) {
        return;
      }

      super.onItemRangeInserted(positionStart, itemCount);
    }
  }

  private final class ConversationScrollRequestValidator implements SnapToTopDataObserver.ScrollRequestValidator {

    @Override
    public boolean isPositionStillValid(int position) {
      if (getListAdapter() == null) {
        return position >= 0;
      } else {
        return position >= 0 && position < getListAdapter().getItemCount();
      }
    }

    @Override
    public boolean isItemAtPositionLoaded(int position) {
      if (getListAdapter() == null) {
        return false;
      } else if (getListAdapter().hasFooter() && position == getListAdapter().getItemCount() - 1) {
        return true;
      } else {
        return getListAdapter().getItem(position) != null;
      }
    }
  }

  private class ReactionsToolbarListener implements Toolbar.OnMenuItemClickListener {

    private final ConversationMessage conversationMessage;

    private ReactionsToolbarListener(@NonNull ConversationMessage conversationMessage) {
      this.conversationMessage = conversationMessage;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
      switch (item.getItemId()) {
        case R.id.action_info:        handleDisplayDetails(conversationMessage);                                            return true;
        case R.id.action_delete:      handleDeleteMessages(SetUtil.newHashSet(conversationMessage));                        return true;
        case R.id.action_copy:        handleCopyMessage(SetUtil.newHashSet(conversationMessage));                           return true;
        case R.id.action_reply:       handleReplyMessage(conversationMessage);                                              return true;
        case R.id.action_multiselect: handleEnterMultiSelect(conversationMessage);                                          return true;
        case R.id.action_forward:     handleForwardMessage(conversationMessage);                                            return true;
        case R.id.action_download:    handleSaveAttachment((MediaMmsMessageRecord) conversationMessage.getMessageRecord()); return true;
        default:                                                                                                            return false;
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
        WindowUtil.setStatusBarColor(window, getResources().getColor(R.color.action_mode_status_bar));
      }

      if (!ThemeUtil.isDarkTheme(getContext())) {
        WindowUtil.setLightStatusBar(getActivity().getWindow());
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
        WindowUtil.setStatusBarColor(requireActivity().getWindow(), statusBarColor);
      }

      WindowUtil.clearLightStatusBar(getActivity().getWindow());
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
          handleDisplayDetails(getSelectedConversationMessage());
          actionMode.finish();
          return true;
        case R.id.menu_context_forward:
          handleForwardMessage(getSelectedConversationMessage());
          actionMode.finish();
          return true;
        case R.id.menu_context_resend:
          handleResendMessage(getSelectedConversationMessage().getMessageRecord());
          actionMode.finish();
          return true;
        case R.id.menu_context_save_attachment:
          handleSaveAttachment((MediaMmsMessageRecord) getSelectedConversationMessage().getMessageRecord());
          actionMode.finish();
          return true;
        case R.id.menu_context_reply:
          maybeShowSwipeToReplyTooltip();
          handleReplyMessage(getSelectedConversationMessage());
          actionMode.finish();
          return true;
      }

      return false;
    }
  }

  private static class ConversationDateHeader extends StickyHeaderViewHolder {

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
