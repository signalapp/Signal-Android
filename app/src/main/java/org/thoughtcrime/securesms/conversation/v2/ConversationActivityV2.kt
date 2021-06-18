package org.thoughtcrime.securesms.conversation.v2

import android.animation.FloatEvaluator
import android.animation.ValueAnimator
import android.content.res.Resources
import android.database.Cursor
import android.graphics.Rect
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.RelativeLayout
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_conversation_v2.*
import kotlinx.android.synthetic.main.activity_conversation_v2.view.*
import kotlinx.android.synthetic.main.activity_conversation_v2_action_bar.*
import kotlinx.android.synthetic.main.view_input_bar.view.*
import kotlinx.android.synthetic.main.view_input_bar_recording.*
import kotlinx.android.synthetic.main.view_input_bar_recording.view.*
import network.loki.messenger.R
import org.session.libsession.messaging.mentions.MentionsManager
import org.session.libsession.messaging.mentions.MentionsManager.getMentionCandidates
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity
import org.thoughtcrime.securesms.conversation.v2.input_bar.InputBarButton
import org.thoughtcrime.securesms.conversation.v2.input_bar.InputBarDelegate
import org.thoughtcrime.securesms.conversation.v2.input_bar.InputBarRecordingViewDelegate
import org.thoughtcrime.securesms.conversation.v2.input_bar.mentions.MentionCandidatesView
import org.thoughtcrime.securesms.conversation.v2.menus.ConversationActionModeCallback
import org.thoughtcrime.securesms.conversation.v2.menus.ConversationMenuHelper
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.loki.utilities.toPx
import org.thoughtcrime.securesms.mms.GlideApp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

class ConversationActivityV2 : PassphraseRequiredActionBarActivity(), InputBarDelegate, InputBarRecordingViewDelegate {
    private val lockViewHitMargin by lazy { toPx(40, resources) }
    private var threadID: Long = -1
    private var actionMode: ActionMode? = null
    private var isLockViewExpanded = false
    private var isShowingAttachmentOptions = false

    // TODO: Selected message background color
    // TODO: Overflow menu background + text color

    private val adapter by lazy {
        val cursor = DatabaseFactory.getMmsSmsDatabase(this).getConversation(threadID)
        val adapter = ConversationAdapter(
            this,
            cursor,
            onItemPress = { message, position ->
                handlePress(message, position)
            },
            onItemSwipeToReply = { message, position ->
                handleSwipeToReply(message, position)
            },
            onItemLongPress = { message, position ->
                handleLongPress(message, position)
            }
        )
        adapter.setHasStableIds(true)
        adapter
    }

    private val thread by lazy {
        DatabaseFactory.getThreadDatabase(this).getRecipientForThreadId(threadID)!!
    }

    private val glide by lazy { GlideApp.with(this) }

