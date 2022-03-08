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
import android.animation.Animator;
import android.animation.LayoutTransition;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
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
import androidx.core.view.ViewCompat;
import androidx.core.view.ViewKt;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.OnScrollListener;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.signal.core.util.DimensionUnit;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.ConversationScrollToView;
import org.thoughtcrime.securesms.components.ConversationTypingView;
import org.thoughtcrime.securesms.components.TypingStatusRepository;
import org.thoughtcrime.securesms.components.menu.ActionItem;
import org.thoughtcrime.securesms.components.menu.SignalBottomActionBar;
import org.thoughtcrime.securesms.components.recyclerview.SmoothScrollingLinearLayoutManager;
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity;
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaControllerOwner;
import org.thoughtcrime.securesms.components.voice.VoiceNotePlaybackState;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.contactshare.ContactUtil;
import org.thoughtcrime.securesms.contactshare.SharedContactDetailsActivity;
import org.thoughtcrime.securesms.conversation.ConversationAdapter.ItemClickListener;
import org.thoughtcrime.securesms.conversation.ConversationAdapter.StickyHeaderViewHolder;
import org.thoughtcrime.securesms.conversation.colors.Colorizer;
import org.thoughtcrime.securesms.conversation.colors.RecyclerViewColorizer;
import org.thoughtcrime.securesms.conversation.mutiselect.ConversationItemAnimator;
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectItemDecoration;
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectPart;
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardBottomSheet;
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment;
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs;
import org.thoughtcrime.securesms.conversation.ui.error.EnableCallNotificationSettingsDialog;
import org.thoughtcrime.securesms.conversation.ui.error.SafetyNumberChangeDialog;
import org.thoughtcrime.securesms.database.MessageDatabase;
import org.thoughtcrime.securesms.database.MmsDatabase;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.SmsDatabase;
import org.thoughtcrime.securesms.database.model.InMemoryMessageRecord;
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ItemDecoration;
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4PlaybackController;
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4PlaybackPolicy;
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ProjectionPlayerHolder;
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ProjectionRecycler;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.groups.GroupMigrationMembershipChange;
import org.thoughtcrime.securesms.groups.ui.invitesandrequests.invite.GroupLinkInviteFriendsBottomSheetDialogFragment;
import org.thoughtcrime.securesms.groups.ui.managegroup.dialogs.GroupDescriptionDialog;
import org.thoughtcrime.securesms.groups.ui.migration.GroupsV1MigrationInfoBottomSheetDialogFragment;
import org.thoughtcrime.securesms.groups.v2.GroupDescriptionUtil;
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob;
import org.thoughtcrime.securesms.jobs.MultiDeviceViewOnceOpenJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.longmessage.LongMessageFragment;
import org.thoughtcrime.securesms.messagedetails.MessageDetailsFragment;
import org.thoughtcrime.securesms.messagerequests.MessageRequestState;
import org.thoughtcrime.securesms.messagerequests.MessageRequestViewModel;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.mms.PartAuthority;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.ratelimit.RecaptchaProofBottomSheetFragment;
import org.thoughtcrime.securesms.reactions.ReactionsBottomSheetDialogFragment;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientExporter;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment;
import org.thoughtcrime.securesms.revealable.ViewOnceMessageActivity;
import org.thoughtcrime.securesms.revealable.ViewOnceUtil;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.stickers.StickerLocator;
import org.thoughtcrime.securesms.stickers.StickerPackPreviewActivity;
import org.thoughtcrime.securesms.util.CachedInflater;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.HtmlUtil;
import org.thoughtcrime.securesms.util.MessageRecordUtil;
import org.thoughtcrime.securesms.util.RemoteDeleteUtil;
import org.thoughtcrime.securesms.util.SaveAttachmentTask;
import org.thoughtcrime.securesms.util.SignalLocalMetrics;
import org.thoughtcrime.securesms.util.SignalProxyUtil;
import org.thoughtcrime.securesms.util.SnapToTopDataObserver;
import org.thoughtcrime.securesms.util.StickyHeaderDecoration;
import org.thoughtcrime.securesms.util.Stopwatch;
import org.thoughtcrime.securesms.util.StorageUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.TopToastPopup;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.WindowUtil;
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.util.task.ProgressDialogAsyncTask;
import org.thoughtcrime.securesms.verify.VerifyIdentityActivity;
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import kotlin.Unit;

@SuppressLint("StaticFieldLeak")
public class ConversationFragment extends LoggingFragment implements MultiselectForwardBottomSheet.Callback {
  private static final String TAG = Log.tag(ConversationFragment.class);

  private static final int SCROLL_ANIMATION_THRESHOLD = 50;
  private static final int CODE_ADD_EDIT_CONTACT      = 77;

  private final ActionModeCallback actionModeCallback     = new ActionModeCallback();
  private final ItemClickListener  selectionClickListener = new ConversationFragmentItemClickListener();

  private ConversationFragmentListener listener;

  private LiveRecipient               recipient;
  private long                        threadId;
  private ActionMode                  actionMode;
  private Locale                      locale;
  private FrameLayout                 videoContainer;
  private RecyclerView                list;
  private RecyclerView.ItemDecoration lastSeenDecoration;
  private RecyclerView.ItemDecoration inlineDateDecoration;
  private ViewSwitcher                topLoadMoreView;
  private ViewSwitcher                bottomLoadMoreView;
  private ConversationTypingView      typingView;
  private View                        composeDivider;
  private ConversationScrollToView    scrollToBottomButton;
  private ConversationScrollToView    scrollToMentionButton;
  private TextView                    scrollDateHeader;
  private ConversationBannerView      conversationBanner;
  private MessageRequestViewModel     messageRequestViewModel;
  private MessageCountsViewModel      messageCountsViewModel;
  private ConversationViewModel       conversationViewModel;
  private ConversationGroupViewModel  groupViewModel;
  private SnapToTopDataObserver       snapToTopDataObserver;
  private MarkReadHelper              markReadHelper;
  private Animation                   scrollButtonInAnimation;
  private Animation                   mentionButtonInAnimation;
  private Animation                   scrollButtonOutAnimation;
  private Animation                   mentionButtonOutAnimation;
  private OnScrollListener            conversationScrollListener;
  private int                         lastSeenScrollOffset;
  private View                        toolbarShadow;
  private Stopwatch                   startupStopwatch;
  private LayoutTransition            layoutTransition;
  private TransitionListener          transitionListener;
  private View                        reactionsShade;
  private SignalBottomActionBar       bottomActionBar;

  private GiphyMp4ProjectionRecycler giphyMp4ProjectionRecycler;
  private Colorizer                  colorizer;
  private ConversationUpdateTick     conversationUpdateTick;
  private MultiselectItemDecoration  multiselectItemDecoration;

