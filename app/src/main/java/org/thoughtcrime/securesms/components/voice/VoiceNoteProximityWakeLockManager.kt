package org.thoughtcrime.securesms.components.voice

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Bundle
import android.os.PowerManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.util.ServiceUtil
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

  private val sensorManager: SensorManager = ServiceUtil.getSensorManager(activity)
  private val proximitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

  private val mediaControllerCallback = ProximityListener()
  private val hardwareSensorEventListener = HardwareSensorEventListener()

  private var startTime: Long = -1

  init {
    if (proximitySensor != null) {
      activity.lifecycle.addObserver(this)
    }
  }

  override fun onResume(owner: LifecycleOwner) {
    if (proximitySensor != null) {
      mediaController.addListener(mediaControllerCallback)
    }
  }

  override fun onPause(owner: LifecycleOwner) {
    if (proximitySensor != null) {
      unregisterCallbacksAndRelease()
    }
  }

  fun unregisterCallbacksAndRelease() {
    mediaController.addListener(mediaControllerCallback)
    cleanUpWakeLock()
  }

  fun unregisterFromLifecycle() {
    if (proximitySensor != null) {
      activity.lifecycle.removeObserver(this)
    }
  }

  private fun isActivityResumed() = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

  private fun cleanUpWakeLock() {
    startTime = -1L
    sensorManager.unregisterListener(hardwareSensorEventListener)

    if (wakeLock?.isHeld == true) {
      Log.d(TAG, "[cleanUpWakeLock] Releasing wake lock.")
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
      super.onEvents(player, events)
      if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
        if (!isActivityResumed()) {
          return
        }

        if (player.isPlaying) {
          if (startTime == -1L) {
            Log.d(TAG, "[onPlaybackStateChanged] Player became active with start time $startTime, registering sensor listener.")
            startTime = System.currentTimeMillis()
            if (wakeLock?.isHeld == false) {
              Log.d(TAG, "[onPlaybackStateChanged] Acquiring wakelock")
              wakeLock.acquire(TimeUnit.MINUTES.toMillis(30))
            }
            sensorManager.registerListener(hardwareSensorEventListener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
          } else {
            Log.d(TAG, "[onPlaybackStateChanged] Player became active without start time, skipping sensor registration")
          }
        } else {
          Log.d(TAG, "[onPlaybackStateChanged] Player became inactive. Cleaning up wake lock.")
          cleanUpWakeLock()
        }
      }
    }
  }

  inner class HardwareSensorEventListener : SensorEventListener {
    override fun onSensorChanged(event: SensorEvent) {
      if (startTime == -1L ||
        System.currentTimeMillis() - startTime <= 500 ||
        !isActivityResumed() ||
        !mediaController.isPlaying ||
        event.sensor.type != Sensor.TYPE_PROXIMITY
      ) {
        return
      }

      val newStreamType = if (event.values[0] < PROXIMITY_THRESHOLD && event.values[0] != proximitySensor?.maximumRange) {
        AudioManager.STREAM_VOICE_CALL
      } else {
        AudioManager.STREAM_MUSIC
      }

      sendNewStreamTypeToPlayer(newStreamType)

      if (newStreamType == AudioManager.STREAM_VOICE_CALL) {
        startTime = System.currentTimeMillis()
      } else {
        if (wakeLock?.isHeld == true) {
          Log.d(TAG, "[onSensorChanged] Releasing wakelock")
          wakeLock.release()
        }
      }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
  }
}
