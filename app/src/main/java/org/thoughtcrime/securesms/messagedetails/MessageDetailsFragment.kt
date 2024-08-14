package org.thoughtcrime.securesms.messagedetails

import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import org.signal.core.util.logging.Log
import org.signal.ringrtc.CallLinkRootKey
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.FullScreenDialogFragment
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaControllerOwner
import org.thoughtcrime.securesms.components.voice.VoiceNotePlaybackState
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.conversation.ConversationItem
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.conversation.colors.Colorizer
import org.thoughtcrime.securesms.conversation.colors.RecyclerViewColorizer
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectPart
import org.thoughtcrime.securesms.conversation.ui.edit.EditMessageHistoryDialog.Companion.show
import org.thoughtcrime.securesms.database.model.InMemoryMessageRecord
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4PlaybackController
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ProjectionPlayerHolder
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ProjectionRecycler
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupMigrationMembershipChange
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.mediapreview.MediaIntentFactory.MediaPreviewArgs
import org.thoughtcrime.securesms.messagedetails.InternalMessageDetailsFragment.Companion.create
import org.thoughtcrime.securesms.messagedetails.MessageDetailsAdapter.MessageDetailsViewState
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.safety.SafetyNumberBottomSheet.forMessageRecord
import org.thoughtcrime.securesms.stickers.StickerLocator
import org.thoughtcrime.securesms.util.Material3OnScrollHelper
import org.thoughtcrime.securesms.util.fragments.requireListener

class MessageDetailsFragment : FullScreenDialogFragment(), MessageDetailsAdapter.Callbacks {
  private lateinit var requestManager: RequestManager
  private lateinit var viewModel: MessageDetailsViewModel
  private lateinit var adapter: MessageDetailsAdapter
  private lateinit var colorizer: Colorizer
  private lateinit var recyclerViewColorizer: RecyclerViewColorizer

  private fun getVoiceNoteMediaController() = requireListener<VoiceNoteMediaControllerOwner>().voiceNoteMediaController

  override fun getTitle() = R.string.AndroidManifest__message_details

  override fun getDialogLayoutResource() = R.layout.message_details_fragment

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    requestManager = Glide.with(this)

