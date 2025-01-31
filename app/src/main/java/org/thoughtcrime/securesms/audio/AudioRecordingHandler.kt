package org.thoughtcrime.securesms.audio

interface AudioRecordingHandler {
  fun onRecordPressed()
  fun onRecordReleased()
  fun onRecordCanceled(byUser: Boolean)
  fun onRecordLocked()
  fun onRecordSaved()
  fun onRecordMoved(offsetX: Float, absoluteX: Float)
  fun onRecordPermissionRequired()
}
