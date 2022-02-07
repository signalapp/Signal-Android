package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewVisibleMessageBinding
import org.session.libsession.messaging.contacts.Contact.ContactContext
import org.session.libsession.messaging.open_groups.OpenGroupAPIV2
import org.session.libsession.utilities.ViewUtil
import org.session.libsignal.utilities.ThreadUtils
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.database.LokiThreadDatabase
import org.thoughtcrime.securesms.database.MmsDatabase
import org.thoughtcrime.securesms.database.MmsSmsDatabase
import org.thoughtcrime.securesms.database.SessionContactDatabase
import org.thoughtcrime.securesms.database.SmsDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.home.UserDetailsBottomSheet
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.disableClipping
import org.thoughtcrime.securesms.util.getColorWithID
import org.thoughtcrime.securesms.util.toDp
import org.thoughtcrime.securesms.util.toPx
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

@AndroidEntryPoint
class VisibleMessageView : LinearLayout {

    @Inject lateinit var threadDb: ThreadDatabase
    @Inject lateinit var contactDb: SessionContactDatabase
    @Inject lateinit var lokiThreadDb: LokiThreadDatabase
    @Inject lateinit var mmsSmsDb: MmsSmsDatabase
    @Inject lateinit var smsDb: SmsDatabase
    @Inject lateinit var mmsDb: MmsDatabase

    private lateinit var binding: ViewVisibleMessageBinding
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
    var indexInAdapter: Int = -1
    var snIsSelected = false
        set(value) {
            field = value
            binding.messageTimestampTextView.isVisible = isSelected
            handleIsSelectedChanged()
        }
    var onPress: ((event: MotionEvent) -> Unit)? = null
    var onSwipeToReply: (() -> Unit)? = null
    var onLongPress: (() -> Unit)? = null
    var contentViewDelegate: VisibleMessageContentViewDelegate? = null

    companion object {
        const val swipeToReplyThreshold = 64.0f // dp
        const val longPressMovementThreshold = 10.0f // dp
        const val longPressDurationThreshold = 250L // ms
        const val maxDoubleTapInterval = 200L
    }

