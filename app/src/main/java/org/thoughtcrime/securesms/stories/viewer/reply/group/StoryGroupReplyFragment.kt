package org.thoughtcrime.securesms.stories.viewer.reply.group

import android.content.ClipData
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehaviorHack
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.getParcelableCompat
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.FixedRoundedCornerBottomSheetDialogFragment
import org.thoughtcrime.securesms.components.emoji.MediaKeyboard
import org.thoughtcrime.securesms.components.mention.MentionAnnotation
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.conversation.MarkReadHelper
import org.thoughtcrime.securesms.conversation.colors.Colorizer
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQuery
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQueryChangedListener
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQueryResultsController
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQueryViewModel
import org.thoughtcrime.securesms.conversation.ui.mentions.MentionsPickerFragment
import org.thoughtcrime.securesms.conversation.ui.mentions.MentionsPickerViewModel
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob
import org.thoughtcrime.securesms.keyboard.KeyboardPage
import org.thoughtcrime.securesms.keyboard.KeyboardPagerViewModel
import org.thoughtcrime.securesms.keyboard.emoji.EmojiKeyboardCallback
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mediasend.v2.UntrustedRecords
import org.thoughtcrime.securesms.notifications.v2.ConversationId
import org.thoughtcrime.securesms.reactions.any.ReactWithAnyEmojiBottomSheetDialogFragment
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment
import org.thoughtcrime.securesms.safety.SafetyNumberBottomSheet
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.stories.viewer.reply.StoryViewsAndRepliesPagerChild
import org.thoughtcrime.securesms.stories.viewer.reply.StoryViewsAndRepliesPagerParent
import org.thoughtcrime.securesms.stories.viewer.reply.composer.StoryReplyComposer
import org.thoughtcrime.securesms.util.DeleteDialog
import org.thoughtcrime.securesms.util.Dialogs
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
  SafetyNumberBottomSheet.Callbacks {

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

  private val viewModel: StoryGroupReplyViewModel by viewModels(
    factoryProducer = {
      StoryGroupReplyViewModel.Factory(storyId, StoryGroupReplyRepository())
    }
  )

  private val mentionsViewModel: MentionsPickerViewModel by viewModels(
    factoryProducer = { MentionsPickerViewModel.Factory() },
    ownerProducer = { requireActivity() }
  )

  private val inlineQueryViewModel: InlineQueryViewModel by viewModels(
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
    get() = requireArguments().getParcelableCompat(ARG_GROUP_RECIPIENT_ID, RecipientId::class.java)!!

  private val isFromNotification: Boolean
    get() = requireArguments().getBoolean(ARG_IS_FROM_NOTIFICATION, false)

  private val groupReplyStartPosition: Int
    get() = requireArguments().getInt(ARG_GROUP_REPLY_START_POSITION, -1)

  private lateinit var recyclerView: RecyclerView
  private lateinit var adapter: PagingMappingAdapter<MessageId>
  private lateinit var dataObserver: RecyclerView.AdapterDataObserver
  private lateinit var composer: StoryReplyComposer
  private lateinit var notInGroup: View

  private var markReadHelper: MarkReadHelper? = null

  private var currentChild: StoryViewsAndRepliesPagerParent.Child? = null

  private var resendBody: CharSequence? = null
  private var resendMentions: List<Mention> = emptyList()
  private var resendReaction: String? = null
  private var resendBodyRanges: BodyRangeList? = null

  private lateinit var inlineQueryResultsController: InlineQueryResultsController

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    SignalExecutors.BOUNDED.execute {
      RetrieveProfileJob.enqueue(groupRecipientId)
    }

    recyclerView = view.findViewById(R.id.recycler)
    composer = view.findViewById(R.id.composer)
    notInGroup = view.findViewById(R.id.not_in_group)

    lifecycleDisposable.bindTo(viewLifecycleOwner)

    val emptyNotice: View = requireView().findViewById(R.id.empty_notice)

    adapter = PagingMappingAdapter<MessageId>().apply {
      setPagingController(viewModel.pagingController)
    }

    val layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
    recyclerView.layoutManager = layoutManager
    recyclerView.adapter = adapter
    recyclerView.itemAnimator = null
    StoryGroupReplyItem.register(adapter)

    composer.callback = this
    composer.hint = getString(R.string.StoryViewerPageFragment__reply_to_group)

    onPageSelected(findListener<StoryViewsAndRepliesPagerParent>()?.selectedChild ?: StoryViewsAndRepliesPagerParent.Child.REPLIES)

    var firstSubmit = true

    lifecycleDisposable += viewModel.state
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy { state ->
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

        adapter.submitList(getConfiguration(state.replies).toMappingModelList()) {
          if (firstSubmit && (groupReplyStartPosition >= 0 && adapter.hasItem(groupReplyStartPosition))) {
            firstSubmit = false
            recyclerView.post { recyclerView.scrollToPosition(groupReplyStartPosition) }
          }
        }
      }

    dataObserver = GroupDataObserver()
    adapter.registerAdapterDataObserver(dataObserver)

    initializeMentions()
    initializeComposer(savedInstanceState)

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

    composer.input.setInlineQueryChangedListener(null)
    composer.input.setMentionValidator(null)
  }

  private fun postMarkAsReadRequest() {
    if (adapter.itemCount == 0 || markReadHelper == null) {
      return
    }

    val lastVisibleItem = (recyclerView.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
    val adapterItem = adapter.getItem(lastVisibleItem)
    if (adapterItem == null || adapterItem !is StoryGroupReplyItem.Model) {
      return
    }

    markReadHelper?.onViewsRevealed(adapterItem.replyBody.sentAtMillis)
  }

  private fun getConfiguration(pageData: List<ReplyBody>): DSLConfiguration {
    return configure {
      pageData.forEach {
        when (it) {
          is ReplyBody.Text -> {
            customPref(
              StoryGroupReplyItem.TextModel(
                text = it,
                nameColor = it.sender.getStoryGroupReplyColor(),
                onCopyClick = { s -> onCopyClick(s) },
                onMentionClick = { recipientId ->
                  RecipientBottomSheetDialogFragment
                    .show(childFragmentManager, recipientId, null)
                },
                onDeleteClick = { m -> onDeleteClick(m) },
                onTapForDetailsClick = { m -> onTapForDetailsClick(m) }
              )
            )
          }
          is ReplyBody.Reaction -> {
            customPref(
              StoryGroupReplyItem.ReactionModel(
                reaction = it,
                nameColor = it.sender.getStoryGroupReplyColor(),
                onCopyClick = { s -> onCopyClick(s) },
                onDeleteClick = { m -> onDeleteClick(m) },
                onTapForDetailsClick = { m -> onTapForDetailsClick(m) }
              )
            )
          }
          is ReplyBody.RemoteDelete -> {
            customPref(
              StoryGroupReplyItem.RemoteDeleteModel(
                remoteDelete = it,
                nameColor = it.sender.getStoryGroupReplyColor(),
                onDeleteClick = { m -> onDeleteClick(m) },
                onTapForDetailsClick = { m -> onTapForDetailsClick(m) }
              )
            )
          }
        }
      }
    }
  }

  private fun onCopyClick(textToCopy: CharSequence) {
    val clipData = ClipData.newPlainText(requireContext().getString(R.string.app_name), textToCopy)
    ServiceUtil.getClipboardManager(requireContext()).setPrimaryClip(clipData)
    Toast.makeText(requireContext(), R.string.StoryGroupReplyFragment__copied_to_clipboard, Toast.LENGTH_SHORT).show()
  }

  private fun onDeleteClick(messageRecord: MessageRecord) {
    lifecycleDisposable += DeleteDialog.show(requireActivity(), setOf(messageRecord)).subscribe { (_, didDeleteThread) ->
      if (didDeleteThread) {
        throw AssertionError("We should never end up deleting a Group Thread like this.")
      }
    }
  }

  private fun onTapForDetailsClick(messageRecord: MessageRecord) {
    if (messageRecord.isRemoteDelete) {
      // TODO [cody] Android doesn't support resending remote deletes yet
      return
    }

    if (messageRecord.isIdentityMismatchFailure) {
      SafetyNumberBottomSheet
        .forMessageRecord(requireContext(), messageRecord)
        .show(childFragmentManager)
    } else if (messageRecord.hasFailedWithNetworkFailures()) {
      MaterialAlertDialogBuilder(requireContext())
        .setMessage(R.string.conversation_activity__message_could_not_be_sent)
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.conversation_activity__send) { _, _ -> SignalExecutors.BOUNDED.execute { MessageSender.resend(requireContext(), messageRecord) } }
        .show()
    }
  }

  override fun onPageSelected(child: StoryViewsAndRepliesPagerParent.Child) {
    currentChild = child
    updateNestedScrolling()

    if (currentChild != StoryViewsAndRepliesPagerParent.Child.REPLIES) {
      composer.close()
    }
  }

  private fun updateNestedScrolling() {
    recyclerView.isNestedScrollingEnabled = currentChild == StoryViewsAndRepliesPagerParent.Child.REPLIES && !(mentionsViewModel.isShowing.value ?: false)
  }

  override fun onSendActionClicked() {
    val send = Runnable {
      val (body, mentions, bodyRanges) = composer.consumeInput()
      performSend(body, mentions, bodyRanges)
    }

    if (SignalStore.uiHints().hasNotSeenTextFormattingAlert() && composer.input.hasStyling()) {
      Dialogs.showFormattedTextDialog(requireContext(), send)
    } else {
      send.run()
    }
  }

  override fun onPickAnyReactionClicked() {
    ReactWithAnyEmojiBottomSheetDialogFragment.createForStory().show(childFragmentManager, null)
  }

  override fun onReactionClicked(emoji: String) {
    sendReaction(emoji)
  }

  override fun onEmojiSelected(emoji: String?) {
    composer.onEmojiSelected(emoji)
  }

  private fun sendReaction(emoji: String) {
    findListener<Callback>()?.onReactionEmojiSelected(emoji)

    lifecycleDisposable += StoryGroupReplySender.sendReaction(requireContext(), storyId, emoji)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy(
        onError = { error ->
          if (error is UntrustedRecords.UntrustedRecordsException) {
            resendReaction = emoji

            SafetyNumberBottomSheet
              .forIdentityRecordsAndDestination(error.untrustedRecords, ContactSearchKey.RecipientSearchKey(groupRecipientId, true))
              .show(childFragmentManager)
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

  override fun onReactWithAnyEmojiDialogDismissed() = Unit

  override fun onReactWithAnyEmojiSelected(emoji: String) {
    sendReaction(emoji)
  }

  private fun initializeComposer(savedInstanceState: Bundle?) {
    val isActiveGroup = Recipient.observable(groupRecipientId).map { it.isActiveGroup }
    if (savedInstanceState == null) {
      lifecycleDisposable += isActiveGroup.firstOrError().observeOn(AndroidSchedulers.mainThread()).subscribe { active ->
        if (active) {
          ViewUtil.focusAndShowKeyboard(composer)
        }
      }
    }

    lifecycleDisposable += isActiveGroup.distinctUntilChanged().observeOn(AndroidSchedulers.mainThread()).forEach { active ->
      composer.visible = active
      notInGroup.visible = !active
    }
  }

  private fun initializeMentions() {
    inlineQueryResultsController = InlineQueryResultsController(
      inlineQueryViewModel,
      composer,
      (requireView() as ViewGroup),
      composer.input,
      viewLifecycleOwner
    )

    Recipient.live(groupRecipientId).observe(viewLifecycleOwner) { recipient ->
      mentionsViewModel.onRecipientChange(recipient)

      composer.input.setInlineQueryChangedListener(object : InlineQueryChangedListener {
        override fun onQueryChanged(inlineQuery: InlineQuery) {
          when (inlineQuery) {
            is InlineQuery.Mention -> {
              if (recipient.isPushV2Group) {
                ensureMentionsContainerFilled()
                mentionsViewModel.onQueryChange(inlineQuery.query)
              }
              inlineQueryViewModel.onQueryChange(inlineQuery)
            }
            is InlineQuery.Emoji -> {
              inlineQueryViewModel.onQueryChange(inlineQuery)
              mentionsViewModel.onQueryChange(null)
            }
            is InlineQuery.NoQuery -> {
              mentionsViewModel.onQueryChange(null)
              inlineQueryViewModel.onQueryChange(inlineQuery)
            }
          }
        }

        override fun clearQuery() {
          onQueryChanged(InlineQuery.NoQuery)
        }
      })

      composer.input.setMentionValidator { annotations ->
        if (!recipient.isPushV2Group) {
          annotations
        } else {
          val validRecipientIds: Set<String> = recipient.participantIds
            .map { id -> MentionAnnotation.idToMentionAnnotationValue(id) }
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

    lifecycleDisposable += inlineQueryViewModel
      .selection
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { r -> composer.input.replaceText(r) }

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

  private fun performSend(body: CharSequence, mentions: List<Mention>, bodyRanges: BodyRangeList?) {
    lifecycleDisposable += StoryGroupReplySender.sendReply(requireContext(), storyId, body, mentions, bodyRanges)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy(
        onError = { throwable ->
          if (throwable is UntrustedRecords.UntrustedRecordsException) {
            resendBody = body
            resendMentions = mentions
            resendBodyRanges = bodyRanges

            SafetyNumberBottomSheet
              .forIdentityRecordsAndDestination(throwable.untrustedRecords, ContactSearchKey.RecipientSearchKey(groupRecipientId, true))
              .show(childFragmentManager)
          } else {
            Log.w(TAG, "Failed to send reply", throwable)
            val context = context
            if (context != null) {
              Toast.makeText(context, R.string.message_details_recipient__failed_to_send, Toast.LENGTH_SHORT).show()
            }
          }
        }
      )
  }

  override fun sendAnywayAfterSafetyNumberChangedInBottomSheet(destinations: List<ContactSearchKey.RecipientSearchKey>) {
    val resendBody = resendBody
    val resendReaction = resendReaction
    if (resendBody != null) {
      performSend(resendBody, resendMentions, resendBodyRanges)
    } else if (resendReaction != null) {
      sendReaction(resendReaction)
    }
  }

  override fun onMessageResentAfterSafetyNumberChangeInBottomSheet() {
    Log.i(TAG, "Message resent")
  }

  override fun onCanceled() {
    resendBody = null
    resendMentions = emptyList()
    resendReaction = null
    resendBodyRanges = null
  }

  @ColorInt
  private fun Recipient.getStoryGroupReplyColor(): Int {
    return colorizer.getIncomingGroupSenderColor(requireContext(), this)
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
      if (positionStart == adapter.itemCount - 1 && item is StoryGroupReplyItem.Model) {
        val isOutgoing = item.replyBody.sender == Recipient.self()
        if (isOutgoing || (!isOutgoing && !recyclerView.canScrollVertically(1))) {
          recyclerView.post { recyclerView.scrollToPosition(positionStart) }
        }
      }
    }
  }

  interface Callback {
    fun onStartDirectReply(recipientId: RecipientId)
    fun requestFullScreen(fullscreen: Boolean)
    fun onReactionEmojiSelected(emoji: String)
  }
}
