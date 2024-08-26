package org.thoughtcrime.securesms.components.webrtc

import android.content.Context
import android.content.ContextWrapper
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View.OnClickListener
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatImageView
import androidx.fragment.app.FragmentActivity
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R

/**
 * A UI button that triggers a picker dialog/bottom sheet allowing the user to select the audio output for the ongoing call.
 */
class WebRtcAudioOutputToggleButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : AppCompatImageView(context, attrs, defStyleAttr), AudioStateUpdater {
  private val TAG = Log.tag(WebRtcAudioOutputToggleButton::class.java)

  private var outputState: ToggleButtonOutputState = ToggleButtonOutputState()

  private var audioOutputChangedListener: OnAudioOutputChangedListener = OnAudioOutputChangedListener { Log.e(TAG, "Attempted to call audioOutputChangedListenerLegacy without initializing!") }
  private var picker: DialogInterface? = null

  private val clickListenerLegacy: OnClickListener = OnClickListener {
    if (picker != null) {
      Log.d(TAG, "Tried to launch new audio device picker but one is already present.")
      return@OnClickListener
    }

    val outputs = outputState.getOutputs()
    if (outputs.size >= SHOW_PICKER_THRESHOLD || !outputState.isEarpieceAvailable) {
      picker = WebRtcAudioPickerLegacy(audioOutputChangedListener, outputState, this).showPicker(context, outputs) { picker = null }
    } else {
      val audioOutput = outputState.peekNext()
      audioOutputChangedListener.audioOutputChanged(WebRtcAudioDevice(audioOutput, null))
      updateAudioOutputState(audioOutput)
    }
  }

  @RequiresApi(31)
  private val clickListener31 = OnClickListener {
    if (picker != null) {
      Log.d(TAG, "Tried to launch new audio device picker but one is already present.")
      return@OnClickListener
    }

    val fragmentActivity = context.fragmentActivity()
    if (fragmentActivity != null) {
      picker = WebRtcAudioPicker31(audioOutputChangedListener, outputState, this).showPicker(fragmentActivity, SHOW_PICKER_THRESHOLD) { picker = null }
    } else {
      Log.e(TAG, "WebRtcAudioOutputToggleButton instantiated from a context that does not inherit from FragmentActivity.")
      Toast.makeText(context, R.string.WebRtcAudioOutputToggleButton_fragment_activity_error, Toast.LENGTH_LONG).show()
    }
  }

  init {
    super.setOnClickListener(
      if (Build.VERSION.SDK_INT >= 31) {
        clickListener31
      } else {
        clickListenerLegacy
      }
    )
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    hidePicker()
  }

  /**
   * DO NOT REMOVE senseless comparison suppression.
   * Somehow, through XML inflation (reflection?), [outputState] can actually be null,
   * even though the compiler disagrees.
   * */
  override fun onCreateDrawableState(extraSpace: Int): IntArray {
    @Suppress("SENSELESS_COMPARISON")
    if (outputState == null) {
      return super.onCreateDrawableState(extraSpace)
    }

    val currentOutput = outputState.getCurrentOutput()

    val shouldShowDropdownForSpeaker = outputState.getOutputs().size >= SHOW_PICKER_THRESHOLD || !outputState.getOutputs().contains(WebRtcAudioOutput.HANDSET)
    val extra = when (currentOutput) {
      WebRtcAudioOutput.HANDSET -> intArrayOf(R.attr.state_speaker_off)
      WebRtcAudioOutput.SPEAKER -> if (shouldShowDropdownForSpeaker) intArrayOf(R.attr.state_speaker_selected) else intArrayOf(R.attr.state_speaker_on)
      WebRtcAudioOutput.BLUETOOTH_HEADSET -> intArrayOf(R.attr.state_bt_headset_selected)
      WebRtcAudioOutput.WIRED_HEADSET -> intArrayOf(R.attr.state_wired_headset_selected)
    }

    Log.d(TAG, "Switching button drawable to $currentOutput")

    val drawableState = super.onCreateDrawableState(extraSpace + extra.size)
    mergeDrawableStates(drawableState, extra)
    return drawableState
  }

  override fun setOnClickListener(l: OnClickListener?) {
    throw UnsupportedOperationException("This View does not support custom click listeners.")
  }

  fun setControlAvailability(isEarpieceAvailable: Boolean, isBluetoothHeadsetAvailable: Boolean, isHeadsetAvailable: Boolean) {
    outputState.isEarpieceAvailable = isEarpieceAvailable
    outputState.isBluetoothHeadsetAvailable = isBluetoothHeadsetAvailable
    outputState.isWiredHeadsetAvailable = isHeadsetAvailable
    refreshDrawableState()
  }

  override fun updateAudioOutputState(audioOutput: WebRtcAudioOutput) {
    val oldOutput = outputState.getCurrentOutput()
    if (oldOutput != audioOutput) {
      outputState.setCurrentOutput(audioOutput)
      refreshDrawableState()
    }
  }

  fun setOnAudioOutputChangedListener(listener: OnAudioOutputChangedListener) {
    audioOutputChangedListener = listener
  }

  override fun onSaveInstanceState(): Parcelable {
    val parentState = super.onSaveInstanceState()
    val bundle = Bundle()
    bundle.putParcelable(STATE_PARENT, parentState)
    bundle.putInt(STATE_OUTPUT_INDEX, outputState.getBackingIndexForBackup())
    bundle.putBoolean(STATE_HEADSET_ENABLED, outputState.isBluetoothHeadsetAvailable)
    bundle.putBoolean(STATE_HANDSET_ENABLED, outputState.isEarpieceAvailable)
    return bundle
  }

  override fun onRestoreInstanceState(state: Parcelable) {
    if (state is Bundle) {
      outputState.isBluetoothHeadsetAvailable = state.getBoolean(STATE_HEADSET_ENABLED)
      outputState.isEarpieceAvailable = state.getBoolean(STATE_HANDSET_ENABLED)
      outputState.setBackingIndexForRestore(state.getInt(STATE_OUTPUT_INDEX))
      refreshDrawableState()
      super.onRestoreInstanceState(state.getParcelable(STATE_PARENT))
    } else {
      super.onRestoreInstanceState(state)
    }
  }

  override fun hidePicker() {
    try {
      picker?.dismiss()
    } catch (e: IllegalStateException) {
      Log.w(TAG, "Picker is not attached to a window.")
    }

    picker = null
  }

  companion object {
    const val SHOW_PICKER_THRESHOLD = 3

    private const val STATE_OUTPUT_INDEX = "audio.output.toggle.state.output.index"
    private const val STATE_HEADSET_ENABLED = "audio.output.toggle.state.headset.enabled"
    private const val STATE_HANDSET_ENABLED = "audio.output.toggle.state.handset.enabled"
    private const val STATE_PARENT = "audio.output.toggle.state.parent"

    private tailrec fun Context.fragmentActivity(): FragmentActivity? = when (this) {
      is FragmentActivity -> this
      else -> (this as? ContextWrapper)?.baseContext?.fragmentActivity()
    }
  }
}
