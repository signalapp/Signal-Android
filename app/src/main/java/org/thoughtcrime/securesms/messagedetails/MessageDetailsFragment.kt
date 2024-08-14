package org.thoughtcrime.securesms.messagedetails

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.FullScreenDialogFragment
import org.thoughtcrime.securesms.conversation.colors.Colorizer
import org.thoughtcrime.securesms.conversation.colors.RecyclerViewColorizer
import org.thoughtcrime.securesms.conversation.ui.edit.EditMessageHistoryDialog.Companion.show
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4PlaybackController
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ProjectionPlayerHolder
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ProjectionRecycler
import org.thoughtcrime.securesms.messagedetails.InternalMessageDetailsFragment.Companion.create
import org.thoughtcrime.securesms.messagedetails.MessageDetailsAdapter.MessageDetailsViewState
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.safety.SafetyNumberBottomSheet.forMessageRecord
import org.thoughtcrime.securesms.util.Material3OnScrollHelper

class MessageDetailsFragment : FullScreenDialogFragment(), MessageDetailsAdapter.Callbacks {
  private lateinit var requestManager: RequestManager
  private lateinit var viewModel: MessageDetailsViewModel
  private lateinit var adapter: MessageDetailsAdapter
  private lateinit var colorizer: Colorizer
  private lateinit var recyclerViewColorizer: RecyclerViewColorizer

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

  interface Callback {
    fun onMessageDetailsFragmentDismissed()
  }

  companion object {
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