    initializeList(view)
    initializeViewModel()
    initializeVideoPlayer(view)
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)

    if (activity is Callback) {
      (activity as Callback?)!!.onMessageDetailsFragmentDismissed()
    } else if (parentFragment is Callback) {
      (parentFragment as Callback?)!!.onMessageDetailsFragmentDismissed()
    }
  }

  private fun initializeList(view: View) {
    val list = view.findViewById<RecyclerView>(R.id.message_details_list)
    val toolbarShadow = view.findViewById<View>(R.id.toolbar_shadow)

    colorizer = Colorizer()
    adapter = MessageDetailsAdapter(viewLifecycleOwner, requestManager, colorizer, this)
    recyclerViewColorizer = RecyclerViewColorizer(list)

    list.adapter = adapter
    list.itemAnimator = null
    Material3OnScrollHelper(requireActivity(), toolbarShadow, viewLifecycleOwner).attach(list)
  }

  private fun initializeViewModel() {
    val recipientId = requireArguments().getParcelable<RecipientId>(RECIPIENT_EXTRA)
    val messageId = requireArguments().getLong(MESSAGE_ID_EXTRA, -1)
    val factory = MessageDetailsViewModel.Factory(recipientId, messageId)

    viewModel = ViewModelProvider(this, factory)[MessageDetailsViewModel::class.java]
    viewModel.messageDetails.observe(this) { details: MessageDetails? ->
      if (details == null) {
        dismissAllowingStateLoss()
      } else {
        adapter.submitList(convertToRows(details))
      }
    }
    viewModel.recipient.observe(this) { recipient: Recipient -> recyclerViewColorizer.setChatColors(recipient.chatColors) }
  }

  private fun initializeVideoPlayer(view: View) {
    val videoContainer = view.findViewById<FrameLayout>(R.id.video_container)
    val recyclerView = view.findViewById<RecyclerView>(R.id.message_details_list)
    val holders = GiphyMp4ProjectionPlayerHolder.injectVideoViews(requireContext(), lifecycle, videoContainer, 1)
    val callback = GiphyMp4ProjectionRecycler(holders)

    GiphyMp4PlaybackController.attach(recyclerView, callback, 1)
  }

  private fun convertToRows(details: MessageDetails): List<MessageDetailsViewState<*>> {
    val list: MutableList<MessageDetailsViewState<*>> = ArrayList()

    list.add(MessageDetailsViewState(details.conversationMessage, MessageDetailsViewState.MESSAGE_HEADER))

    if (details.conversationMessage.messageRecord.isEditMessage) {
      list.add(MessageDetailsViewState(details.conversationMessage.messageRecord, MessageDetailsViewState.EDIT_HISTORY))
    }

    if (details.conversationMessage.messageRecord.isOutgoing) {
      addRecipients(list, RecipientHeader.NOT_SENT, details.notSent)
      addRecipients(list, RecipientHeader.VIEWED, details.viewed)
      addRecipients(list, RecipientHeader.READ, details.read)
      addRecipients(list, RecipientHeader.DELIVERED, details.delivered)
      addRecipients(list, RecipientHeader.SENT_TO, details.sent)
      addRecipients(list, RecipientHeader.PENDING, details.pending)
      addRecipients(list, RecipientHeader.SKIPPED, details.skipped)
    } else {
      addRecipients(list, RecipientHeader.SENT_FROM, details.sent)
    }

    return list
  }

  private fun addRecipients(list: MutableList<MessageDetailsViewState<*>>, header: RecipientHeader, recipients: Collection<RecipientDeliveryStatus>): Boolean {
    if (recipients.isEmpty()) {
      return false
    }

    list.add(MessageDetailsViewState(header, MessageDetailsViewState.RECIPIENT_HEADER))
    for (status in recipients) {
      list.add(MessageDetailsViewState(status, MessageDetailsViewState.RECIPIENT))
    }
    return true
  }

  override fun onErrorClicked(messageRecord: MessageRecord) {
    forMessageRecord(requireContext(), messageRecord)
      .show(childFragmentManager)
  }

  override fun onViewEditHistoryClicked(record: MessageRecord) {
    if (record.isOutgoing) {
      show(parentFragmentManager, record.toRecipient.id, record)
    } else {
      show(parentFragmentManager, record.fromRecipient.id, record)
    }
  }

  override fun onInternalDetailsClicked(record: MessageRecord) {
    create(record).show(parentFragmentManager, InternalMessageDetailsFragment::class.java.simpleName)
  }

  override fun onQuoteClicked(messageRecord: MmsMessageRecord?) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onLinkPreviewClicked(linkPreview: LinkPreview) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onQuotedIndicatorClicked(messageRecord: MessageRecord) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onMoreTextClicked(conversationRecipientId: RecipientId, messageId: Long, isMms: Boolean) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onStickerClicked(stickerLocator: StickerLocator) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onViewOnceMessageClicked(messageRecord: MmsMessageRecord) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onSharedContactDetailsClicked(contact: Contact, avatarTransitionView: View) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onAddToContactsClicked(contact: Contact) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onMessageSharedContactClicked(choices: MutableList<Recipient>) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onInviteSharedContactClicked(choices: MutableList<Recipient>) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onReactionClicked(multiselectPart: MultiselectPart, messageId: Long, isMms: Boolean) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onGroupMemberClicked(recipientId: RecipientId, groupId: GroupId) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onMessageWithErrorClicked(messageRecord: MessageRecord) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onMessageWithRecaptchaNeededClicked(messageRecord: MessageRecord) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onIncomingIdentityMismatchClicked(recipientId: RecipientId) {
    Log.w(TAG, "Not yet implemented!", Exception())
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
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onChatSessionRefreshLearnMoreClicked() {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onBadDecryptLearnMoreClicked(author: RecipientId) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onSafetyNumberLearnMoreClicked(recipient: Recipient) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onJoinGroupCallClicked() {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onInviteFriendsToGroupClicked(groupId: GroupId.V2) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onEnableCallNotificationsClicked() {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onPlayInlineContent(conversationMessage: ConversationMessage?) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onInMemoryMessageClicked(messageRecord: InMemoryMessageRecord) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onViewGroupDescriptionChange(groupId: GroupId?, description: String, isMessageRequestAccepted: Boolean) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onChangeNumberUpdateContact(recipient: Recipient) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onChangeProfileNameUpdateContact(recipient: Recipient) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onCallToAction(action: String) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onDonateClicked() {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onBlockJoinRequest(recipient: Recipient) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onRecipientNameClicked(target: RecipientId) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onInviteToSignalClicked() {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onActivatePaymentsClicked() {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onSendPaymentClicked(recipientId: RecipientId) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onScheduledIndicatorClicked(view: View, conversationMessage: ConversationMessage) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onUrlClicked(url: String): Boolean {
    Log.w(TAG, "Not yet implemented!", Exception())
    return false
  }

  override fun onViewGiftBadgeClicked(messageRecord: MessageRecord) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onGiftBadgeRevealed(messageRecord: MessageRecord) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun goToMediaPreview(parent: ConversationItem?, sharedElement: View?, args: MediaPreviewArgs?) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onEditedIndicatorClicked(conversationMessage: ConversationMessage) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onShowGroupDescriptionClicked(groupName: String, description: String, shouldLinkifyWebLinks: Boolean) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onJoinCallLink(callLinkRootKey: CallLinkRootKey) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onShowSafetyTips(forGroup: Boolean) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onReportSpamLearnMoreClicked() {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onMessageRequestAcceptOptionsClicked() {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onItemDoubleClick(multiselectPart: MultiselectPart?) {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  override fun onPaymentTombstoneClicked() {
    Log.w(TAG, "Not yet implemented!", Exception())
  }

  interface Callback {
    fun onMessageDetailsFragmentDismissed()
  }

  companion object {
    private val TAG = Log.tag(MessageDetailsFragment::class)
    private const val MESSAGE_ID_EXTRA = "message_id"
    private const val RECIPIENT_EXTRA = "recipient_id"

    fun create(message: MessageRecord, recipientId: RecipientId): DialogFragment {
      val dialogFragment: DialogFragment = MessageDetailsFragment()
      val args = Bundle()

      args.putLong(MESSAGE_ID_EXTRA, message.id)
      args.putParcelable(RECIPIENT_EXTRA, recipientId)

      dialogFragment.arguments = args

      return dialogFragment
    }
  }
}