    // region Lifecycle
    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        binding = ViewVisibleMessageBinding.inflate(LayoutInflater.from(context), this, true)
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        isHapticFeedbackEnabled = true
        setWillNotDraw(false)
        binding.expirationTimerViewContainer.disableClipping()
        binding.messageContentContainer.disableClipping()
    }
    // endregion

    // region Updating
    fun bind(message: MessageRecord, previous: MessageRecord?, next: MessageRecord?, glide: GlideRequests, searchQuery: String?) {
        val sender = message.individualRecipient
        val senderSessionID = sender.address.serialize()
        val threadID = message.threadId
        val thread = threadDb.getRecipientForThreadId(threadID) ?: return
        val contact = contactDb.getContactWithSessionID(senderSessionID)
        val isGroupThread = thread.isGroupRecipient
        val isStartOfMessageCluster = isStartOfMessageCluster(message, previous, isGroupThread)
        val isEndOfMessageCluster = isEndOfMessageCluster(message, next, isGroupThread)
        // Show profile picture and sender name if this is a group thread AND
        // the message is incoming
        if (isGroupThread && !message.isOutgoing) {
            binding.profilePictureContainer.visibility = if (isEndOfMessageCluster) View.VISIBLE else View.INVISIBLE
            binding.profilePictureView.publicKey = senderSessionID
            binding.profilePictureView.glide = glide
            binding.profilePictureView.update(message.individualRecipient)
            binding.profilePictureView.setOnClickListener {
                showUserDetails(senderSessionID, threadID)
            }
            if (thread.isOpenGroupRecipient) {
                val openGroup = lokiThreadDb.getOpenGroupChat(threadID) ?: return
                val isModerator = OpenGroupAPIV2.isUserModerator(senderSessionID, openGroup.room, openGroup.server)
                binding.moderatorIconImageView.visibility = if (isModerator) View.VISIBLE else View.INVISIBLE
            } else {
                binding.moderatorIconImageView.visibility = View.INVISIBLE
            }
            binding.senderNameTextView.isVisible = isStartOfMessageCluster
            val context = if (thread.isOpenGroupRecipient) ContactContext.OPEN_GROUP else ContactContext.REGULAR
            binding.senderNameTextView.text = contact?.displayName(context) ?: senderSessionID
        } else {
            binding.profilePictureContainer.visibility = View.GONE
            binding.senderNameTextView.visibility = View.GONE
        }
        // Date break
        binding.dateBreakTextView.showDateBreak(message, previous)
        // Timestamp
        binding.messageTimestampTextView.text = DateUtils.getDisplayFormattedTimeSpanString(context, Locale.getDefault(), message.timestamp)
        // Margins
        val startPadding = if (isGroupThread) {
            if (message.isOutgoing) resources.getDimensionPixelSize(R.dimen.very_large_spacing) else toPx(50,resources)
        } else {
            if (message.isOutgoing) resources.getDimensionPixelSize(R.dimen.very_large_spacing)
            else resources.getDimensionPixelSize(R.dimen.medium_spacing)
        }
        val endPadding = if (message.isOutgoing) resources.getDimensionPixelSize(R.dimen.medium_spacing)
            else resources.getDimensionPixelSize(R.dimen.very_large_spacing)
        binding.messageContentContainer.setPaddingRelative(startPadding, 0, endPadding, 0)
        // Set inter-message spacing
        setMessageSpacing(isStartOfMessageCluster, isEndOfMessageCluster)
        // Gravity
        val gravity = if (message.isOutgoing) Gravity.END else Gravity.START
        binding.mainContainer.gravity = gravity or Gravity.BOTTOM
        // Message status indicator
        val (iconID, iconColor) = getMessageStatusImage(message)
        if (iconID != null) {
            val drawable = ContextCompat.getDrawable(context, iconID)?.mutate()
            if (iconColor != null) {
                drawable?.setTint(iconColor)
            }
            binding.messageStatusImageView.setImageDrawable(drawable)
        }
        if (message.isOutgoing) {
            val lastMessageID = mmsSmsDb.getLastMessageID(message.threadId)
            binding.messageStatusImageView.isVisible = !message.isSent || message.id == lastMessageID
        } else {
            binding.messageStatusImageView.isVisible = false
        }
        // Expiration timer
        updateExpirationTimer(message)
        // Calculate max message bubble width
        var maxWidth = screenWidth - startPadding - endPadding
        if (binding.profilePictureContainer.visibility != View.GONE) { maxWidth -= binding.profilePictureContainer.width }
        // Populate content view
        binding.messageContentView.indexInAdapter = indexInAdapter
        binding.messageContentView.bind(message, isStartOfMessageCluster, isEndOfMessageCluster, glide, maxWidth, thread, searchQuery, message.isOutgoing || isGroupThread || (contact?.isTrusted ?: false))
        binding.messageContentView.delegate = contentViewDelegate
        onDoubleTap = { binding.messageContentView.onContentDoubleTap?.invoke() }
    }

    private fun setMessageSpacing(isStartOfMessageCluster: Boolean, isEndOfMessageCluster: Boolean) {
        val topPadding = if (isStartOfMessageCluster) R.dimen.conversation_vertical_message_spacing_default else R.dimen.conversation_vertical_message_spacing_collapse
        ViewUtil.setPaddingTop(this, resources.getDimension(topPadding).roundToInt())
        val bottomPadding = if (isEndOfMessageCluster) R.dimen.conversation_vertical_message_spacing_default else R.dimen.conversation_vertical_message_spacing_collapse
        ViewUtil.setPaddingBottom(this, resources.getDimension(bottomPadding).roundToInt())
    }

    private fun isStartOfMessageCluster(current: MessageRecord, previous: MessageRecord?, isGroupThread: Boolean): Boolean {
        return if (isGroupThread) {
            previous == null || previous.isUpdate || !DateUtils.isSameHour(current.timestamp, previous.timestamp)
                || current.recipient.address != previous.recipient.address
        } else {
            previous == null || previous.isUpdate || !DateUtils.isSameHour(current.timestamp, previous.timestamp)
                || current.isOutgoing != previous.isOutgoing
        }
    }

    private fun isEndOfMessageCluster(current: MessageRecord, next: MessageRecord?, isGroupThread: Boolean): Boolean {
        return if (isGroupThread) {
            next == null || next.isUpdate || !DateUtils.isSameHour(current.timestamp, next.timestamp)
                || current.recipient.address != next.recipient.address
        } else {
            next == null || next.isUpdate || !DateUtils.isSameHour(current.timestamp, next.timestamp)
                || current.isOutgoing != next.isOutgoing
        }
    }

    private fun getMessageStatusImage(message: MessageRecord): Pair<Int?,Int?> {
        return when {
            !message.isOutgoing -> null to null
            message.isFailed -> R.drawable.ic_error to resources.getColor(R.color.destructive, context.theme)
            message.isPending -> R.drawable.ic_circle_dot_dot_dot to null
            message.isRead -> R.drawable.ic_filled_circle_check to null
            else -> R.drawable.ic_circle_check to null
        }
    }

    private fun updateExpirationTimer(message: MessageRecord) {
        val expirationTimerViewLayoutParams = binding.expirationTimerView.layoutParams as MarginLayoutParams
        val container = binding.expirationTimerViewContainer
        val content = binding.messageContentView
        val expiration = binding.expirationTimerView
        container.removeAllViewsInLayout()
        container.addView(if (message.isOutgoing) expiration else content)
        container.addView(if (message.isOutgoing) content else expiration)
        val expirationTimerViewSize = toPx(12, resources)
        val smallSpacing = resources.getDimension(R.dimen.small_spacing).roundToInt()
        expirationTimerViewLayoutParams.marginStart = if (message.isOutgoing) -(smallSpacing + expirationTimerViewSize) else 0
        expirationTimerViewLayoutParams.marginEnd = if (message.isOutgoing) 0 else -(smallSpacing + expirationTimerViewSize)
        binding.expirationTimerView.layoutParams = expirationTimerViewLayoutParams
        if (message.expiresIn > 0 && !message.isPending) {
            binding.expirationTimerView.setColorFilter(ResourcesCompat.getColor(resources, R.color.text, context.theme))
            binding.expirationTimerView.isVisible = true
            binding.expirationTimerView.setPercentComplete(0.0f)
            if (message.expireStarted > 0) {
                binding.expirationTimerView.setExpirationTime(message.expireStarted, message.expiresIn)
                binding.expirationTimerView.startAnimation()
                if (message.expireStarted + message.expiresIn <= System.currentTimeMillis()) {
                    ApplicationContext.getInstance(context).expiringMessageManager.checkSchedule()
                }
            } else if (!message.isMediaPending) {
                binding.expirationTimerView.setPercentComplete(0.0f)
                binding.expirationTimerView.stopAnimation()
                ThreadUtils.queue {
                    val expirationManager = ApplicationContext.getInstance(context).expiringMessageManager
                    val id = message.getId()
                    val mms = message.isMms
                    if (mms) mmsDb.markExpireStarted(id) else smsDb.markExpireStarted(id)
                    expirationManager.scheduleDeletion(id, mms, message.expiresIn)
                }
            } else {
                binding.expirationTimerView.stopAnimation()
                binding.expirationTimerView.setPercentComplete(0.0f)
            }
        } else {
            binding.expirationTimerView.isVisible = false
        }
        container.requestLayout()
    }

    private fun handleIsSelectedChanged() {
        background = if (snIsSelected) {
            ColorDrawable(context.resources.getColorWithID(R.color.message_selected, context.theme))
        } else {
            null
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (translationX < 0 && !binding.expirationTimerView.isVisible) {
            val spacing = context.resources.getDimensionPixelSize(R.dimen.small_spacing)
            val threshold = swipeToReplyThreshold
            val iconSize = toPx(24, context.resources)
            val bottomVOffset = paddingBottom + binding.messageStatusImageView.height + (binding.messageContentView.height - iconSize) / 2
            swipeToReplyIconRect.left = binding.messageContentContainer.right - binding.messageContentContainer.paddingEnd + spacing
            swipeToReplyIconRect.top = height - bottomVOffset - iconSize
            swipeToReplyIconRect.right = binding.messageContentContainer.right - binding.messageContentContainer.paddingEnd + iconSize + spacing
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
        binding.profilePictureView.recycle()
        binding.messageContentView.recycle()
    }
    // endregion

    // region Interaction
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (onPress == null || onSwipeToReply == null || onLongPress == null) { return false }
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
        gestureHandler.postDelayed(newLongPressCallback, longPressDurationThreshold)
        onDownTimestamp = Date().time
    }

    private fun onMove(event: MotionEvent) {
        val translationX = toDp(event.rawX + dx, context.resources)
        if (abs(translationX) < longPressMovementThreshold || snIsSelected) {
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
        binding.dateBreakTextView.translationX = -x // Bit of a hack to keep the date break text view from moving
        postInvalidate() // Ensure onDraw(canvas:) is called
        if (abs(x) > swipeToReplyThreshold && abs(previousTranslationX) < swipeToReplyThreshold) {
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
        previousTranslationX = x
    }

    private fun onCancel(event: MotionEvent) {
        if (abs(translationX) > swipeToReplyThreshold) {
            onSwipeToReply?.invoke()
        }
        longPressCallback?.let { gestureHandler.removeCallbacks(it) }
        resetPosition()
    }

    private fun onUp(event: MotionEvent) {
        if (abs(translationX) > swipeToReplyThreshold) {
            onSwipeToReply?.invoke()
        } else if ((Date().time - onDownTimestamp) < longPressDurationThreshold) {
            longPressCallback?.let { gestureHandler.removeCallbacks(it) }
            val pressCallback = this.pressCallback
            if (pressCallback != null) {
                // If we're here and pressCallback isn't null, it means that we tapped again within
                // maxDoubleTapInterval ms and we should count this as a double tap
                gestureHandler.removeCallbacks(pressCallback)
                this.pressCallback = null
                onDoubleTap?.invoke()
            } else {
                val newPressCallback = Runnable { onPress(event) }
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
        binding.dateBreakTextView.animate()
            .translationX(0.0f)
            .setDuration(150)
            .start()
    }

    private fun onLongPress() {
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onLongPress?.invoke()
    }

    fun onContentClick(event: MotionEvent) {
        binding.messageContentView.onContentClick.iterator().forEach { clickHandler -> clickHandler.invoke(event) }
    }

    private fun onPress(event: MotionEvent) {
        onPress?.invoke(event)
        pressCallback = null
    }

    private fun showUserDetails(publicKey: String, threadID: Long) {
        val userDetailsBottomSheet = UserDetailsBottomSheet()
        val bundle = bundleOf(
                UserDetailsBottomSheet.ARGUMENT_PUBLIC_KEY to publicKey,
                UserDetailsBottomSheet.ARGUMENT_THREAD_ID to threadID
        )
        userDetailsBottomSheet.arguments = bundle
        val activity = context as AppCompatActivity
        userDetailsBottomSheet.show(activity.supportFragmentManager, userDetailsBottomSheet.tag)
    }

    fun playVoiceMessage() {
        binding.messageContentView.playVoiceMessage()
    }
    // endregion
}
