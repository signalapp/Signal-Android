/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.content.pm.ActivityInfo
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.SingleObserver
import io.reactivex.rxjava3.disposables.Disposable
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.concurrent.addTo
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.audio.AudioRecorder
import org.thoughtcrime.securesms.components.voice.VoiceNoteDraft
import org.thoughtcrime.securesms.conversation.VoiceRecorderWakeLock
import org.thoughtcrime.securesms.util.ServiceUtil

/**
 * Delegate class for VoiceMessage recording.
 */
class VoiceMessageRecordingDelegate(
  private val fragment: Fragment,
  private val audioRecorder: AudioRecorder,
  private val sessionCallback: SessionCallback
) {

  companion object {
    private val TAG = Log.tag(VoiceMessageRecordingDelegate::class.java)
  }

  private val disposables = LifecycleDisposable().apply {
    bindTo(fragment.viewLifecycleOwner)
  }

  private val voiceRecorderWakeLock = VoiceRecorderWakeLock(fragment.requireActivity())

  private var session: Session? = null

  fun onRecorderStarted() {
    beginRecording()
  }

  fun onRecorderLocked() {
    voiceRecorderWakeLock.acquire()
    fragment.requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
  }

  fun onRecorderFinished() {
    voiceRecorderWakeLock.release()
    vibrateAndResetOrientation(20)
    session?.completeRecording()
  }

  fun onRecorderCanceled(byUser: Boolean) {
    voiceRecorderWakeLock.release()
    vibrateAndResetOrientation(50)

    if (byUser) {
      session?.discardRecording()
    } else {
      session?.saveDraft()
    }
  }

  fun onRecordSaveDraft() {
    voiceRecorderWakeLock.release()
    vibrateAndResetOrientation(50)
    session?.saveDraft()
  }

  @Suppress("DEPRECATION")
  private fun vibrateAndResetOrientation(milliseconds: Long) {
    val activity = fragment.activity
    if (activity != null) {
      val vibrator = ServiceUtil.getVibrator(activity)
      vibrator.vibrate(milliseconds)

      activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
  }

  @Suppress("DEPRECATION")
  private fun beginRecording() {
    val vibrator = ServiceUtil.getVibrator(fragment.requireContext())
    vibrator.vibrate(20)

    fragment.requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    fragment.requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

    sessionCallback.onSessionWillBegin()
    session = Session(audioRecorder.startRecording(), sessionCallback).apply {
      addTo(disposables)
    }
  }

  interface SessionCallback {
    fun onSessionWillBegin()
    fun sendVoiceNote(draft: VoiceNoteDraft)
    fun cancelEphemeralVoiceNoteDraft(draft: VoiceNoteDraft)
    fun saveEphemeralVoiceNoteDraft(draft: VoiceNoteDraft)
  }

  private inner class Session(
    observable: Single<VoiceNoteDraft>,
    private val sessionCallback: SessionCallback
  ) : SingleObserver<VoiceNoteDraft>, Disposable {

    private var saveDraft = true
    private var shouldSend = false
    private var disposable = Disposable.empty()

    init {
      observable
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(this)
    }

    override fun onSubscribe(d: Disposable) {
      disposable = d
    }

    override fun onSuccess(draft: VoiceNoteDraft) {
      when {
        shouldSend -> sessionCallback.sendVoiceNote(draft)
        !saveDraft -> sessionCallback.cancelEphemeralVoiceNoteDraft(draft)
        else -> sessionCallback.saveEphemeralVoiceNoteDraft(draft)
      }

      session?.dispose()
      session = null
    }

    override fun onError(e: Throwable) {
      Toast.makeText(fragment.requireContext(), R.string.ConversationActivity_unable_to_record_audio, Toast.LENGTH_LONG).show()
      Log.e(TAG, "Error in RecordingSession.", e)

      session?.dispose()
      session = null
    }

    override fun dispose() = disposable.dispose()

    override fun isDisposed(): Boolean = disposable.isDisposed

    fun completeRecording() {
      shouldSend = true
      audioRecorder.stopRecording()
    }

    fun discardRecording() {
      saveDraft = false
      shouldSend = false
      audioRecorder.discardRecording()
    }

    fun saveDraft() {
      saveDraft = true
      shouldSend = false
      audioRecorder.stopRecording()
    }
  }
}
