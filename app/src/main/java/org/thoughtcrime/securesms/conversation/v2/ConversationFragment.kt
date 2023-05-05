/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.doOnNextLayout
import androidx.fragment.app.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.signal.core.util.ThreadUtil
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.badges.gifts.flow.GiftFlowActivity
import org.thoughtcrime.securesms.badges.gifts.viewgift.received.ViewReceivedGiftBottomSheet
import org.thoughtcrime.securesms.badges.gifts.viewgift.sent.ViewSentGiftBottomSheet
import org.thoughtcrime.securesms.components.ScrollToPositionDelegate
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.components.recyclerview.SmoothScrollingLinearLayoutManager
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.DonateToSignalFragment
import org.thoughtcrime.securesms.components.settings.app.subscription.donate.DonateToSignalType
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaControllerOwner
import org.thoughtcrime.securesms.components.voice.VoiceNotePlaybackState
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.contactshare.ContactUtil
import org.thoughtcrime.securesms.contactshare.SharedContactDetailsActivity
import org.thoughtcrime.securesms.conversation.BadDecryptLearnMoreDialog
import org.thoughtcrime.securesms.conversation.ConversationAdapter
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.conversation.ConversationIntents.ConversationScreenType
import org.thoughtcrime.securesms.conversation.ConversationItem
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.conversation.ConversationOptionsMenu
import org.thoughtcrime.securesms.conversation.MarkReadHelper
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
import org.thoughtcrime.securesms.database.model.InMemoryMessageRecord
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
import org.thoughtcrime.securesms.groups.ui.GroupErrors
import org.thoughtcrime.securesms.groups.ui.invitesandrequests.invite.GroupLinkInviteFriendsBottomSheetDialogFragment
import org.thoughtcrime.securesms.groups.ui.managegroup.dialogs.GroupDescriptionDialog
import org.thoughtcrime.securesms.groups.ui.migration.GroupsV1MigrationInfoBottomSheetDialogFragment
import org.thoughtcrime.securesms.groups.v2.GroupBlockJoinRequestResult
import org.thoughtcrime.securesms.invites.InviteActions
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.longmessage.LongMessageFragment
import org.thoughtcrime.securesms.mediapreview.MediaIntentFactory
import org.thoughtcrime.securesms.mediapreview.MediaIntentFactory.create
import org.thoughtcrime.securesms.mediapreview.MediaPreviewV2Activity
import org.thoughtcrime.securesms.messagedetails.MessageDetailsFragment
import org.thoughtcrime.securesms.mms.AttachmentManager
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.notifications.v2.ConversationId
import org.thoughtcrime.securesms.payments.preferences.PaymentsActivity
import org.thoughtcrime.securesms.ratelimit.RecaptchaProofBottomSheetFragment
import org.thoughtcrime.securesms.reactions.ReactionsBottomSheetDialogFragment
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientExporter
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment
import org.thoughtcrime.securesms.safety.SafetyNumberBottomSheet
import org.thoughtcrime.securesms.stickers.StickerLocator
import org.thoughtcrime.securesms.stickers.StickerPackPreviewActivity
import org.thoughtcrime.securesms.stories.StoryViewerArgs
import org.thoughtcrime.securesms.stories.viewer.StoryViewerActivity
import org.thoughtcrime.securesms.util.BottomSheetUtil
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.ContextUtil
import org.thoughtcrime.securesms.util.DrawableUtil
import org.thoughtcrime.securesms.util.FullscreenHelper
import org.thoughtcrime.securesms.util.SignalLocalMetrics
import org.thoughtcrime.securesms.util.WindowUtil
import org.thoughtcrime.securesms.util.doAfterNextLayout
import org.thoughtcrime.securesms.util.fragments.requireListener
import org.thoughtcrime.securesms.util.hasGiftBadge
import org.thoughtcrime.securesms.util.visible
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper
import org.thoughtcrime.securesms.wallpaper.ChatWallpaperDimLevelUtil
import java.util.Locale

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

  private val disposables = LifecycleDisposable()
  private val binding by ViewBinderDelegate(V2ConversationFragmentBinding::bind)
  private val viewModel: ConversationViewModel by viewModels(
    factoryProducer = {
      ConversationViewModel.Factory(
        args,
        ConversationRepository(requireContext()),
        conversationRecipientRepository
      )
    }
  )

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

  private var animationsAllowed = false

  private val jumpAndPulseScrollStrategy = object : ScrollToPositionDelegate.ScrollStrategy {
    override fun performScroll(recyclerView: RecyclerView, layoutManager: LinearLayoutManager, position: Int, smooth: Boolean) {
      ScrollToPositionDelegate.JumpToPositionStrategy.performScroll(recyclerView, layoutManager, position, smooth)
      adapter.pulseAtPosition(position)
    }
  }

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
    presentActionBarMenu()

    observeConversationThread()
  }

  override fun onResume() {
    super.onResume()

    WindowUtil.setLightNavigationBarFromTheme(requireActivity())
    WindowUtil.setLightStatusBarFromTheme(requireActivity())
    groupCallViewModel.peekGroupCall()

    if (!args.conversationScreenType.isInBubble) {
      ApplicationDependencies.getMessageNotifier().setVisibleThread(ConversationId.forConversation(args.threadId))
    }
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
        SignalLocalMetrics.ConversationOpen.onDataPostedToMain()

        adapter.submitList(it) {
          scrollToPositionDelegate.notifyListCommitted()

          binding.conversationItemRecycler.doAfterNextLayout {
            SignalLocalMetrics.ConversationOpen.onRenderFinished()

            if (firstRender) {
              firstRender = false
              doAfterFirstRender()
              animationsAllowed = true
            }
          }
        }
      })
  }

  private fun doAfterFirstRender() {
    Log.d(TAG, "doAfterFirstRender")

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

    presentGroupCallJoinButton()

    binding.scrollToBottom.setOnClickListener {
      scrollToPositionDelegate.resetScrollPosition()
    }

    binding.scrollToMention.setOnClickListener {
      scrollToNextMention()
    }

    adapter.registerAdapterDataObserver(DataObserver(scrollToPositionDelegate))
  }

  override fun onPause() {
    super.onPause()
    ApplicationDependencies.getMessageNotifier().clearVisibleThread()
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
    binding.toolbar.setNavigationOnClickListener {
      requireActivity().finishAfterTransition()
    }
  }

  private fun presentNavigationIconForBubble() {
    binding.toolbar.navigationIcon = DrawableUtil.tint(
      ContextUtil.requireDrawable(requireContext(), R.drawable.ic_notification),
      ContextCompat.getColor(requireContext(), R.color.signal_accent_primary)
    )

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

    binding.conversationWallpaper.visible = chatWallpaper != null
    binding.scrollToBottom.setWallpaperEnabled(chatWallpaper != null)
    binding.scrollToMention.setWallpaperEnabled(chatWallpaper != null)
    adapter.onHasWallpaperChanged(chatWallpaper != null)
  }

  private fun presentChatColors(chatColors: ChatColors) {
    recyclerViewColorizer.setChatColors(chatColors)
    binding.scrollToMention.setUnreadCountBackgroundTint(chatColors.asSingleColor())
    binding.scrollToBottom.setUnreadCountBackgroundTint(chatColors.asSingleColor())
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

    val multiselectItemDecoration = MultiselectItemDecoration(
      requireContext()
    ) { viewModel.wallpaperSnapshot }

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
        // TODO [alex] reactionsShade.setTranslationY(translationY + list.getHeight())
      },
      0
    )
    return callback
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
      ReactionsBottomSheetDialogFragment.create(messageId, isMms).show(parentFragmentManager, null)
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
        EditMessageHistoryDialog.show(childFragmentManager, messageRecord.toRecipient.id, messageRecord.id)
      } else {
        EditMessageHistoryDialog.show(childFragmentManager, messageRecord.fromRecipient.id, messageRecord.id)
      }
    }

    override fun onItemClick(item: MultiselectPart?) {
      // TODO [alex] -- ("Not yet implemented")
    }

    override fun onItemLongClick(itemView: View?, item: MultiselectPart?) {
      // TODO [alex] -- ("Not yet implemented")
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
        isInMessageRequest = false, // TODO [alex]
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
}
