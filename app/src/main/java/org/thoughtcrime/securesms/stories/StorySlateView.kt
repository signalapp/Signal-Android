package org.thoughtcrime.securesms.stories

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import org.signal.core.util.getParcelableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.blurhash.BlurHash
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.visible

/**
 * Displays loading / error slate in Story viewer.
 */
class StorySlateView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

  var callback: Callback? = null

  var state: State = State.HIDDEN
    private set

  private var postId: Long = 0L

  init {
    inflate(context, R.layout.stories_slate_view, this)
  }

  private val background: ImageView = findViewById(R.id.background)
  private val loadingSpinner: View = findViewById(R.id.loading_spinner)
  private val errorCircle: View = findViewById(R.id.error_circle)
  private val errorBackground: View = findViewById(R.id.stories_error_background)
  private val unavailableText: TextView = findViewById(R.id.unavailable)
  private val errorText: TextView = findViewById(R.id.error_text)

  fun moveToState(state: State, postId: Long, sender: Recipient? = null) {
    if (this.state == state && this.postId == postId) {
      return
    }

    if (this.postId != postId) {
      this.postId = postId
      moveToHiddenState()
      callback?.onStateChanged(State.HIDDEN, postId)
    }

    when (state) {
      State.LOADING -> moveToProgressState(State.LOADING)
      State.ERROR -> moveToErrorState()
      State.RETRY -> moveToProgressState(State.RETRY)
      State.NOT_FOUND, State.FAILED -> moveToNotFoundState(state, sender)
      State.HIDDEN -> moveToHiddenState()
    }

    callback?.onStateChanged(state, postId)
  }

  fun setBackground(blur: BlurHash?) {
    if (blur != null) {
      Glide.with(background)
        .load(blur)
        .into(background)
    } else {
      Glide.with(background).clear(background)
    }
  }

  private fun moveToProgressState(state: State) {
    this.state = state
    visible = true
    background.visible = true
    loadingSpinner.visible = true
    errorCircle.visible = false
    errorBackground.visible = false
    unavailableText.visible = false
    errorText.visible = false
  }

  private fun moveToErrorState() {
    state = State.ERROR
    visible = true
    background.visible = true
    loadingSpinner.visible = false
    errorCircle.visible = true
    errorBackground.visible = true
    unavailableText.visible = false
    errorText.visible = true

    if (NetworkConstraint.isMet(AppDependencies.application)) {
      errorText.setText(R.string.StorySlateView__couldnt_load_content)
    } else {
      errorText.setText(R.string.StorySlateView__no_internet_connection)
    }
  }

  private fun moveToNotFoundState(state: State, sender: Recipient?) {
    this.state = state
    visible = true
    background.visible = true
    loadingSpinner.visible = false
    errorCircle.visible = false
    errorBackground.visible = false
    unavailableText.visible = true
    errorText.visible = false

    if (state == State.FAILED && sender != null) {
      unavailableText.text = context.getString(R.string.StorySlateView__cant_download_story_s_will_need_to_share_it_again, sender.getShortDisplayName(context))
    } else {
      unavailableText.setText(R.string.StorySlateView__this_story_is_no_longer_available)
    }
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
      val rootState: Parcelable? = state.getParcelableCompat("ROOT", Parcelable::class.java)
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

  enum class State(val code: Int, val hasClickableContent: Boolean) {
    LOADING(0, false),
    ERROR(1, true),
    RETRY(2, true),
    NOT_FOUND(3, false),
    HIDDEN(4, false),
    FAILED(5, false);

    companion object {
      fun fromCode(code: Int): State {
        return values().firstOrNull {
          it.code == code
        } ?: HIDDEN
      }
    }
  }
}
