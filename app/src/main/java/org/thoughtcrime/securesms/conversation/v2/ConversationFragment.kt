/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.ViewTreeObserver
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
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.doOnNextLayout
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar.Duration
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.signal.core.util.Result
import org.signal.core.util.ThreadUtil
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.concurrent.addTo
import org.signal.core.util.dp
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.signal.libsignal.protocol.InvalidMessageException
import org.thoughtcrime.securesms.BlockUnblockDialog
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.gifts.OpenableGift
import org.thoughtcrime.securesms.badges.gifts.OpenableGiftItemDecoration
import org.thoughtcrime.securesms.badges.gifts.flow.GiftFlowActivity
import org.thoughtcrime.securesms.badges.gifts.viewgift.received.ViewReceivedGiftBottomSheet
import org.thoughtcrime.securesms.badges.gifts.viewgift.sent.ViewSentGiftBottomSheet
import org.thoughtcrime.securesms.components.AnimatingToggle
import org.thoughtcrime.securesms.components.ComposeText
import org.thoughtcrime.securesms.components.HidingLinearLayout
import org.thoughtcrime.securesms.components.InputAwareConstraintLayout
import org.thoughtcrime.securesms.components.InputPanel
import org.thoughtcrime.securesms.components.ScrollToPositionDelegate
import org.thoughtcrime.securesms.components.SendButton
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalBottomActionBar
import org.thoughtcrime.securesms.components.recyclerview.SmoothScrollingLinearLayoutManager
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.DonateToSignalFragment
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.DonateToSignalType
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaControllerOwner
import org.thoughtcrime.securesms.components.voice.VoiceNotePlaybackState
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.contactshare.ContactUtil
import org.thoughtcrime.securesms.contactshare.SharedContactDetailsActivity
import org.thoughtcrime.securesms.conversation.AttachmentKeyboardButton
import org.thoughtcrime.securesms.conversation.BadDecryptLearnMoreDialog
import org.thoughtcrime.securesms.conversation.ConversationAdapter
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.conversation.ConversationIntents.ConversationScreenType
import org.thoughtcrime.securesms.conversation.ConversationItem
import org.thoughtcrime.securesms.conversation.ConversationItemSelection
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.conversation.ConversationOptionsMenu
import org.thoughtcrime.securesms.conversation.ConversationReactionDelegate
import org.thoughtcrime.securesms.conversation.ConversationReactionOverlay
import org.thoughtcrime.securesms.conversation.ConversationReactionOverlay.OnActionSelectedListener
import org.thoughtcrime.securesms.conversation.ConversationReactionOverlay.OnHideListener
import org.thoughtcrime.securesms.conversation.MarkReadHelper
import org.thoughtcrime.securesms.conversation.MenuState
import org.thoughtcrime.securesms.conversation.MessageSendType
import org.thoughtcrime.securesms.conversation.SelectedConversationModel
import org.thoughtcrime.securesms.conversation.ShowAdminsBottomSheetDialog
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.conversation.colors.Colorizer
import org.thoughtcrime.securesms.conversation.colors.RecyclerViewColorizer
import org.thoughtcrime.securesms.conversation.mutiselect.ConversationItemAnimator
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectItemDecoration
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectPart
import org.thoughtcrime.securesms.conversation.quotes.MessageQuotesBottomSheet
import org.thoughtcrime.securesms.conversation.ui.edit.EditMessageHistoryDialog
import org.thoughtcrime.securesms.conversation.ui.error.EnableCallNotificationSettingsDialog
import org.thoughtcrime.securesms.conversation.v2.groups.ConversationGroupCallViewModel
import org.thoughtcrime.securesms.conversation.v2.groups.ConversationGroupViewModel
import org.thoughtcrime.securesms.conversation.v2.keyboard.AttachmentKeyboardFragment
import org.thoughtcrime.securesms.database.model.InMemoryMessageRecord
import org.thoughtcrime.securesms.database.model.MediaMmsMessageRecord
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.Quote
import org.thoughtcrime.securesms.databinding.V2ConversationFragmentBinding
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ItemDecoration
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4PlaybackController
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4PlaybackPolicy
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ProjectionPlayerHolder
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ProjectionRecycler
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupMigrationMembershipChange
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason
import org.thoughtcrime.securesms.groups.ui.GroupErrors
import org.thoughtcrime.securesms.groups.ui.invitesandrequests.invite.GroupLinkInviteFriendsBottomSheetDialogFragment
import org.thoughtcrime.securesms.groups.ui.managegroup.dialogs.GroupDescriptionDialog
import org.thoughtcrime.securesms.groups.ui.migration.GroupsV1MigrationInfoBottomSheetDialogFragment
import org.thoughtcrime.securesms.groups.ui.migration.GroupsV1MigrationInitiationBottomSheetDialogFragment
import org.thoughtcrime.securesms.groups.v2.GroupBlockJoinRequestResult
import org.thoughtcrime.securesms.invites.InviteActions
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.longmessage.LongMessageFragment
import org.thoughtcrime.securesms.mediapreview.MediaIntentFactory
import org.thoughtcrime.securesms.mediapreview.MediaIntentFactory.create
import org.thoughtcrime.securesms.mediapreview.MediaPreviewV2Activity
import org.thoughtcrime.securesms.mediasend.Media
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionActivity
import org.thoughtcrime.securesms.messagedetails.MessageDetailsFragment
import org.thoughtcrime.securesms.messagerequests.MessageRequestRepository
import org.thoughtcrime.securesms.messagerequests.MessageRequestState
import org.thoughtcrime.securesms.mms.AttachmentManager
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.mms.SlideDeck
import org.thoughtcrime.securesms.notifications.v2.ConversationId
import org.thoughtcrime.securesms.payments.preferences.PaymentsActivity
import org.thoughtcrime.securesms.ratelimit.RecaptchaProofBottomSheetFragment
import org.thoughtcrime.securesms.reactions.ReactionsBottomSheetDialogFragment
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientExporter
import org.thoughtcrime.securesms.recipients.RecipientFormattingException
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment
import org.thoughtcrime.securesms.registration.RegistrationNavigationActivity
import org.thoughtcrime.securesms.safety.SafetyNumberBottomSheet
import org.thoughtcrime.securesms.stickers.StickerLocator
import org.thoughtcrime.securesms.stickers.StickerPackPreviewActivity
import org.thoughtcrime.securesms.stories.StoryViewerArgs
import org.thoughtcrime.securesms.stories.viewer.StoryViewerActivity
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.ContextUtil
import org.thoughtcrime.securesms.util.DrawableUtil
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.FullscreenHelper
import org.thoughtcrime.securesms.util.PlayStoreUtil
import org.thoughtcrime.securesms.util.SignalLocalMetrics
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.WindowUtil
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture
import org.thoughtcrime.securesms.util.doAfterNextLayout
import org.thoughtcrime.securesms.util.fragments.requireListener
import org.thoughtcrime.securesms.util.hasAudio
import org.thoughtcrime.securesms.util.hasGiftBadge
import org.thoughtcrime.securesms.util.viewModel
import org.thoughtcrime.securesms.util.views.Stub
import org.thoughtcrime.securesms.util.visible
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper
import org.thoughtcrime.securesms.wallpaper.ChatWallpaperDimLevelUtil
import java.util.Locale
import java.util.concurrent.ExecutionException

