package org.thoughtcrime.securesms.stories.viewer.post

import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.blurhash.BlurHash
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.stories.viewer.page.StoryCache
import org.thoughtcrime.securesms.stories.viewer.page.StoryDisplay

/**
 * Responsible for managing the lifecycle around loading a BlurHash
 */
class StoryBlurLoader(
  private val lifecycle: Lifecycle,
  private val blurHash: BlurHash?,
  private val cacheKey: Uri,
  private val storyCache: StoryCache,
  private val storySize: StoryDisplay.Size,
  private val blurImage: ImageView,
  private val callback: Callback = NO_OP
) {
  companion object {
    private val TAG = Log.tag(StoryBlurLoader::class.java)

    private val NO_OP = object : Callback {
      override fun onBlurLoaded() = Unit
      override fun onBlurFailed() = Unit
    }
  }

  private val blurListener = object : StoryCache.Listener {
    override fun onResourceReady(resource: Drawable) {
      blurImage.setImageDrawable(resource)
      callback.onBlurLoaded()
    }

    override fun onLoadFailed() {
      callback.onBlurFailed()
    }
  }

  fun load() {
    val cacheValue = storyCache.getFromCache(cacheKey)
    if (cacheValue != null) {
      loadViaCache(cacheValue)
    } else {
      loadViaGlide(blurHash, storySize)
    }
  }

  fun clear() {
    GlideApp.with(blurImage).clear(blurImage)

    blurImage.setImageDrawable(null)
  }

  private fun loadViaCache(cacheValue: StoryCache.StoryCacheValue) {
    Log.d(TAG, "Blur in cache. Loading via cache...")

    val blurTarget = cacheValue.blurTarget
    if (blurTarget != null) {
      blurTarget.addListener(blurListener)
      lifecycle.addObserver(OnDestroy { blurTarget.removeListener(blurListener) })
    } else {
      callback.onBlurFailed()
    }
  }

  private fun loadViaGlide(blurHash: BlurHash?, storySize: StoryDisplay.Size) {
    if (blurHash != null) {
      GlideApp.with(blurImage)
        .load(blurHash)
        .override(storySize.width, storySize.height)
        .addListener(object : RequestListener<Drawable> {
          override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
            callback.onBlurFailed()
            return false
          }

          override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
            callback.onBlurLoaded()
            return false
          }
        })
        .into(blurImage)
    } else {
      callback.onBlurFailed()
    }
  }

  interface Callback {
    fun onBlurLoaded()
    fun onBlurFailed()
  }

  private inner class OnDestroy(private val onDestroy: () -> Unit) : DefaultLifecycleObserver {
    override fun onDestroy(owner: LifecycleOwner) {
      onDestroy()
    }
  }
}
