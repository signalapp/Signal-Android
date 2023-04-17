package org.thoughtcrime.securesms.components.webrtc

import android.content.Context
import android.content.ContextWrapper
import android.content.DialogInterface
import android.media.AudioDeviceInfo
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View.OnClickListener
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatImageView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.webrtc.audio.AudioDeviceMapping
import org.thoughtcrime.securesms.webrtc.audio.SignalAudioManager
import kotlin.math.min

/**
 * A UI button that triggers a picker dialog/bottom sheet allowing the user to select the audio output for the ongoing call.
 */
class WebRtcAudioOutputToggleButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : AppCompatImageView(context, attrs, defStyleAttr) {
  private val TAG = Log.tag(WebRtcAudioOutputToggleButton::class.java)

  private var outputState: OutputState = OutputState()

  private var audioOutputChangedListenerLegacy: OnAudioOutputChangedListener? = null
  private var audioOutputChangedListener31: OnAudioOutputChangedListener31? = null
  private var picker: DialogInterface? = null

  private val clickListenerLegacy: OnClickListener = OnClickListener {
    val outputs = outputState.getOutputs()
    if (outputs.size >= SHOW_PICKER_THRESHOLD || !outputState.isEarpieceAvailable) {
      showPickerLegacy(outputs)
    } else {
      setAudioOutput(outputState.peekNext(), true)
    }
  }