  public static void prepare(@NonNull Context context) {
    FrameLayout parent = new FrameLayout(context);
    parent.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

    CachedInflater.from(context).cacheUntilLimit(R.layout.conversation_item_received_text_only, parent, 25);
    CachedInflater.from(context).cacheUntilLimit(R.layout.conversation_item_sent_text_only, parent, 25);
    CachedInflater.from(context).cacheUntilLimit(R.layout.conversation_item_received_multimedia, parent, 10);
    CachedInflater.from(context).cacheUntilLimit(R.layout.conversation_item_sent_multimedia, parent, 10);
    CachedInflater.from(context).cacheUntilLimit(R.layout.conversation_item_update, parent, 5);
    CachedInflater.from(context).cacheUntilLimit(R.layout.cursor_adapter_header_footer_view, parent, 2);
  }

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    this.locale = Locale.getDefault();
    startupStopwatch = new Stopwatch("conversation-open");
    SignalLocalMetrics.ConversationOpen.start();
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    final View view = inflater.inflate(R.layout.conversation_fragment, container, false);
    videoContainer = view.findViewById(R.id.video_container);
    list           = view.findViewById(android.R.id.list);
    composeDivider = view.findViewById(R.id.compose_divider);

    layoutTransition   = new LayoutTransition();
    transitionListener = new TransitionListener(list);

    scrollToBottomButton  = view.findViewById(R.id.scroll_to_bottom);
    scrollToMentionButton = view.findViewById(R.id.scroll_to_mention);
    scrollDateHeader      = view.findViewById(R.id.scroll_date_header);
    toolbarShadow         = requireActivity().findViewById(R.id.conversation_toolbar_shadow);
    reactionsShade        = view.findViewById(R.id.reactions_shade);
    bottomActionBar       = view.findViewById(R.id.conversation_bottom_action_bar);

    final LinearLayoutManager      layoutManager            = new SmoothScrollingLinearLayoutManager(getActivity(), true);
    final ConversationItemAnimator conversationItemAnimator = new ConversationItemAnimator(
        () -> {
          ConversationAdapter adapter = getListAdapter();
          if (adapter == null) {
            return false;
          } else {
            return Util.hasItems(adapter.getSelectedItems());
          }
        },
        () -> conversationViewModel.shouldPlayMessageAnimations() && list.getScrollState() == RecyclerView.SCROLL_STATE_IDLE,
        () -> list.canScrollVertically(1) || list.canScrollVertically(-1));

    multiselectItemDecoration = new MultiselectItemDecoration(requireContext(),
                                                              () -> conversationViewModel.getWallpaper().getValue());

    list.setHasFixedSize(false);
    list.setLayoutManager(layoutManager);

    RecyclerViewColorizer recyclerViewColorizer = new RecyclerViewColorizer(list);

    list.addItemDecoration(multiselectItemDecoration);
    list.setItemAnimator(conversationItemAnimator);

