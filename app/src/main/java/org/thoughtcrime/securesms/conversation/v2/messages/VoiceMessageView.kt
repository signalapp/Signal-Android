package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.core.view.isVisible
import kotlinx.android.synthetic.main.view_voice_message.view.*
import network.loki.messenger.R
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.audio.AudioSlidePlayer
import org.thoughtcrime.securesms.components.CornerMask
import org.thoughtcrime.securesms.conversation.v2.utilities.MessageBubbleUtilities
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class VoiceMessageView : LinearLayout, AudioSlidePlayer.Listener {
    private val cornerMask by lazy { CornerMask(this) }
    private var isPlaying = false
    set(value) {
        field = value
        renderIcon()
    }
    private var progress = 0.0
    private var duration = 0L
    private var player: AudioSlidePlayer? = null
    private var isPreparing = false

    // region Lifecycle
    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        LayoutInflater.from(context).inflate(R.layout.view_voice_message, this)
        voiceMessageViewDurationTextView.text = String.format("%01d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(0),
            TimeUnit.MILLISECONDS.toSeconds(0))
    }
    // endregion

    // region Updating
    fun bind(message: MmsMessageRecord, isStartOfMessageCluster: Boolean, isEndOfMessageCluster: Boolean) {
        val audio = message.slideDeck.audioSlide!!
        voiceMessageViewLoader.isVisible = audio.isPendingDownload
        val cornerRadii = MessageBubbleUtilities.calculateRadii(context, isStartOfMessageCluster, isEndOfMessageCluster, message.isOutgoing)
        cornerMask.setTopLeftRadius(cornerRadii[0])
        cornerMask.setTopRightRadius(cornerRadii[1])
        cornerMask.setBottomRightRadius(cornerRadii[2])
        cornerMask.setBottomLeftRadius(cornerRadii[3])

        // only process audio if downloaded
        if (audio.isPendingDownload || audio.isInProgress) {
            this.player = null
            return
        }

        val player = AudioSlidePlayer.createFor(context, audio, this)
        this.player = player

        (audio.asAttachment() as? DatabaseAttachment)?.let { attachment ->
            DatabaseFactory.getAttachmentDatabase(context).getAttachmentAudioExtras(attachment.attachmentId)?.let { audioExtras ->
                if (audioExtras.durationMs > 0) {
                    duration = audioExtras.durationMs
                    voiceMessageViewDurationTextView.visibility = View.VISIBLE
                    voiceMessageViewDurationTextView.text = String.format("%01d:%02d",
                            TimeUnit.MILLISECONDS.toMinutes(audioExtras.durationMs),
                            TimeUnit.MILLISECONDS.toSeconds(audioExtras.durationMs))
                }
            }
        }
    }

    override fun onPlayerStart(player: AudioSlidePlayer) {}

    override fun onPlayerProgress(player: AudioSlidePlayer, progress: Double, unused: Long) {
        if (progress == 1.0) {
            togglePlayback()
            handleProgressChanged(0.0)
        } else {
            handleProgressChanged(progress)
        }
    }

    private fun handleProgressChanged(progress: Double) {
        this.progress = progress
        voiceMessageViewDurationTextView.text = String.format("%01d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(duration - (progress * duration.toDouble()).roundToLong()),
            TimeUnit.MILLISECONDS.toSeconds(duration - (progress * duration.toDouble()).roundToLong()))
        val layoutParams = progressView.layoutParams as RelativeLayout.LayoutParams
        layoutParams.width = (width.toFloat() * progress.toFloat()).roundToInt()
        progressView.layoutParams = layoutParams
    }

    override fun onPlayerStop(player: AudioSlidePlayer) {
        Log.d("Loki", "Player stopped")
        isPlaying = false
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        cornerMask.mask(canvas)
    }

    private fun renderIcon() {
        val iconID = if (isPlaying) R.drawable.exo_icon_pause else R.drawable.exo_icon_play
        voiceMessagePlaybackImageView.setImageResource(iconID)
    }

    // endregion

    // region Interaction
    fun togglePlayback() {
        val player = this.player ?: return
        isPlaying = !isPlaying
        if (isPlaying) {
            player.play(progress)
        } else {
            player.stop()
        }
    }

    fun handleDoubleTap() {
        val player = this.player ?: return
        player.playbackSpeed = if (player.playbackSpeed == 1.0f) 1.5f else 1.0f
    }
    // endregion
}
