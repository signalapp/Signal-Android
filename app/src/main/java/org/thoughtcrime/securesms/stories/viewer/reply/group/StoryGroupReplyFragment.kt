package org.thoughtcrime.securesms.stories.viewer.reply.group

import android.content.ClipData
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehaviorHack
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.FixedRoundedCornerBottomSheetDialogFragment
import org.thoughtcrime.securesms.components.emoji.MediaKeyboard
import org.thoughtcrime.securesms.components.mention.MentionAnnotation
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.conversation.MarkReadHelper
import org.thoughtcrime.securesms.conversation.colors.Colorizer
import org.thoughtcrime.securesms.conversation.ui.error.SafetyNumberChangeDialog
import org.thoughtcrime.securesms.conversation.ui.mentions.MentionsPickerFragment
import org.thoughtcrime.securesms.conversation.ui.mentions.MentionsPickerViewModel
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob
import org.thoughtcrime.securesms.keyboard.KeyboardPage
import org.thoughtcrime.securesms.keyboard.KeyboardPagerViewModel
import org.thoughtcrime.securesms.keyboard.emoji.EmojiKeyboardCallback
import org.thoughtcrime.securesms.mediasend.v2.UntrustedRecords
import org.thoughtcrime.securesms.notifications.v2.ConversationId
import org.thoughtcrime.securesms.reactions.any.ReactWithAnyEmojiBottomSheetDialogFragment
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment
import org.thoughtcrime.securesms.stories.viewer.reply.StoryViewsAndRepliesPagerChild
import org.thoughtcrime.securesms.stories.viewer.reply.StoryViewsAndRepliesPagerParent
import org.thoughtcrime.securesms.stories.viewer.reply.composer.StoryReactionBar
import org.thoughtcrime.securesms.stories.viewer.reply.composer.StoryReplyComposer
import org.thoughtcrime.securesms.util.DeleteDialog
import org.thoughtcrime.securesms.util.FragmentDialogs.displayInDialogAboveAnchor
import org.thoughtcrime.securesms.util.LifecycleDisposable
import org.thoughtcrime.securesms.util.ServiceUtil
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.adapter.mapping.PagingMappingAdapter
import org.thoughtcrime.securesms.util.fragments.findListener
import org.thoughtcrime.securesms.util.fragments.requireListener
import org.thoughtcrime.securesms.util.visible

/**
 * Fragment which contains UI to reply to a group story
 */
