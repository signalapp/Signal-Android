package org.thoughtcrime.securesms.loki.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.AnimatedVectorDrawable
import android.util.AttributeSet
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import network.loki.messenger.R
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentTransferProgress
import org.thoughtcrime.securesms.ApplicationContext
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment
import org.thoughtcrime.securesms.audio.AudioSlidePlayer
import org.thoughtcrime.securesms.components.AnimatingToggle
import org.thoughtcrime.securesms.database.DatabaseFactory
import org.thoughtcrime.securesms.events.PartProgressEvent
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.loki.api.PrepareAttachmentAudioExtrasJob
import org.thoughtcrime.securesms.loki.utilities.getColorWithID
import org.thoughtcrime.securesms.mms.AudioSlide
import org.thoughtcrime.securesms.mms.SlideClickListener
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit

class MessageAudioView: FrameLayout, AudioSlidePlayer.Listener {

    companion object {
        private const val TAG = "AudioViewKt"
    }

    private val controlToggle: AnimatingToggle
    private val container: ViewGroup
    private val playButton: ImageView
    private val pauseButton: ImageView
    private val downloadButton: ImageView
    private val downloadProgress: ProgressBar
    private val seekBar: WaveformSeekBar
    private val totalDuration: TextView

    private var downloadListener: SlideClickListener? = null
    private var audioSlidePlayer: AudioSlidePlayer? = null

    /** Background coroutine scope that is available when the view is attached to a window. */
    private var asyncCoroutineScope: CoroutineScope? = null

    private val loadingAnimation: SeekBarLoadingAnimation

    constructor(context: Context): this(context, null)

