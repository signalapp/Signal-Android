package org.thoughtcrime.securesms.components

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat.createBlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import com.pnikosis.materialishprogress.ProgressWheel
import network.loki.messenger.R
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.thoughtcrime.securesms.audio.AudioSlidePlayer
import org.thoughtcrime.securesms.database.AttachmentDatabase
import org.thoughtcrime.securesms.events.PartProgressEvent
import org.thoughtcrime.securesms.logging.Log
import org.thoughtcrime.securesms.mms.AudioSlide
import org.thoughtcrime.securesms.mms.SlideClickListener
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.floor

class AudioView: FrameLayout, AudioSlidePlayer.Listener {

    companion object {
        private const val TAG = "AudioViewKt"
    }

    private val controlToggle: AnimatingToggle
    private val container: ViewGroup
    private val playButton: ImageView
    private val pauseButton: ImageView
    private val downloadButton: ImageView
    private val downloadProgress: ProgressWheel
    private val seekBar: WaveformSeekBar
    private val timestamp: TextView

    private var downloadListener: SlideClickListener? = null
    private var audioSlidePlayer: AudioSlidePlayer? = null
    private var backwardsCounter = 0

    constructor(context: Context): this(context, null)

    constructor(context: Context, attrs: AttributeSet?): this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): super(context, attrs, defStyleAttr) {
        View.inflate(context, R.layout.audio_view, this)
        container = findViewById(R.id.audio_widget_container)
        controlToggle = findViewById(R.id.control_toggle)
        playButton = findViewById(R.id.play)
        pauseButton = findViewById(R.id.pause)
        downloadButton = findViewById(R.id.download)
        downloadProgress = findViewById(R.id.download_progress)
        seekBar = findViewById(R.id.seek)
        timestamp = findViewById(R.id.timestamp)

        playButton.setOnClickListener {
            try {
                Log.d(TAG, "playbutton onClick")
                if (audioSlidePlayer != null) {
                    togglePlayToPause()

                    // Restart the playback if progress bar is near at the end.
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
        seekBar.progressChangeListener = object : WaveformSeekBar.ProgressChangeListener {
            override fun onProgressChanged(waveformSeekBar: WaveformSeekBar, progress: Float, fromUser: Boolean) {
                if (fromUser && audioSlidePlayer != null) {
                    synchronized(audioSlidePlayer!!) {
                        audioSlidePlayer!!.seekTo(progress.toDouble())
                    }
                }
            }
        }
        //TODO Remove this.
        seekBar.sample = Random().let { (0 until 64).map { i -> it.nextFloat() }.toFloatArray() }

        playButton.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.play_icon))
        pauseButton.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.pause_icon))
        playButton.background = ContextCompat.getDrawable(context, R.drawable.ic_circle_fill_white_48dp)
        pauseButton.background = ContextCompat.getDrawable(context, R.drawable.ic_circle_fill_white_48dp)

        if (attrs != null) {
            val typedArray = context.theme.obtainStyledAttributes(attrs, R.styleable.AudioView, 0, 0)
            setTint(typedArray.getColor(R.styleable.AudioView_foregroundTintColor, Color.WHITE),
                    typedArray.getColor(R.styleable.AudioView_backgroundTintColor, Color.WHITE))
            container.setBackgroundColor(typedArray.getColor(R.styleable.AudioView_widgetBackground, Color.TRANSPARENT))
            typedArray.recycle()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        EventBus.getDefault().unregister(this)
    }

    fun setAudio(audio: AudioSlide, showControls: Boolean) {
        if (showControls && audio.isPendingDownload) {
            controlToggle.displayQuick(downloadButton)
            seekBar.isEnabled = false
            downloadButton.setOnClickListener { v -> downloadListener?.onClick(v, audio) }
            if (downloadProgress.isSpinning) {
                downloadProgress.stopSpinning()
            }
        } else if (showControls && audio.transferState == AttachmentDatabase.TRANSFER_PROGRESS_STARTED) {
            controlToggle.displayQuick(downloadProgress)
            seekBar.isEnabled = false
            downloadProgress.spin()
        } else {
            controlToggle.displayQuick(playButton)
            seekBar.isEnabled = true
            if (downloadProgress.isSpinning) {
                downloadProgress.stopSpinning()
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

    fun setTint(foregroundTint: Int, backgroundTint: Int) {
        playButton.backgroundTintList = ColorStateList.valueOf(foregroundTint)
        playButton.imageTintList = ColorStateList.valueOf(backgroundTint)
        pauseButton.backgroundTintList = ColorStateList.valueOf(foregroundTint)
        pauseButton.imageTintList = ColorStateList.valueOf(backgroundTint)

        downloadButton.setColorFilter(foregroundTint, PorterDuff.Mode.SRC_IN)
        downloadProgress.barColor = foregroundTint
        timestamp.setTextColor(foregroundTint)

//        val colorFilter = createBlendModeColorFilterCompat(foregroundTint, BlendModeCompat.SRC_IN)
//        seekBar.progressDrawable.colorFilter = colorFilter
//        seekBar.thumb.colorFilter = colorFilter
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

//        if (seekBar.progress + 5 >= seekBar.max) {
//            backwardsCounter = 4
//            onProgress(0.0, 0)
//        }
    }

    override fun onPlayerProgress(player: AudioSlidePlayer, progress: Double, millis: Long) {
//        val seekProgress = floor(progress * seekBar.max).toInt()
        //TODO Update text.
        seekBar.progress = progress.toFloat()
//        if (/*seekProgress > 1f || */backwardsCounter > 3) {
//            backwardsCounter = 0
//            seekBar.progress = 1f
//            timestamp.text = String.format("%02d:%02d",
//                    TimeUnit.MILLISECONDS.toMinutes(millis),
//                    TimeUnit.MILLISECONDS.toSeconds(millis))
//        } else {
//            backwardsCounter++
//        }
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
        seekBar.isEnabled = enabled
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

//    private inner class SeekBarModifiedListener : OnSeekBarChangeListener {
//        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}
//
//        @Synchronized
//        override fun onStartTrackingTouch(seekBar: SeekBar) {
//            if (audioSlidePlayer != null && pauseButton.visibility == View.VISIBLE) {
//                audioSlidePlayer!!.stop()
//            }
//        }
//
//        @Synchronized
//        override fun onStopTrackingTouch(seekBar: SeekBar) {
//            try {
//                if (audioSlidePlayer != null && pauseButton.visibility == View.VISIBLE) {
//                    audioSlidePlayer!!.play(getProgress())
//                }
//            } catch (e: IOException) {
//                Log.w(TAG, e)
//            }
//        }
//    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventAsync(event: PartProgressEvent) {
        if (audioSlidePlayer != null && event.attachment == audioSlidePlayer!!.audioSlide.asAttachment()) {
            downloadProgress.setInstantProgress(event.progress.toFloat() / event.total)
        }
    }
}