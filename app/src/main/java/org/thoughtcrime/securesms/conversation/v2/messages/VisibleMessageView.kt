package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.*
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import kotlinx.android.synthetic.main.view_visible_message.view.*
import kotlinx.android.synthetic.main.view_visible_message.view.profilePictureView
import network.loki.messenger.R
import org.session.libsession.messaging.contacts.Contact.ContactContext
import org.session.libsession.messaging.open_groups.OpenGroupAPIV2
import org.session.libsession.utilities.ViewUtil
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.loki.utilities.getColorWithID
import org.thoughtcrime.securesms.loki.utilities.toDp
import org.thoughtcrime.securesms.loki.utilities.toPx
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.util.DateUtils
import java.util.*
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class VisibleMessageView : LinearLayout {
    private val screenWidth = Resources.getSystem().displayMetrics.widthPixels
    private val swipeToReplyIcon = ContextCompat.getDrawable(context, R.drawable.ic_baseline_reply_24)!!.mutate()
    private val swipeToReplyIconRect = Rect()
    private var dx = 0.0f
    private var previousTranslationX = 0.0f
    private val gestureHandler = Handler(Looper.getMainLooper())
    private var pressCallback: Runnable? = null
    private var longPressCallback: Runnable? = null
    private var onDownTimestamp = 0L
    private var onDoubleTap: (() -> Unit)? = null
    var snIsSelected = false
        set(value) { field = value; handleIsSelectedChanged()}
    var onPress: ((rawX: Int, rawY: Int) -> Unit)? = null
    var onSwipeToReply: (() -> Unit)? = null
    var onLongPress: (() -> Unit)? = null
    var contentViewDelegate: VisibleMessageContentViewDelegate? = null

    companion object {
        const val swipeToReplyThreshold = 80.0f // dp
        const val longPressMovementTreshold = 10.0f // dp
        const val longPressDurationThreshold = 250L // ms
        const val maxDoubleTapInterval = 200L
    }

    // region Lifecycle
    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        LayoutInflater.from(context).inflate(R.layout.view_visible_message, this)
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        isHapticFeedbackEnabled = true
        setWillNotDraw(false)
    }
    // endregion

    // region Updating
    fun bind(message: MessageRecord, previous: MessageRecord?, next: MessageRecord?, glide: GlideRequests) {
        val sender = message.individualRecipient
        val senderSessionID = sender.address.serialize()
        val threadID = message.threadId
        val threadDB = DatabaseFactory.getThreadDatabase(context)
        val thread = threadDB.getRecipientForThreadId(threadID)!!
        val contactDB = DatabaseFactory.getSessionContactDatabase(context)
        val isGroupThread = thread.isGroupRecipient
        val isStartOfMessageCluster = isStartOfMessageCluster(message, previous, isGroupThread)
        val isEndOfMessageCluster = isEndOfMessageCluster(message, next, isGroupThread)
        // Show profile picture and sender name if this is a group thread AND
        // the message is incoming
        if (isGroupThread && !message.isOutgoing) {
            profilePictureContainer.visibility = if (isEndOfMessageCluster) View.VISIBLE else View.INVISIBLE
            profilePictureView.publicKey = senderSessionID
            profilePictureView.glide = glide
            profilePictureView.update()
            if (thread.isOpenGroupRecipient) {
                val openGroup = DatabaseFactory.getLokiThreadDatabase(context).getOpenGroupChat(threadID)!!
                val isModerator = OpenGroupAPIV2.isUserModerator(senderSessionID, openGroup.room, openGroup.server)
                moderatorIconImageView.visibility = if (isModerator) View.VISIBLE else View.INVISIBLE
            } else {
                moderatorIconImageView.visibility = View.INVISIBLE
            }
            senderNameTextView.isVisible = isStartOfMessageCluster
            val context = if (thread.isOpenGroupRecipient) ContactContext.OPEN_GROUP else ContactContext.REGULAR
            senderNameTextView.text = contactDB.getContactWithSessionID(senderSessionID)?.displayName(context) ?: senderSessionID
        } else {
            profilePictureContainer.visibility = View.GONE
            senderNameTextView.visibility = View.GONE
        }
        // Date break
        val showDateBreak = (previous == null || !DateUtils.isSameDay(message.timestamp, previous.timestamp))
        dateBreakTextView.isVisible = showDateBreak
        dateBreakTextView.text = if (showDateBreak) DateUtils.getRelativeDate(context, Locale.getDefault(), message.timestamp) else ""
        // Timestamp
        messageTimestampTextView.text = DateUtils.getExtendedRelativeTimeSpanString(context, Locale.getDefault(), message.timestamp)
        // Margins
        val messageContentContainerLayoutParams = messageContentContainer.layoutParams as LinearLayout.LayoutParams
        if (isGroupThread) {
            messageContentContainerLayoutParams.leftMargin = if (message.isOutgoing) resources.getDimension(R.dimen.very_large_spacing).toInt() else 0
        } else {
            messageContentContainerLayoutParams.leftMargin = if (message.isOutgoing) resources.getDimension(R.dimen.very_large_spacing).toInt()
                else resources.getDimension(R.dimen.medium_spacing).toInt()
        }
        messageContentContainerLayoutParams.rightMargin = if (message.isOutgoing) resources.getDimension(R.dimen.medium_spacing).toInt()
            else resources.getDimension(R.dimen.very_large_spacing).toInt()
        messageContentContainer.layoutParams = messageContentContainerLayoutParams
        // Set inter-message spacing
        setMessageSpacing(isStartOfMessageCluster, isEndOfMessageCluster)
        // Gravity
        val gravity = if (message.isOutgoing) Gravity.RIGHT else Gravity.LEFT
        mainContainer.gravity = gravity or Gravity.BOTTOM
        // Message status indicator
        val iconID = getMessageStatusImage(message)
        if (iconID != null) {
            messageStatusImageView.setImageResource(iconID)
        }
        if (message.isOutgoing) {
            val lastMessageID = DatabaseFactory.getMmsSmsDatabase(context).getLastMessageID(message.threadId)
            messageStatusImageView.isVisible = !message.isSent || message.id == lastMessageID
        } else {
            messageStatusImageView.isVisible = false
        }
        // Calculate max message bubble width
        var maxWidth = screenWidth - messageContentContainerLayoutParams.leftMargin - messageContentContainerLayoutParams.rightMargin
        if (profilePictureContainer.visibility != View.GONE) { maxWidth -= profilePictureContainer.width }
        // Populate content view
        messageContentView.bind(message, isStartOfMessageCluster, isEndOfMessageCluster, glide, maxWidth, thread)
        messageContentView.delegate = contentViewDelegate
        onDoubleTap = { messageContentView.onContentDoubleTap?.invoke() }
    }

    private fun setMessageSpacing(isStartOfMessageCluster: Boolean, isEndOfMessageCluster: Boolean) {
        val topPadding = if (isStartOfMessageCluster) R.dimen.conversation_vertical_message_spacing_default else R.dimen.conversation_vertical_message_spacing_collapse
        ViewUtil.setPaddingTop(this, resources.getDimension(topPadding).roundToInt())
        val bottomPadding = if (isEndOfMessageCluster) R.dimen.conversation_vertical_message_spacing_default else R.dimen.conversation_vertical_message_spacing_collapse
        ViewUtil.setPaddingBottom(this, resources.getDimension(bottomPadding).roundToInt())
    }

    private fun isStartOfMessageCluster(current: MessageRecord, previous: MessageRecord?, isGroupThread: Boolean): Boolean {
        return if (isGroupThread) {
            previous == null || previous.isUpdate || !DateUtils.isSameDay(current.timestamp, previous.timestamp)
                || current.recipient.address != previous.recipient.address
        } else {
            previous == null || previous.isUpdate || !DateUtils.isSameDay(current.timestamp, previous.timestamp)
                || current.isOutgoing != previous.isOutgoing
        }
    }

    private fun isEndOfMessageCluster(current: MessageRecord, next: MessageRecord?, isGroupThread: Boolean): Boolean {
        return if (isGroupThread) {
            next == null || next.isUpdate || !DateUtils.isSameDay(current.timestamp, next.timestamp)
                || current.recipient.address != next.recipient.address
        } else {
            next == null || next.isUpdate || !DateUtils.isSameDay(current.timestamp, next.timestamp)
                || current.isOutgoing != next.isOutgoing
        }
    }

    private fun getMessageStatusImage(message: MessageRecord): Int? {
        when {
            !message.isOutgoing -> return null
            message.isFailed -> return R.drawable.ic_error
            message.isPending -> return R.drawable.ic_circle_dot_dot_dot
            message.isRead -> return R.drawable.ic_filled_circle_check
            else -> return R.drawable.ic_circle_check
        }
    }

    private fun handleIsSelectedChanged() {
        background = if (snIsSelected) {
            ColorDrawable(context.resources.getColorWithID(R.color.message_selected, context.theme))
        } else {
            null
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (translationX < 0) {
            val spacing = context.resources.getDimensionPixelSize(R.dimen.small_spacing)
            val threshold = VisibleMessageView.swipeToReplyThreshold
            val iconSize = toPx(24, context.resources)
            val bottomVOffset = paddingBottom + messageStatusImageView.height + (messageContentView.height - iconSize) / 2
            swipeToReplyIconRect.left = messageContentContainer.right + spacing
            swipeToReplyIconRect.top = height - bottomVOffset - iconSize
            swipeToReplyIconRect.right = messageContentContainer.right + iconSize + spacing
            swipeToReplyIconRect.bottom = height - bottomVOffset
            swipeToReplyIcon.bounds = swipeToReplyIconRect
            swipeToReplyIcon.alpha = (255.0f * (min(abs(translationX), threshold) / threshold)).roundToInt()
        } else {
            swipeToReplyIcon.alpha = 0
        }
        swipeToReplyIcon.draw(canvas)
        super.onDraw(canvas)
    }

    fun recycle() {
        profilePictureView.recycle()
        messageContentView.recycle()
    }
    // endregion

    // region Interaction
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> onDown(event)
            MotionEvent.ACTION_MOVE -> onMove(event)
            MotionEvent.ACTION_CANCEL -> onCancel(event)
            MotionEvent.ACTION_UP -> onUp(event)
        }
        return true
    }

    private fun onDown(event: MotionEvent) {
        dx = x - event.rawX
        longPressCallback?.let { gestureHandler.removeCallbacks(it) }
        val newLongPressCallback = Runnable { onLongPress() }
        this.longPressCallback = newLongPressCallback
        gestureHandler.postDelayed(newLongPressCallback, VisibleMessageView.longPressDurationThreshold)
        onDownTimestamp = Date().time
    }

    private fun onMove(event: MotionEvent) {
        val translationX = toDp(event.rawX + dx, context.resources)
        if (abs(translationX) < VisibleMessageView.longPressMovementTreshold || snIsSelected) {
            return
        } else {
            longPressCallback?.let { gestureHandler.removeCallbacks(it) }
        }
        if (translationX > 0) { return } // Only allow swipes to the left
        // The idea here is to asymptotically approach a maximum drag distance
        val damping = 50.0f
        val sign = -1.0f
        val x = (damping * (sqrt(abs(translationX)) / sqrt(damping))) * sign
        this.translationX = x
        this.dateBreakTextView.translationX = -x // Bit of a hack to keep the date break text view from moving
        postInvalidate() // Ensure onDraw(canvas:) is called
        if (abs(x) > VisibleMessageView.swipeToReplyThreshold && abs(previousTranslationX) < VisibleMessageView.swipeToReplyThreshold) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            } else {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
        previousTranslationX = x
    }

    private fun onCancel(event: MotionEvent) {
        if (abs(translationX) > VisibleMessageView.swipeToReplyThreshold) {
            onSwipeToReply?.invoke()
        }
        longPressCallback?.let { gestureHandler.removeCallbacks(it) }
        resetPosition()
    }

    private fun onUp(event: MotionEvent) {
        if (abs(translationX) > VisibleMessageView.swipeToReplyThreshold) {
            onSwipeToReply?.invoke()
        } else if ((Date().time - onDownTimestamp) < VisibleMessageView.longPressDurationThreshold) {
            longPressCallback?.let { gestureHandler.removeCallbacks(it) }
            val pressCallback = this.pressCallback
            if (pressCallback != null) {
                // If we're here and pressCallback isn't null, it means that we tapped again within
                // maxDoubleTapInterval ms and we should count this as a double tap
                gestureHandler.removeCallbacks(pressCallback)
                this.pressCallback = null
                onDoubleTap?.invoke()
            } else {
                val newPressCallback = Runnable { onPress(event.rawX.toInt(), event.rawY.toInt()) }
                this.pressCallback = newPressCallback
                gestureHandler.postDelayed(newPressCallback, VisibleMessageView.maxDoubleTapInterval)
            }
        }
        resetPosition()
    }

    private fun resetPosition() {
        animate()
            .translationX(0.0f)
            .setDuration(150)
            .setUpdateListener {
                postInvalidate() // Ensure onDraw(canvas:) is called
            }
            .start()
        // Bit of a hack to keep the date break text view from moving
        dateBreakTextView.animate()
            .translationX(0.0f)
            .setDuration(150)
            .start()
    }

    private fun onLongPress() {
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onLongPress?.invoke()
    }

    fun onContentClick(rawRect: Rect) {
        messageContentView.onContentClick?.invoke(rawRect)
    }

    private fun onPress(rawX: Int, rawY: Int) {
        onPress?.invoke(rawX, rawY)
        pressCallback = null
    }
    // endregion
}
