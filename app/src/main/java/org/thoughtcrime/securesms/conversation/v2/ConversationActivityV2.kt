package org.thoughtcrime.securesms.conversation.v2

import android.animation.FloatEvaluator
import android.animation.ValueAnimator
import android.content.res.Resources
import android.database.Cursor
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.RelativeLayout
import androidx.core.view.isVisible
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.ListenableWorker
import kotlinx.android.synthetic.main.activity_conversation_v2.*
import kotlinx.android.synthetic.main.activity_conversation_v2.view.*
import kotlinx.android.synthetic.main.activity_conversation_v2_action_bar.*
import kotlinx.android.synthetic.main.activity_home.*
import kotlinx.android.synthetic.main.view_conversation.view.*
import kotlinx.android.synthetic.main.view_input_bar.view.*
import kotlinx.android.synthetic.main.view_input_bar_recording.*
import kotlinx.android.synthetic.main.view_input_bar_recording.view.*
import network.loki.messenger.R
import nl.komponents.kovenant.ui.successUi
import org.session.libsession.messaging.mentions.MentionsManager
import org.session.libsession.messaging.open_groups.OpenGroupAPIV2
import org.session.libsession.utilities.recipients.Recipient
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.conversation.v2.dialogs.BlockedDialog
import org.thoughtcrime.securesms.conversation.v2.input_bar.InputBarButton
import org.thoughtcrime.securesms.conversation.v2.input_bar.InputBarDelegate
import org.thoughtcrime.securesms.conversation.v2.input_bar.InputBarRecordingViewDelegate
import org.thoughtcrime.securesms.conversation.v2.input_bar.mentions.MentionCandidatesView
import org.thoughtcrime.securesms.conversation.v2.menus.ConversationActionModeCallback
import org.thoughtcrime.securesms.conversation.v2.menus.ConversationMenuHelper
import org.thoughtcrime.securesms.conversation.v2.messages.VisibleMessageView
import org.thoughtcrime.securesms.database.Database
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.DraftDatabase
import org.thoughtcrime.securesms.database.DraftDatabase.Drafts
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.loki.api.PublicChatInfoUpdateWorker
import org.thoughtcrime.securesms.loki.dialogs.SeedDialog
import org.thoughtcrime.securesms.loki.utilities.OpenGroupUtilities
import org.thoughtcrime.securesms.loki.utilities.toPx
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.util.DateUtils
import java.util.*
import kotlin.math.*

