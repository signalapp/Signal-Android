/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.Browser
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentResultListener
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.ConversationLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar.Duration
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.Result
import org.signal.core.util.ThreadUtil
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.concurrent.ListenableFuture
import org.signal.core.util.concurrent.addTo
import org.signal.core.util.dp
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.signal.core.util.setActionItemTint
import org.signal.ringrtc.CallLinkRootKey
import org.thoughtcrime.securesms.BlockUnblockDialog
import org.thoughtcrime.securesms.GroupMembersDialog
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.MuteDialog
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.audio.AudioRecorder
import org.thoughtcrime.securesms.badges.gifts.OpenableGift
import org.thoughtcrime.securesms.badges.gifts.OpenableGiftItemDecoration
import org.thoughtcrime.securesms.badges.gifts.flow.GiftFlowActivity
import org.thoughtcrime.securesms.badges.gifts.viewgift.received.ViewReceivedGiftBottomSheet
import org.thoughtcrime.securesms.badges.gifts.viewgift.sent.ViewSentGiftBottomSheet
import org.thoughtcrime.securesms.components.AnimatingToggle
import org.thoughtcrime.securesms.components.ComposeText
import org.thoughtcrime.securesms.components.ConversationSearchBottomBar
import org.thoughtcrime.securesms.components.HidingLinearLayout
import org.thoughtcrime.securesms.components.InputAwareConstraintLayout
import org.thoughtcrime.securesms.components.InputPanel
import org.thoughtcrime.securesms.components.InsetAwareConstraintLayout
import org.thoughtcrime.securesms.components.ProgressCardDialogFragment
import org.thoughtcrime.securesms.components.ProgressCardDialogFragmentArgs
import org.thoughtcrime.securesms.components.ScrollToPositionDelegate
import org.thoughtcrime.securesms.components.SendButton
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.components.emoji.EmojiEventListener
import org.thoughtcrime.securesms.components.emoji.MediaKeyboard
import org.thoughtcrime.securesms.components.emoji.RecentEmojiPageModel
import org.thoughtcrime.securesms.components.location.SignalPlace
import org.thoughtcrime.securesms.components.mention.MentionAnnotation
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalBottomActionBar
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.DonateToSignalFragment
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.DonateToSignalType
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsActivity
import org.thoughtcrime.securesms.components.spoiler.SpoilerAnnotation
import org.thoughtcrime.securesms.components.voice.VoiceNoteDraft
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaControllerOwner
import org.thoughtcrime.securesms.components.voice.VoiceNotePlaybackState
import org.thoughtcrime.securesms.components.voice.VoiceNotePlayerView
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey.RecipientSearchKey
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.contactshare.ContactUtil
import org.thoughtcrime.securesms.contactshare.SharedContactDetailsActivity
import org.thoughtcrime.securesms.conversation.AttachmentKeyboardButton
import org.thoughtcrime.securesms.conversation.BadDecryptLearnMoreDialog
import org.thoughtcrime.securesms.conversation.ConversationAdapter
import org.thoughtcrime.securesms.conversation.ConversationBottomSheetCallback
import org.thoughtcrime.securesms.conversation.ConversationData
import org.thoughtcrime.securesms.conversation.ConversationHeaderView
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.conversation.ConversationIntents.ConversationScreenType
import org.thoughtcrime.securesms.conversation.ConversationItem
import org.thoughtcrime.securesms.conversation.ConversationItemSelection
import org.thoughtcrime.securesms.conversation.ConversationItemSwipeCallback
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.conversation.ConversationOptionsMenu
import org.thoughtcrime.securesms.conversation.ConversationReactionDelegate
import org.thoughtcrime.securesms.conversation.ConversationReactionOverlay
import org.thoughtcrime.securesms.conversation.ConversationReactionOverlay.OnActionSelectedListener
import org.thoughtcrime.securesms.conversation.ConversationReactionOverlay.OnHideListener
import org.thoughtcrime.securesms.conversation.ConversationSearchViewModel
import org.thoughtcrime.securesms.conversation.ConversationUpdateTick
import org.thoughtcrime.securesms.conversation.MarkReadHelper
import org.thoughtcrime.securesms.conversation.MenuState
import org.thoughtcrime.securesms.conversation.MessageSendType
import org.thoughtcrime.securesms.conversation.MessageStyler.getStyling
import org.thoughtcrime.securesms.conversation.ReenableScheduledMessagesDialogFragment
import org.thoughtcrime.securesms.conversation.ScheduleMessageContextMenu
import org.thoughtcrime.securesms.conversation.ScheduleMessageDialogCallback
import org.thoughtcrime.securesms.conversation.ScheduleMessageTimePickerBottomSheet
import org.thoughtcrime.securesms.conversation.ScheduleMessageTimePickerBottomSheet.Companion.showSchedule
import org.thoughtcrime.securesms.conversation.ScheduledMessagesBottomSheet
import org.thoughtcrime.securesms.conversation.ScheduledMessagesRepository
import org.thoughtcrime.securesms.conversation.SelectedConversationModel
import org.thoughtcrime.securesms.conversation.ShowAdminsBottomSheetDialog
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.conversation.colors.Colorizer
import org.thoughtcrime.securesms.conversation.colors.RecyclerViewColorizer
import org.thoughtcrime.securesms.conversation.drafts.DraftRepository
import org.thoughtcrime.securesms.conversation.drafts.DraftRepository.ShareOrDraftData
import org.thoughtcrime.securesms.conversation.drafts.DraftViewModel
import org.thoughtcrime.securesms.conversation.mutiselect.ConversationItemAnimator
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectItemDecoration
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectPart
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardBottomSheet
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.conversation.quotes.MessageQuotesBottomSheet
import org.thoughtcrime.securesms.conversation.ui.edit.EditMessageHistoryDialog
import org.thoughtcrime.securesms.conversation.ui.error.EnableCallNotificationSettingsDialog
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQuery
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQueryChangedListener
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQueryReplacement
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQueryResultsControllerV2
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQueryViewModelV2
import org.thoughtcrime.securesms.conversation.v2.computed.ConversationMessageComputeWorkers
import org.thoughtcrime.securesms.conversation.v2.data.ConversationMessageElement
import org.thoughtcrime.securesms.conversation.v2.groups.ConversationGroupCallViewModel
import org.thoughtcrime.securesms.conversation.v2.groups.ConversationGroupViewModel
import org.thoughtcrime.securesms.conversation.v2.items.ChatColorsDrawable
import org.thoughtcrime.securesms.conversation.v2.items.InteractiveConversationElement
import org.thoughtcrime.securesms.conversation.v2.keyboard.AttachmentKeyboardFragment
import org.thoughtcrime.securesms.database.DraftTable
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.database.model.InMemoryMessageRecord
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.Quote
import org.thoughtcrime.securesms.database.model.StickerRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.databinding.V2ConversationFragmentBinding
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.events.GroupCallPeekEvent
import org.thoughtcrime.securesms.events.ReminderUpdateEvent
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ItemDecoration
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4PlaybackController
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4PlaybackPolicy
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ProjectionPlayerHolder
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ProjectionRecycler
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupMigrationMembershipChange
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason
import org.thoughtcrime.securesms.groups.ui.GroupErrors
import org.thoughtcrime.securesms.groups.ui.LeaveGroupDialog
import org.thoughtcrime.securesms.groups.ui.invitesandrequests.ManagePendingAndRequestingMembersActivity
import org.thoughtcrime.securesms.groups.ui.invitesandrequests.invite.GroupLinkInviteFriendsBottomSheetDialogFragment
import org.thoughtcrime.securesms.groups.ui.managegroup.dialogs.GroupDescriptionDialog
import org.thoughtcrime.securesms.groups.ui.migration.GroupsV1MigrationInfoBottomSheetDialogFragment
import org.thoughtcrime.securesms.groups.ui.migration.GroupsV1MigrationSuggestionsDialog
import org.thoughtcrime.securesms.groups.v2.GroupBlockJoinRequestResult
import org.thoughtcrime.securesms.invites.InviteActions
import org.thoughtcrime.securesms.keyboard.KeyboardPage
import org.thoughtcrime.securesms.keyboard.KeyboardPagerFragment
import org.thoughtcrime.securesms.keyboard.KeyboardPagerViewModel
import org.thoughtcrime.securesms.keyboard.KeyboardUtil
import org.thoughtcrime.securesms.keyboard.emoji.EmojiKeyboardPageFragment
import org.thoughtcrime.securesms.keyboard.emoji.search.EmojiSearchFragment
import org.thoughtcrime.securesms.keyboard.gif.GifKeyboardPageFragment
import org.thoughtcrime.securesms.keyboard.sticker.StickerKeyboardPageFragment
import org.thoughtcrime.securesms.keyboard.sticker.StickerSearchDialogFragment
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.linkpreview.LinkPreviewViewModelV2
import org.thoughtcrime.securesms.longmessage.LongMessageFragment
import org.thoughtcrime.securesms.mediaoverview.MediaOverviewActivity
import org.thoughtcrime.securesms.mediapreview.MediaIntentFactory
import org.thoughtcrime.securesms.mediapreview.MediaPreviewV2Activity
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mediasend.MediaSendActivityResult
import org.thoughtcrime.securesms.messagedetails.MessageDetailsFragment
import org.thoughtcrime.securesms.messagerequests.MessageRequestRepository
import org.thoughtcrime.securesms.mms.AttachmentManager
import org.thoughtcrime.securesms.mms.AudioSlide
import org.thoughtcrime.securesms.mms.GifSlide
import org.thoughtcrime.securesms.mms.ImageSlide
import org.thoughtcrime.securesms.mms.MediaConstraints
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.mms.SlideDeck
import org.thoughtcrime.securesms.mms.SlideFactory
import org.thoughtcrime.securesms.mms.StickerSlide
import org.thoughtcrime.securesms.mms.VideoSlide
import org.thoughtcrime.securesms.notifications.v2.ConversationId
import org.thoughtcrime.securesms.payments.preferences.PaymentsActivity
import org.thoughtcrime.securesms.permissions.Permissions
import org.thoughtcrime.securesms.profiles.spoofing.ReviewCardDialogFragment
import org.thoughtcrime.securesms.providers.BlobProvider
import org.thoughtcrime.securesms.ratelimit.RecaptchaProofBottomSheetFragment
import org.thoughtcrime.securesms.ratelimit.RecaptchaRequiredEvent
import org.thoughtcrime.securesms.reactions.ReactionsBottomSheetDialogFragment
import org.thoughtcrime.securesms.reactions.any.ReactWithAnyEmojiBottomSheetDialogFragment
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientExporter
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment
import org.thoughtcrime.securesms.recipients.ui.disappearingmessages.RecipientDisappearingMessagesActivity
import org.thoughtcrime.securesms.registration.RegistrationNavigationActivity
import org.thoughtcrime.securesms.revealable.ViewOnceMessageActivity
import org.thoughtcrime.securesms.revealable.ViewOnceUtil
import org.thoughtcrime.securesms.safety.SafetyNumberBottomSheet
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.stickers.StickerEventListener
import org.thoughtcrime.securesms.stickers.StickerLocator
import org.thoughtcrime.securesms.stickers.StickerManagementActivity
import org.thoughtcrime.securesms.stickers.StickerPackInstallEvent
import org.thoughtcrime.securesms.stickers.StickerPackPreviewActivity
import org.thoughtcrime.securesms.stories.StoryViewerArgs
import org.thoughtcrime.securesms.stories.viewer.StoryViewerActivity
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.BubbleUtil
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.ContextUtil
import org.thoughtcrime.securesms.util.ConversationUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.Debouncer
import org.thoughtcrime.securesms.util.DeleteDialog
import org.thoughtcrime.securesms.util.Dialogs
import org.thoughtcrime.securesms.util.DrawableUtil
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.FullscreenHelper
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.MessageConstraintsUtil
import org.thoughtcrime.securesms.util.MessageConstraintsUtil.getEditMessageThresholdHours
import org.thoughtcrime.securesms.util.MessageConstraintsUtil.isValidEditMessageSend
import org.thoughtcrime.securesms.util.PlayStoreUtil
import org.thoughtcrime.securesms.util.SaveAttachmentUtil
import org.thoughtcrime.securesms.util.SignalLocalMetrics
import org.thoughtcrime.securesms.util.StorageUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.WindowUtil
import org.thoughtcrime.securesms.util.createActivityViewModel
import org.thoughtcrime.securesms.util.doAfterNextLayout
import org.thoughtcrime.securesms.util.fragments.requireListener
import org.thoughtcrime.securesms.util.getRecordQuoteType
import org.thoughtcrime.securesms.util.hasAudio
import org.thoughtcrime.securesms.util.hasGiftBadge
import org.thoughtcrime.securesms.util.hasNonTextSlide
import org.thoughtcrime.securesms.util.isValidReactionTarget
import org.thoughtcrime.securesms.util.savedStateViewModel
import org.thoughtcrime.securesms.util.viewModel
import org.thoughtcrime.securesms.util.views.Stub
import org.thoughtcrime.securesms.util.visible
import org.thoughtcrime.securesms.verify.VerifyIdentityActivity
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper
import org.thoughtcrime.securesms.wallpaper.ChatWallpaperDimLevelUtil
import java.util.Locale
import java.util.Optional
import java.util.concurrent.ExecutionException
import kotlin.time.Duration.Companion.milliseconds

/**
 * A single unified fragment for Conversations.
 */