/**
 * A single unified fragment for Conversations.
 */
class ConversationFragment : LoggingFragment(R.layout.v2_conversation_fragment) {

  companion object {
    private val TAG = Log.tag(ConversationFragment::class.java)
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
  private val binding by ViewBinderDelegate(V2ConversationFragmentBinding::bind)
  private val viewModel: ConversationViewModel by viewModel {
    ConversationViewModel(
      args.threadId,
      args.startingPosition,
      ConversationRepository(requireContext()),
      conversationRecipientRepository,
      messageRequestRepository
    )
  }

  private val groupCallViewModel: ConversationGroupCallViewModel by viewModels(
    factoryProducer = {
      ConversationGroupCallViewModel.Factory(args.threadId, conversationRecipientRepository)
    }
  )

  private val conversationGroupViewModel: ConversationGroupViewModel by viewModels(
    factoryProducer = {
      ConversationGroupViewModel.Factory(args.threadId, conversationRecipientRepository)
    }
  )

  private val messageRequestViewModel: MessageRequestViewModel by viewModel {
    MessageRequestViewModel(args.threadId, conversationRecipientRepository, messageRequestRepository)
  }

  private val conversationTooltips = ConversationTooltips(this)
  private val colorizer = Colorizer()

  private lateinit var conversationOptionsMenuProvider: ConversationOptionsMenu.Provider
  private lateinit var layoutManager: LinearLayoutManager
  private lateinit var markReadHelper: MarkReadHelper
  private lateinit var giphyMp4ProjectionRecycler: GiphyMp4ProjectionRecycler
  private lateinit var addToContactsLauncher: ActivityResultLauncher<Intent>
  private lateinit var scrollToPositionDelegate: ScrollToPositionDelegate
  private lateinit var adapter: ConversationAdapterV2
  private lateinit var recyclerViewColorizer: RecyclerViewColorizer
  private lateinit var attachmentManager: AttachmentManager
  private lateinit var multiselectItemDecoration: MultiselectItemDecoration
  private lateinit var openableGiftItemDecoration: OpenableGiftItemDecoration

  private var animationsAllowed = false
  private var actionMode: ActionMode? = null

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

  private lateinit var reactionDelegate: ConversationReactionDelegate

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    SignalLocalMetrics.ConversationOpen.start()
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    registerForResults()
    disposables.bindTo(viewLifecycleOwner)
    FullscreenHelper(requireActivity()).showSystemUI()

    conversationOptionsMenuProvider = ConversationOptionsMenu.Provider(ConversationOptionsMenuCallback(), disposables)
    markReadHelper = MarkReadHelper(ConversationId.forConversation(args.threadId), requireContext(), viewLifecycleOwner)

    initializeConversationThreadUi()

    val conversationToolbarOnScrollHelper = ConversationToolbarOnScrollHelper(
      requireActivity(),
      binding.toolbar,
      viewModel::wallpaperSnapshot
    )
    conversationToolbarOnScrollHelper.attach(binding.conversationItemRecycler)
    presentWallpaper(args.wallpaper)
    presentChatColors(args.chatColors)
    presentActionBarMenu()

    observeConversationThread()

    viewModel
      .inputReadyState
      .subscribeBy(
        onNext = this::presentInputReadyState
      )
      .addTo(disposables)

    container.fragmentManager = childFragmentManager
  }

