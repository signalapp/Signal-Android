package org.thoughtcrime.securesms.conversation.v2.messages

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.core.view.isVisible
import kotlinx.android.synthetic.main.view_voice_message.view.*
import network.loki.messenger.R
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class VoiceMessageView : LinearLayout {
    private val snHandler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var mockIsPlaying = false
    private var mockProgress = 0L
        set(value) { field = value; handleProgressChanged() }
    private var mockDuration = 12000L

    // region Lifecycle
    constructor(context: Context) : super(context) { initialize() }
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) { initialize() }
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) { initialize() }

    private fun initialize() {
        LayoutInflater.from(context).inflate(R.layout.view_voice_message, this)
        setOnClickListener { togglePlayBack() }
        voiceMessageViewDurationTextView.text = String.format("%01d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(mockDuration),
            TimeUnit.MILLISECONDS.toSeconds(mockDuration))
    }
    // endregion

    // region Updating
    fun bind(message: MmsMessageRecord, background: Drawable) {
        val audio = message.slideDeck.audioSlide!!
        voiceMessageViewLoader.isVisible = audio.isPendingDownload
        mainVoiceMessageViewContainer.background = background
        mainVoiceMessageViewContainer.outlineProvider = ViewOutlineProvider.BACKGROUND
        mainVoiceMessageViewContainer.clipToOutline = true
    }

    private fun handleProgressChanged() {
        voiceMessageViewDurationTextView.text = String.format("%01d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(mockDuration - mockProgress),
            TimeUnit.MILLISECONDS.toSeconds(mockDuration - mockProgress))
        val layoutParams = progressView.layoutParams as RelativeLayout.LayoutParams
        val fraction = mockProgress.toFloat() / mockDuration.toFloat()
        layoutParams.width = (width.toFloat() * fraction).roundToInt()
        progressView.layoutParams = layoutParams
    }

    fun recycle() {
        // TODO: Implement
    }
    // endregion

    // region Interaction
    private fun togglePlayBack() {
        mockIsPlaying = !mockIsPlaying
        val iconID = if (mockIsPlaying) R.drawable.exo_icon_pause else R.drawable.exo_icon_play
        voiceMessagePlaybackImageView.setImageResource(iconID)
        if (mockIsPlaying) {
            updateProgress()
        } else {
            runnable?.let { snHandler.removeCallbacks(it) }
        }
    }

    private fun updateProgress() {
        mockProgress += 20L
        val runnable = Runnable { updateProgress() }
        this.runnable = runnable
        snHandler.postDelayed(runnable, 20L)
    }
    // endregion
}