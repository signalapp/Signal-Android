package org.thoughtcrime.securesms.stories.viewer.page

import android.graphics.drawable.Drawable
import android.net.Uri
import com.bumptech.glide.Priority
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.attachments.Attachment
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.util.MediaUtil

/**
 * StoryCache loads attachment drawables into memory and holds onto them until it is cleared. This class only
 * works with Images.
 */
class StoryCache(
  private val glideRequests: GlideRequests,
  private val storySize: StoryDisplay.Size
) {

  companion object {
    private val TAG = Log.tag(StoryCache::class.java)
  }

  private val cache = mutableMapOf<Uri, StoryCacheValue>()

  /**
   * Load the given list of attachments into memory. This will automatically filter out any that are not yet
   * downloaded, not images, or already in progress.
   */
  fun prefetch(attachments: List<Attachment>) {
    Log.d(TAG, "Loading ${attachments.size} attachments at $storySize")

    val prefetchableAttachments: List<Attachment> = attachments
      .asSequence()
      .filter { it.uri != null && it.uri !in cache }
      .filter { MediaUtil.isImage(it) || it.blurHash != null }
      .filter { it.transferState == AttachmentTable.TRANSFER_PROGRESS_DONE }
      .toList()

    val newMappings: Map<Uri, StoryCacheValue> = prefetchableAttachments.associateWith { attachment ->
      val imageTarget = if (MediaUtil.isImage(attachment)) {
        glideRequests
          .load(DecryptableStreamUriLoader.DecryptableUri(attachment.uri!!))
          .priority(Priority.HIGH)
          .centerInside()
          .into(StoryCacheTarget(attachment.uri!!, storySize))
      } else {
        null
      }

      val blurTarget = if (attachment.blurHash != null) {
        glideRequests
          .load(attachment.blurHash)
          .priority(Priority.HIGH)
          .into(StoryCacheTarget(attachment.uri!!, storySize))
      } else {
        null
      }

      StoryCacheValue(imageTarget, blurTarget)
    }.mapKeys { it.key.uri!! }

    cache.putAll(newMappings)
  }

  /**
   * Clears and cancels all cached values.
   */
  fun clear() {
    val values = ArrayList(cache.values)

    values.forEach { value ->
      glideRequests.clear(value.imageTarget)
      value.blurTarget?.let { glideRequests.clear(it) }
    }

    cache.clear()
  }

  /**
   * Get the appropriate cache value from the cache if it exists.
   * Since this is only used for images, we don't need to worry about transform properties.
   */
  fun getFromCache(uri: Uri): StoryCacheValue? {
    return cache[uri]
  }

  /**
   * Represents the load targets for an image and blur.
   */
  data class StoryCacheValue(val imageTarget: StoryCacheTarget?, val blurTarget: StoryCacheTarget?)

  /**
   * A custom glide target for loading a drawable. Placeholder immediately clears, and we don't want to do that, so we use this instead.
   */
  inner class StoryCacheTarget(val uri: Uri, size: StoryDisplay.Size) : CustomTarget<Drawable>(size.width, size.height) {

    private var resource: Drawable? = null
    private var isFailed: Boolean = false

    private val listeners = mutableSetOf<Listener>()

    fun addListener(listener: Listener) {
      listeners.add(listener)
      resource?.let { listener.onResourceReady(it) }
      if (isFailed) {
        listener.onLoadFailed()
      }
    }

    fun removeListener(listener: Listener) {
      listeners.remove(listener)
    }

    override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
      this.resource = resource
      listeners.forEach { it.onResourceReady(resource) }
    }

    override fun onLoadFailed(errorDrawable: Drawable?) {
      super.onLoadFailed(errorDrawable)
      isFailed = true
      listeners.forEach { it.onLoadFailed() }
    }

    override fun onLoadCleared(placeholder: Drawable?) {
      resource = null
      isFailed = false
      cache.remove(uri)
    }
  }

  /**
   * Feedback from a target for when it's data is loaded or failed.
   */
  interface Listener {
    fun onResourceReady(resource: Drawable)
    fun onLoadFailed()
  }
}