  override fun onResume() {
    super.onResume()

    WindowUtil.setLightNavigationBarFromTheme(requireActivity())
    WindowUtil.setLightStatusBarFromTheme(requireActivity())
    groupCallViewModel.peekGroupCall()

    if (!args.conversationScreenType.isInBubble) {
      ApplicationDependencies.getMessageNotifier().setVisibleThread(ConversationId.forConversation(args.threadId))
    }

    motionEventRelay.setDrain(MotionEventRelayDrain())
  }

  override fun onPause() {
    super.onPause()
    ApplicationDependencies.getMessageNotifier().clearVisibleThread()
    motionEventRelay.setDrain(null)
  }

  private fun observeConversationThread() {
    var firstRender = true
    disposables += viewModel
      .conversationThreadState
      .subscribeOn(Schedulers.io())
      .doOnSuccess { state ->
        SignalLocalMetrics.ConversationOpen.onDataLoaded()
        binding.conversationItemRecycler.doOnNextLayout {
          layoutManager.scrollToPositionWithOffset(
            adapter.getAdapterPositionForMessagePosition(state.meta.getStartPosition()),
            binding.conversationItemRecycler.height
          )
        }
      }
      .flatMapObservable { it.items.data }
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy(onNext = {
        if (firstRender) {
          SignalLocalMetrics.ConversationOpen.onDataPostedToMain()
        }

        adapter.submitList(it) {
          scrollToPositionDelegate.notifyListCommitted()

          if (firstRender) {
            firstRender = false
            binding.conversationItemRecycler.doAfterNextLayout {
              SignalLocalMetrics.ConversationOpen.onRenderFinished()
              doAfterFirstRender()
              animationsAllowed = true
            }
          }
        }
      })
  }

  private fun doAfterFirstRender() {
    Log.d(TAG, "doAfterFirstRender")
    attachmentManager = AttachmentManager(requireContext(), requireView(), AttachmentManagerListener())

    EventBus.getDefault().registerForLifecycle(groupCallViewModel, viewLifecycleOwner)
    viewLifecycleOwner.lifecycle.addObserver(LastSeenPositionUpdater(adapter, layoutManager, viewModel))

    disposables += viewModel.recipient
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy(onNext = this::onRecipientChanged)

    disposables += viewModel.markReadRequests
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy(onNext = markReadHelper::onViewsRevealed)

    disposables += viewModel.scrollButtonState
      .subscribeBy(onNext = this::presentScrollButtons)

    disposables += viewModel
      .nameColorsMap
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy(onNext = {
        colorizer.onNameColorsChanged(it)
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
      })

    val disabledInputListener = DisabledInputListener()
    binding.conversationDisabledInput.listener = disabledInputListener

    val sendButtonListener = SendButtonListener()
    val composeTextEventsListener = ComposeTextEventsListener()

    composeText.apply {
      setOnEditorActionListener(sendButtonListener)

      setCursorPositionChangedListener(composeTextEventsListener)
      setOnKeyListener(composeTextEventsListener)
      addTextChangedListener(composeTextEventsListener)
      setOnClickListener(composeTextEventsListener)
      onFocusChangeListener = composeTextEventsListener

      setMessageSendType(MessageSendType.SignalMessageSendType)
    }

    sendButton.apply {
      setOnClickListener(sendButtonListener)
      isEnabled = true
      post { sendButton.triggerSelectedChangedEvent() }
    }

    val attachListener = { _: View ->
      container.toggleInput(AttachmentKeyboardFragmentCreator, composeText)
    }
    binding.conversationInputPanel.attachButton.setOnClickListener(attachListener)
    binding.conversationInputPanel.inlineAttachmentButton.setOnClickListener(attachListener)

    presentGroupCallJoinButton()

    binding.scrollToBottom.setOnClickListener {
      scrollToPositionDelegate.resetScrollPosition()
    }

    binding.scrollToMention.setOnClickListener {
      scrollToNextMention()
    }

    adapter.registerAdapterDataObserver(DataObserver(scrollToPositionDelegate))

    val keyboardEvents = KeyboardEvents()
    container.listener = keyboardEvents
    requireActivity()
      .onBackPressedDispatcher
      .addCallback(
        viewLifecycleOwner,
        keyboardEvents
      )

    childFragmentManager.setFragmentResultListener(AttachmentKeyboardFragment.RESULT_KEY, viewLifecycleOwner, AttachmentKeyboardFragmentListener())

    val conversationReactionStub = Stub<ConversationReactionOverlay>(binding.conversationReactionScrubberStub)
    reactionDelegate = ConversationReactionDelegate(conversationReactionStub)
    reactionDelegate.setOnReactionSelectedListener(OnReactionsSelectedListener())
  }

