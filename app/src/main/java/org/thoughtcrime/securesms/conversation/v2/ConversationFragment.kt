package org.thoughtcrime.securesms.conversation.v2

import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.greenrobot.eventbus.EventBus
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.LoggingFragment
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.components.recyclerview.SmoothScrollingLinearLayoutManager
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaControllerOwner
import org.thoughtcrime.securesms.components.voice.VoiceNotePlaybackState
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.contactshare.ContactUtil
import org.thoughtcrime.securesms.contactshare.SharedContactDetailsActivity
import org.thoughtcrime.securesms.conversation.ConversationAdapter
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.conversation.ConversationIntents.ConversationScreenType
import org.thoughtcrime.securesms.conversation.ConversationItem
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.conversation.ConversationOptionsMenu
import org.thoughtcrime.securesms.conversation.MarkReadHelper
import org.thoughtcrime.securesms.conversation.colors.Colorizer
import org.thoughtcrime.securesms.conversation.colors.RecyclerViewColorizer
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectItemDecoration
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectPart
import org.thoughtcrime.securesms.conversation.v2.groups.ConversationGroupCallViewModel
import org.thoughtcrime.securesms.conversation.v2.groups.ConversationGroupViewModel
import org.thoughtcrime.securesms.database.model.InMemoryMessageRecord
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.databinding.V2ConversationFragmentBinding
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ItemDecoration
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4PlaybackController
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4PlaybackPolicy
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ProjectionPlayerHolder
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ProjectionRecycler
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupMigrationMembershipChange
import org.thoughtcrime.securesms.invites.InviteActions
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.longmessage.LongMessageFragment
import org.thoughtcrime.securesms.mediapreview.MediaIntentFactory
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.notifications.v2.ConversationId
import org.thoughtcrime.securesms.ratelimit.RecaptchaProofBottomSheetFragment
import org.thoughtcrime.securesms.reactions.ReactionsBottomSheetDialogFragment
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment
import org.thoughtcrime.securesms.safety.SafetyNumberBottomSheet
import org.thoughtcrime.securesms.stickers.StickerLocator
import org.thoughtcrime.securesms.stickers.StickerPackPreviewActivity
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.ContextUtil
import org.thoughtcrime.securesms.util.DrawableUtil
import org.thoughtcrime.securesms.util.FullscreenHelper
import org.thoughtcrime.securesms.util.WindowUtil
import org.thoughtcrime.securesms.util.fragments.requireListener
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

  private val disposables = LifecycleDisposable()
  private val binding by ViewBinderDelegate(V2ConversationFragmentBinding::bind)
  private val viewModel: ConversationViewModel by viewModels(
    factoryProducer = {
      ConversationViewModel.Factory(args, ConversationRepository(requireContext()))
    }
  )

  private val groupCallViewModel: ConversationGroupCallViewModel by viewModels(
    factoryProducer = {
      ConversationGroupCallViewModel.Factory(args.threadId)
    }
  )

  private val conversationGroupViewModel: ConversationGroupViewModel by viewModels(
    factoryProducer = {
      ConversationGroupViewModel.Factory(args.threadId)
    }
  )

  private val conversationTooltips = ConversationTooltips(this)
  private lateinit var conversationOptionsMenuProvider: ConversationOptionsMenu.Provider
  private lateinit var layoutManager: SmoothScrollingLinearLayoutManager
  private lateinit var markReadHelper: MarkReadHelper

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    conversationOptionsMenuProvider = ConversationOptionsMenu.Provider(ConversationOptionsMenuCallback(), disposables)
    markReadHelper = MarkReadHelper(ConversationId.forConversation(args.threadId), requireContext(), viewLifecycleOwner)

    FullscreenHelper(requireActivity()).showSystemUI()

    layoutManager = SmoothScrollingLinearLayoutManager(requireContext(), true)
    binding.conversationItemRecycler.layoutManager = layoutManager

    val recyclerViewColorizer = RecyclerViewColorizer(binding.conversationItemRecycler)
    recyclerViewColorizer.setChatColors(args.chatColors)

    val conversationToolbarOnScrollHelper = ConversationToolbarOnScrollHelper(
      requireActivity(),
      binding.toolbar,
      viewModel::wallpaperSnapshot
    )
    conversationToolbarOnScrollHelper.attach(binding.conversationItemRecycler)

    disposables.bindTo(viewLifecycleOwner)
    disposables += viewModel.recipient
      .firstOrError()
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy(onSuccess = {
        onFirstRecipientLoad(it)
      })

    presentWallpaper(args.wallpaper)
    disposables += viewModel.recipient
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy(onNext = {
        recyclerViewColorizer.setChatColors(it.chatColors)
        presentWallpaper(it.wallpaper)
        presentConversationTitle(it)
      })

    EventBus.getDefault().registerForLifecycle(groupCallViewModel, viewLifecycleOwner)
    presentGroupCallJoinButton()
  }

  override fun onResume() {
    super.onResume()

    WindowUtil.setLightNavigationBarFromTheme(requireActivity())
    WindowUtil.setLightStatusBarFromTheme(requireActivity())
    groupCallViewModel.peekGroupCall()
  }

  private fun onFirstRecipientLoad(recipient: Recipient) {
    Log.d(TAG, "onFirstRecipientLoad")

    val colorizer = Colorizer()
    val adapter = ConversationAdapter(
      requireContext(),
      viewLifecycleOwner,
      GlideApp.with(this),
      Locale.getDefault(),
      ConversationItemClickListener(),
      recipient,
      colorizer
    )

    adapter.setPagingController(viewModel.pagingController)
    viewLifecycleOwner.lifecycle.addObserver(LastSeenPositionUpdater(adapter, layoutManager, viewModel))
    binding.conversationItemRecycler.adapter = adapter
    initializeGiphyMp4()

    binding.conversationItemRecycler.addItemDecoration(
      MultiselectItemDecoration(
        requireContext()
      ) { viewModel.wallpaperSnapshot }
    )

    disposables += viewModel
      .conversationThreadState
      .flatMap { it.items.data }
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy(onNext = {
        adapter.submitList(it)
      })

    disposables += viewModel
      .nameColorsMap
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy(onNext = {
        colorizer.onNameColorsChanged(it)
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
      })

    presentActionBarMenu()
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
  }

  private fun presentGroupCallJoinButton() {
    binding.conversationGroupCallJoin.setOnClickListener {
      handleVideoCall()
    }

    disposables += groupCallViewModel.hasActiveGroupCall.subscribeBy(onNext = {
      // invalidateOptionsMenu
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

  private fun getVoiceNoteMediaController() = requireListener<VoiceNoteMediaControllerOwner>().voiceNoteMediaController

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
    binding.conversationItemRecycler.addItemDecoration(GiphyMp4ItemDecoration(callback) { translationY: Float ->
      // TODO [alex] reactionsShade.setTranslationY(translationY + list.getHeight())
    }, 0)
    return callback
  }


  private inner class ConversationItemClickListener : ConversationAdapter.ItemClickListener {
    override fun onQuoteClicked(messageRecord: MmsMessageRecord?) {
      // TODO [alex] - ("Not yet implemented")
    }

    override fun onLinkPreviewClicked(linkPreview: LinkPreview) {
      val activity = activity ?: return
      CommunicationActions.openBrowserLink(activity, linkPreview.url)
    }

    override fun onQuotedIndicatorClicked(messageRecord: MessageRecord) {
      // TODO [alex] - ("Not yet implemented")
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
      // TODO [alex] - ("Not yet implemented")
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
      parentFragment ?: return
      ReactionsBottomSheetDialogFragment.create(messageId, isMms).show(parentFragmentManager, null)
    }

    override fun onGroupMemberClicked(recipientId: RecipientId, groupId: GroupId) {
      parentFragment ?: return
      RecipientBottomSheetDialogFragment.create(recipientId, groupId).show(parentFragmentManager, "BOTTOM")
    }

    override fun onMessageWithErrorClicked(messageRecord: MessageRecord) {
      // TODO [alex] - ("Not yet implemented")
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
      // TODO [alex] -- ("Not yet implemented")
    }

    override fun onChatSessionRefreshLearnMoreClicked() {
      // TODO [alex] -- ("Not yet implemented")
    }

    override fun onBadDecryptLearnMoreClicked(author: RecipientId) {
      // TODO [alex] -- ("Not yet implemented")
    }

    override fun onSafetyNumberLearnMoreClicked(recipient: Recipient) {
      // TODO [alex] -- ("Not yet implemented")
    }

    override fun onJoinGroupCallClicked() {
      // TODO [alex] -- ("Not yet implemented")
    }

    override fun onInviteFriendsToGroupClicked(groupId: GroupId.V2) {
      // TODO [alex] -- ("Not yet implemented")
    }

    override fun onEnableCallNotificationsClicked() {
      // TODO [alex] -- ("Not yet implemented")
    }

    override fun onPlayInlineContent(conversationMessage: ConversationMessage?) {
      // TODO [alex] - ("Not yet implemented")
    }

    override fun onInMemoryMessageClicked(messageRecord: InMemoryMessageRecord) {
      // TODO [alex] - ("Not yet implemented")
    }

    override fun onViewGroupDescriptionChange(groupId: GroupId?, description: String, isMessageRequestAccepted: Boolean) {
      // TODO [alex] - ("Not yet implemented")
    }

    override fun onChangeNumberUpdateContact(recipient: Recipient) {
      // TODO [alex] - ("Not yet implemented")
    }

    override fun onCallToAction(action: String) {
      // TODO [alex] - ("Not yet implemented")
    }

    override fun onDonateClicked() {
      // TODO [alex] - ("Not yet implemented")
    }

    override fun onBlockJoinRequest(recipient: Recipient) {
      // TODO [alex] - ("Not yet implemented")
    }

    override fun onRecipientNameClicked(target: RecipientId) {
      // TODO [alex] ("Not yet implemented")
    }

    override fun onInviteToSignalClicked() {
      val recipient = viewModel.recipientSnapshot ?: return
      InviteActions.inviteUserToSignal(
        requireContext(),
        recipient,
        {}, // TODO [alex] -- append to compose
        this@ConversationFragment::startActivity
      )
    }

    override fun onActivatePaymentsClicked() {
      // TODO [alex] -- ("Not yet implemented")
    }

    override fun onSendPaymentClicked(recipientId: RecipientId) {
      // TODO [alex] -- ("Not yet implemented")
    }

    override fun onScheduledIndicatorClicked(view: View, messageRecord: MessageRecord) {
      // TODO [alex] -- ("Not yet implemented")
    }

    override fun onUrlClicked(url: String): Boolean {
      return CommunicationActions.handlePotentialGroupLinkUrl(requireActivity(), url) ||
        CommunicationActions.handlePotentialProxyLinkUrl(requireActivity(), url)
    }

    override fun onViewGiftBadgeClicked(messageRecord: MessageRecord) {
      // TODO [alex] -- ("Not yet implemented")
    }

    override fun onGiftBadgeRevealed(messageRecord: MessageRecord) {
      // TODO [alex] -- ("Not yet implemented")
    }

    override fun goToMediaPreview(parent: ConversationItem?, sharedElement: View?, args: MediaIntentFactory.MediaPreviewArgs?) {
      // TODO [alex] -- ("Not yet implemented")
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
    val adapter: ConversationAdapter,
    val layoutManager: SmoothScrollingLinearLayoutManager,
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