class StoryGroupReplyFragment :
  Fragment(R.layout.stories_group_replies_fragment),
  StoryViewsAndRepliesPagerChild,
  StoryReplyComposer.Callback,
  EmojiKeyboardCallback,
  ReactWithAnyEmojiBottomSheetDialogFragment.Callback,
  SafetyNumberChangeDialog.Callback {

  private val viewModel: StoryGroupReplyViewModel by viewModels(
    factoryProducer = {
      StoryGroupReplyViewModel.Factory(storyId, StoryGroupReplyRepository())
    }
  )

  private val mentionsViewModel: MentionsPickerViewModel by viewModels(
    factoryProducer = { MentionsPickerViewModel.Factory() },
    ownerProducer = { requireActivity() }
  )

  private val keyboardPagerViewModel: KeyboardPagerViewModel by viewModels(
    ownerProducer = { requireActivity() }
  )

  private val recyclerListener: RecyclerView.OnItemTouchListener = object : RecyclerView.SimpleOnItemTouchListener() {
    override fun onInterceptTouchEvent(view: RecyclerView, e: MotionEvent): Boolean {
      recyclerView.isNestedScrollingEnabled = view == recyclerView
      composer.emojiPageView?.isNestedScrollingEnabled = view == composer.emojiPageView

      val dialog = (parentFragment as FixedRoundedCornerBottomSheetDialogFragment).dialog as BottomSheetDialog
      BottomSheetBehaviorHack.setNestedScrollingChild(dialog.behavior, view)
      dialog.findViewById<View>(R.id.design_bottom_sheet)?.invalidate()
      return false
    }
  }

  private val colorizer = Colorizer()
  private val lifecycleDisposable = LifecycleDisposable()

  private val storyId: Long
    get() = requireArguments().getLong(ARG_STORY_ID)

  private val groupRecipientId: RecipientId
    get() = requireArguments().getParcelable(ARG_GROUP_RECIPIENT_ID)!!

  private val isFromNotification: Boolean
    get() = requireArguments().getBoolean(ARG_IS_FROM_NOTIFICATION, false)

  private val groupReplyStartPosition: Int
    get() = requireArguments().getInt(ARG_GROUP_REPLY_START_POSITION, -1)

  private lateinit var recyclerView: RecyclerView
  private lateinit var adapter: PagingMappingAdapter<StoryGroupReplyItemData.Key>
  private lateinit var dataObserver: RecyclerView.AdapterDataObserver
  private lateinit var composer: StoryReplyComposer

  private var markReadHelper: MarkReadHelper? = null

  private var currentChild: StoryViewsAndRepliesPagerParent.Child? = null

  private var resendBody: CharSequence? = null
  private var resendMentions: List<Mention> = emptyList()
  private var resendReaction: String? = null

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    SignalExecutors.BOUNDED.execute {
      RetrieveProfileJob.enqueue(groupRecipientId)
    }

    recyclerView = view.findViewById(R.id.recycler)
    composer = view.findViewById(R.id.composer)

    lifecycleDisposable.bindTo(viewLifecycleOwner)

    val emptyNotice: View = requireView().findViewById(R.id.empty_notice)

    adapter = PagingMappingAdapter<StoryGroupReplyItemData.Key>()
    val layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
    recyclerView.layoutManager = layoutManager
    recyclerView.adapter = adapter
    recyclerView.itemAnimator = null
    StoryGroupReplyItem.register(adapter)

    composer.callback = this

    onPageSelected(findListener<StoryViewsAndRepliesPagerParent>()?.selectedChild ?: StoryViewsAndRepliesPagerParent.Child.REPLIES)

    viewModel.state.observe(viewLifecycleOwner) { state ->
      if (markReadHelper == null && state.threadId > 0L) {
        if (isResumed) {
          ApplicationDependencies.getMessageNotifier().setVisibleThread(ConversationId(state.threadId, storyId))
        }

        markReadHelper = MarkReadHelper(ConversationId(state.threadId, storyId), requireContext(), viewLifecycleOwner)

        if (isFromNotification) {
          markReadHelper?.onViewsRevealed(System.currentTimeMillis())
        }
      }

      emptyNotice.visible = state.noReplies && state.loadState == StoryGroupReplyState.LoadState.READY
      colorizer.onNameColorsChanged(state.nameColors)
    }

    viewModel.pagingController.observe(viewLifecycleOwner) { controller ->
      adapter.setPagingController(controller)
    }

    var consumed = false
    viewModel.pageData.observe(viewLifecycleOwner) { pageData ->
      adapter.submitList(getConfiguration(pageData).toMappingModelList()) {
        if (!consumed && (groupReplyStartPosition >= 0 && adapter.hasItem(groupReplyStartPosition))) {
          consumed = true
          recyclerView.post { recyclerView.scrollToPosition(groupReplyStartPosition) }
        }
      }
    }

    dataObserver = GroupDataObserver()
    adapter.registerAdapterDataObserver(dataObserver)

    initializeMentions()

    if (savedInstanceState == null) {
      ViewUtil.focusAndShowKeyboard(composer)
    }

    recyclerView.addOnScrollListener(GroupReplyScrollObserver())
  }

  override fun onResume() {
    super.onResume()
    val threadId = viewModel.stateSnapshot.threadId
    if (threadId != 0L) {
      ApplicationDependencies.getMessageNotifier().setVisibleThread(ConversationId(threadId, storyId))
    }
  }

  override fun onPause() {
    super.onPause()
    ApplicationDependencies.getMessageNotifier().setVisibleThread(null)
  }

  override fun onDestroyView() {
    super.onDestroyView()

    composer.input.setMentionQueryChangedListener(null)
    composer.input.setMentionValidator(null)
  }

  private fun postMarkAsReadRequest() {
    if (adapter.itemCount == 0 || markReadHelper == null) {
      return
    }

    val lastVisibleItem = (recyclerView.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
    val adapterItem = adapter.getItem(lastVisibleItem)
    if (adapterItem == null || adapterItem !is StoryGroupReplyItem.DataWrapper) {
      return
    }

    markReadHelper?.onViewsRevealed(adapterItem.storyGroupReplyItemData.sentAtMillis)
  }

  private fun getConfiguration(pageData: List<StoryGroupReplyItemData>): DSLConfiguration {
    return configure {
      pageData.forEach {
        when (it.replyBody) {
          is StoryGroupReplyItemData.ReplyBody.Text -> {
            customPref(
              StoryGroupReplyItem.TextModel(
                storyGroupReplyItemData = it,
                text = it.replyBody,
                nameColor = colorizer.getIncomingGroupSenderColor(
                  requireContext(),
                  it.sender
                ),
                onCopyClick = { model ->
                  val clipData = ClipData.newPlainText(requireContext().getString(R.string.app_name), model.text.message.getDisplayBody(requireContext()))
                  ServiceUtil.getClipboardManager(requireContext()).setPrimaryClip(clipData)
                  Toast.makeText(requireContext(), R.string.StoryGroupReplyFragment__copied_to_clipboard, Toast.LENGTH_SHORT).show()
                },
                onDeleteClick = { model ->
                  lifecycleDisposable += DeleteDialog.show(requireActivity(), setOf(model.text.message.messageRecord)).subscribe { result ->
                    if (result) {
                      throw AssertionError("We should never end up deleting a Group Thread like this.")
                    }
                  }
                },
                onMentionClick = { recipientId ->
                  RecipientBottomSheetDialogFragment
                    .create(recipientId, null)
                    .show(childFragmentManager, null)
                }
              )
            )
          }
          is StoryGroupReplyItemData.ReplyBody.Reaction -> {
            customPref(
              StoryGroupReplyItem.ReactionModel(
                storyGroupReplyItemData = it,
                reaction = it.replyBody,
                nameColor = colorizer.getIncomingGroupSenderColor(
                  requireContext(),
                  it.sender
                )
              )
            )
          }
          is StoryGroupReplyItemData.ReplyBody.RemoteDelete -> {
            customPref(
              StoryGroupReplyItem.RemoteDeleteModel(
                storyGroupReplyItemData = it,
                remoteDelete = it.replyBody,
                nameColor = colorizer.getIncomingGroupSenderColor(
                  requireContext(),
                  it.sender
                ),
                onDeleteClick = { model ->
                  lifecycleDisposable += DeleteDialog.show(requireActivity(), setOf(model.remoteDelete.messageRecord)).subscribe { didDeleteThread ->
                    if (didDeleteThread) {
                      throw AssertionError("We should never end up deleting a Group Thread like this.")
                    }
                  }
                },
              )
            )
          }
        }
      }
    }
  }

  override fun onPageSelected(child: StoryViewsAndRepliesPagerParent.Child) {
    currentChild = child
    updateNestedScrolling()
  }

  private fun updateNestedScrolling() {
    recyclerView.isNestedScrollingEnabled = currentChild == StoryViewsAndRepliesPagerParent.Child.REPLIES && !(mentionsViewModel.isShowing.value ?: false)
  }

  override fun onSendActionClicked() {
    val (body, mentions) = composer.consumeInput()
    performSend(body, mentions)
  }

  override fun onPickReactionClicked() {
    displayInDialogAboveAnchor(composer.reactionButton, R.layout.stories_reaction_bar_layout) { dialog, view ->
      view.findViewById<StoryReactionBar>(R.id.reaction_bar).apply {
        callback = object : StoryReactionBar.Callback {
          override fun onTouchOutsideOfReactionBar() {
            dialog.dismiss()
          }

          override fun onReactionSelected(emoji: String) {
            dialog.dismiss()
            sendReaction(emoji)
          }

          override fun onOpenReactionPicker() {
            dialog.dismiss()
            ReactWithAnyEmojiBottomSheetDialogFragment.createForStory().show(childFragmentManager, null)
          }
        }
        animateIn()
      }
    }
  }

  override fun onEmojiSelected(emoji: String?) {
    composer.onEmojiSelected(emoji)
  }

  private fun sendReaction(emoji: String) {
    lifecycleDisposable += StoryGroupReplySender.sendReaction(requireContext(), storyId, emoji)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy(
        onError = { error ->
          if (error is UntrustedRecords.UntrustedRecordsException) {
            resendReaction = emoji

            SafetyNumberChangeDialog.show(childFragmentManager, error.untrustedRecords)
          } else {
            Log.w(TAG, "Failed to send reply", error)
            val context = context
            if (context != null) {
              Toast.makeText(context, R.string.message_details_recipient__failed_to_send, Toast.LENGTH_SHORT).show()
            }
          }
        }
      )
  }

  override fun onKeyEvent(keyEvent: KeyEvent?) = Unit

  override fun onInitializeEmojiDrawer(mediaKeyboard: MediaKeyboard) {
    keyboardPagerViewModel.setOnlyPage(KeyboardPage.EMOJI)
    mediaKeyboard.setFragmentManager(childFragmentManager)
  }

  override fun onShowEmojiKeyboard() {
    requireListener<Callback>().requestFullScreen(true)
    recyclerView.addOnItemTouchListener(recyclerListener)
    composer.emojiPageView?.addOnItemTouchListener(recyclerListener)
  }

  override fun onHideEmojiKeyboard() {
    recyclerView.removeOnItemTouchListener(recyclerListener)
    composer.emojiPageView?.removeOnItemTouchListener(recyclerListener)
    requireListener<Callback>().requestFullScreen(false)
  }

  override fun openEmojiSearch() {
    composer.openEmojiSearch()
  }

  override fun closeEmojiSearch() {
    composer.closeEmojiSearch()
  }

  override fun onReactWithAnyEmojiDialogDismissed() {
  }

  override fun onReactWithAnyEmojiSelected(emoji: String) {
    sendReaction(emoji)
  }

  private fun initializeMentions() {
    Recipient.live(groupRecipientId).observe(viewLifecycleOwner) { recipient ->
      mentionsViewModel.onRecipientChange(recipient)

      composer.input.setMentionQueryChangedListener { query ->
        if (recipient.isPushV2Group) {
          ensureMentionsContainerFilled()
          mentionsViewModel.onQueryChange(query)
        }
      }

      composer.input.setMentionValidator { annotations ->
        if (!recipient.isPushV2Group) {
          annotations
        } else {

          val validRecipientIds: Set<String> = recipient.participants
            .map { r -> MentionAnnotation.idToMentionAnnotationValue(r.id) }
            .toSet()

          annotations
            .filter { !validRecipientIds.contains(it.value) }
            .toList()
        }
      }
    }

    mentionsViewModel.selectedRecipient.observe(viewLifecycleOwner) { recipient ->
      composer.input.replaceTextWithMention(recipient.getDisplayName(requireContext()), recipient.id)
    }

    mentionsViewModel.isShowing.observe(viewLifecycleOwner) { updateNestedScrolling() }
  }

  private fun ensureMentionsContainerFilled() {
    val mentionsFragment = childFragmentManager.findFragmentById(R.id.mentions_picker_container)
    if (mentionsFragment == null) {
      childFragmentManager
        .beginTransaction()
        .replace(R.id.mentions_picker_container, MentionsPickerFragment())
        .commitNowAllowingStateLoss()
    }
  }

  private inner class GroupReplyScrollObserver : RecyclerView.OnScrollListener() {
    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
      postMarkAsReadRequest()
    }

    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
      postMarkAsReadRequest()
    }
  }

  private inner class GroupDataObserver : RecyclerView.AdapterDataObserver() {
    override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
      if (itemCount == 0) {
        return
      }

      val item = adapter.getItem(positionStart)
      if (positionStart == adapter.itemCount - 1 && item is StoryGroupReplyItem.DataWrapper) {
        val isOutgoing = item.storyGroupReplyItemData.sender == Recipient.self()
        if (isOutgoing || (!isOutgoing && !recyclerView.canScrollVertically(1))) {
          recyclerView.post { recyclerView.scrollToPosition(positionStart) }
        }
      }
    }
  }

  companion object {
    private val TAG = Log.tag(StoryGroupReplyFragment::class.java)

    private const val ARG_STORY_ID = "arg.story.id"
    private const val ARG_GROUP_RECIPIENT_ID = "arg.group.recipient.id"
    private const val ARG_IS_FROM_NOTIFICATION = "is_from_notification"
    private const val ARG_GROUP_REPLY_START_POSITION = "group_reply_start_position"

    fun create(storyId: Long, groupRecipientId: RecipientId, isFromNotification: Boolean, groupReplyStartPosition: Int): Fragment {
      return StoryGroupReplyFragment().apply {
        arguments = Bundle().apply {
          putLong(ARG_STORY_ID, storyId)
          putParcelable(ARG_GROUP_RECIPIENT_ID, groupRecipientId)
          putBoolean(ARG_IS_FROM_NOTIFICATION, isFromNotification)
          putInt(ARG_GROUP_REPLY_START_POSITION, groupReplyStartPosition)
        }
      }
    }
  }

  private fun performSend(body: CharSequence, mentions: List<Mention>) {
    lifecycleDisposable += StoryGroupReplySender.sendReply(requireContext(), storyId, body, mentions)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy(
        onError = {
          if (it is UntrustedRecords.UntrustedRecordsException) {
            resendBody = body
            resendMentions = mentions

            SafetyNumberChangeDialog.show(childFragmentManager, it.untrustedRecords)
          } else {
            Log.w(TAG, "Failed to send reply", it)
            val context = context
            if (context != null) {
              Toast.makeText(context, R.string.message_details_recipient__failed_to_send, Toast.LENGTH_SHORT).show()
            }
          }
        }
      )
  }

  override fun onSendAnywayAfterSafetyNumberChange(changedRecipients: MutableList<RecipientId>) {
    val resendBody = resendBody
    val resendReaction = resendReaction
    if (resendBody != null) {
      performSend(resendBody, resendMentions)
    } else if (resendReaction != null) {
      sendReaction(resendReaction)
    }
  }

  override fun onMessageResentAfterSafetyNumberChange() {
    error("Should never get here.")
  }

  override fun onCanceled() {
    resendBody = null
    resendMentions = emptyList()
    resendReaction = null
  }

  interface Callback {
    fun onStartDirectReply(recipientId: RecipientId)
    fun requestFullScreen(fullscreen: Boolean)
  }
}
