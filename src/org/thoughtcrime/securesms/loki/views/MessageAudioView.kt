package org.thoughtcrime.securesms.loki.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.AnimatedVectorDrawable
import android.media.MediaDataSource
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.pnikosis.materialishprogress.ProgressWheel
import kotlinx.coroutines.*
import network.loki.messenger.R
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.audio.AudioSlidePlayer
import org.thoughtcrime.securesms.components.AnimatingToggle
import org.thoughtcrime.securesms.database.AttachmentDatabase
import org.thoughtcrime.securesms.events.PartProgressEvent
import org.thoughtcrime.securesms.logging.Log
import org.thoughtcrime.securesms.loki.utilities.audio.DecodedAudio
import org.thoughtcrime.securesms.loki.utilities.audio.calculateRms
import org.thoughtcrime.securesms.mms.AudioSlide
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.mms.SlideClickListener
import java.io.IOException
import java.io.InputStream
import java.lang.Exception
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
    private val downloadProgress: ProgressWheel
    private val seekBar: WaveformSeekBar
    private val totalDuration: TextView

    private var downloadListener: SlideClickListener? = null
    private var audioSlidePlayer: AudioSlidePlayer? = null
//    private var backwardsCounter = 0

    /** Background coroutine scope that is available when the view is attached to a window. */
    private var asyncCoroutineScope: CoroutineScope? = null

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
                    typedArray.getColor(R.styleable.MessageAudioView_backgroundTintColor, Color.WHITE))
            container.setBackgroundColor(typedArray.getColor(R.styleable.MessageAudioView_widgetBackground, Color.TRANSPARENT))
            typedArray.recycle()
        }
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
                if (downloadProgress.isSpinning) {
                    downloadProgress.stopSpinning()
                }
            }
            (showControls && audio.transferState == AttachmentDatabase.TRANSFER_PROGRESS_STARTED) -> {
                controlToggle.displayQuick(downloadProgress)
                seekBar.isEnabled = false
                downloadProgress.spin()
            }
            else -> {
                controlToggle.displayQuick(playButton)
                seekBar.isEnabled = true
                if (downloadProgress.isSpinning) {
                    downloadProgress.stopSpinning()
                }

                // Post to make sure it executes only when the view is attached to a window.
                post(::updateSeekBarFromAudio)
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

    fun setTint(@ColorInt foregroundTint: Int, @ColorInt backgroundTint: Int) {
        playButton.backgroundTintList = ColorStateList.valueOf(foregroundTint)
        playButton.imageTintList = ColorStateList.valueOf(backgroundTint)
        pauseButton.backgroundTintList = ColorStateList.valueOf(foregroundTint)
        pauseButton.imageTintList = ColorStateList.valueOf(backgroundTint)

        downloadButton.setColorFilter(foregroundTint, PorterDuff.Mode.SRC_IN)
        downloadProgress.barColor = foregroundTint
        totalDuration.setTextColor(foregroundTint)

//        val colorFilter = createBlendModeColorFilterCompat(foregroundTint, BlendModeCompat.SRC_IN)
//        seekBar.progressDrawable.colorFilter = colorFilter
//        seekBar.thumb.colorFilter = colorFilter
        seekBar.barProgressColor = foregroundTint
        seekBar.barBackgroundColor = ColorUtils.blendARGB(foregroundTint, backgroundTint, 0.75f)
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

    private fun updateSeekBarFromAudio() {
        if (audioSlidePlayer == null) return

        val attachment = audioSlidePlayer!!.audioSlide.asAttachment()

        // Parse audio and compute RMS values for the WaveformSeekBar in the background.
        asyncCoroutineScope!!.launch {
            val rmsFrames = 32  // The amount of values to be computed for the visualization.

            fun extractAttachmentRandomSeed(attachment: Attachment): Int {
                return when {
                    attachment.digest != null -> attachment.digest!!.sum()
                    attachment.fileName != null -> attachment.fileName.hashCode()
                    else -> attachment.hashCode()
                }
            }

            fun generateFakeRms(seed: Int, frames: Int = rmsFrames): FloatArray {
                return Random(seed.toLong()).let { (0 until frames).map { i -> it.nextFloat() }.toFloatArray() }
            }

            var rmsValues: FloatArray = floatArrayOf()
            var totalDurationMs: Long = -1

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                // Due to API version incompatibility, we just display some random waveform for older API.
                rmsValues = generateFakeRms(extractAttachmentRandomSeed(attachment))
            } else {
                try {
                    @Suppress("BlockingMethodInNonBlockingContext")
                    val decodedAudio = PartAuthority.getAttachmentStream(context, attachment.dataUri!!).use {
                        DecodedAudio(InputStreamMediaDataSource(it))
                    }
                    rmsValues = decodedAudio.calculateRms(rmsFrames)
                    totalDurationMs = (decodedAudio.duration / 1000.0).toLong()
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Failed to decode sample values for the audio attachment \"${attachment.fileName}\".", e)
                    rmsValues = generateFakeRms(extractAttachmentRandomSeed(attachment))
                }
            }

            android.util.Log.d(TAG, "RMS: ${rmsValues.joinToString()}")

            post {
                seekBar.sampleData = rmsValues

                if (totalDurationMs > 0) {
                    totalDuration.visibility = View.VISIBLE
                    totalDuration.text = String.format("%02d:%02d",
                            TimeUnit.MILLISECONDS.toMinutes(totalDurationMs),
                            TimeUnit.MILLISECONDS.toSeconds(totalDurationMs))
                }
            }
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventAsync(event: PartProgressEvent) {
        if (audioSlidePlayer != null && event.attachment == audioSlidePlayer!!.audioSlide.asAttachment()) {
            downloadProgress.setInstantProgress(event.progress.toFloat() / event.total)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.M)
private class InputStreamMediaDataSource: MediaDataSource {

    private val data: ByteArray

    constructor(inputStream: InputStream): super() {
        this.data = inputStream.readBytes()
    }

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        val length: Int = data.size
        if (position >= length) {
            return -1 // -1 indicates EOF
        }
        var actualSize = size
        if (position + size > length) {
            actualSize -= (position + size - length).toInt()
        }
        System.arraycopy(data, position.toInt(), buffer, offset, actualSize)
        return actualSize
    }

    override fun getSize(): Long {
        return data.size.toLong()
    }

    override fun close() {
        // We don't need to close the wrapped stream.
    }
}