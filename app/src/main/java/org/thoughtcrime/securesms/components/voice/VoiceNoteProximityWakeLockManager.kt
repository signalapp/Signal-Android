package org.thoughtcrime.securesms.components.voice

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.PowerManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import org.signal.core.util.ServiceUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.webrtc.audio.AudioManagerCompat
import java.util.concurrent.TimeUnit

private val TAG = Log.tag(VoiceNoteProximityWakeLockManager::class.java)
private const val PROXIMITY_THRESHOLD = 5f

/**
 * Manages the WakeLock while a VoiceNote is playing back in the target activity.
 */
class VoiceNoteProximityWakeLockManager(
  private val activity: FragmentActivity,
  private val mediaController: MediaController
) : DefaultLifecycleObserver {

  private val wakeLock: PowerManager.WakeLock? = ServiceUtil.getPowerManager(activity.applicationContext).newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, TAG)

  private val audioManager: AudioManagerCompat = AppDependencies.androidCallAudioManager
  private val sensorManager: SensorManager = ServiceUtil.getSensorManager(activity)
  private val proximitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

  private val mediaControllerCallback = ProximityListener()
  private val hardwareSensorEventListener = HardwareSensorEventListener()
  private val audioDeviceCallback = object : AudioDeviceCallback() {
    override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = updateProximitySensor()
    override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = updateProximitySensor()
  }

  private var deviceCallbackRegistered = false
  private var sensorRegistered = false

  init {
    if (proximitySensor != null) {
      activity.lifecycle.addObserver(this)
    }
  }

  override fun onResume(owner: LifecycleOwner) {
    if (proximitySensor != null) {
      mediaController.addListener(mediaControllerCallback)
      setMonitoringPlayback(isPlaybackActive())
    }
  }

  override fun onPause(owner: LifecycleOwner) {
    if (proximitySensor != null) {
      unregisterCallbacksAndRelease()
    }
  }

  fun unregisterCallbacksAndRelease() {
    mediaController.removeListener(mediaControllerCallback)
    setMonitoringPlayback(false)
  }

  fun unregisterFromLifecycle() {
    if (proximitySensor != null) {
      activity.lifecycle.removeObserver(this)
    }
  }

  private fun isActivityResumed() = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

  private fun isPlaybackActive(): Boolean {
    return mediaController.playWhenReady &&
      mediaController.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
      (mediaController.playbackState == Player.STATE_READY || mediaController.playbackState == Player.STATE_BUFFERING)
  }

  private fun setMonitoringPlayback(active: Boolean) {
    if (active != deviceCallbackRegistered) {
      deviceCallbackRegistered = active
      if (active) {
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(activity.mainLooper))
      } else {
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
      }
    }

    updateProximitySensor()
  }

  private fun updateProximitySensor() {
    val shouldRegister = deviceCallbackRegistered && !audioManager.isHeadsetConnected
    if (sensorRegistered == shouldRegister) {
      return
    }

    if (shouldRegister) {
      // The system turns the screen back on when the sensor is far. Keep the lock for the next near event.
      wakeLock?.acquire(TimeUnit.MINUTES.toMillis(30))
      sensorRegistered = sensorManager.registerListener(hardwareSensorEventListener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
      if (!sensorRegistered) {
        wakeLock?.release()
      }
      return
    }

    sensorRegistered = false
    sensorManager.unregisterListener(hardwareSensorEventListener)

    if (wakeLock?.isHeld == true) {
      Log.d(TAG, "Stopped monitoring proximity. Releasing wake lock.")
      wakeLock.release()
    }

    sendNewStreamTypeToPlayer(AudioManager.STREAM_MUSIC)
  }

  private fun sendNewStreamTypeToPlayer(newStreamType: Int) {
    val params = Bundle()
    params.putInt(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, newStreamType)
    mediaController.sendCustomCommand(SessionCommand(VoiceNotePlaybackService.ACTION_SET_AUDIO_STREAM, Bundle.EMPTY), params)
  }

  inner class ProximityListener : Player.Listener {
    override fun onEvents(player: Player, events: Player.Events) {
      if (!isActivityResumed()) return
      if (events.containsAny(Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED)) {
        setMonitoringPlayback(isPlaybackActive())
      }
    }
  }

  inner class HardwareSensorEventListener : SensorEventListener {
    override fun onSensorChanged(event: SensorEvent) {
      if (!sensorRegistered ||
        !isActivityResumed() ||
        !isPlaybackActive() ||
        event.sensor.type != Sensor.TYPE_PROXIMITY ||
        audioManager.isHeadsetConnected()
      ) {
        return
      }

      val newStreamType = if (event.values[0] < PROXIMITY_THRESHOLD && event.values[0] != proximitySensor?.maximumRange) {
        AudioManager.STREAM_VOICE_CALL
      } else {
        AudioManager.STREAM_MUSIC
      }

      sendNewStreamTypeToPlayer(newStreamType)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
  }
}