  private fun presentInputReadyState(inputReadyState: InputReadyState) {
    val disabledInputView = binding.conversationDisabledInput

    var inputDisabled = true
    when {
      inputReadyState.isClientExpired || inputReadyState.isUnauthorized -> disabledInputView.showAsExpiredOrUnauthorized(inputReadyState.isClientExpired, inputReadyState.isUnauthorized)
      inputReadyState.messageRequestState != MessageRequestState.NONE -> disabledInputView.showAsMessageRequest(inputReadyState.conversationRecipient, inputReadyState.messageRequestState)
      inputReadyState.isActiveGroup == false -> disabledInputView.showAsNoLongerAMember()
      inputReadyState.isRequestingMember == true -> disabledInputView.showAsRequestingMember()
      inputReadyState.isAnnouncementGroup == true && inputReadyState.isAdmin == false -> disabledInputView.showAsAnnouncementGroupAdminsOnly()
      else -> inputDisabled = false
    }

    inputPanel.setHideForMessageRequestState(inputDisabled)

    if (inputDisabled) {
      WindowUtil.setNavigationBarColor(requireActivity(), disabledInputView.color)
    } else {
      disabledInputView.clear()
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
  }

  private fun onRecipientChanged(recipient: Recipient) {
    presentWallpaper(recipient.wallpaper)
    presentConversationTitle(recipient)
    presentChatColors(recipient.chatColors)
  }

  private fun invalidateOptionsMenu() {
    // TODO [alex] -- Handle search... is there a better way to manage this state? Maybe an event system?
    conversationOptionsMenuProvider.onCreateMenu(binding.toolbar.menu, requireActivity().menuInflater)
  }

  private fun presentActionBarMenu() {
    invalidateOptionsMenu()

    when (args.conversationScreenType) {
      ConversationScreenType.NORMAL -> presentNavigationIconForNormal()
      ConversationScreenType.BUBBLE -> presentNavigationIconForBubble()
      ConversationScreenType.POPUP -> Unit
    }

    binding.toolbar.setOnMenuItemClickListener(conversationOptionsMenuProvider::onMenuItemSelected)
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

  private fun presentConversationTitle(recipient: Recipient) {
    binding.conversationTitleView.root.setTitle(GlideApp.with(this), recipient)
  }

  private fun presentWallpaper(chatWallpaper: ChatWallpaper?) {
    if (chatWallpaper != null) {
      chatWallpaper.loadInto(binding.conversationWallpaper)
      ChatWallpaperDimLevelUtil.applyDimLevelForNightMode(binding.conversationWallpaperDim, chatWallpaper)
    } else {
      binding.conversationWallpaperDim.visible = false
    }

    val wallpaperEnabled = chatWallpaper != null
    binding.conversationWallpaper.visible = wallpaperEnabled
    binding.scrollToBottom.setWallpaperEnabled(wallpaperEnabled)
    binding.scrollToMention.setWallpaperEnabled(wallpaperEnabled)
    binding.conversationDisabledInput.setWallpaperEnabled(wallpaperEnabled)

    adapter.onHasWallpaperChanged(wallpaperEnabled)
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

    disposables += groupCallViewModel.hasActiveGroupCall.subscribeBy(onNext = {
      invalidateOptionsMenu()
      binding.conversationGroupCallJoin.visible = it
    })

    disposables += groupCallViewModel.hasCapacity.subscribeBy(onNext = {
      binding.conversationGroupCallJoin.setText(
        if (it) R.string.ConversationActivity_join else R.string.ConversationActivity_full
      )
    })
  }

  private fun handleVideoCall() {
    val recipient: Single<Recipient> = viewModel.recipient.firstOrError()
    val hasActiveGroupCall: Single<Boolean> = groupCallViewModel.hasActiveGroupCall.firstOrError()
    val isNonAdminInAnnouncementGroup: Boolean = conversationGroupViewModel.isNonAdminInAnnouncementGroup()
    val cannotCreateGroupCall = Single.zip(recipient, hasActiveGroupCall) { r, active ->
      r to (r.isPushV2Group && !active && isNonAdminInAnnouncementGroup)
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

  private fun getVoiceNoteMediaController() = requireListener<VoiceNoteMediaControllerOwner>().voiceNoteMediaController

  private fun initializeConversationThreadUi() {
    layoutManager = SmoothScrollingLinearLayoutManager(requireContext(), true)
    binding.conversationItemRecycler.setHasFixedSize(false)
    binding.conversationItemRecycler.layoutManager = layoutManager
    binding.conversationItemRecycler.addOnScrollListener(ScrollListener())

    adapter = ConversationAdapterV2(
      lifecycleOwner = viewLifecycleOwner,
      glideRequests = GlideApp.with(this),
      clickListener = ConversationItemClickListener(),
      hasWallpaper = args.wallpaper != null,
      colorizer = colorizer
    )

    scrollToPositionDelegate = ScrollToPositionDelegate(
      binding.conversationItemRecycler,
      adapter::canJumpToPosition,
      adapter::getAdapterPositionForMessagePosition
    )

    ConversationAdapter.initializePool(binding.conversationItemRecycler.recycledViewPool)
    adapter.setPagingController(viewModel.pagingController)

    binding.conversationItemRecycler.adapter = adapter
    giphyMp4ProjectionRecycler = initializeGiphyMp4()

    multiselectItemDecoration = MultiselectItemDecoration(
      requireContext()
    ) { viewModel.wallpaperSnapshot }

    openableGiftItemDecoration = OpenableGiftItemDecoration(requireContext())
    binding.conversationItemRecycler.addItemDecoration(openableGiftItemDecoration)

    binding.conversationItemRecycler.addItemDecoration(multiselectItemDecoration)
    viewLifecycleOwner.lifecycle.addObserver(multiselectItemDecoration)

    val layoutTransitionListener = BubbleLayoutTransitionListener(binding.conversationItemRecycler)
    viewLifecycleOwner.lifecycle.addObserver(layoutTransitionListener)

    recyclerViewColorizer = RecyclerViewColorizer(binding.conversationItemRecycler)
    recyclerViewColorizer.setChatColors(args.chatColors)

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
      GiphyMp4ItemDecoration(callback) { translationY: Float ->
        binding.reactionsShade.translationY = translationY + binding.conversationItemRecycler.height
      },
      0
    )
    return callback
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
      // todo [cody] draftViewModel.voiceNoteDraft != null) { {
      // buttonToggle.display(sendButton)
      // quickAttachment.hide()
      // inlineAttachment.hide()
      // }
      composeText.text?.isEmpty() == true && !attachmentManager.isAttachmentPresent -> {
        buttonToggle.display(binding.conversationInputPanel.attachButton)
        quickAttachment.show()
        inlineAttachment.hide()
      }

      else -> {
        buttonToggle.display(sendButton)
        quickAttachment.hide()

        if (!attachmentManager.isAttachmentPresent) { // todo [cody] && !linkPreviewViewModel.hasLinkPreviewUi()) {
          inlineAttachment.show()
        } else {
          inlineAttachment.hide()
        }
      }
    }
  }

  private fun sendMessage(
    metricId: String? = null,
    scheduledDate: Long = -1,
    slideDeck: SlideDeck? = if (attachmentManager.isAttachmentPresent) attachmentManager.buildSlideDeck() else null
  ) {
    val send: Completable = viewModel.sendMessage(
      metricId = metricId,
      body = composeText.editableText.toString(),
      slideDeck = slideDeck,
      scheduledDate = scheduledDate,
      messageToEdit = inputPanel.editMessageId,
      quote = inputPanel.quote.orNull(),
      mentions = composeText.mentions,
      bodyRanges = composeText.styling
    )

    disposables += send
      .doOnSubscribe {
        composeText.setText("")
      }
      .subscribeBy(
        onError = { t ->
          Log.w(TAG, "Error sending", t)
          when (t) {
            is InvalidMessageException -> toast(R.string.ConversationActivity_message_is_empty_exclamation)
            is RecipientFormattingException -> toast(R.string.ConversationActivity_recipient_is_not_a_valid_sms_or_email_address_exclamation, Toast.LENGTH_LONG)
          }
        },
        onComplete = this::onSendComplete
      )
  }

  private fun onSendComplete() {
    if (isDetached || activity?.isFinishing == true) {
      if (args.conversationScreenType.isInPopup) {
        activity?.finish()
      }
      return
    }

    // todo [cody] fragment.setLastSeen(0);

    scrollToPositionDelegate.resetScrollPosition()
    attachmentManager.cleanup()

    // todo [cody] updateLinkPreviewState();

    // todo [cody] draftViewModel.onSendComplete(threadId);

    inputPanel.exitEditMessageMode()
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

    if (menuState.shouldShowEditAction() && FeatureFlags.editMessageSending()) {
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
          handleSaveAttachment(getSelectedConversationMessage().messageRecord as MediaMmsMessageRecord)
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
      inputPanel.setHideForSelection(true)

      bottomActionBar.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
          if (bottomActionBar.height == 0 && bottomActionBar.visible) {
            return false
          }

          bottomActionBar.viewTreeObserver.removeOnPreDrawListener(this)
          val bottomPadding = bottomActionBar.height + 18.dp
          ViewUtil.setPaddingBottom(binding.conversationItemRecycler, bottomPadding)
          binding.conversationItemRecycler.scrollBy(0, -(bottomPadding - additionalScrollOffset))
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

    /*
    // TODO [alex]
    if (attachmentKeyboardStub.resolved()) {
      attachmentKeyboardStub.get().hide(true);
    }
     */
  }

  //region Message action handling

  private fun handleReplyToMessage(conversationMessage: ConversationMessage) {
    // TODO [alex] -- Not implemented yet.
  }

  private fun handleEditMessage(conversationMessage: ConversationMessage) {
    // TODO [alex] -- Not implemented yet.
  }

  private fun handleForwardMessageParts(messageParts: Set<MultiselectPart>) {
    // TODO [alex] -- Not implemented yet.
  }

  private fun handleSaveAttachment(record: MediaMmsMessageRecord) {
    // TODO [alex] -- Not implemented yet.
  }

  private fun handleCopyMessage(messageParts: Set<MultiselectPart>) {
    // TODO [alex] -- Not implemented yet.
  }

  private fun handleDisplayDetails(conversationMessage: ConversationMessage) {
    // TODO [alex] -- Not implemented yet.
  }

  private fun handleDeleteMessages(messageParts: Set<MultiselectPart>) {
    // TODO [alex] -- Not implemented yet.
  }

  //endregion

  //region Scroll Handling

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
    return layoutManager.findFirstCompletelyVisibleItemPosition() > 4
  }

  private inner class ScrollListener : RecyclerView.OnScrollListener() {
    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
      if (isScrolledToBottom()) {
        viewModel.setShowScrollButtons(false)
      } else if (isScrolledPastButtonThreshold()) {
        viewModel.setShowScrollButtons(true)
      }

      val timestamp = MarkReadHelper.getLatestTimestamp(adapter, layoutManager)
      timestamp.ifPresent(viewModel::requestMarkRead)
    }
  }

  private inner class DataObserver(
    private val scrollToPositionDelegate: ScrollToPositionDelegate
  ) : RecyclerView.AdapterDataObserver() {
    override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
      Log.d(TAG, "onItemRangeInserted $positionStart $itemCount")
      if (positionStart == 0 && itemCount == 1 && !binding.conversationItemRecycler.canScrollVertically(1)) {
        Log.d(TAG, "Requesting scroll to bottom.")
        scrollToPositionDelegate.resetScrollPosition()
      }
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

      MessageQuotesBottomSheet.show(
        childFragmentManager,
        MessageId(messageRecord.id),
        recipientId
      )
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
      // TODO [alex] - ("Not yet implemented")
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
        ReactionsBottomSheetDialogFragment.create(messageId, isMms).show(parentFragmentManager, reactionsTag)
      }
    }