    private val screenWidth by lazy { Resources.getSystem().displayMetrics.widthPixels }

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
    }

    private fun setUpRecyclerView() {
        conversationRecyclerView.adapter = adapter
        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, true)
        conversationRecyclerView.layoutManager = layoutManager
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

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        ConversationMenuHelper.onPrepareOptionsMenu(menu, menuInflater, thread, this) { onOptionsItemSelected(it) }
        super.onPrepareOptionsMenu(menu)
        return true
    }
    // endregion

    // region Updating
    override fun inputBarHeightChanged(newValue: Int) {
        // Recycler view
        val recyclerViewLayoutParams = conversationRecyclerView.layoutParams as RelativeLayout.LayoutParams
        recyclerViewLayoutParams.bottomMargin = newValue + inputBarAdditionalContentContainer.height
        conversationRecyclerView.layoutParams = recyclerViewLayoutParams
        // Input bar additional content container
        val inputBarAdditionalContentContainerLayoutParams = inputBarAdditionalContentContainer.layoutParams as RelativeLayout.LayoutParams
        inputBarAdditionalContentContainerLayoutParams.bottomMargin = newValue
        inputBarAdditionalContentContainer.layoutParams = inputBarAdditionalContentContainerLayoutParams
        // Attachment options
        val attachmentButtonHeight = inputBar.attachmentsButtonContainer.height
        val bottomMargin = (newValue - attachmentButtonHeight) / 2
        val margin = toPx(8, resources)
        val attachmentOptionsContainerLayoutParams = attachmentOptionsContainer.layoutParams as RelativeLayout.LayoutParams
        attachmentOptionsContainerLayoutParams.bottomMargin = bottomMargin + attachmentButtonHeight + margin
        attachmentOptionsContainer.layoutParams = attachmentOptionsContainerLayoutParams
    }

    override fun inputBarEditTextContentChanged(newContent: CharSequence) {
        // TODO: Work this out further
        if (newContent.contains("@")) {
            showMentionCandidates()
        }
    }

    private fun showMentionCandidates() {
        inputBarAdditionalContentContainer.removeAllViews()
        val mentionCandidatesView = MentionCandidatesView(this)
        mentionCandidatesView.glide = glide
        inputBarAdditionalContentContainer.addView(mentionCandidatesView)
        val mentionCandidates = MentionsManager.getMentionCandidates("", threadID, thread.isOpenGroupRecipient)
        mentionCandidatesView.show(mentionCandidates, threadID)
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

    override fun handleInputBarRecordingViewHidden() {
        inputBar.alpha = 1.0f
        val animation = ValueAnimator.ofObject(FloatEvaluator(), 0.0f, 1.0f)
        animation.duration = 250L
        animation.addUpdateListener { animator ->
            inputBar.alpha = animator.animatedValue as Float
        }
        animation.start()
    }
    // endregion

    // region Interaction
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // TODO: Implement
        return super.onOptionsItemSelected(item)
    }

    // `position` is the adapter position; not the visual position
    private fun handlePress(message: MessageRecord, position: Int) {
        val actionMode = this.actionMode
        if (actionMode != null) {
            adapter.toggleSelection(message, position)
            val actionModeCallback = ConversationActionModeCallback(adapter, threadID, this)
            actionModeCallback.updateActionModeMenu(actionMode.menu)
            if (adapter.selectedItems.isEmpty()) {
                actionMode.finish()
                this.actionMode = null
            }
        }
    }

    // `position` is the adapter position; not the visual position
    private fun handleSwipeToReply(message: MessageRecord, position: Int) {

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
                expandLockView()
                isLockViewExpanded = true
            }
        } else {
            if (isLockViewExpanded) {
                collapseLockView()
                isLockViewExpanded = false
            }
        }
    }

    private fun isValidLockViewLocation(x: Int, y: Int): Boolean {
        val lockViewLocation = IntArray(2) { 0 }
        lockView.getLocationOnScreen(lockViewLocation)
        val hitRect = Rect(lockViewLocation[0] - lockViewHitMargin, 0,
            lockViewLocation[0] + lockView.width + lockViewHitMargin, lockViewLocation[1] + lockView.height)
        return hitRect.contains(x, y)
    }

    private fun expandLockView() {
        val animation = ValueAnimator.ofObject(FloatEvaluator(), lockView.scaleX, 1.10f)
        animation.duration = 250L
        animation.addUpdateListener { animator ->
            lockView.scaleX = animator.animatedValue as Float
            lockView.scaleY = animator.animatedValue as Float
        }
        animation.start()
    }

    private fun collapseLockView() {
        val animation = ValueAnimator.ofObject(FloatEvaluator(), lockView.scaleX, 1.0f)
        animation.duration = 250L
        animation.addUpdateListener { animator ->
            lockView.scaleX = animator.animatedValue as Float
            lockView.scaleY = animator.animatedValue as Float
        }
        animation.start()
    }

    override fun onMicrophoneButtonCancel(event: MotionEvent) {
        resetVoiceMessageUI()
    }

    override fun onMicrophoneButtonUp(event: MotionEvent) {
        if (isValidLockViewLocation(event.rawX.roundToInt(), event.rawY.roundToInt())) {
            inputBarRecordingView.lock()
        } else {
            resetVoiceMessageUI()
        }
    }

    private fun resetVoiceMessageUI() {
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
    // endregion
}