    getViewLifecycleOwner().getLifecycle().addObserver(multiselectItemDecoration);

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
                                                               messageRequestViewModel.shouldShowMessageRequest(),
                                                               groupViewModel.isNonAdminInAnnouncementGroup()),
            this::handleReplyMessage
    ).attachToRecyclerView(list);

    giphyMp4ProjectionRecycler = initializeGiphyMp4();

    this.groupViewModel         = new ViewModelProvider(getParentFragment(), new ConversationGroupViewModel.Factory()).get(ConversationGroupViewModel.class);
    this.messageCountsViewModel = new ViewModelProvider(getParentFragment()).get(MessageCountsViewModel.class);
    this.conversationViewModel  = new ViewModelProvider(getParentFragment(), new ConversationViewModel.Factory()).get(ConversationViewModel.class);

    conversationViewModel.getChatColors().observe(getViewLifecycleOwner(), recyclerViewColorizer::setChatColors);
    conversationViewModel.getMessages().observe(getViewLifecycleOwner(), messages -> {
      ConversationAdapter adapter = getListAdapter();
      if (adapter != null) {
        getListAdapter().submitList(messages, () -> {
          list.post(() -> conversationViewModel.onMessagesCommitted(messages));
        });
      }
    });

    conversationViewModel.getConversationMetadata().observe(getViewLifecycleOwner(), this::presentConversationMetadata);

    conversationViewModel.getShowMentionsButton().observe(getViewLifecycleOwner(), shouldShow -> {
      if (shouldShow) {
        ViewUtil.animateIn(scrollToMentionButton, mentionButtonInAnimation);
      } else {
        ViewUtil.animateOut(scrollToMentionButton, mentionButtonOutAnimation, View.INVISIBLE);
      }
    });

    conversationViewModel.getShowScrollToBottom().observe(getViewLifecycleOwner(), shouldShow -> {
      if (shouldShow) {
        ViewUtil.animateIn(scrollToBottomButton, scrollButtonInAnimation);
      } else {
        ViewUtil.animateOut(scrollToBottomButton, scrollButtonOutAnimation, View.INVISIBLE);
      }
    });

    scrollToBottomButton.setOnClickListener(v -> scrollToBottom());
    scrollToMentionButton.setOnClickListener(v -> scrollToNextMention());

    updateToolbarDependentMargins();

    colorizer = new Colorizer();
    conversationViewModel.getNameColorsMap().observe(getViewLifecycleOwner(), nameColorsMap -> {
      colorizer.onNameColorsChanged(nameColorsMap);

      ConversationAdapter adapter = getListAdapter();
      if (adapter != null) {
        adapter.notifyItemRangeChanged(0, adapter.getItemCount(), ConversationAdapter.PAYLOAD_NAME_COLORS);
      }
    });

    conversationUpdateTick = new ConversationUpdateTick(this::updateConversationItemTimestamps);
    getViewLifecycleOwner().getLifecycle().addObserver(conversationUpdateTick);

    listener.getVoiceNoteMediaController().getVoiceNotePlayerViewState().observe(getViewLifecycleOwner(), state -> conversationViewModel.setInlinePlayerVisible(state.isPresent()));
    conversationViewModel.getConversationTopMargin().observe(getViewLifecycleOwner(), topMargin -> {
      lastSeenScrollOffset = topMargin;
      ViewUtil.setTopMargin(scrollDateHeader, topMargin + ViewUtil.dpToPx(8));
    });

    conversationViewModel.getActiveNotificationProfile().observe(getViewLifecycleOwner(), this::updateNotificationProfileStatus);

    initializeScrollButtonAnimations();
    initializeResources();
    initializeMessageRequestViewModel();
    initializeListAdapter();

    conversationViewModel.getSearchQuery().observe(getViewLifecycleOwner(), this::onSearchQueryUpdated);

    return view;
  }

  private @NonNull GiphyMp4ProjectionRecycler initializeGiphyMp4() {
    int                                            maxPlayback = GiphyMp4PlaybackPolicy.maxSimultaneousPlaybackInConversation();
    List<GiphyMp4ProjectionPlayerHolder>           holders     = GiphyMp4ProjectionPlayerHolder.injectVideoViews(requireContext(),
                                                                                                                 getViewLifecycleOwner().getLifecycle(),
                                                                                                                 videoContainer,
                                                                                                                 maxPlayback);
    GiphyMp4ProjectionRecycler callback = new GiphyMp4ProjectionRecycler(holders);

    GiphyMp4PlaybackController.attach(list, callback, maxPlayback);
    list.addItemDecoration(new GiphyMp4ItemDecoration(callback, translationY -> {
      reactionsShade.setTranslationY(translationY + list.getHeight());
      return Unit.INSTANCE;
    }), 0);

    return callback;
  }

  public void clearFocusedItem() {
    multiselectItemDecoration.setFocusedItem(null);
    list.invalidateItemDecorations();
  }

  private void updateConversationItemTimestamps() {
    ConversationAdapter conversationAdapter = getListAdapter();
    if (conversationAdapter != null) {
      getListAdapter().updateTimestamps();
    }
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    this.listener = (ConversationFragmentListener) getParentFragment();
  }

  @Override
  public void onStart() {
    super.onStart();
    initializeTypingObserver();
    SignalProxyUtil.startListeningToWebsocket();
    layoutTransition.getAnimator(LayoutTransition.CHANGE_DISAPPEARING).addListener(transitionListener);
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
    SignalExecutors.BOUNDED.submit(() -> SignalDatabase.threads().setLastScrolled(threadId, lastVisibleMessageTimestamp));
  }

  @Override
  public void onStop() {
    super.onStop();
    ApplicationDependencies.getTypingStatusRepository().getTypists(threadId).removeObservers(getViewLifecycleOwner());
    layoutTransition.getAnimator(LayoutTransition.CHANGE_DISAPPEARING).removeListener(transitionListener);
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    updateToolbarDependentMargins();
  }

  public void onNewIntent() {
    Log.d(TAG, "[onNewIntent]");

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

  public void onWallpaperChanged(@Nullable ChatWallpaper wallpaper) {
    if (list != null) {
      ConversationAdapter adapter = getListAdapter();

      if (adapter != null) {
        Log.d(TAG, "Notifying adapter that wallpaper state has changed.");

        if (adapter.onHasWallpaperChanged(wallpaper != null)) {
          setInlineDateDecoration(adapter);
        }
      }
    }
  }

  private int getStartPosition() {
    return conversationViewModel.getArgs().getStartingPosition();
  }

  private void initializeMessageRequestViewModel() {
    MessageRequestViewModel.Factory factory = new MessageRequestViewModel.Factory(requireContext());

    messageRequestViewModel = new ViewModelProvider(requireParentFragment(), factory).get(MessageRequestViewModel.class);
    messageRequestViewModel.setConversationInfo(recipient.getId(), threadId);

    listener.onMessageRequest(messageRequestViewModel);

    messageRequestViewModel.getRecipientInfo().observe(getViewLifecycleOwner(), recipientInfo -> {
      presentMessageRequestProfileView(requireContext(), recipientInfo, conversationBanner);
    });

    messageRequestViewModel.getMessageData().observe(getViewLifecycleOwner(), data -> {
      ConversationAdapter adapter = getListAdapter();
      if (adapter != null) {
        adapter.setMessageRequestAccepted(data.getMessageState() == MessageRequestState.NONE);
      }
    });
  }

  private void presentMessageRequestProfileView(@NonNull Context context, @NonNull MessageRequestViewModel.RecipientInfo recipientInfo, @Nullable ConversationBannerView conversationBanner) {
    if (conversationBanner == null) {
      return;
    }

    Recipient    recipient          = recipientInfo.getRecipient();
    boolean      isSelf             = Recipient.self().equals(recipient);
    int          memberCount        = recipientInfo.getGroupMemberCount();
    int          pendingMemberCount = recipientInfo.getGroupPendingMemberCount();
    List<String> groups             = recipientInfo.getSharedGroups();

    conversationBanner.setBadge(recipient);

    if (recipient != null) {
      conversationBanner.setAvatar(GlideApp.with(context), recipient);
      conversationBanner.showBackgroundBubble(recipient.hasWallpaper());

      String title = conversationBanner.setTitle(recipient);
      conversationBanner.setAbout(recipient);

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
      if (TextUtils.isEmpty(recipientInfo.getGroupDescription())) {
        conversationBanner.setLinkifyDescription(false);
        conversationBanner.hideDescription();
      } else {
        conversationBanner.setLinkifyDescription(true);
        boolean linkifyWebLinks = recipientInfo.getMessageRequestState() == MessageRequestState.NONE;
        conversationBanner.showDescription();
        GroupDescriptionUtil.setText(context,
                                     conversationBanner.getDescription(),
                                     recipientInfo.getGroupDescription(),
                                     linkifyWebLinks,
                                     () -> GroupDescriptionDialog.show(getChildFragmentManager(),
                                                                       recipient.getDisplayName(context),
                                                                       recipientInfo.getGroupDescription(),
                                                                       linkifyWebLinks));
      }
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
    this.markReadHelper = new MarkReadHelper(threadId, requireContext(), getViewLifecycleOwner());

    conversationViewModel.onConversationDataAvailable(recipient.getId(), threadId, startingPosition);
    messageCountsViewModel.setThreadId(threadId);

    messageCountsViewModel.getUnreadMessagesCount().observe(getViewLifecycleOwner(), scrollToBottomButton::setUnreadCount);
    messageCountsViewModel.getUnreadMentionsCount().observe(getViewLifecycleOwner(), count -> {
      scrollToMentionButton.setUnreadCount(count);
      conversationViewModel.setHasUnreadMentions(count > 0);
    });

    conversationScrollListener = new ConversationScrollListener(requireContext());
    list.addOnScrollListener(conversationScrollListener);
    list.addOnScrollListener(new ShadowScrollListener());

    if (oldThreadId != threadId) {
      ApplicationDependencies.getTypingStatusRepository().getTypists(oldThreadId).removeObservers(getViewLifecycleOwner());
    }
  }

  private void initializeListAdapter() {
    if (threadId == -1) {
      toolbarShadow.setVisibility(View.GONE);
    }

    if (this.recipient != null) {
      if (getListAdapter() != null && getListAdapter().isForRecipientId(this.recipient.getId())) {
        Log.d(TAG, "List adapter already initialized for " + this.recipient.getId());
        return;
      }

      Log.d(TAG, "Initializing adapter for " + recipient.getId());
      ConversationAdapter adapter = new ConversationAdapter(requireContext(), this, GlideApp.with(this), locale, selectionClickListener, this.recipient.get(), colorizer);
      adapter.setPagingController(conversationViewModel.getPagingController());
      list.setAdapter(adapter);
      setInlineDateDecoration(adapter);
      ConversationAdapter.initializePool(list.getRecycledViewPool());

      adapter.registerAdapterDataObserver(snapToTopDataObserver);
      adapter.registerAdapterDataObserver(new CheckExpirationDataObserver());

      setLastSeen(conversationViewModel.getLastSeen());

      adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
          startupStopwatch.split("data-set");
          SignalLocalMetrics.ConversationOpen.onDataLoaded();
          adapter.unregisterAdapterDataObserver(this);
          list.post(() -> {
            startupStopwatch.split("first-render");
            startupStopwatch.stop(TAG);
            SignalLocalMetrics.ConversationOpen.onRenderFinished();
          });
        }
      });
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

    typists.removeObservers(getViewLifecycleOwner());
    typists.observe(getViewLifecycleOwner(), typingState ->  {
      List<Recipient> recipients;
      boolean         replacedByIncomingMessage;

      if (typingState != null) {
        recipients                = typingState.getTypists();
        replacedByIncomingMessage = typingState.isReplacedByIncomingMessage();
      } else {
        recipients                = Collections.emptyList();
        replacedByIncomingMessage = false;
      }

      Recipient resolved = recipient.get();
      typingView.setTypists(GlideApp.with(ConversationFragment.this), recipients, resolved.isGroup(), resolved.hasWallpaper());

      ConversationAdapter adapter = getListAdapter();
      adapter.setTypingView(typingView);

      if (recipients.size() > 0) {
        if (!isTypingIndicatorShowing() && isAtBottom()) {
          adapter.setTypingViewEnabled(true);
          list.scrollToPosition(0);
        } else {
          adapter.setTypingViewEnabled(true);
        }
      } else {
        if (isTypingIndicatorShowing() && getListLayoutManager().findFirstCompletelyVisibleItemPosition() == 0 && getListLayoutManager().getItemCount() > 1 && !replacedByIncomingMessage) {
          adapter.setTypingViewEnabled(false);
        } else if (!replacedByIncomingMessage) {
          adapter.setTypingViewEnabled(false);
        } else {
          adapter.setTypingViewEnabled(false);
        }
      }
    });
  }

  private void setCorrectActionModeMenuVisibility() {
    Set<MultiselectPart> selectedParts = getListAdapter().getSelectedItems();

    if (actionMode != null && selectedParts.size() == 0) {
      actionMode.finish();
      return;
    }

    setBottomActionBarVisibility(true);

    MenuState menuState = MenuState.getMenuState(recipient.get(), selectedParts, messageRequestViewModel.shouldShowMessageRequest(), groupViewModel.isNonAdminInAnnouncementGroup());

    List<ActionItem> items = new ArrayList<>();

    if (menuState.shouldShowReplyAction()) {
      items.add(new ActionItem(R.drawable.ic_reply_24_tinted, getResources().getString(R.string.conversation_selection__menu_reply), () -> {
        maybeShowSwipeToReplyTooltip();
        handleReplyMessage(getSelectedConversationMessage());
        actionMode.finish();
      }));
    }

    if (menuState.shouldShowForwardAction()) {
      items.add(new ActionItem(R.drawable.ic_forward_24_tinted, getResources().getString(R.string.conversation_selection__menu_forward), () -> handleForwardMessageParts(selectedParts)));
    }

    if (menuState.shouldShowSaveAttachmentAction()) {
      items.add(new ActionItem(R.drawable.ic_save_24_tinted, getResources().getString(R.string.conversation_selection__menu_save), () -> {
        handleSaveAttachment((MediaMmsMessageRecord) getSelectedConversationMessage().getMessageRecord());
        actionMode.finish();
      }));
    }

    if (menuState.shouldShowCopyAction()) {
      items.add(new ActionItem(R.drawable.ic_copy_24_tinted, getResources().getString(R.string.conversation_selection__menu_copy), () -> {
        handleCopyMessage(selectedParts);
        actionMode.finish();
      }));
    }

    if (menuState.shouldShowDetailsAction()) {
      items.add(new ActionItem(R.drawable.ic_info_tinted_24, getResources().getString(R.string.conversation_selection__menu_message_details), () -> {
        handleDisplayDetails(getSelectedConversationMessage());
        actionMode.finish();
      }));
    }

    if (menuState.shouldShowDeleteAction()) {
      items.add(new ActionItem(R.drawable.ic_delete_tinted_24, getResources().getString(R.string.conversation_selection__menu_delete), () -> {
        handleDeleteMessages(selectedParts);
        actionMode.finish();
      }));
    }

    bottomActionBar.setItems(items);
  }

  private void setBottomActionBarVisibility(boolean isVisible) {
    boolean isCurrentlyVisible = bottomActionBar.getVisibility() == View.VISIBLE;
    if (isVisible == isCurrentlyVisible) {
      return;
    }

    int additionalScrollOffset = (int) DimensionUnit.DP.toPixels(54);

    if (isVisible) {
      ViewUtil.animateIn(bottomActionBar, bottomActionBar.getEnterAnimation());
      listener.onBottomActionBarVisibilityChanged(View.VISIBLE);

      bottomActionBar.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
          if (bottomActionBar.getHeight() == 0 && bottomActionBar.getVisibility() == View.VISIBLE) {
            return false;
          }

          bottomActionBar.getViewTreeObserver().removeOnPreDrawListener(this);

          int bottomPadding = bottomActionBar.getHeight() + (int) DimensionUnit.DP.toPixels(18);
          list.setPadding(list.getPaddingLeft(), list.getPaddingTop(), list.getPaddingRight(), bottomPadding);

          list.scrollBy(0, -(bottomPadding - additionalScrollOffset));

          return false;
        }
      });
    } else {
      ViewUtil.animateOut(bottomActionBar, bottomActionBar.getExitAnimation())
              .addListener(new ListenableFuture.Listener<Boolean>() {
                @Override public void onSuccess(Boolean result) {
                  int scrollOffset = list.getPaddingBottom() - additionalScrollOffset;
                  listener.onBottomActionBarVisibilityChanged(View.GONE);
                  list.setPadding(list.getPaddingLeft(), list.getPaddingTop(), list.getPaddingRight(), getResources().getDimensionPixelSize(R.dimen.conversation_bottom_padding));

                  ViewKt.doOnPreDraw(list, view -> {
                    list.scrollBy(0, scrollOffset);
                    return Unit.INSTANCE;
                  });
                }

                @Override public void onFailure(ExecutionException e) {
                }
              });
    }
  }

  private @Nullable ConversationAdapter getListAdapter() {
    return (ConversationAdapter) list.getAdapter();
  }

  private SmoothScrollingLinearLayoutManager getListLayoutManager() {
    return (SmoothScrollingLinearLayoutManager) list.getLayoutManager();
  }

  private ConversationMessage getSelectedConversationMessage() {
    Set<ConversationMessage> messageRecords = Stream.of(getListAdapter().getSelectedItems())
                                                    .map(MultiselectPart::getConversationMessage)
                                                    .distinct()
                                                    .collect(Collectors.toSet());

    if (messageRecords.size() == 1) return messageRecords.stream().findFirst().get();
    else                            throw new AssertionError();
  }

  public void reload(Recipient recipient, long threadId) {
    Log.d(TAG, "[reload] Recipient: " + recipient.getId() + ", ThreadId: " + threadId);
    this.recipient = recipient.live();

    if (this.threadId != threadId) {
      Log.i(TAG, "ThreadId changed from " + this.threadId + " to " + threadId + ". Recipient was " + this.recipient.getId() + " and is now " + recipient.getId());

      this.threadId = threadId;
      messageRequestViewModel.setConversationInfo(recipient.getId(), threadId);

      snapToTopDataObserver.requestScrollPosition(0);
      conversationViewModel.onConversationDataAvailable(recipient.getId(), threadId, -1);
      messageCountsViewModel.setThreadId(threadId);
      markReadHelper = new MarkReadHelper(threadId, requireContext(), getViewLifecycleOwner());
      initializeListAdapter();
      initializeTypingObserver();
    }
  }

  public void scrollToBottom() {
    if (getListLayoutManager().findFirstVisibleItemPosition() < SCROLL_ANIMATION_THRESHOLD) {
      Log.d(TAG, "scrollToBottom: Smooth scrolling to bottom of screen.");
      list.smoothScrollToPosition(0);
    } else {
      Log.d(TAG, "scrollToBottom: Scrolling to bottom of screen.");
      list.scrollToPosition(0);
    }
  }

  public void setInlineDateDecoration(@NonNull ConversationAdapter adapter) {
    if (inlineDateDecoration != null) {
      list.removeItemDecoration(inlineDateDecoration);
    }

    inlineDateDecoration = new StickyHeaderDecoration(adapter, false, false, ConversationAdapter.HEADER_TYPE_INLINE_DATE);
    list.addItemDecoration(inlineDateDecoration, 0);
  }

  public void setLastSeen(long lastSeen) {
    if (lastSeenDecoration != null) {
      list.removeItemDecoration(lastSeenDecoration);
    }

    lastSeenDecoration = new LastSeenHeader(getListAdapter(), lastSeen);
    list.addItemDecoration(lastSeenDecoration, 0);
  }

  private void handleCopyMessage(final Set<MultiselectPart> multiselectParts) {
    CharSequence bodies = Stream.of(multiselectParts)
                                .sortBy(m -> m.getMessageRecord().getDateReceived())
                                .map(MultiselectPart::getConversationMessage)
                                .distinct()
                                .map(m -> m.getDisplayBody(requireContext()))
                                .filterNot(TextUtils::isEmpty)
                                .collect(SpannableStringBuilder::new, (bodyBuilder, body) -> {
                                  if (bodyBuilder.length() > 0) {
                                    bodyBuilder.append('\n');
                                  }
                                  bodyBuilder.append(body);
                                });

    if (!TextUtils.isEmpty(bodies)) {
      Util.copyToClipboard(requireContext(), bodies);
    }
  }

  private void handleDeleteMessages(final Set<MultiselectPart> multiselectParts) {
    Set<MessageRecord> messageRecords = Stream.of(multiselectParts).map(MultiselectPart::getMessageRecord).collect(Collectors.toSet());
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
              threadDeleted = SignalDatabase.mms().deleteMessage(messageRecord.getId());
            } else {
              threadDeleted = SignalDatabase.sms().deleteMessage(messageRecord.getId());
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
    MessageDetailsFragment.create(message.getMessageRecord(), recipient.getId()).show(getChildFragmentManager(), null);
  }

  private void handleForwardMessageParts(Set<MultiselectPart> multiselectParts) {
    listener.onForwardClicked();

    MultiselectForwardFragmentArgs.create(requireContext(),
                                          multiselectParts,
                                          args -> MultiselectForwardFragment.showBottomSheet(getChildFragmentManager(), args));
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
                                                            .map(s -> new SaveAttachmentTask.Attachment(s.getUri(), s.getContentType(), message.getDateSent(), s.getFileName().orNull()))
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

  public long stageOutgoingMessage(OutgoingMediaMessage message) {
    MessageRecord messageRecord = MmsDatabase.readerFor(message, threadId).getCurrent();

    if (getListAdapter() != null) {
      setLastSeen(0);
      list.post(() -> list.scrollToPosition(0));
    }

    return messageRecord.getId();
  }

  public long stageOutgoingMessage(OutgoingTextMessage message, long messageId) {
    MessageRecord messageRecord = SmsDatabase.readerFor(message, threadId, messageId).getCurrent();

    if (getListAdapter() != null) {
      setLastSeen(0);
      list.post(() -> list.scrollToPosition(0));
    }

    return messageRecord.getId();
  }

  private void presentConversationMetadata(@NonNull ConversationData conversation) {
    ConversationAdapter adapter = getListAdapter();
    if (adapter == null) {
      return;
    }

    adapter.setFooterView(conversationBanner);

    Runnable afterScroll = () -> {
      if (!conversation.getMessageRequestData().isMessageRequestAccepted()) {
        snapToTopDataObserver.requestScrollPosition(adapter.getItemCount() - 1);
      }

      setLastSeen(conversation.getLastSeen());

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
    } else if (conversation.getMessageRequestData().isMessageRequestAccepted()) {
      snapToTopDataObserver.buildScrollPosition(conversation.shouldScrollToLastSeen() ? lastSeenPosition : lastScrolledPosition)
                           .withOnPerformScroll((layoutManager, position) -> layoutManager.scrollToPositionWithOffset(position, list.getHeight() - (conversation.shouldScrollToLastSeen() ? lastSeenScrollOffset : 0)))
                           .withOnScrollRequestComplete(afterScroll)
                           .submit();
    } else {
      snapToTopDataObserver.buildScrollPosition(adapter.getItemCount() - 1)
                           .withOnScrollRequestComplete(afterScroll)
                           .submit();
    }
  }

  private void updateNotificationProfileStatus(@NonNull Optional<NotificationProfile> activeProfile) {
    if (activeProfile.isPresent() && activeProfile.get().getId() != SignalStore.notificationProfileValues().getLastProfilePopup()) {
      requireView().postDelayed(() -> {
        SignalStore.notificationProfileValues().setLastProfilePopup(activeProfile.get().getId());
        SignalStore.notificationProfileValues().setLastProfilePopupTime(System.currentTimeMillis());
        TopToastPopup.show(((ViewGroup) requireView()), R.drawable.ic_moon_16, getString(R.string.ConversationFragment__s_on, activeProfile.get().getName()));
      }, 500L);
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
    return getListAdapter().isTypingViewEnabled();
  }

  private void onSearchQueryUpdated(@Nullable String query) {
    if (getListAdapter() != null) {
      getListAdapter().onSearchQueryUpdated(query);
    }
  }

  public @NonNull Colorizer getColorizer() {
    return Objects.requireNonNull(colorizer);
  }

  @SuppressWarnings("CodeBlock2Expr")
  public void jumpToMessage(@NonNull RecipientId author, long timestamp, @Nullable Runnable onMessageNotFound) {
    SimpleTask.run(getLifecycle(), () -> {
      return SignalDatabase.mmsSms().getMessagePositionInConversation(threadId, timestamp, author);
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

                                 if (child == null || !layoutManager.isViewPartiallyVisible(child, true, false)) {
                                   layoutManager.scrollToPositionWithOffset(p, list.getHeight() / 4);
                                 }
                               } else {
                                 layoutManager.scrollToPositionWithOffset(p, list.getHeight() / 4);
                               }
                               getListAdapter().pulseAtPosition(position);
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
      int text = ViewUtil.isLtr(requireContext()) ? R.string.ConversationFragment_you_can_swipe_to_the_right_reply
                                                  : R.string.ConversationFragment_you_can_swipe_to_the_left_reply;
      Snackbar.make(list, text, Snackbar.LENGTH_LONG)
              .setTextColor(Color.WHITE)
              .show();

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
      return SignalDatabase.mms().getOldestUnreadMentionDetails(threadId);
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
        MessageRecord record                 = item.getMessageRecord();
        long          latestReactionReceived = Stream.of(record.getReactions())
                                                     .map(ReactionRecord::getDateReceived)
                                                     .max(Long::compareTo)
                                                     .orElse(0L);

        markReadHelper.onViewsRevealed(Math.max(record.getDateReceived(), latestReactionReceived));
      }
    }
  }

  private void updateToolbarDependentMargins() {
    Toolbar toolbar = requireActivity().findViewById(R.id.toolbar);
    toolbar.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
      @Override
      public void onGlobalLayout() {
        Rect rect = new Rect();
        toolbar.getGlobalVisibleRect(rect);
        conversationViewModel.setToolbarBottom(rect.bottom);
        ViewUtil.setTopMargin(conversationBanner, rect.bottom + ViewUtil.dpToPx(16));
        toolbar.getViewTreeObserver().removeOnGlobalLayoutListener(this);
      }
    });
  }

  private @NonNull String calculateSelectedItemCount() {
    ConversationAdapter adapter = getListAdapter();
    int count = 0;
    if (adapter != null && !adapter.getSelectedItems().isEmpty()) {
      count = (int) adapter.getSelectedItems()
                           .stream()
                           .map(MultiselectPart::getConversationMessage)
                           .distinct()
                           .count();
    }

    return requireContext().getResources().getQuantityString(R.plurals.conversation_context__s_selected, count, count);

  }

  @Override
  public void onFinishForwardAction() {
    if (actionMode != null) {
      actionMode.finish();
    }
  }

  @Override
  public void onDismissForwardSheet() {
  }

  public interface ConversationFragmentListener extends VoiceNoteMediaControllerOwner {
    boolean isKeyboardOpen();
    void    setThreadId(long threadId);
    void    handleReplyMessage(ConversationMessage conversationMessage);
    void    onMessageActionToolbarOpened();
    void    onBottomActionBarVisibilityChanged(int visibility);
    void    onForwardClicked();
    void    onMessageRequest(@NonNull MessageRequestViewModel viewModel);
    void    handleReaction(@NonNull ConversationMessage conversationMessage,
                           @NonNull ConversationReactionOverlay.OnActionSelectedListener onActionSelectedListener,
                           @NonNull SelectedConversationModel selectedConversationModel,
                           @NonNull ConversationReactionOverlay.OnHideListener onHideListener);
    void    onCursorChanged();
    void    onMessageWithErrorClicked(@NonNull MessageRecord messageRecord);
    void    onVoiceNotePause(@NonNull Uri uri);
    void    onVoiceNotePlay(@NonNull Uri uri, long messageId, double progress);
    void    onVoiceNoteResume(@NonNull Uri uri, long messageId);
    void    onVoiceNoteSeekTo(@NonNull Uri uri, double progress);
    void    onVoiceNotePlaybackSpeedChanged(@NonNull Uri uri, float speed);
    void    onRegisterVoiceNoteCallbacks(@NonNull Observer<VoiceNotePlaybackState> onPlaybackStartObserver);
    void    onUnregisterVoiceNoteCallbacks(@NonNull Observer<VoiceNotePlaybackState> onPlaybackStartObserver);
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
        ((ConversationAdapter) list.getAdapter()).onBindHeaderViewHolder(headerViewHolder, positionId, ConversationAdapter.HEADER_TYPE_POPOVER_DATE);
      }
    }
  }

  private class ConversationFragmentItemClickListener implements ItemClickListener {

    @Override
    public void onItemClick(MultiselectPart item) {
      if (actionMode != null) {
        ((ConversationAdapter) list.getAdapter()).toggleSelection(item);
        list.invalidateItemDecorations();

        if (getListAdapter().getSelectedItems().size() == 0) {
          actionMode.finish();
        } else {
          setCorrectActionModeMenuVisibility();
          actionMode.setTitle(calculateSelectedItemCount());
        }
      }
    }

    @Override
    public void onItemLongClick(View itemView, MultiselectPart item) {

      if (actionMode != null) return;

      MessageRecord messageRecord = item.getConversationMessage().getMessageRecord();;

      if (messageRecord.isSecure()                                        &&
          !messageRecord.isRemoteDelete()                                 &&
          !messageRecord.isUpdate()                                       &&
          !recipient.get().isBlocked()                                    &&
          !messageRequestViewModel.shouldShowMessageRequest()             &&
          (!recipient.get().isGroup() || recipient.get().isActiveGroup()) &&
          ((ConversationAdapter) list.getAdapter()).getSelectedItems().isEmpty())
      {
        multiselectItemDecoration.setFocusedItem(new MultiselectPart.Message(item.getConversationMessage()));
        list.invalidateItemDecorations();

        reactionsShade.setVisibility(View.VISIBLE);
        list.setLayoutFrozen(true);

        if (itemView instanceof ConversationItem) {
          Uri audioUri = getAudioUriForLongClick(messageRecord);
          if (audioUri != null) {
            listener.onVoiceNotePause(audioUri);
          }

          Bitmap videoBitmap          = null;
          int    childAdapterPosition = list.getChildAdapterPosition(itemView);

          GiphyMp4ProjectionPlayerHolder mp4Holder = null;
          if (childAdapterPosition != RecyclerView.NO_POSITION) {
            mp4Holder = giphyMp4ProjectionRecycler.getCurrentHolder(childAdapterPosition);
            if (mp4Holder != null && mp4Holder.isVisible()) {
              mp4Holder.pause();
              videoBitmap = mp4Holder.getBitmap();
              mp4Holder.hide();
            } else {
              mp4Holder = null;
            }
          }
          final GiphyMp4ProjectionPlayerHolder finalMp4Holder = mp4Holder;

          ConversationItem conversationItem = (ConversationItem) itemView;
          Bitmap           bitmap           = ConversationItemSelection.snapshotView(conversationItem, list, messageRecord, videoBitmap);

          View focusedView = listener.isKeyboardOpen() ? conversationItem.getRootView().findFocus() : null;

          final ConversationItemBodyBubble bodyBubble                = conversationItem.bodyBubble;
                SelectedConversationModel  selectedConversationModel = new SelectedConversationModel(bitmap,
                                                                                                     itemView.getX(),
                                                                                                     itemView.getY() + list.getTranslationY(),
                                                                                                     bodyBubble.getX(),
                                                                                                     bodyBubble.getY(),
                                                                                                     bodyBubble.getWidth(),
                                                                                                     audioUri,
                                                                                                     messageRecord.isOutgoing(),
                                                                                                     focusedView);

          bodyBubble.setVisibility(View.INVISIBLE);
          conversationItem.reactionsView.setVisibility(View.INVISIBLE);

          ViewUtil.hideKeyboard(requireContext(), conversationItem);

          boolean showScrollButtons = conversationViewModel.getShowScrollButtons();
          if (showScrollButtons) {
            conversationViewModel.setShowScrollButtons(false);
          }

          listener.handleReaction(item.getConversationMessage(),
                                  new ReactionsToolbarListener(item.getConversationMessage()),
                                  selectedConversationModel,
                                  new ConversationReactionOverlay.OnHideListener() {
                                    @Override public void startHide() {
                                      multiselectItemDecoration.hideShade(list);
                                      ViewUtil.fadeOut(reactionsShade, getResources().getInteger(R.integer.reaction_scrubber_hide_duration), View.GONE);
                                    }

                                    @Override public void onHide() {
                                      list.setLayoutFrozen(false);

                                      if (selectedConversationModel.getAudioUri() != null) {
                                        listener.onVoiceNoteResume(selectedConversationModel.getAudioUri(), messageRecord.getId());
                                      }

                                      WindowUtil.setLightStatusBarFromTheme(requireActivity());
                                      WindowUtil.setLightNavigationBarFromTheme(requireActivity());
                                      clearFocusedItem();

                                      if (finalMp4Holder != null) {
                                        finalMp4Holder.show();
                                        finalMp4Holder.resume();
                                      }

                                      bodyBubble.setVisibility(View.VISIBLE);
                                      conversationItem.reactionsView.setVisibility(View.VISIBLE);

                                      if (showScrollButtons) {
                                        conversationViewModel.setShowScrollButtons(true);
                                      }
                                    }
                                  });
        }
      } else {
        clearFocusedItem();
        ((ConversationAdapter) list.getAdapter()).toggleSelection(item);
        list.invalidateItemDecorations();

        actionMode = ((AppCompatActivity)getActivity()).startSupportActionMode(actionModeCallback);
      }
    }

    @Nullable private Uri getAudioUriForLongClick(@NonNull MessageRecord messageRecord) {
      VoiceNotePlaybackState playbackState = listener.getVoiceNoteMediaController().getVoiceNotePlaybackState().getValue();
      if (playbackState == null || !playbackState.isPlaying()) {
        return null;
      }

      if (!MessageRecordUtil.hasAudio(messageRecord) || !messageRecord.isMms()) {
        return null;
      }

      Uri messageUri = ((MmsMessageRecord) messageRecord).getSlideDeck().getAudioSlide().getUri();
      return playbackState.getUri().equals(messageUri) ? messageUri : null;
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
        return SignalDatabase.mmsSms().getQuotedMessagePosition(threadId,
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
        LongMessageFragment.create(messageId, isMms).show(getChildFragmentManager(), null);
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

          SignalDatabase.attachments().deleteAttachmentFilesForViewOnceMessage(messageRecord.getId());

          ApplicationDependencies.getViewOnceMessageManager().scheduleIfNecessary();

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
          SignalExecutors.BOUNDED.execute(() -> SignalDatabase.attachments().deleteAttachmentFilesForViewOnceMessage(messageRecord.getId()));
        }
      });
    }

    @Override
    public void onSharedContactDetailsClicked(@NonNull Contact contact, @NonNull View avatarTransitionView) {
      if (getContext() != null && getActivity() != null) {
        ViewCompat.setTransitionName(avatarTransitionView, "avatar");
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
    public void onReactionClicked(@NonNull MultiselectPart multiselectPart, long messageId, boolean isMms) {
      if (getParentFragment() == null) return;

      ReactionsBottomSheetDialogFragment.create(messageId, isMms).show(getParentFragmentManager(), null);
    }

    @Override
    public void onGroupMemberClicked(@NonNull RecipientId recipientId, @NonNull GroupId groupId) {
      if (getParentFragment() == null) return;

      RecipientBottomSheetDialogFragment.create(recipientId, groupId).show(getParentFragmentManager(), "BOTTOM");
    }

    @Override
    public void onMessageWithErrorClicked(@NonNull MessageRecord messageRecord) {
      listener.onMessageWithErrorClicked(messageRecord);
    }

    @Override
    public void onMessageWithRecaptchaNeededClicked(@NonNull MessageRecord messageRecord) {
      RecaptchaProofBottomSheetFragment.show(getChildFragmentManager());
    }

    @Override
    public void onIncomingIdentityMismatchClicked(@NonNull RecipientId recipientId) {
      SafetyNumberChangeDialog.show(getParentFragmentManager(), recipientId);
    }

    @Override
    public void onVoiceNotePause(@NonNull Uri uri) {
      listener.onVoiceNotePause(uri);
    }

    @Override
    public void onVoiceNotePlay(@NonNull Uri uri, long messageId, double progress) {
      listener.onVoiceNotePlay(uri, messageId, progress);
    }

    @Override
    public void onVoiceNoteSeekTo(@NonNull Uri uri, double progress) {
      listener.onVoiceNoteSeekTo(uri, progress);
    }

    @Override
    public void onVoiceNotePlaybackSpeedChanged(@NonNull Uri uri, float speed) {
      listener.onVoiceNotePlaybackSpeedChanged(uri, speed);
    }

    @Override
    public void onRegisterVoiceNoteCallbacks(@NonNull Observer<VoiceNotePlaybackState> onPlaybackStartObserver) {
      listener.onRegisterVoiceNoteCallbacks(onPlaybackStartObserver);
    }

    @Override
    public void onUnregisterVoiceNoteCallbacks(@NonNull Observer<VoiceNotePlaybackState> onPlaybackStartObserver) {
      listener.onUnregisterVoiceNoteCallbacks(onPlaybackStartObserver);
    }

    @Override
    public boolean onUrlClicked(@NonNull String url) {
      return CommunicationActions.handlePotentialGroupLinkUrl(requireActivity(), url) ||
             CommunicationActions.handlePotentialProxyLinkUrl(requireActivity(), url);
    }

    @Override
    public void onGroupMigrationLearnMoreClicked(@NonNull GroupMigrationMembershipChange membershipChange) {
      if (getParentFragment() == null) {
        return;
      }

      GroupsV1MigrationInfoBottomSheetDialogFragment.show(getParentFragmentManager(), membershipChange);
    }

    @Override
    public void onChatSessionRefreshLearnMoreClicked() {
      new AlertDialog.Builder(requireContext())
          .setView(R.layout.decryption_failed_dialog)
          .setPositiveButton(android.R.string.ok, (d, w) -> {
            d.dismiss();
          })
          .setNeutralButton(R.string.ConversationFragment_contact_us, (d, w) -> {
            startActivity(AppSettingsActivity.help(requireContext(), 0));
            d.dismiss();
          })
          .show();
    }

    @Override
    public void onBadDecryptLearnMoreClicked(@NonNull RecipientId author) {
      SimpleTask.run(getLifecycle(),
                     () -> Recipient.resolved(author).getDisplayName(requireContext()),
                     name -> BadDecryptLearnMoreDialog.show(getParentFragmentManager(), name, recipient.get().isGroup()));
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
                                              return ApplicationDependencies.getProtocolStore().aci().identities().getIdentityRecord(recipient.getId());
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

    @Override
    public void onEnableCallNotificationsClicked() {
      EnableCallNotificationSettingsDialog.fixAutomatically(requireContext());
      if (EnableCallNotificationSettingsDialog.shouldShow(requireContext())) {
        EnableCallNotificationSettingsDialog.show(getChildFragmentManager());
      } else {
        refreshList();
      }
    }

    @Override
    public void onPlayInlineContent(ConversationMessage conversationMessage) {
      getListAdapter().playInlineContent(conversationMessage);
    }

    @Override
    public void onInMemoryMessageClicked(@NonNull InMemoryMessageRecord messageRecord) {
      if (messageRecord instanceof InMemoryMessageRecord.NoGroupsInCommon) {
        boolean isGroup = ((InMemoryMessageRecord.NoGroupsInCommon) messageRecord).isGroup();
        new MaterialAlertDialogBuilder(requireContext(), R.style.Signal_ThemeOverlay_Dialog_Rounded)
            .setMessage(isGroup ? R.string.GroupsInCommonMessageRequest__none_of_your_contacts_or_people_you_chat_with_are_in_this_group
                                : R.string.GroupsInCommonMessageRequest__you_have_no_groups_in_common_with_this_person)
            .setNeutralButton(R.string.GroupsInCommonMessageRequest__about_message_requests, (d, w) -> CommunicationActions.openBrowserLink(requireContext(), getString(R.string.GroupsInCommonMessageRequest__support_article)))
            .setPositiveButton(R.string.GroupsInCommonMessageRequest__okay, null)
            .show();
      }
    }

    @Override
    public void onViewGroupDescriptionChange(@Nullable GroupId groupId, @NonNull String description, boolean isMessageRequestAccepted) {
      if (groupId != null) {
        GroupDescriptionDialog.show(getChildFragmentManager(), groupId, description, isMessageRequestAccepted);
      }
    }

    @Override
    public void onChangeNumberUpdateContact(@NonNull Recipient recipient) {
      startActivity(RecipientExporter.export(recipient).asAddContactIntent());
    }

    @Override
    public void onCallToAction(@NonNull String action) {

    }

    @Override
    public void onDonateClicked() {
      if (SignalStore.donationsValues().isLikelyASustainer()) {
        NavHostFragment navHostFragment = NavHostFragment.create(R.navigation.boosts);

        requireActivity().getSupportFragmentManager()
                         .beginTransaction()
                         .add(navHostFragment, "boost_nav")
                         .commitNow();
      } else {
        startActivity(AppSettingsActivity.subscriptions(requireContext()));
      }
    }
  }

  public void refreshList() {
    ConversationAdapter listAdapter = getListAdapter();
    if (listAdapter != null) {
      listAdapter.notifyDataSetChanged();
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
    Set<MultiselectPart> multiselectParts = conversationMessage.getMultiselectCollection().toSet();

    multiselectParts.stream().forEach(part -> {
      ((ConversationAdapter) list.getAdapter()).toggleSelection(part);
    });

    list.invalidateItemDecorations();

    actionMode = ((AppCompatActivity)getActivity()).startSupportActionMode(actionModeCallback);
  }

  private final class CheckExpirationDataObserver extends RecyclerView.AdapterDataObserver {
    @Override
    public void onItemRangeRemoved(int positionStart, int itemCount) {
      ConversationAdapter adapter = getListAdapter();
      if (adapter == null || actionMode == null) {
        return;
      }

      Set<MultiselectPart> selected = adapter.getSelectedItems();
      Set<MultiselectPart> expired  = new HashSet<>();

      for (final MultiselectPart multiselectPart : selected) {
        if (multiselectPart.isExpired()) {
          expired.add(multiselectPart);
        }
      }

      adapter.removeFromSelection(expired);

      if (adapter.getSelectedItems().isEmpty()) {
        actionMode.finish();
      } else {
        actionMode.setTitle(calculateSelectedItemCount());
      }
    }
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

    @Override
    public void onItemRangeChanged(int positionStart, int itemCount) {
      super.onItemRangeChanged(positionStart, itemCount);
      list.post(ConversationFragment.this::postMarkAsReadRequest);
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

  private class ReactionsToolbarListener implements ConversationReactionOverlay.OnActionSelectedListener {

    private final ConversationMessage conversationMessage;

    private ReactionsToolbarListener(@NonNull ConversationMessage conversationMessage) {
      this.conversationMessage = conversationMessage;
    }

    @Override
    public void onActionSelected(@NonNull ConversationReactionOverlay.Action action) {
      switch (action) {
        case REPLY:
          handleReplyMessage(conversationMessage);
          break;
        case FORWARD:
          handleForwardMessageParts(conversationMessage.getMultiselectCollection().toSet());
          break;
        case RESEND:
          handleResendMessage(conversationMessage.getMessageRecord());
          break;
        case DOWNLOAD:
          handleSaveAttachment((MediaMmsMessageRecord) conversationMessage.getMessageRecord());
          break;
        case COPY:
          handleCopyMessage(conversationMessage.getMultiselectCollection().toSet());
          break;
        case MULTISELECT:
          handleEnterMultiSelect(conversationMessage);
          break;
        case VIEW_INFO:
          handleDisplayDetails(conversationMessage);
          break;
        case DELETE:
          handleDeleteMessages(conversationMessage.getMultiselectCollection().toSet());
          break;
      }
    }
  }

  private class ActionModeCallback implements ActionMode.Callback {

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
      mode.setTitle(calculateSelectedItemCount());

      setCorrectActionModeMenuVisibility();
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
      list.invalidateItemDecorations();
      setBottomActionBarVisibility(false);
      actionMode = null;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
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

  private class ShadowScrollListener extends RecyclerView.OnScrollListener {
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

  private static final class TransitionListener implements Animator.AnimatorListener {

    private final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);

    TransitionListener(RecyclerView recyclerView) {
      animator.addUpdateListener(unused -> recyclerView.invalidate());
      animator.setDuration(100L);
    }

    @Override
    public void onAnimationStart(Animator animation) {
      animator.start();
    }

    @Override
    public void onAnimationEnd(Animator animation) {
      animator.end();
    }

    @Override
    public void onAnimationCancel(Animator animation) {
      // Do Nothing
    }

    @Override
    public void onAnimationRepeat(Animator animation) {
      // Do Nothing
    }
  }
}
