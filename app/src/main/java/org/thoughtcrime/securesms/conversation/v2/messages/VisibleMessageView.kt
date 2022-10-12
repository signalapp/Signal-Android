package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import dagger.hilt.android.AndroidEntryPoint
import network.loki.messenger.R
import network.loki.messenger.databinding.ViewVisibleMessageBinding
import org.session.libsession.messaging.contacts.Contact
import org.session.libsession.messaging.contacts.Contact.ContactContext
import org.session.libsession.messaging.open_groups.OpenGroupApi
import org.session.libsession.utilities.Address
import org.session.libsession.utilities.ViewUtil
import org.session.libsession.utilities.getColorFromAttr
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.ThreadUtils
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.conversation.v2.ConversationActivityV2
import org.thoughtcrime.securesms.database.LokiAPIDatabase
import org.thoughtcrime.securesms.database.LokiThreadDatabase
import org.thoughtcrime.securesms.database.MmsDatabase
import org.thoughtcrime.securesms.database.MmsSmsDatabase
import org.thoughtcrime.securesms.database.SmsDatabase
import org.thoughtcrime.securesms.database.ThreadDatabase
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.groups.OpenGroupManager
import org.thoughtcrime.securesms.home.UserDetailsBottomSheet
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.disableClipping
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
    @Inject lateinit var lokiThreadDb: LokiThreadDatabase
    @Inject lateinit var lokiApiDb: LokiAPIDatabase
    @Inject lateinit var mmsSmsDb: MmsSmsDatabase
    @Inject lateinit var smsDb: SmsDatabase
    @Inject lateinit var mmsDb: MmsDatabase

    private val binding by lazy { ViewVisibleMessageBinding.bind(this) }
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
            handleIsSelectedChanged()
        }
    var onPress: ((event: MotionEvent) -> Unit)? = null
    var onSwipeToReply: (() -> Unit)? = null
    var onLongPress: (() -> Unit)? = null
    val messageContentView: VisibleMessageContentView by lazy { binding.messageContentView }

    companion object {
        const val swipeToReplyThreshold = 64.0f // dp
        const val longPressMovementThreshold = 10.0f // dp
        const val longPressDurationThreshold = 250L // ms
        const val maxDoubleTapInterval = 200L
    }

    // region Lifecycle
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onFinishInflate() {
        super.onFinishInflate()
        initialize()
    }

    private fun initialize() {
        isHapticFeedbackEnabled = true
        setWillNotDraw(false)
        binding.messageInnerContainer.disableClipping()
        binding.messageContentView.disableClipping()
    }
    // endregion

    // region Updating
    fun bind(
        message: MessageRecord,
        previous: MessageRecord?,
        next: MessageRecord?,
        glide: GlideRequests,
        searchQuery: String?,
        contact: Contact?,
        senderSessionID: String,
        delegate: VisibleMessageViewDelegate?,
    ) {
        val threadID = message.threadId
        val thread = threadDb.getRecipientForThreadId(threadID) ?: return
        val isGroupThread = thread.isGroupRecipient
        val isStartOfMessageCluster = isStartOfMessageCluster(message, previous, isGroupThread)
        val isEndOfMessageCluster = isEndOfMessageCluster(message, next, isGroupThread)
        // Show profile picture and sender name if this is a group thread AND
        // the message is incoming
        binding.moderatorIconImageView.isVisible = false
        binding.profilePictureView.root.visibility = when {
            thread.isGroupRecipient && !message.isOutgoing && isEndOfMessageCluster -> View.VISIBLE
            thread.isGroupRecipient -> View.INVISIBLE
            else -> View.GONE
        }

        val bottomMargin = if (isEndOfMessageCluster) resources.getDimensionPixelSize(R.dimen.small_spacing)
        else ViewUtil.dpToPx(context,2)

        if (binding.profilePictureView.root.visibility == View.GONE) {
            val expirationParams = binding.messageInnerContainer.layoutParams as MarginLayoutParams
            expirationParams.bottomMargin = bottomMargin
            binding.messageInnerContainer.layoutParams = expirationParams
        } else {
            val avatarLayoutParams = binding.profilePictureView.root.layoutParams as MarginLayoutParams
            avatarLayoutParams.bottomMargin = bottomMargin
            binding.profilePictureView.root.layoutParams = avatarLayoutParams
        }

        if (isGroupThread && !message.isOutgoing) {
            if (isEndOfMessageCluster) {
                binding.profilePictureView.root.publicKey = senderSessionID
                binding.profilePictureView.root.glide = glide
                binding.profilePictureView.root.update(message.individualRecipient)
                binding.profilePictureView.root.setOnClickListener {
                    if (thread.isOpenGroupRecipient) {
                        if (IdPrefix.fromValue(senderSessionID) == IdPrefix.BLINDED) {
                            val intent = Intent(context, ConversationActivityV2::class.java)
                            intent.putExtra(ConversationActivityV2.FROM_GROUP_THREAD_ID, threadID)
                            intent.putExtra(ConversationActivityV2.ADDRESS, Address.fromSerialized(senderSessionID))
                            context.startActivity(intent)
                        }
                    } else {
                        maybeShowUserDetails(senderSessionID, threadID)
                    }
                }
                if (thread.isOpenGroupRecipient) {
                    val openGroup = lokiThreadDb.getOpenGroupChat(threadID) ?: return
                    var standardPublicKey = ""
                    var blindedPublicKey: String? = null
                    if (IdPrefix.fromValue(senderSessionID) == IdPrefix.BLINDED) {
                        blindedPublicKey = senderSessionID
                    } else {
                        standardPublicKey = senderSessionID
                    }
                    val isModerator = OpenGroupManager.isUserModerator(context, openGroup.groupId, standardPublicKey, blindedPublicKey)
                    binding.moderatorIconImageView.isVisible = !message.isOutgoing && isModerator
                }
            }
        }
        binding.senderNameTextView.isVisible = !message.isOutgoing && (isStartOfMessageCluster && (isGroupThread || snIsSelected))
        val contactContext =
            if (thread.isOpenGroupRecipient) ContactContext.OPEN_GROUP else ContactContext.REGULAR
        binding.senderNameTextView.text = contact?.displayName(contactContext) ?: senderSessionID
        // Date break
        val showDateBreak = isStartOfMessageCluster || snIsSelected
        binding.dateBreakTextView.text = if (showDateBreak) DateUtils.getDisplayFormattedTimeSpanString(context, Locale.getDefault(), message.timestamp) else null
        binding.dateBreakTextView.isVisible = showDateBreak
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
            binding.messageStatusImageView.isVisible =
                !message.isSent || message.id == lastMessageID
        } else {
            binding.messageStatusImageView.isVisible = false
        }
        // Expiration timer
        updateExpirationTimer(message)
        // Emoji Reactions
        val emojiLayoutParams = binding.emojiReactionsView.layoutParams as ConstraintLayout.LayoutParams
        emojiLayoutParams.horizontalBias = if (message.isOutgoing) 1f else 0f
        binding.emojiReactionsView.layoutParams = emojiLayoutParams
        val capabilities = lokiThreadDb.getOpenGroupChat(threadID)?.server?.let { lokiApiDb.getServerCapabilities(it) }
        if (message.reactions.isNotEmpty() &&
            (capabilities.isNullOrEmpty() || capabilities.contains(OpenGroupApi.Capability.REACTIONS.name.lowercase()))
        ) {
            binding.emojiReactionsView.setReactions(message.id, message.reactions, message.isOutgoing, delegate)
            binding.emojiReactionsView.isVisible = true
        } else {
            binding.emojiReactionsView.isVisible = false
        }

        // Populate content view
        binding.messageContentView.indexInAdapter = indexInAdapter
        binding.messageContentView.bind(
            message,
            isStartOfMessageCluster,
            isEndOfMessageCluster,
            glide,
            thread,
            searchQuery,
            message.isOutgoing || isGroupThread || (contact?.isTrusted ?: false)
        )
        binding.messageContentView.delegate = delegate
        onDoubleTap = { binding.messageContentView.onContentDoubleTap?.invoke() }
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
        val container = binding.messageInnerContainer
        val content = binding.messageContentView
        val expiration = binding.expirationTimerView
        val spacing = binding.messageContentSpacing
        container.removeAllViewsInLayout()
        container.addView(if (message.isOutgoing) expiration else content)
        container.addView(if (message.isOutgoing) content else expiration)
        container.addView(spacing, if (message.isOutgoing) 0 else 2)
        val containerParams = container.layoutParams as ConstraintLayout.LayoutParams
        containerParams.horizontalBias = if (message.isOutgoing) 1f else 0f
        container.layoutParams = containerParams
        if (message.expiresIn > 0 && !message.isPending) {
            binding.expirationTimerView.setColorFilter(context.getColorFromAttr(android.R.attr.textColorPrimary))
            binding.expirationTimerView.isInvisible = false
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
            binding.expirationTimerView.isInvisible = true
        }
        container.requestLayout()
    }

    private fun handleIsSelectedChanged() {
        background = if (snIsSelected) {
            ColorDrawable(context.getColorFromAttr(R.attr.message_selected))
        } else {
            null
        }
    }

    override fun onDraw(canvas: Canvas) {
        val spacing = context.resources.getDimensionPixelSize(R.dimen.small_spacing)
        val iconSize = toPx(24, context.resources)
        val left = binding.messageInnerContainer.left + binding.messageContentView.right + spacing
        val top = height - (binding.messageInnerContainer.height / 2) - binding.profilePictureView.root.marginBottom - (iconSize / 2)
        val right = left + iconSize
        val bottom = top + iconSize
        swipeToReplyIconRect.left = left
        swipeToReplyIconRect.top = top
        swipeToReplyIconRect.right = right
        swipeToReplyIconRect.bottom = bottom

        if (translationX < 0 && !binding.expirationTimerView.isVisible) {
            val threshold = swipeToReplyThreshold
            swipeToReplyIcon.bounds = swipeToReplyIconRect
            swipeToReplyIcon.alpha = (255.0f * (min(abs(translationX), threshold) / threshold)).roundToInt()
        } else {
            swipeToReplyIcon.alpha = 0
        }
        swipeToReplyIcon.draw(canvas)
        super.onDraw(canvas)
    }

    fun recycle() {
        binding.profilePictureView.root.recycle()
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
                gestureHandler.postDelayed(newPressCallback, maxDoubleTapInterval)
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

    private fun maybeShowUserDetails(publicKey: String, threadID: Long) {
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