    override fun onGroupMemberClicked(recipientId: RecipientId, groupId: GroupId) {
      context ?: return
      RecipientBottomSheetDialogFragment.create(recipientId, groupId).show(parentFragmentManager, "BOTTOM")
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
        RecipientBottomSheetDialogFragment.create(
          target,
          it.groupId.orElse(null)
        ).show(parentFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
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
        requireActivity().startActivity(create(requireActivity(), args.skipSharedElementTransition(true)))
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

      sharedElement.transitionName = MediaPreviewV2Activity.SHARED_ELEMENT_TRANSITION_NAME
      requireActivity().setExitSharedElementCallback(MaterialContainerTransformSharedElementCallback())
      val options = ActivityOptions.makeSceneTransitionAnimation(requireActivity(), sharedElement, MediaPreviewV2Activity.SHARED_ELEMENT_TRANSITION_NAME)
      requireActivity().startActivity(create(requireActivity(), args), options.toBundle())
    }

    override fun onEditedIndicatorClicked(messageRecord: MessageRecord) {
      if (messageRecord.isOutgoing) {
        EditMessageHistoryDialog.show(childFragmentManager, messageRecord.toRecipient.id, messageRecord)
      } else {
        EditMessageHistoryDialog.show(childFragmentManager, messageRecord.fromRecipient.id, messageRecord)
      }
    }

    override fun onItemClick(item: MultiselectPart?) {
      // TODO [alex] -- ("Not yet implemented")
    }

    override fun onItemLongClick(itemView: View, item: MultiselectPart) {
      Log.d(TAG, "onItemLongClick")
      if (actionMode != null) return

      val messageRecord = item.getMessageRecord()
      val recipient = viewModel.recipientSnapshot ?: return

      if (isUnopenedGift(itemView, messageRecord)) {
        return
      }

      if (messageRecord.isSecure &&
        !messageRecord.isRemoteDelete &&
        !messageRecord.isUpdate &&
        !recipient.isBlocked &&
        !viewModel.hasMessageRequestState &&
        (!recipient.isGroup || recipient.isActiveGroup) &&
        adapter.selectedItems.isEmpty()
      ) {
        multiselectItemDecoration.setFocusedItem(MultiselectPart.Message(item.conversationMessage))
        binding.conversationItemRecycler.invalidateItemDecorations()
        binding.reactionsShade.visibility = View.VISIBLE
        binding.conversationItemRecycler.suppressLayout(true)

        if (itemView is ConversationItem) {
          val audioUri = messageRecord.getAudioUriForLongClick()
          if (audioUri != null) {
            getVoiceNoteMediaController().pausePlayback(audioUri)
          }

          val childAdapterPosition = binding.conversationItemRecycler.getChildAdapterPosition(itemView)
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

          val snapshot = ConversationItemSelection.snapshotView(itemView, binding.conversationItemRecycler, messageRecord, videoBitmap)

          // TODO [alex] -- Should only have a focused view if the keyboard was open.
          val focusedView = null // itemView.rootView.findFocus()
          val bodyBubble = itemView.bodyBubble!!
          val selectedConversationModel = SelectedConversationModel(
            snapshot,
            itemView.x,
            itemView.y + binding.conversationItemRecycler.translationY,
            bodyBubble.x,
            bodyBubble.y,
            bodyBubble.width,
            audioUri,
            messageRecord.isOutgoing,
            focusedView
          )

          bodyBubble.visibility = View.INVISIBLE
          itemView.reactionsView?.visibility = View.INVISIBLE

          val quotedIndicatorVisible = itemView.quotedIndicator?.visibility == View.VISIBLE
          if (quotedIndicatorVisible) {
            ViewUtil.fadeOut(itemView.quotedIndicator!!, 150, View.INVISIBLE)
          }

          ViewUtil.hideKeyboard(requireContext(), itemView)

          val showScrollButtons = viewModel.showScrollButtonsSnapshot
          if (showScrollButtons) {
            viewModel.setShowScrollButtons(false)
          }

          val conversationItem: ConversationItem = itemView
          val isAttachmentKeyboardOpen = false /* TODO [alex] -- isAttachmentKeyboardOpen */
          handleReaction(
            item.conversationMessage,
            ReactionsToolbarListener(item.conversationMessage),
            selectedConversationModel,
            object : OnHideListener {
              override fun startHide() {
                multiselectItemDecoration.hideShade(binding.conversationItemRecycler)
                ViewUtil.fadeOut(binding.reactionsShade, resources.getInteger(R.integer.reaction_scrubber_hide_duration), View.GONE)
              }

              override fun onHide() {
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
                conversationItem.reactionsView?.visibility = View.VISIBLE

                if (quotedIndicatorVisible && conversationItem.quotedIndicator != null) {
                  ViewUtil.fadeIn(conversationItem.quotedIndicator!!, 150)
                }

                if (showScrollButtons) {
                  viewModel.setShowScrollButtons(true)
                }

                if (isAttachmentKeyboardOpen) {
                  // listener.openAttachmentKeyboard();
                }
              }
            }
          )
        } else {
          clearFocusedItem()
          adapter.toggleSelection(item)
          binding.conversationItemRecycler.invalidateItemDecorations()

          actionMode = (requireActivity() as AppCompatActivity).startSupportActionMode(actionModeCallback)
        }
      }
    }

    override fun onShowGroupDescriptionClicked(groupName: String, description: String, shouldLinkifyWebLinks: Boolean) {
      GroupDescriptionDialog.show(childFragmentManager, groupName, description, shouldLinkifyWebLinks)
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
        isPushAvailable = true, // TODO [alex]
        canShowAsBubble = Observable.empty(),
        isActiveGroup = recipient?.isActiveGroup == true,
        isActiveV2Group = recipient?.let { it.isActiveGroup && it.isPushV2Group } == true,
        isInActiveGroup = recipient?.isActiveGroup == false,
        hasActiveGroupCall = groupCallViewModel.hasActiveGroupCallSnapshot,
        distributionType = args.distributionType,
        threadId = args.threadId,
        isInMessageRequest = viewModel.hasMessageRequestState,
        isInBubble = args.conversationScreenType.isInBubble
      )
    }

