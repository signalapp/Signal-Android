package org.thoughtcrime.securesms.stories.viewer

import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.core.Flowable
import org.thoughtcrime.securesms.util.rx.RxStore

class StoryVolumeViewModel : ViewModel() {
  private val store = RxStore(StoryVolumeState())

  val state: Flowable<StoryVolumeState> = store.stateFlowable
  val snapshot: StoryVolumeState get() = store.state

  override fun onCleared() {
    store.dispose()
  }

  fun mute() {
    store.update { it.copy(isMuted = true) }
  }

  fun unmute() {
    store.update { it.copy(isMuted = false) }
  }

  fun onVolumeDown(level: Int) {
    store.update { it.copy(level = level) }
  }

  fun onVolumeUp(level: Int) {
    store.update { it.copy(isMuted = false, level = level) }
  }
}
