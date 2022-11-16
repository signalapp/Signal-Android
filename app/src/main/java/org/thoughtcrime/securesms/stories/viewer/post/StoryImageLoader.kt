package org.thoughtcrime.securesms.stories.viewer.post

import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.stories.viewer.page.StoryCache
import org.thoughtcrime.securesms.stories.viewer.page.StoryDisplay

/**
 * Render logic for story image posts
 */
class StoryImageLoader(
  private val fragment: StoryPostFragment,
  private val imagePost: StoryPostState.ImagePost,
  private val storyCache: StoryCache,
  private val storySize: StoryDisplay.Size,
  private val postImage: ImageView,
  blurImage: ImageView,
  private val callback: StoryPostFragment.Callback
) : StoryBlurLoader.Callback {

  private val blurLoader = StoryBlurLoader(
    fragment.viewLifecycleOwner.lifecycle,
    imagePost.blurHash,
    imagePost.imageUri,
    storyCache,
    storySize,
    blurImage,
    this
  )

  companion object {
    private val TAG = Log.tag(StoryImageLoader::class.java)
  }

  private var blurState: LoadState = LoadState.INIT
  private var imageState: LoadState = LoadState.INIT

  private val imageListener = object : StoryCache.Listener {
    override fun onResourceReady(resource: Drawable) {
      Log.d(TAG, "Loaded cached resource of size w${resource.intrinsicWidth} x h${resource.intrinsicHeight}")
      postImage.setImageDrawable(resource)
      imageState = LoadState.READY
      notifyListeners()
    }

    override fun onLoadFailed() {
      imageState = LoadState.FAILED
      notifyListeners()
    }
  }

  fun load() {
    val cacheValue = storyCache.getFromCache(imagePost.imageUri)
    if (cacheValue != null) {
      loadViaCache(cacheValue)
    } else {
      loadViaGlide(storySize)
    }

    blurLoader.load()
  }

  fun clear() {
    GlideApp.with(postImage).clear(postImage)

    postImage.setImageDrawable(null)

    blurLoader.clear()
  }

  private fun loadViaCache(cacheValue: StoryCache.StoryCacheValue) {
    Log.d(TAG, "Image in cache. Loading via cache...")

    val imageTarget = cacheValue.imageTarget!!
    imageTarget.addListener(imageListener)
    fragment.viewLifecycleOwner.lifecycle.addObserver(OnDestroy { imageTarget.removeListener(imageListener) })
  }

  private fun loadViaGlide(storySize: StoryDisplay.Size) {
    Log.d(TAG, "Image not in cache. Loading via glide...")
    GlideApp.with(postImage)
      .load(DecryptableStreamUriLoader.DecryptableUri(imagePost.imageUri))
      .override(storySize.width, storySize.height)
      .centerInside()
      .addListener(object : RequestListener<Drawable> {
        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
          imageState = LoadState.FAILED
          notifyListeners()
          return false
        }

        override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
          imageState = LoadState.READY
          notifyListeners()
          return false
        }
      })
      .into(postImage)
  }

  override fun onBlurLoaded() {
    blurState = LoadState.READY
    notifyListeners()
  }

  override fun onBlurFailed() {
    blurState = LoadState.FAILED
    notifyListeners()
  }

  private fun notifyListeners() {
    if (fragment.isDetached) {
      Log.w(TAG, "Fragment is detached, dropping notify call.")
      return
    }

    if (blurState != LoadState.INIT && imageState != LoadState.INIT) {
      if (imageState == LoadState.FAILED) {
        callback.onContentNotAvailable()
      } else {
        callback.onContentReady()
      }
    }
  }

  private enum class LoadState {
    INIT,
    READY,
    FAILED
  }

  private inner class OnDestroy(private val onDestroy: () -> Unit) : DefaultLifecycleObserver {
    override fun onDestroy(owner: LifecycleOwner) {
      onDestroy()
    }
  }
}
