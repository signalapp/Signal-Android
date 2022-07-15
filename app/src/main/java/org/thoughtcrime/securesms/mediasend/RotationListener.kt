package org.thoughtcrime.securesms.mediasend

import android.content.Context
import android.view.OrientationEventListener
import android.view.Surface
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.Subject

/**
 * Utilizes the OrientationEventListener to determine relative surface rotation.
 *
 * @param context A context, which will be held on to for the lifespan of the listener.
 */
class RotationListener(
  context: Context
) : OrientationEventListener(context) {

  private val subject: Subject<Rotation> = BehaviorSubject.create()

  /**
   * Observes the stream of orientation changes. This can emit a lot of data, as it does
   * not perform any duplication.
   */
  val observable = subject
    .doOnSubscribe { enable() }
    .doOnTerminate { disable() }

  override fun onOrientationChanged(orientation: Int) {
    subject.onNext(
      when {
        orientation == ORIENTATION_UNKNOWN -> Rotation.ROTATION_0
        orientation > 315 || orientation < 45 -> Rotation.ROTATION_0
        orientation < 135 -> Rotation.ROTATION_270
        orientation < 225 -> Rotation.ROTATION_180
        else -> Rotation.ROTATION_90
      }
    )
  }

  /**
   * Expresses the rotation as a handy enum.
   */
  enum class Rotation(val surfaceRotation: Int) {
    ROTATION_0(Surface.ROTATION_0),
    ROTATION_90(Surface.ROTATION_90),
    ROTATION_180(Surface.ROTATION_180),
    ROTATION_270(Surface.ROTATION_270)
  }
}