class ConversationFragment :
  LoggingFragment(R.layout.v2_conversation_fragment),
  ReactWithAnyEmojiBottomSheetDialogFragment.Callback,
  ReactionsBottomSheetDialogFragment.Callback,
  EmojiKeyboardPageFragment.Callback,
  EmojiEventListener,
  GifKeyboardPageFragment.Host,
  StickerEventListener,
  StickerKeyboardPageFragment.Callback,
  MediaKeyboard.MediaKeyboardListener,
  EmojiSearchFragment.Callback,
  ScheduleMessageTimePickerBottomSheet.ScheduleCallback,
  ScheduleMessageDialogCallback,
  ConversationBottomSheetCallback,
  SafetyNumberBottomSheet.Callbacks,
  EnableCallNotificationSettingsDialog.Callback,
  MultiselectForwardBottomSheet.Callback {

  companion object {
    private val TAG = Log.tag(ConversationFragment::class.java)
    private const val ACTION_PINNED_SHORTCUT = "action_pinned_shortcut"
    private const val SAVED_STATE_IS_SEARCH_REQUESTED = "is_search_requested"
    private const val EMOJI_SEARCH_FRAGMENT_TAG = "EmojiSearchFragment"

    private const val SCROLL_HEADER_ANIMATION_DURATION: Long = 100L
    private const val SCROLL_HEADER_CLOSE_DELAY: Long = SCROLL_HEADER_ANIMATION_DURATION * 4
  }

  private val args: ConversationIntents.Args by lazy {
    ConversationIntents.Args.from(requireArguments())
  }

  private val conversationRecipientRepository: ConversationRecipientRepository by lazy {
    ConversationRecipientRepository(args.threadId)
  }

  private val messageRequestRepository: MessageRequestRepository by lazy {
    MessageRequestRepository(requireContext())
  }

  private val disposables = LifecycleDisposable()
  private val binding by ViewBinderDelegate(V2ConversationFragmentBinding::bind) { _binding ->
    _binding.conversationInputPanel.embeddedTextEditor.apply {
      setOnEditorActionListener(null)
      setCursorPositionChangedListener(null)
      setOnKeyListener(null)
      removeTextChangedListener(composeTextEventsListener)
      setStylingChangedListener(null)
      setOnClickListener(null)
      removeOnFocusChangeListener(composeTextEventsListener)
    }

    dataObserver?.let {
      adapter.unregisterAdapterDataObserver(it)
    }

    scrollListener?.let {
      _binding.conversationItemRecycler.removeOnScrollListener(it)
    }
    scrollListener = null

    _binding.conversationItemRecycler.adapter = null

    textDraftSaveDebouncer.clear()
  }

  private val viewModel: ConversationViewModel by viewModel {
    ConversationViewModel(
      threadId = args.threadId,
      requestedStartingPosition = args.startingPosition,
      repository = ConversationRepository(localContext = requireContext(), isInBubble = args.conversationScreenType == ConversationScreenType.BUBBLE),
      recipientRepository = conversationRecipientRepository,
      messageRequestRepository = messageRequestRepository,
      scheduledMessagesRepository = ScheduledMessagesRepository(),
      initialChatColors = args.chatColors
    )
  }

  private val linkPreviewViewModel: LinkPreviewViewModelV2 by savedStateViewModel {
    LinkPreviewViewModelV2(it, enablePlaceholder = false)
  }

  private val groupCallViewModel: ConversationGroupCallViewModel by viewModel {
    ConversationGroupCallViewModel(conversationRecipientRepository)
  }

  private val conversationGroupViewModel: ConversationGroupViewModel by viewModels(
    factoryProducer = {
      ConversationGroupViewModel.Factory(args.threadId, conversationRecipientRepository)
    }
  )

  private val messageRequestViewModel: MessageRequestViewModel by viewModel {
    MessageRequestViewModel(args.threadId, conversationRecipientRepository, messageRequestRepository)
  }

  private val draftViewModel: DraftViewModel by viewModel {
    DraftViewModel(threadId = args.threadId, repository = DraftRepository(conversationArguments = args))
  }

  private val searchViewModel: ConversationSearchViewModel by viewModel {
    ConversationSearchViewModel(getString(R.string.note_to_self))
  }

  private val keyboardPagerViewModel: KeyboardPagerViewModel by activityViewModels()

  private val stickerViewModel: StickerSuggestionsViewModel by viewModel {
    StickerSuggestionsViewModel()
  }

  private lateinit var inlineQueryViewModel: InlineQueryViewModelV2

  private val shareDataTimestampViewModel: ShareDataTimestampViewModel by activityViewModels()

  private val inlineQueryController: InlineQueryResultsControllerV2 by lazy {
    InlineQueryResultsControllerV2(
      this,
      inlineQueryViewModel,
      inputPanel,
      (requireView() as ViewGroup),
      composeText
    )
  }

  private val voiceNotePlayerListener: VoiceNotePlayerView.Listener by lazy {
    VoiceNotePlayerViewListener()
  }

  private val conversationTooltips = ConversationTooltips(this)
  private val colorizer = Colorizer()
  private val textDraftSaveDebouncer = Debouncer(500)
  private val recentEmojis: RecentEmojiPageModel by lazy { RecentEmojiPageModel(ApplicationDependencies.getApplication(), TextSecurePreferences.RECENT_STORAGE_KEY) }

  private lateinit var layoutManager: ConversationLayoutManager
  private lateinit var markReadHelper: MarkReadHelper
  private lateinit var giphyMp4ProjectionRecycler: GiphyMp4ProjectionRecycler
  private lateinit var addToContactsLauncher: ActivityResultLauncher<Intent>
  private lateinit var conversationActivityResultContracts: ConversationActivityResultContracts
  private lateinit var scrollToPositionDelegate: ScrollToPositionDelegate
  private lateinit var adapter: ConversationAdapterV2
  private lateinit var typingIndicatorAdapter: ConversationTypingIndicatorAdapter
  private lateinit var recyclerViewColorizer: RecyclerViewColorizer
  private lateinit var attachmentManager: AttachmentManager
  private lateinit var multiselectItemDecoration: MultiselectItemDecoration
  private lateinit var openableGiftItemDecoration: OpenableGiftItemDecoration
  private lateinit var threadHeaderMarginDecoration: ThreadHeaderMarginDecoration
  private lateinit var conversationItemDecorations: ConversationItemDecorations
  private lateinit var optionsMenuCallback: ConversationOptionsMenuCallback
  private lateinit var backPressedCallback: BackPressedDelegate

  private var animationsAllowed = false
  private var actionMode: ActionMode? = null
  private var pinnedShortcutReceiver: BroadcastReceiver? = null
  private var searchMenuItem: MenuItem? = null
  private var isSearchRequested: Boolean = false
  private var previousPages: Set<KeyboardPage>? = null
  private var reShowScheduleMessagesBar: Boolean = false
  private var composeTextEventsListener: ComposeTextEventsListener? = null
  private var dataObserver: DataObserver? = null
  private var menuProvider: ConversationOptionsMenu.Provider? = null
  private var scrollListener: ScrollListener? = null

  private val jumpAndPulseScrollStrategy = object : ScrollToPositionDelegate.ScrollStrategy {
    override fun performScroll(recyclerView: RecyclerView, layoutManager: LinearLayoutManager, position: Int, smooth: Boolean) {
      ScrollToPositionDelegate.JumpToPositionStrategy.performScroll(recyclerView, layoutManager, position, smooth)
      adapter.pulseAtPosition(position)
    }
  }

  private val motionEventRelay: MotionEventRelay by viewModels(ownerProducer = { requireActivity() })

  private val actionModeCallback = ActionModeCallback()

  private val container: InputAwareConstraintLayout
    get() = requireView() as InputAwareConstraintLayout

  private val inputPanel: InputPanel
    get() = binding.conversationInputPanel.root

  private val composeText: ComposeText
    get() = binding.conversationInputPanel.embeddedTextEditor

  private val sendButton: SendButton
    get() = binding.conversationInputPanel.sendButton

  private val sendEditButton: ImageButton
    get() = binding.conversationInputPanel.sendEditButton

  private val bottomActionBar: SignalBottomActionBar
    get() = binding.conversationBottomActionBar

  private val searchNav: ConversationSearchBottomBar
    get() = binding.conversationSearchBottomBar.root

  private val scheduledMessagesStub: Stub<View> by lazy { Stub(binding.scheduledMessagesStub) }

  private val reactionDelegate: ConversationReactionDelegate by lazy(LazyThreadSafetyMode.NONE) {
    val conversationReactionStub = Stub<ConversationReactionOverlay>(binding.conversationReactionScrubberStub)
    val delegate = ConversationReactionDelegate(conversationReactionStub)
    delegate.setOnReactionSelectedListener(OnReactionsSelectedListener())

    delegate
  }

  private lateinit var voiceMessageRecordingDelegate: VoiceMessageRecordingDelegate

  //region Android Lifecycle

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    SignalLocalMetrics.ConversationOpen.start()
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.toolbar.isBackInvokedCallbackEnabled = false

    disposables.bindTo(viewLifecycleOwner)
    FullscreenHelper(requireActivity()).showSystemUI()

    markReadHelper = MarkReadHelper(ConversationId.forConversation(args.threadId), requireContext(), viewLifecycleOwner)
    markReadHelper.ignoreViewReveals()

    inlineQueryViewModel = createActivityViewModel { InlineQueryViewModelV2(recipientRepository = conversationRecipientRepository) }

    attachmentManager = AttachmentManager(requireContext(), requireView(), AttachmentManagerListener())

    initializeConversationThreadUi()

    val conversationToolbarOnScrollHelper = ConversationToolbarOnScrollHelper(
      requireActivity(),
      binding.toolbarBackground,
      viewModel::wallpaperSnapshot,
      viewLifecycleOwner
    )
    conversationToolbarOnScrollHelper.attach(binding.conversationItemRecycler)
    presentWallpaper(args.wallpaper)
    presentChatColors(args.chatColors)
    presentConversationTitle(viewModel.recipientSnapshot)
    presentActionBarMenu()
    presentStoryRing()

    observeConversationThread()

    viewModel
      .inputReadyState
      .distinctUntilChanged()
      .subscribeBy(
        onNext = this::presentInputReadyState
      )
      .addTo(disposables)

    container.fragmentManager = childFragmentManager

    ToolbarDependentMarginListener(binding.toolbar)
    initializeMediaKeyboard()

    binding.conversationVideoContainer.setClipToOutline(true)

    SpoilerAnnotation.resetRevealedSpoilers()

    registerForResults()

    inputPanel.setMediaListener(InputPanelMediaListener())

    binding.conversationItemRecycler.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
      viewModel.onChatBoundsChanged(Rect(left, top, right, bottom))
    }

    binding.conversationItemRecycler.addItemDecoration(ChatColorsDrawable.ChatColorsItemDecoration)
  }

  override fun onViewStateRestored(savedInstanceState: Bundle?) {
    super.onViewStateRestored(savedInstanceState)

    isSearchRequested = savedInstanceState?.getBoolean(SAVED_STATE_IS_SEARCH_REQUESTED, false) ?: args.isWithSearchOpen
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)

    outState.putBoolean(SAVED_STATE_IS_SEARCH_REQUESTED, isSearchRequested)
  }

  override fun onResume() {
    super.onResume()

    WindowUtil.setLightNavigationBarFromTheme(requireActivity())
    WindowUtil.setLightStatusBarFromTheme(requireActivity())

    EventBus.getDefault().register(this)

    groupCallViewModel.peekGroupCall()

    if (!args.conversationScreenType.isInBubble) {
      ApplicationDependencies.getMessageNotifier().setVisibleThread(ConversationId.forConversation(args.threadId))
    }

    viewModel.updateIdentityRecordsInBackground()

    if (args.isFirstTimeInSelfCreatedGroup) {
      conversationGroupViewModel.checkJustSelfInGroup().subscribeBy(
        onSuccess = {
          GroupLinkInviteFriendsBottomSheetDialogFragment.show(childFragmentManager, it)
        }
      ).addTo(disposables)
    }

    conversationGroupViewModel.updateGroupStateIfNeeded()

    if (SignalStore.rateLimit().needsRecaptcha()) {
      RecaptchaProofBottomSheetFragment.show(childFragmentManager)
    }
  }

  override fun onPause() {
    super.onPause()

    ConversationUtil.refreshRecipientShortcuts()

    if (!args.conversationScreenType.isInBubble) {
      ApplicationDependencies.getMessageNotifier().clearVisibleThread()
    }

    if (activity?.isFinishing == true) {
      activity?.overridePendingTransition(R.anim.fade_scale_in, R.anim.slide_to_end)
    }

    inputPanel.onPause()

    viewModel.markLastSeen()

    EventBus.getDefault().unregister(this)
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    ToolbarDependentMarginListener(binding.toolbar)
    inlineQueryController.onOrientationChange(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
  }

  override fun onDestroyView() {
    super.onDestroyView()
    if (pinnedShortcutReceiver != null) {
      requireActivity().unregisterReceiver(pinnedShortcutReceiver)
    }
  }

  @Suppress("OVERRIDE_DEPRECATION")
  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
  }

  override fun startActivity(intent: Intent) {
    if (intent.getStringArrayExtra(Browser.EXTRA_APPLICATION_ID) != null) {
      intent.removeExtra(Browser.EXTRA_APPLICATION_ID)
    }

    try {
      super.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
      Log.w(TAG, e)
      toast(
        toastTextId = R.string.ConversationActivity_there_is_no_app_available_to_handle_this_link_on_your_device,
        toastDuration = Toast.LENGTH_LONG
      )
    }
  }

  //endregion

  //region Fragment callbacks and listeners

  override fun getConversationAdapterListener(): ConversationAdapter.ItemClickListener {
    return adapter.clickListener
  }

  override fun jumpToMessage(messageRecord: MessageRecord) {
    viewModel
      .moveToMessage(messageRecord)
      .subscribeBy {
        moveToPosition(it)
      }
      .addTo(disposables)
  }

  override fun onReactWithAnyEmojiDialogDismissed() {
    reactionDelegate.hide()
  }

  override fun onReactWithAnyEmojiSelected(emoji: String) {
    reactionDelegate.hide()
  }

  override fun onReactionsDialogDismissed() {
    clearFocusedItem()
  }

  override fun openEmojiSearch() {
    val fragment = childFragmentManager.findFragmentByTag(EMOJI_SEARCH_FRAGMENT_TAG)
    if (fragment == null) {
      childFragmentManager.commit {
        add(R.id.emoji_search_container, EmojiSearchFragment(), EMOJI_SEARCH_FRAGMENT_TAG)
      }
    }
  }

  override fun closeEmojiSearch() {
    val fragment = childFragmentManager.findFragmentByTag(EMOJI_SEARCH_FRAGMENT_TAG)
    if (fragment != null) {
      childFragmentManager.commit(allowStateLoss = true) {
        remove(fragment)
      }
    }
  }

  override fun onEmojiSelected(emoji: String?) {
    if (emoji != null) {
      inputPanel.onEmojiSelected(emoji)
      recentEmojis.onCodePointSelected(emoji)
    }
  }

  override fun onKeyEvent(keyEvent: KeyEvent?) {
    if (keyEvent != null) {
      inputPanel.onKeyEvent(keyEvent)
    }
  }

  override fun openStickerSearch() {
    StickerSearchDialogFragment.show(childFragmentManager)
  }

  override fun onStickerSelected(sticker: StickerRecord) {
    sendSticker(
      stickerRecord = sticker,
      clearCompose = false
    )
  }

  override fun onStickerManagementClicked() {
    startActivity(StickerManagementActivity.getIntent(requireContext()))
    container.hideInput()
  }

  override fun isMms(): Boolean {
    return false
  }

  override fun openGifSearch() {
    val recipientId = viewModel.recipientSnapshot?.id ?: return
    conversationActivityResultContracts.launchGifSearch(recipientId, composeText.textTrimmed)
  }

  override fun onGifSelectSuccess(blobUri: Uri, width: Int, height: Int) {
    setMedia(
      uri = blobUri,
      mediaType = SlideFactory.MediaType.from(BlobProvider.getMimeType(blobUri))!!,
      width = width,
      height = height,
      videoGif = true
    )
  }

  override fun onShown() {
    inputPanel.mediaKeyboardListener.onShown()
  }

  override fun onHidden() {
    inputPanel.mediaKeyboardListener.onHidden()
    closeEmojiSearch()
  }

  override fun onKeyboardChanged(page: KeyboardPage) {
    inputPanel.mediaKeyboardListener.onKeyboardChanged(page)
  }

  override fun onScheduleSend(scheduledTime: Long) {
    sendMessage(scheduledDate = scheduledTime)
  }

  override fun onSchedulePermissionsGranted(metricId: String?, scheduledDate: Long) {
    sendMessage(scheduledDate = scheduledDate)
  }

  override fun sendAnywayAfterSafetyNumberChangedInBottomSheet(destinations: List<RecipientSearchKey>) {
    Log.d(TAG, "onSendAnywayAfterSafetyNumberChange")
    viewModel
      .updateIdentityRecords()
      .subscribeBy(
        onError = { t -> Log.w(TAG, "Error sending", t) },
        onComplete = { sendMessage() }
      )
      .addTo(disposables)
  }

  override fun onMessageResentAfterSafetyNumberChangeInBottomSheet() {
    Log.d(TAG, "onMessageResentAfterSafetyNumberChange")
    viewModel.updateIdentityRecordsInBackground()
  }

  override fun onCanceled() = Unit

  override fun onCallNotificationSettingsDialogDismissed() {
    adapter.notifyDataSetChanged()
  }

  override fun onFinishForwardAction() {
    actionMode?.finish()
  }

  override fun onDismissForwardSheet() = Unit

  //endregion

  private fun observeConversationThread() {
    var firstRender = true
    disposables += viewModel
      .conversationThreadState
      .subscribeOn(Schedulers.io())
      .doOnSuccess { state ->
        updateMessageRequestAcceptedState(state.meta.messageRequestData.isMessageRequestAccepted)
        SignalLocalMetrics.ConversationOpen.onDataLoaded()
        conversationItemDecorations.setFirstUnreadCount(state.meta.unreadCount)
        colorizer.onGroupMembershipChanged(state.meta.groupMemberAcis)
      }
      .observeOn(AndroidSchedulers.mainThread())
      .doOnSuccess { state ->
        moveToStartPosition(state.meta)
      }
      .flatMapObservable { it.items.data }
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy(onNext = {
        if (firstRender) {
          SignalLocalMetrics.ConversationOpen.onDataPostedToMain()
        }

        adapter.submitList(it) {
          scrollToPositionDelegate.notifyListCommitted()
          conversationItemDecorations.currentItems = it

          if (firstRender) {
            firstRender = false
            binding.conversationItemRecycler.doAfterNextLayout {
              SignalLocalMetrics.ConversationOpen.onRenderFinished()
              doAfterFirstRender()
            }
          }
        }
      })
  }

  private fun doAfterFirstRender() {
    Log.d(TAG, "doAfterFirstRender")

    if (!isAdded || view == null) {
      Log.w(TAG, "Bailing, fragment no longer added")
      return
    }

    activity?.supportStartPostponedEnterTransition()

    backPressedCallback = BackPressedDelegate()
    requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)

    menuProvider?.afterFirstRenderMode = true

    viewLifecycleOwner.lifecycle.addObserver(LastScrolledPositionUpdater(adapter, layoutManager, viewModel))

    disposables += viewModel.recipient
      .observeOn(AndroidSchedulers.mainThread())
      .distinctUntilChanged { r1, r2 -> r1 === r2 || r1.hasSameContent(r2) }
      .subscribeBy(onNext = this::onRecipientChanged)

    disposables += viewModel.scrollButtonState
      .subscribeBy(onNext = this::presentScrollButtons)

    disposables += viewModel
      .groupMemberServiceIds
      .subscribeBy(onNext = {
        colorizer.onGroupMembershipChanged(it)
        adapter.updateNameColors()
      })

    val disabledInputListener = DisabledInputListener()
    binding.conversationDisabledInput.listener = disabledInputListener

    val sendButtonListener = SendButtonListener()
    composeTextEventsListener = ComposeTextEventsListener()

    composeText.apply {
      setOnEditorActionListener(sendButtonListener)

      setCursorPositionChangedListener(composeTextEventsListener)
      setOnKeyListener(composeTextEventsListener)
      addTextChangedListener(composeTextEventsListener)
      setStylingChangedListener(composeTextEventsListener)
      setOnClickListener(composeTextEventsListener)
      onFocusChangeListener = composeTextEventsListener
    }

    sendButton.apply {
      setPopupContainer(binding.root)
      setOnClickListener(sendButtonListener)
      setScheduledSendListener(sendButtonListener)
      isEnabled = true
    }

    sendEditButton.setOnClickListener { handleSendEditMessage() }

    val attachListener = { _: View ->
      container.toggleInput(AttachmentKeyboardFragmentCreator, composeText)
    }
    binding.conversationInputPanel.attachButton.setOnClickListener(attachListener)
    binding.conversationInputPanel.inlineAttachmentButton.setOnClickListener(attachListener)

    presentGroupCallJoinButton()

    binding.scrollToBottom.setOnClickListener {
      binding.conversationItemRecycler.stopScroll()
      scrollToPositionDelegate.resetScrollPosition()
    }

    binding.scrollToMention.setOnClickListener {
      binding.conversationItemRecycler.stopScroll()
      scrollToNextMention()
    }

    dataObserver = DataObserver()
    adapter.registerAdapterDataObserver(dataObserver!!)

    val keyboardEvents = KeyboardEvents()
    container.addInputListener(keyboardEvents)
    container.addKeyboardStateListener(keyboardEvents)
    requireActivity()
      .onBackPressedDispatcher
      .addCallback(
        viewLifecycleOwner,
        keyboardEvents
      )

    childFragmentManager.setFragmentResultListener(AttachmentKeyboardFragment.RESULT_KEY, viewLifecycleOwner, AttachmentKeyboardFragmentListener())
    motionEventRelay.setDrain(MotionEventRelayDrain(this))

    voiceMessageRecordingDelegate = VoiceMessageRecordingDelegate(
      this,
      AudioRecorder(requireContext(), inputPanel),
      VoiceMessageRecordingSessionCallbacks()
    )

    binding.conversationBanner.listener = ConversationBannerListener()
    viewModel
      .reminder
      .subscribeBy { reminder ->
        if (reminder.isPresent) {
          binding.conversationBanner.showReminder(reminder.get())
        } else {
          binding.conversationBanner.clearReminder()
        }
      }
      .addTo(disposables)

    viewModel
      .identityRecordsObservable
      .distinctUntilChanged()
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy { presentIdentityRecordsState(it) }
      .addTo(disposables)

    viewModel
      .getRequestReviewState()
      .subscribeBy { presentRequestReviewState(it) }
      .addTo(disposables)

    ConversationItemSwipeCallback(
      SwipeAvailabilityProvider(),
      this::handleReplyToMessage
    ).attachToRecyclerView(binding.conversationItemRecycler)

    draftViewModel.loadShareOrDraftData(shareDataTimestampViewModel.timestamp)
      .subscribeBy { data -> handleShareOrDraftData(data) }
      .addTo(disposables)

    disposables.add(
      draftViewModel
        .state
        .distinctUntilChanged { previous, next -> previous.voiceNoteDraft == next.voiceNoteDraft }
        .subscribe {
          inputPanel.voiceNoteDraft = it.voiceNoteDraft
          updateToggleButtonState()
        }
    )

    initializeSearch()
    initializeLinkPreviews()
    initializeStickerSuggestions()
    initializeInlineSearch()

    inputPanel.setListener(InputPanelListener())

    viewModel
      .getScheduledMessagesCount()
      .subscribeBy { count -> handleScheduledMessagesCountChange(count) }
      .addTo(disposables)

    presentTypingIndicator()

    getVoiceNoteMediaController().finishPostpone()

    getVoiceNoteMediaController()
      .voiceNotePlayerViewState
      .observe(viewLifecycleOwner) { state: Optional<VoiceNotePlayerView.State> ->
        if (state.isPresent) {
          binding.conversationBanner.showVoiceNotePlayer(state.get(), voiceNotePlayerListener)
        } else {
          binding.conversationBanner.clearVoiceNotePlayer()
        }
      }

    getVoiceNoteMediaController().voiceNotePlaybackState.observe(viewLifecycleOwner, inputPanel.playbackStateObserver)

    val conversationUpdateTick = ConversationUpdateTick {
      disposables += ConversationMessageComputeWorkers.recomputeFormattedDate(
        requireContext(),
        adapter.currentList.filterIsInstance<ConversationMessageElement>()
      ).observeOn(AndroidSchedulers.mainThread()).subscribeBy { adapter.updateTimestamps() }
    }

    viewLifecycleOwner.lifecycle.addObserver(conversationUpdateTick)

    if (args.conversationScreenType.isInPopup) {
      composeText.requestFocus()
      binding.conversationInputPanel.quickAttachmentToggle.disable()
    }
  }

  private fun initializeInlineSearch() {
    inlineQueryController.onOrientationChange(resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)

    composeText.apply {
      setInlineQueryChangedListener(object : InlineQueryChangedListener {
        override fun onQueryChanged(inlineQuery: InlineQuery) {
          inlineQueryViewModel.onQueryChange(inlineQuery)
        }
      })

      setMentionValidator { annotations ->
        val recipient = viewModel.recipientSnapshot ?: return@setMentionValidator annotations

        val validIds = recipient.participantIds
          .map { MentionAnnotation.idToMentionAnnotationValue(it) }
          .toSet()

        annotations.filterNot { validIds.contains(it.value) }
      }
    }

    inlineQueryViewModel
      .selection
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { r: InlineQueryReplacement -> composeText.replaceText(r) }
      .addTo(disposables)
  }

  private fun presentTypingIndicator() {
    typingIndicatorAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
      override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
        if (positionStart == 0 && itemCount == 1 && layoutManager.findFirstCompletelyVisibleItemPosition() == 0) {
          scrollToPositionDelegate.resetScrollPosition()
        }
      }
    })

    ApplicationDependencies.getTypingStatusRepository().getTypists(args.threadId).observe(viewLifecycleOwner) {
      val recipient = viewModel.recipientSnapshot ?: return@observe

      typingIndicatorAdapter.setState(
        ConversationTypingIndicatorAdapter.State(
          typists = it.typists,
          isGroupThread = recipient.isGroup,
          hasWallpaper = recipient.hasWallpaper(),
          isReplacedByIncomingMessage = it.isReplacedByIncomingMessage
        )
      )
    }
  }

  private fun presentStoryRing() {
    if (SignalStore.storyValues().isFeatureDisabled) {
      return
    }

    disposables += viewModel.storyRingState.subscribeBy {
      binding.conversationTitleView.conversationTitleView.setStoryRingFromState(it)
    }

    binding.conversationTitleView.conversationTitleView.setOnStoryRingClickListener {
      val recipient: Recipient = viewModel.recipientSnapshot ?: return@setOnStoryRingClickListener
      val args = StoryViewerArgs.Builder(recipient.id, recipient.shouldHideStory())
        .isFromQuote(true)
        .build()

      startActivity(StoryViewerActivity.createIntent(requireContext(), args))
    }
  }

  private fun presentInputReadyState(inputReadyState: InputReadyState) {
    presentConversationTitle(inputReadyState.conversationRecipient)

    val disabledInputView = binding.conversationDisabledInput

    var inputDisabled = true
    when {
      inputReadyState.isClientExpired || inputReadyState.isUnauthorized -> disabledInputView.showAsExpiredOrUnauthorized(inputReadyState.isClientExpired, inputReadyState.isUnauthorized)
      !inputReadyState.messageRequestState.isAccepted -> disabledInputView.showAsMessageRequest(inputReadyState.conversationRecipient, inputReadyState.messageRequestState)
      inputReadyState.isActiveGroup == false -> disabledInputView.showAsNoLongerAMember()
      inputReadyState.isRequestingMember == true -> disabledInputView.showAsRequestingMember()
      inputReadyState.isAnnouncementGroup == true && inputReadyState.isAdmin == false -> disabledInputView.showAsAnnouncementGroupAdminsOnly()
      inputReadyState.conversationRecipient.isReleaseNotes -> disabledInputView.showAsReleaseNotesChannel(inputReadyState.conversationRecipient)
      inputReadyState.shouldShowInviteToSignal() -> disabledInputView.showAsInviteToSignal(requireContext(), inputReadyState.conversationRecipient)
      else -> inputDisabled = false
    }

    inputPanel.setHideForMessageRequestState(inputDisabled)

    if (inputDisabled) {
      WindowUtil.setNavigationBarColor(requireActivity(), disabledInputView.color)
    } else {
      disabledInputView.clear()
    }

    composeText.setMessageSendType(MessageSendType.SignalMessageSendType)
  }

  private fun presentIdentityRecordsState(identityRecordsState: IdentityRecordsState) {
    if (!identityRecordsState.isGroup) {
      binding.conversationTitleView.root.setVerified(identityRecordsState.isVerified)
    }

    if (identityRecordsState.isUnverified) {
      binding.conversationBanner.showUnverifiedBanner(identityRecordsState.identityRecords)
    } else {
      binding.conversationBanner.clearUnverifiedBanner()
    }
  }

  private fun presentRequestReviewState(requestReviewState: RequestReviewState) {
    if (requestReviewState.shouldShowReviewBanner()) {
      binding.conversationBanner.showReviewBanner(requestReviewState)
    } else {
      binding.conversationBanner.clearRequestReview()
    }
  }

  private fun setMedia(uri: Uri, mediaType: SlideFactory.MediaType, width: Int = 0, height: Int = 0, borderless: Boolean = false, videoGif: Boolean = false) {
    val recipientId: RecipientId = viewModel.recipientSnapshot?.id ?: return

    if (mediaType == SlideFactory.MediaType.VCARD) {
      conversationActivityResultContracts.launchContactShareEditor(uri, viewModel.recipientSnapshot!!.chatColors)
    } else if (mediaType == SlideFactory.MediaType.IMAGE || mediaType == SlideFactory.MediaType.GIF || mediaType == SlideFactory.MediaType.VIDEO) {
      val mimeType = MediaUtil.getMimeType(requireContext(), uri) ?: mediaType.toFallbackMimeType()
      val media = Media(
        uri,
        mimeType,
        0,
        width,
        height,
        0,
        0,
        borderless,
        videoGif,
        Optional.empty(),
        Optional.empty(),
        Optional.empty()
      )
      conversationActivityResultContracts.launchMediaEditor(listOf(media), recipientId, composeText.textTrimmed)
    } else {
      attachmentManager.setMedia(Glide.with(this), uri, mediaType, MediaConstraints.getPushMediaConstraints(), width, height)
    }
  }

  private fun calculateCharactersRemaining() {
    val messageBody: String = binding.conversationInputPanel.embeddedTextEditor.textTrimmed.toString()
    val charactersLeftView: TextView = binding.conversationInputSpaceLeft
    val characterState = MessageSendType.SignalMessageSendType.calculateCharacters(messageBody)

    if (characterState.charactersRemaining <= 15 || characterState.messagesSpent > 1) {
      charactersLeftView.text = String.format(
        Locale.getDefault(),
        "%d/%d (%d)",
        characterState.charactersRemaining,
        characterState.maxTotalMessageSize,
        characterState.messagesSpent
      )
      charactersLeftView.visibility = View.VISIBLE
    } else {
      charactersLeftView.visibility = View.GONE
    }
  }

  private fun registerForResults() {
    addToContactsLauncher = registerForActivityResult(AddToContactsContract()) {}
    conversationActivityResultContracts = ConversationActivityResultContracts(this, ActivityResultCallbacks())
  }

  private fun onRecipientChanged(recipient: Recipient) {
    presentWallpaper(recipient.wallpaper)
    presentConversationTitle(recipient)
    presentChatColors(recipient.chatColors)
    invalidateOptionsMenu()

    updateMessageRequestAcceptedState(!viewModel.hasMessageRequestState)
  }

  private fun updateMessageRequestAcceptedState(isMessageRequestAccepted: Boolean) {
    if (binding.conversationItemRecycler.isInLayout) {
      binding.conversationItemRecycler.doAfterNextLayout {
        adapter.setMessageRequestIsAccepted(isMessageRequestAccepted)
      }
    } else {
      adapter.setMessageRequestIsAccepted(isMessageRequestAccepted)
    }
  }

  private fun invalidateOptionsMenu() {
    if (searchMenuItem?.isActionViewExpanded != true || !isSearchRequested) {
      binding.toolbar.invalidateMenu()
    }
  }

  private fun presentActionBarMenu() {
    if (!args.conversationScreenType.isInPopup) {
      optionsMenuCallback = ConversationOptionsMenuCallback()
      menuProvider = ConversationOptionsMenu.Provider(optionsMenuCallback, disposables)
      binding.toolbar.addMenuProvider(menuProvider!!)
      invalidateOptionsMenu()
    }

    when (args.conversationScreenType) {
      ConversationScreenType.NORMAL -> presentNavigationIconForNormal()
      ConversationScreenType.BUBBLE,
      ConversationScreenType.POPUP -> presentNavigationIconForBubble()
    }
  }

  private fun presentNavigationIconForNormal() {
    binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_left_24)
    binding.toolbar.setNavigationContentDescription(R.string.ConversationFragment__content_description_back_button)
    binding.toolbar.setNavigationOnClickListener {
      requireActivity().finishAfterTransition()
    }
  }

  private fun presentNavigationIconForBubble() {
    binding.toolbar.navigationIcon = DrawableUtil.tint(
      ContextUtil.requireDrawable(requireContext(), R.drawable.ic_notification),
      ContextCompat.getColor(requireContext(), R.color.signal_accent_primary)
    )

    binding.toolbar.setNavigationContentDescription(R.string.ConversationFragment__content_description_launch_signal_button)

    binding.toolbar.setNavigationOnClickListener {
      startActivity(MainActivity.clearTop(requireContext()))
    }
  }

  private fun presentConversationTitle(recipient: Recipient?) {
    if (recipient == null) {
      return
    }

    val titleView = binding.conversationTitleView.root

    titleView.setTitle(Glide.with(this), recipient)
    if (recipient.expiresInSeconds > 0) {
      titleView.showExpiring(recipient)
    } else {
      titleView.clearExpiring()
    }

    if (!args.conversationScreenType.isInPopup) {
      titleView.setOnClickListener {
        optionsMenuCallback.handleConversationSettings()
      }
    }
  }

  private fun presentWallpaper(chatWallpaper: ChatWallpaper?) {
    if (chatWallpaper != null) {
      chatWallpaper.loadInto(binding.conversationWallpaper)
      ChatWallpaperDimLevelUtil.applyDimLevelForNightMode(binding.conversationWallpaperDim, chatWallpaper)
    } else {
      binding.conversationWallpaperDim.visible = false
    }

    val toolbarTint = ContextCompat.getColor(
      requireContext(),
      if (chatWallpaper != null) {
        R.color.signal_colorNeutralInverse
      } else {
        R.color.signal_colorOnSurface
      }
    )

    binding.toolbar.setTitleTextColor(toolbarTint)
    binding.toolbar.setActionItemTint(toolbarTint)

    val wallpaperEnabled = chatWallpaper != null
    binding.conversationWallpaper.visible = wallpaperEnabled
    binding.scrollToBottom.setWallpaperEnabled(wallpaperEnabled)
    binding.scrollToMention.setWallpaperEnabled(wallpaperEnabled)
    binding.conversationDisabledInput.setWallpaperEnabled(wallpaperEnabled)
    inputPanel.setWallpaperEnabled(wallpaperEnabled)

    val stateChanged = adapter.onHasWallpaperChanged(wallpaperEnabled)
    conversationItemDecorations.hasWallpaper = wallpaperEnabled
    if (stateChanged) {
      binding.conversationItemRecycler.invalidateItemDecorations()
    }

    val navColor = if (wallpaperEnabled) {
      R.color.conversation_navigation_wallpaper
    } else {
      R.color.signal_colorBackground
    }

    binding.scrollDateHeader.setBackgroundResource(
      if (wallpaperEnabled) R.drawable.sticky_date_header_background_wallpaper else R.drawable.sticky_date_header_background
    )

    binding.scrollDateHeader.setTextColor(
      ContextCompat.getColor(
        requireContext(),
        if (wallpaperEnabled) R.color.sticky_header_foreground_wallpaper else R.color.signal_colorOnSurfaceVariant
      )
    )

    WindowUtil.setNavigationBarColor(requireActivity(), ContextCompat.getColor(requireContext(), navColor))
  }

  private fun presentChatColors(chatColors: ChatColors) {
    recyclerViewColorizer.setChatColors(chatColors)
    binding.scrollToMention.setUnreadCountBackgroundTint(chatColors.asSingleColor())
    binding.scrollToBottom.setUnreadCountBackgroundTint(chatColors.asSingleColor())
    binding.conversationInputPanel.buttonToggle.background.apply {
      colorFilter = PorterDuffColorFilter(chatColors.asSingleColor(), PorterDuff.Mode.MULTIPLY)
      invalidateSelf()
    }
  }

  private fun presentScrollButtons(scrollButtonState: ConversationScrollButtonState) {
    Log.d(TAG, "Update scroll state $scrollButtonState")
    binding.scrollToBottom.setUnreadCount(scrollButtonState.unreadCount)
    binding.scrollToMention.isShown = scrollButtonState.hasMentions && scrollButtonState.showScrollButtons
    binding.scrollToBottom.isShown = scrollButtonState.showScrollButtons
  }

  private fun presentGroupCallJoinButton() {
    binding.conversationGroupCallJoin.setOnClickListener {
      handleVideoCall()
    }

    disposables += groupCallViewModel
      .state
      .distinctUntilChanged()
      .subscribeBy {
        binding.conversationGroupCallJoin.visible = it.ongoingCall
        binding.conversationGroupCallJoin.setText(if (it.hasCapacity) R.string.ConversationActivity_join else R.string.ConversationActivity_full)
        invalidateOptionsMenu()
      }
  }

  private fun handleVideoCall() {
    val recipient = viewModel.recipientSnapshot ?: return
    if (!recipient.isGroup) {
      CommunicationActions.startVideoCall(this, recipient)
      return
    }

    val hasActiveGroupCall: Single<Boolean> = groupCallViewModel.state.map { it.ongoingCall }.firstOrError()
    val isNonAdminInAnnouncementGroup: Boolean = conversationGroupViewModel.isNonAdminInAnnouncementGroup()
    val cannotCreateGroupCall = hasActiveGroupCall.map { active ->
      recipient to (recipient.isPushV2Group && !active && isNonAdminInAnnouncementGroup)
    }

    disposables += cannotCreateGroupCall
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { (recipient, notAllowed) ->
        if (notAllowed) {
          ConversationDialogs.displayCannotStartGroupCallDueToPermissionsDialog(requireContext())
        } else {
          CommunicationActions.startVideoCall(this, recipient)
        }
      }
  }

  private fun handleBlockJoinRequest(recipient: Recipient) {
    disposables += conversationGroupViewModel.blockJoinRequests(recipient).subscribeBy { result ->
      if (result.isFailure()) {
        val failureReason = GroupErrors.getUserDisplayMessage((result as GroupBlockJoinRequestResult.Failure).reason)
        Toast.makeText(requireContext(), failureReason, Toast.LENGTH_SHORT).show()
      } else {
        Toast.makeText(requireContext(), R.string.ConversationFragment__blocked, Toast.LENGTH_SHORT).show()
      }
    }
  }

  private fun handleShareOrDraftData(data: ShareOrDraftData) {
    shareDataTimestampViewModel.timestamp = args.shareDataTimestamp

    when (data) {
      is ShareOrDraftData.SendKeyboardImage -> sendMessageWithoutComposeInput(slide = data.slide, clearCompose = false)
      is ShareOrDraftData.SendSticker -> sendMessageWithoutComposeInput(slide = data.slide, clearCompose = true)
      is ShareOrDraftData.SetText -> composeText.setDraftText(data.text)
      is ShareOrDraftData.SetLocation -> attachmentManager.setLocation(data.location, MediaConstraints.getPushMediaConstraints())
      is ShareOrDraftData.SetEditMessage -> {
        composeText.setDraftText(data.draftText)
        inputPanel.enterEditMessageMode(Glide.with(this), data.messageEdit, true)
      }

      is ShareOrDraftData.SetMedia -> {
        composeText.setDraftText(data.text)
        setMedia(data.media, data.mediaType)
      }

      is ShareOrDraftData.SetQuote -> {
        composeText.setDraftText(data.draftText)
        handleReplyToMessage(data.quote)
      }

      is ShareOrDraftData.StartSendMedia -> {
        val recipientId = viewModel.recipientSnapshot?.id ?: return
        conversationActivityResultContracts.launchMediaEditor(data.mediaList, recipientId, data.text)
      }
    }
  }

  private fun handleScheduledMessagesCountChange(count: Int) {
    if (count <= 0) {
      scheduledMessagesStub.visibility = View.GONE
      reShowScheduleMessagesBar = false
    } else {
      scheduledMessagesStub.get().apply {
        visibility = View.VISIBLE

        findViewById<View>(R.id.scheduled_messages_show_all)
          .setOnClickListener {
            val recipient = viewModel.recipientSnapshot ?: return@setOnClickListener
            container.runAfterAllHidden(composeText) {
              ScheduledMessagesBottomSheet.show(childFragmentManager, args.threadId, recipient.id)
            }
          }

        findViewById<TextView>(R.id.scheduled_messages_text).text = resources.getQuantityString(R.plurals.conversation_scheduled_messages_bar__number_of_messages, count, count)
      }
      reShowScheduleMessagesBar = true
    }
  }

  private fun handleSendEditMessage() {
    if (!inputPanel.inEditMessageMode()) {
      Log.w(TAG, "Not in edit message mode, unknown state, forcing re-exit")
      inputPanel.exitEditMessageMode()
      return
    }

    if (SignalStore.uiHints().hasNotSeenEditMessageBetaAlert()) {
      Dialogs.showEditMessageBetaDialog(requireContext()) { handleSendEditMessage() }
      return
    }

    val editMessage = inputPanel.editMessage
    if (editMessage == null) {
      Log.w(TAG, "No edit message found, forcing exit")
      inputPanel.exitEditMessageMode()
      return
    }

    if (!MessageConstraintsUtil.isWithinMaxEdits(editMessage)) {
      Log.i(TAG, "Too many edits to the message")
      Dialogs.showAlertDialog(requireContext(), null, resources.getQuantityString(R.plurals.ConversationActivity_edit_message_too_many_edits, MessageConstraintsUtil.MAX_EDIT_COUNT, MessageConstraintsUtil.MAX_EDIT_COUNT))
      return
    }

    if (!isValidEditMessageSend(editMessage, System.currentTimeMillis())) {
      Log.i(TAG, "Edit message no longer valid")
      val editDurationHours = getEditMessageThresholdHours()
      Dialogs.showAlertDialog(requireContext(), null, resources.getQuantityString(R.plurals.ConversationActivity_edit_message_too_old, editDurationHours, editDurationHours))
      return
    }

    sendMessage()
  }

  private fun getVoiceNoteMediaController() = requireListener<VoiceNoteMediaControllerOwner>().voiceNoteMediaController

  private fun initializeConversationThreadUi() {
    layoutManager = ConversationLayoutManager(requireContext())
    binding.conversationItemRecycler.setHasFixedSize(false)
    binding.conversationItemRecycler.layoutManager = layoutManager
    scrollListener = ScrollListener()
    binding.conversationItemRecycler.addOnScrollListener(scrollListener!!)

    adapter = ConversationAdapterV2(
      lifecycleOwner = viewLifecycleOwner,
      requestManager = Glide.with(this),
      clickListener = ConversationItemClickListener(),
      hasWallpaper = args.wallpaper != null,
      colorizer = colorizer,
      startExpirationTimeout = viewModel::startExpirationTimeout,
      chatColorsDataProvider = viewModel::chatColorsSnapshot
    )

    typingIndicatorAdapter = ConversationTypingIndicatorAdapter(Glide.with(this))

    scrollToPositionDelegate = ScrollToPositionDelegate(
      recyclerView = binding.conversationItemRecycler,
      canJumpToPosition = adapter::canJumpToPosition
    )

    adapter.setPagingController(viewModel.pagingController)

    recyclerViewColorizer = RecyclerViewColorizer(binding.conversationItemRecycler)
    recyclerViewColorizer.setChatColors(args.chatColors)

    binding.conversationItemRecycler.adapter = ConcatAdapter(typingIndicatorAdapter, adapter)
    multiselectItemDecoration = MultiselectItemDecoration(
      requireContext()
    ) { viewModel.wallpaperSnapshot }

    openableGiftItemDecoration = OpenableGiftItemDecoration(requireContext())
    binding.conversationItemRecycler.addItemDecoration(openableGiftItemDecoration)

    binding.conversationItemRecycler.addItemDecoration(multiselectItemDecoration)
    viewLifecycleOwner.lifecycle.addObserver(multiselectItemDecoration)

    giphyMp4ProjectionRecycler = initializeGiphyMp4()

    val layoutTransitionListener = BubbleLayoutTransitionListener(binding.conversationItemRecycler)
    viewLifecycleOwner.lifecycle.addObserver(layoutTransitionListener)

    binding.conversationItemRecycler.itemAnimator = ConversationItemAnimator(
      isInMultiSelectMode = adapter.selectedItems::isNotEmpty,
      shouldPlayMessageAnimations = {
        animationsAllowed && scrollToPositionDelegate.isListCommitted() && binding.conversationItemRecycler.scrollState == RecyclerView.SCROLL_STATE_IDLE
      },
      isParentFilled = {
        binding.conversationItemRecycler.canScrollVertically(1) || binding.conversationItemRecycler.canScrollVertically(-1)
      },
      shouldUseSlideAnimation = { viewHolder ->
        true
      }
    )

    threadHeaderMarginDecoration = ThreadHeaderMarginDecoration()
    binding.conversationItemRecycler.addItemDecoration(threadHeaderMarginDecoration)

    conversationItemDecorations = ConversationItemDecorations(hasWallpaper = args.wallpaper != null)
    binding.conversationItemRecycler.addItemDecoration(conversationItemDecorations, 0)
  }

  private fun initializeGiphyMp4(): GiphyMp4ProjectionRecycler {
    val maxPlayback = GiphyMp4PlaybackPolicy.maxSimultaneousPlaybackInConversation()
    val holders = GiphyMp4ProjectionPlayerHolder.injectVideoViews(
      requireContext(),
      viewLifecycleOwner.lifecycle,
      binding.conversationVideoContainer,
      maxPlayback
    )

    val callback = GiphyMp4ProjectionRecycler(holders)
    GiphyMp4PlaybackController.attach(binding.conversationItemRecycler, callback, maxPlayback)
    binding.conversationItemRecycler.addItemDecoration(
      GiphyMp4ItemDecoration(callback),
      0
    )
    return callback
  }

  private fun initializeSearch() {
    searchViewModel.searchResults.observe(viewLifecycleOwner) { result ->
      if (result == null) {
        return@observe
      }

      if (result.results.isNotEmpty()) {
        val messageResult = result.results[result.position]
        disposables += viewModel
          .moveToSearchResult(messageResult)
          .observeOn(AndroidSchedulers.mainThread())
          .subscribeBy {
            moveToPosition(it)
          }
      }

      searchNav.setData(result.position, result.results.size)
    }

    searchNav.setEventListener(SearchEventListener())

    disposables += viewModel.searchQuery.subscribeBy {
      adapter.updateSearchQuery(it)
    }
  }

  private fun initializeLinkPreviews() {
    linkPreviewViewModel.linkPreviewState
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy { state ->
        if (state.isLoading) {
          inputPanel.setLinkPreviewLoading()
        } else if (state.hasLinks() && !state.linkPreview.isPresent) {
          inputPanel.setLinkPreviewNoPreview(state.error)
        } else {
          inputPanel.setLinkPreview(Glide.with(this), state.linkPreview)
        }

        updateToggleButtonState()
      }
      .addTo(disposables)
  }

  private fun initializeMediaKeyboard() {
    val isSystemEmojiPreferred = SignalStore.settings().isPreferSystemEmoji
    val keyboardMode: TextSecurePreferences.MediaKeyboardMode = TextSecurePreferences.getMediaKeyboardMode(requireContext())
    val stickerIntro: Boolean = !TextSecurePreferences.hasSeenStickerIntroTooltip(requireContext())

    inputPanel.showMediaKeyboardToggle(true)

    val keyboardPage = when (keyboardMode) {
      TextSecurePreferences.MediaKeyboardMode.EMOJI -> if (isSystemEmojiPreferred) KeyboardPage.STICKER else KeyboardPage.EMOJI
      TextSecurePreferences.MediaKeyboardMode.STICKER -> KeyboardPage.STICKER
      TextSecurePreferences.MediaKeyboardMode.GIF -> if (FeatureFlags.gifSearchAvailable()) KeyboardPage.GIF else KeyboardPage.STICKER
    }

    inputPanel.setMediaKeyboardToggleMode(keyboardPage)
    keyboardPagerViewModel.switchToPage(keyboardPage)

    if (stickerIntro) {
      TextSecurePreferences.setMediaKeyboardMode(requireContext(), TextSecurePreferences.MediaKeyboardMode.STICKER)
      inputPanel.setMediaKeyboardToggleMode(KeyboardPage.STICKER)
      conversationTooltips.displayStickerIntroductionTooltip(inputPanel.mediaKeyboardToggleAnchorView) {
        EventBus.getDefault().removeStickyEvent(StickerPackInstallEvent::class.java)
      }
    }
  }

  private fun initializeStickerSuggestions() {
    stickerViewModel.stickers
      .subscribeBy(onNext = inputPanel::setStickerSuggestions)
      .addTo(disposables)
  }

  private fun updateLinkPreviewState() {
    if (viewModel.isPushAvailable && !attachmentManager.isAttachmentPresent && context != null && inputPanel.editMessage?.hasNonTextSlide() != true) {
      linkPreviewViewModel.onEnabled()
      linkPreviewViewModel.onTextChanged(composeText.textTrimmed.toString(), composeText.selectionStart, composeText.selectionEnd)
    } else {
      linkPreviewViewModel.onUserCancel()
    }
  }

  private fun updateToggleButtonState() {
    val buttonToggle: AnimatingToggle = binding.conversationInputPanel.buttonToggle
    val quickAttachment: HidingLinearLayout = binding.conversationInputPanel.quickAttachmentToggle
    val inlineAttachment: HidingLinearLayout = binding.conversationInputPanel.inlineAttachmentContainer

    when {
      inputPanel.isRecordingInLockedMode -> {
        buttonToggle.display(sendButton)
        quickAttachment.show()
        inlineAttachment.hide()
      }

      inputPanel.inEditMessageMode() -> {
        buttonToggle.display(sendEditButton)
        quickAttachment.hide()
        inlineAttachment.hide()
      }

      draftViewModel.voiceNoteDraft != null -> {
        buttonToggle.display(sendButton)
        quickAttachment.hide()
        inlineAttachment.hide()
      }

      composeText.text.isNullOrBlank() && !attachmentManager.isAttachmentPresent -> {
        buttonToggle.display(binding.conversationInputPanel.attachButton)
        quickAttachment.show()
        inlineAttachment.hide()
      }

      else -> {
        buttonToggle.display(sendButton)
        quickAttachment.hide()

        if (!attachmentManager.isAttachmentPresent && !linkPreviewViewModel.hasLinkPreviewUi) {
          inlineAttachment.show()
        } else {
          inlineAttachment.hide()
        }
      }
    }
  }

  private fun sendSticker(
    stickerRecord: StickerRecord,
    clearCompose: Boolean
  ) {
    val stickerLocator = StickerLocator(stickerRecord.packId, stickerRecord.packKey, stickerRecord.stickerId, stickerRecord.emoji)
    val slide = StickerSlide(
      requireContext(),
      stickerRecord.uri,
      stickerRecord.size,
      stickerLocator,
      stickerRecord.contentType
    )

    sendMessageWithoutComposeInput(slide = slide, clearCompose = clearCompose)

    viewModel.updateStickerLastUsedTime(stickerRecord, System.currentTimeMillis().milliseconds)
  }

  private fun sendMessageWithoutComposeInput(
    slide: Slide? = null,
    contacts: List<Contact> = emptyList(),
    quote: QuoteModel? = null,
    clearCompose: Boolean = true
  ) {
    sendMessage(
      slideDeck = slide?.let { SlideDeck().apply { addSlide(slide) } },
      contacts = contacts,
      clearCompose = clearCompose,
      body = "",
      mentions = emptyList(),
      bodyRanges = null,
      messageToEdit = null,
      quote = quote,
      linkPreviews = emptyList(),
      bypassPreSendSafetyNumberCheck = true
    )
  }

  private fun sendMessage(
    body: String = composeText.editableText.toString(),
    mentions: List<Mention> = composeText.mentions,
    bodyRanges: BodyRangeList? = composeText.styling,
    messageToEdit: MessageId? = inputPanel.editMessageId,
    quote: QuoteModel? = inputPanel.quote.orNull(),
    scheduledDate: Long = -1,
    slideDeck: SlideDeck? = if (attachmentManager.isAttachmentPresent) attachmentManager.buildSlideDeck() else null,
    contacts: List<Contact> = emptyList(),
    clearCompose: Boolean = true,
    linkPreviews: List<LinkPreview> = linkPreviewViewModel.onSend(),
    preUploadResults: List<MessageSender.PreUploadResult> = emptyList(),
    bypassPreSendSafetyNumberCheck: Boolean = false,
    isViewOnce: Boolean = false,
    afterSendComplete: () -> Unit = {}
  ) {
    val threadRecipient = viewModel.recipientSnapshot

    if (threadRecipient == null) {
      Log.w(TAG, "Unable to send due to invalid thread recipient")
      toast(R.string.ConversationActivity_recipient_is_not_a_valid_sms_or_email_address_exclamation, Toast.LENGTH_LONG)
      return
    }

    if (scheduledDate != -1L && ReenableScheduledMessagesDialogFragment.showIfNeeded(requireContext(), childFragmentManager, null, scheduledDate)) {
      return
    }

    if (SignalStore.uiHints().hasNotSeenTextFormattingAlert() && bodyRanges != null && bodyRanges.ranges.isNotEmpty()) {
      Dialogs.showFormattedTextDialog(requireContext()) {
        sendMessage(body, mentions, bodyRanges, messageToEdit, quote, scheduledDate, slideDeck, contacts, clearCompose, linkPreviews, preUploadResults, bypassPreSendSafetyNumberCheck, isViewOnce, afterSendComplete)
      }
      return
    }

    if (inputPanel.isRecordingInLockedMode) {
      inputPanel.releaseRecordingLock()
      return
    }

    if (slideDeck == null) {
      val voiceNote: DraftTable.Draft? = draftViewModel.voiceNoteDraft
      if (voiceNote != null) {
        sendMessageWithoutComposeInput(slide = AudioSlide.createFromVoiceNoteDraft(voiceNote), clearCompose = true)
        return
      }
    }

    if (body.isNullOrBlank() && slideDeck?.containsMediaSlide() != true && preUploadResults.isEmpty() && contacts.isEmpty()) {
      Log.i(TAG, "Unable to send due to empty message")
      toast(R.string.ConversationActivity_message_is_empty_exclamation)
      return
    }

    if (viewModel.identityRecordsState.hasRecentSafetyNumberChange() && !bypassPreSendSafetyNumberCheck) {
      Log.i(TAG, "Unable to send due to SNC")
      handleRecentSafetyNumberChange(viewModel.identityRecordsState.getRecentSafetyNumberChangeRecords())
      return
    }

    val metricId = viewModel.recipientSnapshot?.let { if (it.isGroup) SignalLocalMetrics.GroupMessageSend.start() else SignalLocalMetrics.IndividualMessageSend.start() }

    val send: Completable = viewModel.sendMessage(
      metricId = metricId,
      threadRecipient = threadRecipient,
      body = body,
      slideDeck = slideDeck,
      scheduledDate = scheduledDate,
      messageToEdit = messageToEdit,
      quote = quote,
      mentions = mentions,
      bodyRanges = bodyRanges,
      contacts = contacts,
      linkPreviews = linkPreviews,
      preUploadResults = preUploadResults,
      isViewOnce = isViewOnce
    )

    disposables += send
      .doOnSubscribe {
        if (clearCompose) {
          composeTextEventsListener?.typingStatusEnabled = false
          composeText.setText("")
          composeTextEventsListener?.typingStatusEnabled = true
          attachmentManager.clear(Glide.with(this@ConversationFragment), false)
          inputPanel.clearQuote()
        }
        scrollToPositionDelegate.markListCommittedVersion()
      }
      .subscribeBy(
        onComplete = {
          onSendComplete()
          afterSendComplete()
        }
      )
  }

  private fun onSendComplete() {
    if (isDetached || activity?.isFinishing == true) {
      return
    }

    scrollToPositionDelegate.resetScrollPositionAfterMarkListVersionSurpassed()
    attachmentManager.cleanup()

    updateLinkPreviewState()

    draftViewModel.onSendComplete()

    inputPanel.exitEditMessageMode()

    if (args.conversationScreenType.isInPopup) {
      activity?.finish()
    }
  }

  private fun handleRecentSafetyNumberChange(changedRecords: List<IdentityRecord>) {
    val recipient = viewModel.recipientSnapshot ?: return
    SafetyNumberBottomSheet
      .forIdentityRecordsAndDestination(changedRecords, RecipientSearchKey(recipient.getId(), false))
      .show(childFragmentManager)
  }

  private fun toast(@StringRes toastTextId: Int, toastDuration: Int = Toast.LENGTH_SHORT) {
    ThreadUtil.runOnMain {
      if (context != null) {
        Toast.makeText(context, toastTextId, toastDuration).show()
      } else {
        Log.w(TAG, "Dropping toast without context.")
      }
    }
  }

  private fun snackbar(
    @StringRes text: Int,
    anchor: View = binding.conversationItemRecycler,
    @Duration duration: Int = Snackbar.LENGTH_LONG
  ) {
    Snackbar.make(anchor, text, duration).show()
  }

  private fun maybeShowSwipeToReplyTooltip() {
    if (!TextSecurePreferences.hasSeenSwipeToReplyTooltip(requireContext())) {
      val tooltipText = if (ViewUtil.isLtr(requireContext())) {
        R.string.ConversationFragment_you_can_swipe_to_the_right_reply
      } else {
        R.string.ConversationFragment_you_can_swipe_to_the_left_reply
      }

      snackbar(tooltipText)

      TextSecurePreferences.setHasSeenSwipeToReplyTooltip(requireContext(), true)
    }
  }

  private fun calculateSelectedItemCount(): String {
    val count = adapter.selectedItems.map(MultiselectPart::conversationMessage).distinct().count()
    return requireContext().resources.getQuantityString(R.plurals.conversation_context__s_selected, count, count)
  }

  private fun getSelectedConversationMessage(): ConversationMessage {
    val records = adapter.selectedItems.map(MultiselectPart::conversationMessage).distinct().toSet()
    if (records.size == 1) {
      return records.first()
    }

    error("More than one conversation message in set.")
  }

  private fun setCorrectActionModeMenuVisibility() {
    val selectedParts = adapter.selectedItems

    if (actionMode != null && selectedParts.isEmpty()) {
      actionMode?.finish()
      return
    }

    setBottomActionBarVisibility(true)

    val recipient = viewModel.recipientSnapshot ?: return
    val menuState = MenuState.getMenuState(
      recipient,
      selectedParts,
      viewModel.hasMessageRequestState,
      conversationGroupViewModel.isNonAdminInAnnouncementGroup()
    )

    val items = arrayListOf<ActionItem>()

    if (menuState.shouldShowReplyAction()) {
      items.add(
        ActionItem(R.drawable.symbol_reply_24, resources.getString(R.string.conversation_selection__menu_reply)) {
          maybeShowSwipeToReplyTooltip()
          handleReplyToMessage(getSelectedConversationMessage())
          actionMode?.finish()
        }
      )
    }

    if (menuState.shouldShowEditAction()) {
      items.add(
        ActionItem(R.drawable.symbol_edit_24, resources.getString(R.string.conversation_selection__menu_edit)) {
          handleEditMessage(getSelectedConversationMessage())
          actionMode?.finish()
        }
      )
    }

    if (menuState.shouldShowForwardAction()) {
      items.add(
        ActionItem(R.drawable.symbol_forward_24, resources.getString(R.string.conversation_selection__menu_forward)) {
          handleForwardMessageParts(selectedParts)
        }
      )
    }

    if (menuState.shouldShowSaveAttachmentAction()) {
      items.add(
        ActionItem(R.drawable.symbol_save_android_24, getResources().getString(R.string.conversation_selection__menu_save)) {
          handleSaveAttachment(getSelectedConversationMessage().messageRecord as MmsMessageRecord)
          actionMode?.finish()
        }
      )
    }

    if (menuState.shouldShowCopyAction()) {
      items.add(
        ActionItem(R.drawable.symbol_copy_android_24, getResources().getString(R.string.conversation_selection__menu_copy)) {
          handleCopyMessage(selectedParts)
          actionMode?.finish()
        }
      )
    }

    if (menuState.shouldShowDetailsAction()) {
      items.add(
        ActionItem(R.drawable.symbol_info_24, getResources().getString(R.string.conversation_selection__menu_message_details)) {
          handleDisplayDetails(getSelectedConversationMessage())
          actionMode?.finish()
        }
      )
    }

    if (menuState.shouldShowDeleteAction()) {
      items.add(
        ActionItem(R.drawable.symbol_trash_24, getResources().getString(R.string.conversation_selection__menu_delete)) {
          handleDeleteMessages(selectedParts)
          actionMode?.finish()
        }
      )
    }

    bottomActionBar.setItems(items)
  }

  private fun setBottomActionBarVisibility(isVisible: Boolean) {
    val isCurrentlyVisible = bottomActionBar.isVisible
    if (isVisible == isCurrentlyVisible) {
      return
    }

    val additionalScrollOffset = 54.dp
    if (isVisible) {
      ViewUtil.animateIn(bottomActionBar, bottomActionBar.enterAnimation)
      container.hideInput()
      inputPanel.setHideForSelection(true)
      animationsAllowed = false

      bottomActionBar.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
          if (bottomActionBar.height == 0 && bottomActionBar.visible) {
            return false
          }
          bottomActionBar.viewTreeObserver.removeOnPreDrawListener(this)

          val bottomPadding = bottomActionBar.height + ((bottomActionBar.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 18.dp)
          ViewUtil.setPaddingBottom(binding.conversationItemRecycler, bottomPadding)
          binding.conversationItemRecycler.scrollBy(0, -(bottomPadding - additionalScrollOffset))
          animationsAllowed = true
          return false
        }
      })
    } else {
      ViewUtil.animateOut(bottomActionBar, bottomActionBar.exitAnimation)
        .addListener(object : ListenableFuture.Listener<Boolean> {
          override fun onSuccess(result: Boolean?) {
            val scrollOffset = binding.conversationItemRecycler.paddingBottom - additionalScrollOffset
            inputPanel.setHideForSelection(false)
            val bottomPadding = resources.getDimensionPixelSize(R.dimen.conversation_bottom_padding)
            ViewUtil.setPaddingBottom(binding.conversationItemRecycler, bottomPadding)
            binding.conversationItemRecycler.doOnPreDraw {
              it.scrollBy(0, scrollOffset)
            }
          }

          override fun onFailure(e: ExecutionException?) = Unit
        })
    }
  }

  private fun isUnopenedGift(itemView: View, messageRecord: MessageRecord): Boolean {
    if (itemView is OpenableGift) {
      val projection = (itemView as OpenableGift).getOpenableGiftProjection(false)
      if (projection != null) {
        projection.release()
        return !openableGiftItemDecoration.hasOpenedGiftThisSession(messageRecord.id)
      }
    }

    return false
  }

  private fun clearFocusedItem() {
    multiselectItemDecoration.setFocusedItem(null)
    binding.conversationItemRecycler.invalidateItemDecorations()
  }

  private fun handleReaction(
    conversationMessage: ConversationMessage,
    onActionSelectedListener: OnActionSelectedListener,
    selectedConversationModel: SelectedConversationModel,
    onHideListener: OnHideListener
  ) {
    reactionDelegate.setOnActionSelectedListener(onActionSelectedListener)
    reactionDelegate.setOnHideListener(onHideListener)
    reactionDelegate.show(requireActivity(), viewModel.recipientSnapshot!!, conversationMessage, conversationGroupViewModel.isNonAdminInAnnouncementGroup(), selectedConversationModel)
    composeText.clearFocus()
  }

  //region Message Request Helpers

  @SuppressLint("CheckResult")
  private fun onReportSpam() {
    val recipient = viewModel.recipientSnapshot
    if (recipient == null) {
      Log.w(TAG, "[onBlockClicked] No recipient!")
      return
    }

    BlockUnblockDialog.showReportSpamFor(
      requireContext(),
      lifecycle,
      recipient,
      {
        messageRequestViewModel
          .onReportSpam()
          .doOnSubscribe { binding.conversationDisabledInput.showBusy() }
          .doOnTerminate { binding.conversationDisabledInput.hideBusy() }
          .subscribeBy {
            Log.d(TAG, "report spam complete")
            toast(R.string.ConversationFragment_reported_as_spam)
          }
      },
      if (recipient.isBlocked) {
        null
      } else {
        Runnable {
          messageRequestViewModel
            .onBlockAndReportSpam()
            .doOnSubscribe { binding.conversationDisabledInput.showBusy() }
            .doOnTerminate { binding.conversationDisabledInput.hideBusy() }
            .subscribeBy { result ->
              when (result) {
                is Result.Success -> {
                  Log.d(TAG, "report spam complete")
                  toast(R.string.ConversationFragment_reported_as_spam_and_blocked)
                }
                is Result.Failure -> {
                  Log.d(TAG, "report spam failed ${result.failure}")
                  toast(GroupErrors.getUserDisplayMessage(result.failure))
                }
              }
            }
        }
      }
    )
  }

  @SuppressLint("CheckResult")
  private fun onBlock() {
    val recipient = viewModel.recipientSnapshot
    if (recipient == null) {
      Log.w(TAG, "[onBlockClicked] No recipient!")
      return
    }

    BlockUnblockDialog.showBlockFor(
      requireContext(),
      lifecycle,
      recipient
    ) {
      messageRequestViewModel
        .onBlock()
        .subscribeWithShowProgress("block")
    }
  }

  @SuppressLint("CheckResult")
  private fun onUnblock() {
    val recipient = viewModel.recipientSnapshot
    if (recipient == null) {
      Log.w(TAG, "[onUnblockClicked] No recipient!")
      return
    }

    BlockUnblockDialog.showUnblockFor(
      requireContext(),
      lifecycle,
      recipient
    ) {
      messageRequestViewModel
        .onUnblock()
        .subscribeWithShowProgress("unblock")
    }
  }

  private fun onMessageRequestAccept() {
    messageRequestViewModel
      .onAccept()
      .subscribeWithShowProgress("accept message request")
      .addTo(disposables)
  }

  private fun onDeleteConversation() {
    val recipient = viewModel.recipientSnapshot
    if (recipient == null) {
      Log.w(TAG, "[onDeleteConversation] No recipient!")
      return
    }

    ConversationDialogs.displayDeleteDialog(requireContext(), recipient) {
      messageRequestViewModel
        .onDelete()
        .subscribeWithShowProgress("delete message request")
    }
  }

  private fun Single<Result<Unit, GroupChangeFailureReason>>.subscribeWithShowProgress(logMessage: String): Disposable {
    return doOnSubscribe { binding.conversationDisabledInput.showBusy() }
      .doOnTerminate { binding.conversationDisabledInput.hideBusy() }
      .subscribeBy { result ->
        when (result) {
          is Result.Success -> Log.d(TAG, "$logMessage complete")
          is Result.Failure -> {
            Log.d(TAG, "$logMessage failed ${result.failure}")
            toast(GroupErrors.getUserDisplayMessage(result.failure))
          }
        }
      }
  }

  private inner class BackPressedDelegate : OnBackPressedCallback(true) {
    override fun handleOnBackPressed() {
      Log.d(TAG, "onBackPressed()")
      if (reactionDelegate.isShowing) {
        reactionDelegate.hide()
      } else if (isSearchRequested) {
        searchMenuItem?.collapseActionView()
      } else if (args.conversationScreenType.isInBubble) {
        isEnabled = false
        requireActivity().onBackPressed()
      } else {
        requireActivity().finish()
      }
    }
  }

  // endregion

  //region Message action handling

  private fun handleReplyToMessage(conversationMessage: ConversationMessage) {
    if (isSearchRequested) {
      searchMenuItem?.collapseActionView()
    }

    if (inputPanel.inEditMessageMode()) {
      inputPanel.exitEditMessageMode()
    }

    val (slideDeck, body) = viewModel.getSlideDeckAndBodyForReply(requireContext(), conversationMessage)
    val author = conversationMessage.messageRecord.fromRecipient

    inputPanel.setQuote(
      Glide.with(this),
      conversationMessage.messageRecord.dateSent,
      author,
      body,
      slideDeck,
      conversationMessage.messageRecord.getRecordQuoteType()
    )

    inputPanel.clickOnComposeInput()
  }

  private fun handleEditMessage(conversationMessage: ConversationMessage) {
    if (isSearchRequested) {
      searchMenuItem?.collapseActionView()
    }

    viewModel.resolveMessageToEdit(conversationMessage)
      .subscribeBy { updatedMessage ->
        inputPanel.enterEditMessageMode(Glide.with(this), updatedMessage, false)
      }
      .addTo(disposables)
  }

  private fun handleForwardMessageParts(messageParts: Set<MultiselectPart>) {
    inputPanel.clearQuote()

    MultiselectForwardFragmentArgs.create(requireContext(), messageParts) { args ->
      MultiselectForwardFragment.showBottomSheet(childFragmentManager, args)
    }
  }

  private fun handleSaveAttachment(record: MmsMessageRecord) {
    if (record.isViewOnce) {
      error("Cannot save a view-once message")
    }

    val attachments = SaveAttachmentUtil.getAttachmentsForRecord(record)

    SaveAttachmentUtil.showWarningDialog(requireContext(), attachments.size) { _, _ ->
      if (StorageUtil.canWriteToMediaStore()) {
        performAttachmentSave(attachments)
      } else {
        Permissions.with(this)
          .request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
          .ifNecessary()
          .withPermanentDenialDialog(getString(R.string.MediaPreviewActivity_signal_needs_the_storage_permission_in_order_to_write_to_external_storage_but_it_has_been_permanently_denied))
          .onAnyDenied { toast(R.string.MediaPreviewActivity_unable_to_write_to_external_storage_without_permission, toastDuration = Toast.LENGTH_LONG) }
          .onAllGranted { performAttachmentSave(attachments) }
          .execute()
      }
    }
  }

  private fun performAttachmentSave(attachments: Set<SaveAttachmentUtil.SaveAttachment>) {
    val progressDialog = ProgressCardDialogFragment.create()
    progressDialog.arguments = ProgressCardDialogFragmentArgs.Builder(
      resources.getQuantityString(R.plurals.ConversationFragment_saving_n_attachments_to_sd_card, attachments.size, attachments.size)
    ).build().toBundle()

    SaveAttachmentUtil.saveAttachments(attachments)
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .doOnSubscribe { progressDialog.show(parentFragmentManager, null) }
      .doOnTerminate { progressDialog.dismissAllowingStateLoss() }
      .subscribeBy { it.toast(requireContext()) }
      .addTo(disposables)
  }

  private fun handleCopyMessage(messageParts: Set<MultiselectPart>) {
    viewModel.copyToClipboard(requireContext(), messageParts).subscribe().addTo(disposables)
  }

  private fun handleResend(conversationMessage: ConversationMessage) {
    viewModel.resendMessage(conversationMessage).subscribe()
  }

  private fun handleEnterMultiselect(conversationMessage: ConversationMessage) {
    val parts = conversationMessage.multiselectCollection.toSet()
    parts.forEach { adapter.toggleSelection(it) }
    binding.conversationItemRecycler.invalidateItemDecorations()
    actionMode = (requireActivity() as AppCompatActivity).startSupportActionMode(actionModeCallback)
  }

  private fun handleViewPaymentDetails(conversationMessage: ConversationMessage) {
    val record: MmsMessageRecord = conversationMessage.messageRecord as? MmsMessageRecord ?: return
    val payment = record.payment ?: return
    if (record.isPaymentNotification) {
      startActivity(PaymentsActivity.navigateToPaymentDetails(requireContext(), payment.uuid))
    }
  }

  private fun handleDisplayDetails(conversationMessage: ConversationMessage) {
    val recipientSnapshot = viewModel.recipientSnapshot ?: return
    MessageDetailsFragment.create(conversationMessage.messageRecord, recipientSnapshot.id).show(childFragmentManager, null)
  }

  private fun handleDeleteMessages(messageParts: Set<MultiselectPart>) {
    val records = messageParts.map(MultiselectPart::getMessageRecord).toSet()
    disposables += DeleteDialog.show(
      context = requireContext(),
      messageRecords = records
    ).observeOn(AndroidSchedulers.mainThread())
      .subscribe { (deleted: Boolean, _: Boolean) ->
        if (!deleted) return@subscribe
        val editMessageId = inputPanel.editMessageId?.id
        if (editMessageId != null && records.any { it.id == editMessageId }) {
          inputPanel.exitEditMessageMode()
        }
      }
  }

  private inner class SwipeAvailabilityProvider : ConversationItemSwipeCallback.SwipeAvailabilityProvider {
    override fun isSwipeAvailable(conversationMessage: ConversationMessage): Boolean {
      val recipient = viewModel.recipientSnapshot ?: return false

      return actionMode == null && MenuState.canReplyToMessage(
        recipient,
        MenuState.isActionMessage(conversationMessage.messageRecord),
        conversationMessage.messageRecord,
        viewModel.hasMessageRequestState,
        conversationGroupViewModel.isNonAdminInAnnouncementGroup()
      )
    }
  }

  //endregion

  //region Scroll Handling

  private fun moveToStartPosition(meta: ConversationData) {
    if (meta.getStartPosition() == 0) {
      layoutManager.scrollToPositionWithOffset(0, 0) {
        animationsAllowed = true
        markReadHelper.stopIgnoringViewReveals(MarkReadHelper.getLatestTimestamp(adapter, layoutManager).orNull())
      }
    } else {
      binding.toolbar.viewTreeObserver.addOnGlobalLayoutListener(StartPositionScroller(meta))
    }
  }

  /** Helper to scroll the conversation to the correct position and offset based on toolbar height and the type of position */
  private inner class StartPositionScroller(private val meta: ConversationData) : ViewTreeObserver.OnGlobalLayoutListener {

    override fun onGlobalLayout() {
      if (!isAdded || view == null) {
        return
      }

      val rect = Rect()
      binding.toolbar.getGlobalVisibleRect(rect)
      val toolbarOffset = rect.bottom
      binding.toolbar.viewTreeObserver.removeOnGlobalLayoutListener(this)

      val offset = when {
        meta.getStartPosition() == 0 -> 0
        meta.shouldJumpToMessage() -> (binding.conversationItemRecycler.height - toolbarOffset) / 4
        meta.shouldScrollToLastSeen() -> binding.conversationItemRecycler.height - toolbarOffset
        else -> binding.conversationItemRecycler.height
      }

      Log.d(TAG, "Scrolling to start position ${meta.getStartPosition()}")
      layoutManager.scrollToPositionWithOffset(meta.getStartPosition(), offset) {
        animationsAllowed = true
        markReadHelper.stopIgnoringViewReveals(MarkReadHelper.getLatestTimestamp(adapter, layoutManager).orNull())
        if (meta.shouldJumpToMessage()) {
          binding.conversationItemRecycler.post {
            adapter.pulseAtPosition(meta.getStartPosition())
          }
        }
      }
    }
  }

  /**
   * Requests a jump to the desired position, and ensures that the position desired will be visible on the screen.
   */
  private fun moveToPosition(position: Int) {
    scrollToPositionDelegate.requestScrollPosition(
      position = position,
      smooth = true,
      scrollStrategy = jumpAndPulseScrollStrategy
    )
  }

  private fun scrollToNextMention() {
    disposables += viewModel.getNextMentionPosition().subscribeBy {
      moveToPosition(it)
    }
  }

  private fun isScrolledToBottom(): Boolean {
    return !binding.conversationItemRecycler.canScrollVertically(1)
  }

  private fun isScrolledPastButtonThreshold(): Boolean {
    return layoutManager.findFirstVisibleItemPosition() > 4
  }

  private fun shouldScrollToBottom(): Boolean {
    return isScrolledToBottom() || layoutManager.findFirstVisibleItemPosition() <= 0
  }

  /**
   * Controls animation and visibility of the scrollDateHeader.
   */
  private inner class ScrollDateHeaderHelper {

    private val slideIn = AnimationUtils.loadAnimation(
      requireContext(),
      R.anim.slide_from_top
    ).apply {
      duration = SCROLL_HEADER_ANIMATION_DURATION
    }

    private val slideOut = AnimationUtils.loadAnimation(
      requireContext(),
      R.anim.conversation_scroll_date_header_slide_to_top
    ).apply {
      duration = SCROLL_HEADER_ANIMATION_DURATION
    }

    private var pendingHide = false

    fun show() {
      if (binding.scrollDateHeader.text.isNullOrEmpty()) {
        return
      }

      if (pendingHide) {
        pendingHide = false
      } else {
        ViewUtil.animateIn(binding.scrollDateHeader, slideIn)
      }
    }

    fun bind(message: ConversationMessage?) {
      if (message != null) {
        binding.scrollDateHeader.text = DateUtils.getConversationDateHeaderString(requireContext(), Locale.getDefault(), message.conversationTimestamp)
      }
    }

    fun hide() {
      pendingHide = true

      val header = binding.scrollDateHeader

      header.postDelayed({
        if (pendingHide) {
          pendingHide = false
          ViewUtil.animateOut(header, slideOut)
        }
      }, SCROLL_HEADER_CLOSE_DELAY)
    }
  }

  private inner class ScrollListener : RecyclerView.OnScrollListener() {

    private var wasAtBottom = true
    private val scrollDateHeaderHelper = ScrollDateHeaderHelper()

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
      if (isScrolledToBottom()) {
        viewModel.setShowScrollButtonsForScrollPosition(showScrollButtons = false, willScrollToBottomOnNewMessage = true)
      } else if (isScrolledPastButtonThreshold()) {
        viewModel.setShowScrollButtonsForScrollPosition(showScrollButtons = true, willScrollToBottomOnNewMessage = false)
      } else {
        viewModel.setShowScrollButtonsForScrollPosition(showScrollButtons = false, willScrollToBottomOnNewMessage = shouldScrollToBottom())
      }

      presentComposeDivider()

      val message = adapter.getConversationMessage(layoutManager.findLastVisibleItemPosition())
      scrollDateHeaderHelper.bind(message)

      val timestamp = MarkReadHelper.getLatestTimestamp(adapter, layoutManager)
      timestamp.ifPresent(markReadHelper::onViewsRevealed)
    }

    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
      if (newState != RecyclerView.SCROLL_STATE_IDLE) {
        scrollDateHeaderHelper.show()
      } else {
        scrollDateHeaderHelper.hide()
      }
    }

    private fun presentComposeDivider() {
      val isAtBottom = isScrolledToBottom()
      if (isAtBottom && !wasAtBottom) {
        ViewUtil.fadeOut(binding.composeDivider, 50, View.INVISIBLE)
      } else if (wasAtBottom && !isAtBottom) {
        ViewUtil.fadeIn(binding.composeDivider, 500)
      }

      wasAtBottom = isAtBottom
    }
  }

  private inner class DataObserver : RecyclerView.AdapterDataObserver() {
    override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
      if (positionStart == 0 && shouldScrollToBottom()) {
        layoutManager.scrollToPositionWithOffset(0, 0)
        scrollListener?.onScrolled(binding.conversationItemRecycler, 0, 0)
      }
    }

    override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
      if (actionMode == null) {
        return
      }

      val expired: Set<MultiselectPart> = adapter
        .selectedItems
        .filter { it.isExpired() }
        .toSet()

      adapter.removeFromSelection(expired)

      if (adapter.selectedItems.isEmpty()) {
        actionMode?.finish()
      } else {
        actionMode?.setTitle(calculateSelectedItemCount())
      }
    }

    override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
      scrollListener?.onScrolled(binding.conversationItemRecycler, 0, 0)
    }
  }

  //endregion Scroll Handling

  // region Conversation Callbacks

  private inner class ConversationItemClickListener : ConversationAdapter.ItemClickListener {
    override fun onQuoteClicked(messageRecord: MmsMessageRecord) {
      val quote: Quote? = messageRecord.quote
      if (quote == null) {
        Log.w(TAG, "onQuoteClicked: Received an event but there is no quote.")
        return
      }

      if (quote.isOriginalMissing) {
        Log.i(TAG, "onQuoteClicked: Original message is missing.")
        toast(R.string.ConversationFragment_quoted_message_not_found)
        return
      }

      val parentStoryId = messageRecord.parentStoryId
      if (parentStoryId != null) {
        startActivity(
          StoryViewerActivity.createIntent(
            requireContext(),
            StoryViewerArgs.Builder(quote.author, Recipient.resolved(quote.author).shouldHideStory())
              .withStoryId(parentStoryId.asMessageId().id)
              .isFromQuote(true)
              .build()
          )
        )

        return
      }

      disposables += viewModel.getQuotedMessagePosition(quote)
        .subscribeBy {
          if (it >= 0) {
            moveToPosition(it)
          } else {
            toast(R.string.ConversationFragment_quoted_message_no_longer_available)
          }
        }
    }

    override fun onLinkPreviewClicked(linkPreview: LinkPreview) {
      val activity = activity ?: return
      CommunicationActions.openBrowserLink(activity, linkPreview.url)
    }

    override fun onQuotedIndicatorClicked(messageRecord: MessageRecord) {
      context ?: return
      activity ?: return
      val recipientId = viewModel.recipientSnapshot?.id ?: return

      container.runAfterAllHidden(composeText) {
        MessageQuotesBottomSheet.show(
          childFragmentManager,
          MessageId(messageRecord.id),
          recipientId
        )
      }
    }

    override fun onMoreTextClicked(conversationRecipientId: RecipientId, messageId: Long, isMms: Boolean) {
      context ?: return
      LongMessageFragment.create(messageId, isMms).show(childFragmentManager, null)
    }

    override fun onStickerClicked(stickerLocator: StickerLocator) {
      context ?: return
      startActivity(StickerPackPreviewActivity.getIntent(stickerLocator.packId, stickerLocator.packKey))
    }

    override fun onViewOnceMessageClicked(messageRecord: MmsMessageRecord) {
      if (!messageRecord.isViewOnce) {
        error("Non-revealable message clicked.")
      }

      if (!ViewOnceUtil.isViewable(messageRecord)) {
        val toastText = if (messageRecord.isOutgoing) {
          R.string.ConversationFragment_view_once_media_is_deleted_after_sending
        } else {
          R.string.ConversationFragment_you_already_viewed_this_message
        }

        toast(toastText)
        return
      }

      disposables += viewModel.getTemporaryViewOnceUri(messageRecord).subscribeBy(
        onSuccess = {
          container.hideAll(composeText)
          startActivity(ViewOnceMessageActivity.getIntent(requireContext(), messageRecord.id, it))
        },
        onComplete = {
          toast(R.string.ConversationFragment_failed_to_open_message)
        }
      )
    }

    override fun onSharedContactDetailsClicked(contact: Contact, avatarTransitionView: View) {
      val activity = activity ?: return
      ViewCompat.setTransitionName(avatarTransitionView, "avatar")
      val bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(activity, avatarTransitionView, "avatar").toBundle()
      ActivityCompat.startActivity(activity, SharedContactDetailsActivity.getIntent(activity, contact), bundle)
    }

    override fun onAddToContactsClicked(contact: Contact) {
      disposables += AddToContactsContract.createIntentAndLaunch(
        this@ConversationFragment,
        addToContactsLauncher,
        contact
      )
    }

    override fun onMessageSharedContactClicked(choices: MutableList<Recipient>) {
      val context = context ?: return
      ContactUtil.selectRecipientThroughDialog(context, choices, Locale.getDefault()) { recipient: Recipient ->
        CommunicationActions.startConversation(context, recipient, null)
      }
    }

    override fun onInviteSharedContactClicked(choices: MutableList<Recipient>) {
      val context = context ?: return
      ContactUtil.selectRecipientThroughDialog(context, choices, Locale.getDefault()) { recipient: Recipient ->
        CommunicationActions.composeSmsThroughDefaultApp(
          context,
          recipient,
          getString(R.string.InviteActivity_lets_switch_to_signal, getString(R.string.install_url))
        )
      }
    }

    override fun onReactionClicked(multiselectPart: MultiselectPart, messageId: Long, isMms: Boolean) {
      context ?: return
      val reactionsTag = "REACTIONS"
      if (parentFragmentManager.findFragmentByTag(reactionsTag) == null) {
        ReactionsBottomSheetDialogFragment.create(messageId, isMms).show(childFragmentManager, reactionsTag)
      }
    }

    override fun onGroupMemberClicked(recipientId: RecipientId, groupId: GroupId) {
      context ?: return
      RecipientBottomSheetDialogFragment.show(childFragmentManager, recipientId, groupId)
    }

    override fun onMessageWithErrorClicked(messageRecord: MessageRecord) {
      val recipientId = viewModel.recipientSnapshot?.id ?: return
      if (messageRecord.isIdentityMismatchFailure) {
        SafetyNumberBottomSheet
          .forMessageRecord(requireContext(), messageRecord)
          .show(childFragmentManager)
      } else if (messageRecord.hasFailedWithNetworkFailures()) {
        ConversationDialogs.displayMessageCouldNotBeSentDialog(requireContext(), messageRecord)
      } else {
        MessageDetailsFragment.create(messageRecord, recipientId).show(childFragmentManager, null)
      }
    }

    override fun onMessageWithRecaptchaNeededClicked(messageRecord: MessageRecord) {
      RecaptchaProofBottomSheetFragment.show(childFragmentManager)
    }

    override fun onIncomingIdentityMismatchClicked(recipientId: RecipientId) {
      SafetyNumberBottomSheet.forRecipientId(recipientId).show(parentFragmentManager)
    }

    override fun onRegisterVoiceNoteCallbacks(onPlaybackStartObserver: Observer<VoiceNotePlaybackState>) {
      getVoiceNoteMediaController()
        .voiceNotePlaybackState
        .observe(viewLifecycleOwner, onPlaybackStartObserver)
    }

    override fun onUnregisterVoiceNoteCallbacks(onPlaybackStartObserver: Observer<VoiceNotePlaybackState>) {
      getVoiceNoteMediaController()
        .voiceNotePlaybackState
        .removeObserver(onPlaybackStartObserver)
    }

    override fun onVoiceNotePause(uri: Uri) {
      getVoiceNoteMediaController().pausePlayback(uri)
    }

    override fun onVoiceNotePlay(uri: Uri, messageId: Long, position: Double) {
      getVoiceNoteMediaController().startConsecutivePlayback(uri, messageId, position)
    }

    override fun onVoiceNoteSeekTo(uri: Uri, position: Double) {
      getVoiceNoteMediaController().seekToPosition(uri, position)
    }

    override fun onVoiceNotePlaybackSpeedChanged(uri: Uri, speed: Float) {
      getVoiceNoteMediaController().setPlaybackSpeed(uri, speed)
    }

    override fun onGroupMigrationLearnMoreClicked(membershipChange: GroupMigrationMembershipChange) {
      GroupsV1MigrationInfoBottomSheetDialogFragment.show(parentFragmentManager, membershipChange)
    }

    override fun onChatSessionRefreshLearnMoreClicked() {
      ConversationDialogs.displayChatSessionRefreshLearnMoreDialog(requireContext())
    }

    override fun onBadDecryptLearnMoreClicked(author: RecipientId) {
      val isGroup = viewModel.recipientSnapshot?.isGroup ?: return
      val recipientName = Recipient.resolved(author).getDisplayName(requireContext())
      BadDecryptLearnMoreDialog.show(parentFragmentManager, recipientName, isGroup)
    }

    override fun onSafetyNumberLearnMoreClicked(recipient: Recipient) {
      ConversationDialogs.displaySafetyNumberLearnMoreDialog(this@ConversationFragment, recipient)
    }

    override fun onJoinGroupCallClicked() {
      val activity = activity ?: return
      val recipient = viewModel.recipientSnapshot ?: return
      CommunicationActions.startVideoCall(activity, recipient)
    }

    override fun onInviteFriendsToGroupClicked(groupId: GroupId.V2) {
      GroupLinkInviteFriendsBottomSheetDialogFragment.show(requireActivity().supportFragmentManager, groupId)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onEnableCallNotificationsClicked() {
      EnableCallNotificationSettingsDialog.fixAutomatically(requireContext())
      if (EnableCallNotificationSettingsDialog.shouldShow(requireContext())) {
        EnableCallNotificationSettingsDialog.show(childFragmentManager)
      } else {
        adapter.notifyDataSetChanged()
      }
    }

    override fun onPlayInlineContent(conversationMessage: ConversationMessage?) {
      adapter.playInlineContent(conversationMessage)
    }

    override fun onInMemoryMessageClicked(messageRecord: InMemoryMessageRecord) {
      ConversationDialogs.displayInMemoryMessageDialog(requireContext(), messageRecord)
    }

    override fun onViewGroupDescriptionChange(groupId: GroupId?, description: String, isMessageRequestAccepted: Boolean) {
      if (groupId != null) {
        GroupDescriptionDialog.show(childFragmentManager, groupId, description, isMessageRequestAccepted)
      }
    }

    override fun onChangeNumberUpdateContact(recipient: Recipient) {
      startActivity(RecipientExporter.export(recipient).asAddContactIntent())
    }

    override fun onCallToAction(action: String) {
      if ("gift_badge" == action) {
        startActivity(Intent(requireContext(), GiftFlowActivity::class.java))
      }
    }

    override fun onDonateClicked() {
      requireActivity()
        .supportFragmentManager
        .beginTransaction()
        .add(DonateToSignalFragment.Dialog.create(DonateToSignalType.ONE_TIME), "one_time_nav")
        .commitNow()
    }

    override fun onBlockJoinRequest(recipient: Recipient) {
      MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.ConversationFragment__block_request)
        .setMessage(getString(R.string.ConversationFragment__s_will_not_be_able_to_join_or_request_to_join_this_group_via_the_group_link, recipient.getDisplayName(requireContext())))
        .setNegativeButton(R.string.ConversationFragment__cancel, null)
        .setPositiveButton(R.string.ConversationFragment__block_request_button) { _, _ -> handleBlockJoinRequest(recipient) }
        .show()
    }

    override fun onRecipientNameClicked(target: RecipientId) {
      context ?: return
      disposables += viewModel.recipient.firstOrError().observeOn(AndroidSchedulers.mainThread()).subscribeBy {
        RecipientBottomSheetDialogFragment.show(
          parentFragmentManager,
          target,
          it.groupId.orElse(null)
        )
      }
    }

    override fun onInviteToSignalClicked() {
      val recipient = viewModel.recipientSnapshot ?: return
      InviteActions.inviteUserToSignal(
        requireContext(),
        recipient,
        binding.conversationInputPanel.embeddedTextEditor::appendInvite,
        this@ConversationFragment::startActivity
      )
    }

    override fun onActivatePaymentsClicked() {
      startActivity(Intent(requireContext(), PaymentsActivity::class.java))
    }

    override fun onSendPaymentClicked(recipientId: RecipientId) {
      val recipient = viewModel.recipientSnapshot ?: return
      AttachmentManager.selectPayment(this@ConversationFragment, recipient)
    }

    override fun onScheduledIndicatorClicked(view: View, conversationMessage: ConversationMessage) = Unit

    override fun onUrlClicked(url: String): Boolean {
      return CommunicationActions.handlePotentialGroupLinkUrl(requireActivity(), url) ||
        CommunicationActions.handlePotentialProxyLinkUrl(requireActivity(), url)
    }

    override fun onViewGiftBadgeClicked(messageRecord: MessageRecord) {
      if (!messageRecord.hasGiftBadge()) {
        return
      }

      if (messageRecord.isOutgoing) {
        ViewSentGiftBottomSheet.show(childFragmentManager, (messageRecord as MmsMessageRecord))
      } else {
        ViewReceivedGiftBottomSheet.show(childFragmentManager, (messageRecord as MmsMessageRecord))
      }
    }

    override fun onGiftBadgeRevealed(messageRecord: MessageRecord) {
      viewModel.markGiftBadgeRevealed(messageRecord)
    }

    override fun goToMediaPreview(parent: ConversationItem, sharedElement: View, args: MediaIntentFactory.MediaPreviewArgs) {
      if (this@ConversationFragment.args.conversationScreenType.isInBubble) {
        val recipient = viewModel.recipientSnapshot ?: return
        val intent = ConversationIntents.createBuilderSync(requireActivity(), recipient.id, viewModel.threadId)
          .withStartingPosition(binding.conversationItemRecycler.getChildAdapterPosition(parent))
          .build()

        requireActivity().startActivity(intent)
        requireActivity().startActivity(MediaIntentFactory.create(requireActivity(), args.skipSharedElementTransition(true)))
        return
      }

      if (args.isVideoGif) {
        val adapterPosition: Int = binding.conversationItemRecycler.getChildAdapterPosition(parent)
        val holder: GiphyMp4ProjectionPlayerHolder? = giphyMp4ProjectionRecycler.getCurrentHolder(adapterPosition)
        if (holder != null) {
          parent.showProjectionArea()
          holder.hide()
        }
      }

      container.hideAll(composeText)

      sharedElement.transitionName = MediaPreviewV2Activity.SHARED_ELEMENT_TRANSITION_NAME
      requireActivity().setExitSharedElementCallback(MaterialContainerTransformSharedElementCallback())
      val options = ActivityOptions.makeSceneTransitionAnimation(requireActivity(), sharedElement, MediaPreviewV2Activity.SHARED_ELEMENT_TRANSITION_NAME)
      requireActivity().startActivity(MediaIntentFactory.create(requireActivity(), args), options.toBundle())
    }

    override fun onEditedIndicatorClicked(messageRecord: MessageRecord) {
      if (messageRecord.isOutgoing) {
        EditMessageHistoryDialog.show(childFragmentManager, messageRecord.toRecipient.id, messageRecord)
      } else {
        EditMessageHistoryDialog.show(childFragmentManager, messageRecord.fromRecipient.id, messageRecord)
      }
    }

    override fun onItemClick(item: MultiselectPart) {
      if (actionMode != null) {
        adapter.toggleSelection(item)
        binding.conversationItemRecycler.invalidateItemDecorations()

        if (adapter.selectedItems.isEmpty()) {
          actionMode?.finish()
        } else {
          setCorrectActionModeMenuVisibility()
          actionMode?.title = calculateSelectedItemCount()
        }
      }
    }

    override fun onItemLongClick(itemView: View, item: MultiselectPart) {
      Log.d(TAG, "onItemLongClick")
      if (actionMode != null) return

      val messageRecord = item.getMessageRecord()
      val recipient = viewModel.recipientSnapshot ?: return

      if (isUnopenedGift(itemView, messageRecord)) {
        return
      }

      if (messageRecord.isValidReactionTarget() &&
        !recipient.isBlocked &&
        !viewModel.hasMessageRequestState &&
        (!recipient.isGroup || recipient.isActiveGroup) &&
        adapter.selectedItems.isEmpty()
      ) {
        multiselectItemDecoration.setFocusedItem(MultiselectPart.Message(item.conversationMessage))
        binding.conversationItemRecycler.invalidateItemDecorations()
        binding.reactionsShade.visibility = View.VISIBLE
        binding.conversationItemRecycler.suppressLayout(true)

        val target: InteractiveConversationElement? = if (itemView is InteractiveConversationElement) {
          itemView
        } else {
          val viewHolder = binding.conversationItemRecycler.getChildViewHolder(itemView)
          if (viewHolder is InteractiveConversationElement) {
            viewHolder
          } else {
            null
          }
        }

        if (target != null) {
          val audioUri = messageRecord.getAudioUriForLongClick()
          if (audioUri != null) {
            getVoiceNoteMediaController().pausePlayback(audioUri)
          }

          val childAdapterPosition = target.getAdapterPosition(binding.conversationItemRecycler)
          var mp4Holder: GiphyMp4ProjectionPlayerHolder? = null
          var videoBitmap: Bitmap? = null
          if (childAdapterPosition != RecyclerView.NO_POSITION) {
            mp4Holder = giphyMp4ProjectionRecycler.getCurrentHolder(childAdapterPosition)
            if (mp4Holder?.isVisible == true) {
              mp4Holder.pause()
              videoBitmap = mp4Holder.bitmap
              mp4Holder.hide()
            }
          }

          val snapshot = ConversationItemSelection.snapshotView(target, binding.conversationItemRecycler, messageRecord, videoBitmap)

          val focusedView = if (container.isInputShowing || !container.isKeyboardShowing) null else itemView.rootView.findFocus()
          val bodyBubble = target.bubbleView
          val selectedConversationModel = SelectedConversationModel(
            bitmap = snapshot,
            itemX = itemView.x,
            itemY = itemView.y + binding.conversationItemRecycler.translationY,
            bubbleY = bodyBubble.y,
            bubbleWidth = bodyBubble.width,
            audioUri = audioUri,
            isOutgoing = messageRecord.isOutgoing,
            focusedView = focusedView,
            snapshotMetrics = target.getSnapshotStrategy()?.snapshotMetrics ?: InteractiveConversationElement.SnapshotMetrics(
              snapshotOffset = bodyBubble.x,
              contextMenuPadding = bodyBubble.x
            )
          )

          bodyBubble.visibility = View.INVISIBLE
          target.reactionsView.visibility = View.INVISIBLE

          val quotedIndicatorVisible = target.quotedIndicatorView?.visibility == View.VISIBLE
          if (quotedIndicatorVisible) {
            ViewUtil.fadeOut(target.quotedIndicatorView!!, 150, View.INVISIBLE)
          }

          container.hideKeyboard(composeText, keepHeightOverride = true)

          viewModel.setHideScrollButtonsForReactionOverlay(true)

          val targetViews: InteractiveConversationElement = target
          handleReaction(
            item.conversationMessage,
            ReactionsToolbarListener(item.conversationMessage),
            selectedConversationModel,
            object : OnHideListener {
              override fun startHide(focusedView: View?) {
                multiselectItemDecoration.hideShade(binding.conversationItemRecycler)
                ViewUtil.fadeOut(binding.reactionsShade, resources.getInteger(R.integer.reaction_scrubber_hide_duration), View.GONE)

                if (focusedView == composeText) {
                  container.showSoftkey(composeText)
                }
              }

              override fun onHide() {
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED)) {
                  return
                }

                binding.conversationItemRecycler.suppressLayout(false)
                if (selectedConversationModel.audioUri != null) {
                  getVoiceNoteMediaController().resumePlayback(selectedConversationModel.audioUri, messageRecord.getId())
                }

                if (activity != null) {
                  WindowUtil.setLightStatusBarFromTheme(requireActivity())
                  WindowUtil.setLightNavigationBarFromTheme(requireActivity())
                }

                clearFocusedItem()

                if (mp4Holder != null) {
                  mp4Holder.show()
                  mp4Holder.resume()
                }

                bodyBubble.visibility = View.VISIBLE
                targetViews.reactionsView.visibility = View.VISIBLE

                if (quotedIndicatorVisible && targetViews.quotedIndicatorView != null) {
                  ViewUtil.fadeIn(targetViews.quotedIndicatorView!!, 150)
                }

                viewModel.setHideScrollButtonsForReactionOverlay(false)
              }
            }
          )
        }
      } else {
        clearFocusedItem()
        adapter.toggleSelection(item)
        binding.conversationItemRecycler.invalidateItemDecorations()

        actionMode = (requireActivity() as AppCompatActivity).startSupportActionMode(actionModeCallback)
      }
    }

    override fun onShowGroupDescriptionClicked(groupName: String, description: String, shouldLinkifyWebLinks: Boolean) {
      GroupDescriptionDialog.show(childFragmentManager, groupName, description, shouldLinkifyWebLinks)
    }

    override fun onJoinCallLink(callLinkRootKey: CallLinkRootKey) {
      CommunicationActions.startVideoCall(this@ConversationFragment, callLinkRootKey)
    }

    override fun onShowSafetyTips(forGroup: Boolean) {
      SafetyTipsBottomSheetDialog.show(childFragmentManager, forGroup)
    }

    override fun onReportSpamLearnMoreClicked() {
      MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.ConversationFragment_reported_spam)
        .setMessage(R.string.ConversationFragment_reported_spam_message)
        .setPositiveButton(android.R.string.ok, null)
        .show()
    }

    override fun onMessageRequestAcceptOptionsClicked() {
      val recipient: Recipient? = viewModel.recipientSnapshot

      if (recipient != null) {
        MaterialAlertDialogBuilder(requireContext())
          .setMessage(getString(R.string.ConversationFragment_you_accepted_a_message_request_from_s, recipient.getDisplayName(requireContext())))
          .setPositiveButton(R.string.ConversationFragment_block) { _, _ -> onBlock() }
          .setNegativeButton(R.string.ConversationFragment_report_spam) { _, _ -> onReportSpam() }
          .setNeutralButton(R.string.ConversationFragment__cancel, null)
          .show()
      }
    }

    private fun MessageRecord.getAudioUriForLongClick(): Uri? {
      val playbackState = getVoiceNoteMediaController().voiceNotePlaybackState.value
      if (playbackState == null || !playbackState.isPlaying) {
        return null
      }

      if (hasAudio() || !isMms) {
        return null
      }

      val uri = (this as MmsMessageRecord).slideDeck.audioSlide?.uri
      return uri.takeIf { it == playbackState.uri }
    }
  }

  private inner class ConversationOptionsMenuCallback : ConversationOptionsMenu.Callback {

    override fun getSnapshot(): ConversationOptionsMenu.Snapshot {
      val recipient: Recipient? = viewModel.recipientSnapshot
      return ConversationOptionsMenu.Snapshot(
        recipient = recipient,
        isPushAvailable = viewModel.isPushAvailable,
        canShowAsBubble = viewModel.canShowAsBubble(requireContext()),
        isActiveGroup = recipient?.isActiveGroup == true,
        isActiveV2Group = recipient?.let { it.isActiveGroup && it.isPushV2Group } == true,
        isInActiveGroup = recipient?.isActiveGroup == false,
        hasActiveGroupCall = groupCallViewModel.hasOngoingGroupCallSnapshot,
        distributionType = args.distributionType,
        threadId = args.threadId,
        messageRequestState = viewModel.messageRequestState,
        isInBubble = args.conversationScreenType.isInBubble
      )
    }

    override fun isTextHighlighted(): Boolean {
      return composeText.isTextHighlighted
    }

    override fun onOptionsMenuCreated(menu: Menu) {
      searchMenuItem = menu.findItem(R.id.menu_search)

      val searchView: SearchView = searchMenuItem!!.actionView as SearchView
      val queryListener: SearchView.OnQueryTextListener = object : SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(query: String): Boolean {
          searchViewModel.onQueryUpdated(query, args.threadId, true)
          searchNav.showLoading()
          viewModel.setSearchQuery(query)
          return true
        }

        override fun onQueryTextChange(newText: String): Boolean {
          searchViewModel.onQueryUpdated(newText, args.threadId, false)
          searchNav.showLoading()
          viewModel.setSearchQuery(newText)
          return true
        }
      }

      searchMenuItem!!.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
        override fun onMenuItemActionExpand(item: MenuItem): Boolean {
          searchView.setOnQueryTextListener(queryListener)
          isSearchRequested = true
          searchViewModel.onSearchOpened()
          searchNav.visible = true
          searchNav.setData(0, 0)
          inputPanel.setHideForSearch(true)

          (0 until menu.size()).forEach {
            if (menu.getItem(it) != searchMenuItem) {
              menu.getItem(it).isVisible = false
            }
          }

          return true
        }

        override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
          searchView.setOnQueryTextListener(null)
          isSearchRequested = false
          searchViewModel.onSearchClosed()
          searchNav.visible = false
          inputPanel.setHideForSearch(false)
          viewModel.setSearchQuery(null)
          invalidateOptionsMenu()
          return true
        }
      })

      searchView.maxWidth = Integer.MAX_VALUE

      if (isSearchRequested) {
        if (searchMenuItem!!.expandActionView()) {
          searchViewModel.onSearchOpened()
        }
      }

      val toolbarTextAndIconColor = ContextCompat.getColor(
        requireContext(),
        if (viewModel.wallpaperSnapshot != null) {
          R.color.signal_colorNeutralInverse
        } else {
          R.color.signal_colorOnSurface
        }
      )

      binding.toolbar.setActionItemTint(toolbarTextAndIconColor)
    }

    override fun handleVideo() {
      this@ConversationFragment.handleVideoCall()
    }

    override fun handleDial() {
      val recipient: Recipient = viewModel.recipientSnapshot ?: return
      CommunicationActions.startVoiceCall(this@ConversationFragment, recipient)
    }

    override fun handleViewMedia() {
      startActivity(MediaOverviewActivity.forThread(requireContext(), args.threadId))
    }

    override fun handleAddShortcut() {
      val recipient: Recipient = viewModel.recipientSnapshot ?: return
      Log.i(TAG, "Creating home screen shortcut for recipient ${recipient.id}")

      if (pinnedShortcutReceiver == null) {
        pinnedShortcutReceiver = object : BroadcastReceiver() {
          override fun onReceive(context: Context, intent: Intent?) {
            toast(
              toastTextId = R.string.ConversationActivity_added_to_home_screen,
              toastDuration = Toast.LENGTH_LONG
            )
          }
        }

        requireActivity().registerReceiver(pinnedShortcutReceiver, IntentFilter(ACTION_PINNED_SHORTCUT))
      }

      viewModel.getContactPhotoIcon(requireContext(), Glide.with(this@ConversationFragment))
        .subscribe { infoCompat ->
          val intent = Intent(ACTION_PINNED_SHORTCUT)
          val callback = PendingIntent.getBroadcast(requireContext(), 902, intent, PendingIntentFlags.mutable())
          ShortcutManagerCompat.requestPinShortcut(requireContext(), infoCompat, callback.intentSender)
        }
        .addTo(disposables)
    }

    override fun handleSearch() {
      searchViewModel.onSearchOpened()
    }

    override fun handleAddToContacts() {
      val recipient = viewModel.recipientSnapshot?.takeIf { it.isIndividual } ?: return

      AddToContactsContract.createIntentAndLaunch(
        fragment = this@ConversationFragment,
        launcher = addToContactsLauncher,
        recipient = recipient
      )
    }

    override fun handleDisplayGroupRecipients() {
      val recipientSnapshot = viewModel.recipientSnapshot?.takeIf { it.isGroup } ?: return
      GroupMembersDialog(requireActivity(), recipientSnapshot).display()
    }

    override fun handleManageGroup() {
      val recipient = viewModel.recipientSnapshot ?: return
      val intent = ConversationSettingsActivity.forGroup(requireContext(), recipient.requireGroupId())
      val bundle = ConversationSettingsActivity.createTransitionBundle(
        requireContext(),
        binding.conversationTitleView.root.findViewById(R.id.contact_photo_image),
        binding.toolbar
      )

      ActivityCompat.startActivity(requireContext(), intent, bundle)
    }

    override fun handleLeavePushGroup() {
      val recipient = viewModel.recipientSnapshot
      if (recipient == null) {
        toast(R.string.ConversationActivity_invalid_recipient, toastDuration = Toast.LENGTH_LONG)
        return
      }

      LeaveGroupDialog.handleLeavePushGroup(
        requireActivity(),
        recipient.requireGroupId().requirePush()
      ) { requireActivity().finish() }
    }

    override fun handleInviteLink() {
      val recipient = viewModel.recipientSnapshot ?: return

      InviteActions.inviteUserToSignal(
        context = requireContext(),
        recipient = recipient,
        appendInviteToComposer = composeText::appendInvite,
        launchIntent = this@ConversationFragment::startActivity
      )
    }

    override fun handleMuteNotifications() {
      MuteDialog.show(requireContext(), viewModel::muteConversation)
    }

    override fun handleUnmuteNotifications() {
      viewModel.muteConversation(0L)
    }

    override fun handleConversationSettings() {
      val recipient = viewModel.recipientSnapshot ?: return
      if (recipient.isGroup) {
        handleManageGroup()
        return
      }

      if (viewModel.hasMessageRequestState && !recipient.isBlocked) {
        return
      }

      val intent = ConversationSettingsActivity.forRecipient(requireContext(), recipient.id)
      val bundle = ConversationSettingsActivity.createTransitionBundle(
        requireActivity(),
        binding.conversationTitleView.root.findViewById(R.id.contact_photo_image),
        binding.toolbar
      )

      ActivityCompat.startActivity(requireActivity(), intent, bundle)
    }

    override fun handleSelectMessageExpiration() {
      val recipient = viewModel.recipientSnapshot ?: return
      if (recipient.isPushGroup && !recipient.isActiveGroup) {
        return
      }

      startActivity(RecipientDisappearingMessagesActivity.forRecipient(requireContext(), recipient.id))
    }

    override fun handleCreateBubble() {
      val recipientId = viewModel.recipientSnapshot?.id ?: return

      BubbleUtil.displayAsBubble(requireContext(), recipientId, args.threadId)
      requireActivity().finish()
    }

    override fun handleGoHome() {
      requireActivity().finish()
    }

    override fun showExpiring(recipient: Recipient) = Unit
    override fun clearExpiring() = Unit

    override fun handleFormatText(id: Int) {
      composeText.handleFormatText(id)
    }

    override fun handleBlock() {
      onBlock()
    }

    override fun handleUnblock() {
      onUnblock()
    }

    override fun handleReportSpam() {
      onReportSpam()
    }

    override fun handleMessageRequestAccept() {
      onMessageRequestAccept()
    }

    override fun handleDeleteConversation() {
      onDeleteConversation()
    }
  }

  private inner class OnReactionsSelectedListener : ConversationReactionOverlay.OnReactionSelectedListener {
    override fun onReactionSelected(messageRecord: MessageRecord, emoji: String?) {
      reactionDelegate.hide()

      if (emoji != null) {
        disposables += viewModel.updateReaction(messageRecord, emoji).subscribe()
      }
    }

    override fun onCustomReactionSelected(messageRecord: MessageRecord, hasAddedCustomEmoji: Boolean) {
      reactionDelegate.hide()
      disposables += viewModel.updateCustomReaction(messageRecord, hasAddedCustomEmoji)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeBy(
          onSuccess = {
            ReactWithAnyEmojiBottomSheetDialogFragment
              .createForMessageRecord(messageRecord, -1)
              .show(childFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
          }
        )
    }
  }

  private inner class MotionEventRelayDrain(lifecycleOwner: LifecycleOwner) : MotionEventRelay.Drain {
    private val lifecycle = lifecycleOwner.lifecycle

    override fun accept(motionEvent: MotionEvent): Boolean {
      return if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
        reactionDelegate.applyTouchEvent(motionEvent)
      } else {
        false
      }
    }
  }

  private inner class ReactionsToolbarListener(
    private val conversationMessage: ConversationMessage
  ) : OnActionSelectedListener {
    override fun onActionSelected(action: ConversationReactionOverlay.Action) {
      when (action) {
        ConversationReactionOverlay.Action.REPLY -> handleReplyToMessage(conversationMessage)
        ConversationReactionOverlay.Action.EDIT -> handleEditMessage(conversationMessage)
        ConversationReactionOverlay.Action.FORWARD -> handleForwardMessageParts(conversationMessage.multiselectCollection.toSet())
        ConversationReactionOverlay.Action.RESEND -> handleResend(conversationMessage)
        ConversationReactionOverlay.Action.DOWNLOAD -> handleSaveAttachment(conversationMessage.messageRecord as MmsMessageRecord)
        ConversationReactionOverlay.Action.COPY -> handleCopyMessage(conversationMessage.multiselectCollection.toSet())
        ConversationReactionOverlay.Action.MULTISELECT -> handleEnterMultiselect(conversationMessage)
        ConversationReactionOverlay.Action.PAYMENT_DETAILS -> handleViewPaymentDetails(conversationMessage)
        ConversationReactionOverlay.Action.VIEW_INFO -> handleDisplayDetails(conversationMessage)
        ConversationReactionOverlay.Action.DELETE -> handleDeleteMessages(conversationMessage.multiselectCollection.toSet())
      }
    }
  }

  inner class ActionModeCallback : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
      mode.title = calculateSelectedItemCount()

      searchMenuItem?.collapseActionView()
      binding.toolbar.visible = false
      if (scheduledMessagesStub.isVisible) {
        reShowScheduleMessagesBar = true
        scheduledMessagesStub.visibility = View.GONE
      }

      setCorrectActionModeMenuVisibility()
      return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false

    override fun onDestroyActionMode(mode: ActionMode) {
      adapter.clearSelection()
      setBottomActionBarVisibility(false)

      binding.toolbar.visible = true
      if (reShowScheduleMessagesBar) {
        scheduledMessagesStub.visibility = View.VISIBLE
        reShowScheduleMessagesBar = false
      }

      binding.conversationItemRecycler.invalidateItemDecorations()
      actionMode = null
    }
  }
  // endregion Conversation Callbacks

  //region Activity Results Callbacks

  private inner class ActivityResultCallbacks : ConversationActivityResultContracts.Callbacks {
    override fun onSendContacts(contacts: List<Contact>) {
      sendMessageWithoutComposeInput(
        contacts = contacts,
        clearCompose = false
      )
    }

    override fun onMediaSend(result: MediaSendActivityResult?) {
      if (result == null) {
        return
      }

      val recipientSnapshot = viewModel.recipientSnapshot
      if (result.recipientId != recipientSnapshot?.id) {
        Log.w(TAG, "Result's recipientId did not match ours! Result: " + result.recipientId + ", Ours: " + recipientSnapshot?.id)
        toast(R.string.ConversationActivity_error_sending_media)
        return
      }

      if (result.isPushPreUpload) {
        sendPreUploadMediaMessage(result)
        return
      }

      val slides: List<Slide> = result.nonUploadedMedia.mapNotNull {
        when {
          MediaUtil.isVideoType(it.mimeType) -> VideoSlide(requireContext(), it.uri, it.size, it.isVideoGif, it.width, it.height, it.caption.orNull(), it.transformProperties.orNull())
          MediaUtil.isGif(it.mimeType) -> GifSlide(requireContext(), it.uri, it.size, it.width, it.height, it.isBorderless, it.caption.orNull())
          MediaUtil.isImageType(it.mimeType) -> ImageSlide(requireContext(), it.uri, it.mimeType, it.size, it.width, it.height, it.isBorderless, it.caption.orNull(), null, it.transformProperties.orNull())
          else -> {
            Log.w(TAG, "Asked to send an unexpected mimeType: '${it.mimeType}'. Skipping.")
            null
          }
        }
      }

      sendMessage(
        body = result.body,
        mentions = result.mentions,
        bodyRanges = result.bodyRanges,
        messageToEdit = null,
        quote = if (result.isViewOnce) null else inputPanel.quote.orNull(),
        scheduledDate = result.scheduledTime,
        slideDeck = SlideDeck().apply { slides.forEach { addSlide(it) } },
        contacts = emptyList(),
        clearCompose = true,
        linkPreviews = emptyList(),
        isViewOnce = result.isViewOnce
      ) {
        viewModel.deleteSlideData(slides)
      }
    }

    private fun sendPreUploadMediaMessage(result: MediaSendActivityResult) {
      sendMessage(
        body = result.body,
        mentions = result.mentions,
        bodyRanges = result.bodyRanges,
        messageToEdit = null,
        quote = if (result.isViewOnce) null else inputPanel.quote.orNull(),
        scheduledDate = result.scheduledTime,
        slideDeck = null,
        contacts = emptyList(),
        clearCompose = true,
        linkPreviews = emptyList(),
        preUploadResults = result.preUploadResults,
        isViewOnce = result.isViewOnce
      )
    }

    override fun onContactSelect(uri: Uri?) {
      val recipient = viewModel.recipientSnapshot
      if (uri != null && recipient != null) {
        conversationActivityResultContracts.launchContactShareEditor(uri, recipient.chatColors)
      }
    }

    override fun onLocationSelected(place: SignalPlace?, uri: Uri?) {
      if (place != null && uri != null) {
        attachmentManager.setLocation(place, uri)
        draftViewModel.setLocationDraft(place)
      } else {
        Log.w(TAG, "Location missing thumbnail")
      }
    }

    override fun onFileSelected(uri: Uri?) {
      if (uri != null) {
        setMedia(uri, SlideFactory.MediaType.DOCUMENT)
      }
    }
  }

  //endregion

  private class LastScrolledPositionUpdater(
    val adapter: ConversationAdapterV2,
    val layoutManager: LinearLayoutManager,
    val viewModel: ConversationViewModel
  ) : DefaultLifecycleObserver {
    override fun onPause(owner: LifecycleOwner) {
      val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
      val firstVisiblePosition = layoutManager.findFirstCompletelyVisibleItemPosition()
      val lastVisibleMessageTimestamp = if (firstVisiblePosition > 0 && lastVisiblePosition != RecyclerView.NO_POSITION) {
        adapter.getLastVisibleConversationMessage(lastVisiblePosition)?.messageRecord?.dateReceived ?: 0L
      } else {
        0L
      }

      viewModel.setLastScrolled(lastVisibleMessageTimestamp)
    }
  }

  //region Conversation Banner Callbacks

  private inner class ConversationBannerListener : ConversationBannerView.Listener {
    override fun updateAppAction() {
      PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(requireContext())
    }

    override fun reRegisterAction() {
      startActivity(RegistrationNavigationActivity.newIntentForReRegistration(requireContext()))
    }

    override fun reviewJoinRequestsAction() {
      viewModel.recipientSnapshot?.let { recipient ->
        val intent = ManagePendingAndRequestingMembersActivity.newIntent(requireContext(), recipient.requireGroupId().requireV2())
        startActivity(intent)
      }
    }

    override fun gv1SuggestionsAction(actionId: Int) {
      if (actionId == R.id.reminder_action_gv1_suggestion_add_members) {
        conversationGroupViewModel.groupRecordSnapshot?.let { groupRecord ->
          GroupsV1MigrationSuggestionsDialog.show(requireActivity(), groupRecord.id.requireV2(), groupRecord.gv1MigrationSuggestions)
        }
      } else if (actionId == R.id.reminder_action_gv1_suggestion_no_thanks) {
        conversationGroupViewModel.onSuggestedMembersBannerDismissed()
      }
    }

    @SuppressLint("InlinedApi")
    override fun changeBubbleSettingAction(disableSetting: Boolean) {
      SignalStore.tooltips().markBubbleOptOutTooltipSeen()

      if (disableSetting) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS)
          .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
      }
    }

    override fun onUnverifiedBannerClicked(unverifiedIdentities: List<IdentityRecord>) {
      if (unverifiedIdentities.size == 1) {
        VerifyIdentityActivity.startOrShowExchangeMessagesDialog(requireContext(), unverifiedIdentities[0], false)
      } else {
        val unverifiedNames = unverifiedIdentities
          .map { Recipient.resolved(it.recipientId).getDisplayName(requireContext()) }
          .toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
          .setIcon(R.drawable.symbol_error_triangle_fill_24)
          .setTitle(R.string.ConversationFragment__no_longer_verified)
          .setItems(unverifiedNames) { _, which: Int -> VerifyIdentityActivity.startOrShowExchangeMessagesDialog(requireContext(), unverifiedIdentities[which], false) }
          .show()
      }
    }

    override fun onUnverifiedBannerDismissed(unverifiedIdentities: List<IdentityRecord>) {
      viewModel.resetVerifiedStatusToDefault(unverifiedIdentities)
    }

    override fun onRequestReviewIndividual(recipientId: RecipientId) {
      ReviewCardDialogFragment.createForReviewRequest(recipientId).show(childFragmentManager, null)
    }

    override fun onReviewGroupMembers(groupId: GroupId.V2) {
      ReviewCardDialogFragment.createForReviewMembers(groupId).show(childFragmentManager, null)
    }
  }

  //endregion

  //region Disabled Input Callbacks

  private inner class DisabledInputListener : DisabledInputView.Listener {
    override fun onUpdateAppClicked() {
      PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(requireContext())
    }

    override fun onReRegisterClicked() {
      startActivity(RegistrationNavigationActivity.newIntentForReRegistration(requireContext()))
    }

    override fun onCancelGroupRequestClicked() {
      conversationGroupViewModel
        .cancelJoinRequest()
        .subscribeBy { result ->
          when (result) {
            is Result.Success -> Log.d(TAG, "Cancel request complete")
            is Result.Failure -> {
              Log.d(TAG, "Cancel join request failed ${result.failure}")
              toast(GroupErrors.getUserDisplayMessage(result.failure))
            }
          }
        }
        .addTo(disposables)
    }

    override fun onShowAdminsBottomSheetDialog() {
      viewModel.recipientSnapshot?.let { recipient ->
        ShowAdminsBottomSheetDialog.show(childFragmentManager, recipient.requireGroupId().requireV2())
      }
    }

    override fun onAcceptMessageRequestClicked() {
      onMessageRequestAccept()
    }

    override fun onDeleteClicked() {
      onDeleteConversation()
    }

    override fun onBlockClicked() {
      onBlock()
    }

    override fun onUnblockClicked() {
      onUnblock()
    }

    override fun onReportSpamClicked() {
      onReportSpam()
    }

    override fun onInviteToSignal(recipient: Recipient) {
      InviteActions.inviteUserToSignal(
        context = requireContext(),
        recipient = recipient,
        appendInviteToComposer = null,
        launchIntent = this@ConversationFragment::startActivity
      )
    }

    override fun onUnmuteReleaseNotesChannel() {
      viewModel.muteConversation(0L)
    }
  }

  //endregion

  //region Compose + Send Callbacks

  private inner class SendButtonListener : View.OnClickListener, OnEditorActionListener, SendButton.ScheduledSendListener {
    override fun onClick(v: View) {
      sendMessage()
    }

    override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent?): Boolean {
      if (actionId == EditorInfo.IME_ACTION_SEND) {
        if (inputPanel.isInEditMode) {
          sendEditButton.performClick()
        } else {
          sendButton.performClick()
        }
        return true
      }
      return false
    }

    override fun onSendScheduled() {
      ScheduleMessageContextMenu.show(sendButton, (requireView() as ViewGroup)) { time ->
        if (time == -1L) {
          showSchedule(childFragmentManager)
        } else {
          sendMessage(scheduledDate = time)
        }
      }
    }

    override fun canSchedule(): Boolean {
      return !(inputPanel.isRecordingInLockedMode || draftViewModel.voiceNoteDraft != null)
    }
  }

  private inner class ComposeTextEventsListener :
    View.OnKeyListener,
    View.OnClickListener,
    TextWatcher,
    OnFocusChangeListener,
    ComposeText.CursorPositionChangedListener,
    ComposeText.StylingChangedListener {

    private var beforeLength = 0
    private var previousText = ""

    var typingStatusEnabled = true

    override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
      if (event.action == KeyEvent.ACTION_DOWN) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
          if (SignalStore.settings().isEnterKeySends || event.isCtrlPressed) {
            sendButton.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            sendButton.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            return true
          }
        }
      }
      return false
    }

    override fun onClick(v: View) {
      container.showSoftkey(composeText)
    }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
      beforeLength = composeText.textTrimmed.length
    }

    override fun afterTextChanged(s: Editable) {
      calculateCharactersRemaining()
      if (composeText.textTrimmed.isEmpty() || beforeLength == 0) {
        composeText.postDelayed({
          if (lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            updateToggleButtonState()
          }
        }, 50)
      }

      if (!inputPanel.inEditMessageMode()) {
        stickerViewModel.onInputTextUpdated(s.toString())
      } else {
        stickerViewModel.onInputTextUpdated("")
      }
    }

    override fun onFocusChange(v: View, hasFocus: Boolean) {
      if (hasFocus) { // && container.getCurrentInput() == emojiDrawerStub.get()) {
        container.showSoftkey(composeText)
      }
    }

    override fun onCursorPositionChanged(start: Int, end: Int) {
      linkPreviewViewModel.onTextChanged(composeText.textTrimmed.toString(), start, end)
    }

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
      handleSaveDraftOnTextChange(composeText.textTrimmed)
      handleTypingIndicatorOnTextChange(s.toString())
    }

    private fun handleSaveDraftOnTextChange(text: CharSequence) {
      textDraftSaveDebouncer.publish {
        if (inputPanel.inEditMessageMode()) {
          draftViewModel.setMessageEditDraft(inputPanel.editMessageId!!, text.toString(), MentionAnnotation.getMentionsFromAnnotations(text), getStyling(text))
        } else {
          draftViewModel.setTextDraft(text.toString(), MentionAnnotation.getMentionsFromAnnotations(text), getStyling(text))
        }
      }
    }

    private fun handleTypingIndicatorOnTextChange(text: String) {
      val recipient = viewModel.recipientSnapshot

      if (recipient == null || !typingStatusEnabled || recipient.isBlocked || recipient.isSelf) {
        return
      }

      val typingStatusSender = ApplicationDependencies.getTypingStatusSender()
      if (text.length == 0) {
        typingStatusSender.onTypingStoppedWithNotify(args.threadId)
      } else if (text.length < previousText.length && previousText.contains(text)) {
        typingStatusSender.onTypingStopped(args.threadId)
      } else {
        typingStatusSender.onTypingStarted(args.threadId)
      }

      previousText = text
    }

    override fun onStylingChanged() {
      handleSaveDraftOnTextChange(composeText.textTrimmed)
    }
  }

  //endregion Compose + Send Callbacks

  //region Input Panel Callbacks

  private inner class InputPanelListener : InputPanel.Listener {
    override fun onVoiceNoteDraftPlay(audioUri: Uri, progress: Double) {
      getVoiceNoteMediaController().startSinglePlaybackForDraft(audioUri, args.threadId, progress)
    }

    override fun onVoiceNoteDraftSeekTo(audioUri: Uri, progress: Double) {
      getVoiceNoteMediaController().seekToPosition(audioUri, progress)
    }

    override fun onVoiceNoteDraftPause(audioUri: Uri) {
      getVoiceNoteMediaController().pausePlayback(audioUri)
    }

    override fun onVoiceNoteDraftDelete(audioUri: Uri) {
      getVoiceNoteMediaController().stopPlaybackAndReset(audioUri)
      draftViewModel.deleteVoiceNoteDraft()
    }

    override fun onRecorderStarted() {
      voiceMessageRecordingDelegate.onRecorderStarted()
    }

    override fun onRecorderLocked() {
      updateToggleButtonState()
      voiceMessageRecordingDelegate.onRecorderLocked()
    }

    override fun onRecorderFinished() {
      updateToggleButtonState()
      voiceMessageRecordingDelegate.onRecorderFinished()
    }

    override fun onRecorderCanceled(byUser: Boolean) {
      if (lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
        updateToggleButtonState()
      }
      voiceMessageRecordingDelegate.onRecorderCanceled(byUser)
    }

    override fun onRecorderPermissionRequired() {
      Permissions
        .with(this@ConversationFragment)
        .request(Manifest.permission.RECORD_AUDIO)
        .ifNecessary()
        .withRationaleDialog(getString(R.string.ConversationActivity_to_send_audio_messages_allow_signal_access_to_your_microphone), R.drawable.ic_mic_solid_24)
        .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_requires_the_microphone_permission_in_order_to_send_audio_messages))
        .execute()
    }

    override fun onEmojiToggle() {
      container.toggleInput(MediaKeyboardFragmentCreator, composeText, showSoftKeyOnHide = true)
    }

    override fun onLinkPreviewCanceled() {
      linkPreviewViewModel.onUserCancel()
    }

    override fun onStickerSuggestionSelected(sticker: StickerRecord) {
      sendSticker(
        stickerRecord = sticker,
        clearCompose = true
      )
    }

    override fun onQuoteChanged(id: Long, author: RecipientId) {
      draftViewModel.setQuoteDraft(id, author)
    }

    override fun onQuoteCleared() {
      draftViewModel.clearQuoteDraft()
    }

    override fun onEnterEditMode() {
      updateToggleButtonState()
      previousPages = keyboardPagerViewModel.pages().value
      keyboardPagerViewModel.setOnlyPage(KeyboardPage.EMOJI)
      onKeyboardChanged(KeyboardPage.EMOJI)
      stickerViewModel.onInputTextUpdated("")
      updateLinkPreviewState()
    }

    override fun onExitEditMode() {
      updateToggleButtonState()
      draftViewModel.deleteMessageEditDraft()
      if (previousPages != null) {
        keyboardPagerViewModel.setPages(previousPages!!)
        previousPages = null
      }
      updateLinkPreviewState()
    }

    override fun onQuickCameraToggleClicked() {
      val recipientId = viewModel.recipientSnapshot?.id ?: return
      composeText.clearFocus()
      conversationActivityResultContracts.launchCamera(recipientId, inputPanel.quote.isPresent)
    }
  }

  private inner class InputPanelMediaListener : InputPanel.MediaListener {
    override fun onMediaSelected(uri: Uri, contentType: String?) {
      if (MediaUtil.isGif(contentType) || MediaUtil.isImageType(contentType)) {
        disposables += viewModel.getKeyboardImageDetails(uri)
          .observeOn(AndroidSchedulers.mainThread())
          .subscribeBy(
            onSuccess = {
              sendKeyboardImage(uri, contentType!!, it)
            },
            onComplete = {
              sendKeyboardImage(uri, contentType!!, null)
            }
          )
      } else if (MediaUtil.isVideoType(contentType)) {
        setMedia(uri, SlideFactory.MediaType.VIDEO)
      } else {
        setMedia(uri, SlideFactory.MediaType.AUDIO)
      }
    }

    private fun sendKeyboardImage(uri: Uri, contentType: String, keyboardImageDetails: KeyboardUtil.ImageDetails?) {
      if (keyboardImageDetails == null || !keyboardImageDetails.hasTransparency) {
        setMedia(uri, requireNotNull(SlideFactory.MediaType.from(contentType)))
        return
      }

      val slide: Slide = when {
        MediaUtil.isGif(contentType) -> GifSlide(requireContext(), uri, 0, keyboardImageDetails.width, keyboardImageDetails.height, true, null)
        MediaUtil.isImageType(contentType) -> ImageSlide(requireContext(), uri, contentType, 0, keyboardImageDetails.width, keyboardImageDetails.height, true, null, null)
        else -> null
      } ?: error("Only images are supported!")

      sendMessageWithoutComposeInput(slide = slide)
    }
  }

  //endregion

  //region Attachment + Media Keyboard

  private inner class AttachmentManagerListener : AttachmentManager.AttachmentListener {
    override fun onAttachmentChanged() {
      updateToggleButtonState()
      updateLinkPreviewState()
    }

    override fun onLocationRemoved() {
      draftViewModel.clearLocationDraft()
    }
  }

  private object AttachmentKeyboardFragmentCreator : InputAwareConstraintLayout.FragmentCreator {
    override val id: Int = 1
    override fun create(): Fragment = AttachmentKeyboardFragment()
  }

  private inner class AttachmentKeyboardFragmentListener : FragmentResultListener {
    @Suppress("DEPRECATION")
    override fun onFragmentResult(requestKey: String, result: Bundle) {
      val recipient = viewModel.recipientSnapshot ?: return
      val button: AttachmentKeyboardButton? = result.getSerializable(AttachmentKeyboardFragment.BUTTON_RESULT) as? AttachmentKeyboardButton
      val media: Media? = result.getParcelable(AttachmentKeyboardFragment.MEDIA_RESULT)

      if (button != null) {
        when (button) {
          AttachmentKeyboardButton.GALLERY -> conversationActivityResultContracts.launchGallery(recipient.id, composeText.textTrimmed, inputPanel.quote.isPresent)
          AttachmentKeyboardButton.CONTACT -> conversationActivityResultContracts.launchSelectContact()
          AttachmentKeyboardButton.LOCATION -> conversationActivityResultContracts.launchSelectLocation(recipient.chatColors)
          AttachmentKeyboardButton.PAYMENT -> AttachmentManager.selectPayment(this@ConversationFragment, recipient)
          AttachmentKeyboardButton.FILE -> {
            if (!conversationActivityResultContracts.launchSelectFile()) {
              toast(R.string.AttachmentManager_cant_open_media_selection, Toast.LENGTH_LONG)
            }
          }
        }
      } else if (media != null) {
        conversationActivityResultContracts.launchMediaEditor(listOf(media), recipient.id, composeText.textTrimmed)
      }

      container.hideInput()
    }
  }

  private object MediaKeyboardFragmentCreator : InputAwareConstraintLayout.FragmentCreator {
    override val id: Int = 2
    override fun create(): Fragment = KeyboardPagerFragment()
  }

  private inner class KeyboardEvents : OnBackPressedCallback(false), InputAwareConstraintLayout.Listener, InsetAwareConstraintLayout.KeyboardStateListener {
    override fun handleOnBackPressed() {
      container.hideInput()
    }

    override fun onInputShown() {
      isEnabled = true
    }

    override fun onInputHidden() {
      isEnabled = false
    }

    override fun onKeyboardShown() = Unit

    override fun onKeyboardHidden() {
      closeEmojiSearch()

      if (searchMenuItem?.isActionViewExpanded == true && searchMenuItem?.actionView?.hasFocus() == true) {
        searchMenuItem?.actionView?.clearFocus()
      }
    }
  }

  //endregion

  //region Event Bus

  @Subscribe(threadMode = ThreadMode.POSTING)
  fun onIdentityRecordUpdate(event: IdentityRecord?) {
    viewModel.updateIdentityRecordsInBackground()
  }

  @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
  fun onStickerPackInstalled(event: StickerPackInstallEvent?) {
    if (event == null) {
      return
    }

    if (!TextSecurePreferences.hasSeenStickerIntroTooltip(requireContext())) {
      return
    }

    EventBus.getDefault().removeStickyEvent(event)

    if (!inputPanel.isStickerMode) {
      conversationTooltips.displayStickerPackInstalledTooltip(inputPanel.mediaKeyboardToggleAnchorView, event)
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
  fun onGroupCallPeekEvent(groupCallPeekEvent: GroupCallPeekEvent) {
    groupCallViewModel.onGroupCallPeekEvent(groupCallPeekEvent)
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onReminderUpdateEvent(reminderUpdateEvent: ReminderUpdateEvent) {
    viewModel.refreshReminder()
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onRecaptchaRequiredEvent(recaptchaRequiredEvent: RecaptchaRequiredEvent) {
    RecaptchaProofBottomSheetFragment.show(childFragmentManager)
  }

  //endregion

  private inner class SearchEventListener : ConversationSearchBottomBar.EventListener {
    override fun onSearchMoveUpPressed() {
      searchViewModel.onMoveUp()
    }

    override fun onSearchMoveDownPressed() {
      searchViewModel.onMoveDown()
    }
  }

  private inner class ToolbarDependentMarginListener(private val toolbar: Toolbar) : ViewTreeObserver.OnGlobalLayoutListener {

    init {
      toolbar.viewTreeObserver.addOnGlobalLayoutListener(this)
    }

    override fun onGlobalLayout() {
      if (!isAdded || view == null) {
        return
      }

      val rect = Rect()
      toolbar.getGlobalVisibleRect(rect)
      threadHeaderMarginDecoration.toolbarMargin = rect.bottom + 16.dp
      binding.conversationItemRecycler.invalidateItemDecorations()
      toolbar.viewTreeObserver.removeOnGlobalLayoutListener(this)
    }
  }

  private inner class ThreadHeaderMarginDecoration : RecyclerView.ItemDecoration() {
    var toolbarMargin: Int = 0

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
      super.getItemOffsets(outRect, view, parent, state)
      if (view is ConversationHeaderView) {
        outRect.top = toolbarMargin
      }
    }
  }

  private inner class VoiceMessageRecordingSessionCallbacks : VoiceMessageRecordingDelegate.SessionCallback {
    override fun onSessionWillBegin() {
      getVoiceNoteMediaController().pausePlayback()
    }

    override fun sendVoiceNote(draft: VoiceNoteDraft) {
      val audioSlide = AudioSlide(draft.uri, draft.size, MediaUtil.AUDIO_AAC, true)

      sendMessageWithoutComposeInput(
        slide = audioSlide,
        quote = inputPanel.quote.orNull()
      )
    }

    override fun cancelEphemeralVoiceNoteDraft(draft: VoiceNoteDraft) {
      draftViewModel.cancelEphemeralVoiceNoteDraft(draft.asDraft())
    }

    override fun saveEphemeralVoiceNoteDraft(draft: VoiceNoteDraft) {
      draftViewModel.saveEphemeralVoiceNoteDraft(draft.asDraft())
    }
  }

  private inner class VoiceNotePlayerViewListener : VoiceNotePlayerView.Listener {
    override fun onCloseRequested(uri: Uri) {
      getVoiceNoteMediaController().stopPlaybackAndReset(uri)
    }

    override fun onSpeedChangeRequested(uri: Uri, speed: Float) {
      getVoiceNoteMediaController().setPlaybackSpeed(uri, speed)
    }

    override fun onPlay(uri: Uri, messageId: Long, position: Double) {
      getVoiceNoteMediaController().startSinglePlayback(uri, messageId, position)
    }

    override fun onPause(uri: Uri) {
      getVoiceNoteMediaController().pausePlayback(uri)
    }

    override fun onNavigateToMessage(threadId: Long, threadRecipientId: RecipientId, senderId: RecipientId, messageTimestamp: Long, messagePositionInThread: Long) {
      if (threadId != viewModel.threadId) {
        startActivity(
          ConversationIntents.createBuilderSync(requireActivity(), threadRecipientId, threadId)
            .withStartingPosition(messagePositionInThread.toInt())
            .build()
        )
      } else {
        viewModel
          .moveToMessage(messageTimestamp, senderId)
          .subscribeBy {
            moveToPosition(it)
          }
          .addTo(disposables)
      }
    }
  }
}