    constructor(context: Context, attrs: AttributeSet?): this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): super(context, attrs, defStyleAttr) {
        View.inflate(context, R.layout.message_audio_view, this)
        container = findViewById(R.id.audio_widget_container)
        controlToggle = findViewById(R.id.control_toggle)
        playButton = findViewById(R.id.play)
        pauseButton = findViewById(R.id.pause)
        downloadButton = findViewById(R.id.download)
        downloadProgress = findViewById(R.id.download_progress)
        seekBar = findViewById(R.id.seek)
        totalDuration = findViewById(R.id.total_duration)

        playButton.setOnClickListener {
            try {
                Log.d(TAG, "playbutton onClick")
                if (audioSlidePlayer != null) {
                    togglePlayToPause()

                    // Restart the playback if progress bar is nearly at the end.
                    val progress = if (seekBar.progress < 0.99f) seekBar.progress.toDouble() else 0.0

                    audioSlidePlayer!!.play(progress)
                }
            } catch (e: IOException) {
                Log.w(TAG, e)
            }
        }
        pauseButton.setOnClickListener {
            Log.d(TAG, "pausebutton onClick")
            if (audioSlidePlayer != null) {
                togglePauseToPlay()
                audioSlidePlayer!!.stop()
            }
        }
        seekBar.isEnabled = false
        seekBar.progressChangeListener = object : WaveformSeekBar.ProgressChangeListener {
            override fun onProgressChanged(waveformSeekBar: WaveformSeekBar, progress: Float, fromUser: Boolean) {
                if (fromUser && audioSlidePlayer != null) {
                    synchronized(audioSlidePlayer!!) {
                        audioSlidePlayer!!.seekTo(progress.toDouble())
                    }
                }
            }
        }

        playButton.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.play_icon))
        pauseButton.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.pause_icon))
        playButton.background = ContextCompat.getDrawable(context, R.drawable.ic_circle_fill_white_48dp)
        pauseButton.background = ContextCompat.getDrawable(context, R.drawable.ic_circle_fill_white_48dp)

        if (attrs != null) {
            val typedArray = context.theme.obtainStyledAttributes(attrs, R.styleable.MessageAudioView, 0, 0)
            setTint(typedArray.getColor(R.styleable.MessageAudioView_foregroundTintColor, Color.WHITE),
                    typedArray.getColor(R.styleable.MessageAudioView_waveformFillColor, Color.WHITE),
                    typedArray.getColor(R.styleable.MessageAudioView_waveformBackgroundColor, Color.WHITE))
            container.setBackgroundColor(typedArray.getColor(R.styleable.MessageAudioView_widgetBackground, Color.TRANSPARENT))
            typedArray.recycle()
        }

        loadingAnimation = SeekBarLoadingAnimation(this, seekBar)
        loadingAnimation.start()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)

        asyncCoroutineScope = CoroutineScope(Job() + Dispatchers.IO)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        EventBus.getDefault().unregister(this)

        // Cancel all the background operations.
        asyncCoroutineScope!!.cancel()
        asyncCoroutineScope = null
    }

    fun setAudio(audio: AudioSlide, showControls: Boolean) {
        when {
            showControls && audio.isPendingDownload -> {
                controlToggle.displayQuick(downloadButton)
                seekBar.isEnabled = false
                downloadButton.setOnClickListener { v -> downloadListener?.onClick(v, audio) }
                if (downloadProgress.isIndeterminate) {
                    downloadProgress.isIndeterminate = false
                    downloadProgress.progress = 0
                }
            }
            (showControls && audio.transferState == AttachmentTransferProgress.TRANSFER_PROGRESS_STARTED) -> {
                controlToggle.displayQuick(downloadProgress)
                seekBar.isEnabled = false
                downloadProgress.isIndeterminate = true
            }
            else -> {
                controlToggle.displayQuick(playButton)
                seekBar.isEnabled = true
                if (downloadProgress.isIndeterminate) {
                    downloadProgress.isIndeterminate = false
                    downloadProgress.progress = 100
                }

                // Post to make sure it executes only when the view is attached to a window.
                post(::updateFromAttachmentAudioExtras)
            }
        }
        audioSlidePlayer = AudioSlidePlayer.createFor(context, audio, this)
    }

    fun cleanup() {
        if (audioSlidePlayer != null && pauseButton.visibility == View.VISIBLE) {
            audioSlidePlayer!!.stop()
        }
    }

    fun setDownloadClickListener(listener: SlideClickListener?) {
        downloadListener = listener
    }

    fun setTint(@ColorInt foregroundTint: Int, @ColorInt waveformFill: Int, @ColorInt waveformBackground: Int) {
        playButton.backgroundTintList = ColorStateList.valueOf(resources.getColorWithID(R.color.white, context.theme))
        playButton.imageTintList = ColorStateList.valueOf(resources.getColorWithID(R.color.black, context.theme))
        pauseButton.backgroundTintList = ColorStateList.valueOf(resources.getColorWithID(R.color.white, context.theme))
        pauseButton.imageTintList = ColorStateList.valueOf(resources.getColorWithID(R.color.black, context.theme))

        downloadButton.setColorFilter(foregroundTint, PorterDuff.Mode.SRC_IN)

        downloadProgress.backgroundTintList = ColorStateList.valueOf(resources.getColorWithID(R.color.white, context.theme))
        downloadProgress.progressTintList = ColorStateList.valueOf(resources.getColorWithID(R.color.black, context.theme))
        downloadProgress.indeterminateTintList = ColorStateList.valueOf(resources.getColorWithID(R.color.black, context.theme))

        totalDuration.setTextColor(foregroundTint)

        seekBar.barProgressColor = waveformFill
        seekBar.barBackgroundColor = waveformBackground
    }

    override fun onPlayerStart(player: AudioSlidePlayer) {
        if (pauseButton.visibility != View.VISIBLE) {
            togglePlayToPause()
        }
    }

    override fun onPlayerStop(player: AudioSlidePlayer) {
        if (playButton.visibility != View.VISIBLE) {
            togglePauseToPlay()
        }
    }

    override fun onPlayerProgress(player: AudioSlidePlayer, progress: Double, millis: Long) {
        seekBar.progress = progress.toFloat()
    }

    override fun setFocusable(focusable: Boolean) {
        super.setFocusable(focusable)
        playButton.isFocusable = focusable
        pauseButton.isFocusable = focusable
        seekBar.isFocusable = focusable
        seekBar.isFocusableInTouchMode = focusable
        downloadButton.isFocusable = focusable
    }

    override fun setClickable(clickable: Boolean) {
        super.setClickable(clickable)
        playButton.isClickable = clickable
        pauseButton.isClickable = clickable
        seekBar.isClickable = clickable
        seekBar.setOnTouchListener(if (clickable) null else
            OnTouchListener { _, _ -> return@OnTouchListener true }) // Suppress touch events.
        downloadButton.isClickable = clickable
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        playButton.isEnabled = enabled
        pauseButton.isEnabled = enabled
        downloadButton.isEnabled = enabled
    }

    private fun togglePlayToPause() {
        controlToggle.displayQuick(pauseButton)
        val playToPauseDrawable = ContextCompat.getDrawable(context, R.drawable.play_to_pause_animation) as AnimatedVectorDrawable
        pauseButton.setImageDrawable(playToPauseDrawable)
        playToPauseDrawable.start()
    }

    private fun togglePauseToPlay() {
        controlToggle.displayQuick(playButton)
        val pauseToPlayDrawable = ContextCompat.getDrawable(context, R.drawable.pause_to_play_animation) as AnimatedVectorDrawable
        playButton.setImageDrawable(pauseToPlayDrawable)
        pauseToPlayDrawable.start()
    }

    private fun obtainDatabaseAttachment(): DatabaseAttachment? {
        audioSlidePlayer ?: return null
        val attachment = audioSlidePlayer!!.audioSlide.asAttachment()
        return if (attachment is DatabaseAttachment) attachment else null
    }

    private fun updateFromAttachmentAudioExtras() {
        val attachment = obtainDatabaseAttachment() ?: return

        val audioExtras = DatabaseFactory.getAttachmentDatabase(context)
                .getAttachmentAudioExtras(attachment.attachmentId)

        // Schedule a job request if no audio extras were generated yet.
        if (audioExtras == null) {
            ApplicationContext.getInstance(context).jobManager
                    .add(PrepareAttachmentAudioExtrasJob(attachment.attachmentId))
            return
        }

        loadingAnimation.stop()
        seekBar.sampleData = audioExtras.visualSamples

        if (audioExtras.durationMs > 0) {
            totalDuration.visibility = View.VISIBLE
            totalDuration.text = String.format("%02d:%02d",
                    TimeUnit.MILLISECONDS.toMinutes(audioExtras.durationMs),
                    TimeUnit.MILLISECONDS.toSeconds(audioExtras.durationMs))
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEvent(event: PartProgressEvent) {
        if (audioSlidePlayer != null && event.attachment == audioSlidePlayer!!.audioSlide.asAttachment()) {
            val progress = ((event.progress.toFloat() / event.total) * 100f).toInt()
            downloadProgress.progress = progress
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: PrepareAttachmentAudioExtrasJob.AudioExtrasUpdatedEvent) {
        if (event.attachmentId == obtainDatabaseAttachment()?.attachmentId) {
            updateFromAttachmentAudioExtras()
        }
    }

    private class SeekBarLoadingAnimation(
            private val hostView: View,
            private val seekBar: WaveformSeekBar): Runnable {

        private var active = false

        companion object {
            private const val UPDATE_PERIOD = 250L // In milliseconds.
            private val random = Random()
        }

        fun start() {
            stop()
            active = true
            hostView.postDelayed(this, UPDATE_PERIOD)
        }

        fun stop() {
            active = false
            hostView.removeCallbacks(this)
        }

        override fun run() {
            if (!active) return

            // Generate a random samples with values up to the 50% of the maximum value.
            seekBar.sampleData = ByteArray(PrepareAttachmentAudioExtrasJob.VISUAL_RMS_FRAMES)
                { (random.nextInt(127) - 64).toByte() }
            hostView.postDelayed(this, UPDATE_PERIOD)
        }
    }
}