class ConversationActivityV2 : PassphraseRequiredActionBarActivity(), InputBarDelegate,
    InputBarRecordingViewDelegate, ConversationRecyclerViewDelegate {
    private val scrollButtonFullVisibilityThreshold by lazy { toPx(120.0f, resources) }
    private val scrollButtonNoVisibilityThreshold by lazy { toPx(20.0f, resources) }
    private val screenWidth = Resources.getSystem().displayMetrics.widthPixels
    private var threadID: Long = -1
    private var actionMode: ActionMode? = null
    private var isLockViewExpanded = false
    private var isShowingAttachmentOptions = false
    private var mentionCandidatesView: MentionCandidatesView? = null

    // TODO: Selected message background color
    // TODO: Overflow menu background + text color
    // TODO: Typing indicators

    private val adapter by lazy {
        val cursor = DatabaseFactory.getMmsSmsDatabase(this).getConversation(threadID)
        val adapter = ConversationAdapter(
            this,
            cursor,
            onItemPress = { message, position, view ->
                handlePress(message, position, view)
            },
            onItemSwipeToReply = { message, position ->
                handleSwipeToReply(message, position)
            },
            onItemLongPress = { message, position ->
                handleLongPress(message, position)
            },
            glide
        )
        adapter
    }

    private val thread by lazy {
        DatabaseFactory.getThreadDatabase(this).getRecipientForThreadId(threadID)!!
    }

    private val glide by lazy { GlideApp.with(this) }
    private val lockViewHitMargin by lazy { toPx(40, resources) }
    private val gifButton by lazy { InputBarButton(this, R.drawable.ic_gif_white_24dp, hasOpaqueBackground = true, isGIFButton = true) }
    private val documentButton by lazy { InputBarButton(this, R.drawable.ic_document_small_dark, hasOpaqueBackground = true) }
    private val libraryButton by lazy { InputBarButton(this, R.drawable.ic_baseline_photo_library_24, hasOpaqueBackground = true) }
    private val cameraButton by lazy { InputBarButton(this, R.drawable.ic_baseline_photo_camera_24, hasOpaqueBackground = true) }

    // region Settings
    companion object {
        const val THREAD_ID = "thread_id"
    }
    // endregion

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?, isReady: Boolean) {
        super.onCreate(savedInstanceState, isReady)
        setContentView(R.layout.activity_conversation_v2)
        threadID = intent.getLongExtra(THREAD_ID, -1)
        setUpRecyclerView()
        setUpToolBar()
        setUpInputBar()
        restoreDraftIfNeeded()
        addOpenGroupGuidelinesIfNeeded()
        scrollToBottomButton.setOnClickListener { conversationRecyclerView.smoothScrollToPosition(0) }
        updateUnreadCount()
        setUpTypingObserver()
        updateSubtitle()
        getLatestOpenGroupInfoIfNeeded()
    }

    private fun setUpRecyclerView() {
        conversationRecyclerView.adapter = adapter
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, true)
        conversationRecyclerView.layoutManager = layoutManager
        conversationRecyclerView.delegate = this
        // Workaround for the fact that CursorRecyclerViewAdapter doesn't auto-update automatically (even though it says it will)
        LoaderManager.getInstance(this).restartLoader(0, null, object : LoaderManager.LoaderCallbacks<Cursor> {

            override fun onCreateLoader(id: Int, bundle: Bundle?): Loader<Cursor> {
                return ConversationLoader(threadID, this@ConversationActivityV2)
            }

            override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
                adapter.changeCursor(cursor)
            }

            override fun onLoaderReset(cursor: Loader<Cursor>) {
                adapter.changeCursor(null)
            }
        })
    }

    private fun setUpToolBar() {
        val actionBar = supportActionBar!!
        actionBar.setCustomView(R.layout.activity_conversation_v2_action_bar)
        actionBar.setDisplayShowCustomEnabled(true)
        conversationTitleView.text = thread.toShortString()
        profilePictureView.glide = glide
        profilePictureView.update(thread, threadID)
    }

    private fun setUpInputBar() {
        inputBar.delegate = this
        inputBarRecordingView.delegate = this
        // GIF button
        gifButtonContainer.addView(gifButton)
        gifButton.layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
        // Document button
        documentButtonContainer.addView(documentButton)
        documentButton.layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
        // Library button
        libraryButtonContainer.addView(libraryButton)
        libraryButton.layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
        // Camera button
        cameraButtonContainer.addView(cameraButton)
        cameraButton.layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
    }

    private fun restoreDraftIfNeeded() {
        val draftDB = DatabaseFactory.getDraftDatabase(this)
        val drafts = draftDB.getDrafts(threadID)
        draftDB.clearDrafts(threadID)
        val text = drafts.find { it.type == DraftDatabase.Draft.TEXT }?.value ?: return
        inputBar.text = text
    }

    private fun addOpenGroupGuidelinesIfNeeded() {
        val openGroup = DatabaseFactory.getLokiThreadDatabase(this).getOpenGroupChat(threadID) ?: return
        val isOxenHostedOpenGroup = openGroup.room == "session" || openGroup.room == "oxen"
            || openGroup.room == "lokinet" || openGroup.room == "crypto"
        if (!isOxenHostedOpenGroup) { return }
        openGroupGuidelinesView.visibility = View.VISIBLE
        val recyclerViewLayoutParams = conversationRecyclerView.layoutParams as RelativeLayout.LayoutParams
        recyclerViewLayoutParams.topMargin = toPx(57, resources) // The height of the open group guidelines view is hardcoded to this
        conversationRecyclerView.layoutParams = recyclerViewLayoutParams
    }

    private fun setUpTypingObserver() {
        ApplicationContext.getInstance(this).typingStatusRepository.getTypists(threadID).observe(this) { state ->
            val recipients = if (state != null) state.typists else listOf()
            typingIndicatorViewContainer.isVisible = recipients.isNotEmpty()
            typingIndicatorViewContainer.setTypists(recipients)
            inputBarHeightChanged(inputBar.height)
        }
    }

    private fun getLatestOpenGroupInfoIfNeeded() {
        val openGroup = DatabaseFactory.getLokiThreadDatabase(this).getOpenGroupChat(threadID) ?: return
        OpenGroupAPIV2.getMemberCount(openGroup.room, openGroup.server).successUi { updateSubtitle() }
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        ConversationMenuHelper.onPrepareOptionsMenu(menu, menuInflater, thread, this) { onOptionsItemSelected(it) }
        super.onPrepareOptionsMenu(menu)
        return true
    }

    override fun onDestroy() {
        saveDraft()
        super.onDestroy()
    }
    // endregion

    // region Updating & Animation
    override fun inputBarHeightChanged(newValue: Int) {
        // 36 DP is the exact height of the typing indicator view. It's also exactly 18 * 2, and 18 is the large message
        // corner radius. This makes 36 DP look "correct" in the context of other messages on the screen.
        val typingIndicatorHeight = if (typingIndicatorViewContainer.isVisible) toPx(36, resources) else 0
        // Recycler view
        val recyclerViewLayoutParams = conversationRecyclerView.layoutParams as RelativeLayout.LayoutParams
        recyclerViewLayoutParams.bottomMargin = newValue + additionalContentContainer.height + typingIndicatorHeight
        conversationRecyclerView.layoutParams = recyclerViewLayoutParams
        // Additional content container
        val additionalContentContainerLayoutParams = additionalContentContainer.layoutParams as RelativeLayout.LayoutParams
        additionalContentContainerLayoutParams.bottomMargin = newValue
        additionalContentContainer.layoutParams = additionalContentContainerLayoutParams
        // Attachment options
        val attachmentButtonHeight = inputBar.attachmentsButtonContainer.height
        val bottomMargin = (newValue - inputBar.additionalContentHeight - attachmentButtonHeight) / 2
        val margin = toPx(8, resources)
        val attachmentOptionsContainerLayoutParams = attachmentOptionsContainer.layoutParams as RelativeLayout.LayoutParams
        attachmentOptionsContainerLayoutParams.bottomMargin = bottomMargin + attachmentButtonHeight + margin
        attachmentOptionsContainer.layoutParams = attachmentOptionsContainerLayoutParams
        // Scroll to bottom button
        val scrollToBottomButtonLayoutParams = scrollToBottomButton.layoutParams as RelativeLayout.LayoutParams
        scrollToBottomButtonLayoutParams.bottomMargin = newValue + additionalContentContainer.height + toPx(12, resources)
        scrollToBottomButton.layoutParams = scrollToBottomButtonLayoutParams
    }

    override fun inputBarEditTextContentChanged(newContent: CharSequence) {
        // TODO: Implement the full mention show/hide logic
        if (newContent.contains("@")) {
            showMentionCandidates()
        } else {
            hideMentionCandidates()
        }
    }

    private fun showMentionCandidates() {
        additionalContentContainer.removeAllViews()
        val mentionCandidatesView = MentionCandidatesView(this)
        mentionCandidatesView.glide = glide
        additionalContentContainer.addView(mentionCandidatesView)
        val mentionCandidates = MentionsManager.getMentionCandidates("", threadID, thread.isOpenGroupRecipient)
        this.mentionCandidatesView = mentionCandidatesView
        mentionCandidatesView.show(mentionCandidates, threadID)
        mentionCandidatesView.alpha = 0.0f
        val animation = ValueAnimator.ofObject(FloatEvaluator(), mentionCandidatesView.alpha, 1.0f)
        animation.duration = 250L
        animation.addUpdateListener { animator ->
            mentionCandidatesView.alpha = animator.animatedValue as Float
        }
        animation.start()
    }

    private fun hideMentionCandidates() {
        val mentionCandidatesView = mentionCandidatesView ?: return
        val animation = ValueAnimator.ofObject(FloatEvaluator(), mentionCandidatesView.alpha, 0.0f)
        animation.duration = 250L
        animation.addUpdateListener { animator ->
            mentionCandidatesView.alpha = animator.animatedValue as Float
            if (animator.animatedFraction == 1.0f) { additionalContentContainer.removeAllViews() }
        }
        animation.start()
    }

    override fun toggleAttachmentOptions() {
        val targetAlpha = if (isShowingAttachmentOptions) 0.0f else 1.0f
        val allButtons = listOf( cameraButtonContainer, libraryButtonContainer, documentButtonContainer, gifButtonContainer)
        val isReversed = isShowingAttachmentOptions // Run the animation in reverse
        val count = allButtons.size
        allButtons.indices.forEach { index ->
            val view = allButtons[index]
            val animation = ValueAnimator.ofObject(FloatEvaluator(), view.alpha, targetAlpha)
            animation.duration = 250L
            animation.startDelay = if (isReversed) 50L * (count - index.toLong()) else 50L * index.toLong()
            animation.addUpdateListener { animator ->
                view.alpha = animator.animatedValue as Float
            }
            animation.start()
        }
        isShowingAttachmentOptions = !isShowingAttachmentOptions
    }

    override fun showVoiceMessageUI() {
        inputBarRecordingView.show()
        inputBar.alpha = 0.0f
        val animation = ValueAnimator.ofObject(FloatEvaluator(), 1.0f, 0.0f)
        animation.duration = 250L
        animation.addUpdateListener { animator ->
            inputBar.alpha = animator.animatedValue as Float
        }
        animation.start()
    }

    private fun expandVoiceMessageLockView() {
        val animation = ValueAnimator.ofObject(FloatEvaluator(), lockView.scaleX, 1.10f)
        animation.duration = 250L
        animation.addUpdateListener { animator ->
            lockView.scaleX = animator.animatedValue as Float
            lockView.scaleY = animator.animatedValue as Float
        }
        animation.start()
    }

    private fun collapseVoiceMessageLockView() {
        val animation = ValueAnimator.ofObject(FloatEvaluator(), lockView.scaleX, 1.0f)
        animation.duration = 250L
        animation.addUpdateListener { animator ->
            lockView.scaleX = animator.animatedValue as Float
            lockView.scaleY = animator.animatedValue as Float
        }
        animation.start()
    }

    private fun hideVoiceMessageUI() {
        val chevronImageView = inputBarRecordingView.inputBarChevronImageView
        val slideToCancelTextView = inputBarRecordingView.inputBarSlideToCancelTextView
        listOf( chevronImageView, slideToCancelTextView ).forEach { view ->
            val animation = ValueAnimator.ofObject(FloatEvaluator(), view.translationX, 0.0f)
            animation.duration = 250L
            animation.addUpdateListener { animator ->
                view.translationX = animator.animatedValue as Float
            }
            animation.start()
        }
        inputBarRecordingView.hide()
    }

    override fun handleVoiceMessageUIHidden() {
        inputBar.alpha = 1.0f
        val animation = ValueAnimator.ofObject(FloatEvaluator(), 0.0f, 1.0f)
        animation.duration = 250L
        animation.addUpdateListener { animator ->
            inputBar.alpha = animator.animatedValue as Float
        }
        animation.start()
    }

    override fun handleConversationRecyclerViewBottomOffsetChanged(bottomOffset: Int) {
        val rawAlpha = (bottomOffset.toFloat() - scrollButtonNoVisibilityThreshold) /
            (scrollButtonFullVisibilityThreshold - scrollButtonNoVisibilityThreshold)
        val alpha = max(min(rawAlpha, 1.0f), 0.0f)
        scrollToBottomButton.alpha = alpha
        updateUnreadCount()
    }

    private fun updateUnreadCount() {
        val unreadCount = DatabaseFactory.getMmsSmsDatabase(this).getUnreadCount(threadID)
        val formattedUnreadCount = if (unreadCount < 100) unreadCount.toString() else "99+"
        unreadCountTextView.text = formattedUnreadCount
        val textSize = if (unreadCount < 100) 12.0f else 9.0f
        unreadCountTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize)
        unreadCountTextView.setTypeface(Typeface.DEFAULT, if (unreadCount < 100) Typeface.BOLD else Typeface.NORMAL)
        unreadCountIndicator.isVisible = (unreadCount != 0)
    }

    private fun updateSubtitle() {
        muteIconImageView.isVisible = thread.isMuted
        conversationSubtitleView.isVisible = true
        if (thread.isMuted) {
            conversationSubtitleView.text = getString(R.string.ConversationActivity_muted_until_date, DateUtils.getFormattedDateTime(thread.mutedUntil, "EEE, MMM d, yyyy HH:mm", Locale.getDefault()))
        } else if (thread.isGroupRecipient) {
            val openGroup = DatabaseFactory.getLokiThreadDatabase(this).getOpenGroupChat(threadID)
            if (openGroup != null) {
                val userCount = DatabaseFactory.getLokiAPIDatabase(this).getUserCount(openGroup.room, openGroup.server) ?: 0
                conversationSubtitleView.text = getString(R.string.ConversationActivity_member_count, userCount)
            } else {
                conversationSubtitleView.isVisible = false
            }
        } else {
            conversationSubtitleView.isVisible = false
        }
    }
    // endregion

    // region Interaction
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // TODO: Implement
        return super.onOptionsItemSelected(item)
    }

    // `position` is the adapter position; not the visual position
    private fun handlePress(message: MessageRecord, position: Int, view: VisibleMessageView) {
        val actionMode = this.actionMode
        if (actionMode != null) {
            adapter.toggleSelection(message, position)
            val actionModeCallback = ConversationActionModeCallback(adapter, threadID, this)
            actionModeCallback.updateActionModeMenu(actionMode.menu)
            if (adapter.selectedItems.isEmpty()) {
                actionMode.finish()
                this.actionMode = null
            }
        } else {
            // NOTE:
            // We have to use onContentClick (rather than a click listener directly on
            // the view) so as to not interfere with all the other gestures. Do not add
            // onClickListeners directly to message content views.
            view.onContentClick()
            BlockedDialog(thread).show(supportFragmentManager, "Blocked Dialog")
        }
    }

    // `position` is the adapter position; not the visual position
    private fun handleSwipeToReply(message: MessageRecord, position: Int) {
        inputBar.draftQuote(message)
    }

    // `position` is the adapter position; not the visual position
    private fun handleLongPress(message: MessageRecord, position: Int) {
        val actionMode = this.actionMode
        val actionModeCallback = ConversationActionModeCallback(adapter, threadID, this)
        if (actionMode == null) { // Nothing should be selected if this is the case
            adapter.toggleSelection(message, position)
            this.actionMode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                startActionMode(actionModeCallback, ActionMode.TYPE_PRIMARY)
            } else {
                startActionMode(actionModeCallback)
            }
        } else {
            adapter.toggleSelection(message, position)
            actionModeCallback.updateActionModeMenu(actionMode.menu)
            if (adapter.selectedItems.isEmpty()) {
                actionMode.finish()
                this.actionMode = null
            }
        }
    }

    override fun onMicrophoneButtonMove(event: MotionEvent) {
        val rawX = event.rawX
        val chevronImageView = inputBarRecordingView.inputBarChevronImageView
        val slideToCancelTextView = inputBarRecordingView.inputBarSlideToCancelTextView
        if (rawX < screenWidth / 2) {
            val translationX = rawX - screenWidth / 2
            val sign = -1.0f
            val chevronDamping = 4.0f
            val labelDamping = 3.0f
            val chevronX = (chevronDamping * (sqrt(abs(translationX)) / sqrt(chevronDamping))) * sign
            val labelX = (labelDamping * (sqrt(abs(translationX)) / sqrt(labelDamping))) * sign
            chevronImageView.translationX = chevronX
            slideToCancelTextView.translationX = labelX
        } else {
            chevronImageView.translationX = 0.0f
            slideToCancelTextView.translationX = 0.0f
        }
        if (isValidLockViewLocation(event.rawX.roundToInt(), event.rawY.roundToInt())) {
            if (!isLockViewExpanded) {
                expandVoiceMessageLockView()
                isLockViewExpanded = true
            }
        } else {
            if (isLockViewExpanded) {
                collapseVoiceMessageLockView()
                isLockViewExpanded = false
            }
        }
    }

    override fun onMicrophoneButtonCancel(event: MotionEvent) {
        hideVoiceMessageUI()
    }

    override fun onMicrophoneButtonUp(event: MotionEvent) {
        if (isValidLockViewLocation(event.rawX.roundToInt(), event.rawY.roundToInt())) {
            inputBarRecordingView.lock()
        } else {
            hideVoiceMessageUI()
        }
    }

    private fun isValidLockViewLocation(x: Int, y: Int): Boolean {
        // We can be anywhere above the lock view and a bit to the side of it (at most `lockViewHitMargin`
        // to the side)
        val lockViewLocation = IntArray(2) { 0 }
        lockView.getLocationOnScreen(lockViewLocation)
        val hitRect = Rect(lockViewLocation[0] - lockViewHitMargin, 0,
            lockViewLocation[0] + lockView.width + lockViewHitMargin, lockViewLocation[1] + lockView.height)
        return hitRect.contains(x, y)
    }
    // endregion

    // region General
    private fun saveDraft() {
        val text = inputBar.text.trim()
        if (text.isEmpty()) { return }
        val drafts = Drafts()
        drafts.add(DraftDatabase.Draft(DraftDatabase.Draft.TEXT, text))
        val draftDB = DatabaseFactory.getDraftDatabase(this)
        draftDB.insertDrafts(threadID, drafts)
    }
    // endregion
}