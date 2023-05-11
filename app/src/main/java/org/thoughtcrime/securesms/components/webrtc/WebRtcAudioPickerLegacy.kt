package org.thoughtcrime.securesms.components.webrtc

import android.content.Context
import android.content.DialogInterface
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.thoughtcrime.securesms.R

/**
 * This launches the bottom sheet on Android 11 and below devices for selecting which audio device to use during a call.
 * In cases where there are only [SHOW_PICKER_THRESHOLD] devices, it will cycle through them without presenting a bottom sheet.
 */
class WebRtcAudioPickerLegacy(private val audioOutputChangedListener: OnAudioOutputChangedListener, private val outputState: ToggleButtonOutputState, private val stateUpdater: AudioStateUpdater) {

  fun showPicker(context: Context, availableModes: List<WebRtcAudioOutput?>, dismissListener: DialogInterface.OnDismissListener): DialogInterface? {
    val rv = RecyclerView(context)
    val adapter = AudioOutputAdapter(
      fun(audioDevice: WebRtcAudioDevice) {
        audioOutputChangedListener.audioOutputChanged(audioDevice)
        stateUpdater.updateAudioOutputState(audioDevice.webRtcAudioOutput)
        stateUpdater.hidePicker()
      },
      availableModes
    )
    adapter.setSelectedOutput(outputState.getCurrentOutput())
    rv.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
    rv.adapter = adapter
    return MaterialAlertDialogBuilder(context)
      .setTitle(R.string.WebRtcAudioOutputToggle__audio_output)
      .setView(rv)
      .setCancelable(true)
      .setOnDismissListener(dismissListener)
      .show()
  }
}
