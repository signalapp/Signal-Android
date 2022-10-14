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
import org.thoughtcrime.securesms.blurhash.BlurHash
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
  private val blurImage: ImageView,
  private val callback: StoryPostFragment.Callback
) {

  companion object {
    private val TAG = Log.tag(StoryImageLoader::class.java)
  }

  private var blurState: LoadState = LoadState.INIT
  private var imageState: LoadState = LoadState.INIT

  private val imageListener = object : StoryCache.Listener {
    override fun onResourceReady(resource: Drawable) {
      postImage.setImageDrawable(resource)
      imageState = LoadState.READY
      notifyListeners()
    }

    override fun onLoadFailed() {
      imageState = LoadState.FAILED
      notifyListeners()
    }
  }

  private val blurListener = object : StoryCache.Listener {
    override fun onResourceReady(resource: Drawable) {
      blurImage.setImageDrawable(resource)
      blurState = LoadState.READY
      notifyListeners()
    }

    override fun onLoadFailed() {
      blurState = LoadState.FAILED
      notifyListeners()
    }
  }

  fun load() {
    val cacheValue = storyCache.getFromCache(imagePost.imageUri)
    if (cacheValue != null) {
      loadViaCache(cacheValue)
    } else {
      loadViaGlide(imagePost.blurHash, storySize)
    }
  }

  fun clear() {
    GlideApp.with(postImage).clear(postImage)
    GlideApp.with(blurImage).clear(blurImage)

    postImage.setImageDrawable(null)
    blurImage.setImageDrawable(null)
  }

  private fun loadViaCache(cacheValue: StoryCache.StoryCacheValue) {
    Log.d(TAG, "Attachment in cache. Loading via cache...")
    val blurTarget = cacheValue.blurTarget
    if (blurTarget != null) {
      blurTarget.addListener(blurListener)
      fragment.viewLifecycleOwner.lifecycle.addObserver(OnDestroy { blurTarget.removeListener(blurListener) })
    } else {
      blurState = LoadState.FAILED
      notifyListeners()
    }

    val imageTarget = cacheValue.imageTarget
    imageTarget.addListener(imageListener)
    fragment.viewLifecycleOwner.lifecycle.addObserver(OnDestroy { imageTarget.removeListener(blurListener) })
  }

  private fun loadViaGlide(blurHash: BlurHash?, storySize: StoryDisplay.Size) {
    Log.d(TAG, "Attachment not in cache. Loading via glide...")
    if (blurHash != null) {
      GlideApp.with(blurImage)
        .load(blurHash)
        .override(storySize.width, storySize.height)
        .addListener(object : RequestListener<Drawable> {
          override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
            blurState = LoadState.FAILED
            notifyListeners()
            return false
          }

          override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
            blurState = LoadState.READY
            notifyListeners()
            return false
          }
        })
        .into(blurImage)
    } else {
      blurState = LoadState.FAILED
      notifyListeners()
    }

    GlideApp.with(postImage)
      .load(DecryptableStreamUriLoader.DecryptableUri(imagePost.imageUri))
      .override(storySize.width, storySize.height)
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

  private inner class OnDestroy(private val onDestroy: () -> Unit) : DefaultLifecycleObserver {
    override fun onDestroy(owner: LifecycleOwner) {
      onDestroy()
    }
  }

  private enum class LoadState {
    INIT,
    READY,
    FAILED
  }
}
