package org.thoughtcrime.securesms.util.views

import android.animation.Animator
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.ViewAnimationUtils
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.annotation.VisibleForTesting
import androidx.core.animation.doOnEnd
import androidx.core.content.withStyledAttributes
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.theme.overlay.MaterialThemeOverlay
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.visible
import kotlin.math.max

/**
 * Drop-In replacement for CircularProgressButton that better supports material design.
 */
class CircularProgressMaterialButton @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : FrameLayout(MaterialThemeOverlay.wrap(context, attrs, 0, 0), attrs, 0) {
  init {
    inflate(getContext(), R.layout.circular_progress_material_button, this)
  }

  private var currentState: State = State.BUTTON
  private var requestedState: State = State.BUTTON

  private var animator: Animator? = null

  private val materialButton: MaterialButton = findViewById(R.id.button)
  private val progressIndicator: CircularProgressIndicator = findViewById(R.id.progress_indicator)

  var text: CharSequence?
    get() = materialButton.text
    set(value) {
      materialButton.text = value
    }

  init {
    getContext().withStyledAttributes(attrs, R.styleable.CircularProgressMaterialButton) {
      val label = getString(R.styleable.CircularProgressMaterialButton_circularProgressMaterialButton__label)

      materialButton.text = label
    }
  }

  fun setText(@StringRes resId: Int) {
    materialButton.setText(resId)
  }

  override fun setEnabled(enabled: Boolean) {
    super.setEnabled(enabled)
    materialButton.isEnabled = enabled
    progressIndicator.visible = enabled
  }

  override fun setClickable(clickable: Boolean) {
    super.setClickable(clickable)
    materialButton.isClickable = clickable
    progressIndicator.visible = clickable
  }

  override fun onSaveInstanceState(): Parcelable {
    return Bundle().apply {
      putParcelable(SUPER_STATE, super.onSaveInstanceState())
      putInt(STATE, if (requestedState != currentState) requestedState.code else currentState.code)
    }
  }

  override fun onRestoreInstanceState(state: Parcelable) {
    val stateBundle = state as Bundle
    val superState: Parcelable? = stateBundle.getParcelable(SUPER_STATE)
    super.onRestoreInstanceState(superState)

    currentState = if (materialButton.visibility == INVISIBLE) State.PROGRESS else State.BUTTON
    requestedState = State.fromCode(stateBundle.getInt(STATE))
    ensureRequestedState(false)
  }

  override fun setOnClickListener(onClickListener: OnClickListener?) {
    materialButton.setOnClickListener(onClickListener)
  }

  @VisibleForTesting
  fun getRequestedState(): State {
    return requestedState
  }

  fun setSpinning() {
    transformTo(State.PROGRESS, true)
  }

  fun cancelSpinning() {
    transformTo(State.BUTTON, true)
  }

  private fun transformTo(state: State, animate: Boolean) {
    if (!isAttachedToWindow) {
      return
    }

    if (state == currentState && state == requestedState) {
      return
    }

    requestedState = state
    if (animator?.isRunning == true) {
      return
    }

    if (!animate) {
      materialButton.visibility = state.materialButtonVisibility
      currentState = state
      return
    }

    currentState = state
    if (state == State.BUTTON) {
      materialButton.visibility = VISIBLE
    }

    val buttonShrunkRadius = 0f
    val buttonExpandedRadius = max(measuredWidth, measuredHeight).toFloat()

    animator = ViewAnimationUtils.createCircularReveal(
      materialButton,
      materialButton.measuredWidth / 2,
      materialButton.measuredHeight / 2,
      if (state == State.BUTTON) buttonShrunkRadius else buttonExpandedRadius,
      if (state == State.PROGRESS) buttonShrunkRadius else buttonExpandedRadius
    ).apply {
      duration = ANIMATION_DURATION
      doOnEnd {
        materialButton.visibility = state.materialButtonVisibility
        ensureRequestedState(true)
      }
      start()
    }
  }

  private fun ensureRequestedState(animate: Boolean) {
    if (requestedState == currentState || !isAttachedToWindow) {
      return
    }

    transformTo(requestedState, animate)
  }

  enum class State(val code: Int, val materialButtonVisibility: Int) {
    BUTTON(0, VISIBLE),
    PROGRESS(1, INVISIBLE);

    companion object {
      fun fromCode(code: Int): State {
        return when (code) {
          0 -> BUTTON
          1 -> PROGRESS
          else -> error("Unexpected code $code")
        }
      }
    }
  }

  companion object {
    private val ANIMATION_DURATION = 300L

    private val SUPER_STATE = "super_state"
    private val STATE = "state"
  }
}