  @RequiresApi(31)
  private val clickListener31 = OnClickListener {
    val fragmentActivity = context.fragmentActivity()
    if (fragmentActivity != null) {
      showPicker31(fragmentActivity.supportFragmentManager)
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
    val extra = when (currentOutput) {
      WebRtcAudioOutput.HANDSET -> intArrayOf(R.attr.state_handset_selected)
      WebRtcAudioOutput.SPEAKER -> intArrayOf(R.attr.state_speaker_selected)
      WebRtcAudioOutput.BLUETOOTH_HEADSET -> intArrayOf(R.attr.state_bt_headset_selected)
      WebRtcAudioOutput.WIRED_HEADSET -> intArrayOf(R.attr.state_wired_headset_selected)
    }

    val label = context.getString(currentOutput.labelRes)
    Log.i(TAG, "Switching to $label")

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
  }

  fun setAudioOutput(audioOutput: WebRtcAudioOutput, notifyListener: Boolean) {
    val oldOutput = outputState.getCurrentOutput()
    if (oldOutput != audioOutput) {
      outputState.setCurrentOutput(audioOutput)
      refreshDrawableState()
      if (notifyListener) {
        audioOutputChangedListenerLegacy?.audioOutputChanged(audioOutput)
      }
    }
  }

  fun setOnAudioOutputChangedListenerLegacy(listener: OnAudioOutputChangedListener?) {
    audioOutputChangedListenerLegacy = listener
  }

  @RequiresApi(31)
  fun setOnAudioOutputChangedListener31(listener: OnAudioOutputChangedListener31?) {
    audioOutputChangedListener31 = listener
  }

  private fun showPickerLegacy(availableModes: List<WebRtcAudioOutput?>) {
    val rv = RecyclerView(context)
    val adapter = AudioOutputAdapter(
      { audioOutput: WebRtcAudioOutput ->
        setAudioOutput(audioOutput, true)
        hidePicker()
      },
      availableModes
    )
    adapter.setSelectedOutput(outputState.getCurrentOutput())
    rv.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
    rv.adapter = adapter
    picker = MaterialAlertDialogBuilder(context)
      .setTitle(R.string.WebRtcAudioOutputToggle__audio_output)
      .setView(rv)
      .setCancelable(true)
      .show()
  }

  @RequiresApi(31)
  private fun showPicker31(fragmentManager: FragmentManager) {
    val am = ApplicationDependencies.getAndroidCallAudioManager()
    if (am.availableCommunicationDevices.isEmpty()) {
      Toast.makeText(context, R.string.WebRtcAudioOutputToggleButton_no_eligible_audio_i_o_detected, Toast.LENGTH_LONG).show()
      return
    }

    val devices: List<AudioOutputOption> = am.availableCommunicationDevices.map { AudioOutputOption(it.toFriendlyName(context).toString(), AudioDeviceMapping.fromPlatformType(it.type), it.id) }
    picker = WebRtcAudioOutputBottomSheet.show(fragmentManager, devices, am.communicationDevice?.id ?: -1) {
      audioOutputChangedListener31?.audioOutputChanged(it.deviceId)

      when (it.deviceType) {
        SignalAudioManager.AudioDevice.WIRED_HEADSET -> {
          outputState.isWiredHeadsetAvailable = true
          setAudioOutput(WebRtcAudioOutput.WIRED_HEADSET, true)
        }

        SignalAudioManager.AudioDevice.EARPIECE -> {
          outputState.isEarpieceAvailable = true
          setAudioOutput(WebRtcAudioOutput.HANDSET, true)
        }

        SignalAudioManager.AudioDevice.BLUETOOTH -> {
          outputState.isBluetoothHeadsetAvailable = true
          setAudioOutput(WebRtcAudioOutput.BLUETOOTH_HEADSET, true)
        }

        SignalAudioManager.AudioDevice.SPEAKER_PHONE, SignalAudioManager.AudioDevice.NONE -> setAudioOutput(WebRtcAudioOutput.SPEAKER, true)
      }
    }
  }

  @RequiresApi(23)
  private fun AudioDeviceInfo.toFriendlyName(context: Context): CharSequence {
    return when (this.type) {
      AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> context.getString(R.string.WebRtcAudioOutputToggle__phone_earpiece)
      AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> context.getString(R.string.WebRtcAudioOutputToggle__speaker)
      AudioDeviceInfo.TYPE_WIRED_HEADSET -> context.getString(R.string.WebRtcAudioOutputToggle__wired_headset)
      AudioDeviceInfo.TYPE_USB_HEADSET -> context.getString(R.string.WebRtcAudioOutputToggle__wired_headset_usb)
      else -> this.productName
    }
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

  private fun hidePicker() {
    try {
      picker?.dismiss()
    } catch (e: IllegalStateException) {
      Log.w(TAG, "Picker is not attached to a window.")
    }

    picker = null
  }

  inner class OutputState {
    private val availableOutputs: LinkedHashSet<WebRtcAudioOutput> = linkedSetOf(WebRtcAudioOutput.SPEAKER)
    private var selectedDevice = 0
      set(value) {
        if (value >= availableOutputs.size) {
          throw IndexOutOfBoundsException("Index: $value, size: ${availableOutputs.size}")
        }
        field = value
      }

    @Deprecated("Used only for onSaveInstanceState.")
    fun getBackingIndexForBackup(): Int {
      return selectedDevice
    }

    @Deprecated("Used only for onRestoreInstanceState.")
    fun setBackingIndexForRestore(index: Int) {
      selectedDevice = 0
    }

    fun getCurrentOutput(): WebRtcAudioOutput {
      return getOutputs()[selectedDevice]
    }

    fun setCurrentOutput(outputType: WebRtcAudioOutput): Boolean {
      val newIndex = getOutputs().indexOf(outputType)
      return if (newIndex < 0) {
        false
      } else {
        selectedDevice = newIndex
        true
      }
    }

    fun getOutputs(): List<WebRtcAudioOutput> {
      return availableOutputs.toList()
    }

    fun peekNext(): WebRtcAudioOutput {
      val peekIndex = (selectedDevice + 1) % availableOutputs.size
      return getOutputs()[peekIndex]
    }

    var isEarpieceAvailable: Boolean
      get() = availableOutputs.contains(WebRtcAudioOutput.HANDSET)
      set(value) {
        if (value) {
          availableOutputs.add(WebRtcAudioOutput.HANDSET)
        } else {
          availableOutputs.remove(WebRtcAudioOutput.HANDSET)
          selectedDevice = min(selectedDevice, availableOutputs.size - 1)
        }
      }

    var isBluetoothHeadsetAvailable: Boolean
      get() = availableOutputs.contains(WebRtcAudioOutput.BLUETOOTH_HEADSET)
      set(value) {
        if (value) {
          availableOutputs.add(WebRtcAudioOutput.BLUETOOTH_HEADSET)
        } else {
          availableOutputs.remove(WebRtcAudioOutput.BLUETOOTH_HEADSET)
          selectedDevice = min(selectedDevice, availableOutputs.size - 1)
        }
      }
    var isWiredHeadsetAvailable: Boolean
      get() = availableOutputs.contains(WebRtcAudioOutput.WIRED_HEADSET)
      set(value) {
        if (value) {
          availableOutputs.add(WebRtcAudioOutput.WIRED_HEADSET)
        } else {
          availableOutputs.remove(WebRtcAudioOutput.WIRED_HEADSET)
          selectedDevice = min(selectedDevice, availableOutputs.size - 1)
        }
      }
  }

  companion object {
    private const val SHOW_PICKER_THRESHOLD = 3
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
