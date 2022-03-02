package org.thoughtcrime.securesms.stories

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.util.visible

/**
 * Displays loading / error slate in Story viewer.
 */
class StorySlateView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

  companion object {
    private val TAG = Log.tag(StorySlateView::class.java)
  }

  var callback: Callback? = null

  var state: State = State.HIDDEN
    private set

  private var postId: Long = 0L

  init {
    inflate(context, R.layout.stories_slate_view, this)
  }

  private val background: View = findViewById(R.id.background)
  private val loadingSpinner: View = findViewById(R.id.loading_spinner)
  private val errorCircle: View = findViewById(R.id.error_circle)
  private val unavailableText: View = findViewById(R.id.unavailable)
  private val errorText: TextView = findViewById(R.id.error_text)

  fun moveToState(state: State, postId: Long) {
    if (this.state == state && this.postId == postId) {
      return
    }

    if (this.postId != postId) {
      this.postId = postId
      moveToHiddenState()
      callback?.onStateChanged(State.HIDDEN, postId)
    }

    if (this.state.isValidTransitionTo(state)) {
      when (state) {
        State.LOADING -> moveToProgressState(State.LOADING)
        State.ERROR -> moveToErrorState()
        State.RETRY -> moveToProgressState(State.RETRY)
        State.NOT_FOUND -> moveToNotFoundState()
        State.HIDDEN -> moveToHiddenState()
      }

      callback?.onStateChanged(state, postId)
    } else {
      Log.d(TAG, "Invalid state transfer: ${this.state} -> $state")
    }
  }

  private fun moveToProgressState(state: State) {
    this.state = state
    visible = true
    background.visible = true
    loadingSpinner.visible = true
    errorCircle.visible = false
    unavailableText.visible = false
    errorText.visible = false
  }

  private fun moveToErrorState() {
    state = State.ERROR
    visible = true
    background.visible = true
    loadingSpinner.visible = false
    errorCircle.visible = true
    unavailableText.visible = false
    errorText.visible = true

    if (NetworkConstraint.isMet(ApplicationDependencies.getApplication())) {
      errorText.setText(R.string.StorySlateView__couldnt_load_content)
    } else {
      errorText.setText(R.string.StorySlateView__no_internet_connection)
    }
  }

  private fun moveToNotFoundState() {
    state = State.NOT_FOUND
    visible = true
    background.visible = true
    loadingSpinner.visible = false
    errorCircle.visible = false
    unavailableText.visible = true
    errorText.visible = false
  }

  private fun moveToHiddenState() {
    state = State.HIDDEN
    visible = false
  }

  override fun onSaveInstanceState(): Parcelable {
    val rootState = super.onSaveInstanceState()
    return Bundle().apply {
      putParcelable("ROOT", rootState)
      putInt("STATE", state.code)
      putLong("ID", postId)
    }
  }

  override fun onRestoreInstanceState(state: Parcelable?) {
    if (state is Bundle) {
      val rootState: Parcelable? = state.getParcelable("ROOT")
      this.state = State.fromCode(state.getInt("STATE", State.HIDDEN.code))
      this.postId = state.getLong("ID")
      super.onRestoreInstanceState(rootState)
    } else {
      super.onRestoreInstanceState(state)
    }
  }

  init {
    errorCircle.setOnClickListener { moveToState(State.RETRY, postId) }
  }

  interface Callback {
    fun onStateChanged(state: State, postId: Long)
  }

  enum class State(val code: Int) {
    LOADING(0),
    ERROR(1),
    RETRY(2),
    NOT_FOUND(3),
    HIDDEN(4);

    fun isValidTransitionTo(newState: State): Boolean {
      if (newState in listOf(HIDDEN, NOT_FOUND)) {
        return true
      }

      return when (this) {
        LOADING -> newState == ERROR
        ERROR -> newState == RETRY
        RETRY -> newState == ERROR
        HIDDEN -> newState == LOADING
        else -> false
      }
    }

    companion object {
      fun fromCode(code: Int): State {
        return values().firstOrNull {
          it.code == code
        } ?: HIDDEN
      }
    }
  }
}
