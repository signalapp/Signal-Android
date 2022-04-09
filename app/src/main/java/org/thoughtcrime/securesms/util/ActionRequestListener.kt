package org.thoughtcrime.securesms.util

import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target

/**
 * Performs actions in onResourceReady and onLoadFailed
 */
class ActionRequestListener<T>(
  private val onResourceReady: Runnable,
  private val onLoadFailed: Runnable
) : RequestListener<T> {

  companion object {
    @JvmStatic
    fun <T> onEither(onEither: Runnable): ActionRequestListener<T> {
      return ActionRequestListener(onEither, onEither)
    }
  }

  override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<T>?, isFirstResource: Boolean): Boolean {
    onLoadFailed.run()
    return false
  }

  override fun onResourceReady(resource: T, model: Any?, target: Target<T>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
    onResourceReady.run()
    return false
  }
}
