package org.thoughtcrime.securesms.stories.viewer.page

import android.view.ViewStub
import org.thoughtcrime.securesms.blurhash.BlurHash
import org.thoughtcrime.securesms.stories.StoryFirstTimeNavigationView
import org.thoughtcrime.securesms.util.views.Stub

/**
 * Specialized stub that allows for early arrival of the blurhash and callback.
 */
class StoryFirstNavigationStub(viewStub: ViewStub) : Stub<StoryFirstTimeNavigationView>(viewStub) {

  private var callback: StoryFirstTimeNavigationView.Callback? = null
  private var blurHash: BlurHash? = null

  fun setCallback(callback: StoryFirstTimeNavigationView.Callback) {
    if (resolved()) {
      get().callback = callback
    } else {
      this.callback = callback
    }
  }

  fun setBlurHash(blurHash: BlurHash?) {
    if (resolved()) {
      get().setBlurHash(blurHash)
    } else {
      this.blurHash = blurHash
    }
  }

  override fun get(): StoryFirstTimeNavigationView {
    val needsResolve = !resolved()
    val view = super.get()
    if (needsResolve) {
      view.setBlurHash(blurHash)
      view.callback = callback
      blurHash = null
      callback = null
    }

    return view
  }
}