    override fun onOptionsMenuCreated(menu: Menu) {
      // TODO [alex]
    }

    override fun handleVideo() {
      this@ConversationFragment.handleVideoCall()
    }

    override fun handleDial(isSecure: Boolean) {
      // TODO [alex] - ("Not yet implemented")
    }

    override fun handleViewMedia() {
      // TODO [alex] - ("Not yet implemented")
    }

    override fun handleAddShortcut() {
      // TODO [alex] - ("Not yet implemented")
    }

    override fun handleSearch() {
      // TODO [alex] - ("Not yet implemented")
    }

    override fun handleAddToContacts() {
      // TODO [alex] - ("Not yet implemented")
    }

    override fun handleDisplayGroupRecipients() {
      // TODO [alex] - ("Not yet implemented")
    }

    override fun handleDistributionBroadcastEnabled(menuItem: MenuItem) {
      // TODO [alex] - ("Not yet implemented")
    }

    override fun handleDistributionConversationEnabled(menuItem: MenuItem) {
      // TODO [alex] - ("Not yet implemented")
    }

    override fun handleManageGroup() {
      // TODO [alex] - ("Not yet implemented")
    }

    override fun handleLeavePushGroup() {
      // TODO [alex] - ("Not yet implemented")
    }

    override fun handleInviteLink() {
      // TODO [alex] - ("Not yet implemented")
    }

    override fun handleMuteNotifications() {
      // TODO [alex] - ("Not yet implemented")
    }

    override fun handleUnmuteNotifications() {
      // TODO [alex] - ("Not yet implemented")
    }

    override fun handleConversationSettings() {
      // TODO [alex] - ("Not yet implemented")
    }

    override fun handleSelectMessageExpiration() {
      // TODO [alex] - ("Not yet implemented")
    }

    override fun handleCreateBubble() {
      // TODO [alex] - ("Not yet implemented")
    }

    override fun handleGoHome() {
      // TODO [alex] - ("Not yet implemented")
    }

    override fun showExpiring(recipient: Recipient) {
      binding.conversationTitleView.root.showExpiring(recipient)
    }

    override fun clearExpiring() {
      binding.conversationTitleView.root.clearExpiring()
    }

    override fun showGroupCallingTooltip() {
      conversationTooltips.displayGroupCallingTooltip(requireView().findViewById(R.id.menu_video_secure))
    }
  }

  private inner class OnReactionsSelectedListener : ConversationReactionOverlay.OnReactionSelectedListener {
    override fun onReactionSelected(messageRecord: MessageRecord, emoji: String?) {
      reactionDelegate.hide()
    }

    override fun onCustomReactionSelected(messageRecord: MessageRecord, hasAddedCustomEmoji: Boolean) {
      reactionDelegate.hide()
    }
  }

  private inner class MotionEventRelayDrain : MotionEventRelay.Drain {
    override fun accept(motionEvent: MotionEvent): Boolean {
      return reactionDelegate.applyTouchEvent(motionEvent)
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
        ConversationReactionOverlay.Action.RESEND -> Unit // TODO [cfv2]
        ConversationReactionOverlay.Action.DOWNLOAD -> handleSaveAttachment(conversationMessage.messageRecord as MediaMmsMessageRecord)
        ConversationReactionOverlay.Action.COPY -> handleCopyMessage(conversationMessage.multiselectCollection.toSet())
        ConversationReactionOverlay.Action.MULTISELECT -> Unit // TODO [cfv2]
        ConversationReactionOverlay.Action.PAYMENT_DETAILS -> Unit // TODO [cfv2]
        ConversationReactionOverlay.Action.VIEW_INFO -> handleDisplayDetails(conversationMessage)
        ConversationReactionOverlay.Action.DELETE -> handleDeleteMessages(conversationMessage.multiselectCollection.toSet())
      }
    }
  }

  inner class ActionModeCallback : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
      mode.title = calculateSelectedItemCount()
      // TODO [alex] listener.onMessageActionToolbarOpened();
      setCorrectActionModeMenuVisibility()
      return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false

    override fun onDestroyActionMode(mode: ActionMode) {
      adapter.clearSelection()
      setBottomActionBarVisibility(false)
      // TODO [alex] listener.onMessageActionToolbarClosed();
      binding.conversationItemRecycler.invalidateItemDecorations()
      actionMode = null
    }
  }
  // endregion Conversation Callbacks

  private class LastSeenPositionUpdater(
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
      messageRequestViewModel
        .onAccept()
        .subscribeWithShowProgress("accept message request")
        .addTo(disposables)
    }

    override fun onDeleteGroupClicked() {
      val recipient = viewModel.recipientSnapshot
      if (recipient == null) {
        Log.w(TAG, "[onDeleteGroupClicked] No recipient!")
        return
      }

      ConversationDialogs.displayDeleteDialog(requireContext(), recipient) {
        messageRequestViewModel
          .onDelete()
          .subscribeWithShowProgress("delete message request")
      }
    }

    override fun onBlockClicked() {
      val recipient = viewModel.recipientSnapshot
      if (recipient == null) {
        Log.w(TAG, "[onBlockClicked] No recipient!")
        return
      }

      BlockUnblockDialog.showBlockAndReportSpamFor(
        requireContext(),
        lifecycle,
        recipient,
        {
          messageRequestViewModel
            .onBlock()
            .subscribeWithShowProgress("block")
        },
        {
          messageRequestViewModel
            .onBlockAndReportSpam()
            .subscribeWithShowProgress("block")
        }
      )
    }

    override fun onUnblockClicked() {
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

    override fun onGroupV1MigrationClicked() {
      val recipient = viewModel.recipientSnapshot
      if (recipient == null) {
        Log.w(TAG, "[onGroupV1MigrationClicked] No recipient!")
        return
      }

      GroupsV1MigrationInitiationBottomSheetDialogFragment.showForInitiation(childFragmentManager, recipient.id)
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
  }

  //endregion

  //region Compose + Send Callbacks

  private inner class SendButtonListener : View.OnClickListener, OnEditorActionListener {
    override fun onClick(v: View) {
      val metricId = if (viewModel.recipientSnapshot?.isGroup == true) {
        SignalLocalMetrics.GroupMessageSend.start()
      } else {
        SignalLocalMetrics.IndividualMessageSend.start()
      }

      sendMessage(metricId)
    }

    override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean {
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
  }

  private inner class ComposeTextEventsListener :
    View.OnKeyListener,
    View.OnClickListener,
    TextWatcher,
    OnFocusChangeListener,
    ComposeText.CursorPositionChangedListener {

    private var beforeLength = 0

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
        composeText.postDelayed({ updateToggleButtonState() }, 50)
      }
      // todo [cody] stickerViewModel.onInputTextUpdated(s.toString())
    }

    override fun onFocusChange(v: View, hasFocus: Boolean) {
      if (hasFocus) { // && container.getCurrentInput() == emojiDrawerStub.get()) {
        container.showSoftkey(composeText)
      }
    }

    override fun onCursorPositionChanged(start: Int, end: Int) {
      // todo [cody] linkPreviewViewModel.onTextChanged(requireContext(), composeText.getTextTrimmed().toString(), start, end);
    }

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit
  }

  //endregion Compose + Send Callbacks

  //region Attachment + Media Keyboard

  private inner class AttachmentManagerListener : AttachmentManager.AttachmentListener {
    override fun onAttachmentChanged() {
      // TODO [cody] implement
    }

    override fun onLocationRemoved() {
      // TODO [cody] implement
    }
  }

  private object AttachmentKeyboardFragmentCreator : InputAwareConstraintLayout.FragmentCreator {
    override val id: Int = 1
    override fun create(): Fragment = AttachmentKeyboardFragment()
  }

  private inner class AttachmentKeyboardFragmentListener : FragmentResultListener {
    @Suppress("DEPRECATION")
    override fun onFragmentResult(requestKey: String, result: Bundle) {
      val button: AttachmentKeyboardButton? = result.getSerializable(AttachmentKeyboardFragment.BUTTON_RESULT) as? AttachmentKeyboardButton
      val media: Media? = result.getParcelable(AttachmentKeyboardFragment.MEDIA_RESULT)

      if (button != null) {
        when (button) {
          AttachmentKeyboardButton.GALLERY -> AttachmentManager.selectGallery(this@ConversationFragment, 1, viewModel.recipientSnapshot!!, composeText.textTrimmed, sendButton.selectedSendType, inputPanel.quote.isPresent)
          AttachmentKeyboardButton.FILE -> AttachmentManager.selectDocument(this@ConversationFragment, 1)
          AttachmentKeyboardButton.CONTACT -> AttachmentManager.selectContactInfo(this@ConversationFragment, 1)
          AttachmentKeyboardButton.LOCATION -> AttachmentManager.selectLocation(this@ConversationFragment, 1, viewModel.recipientSnapshot!!.chatColors.asSingleColor())
          AttachmentKeyboardButton.PAYMENT -> AttachmentManager.selectPayment(this@ConversationFragment, viewModel.recipientSnapshot!!)
        }
      } else if (media != null) {
        startActivityForResult(MediaSelectionActivity.editor(requireActivity(), sendButton.selectedSendType, listOf(media), viewModel.recipientSnapshot!!.id, composeText.textTrimmed), 12)
      }

      container.hideInput()
    }
  }

  private inner class KeyboardEvents : OnBackPressedCallback(false), InputAwareConstraintLayout.Listener {
    override fun handleOnBackPressed() {
      container.hideInput()
    }

    override fun onInputShown() {
      isEnabled = true
    }

    override fun onInputHidden() {
      isEnabled = false
    }
  }

  //endregion
}
