package org.thoughtcrime.securesms.stories.viewer.reply

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.animation.transitions.CrossfaderTransition
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.stories.StoryTextPostModel

class StoriesSharedElementCrossFaderView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs), CrossfaderTransition.Crossfadeable {

  init {
    inflate(context, R.layout.stories_shared_element_crossfader, this)
  }

  private val sourceView: ImageView = findViewById(R.id.source_image)
  private val targetView: ImageView = findViewById(R.id.target_image)

  private var isSourceReady: Boolean = false
  private var isTargetReady: Boolean = false

  private var latestSource: Any? = null
  private var latestTarget: Any? = null

  var callback: Callback? = null

  fun setSourceView(storyTextPostModel: StoryTextPostModel) {
    if (latestSource == storyTextPostModel) {
      return
    }

    latestSource = storyTextPostModel

    GlideApp.with(sourceView)
      .load(storyTextPostModel)
      .addListener(
        OnReadyListener {
          isSourceReady = true
          notifyIfReady()
        }
      )
      .placeholder(storyTextPostModel.getPlaceholder())
      .dontAnimate()
      .centerCrop()
      .into(sourceView)
  }

  fun setSourceView(uri: Uri) {
    if (latestSource == uri) {
      return
    }

    latestSource = uri

    GlideApp.with(sourceView)
      .load(DecryptableStreamUriLoader.DecryptableUri(uri))
      .addListener(
        OnReadyListener {
          isSourceReady = true
          notifyIfReady()
        }
      )
      .dontAnimate()
      .centerCrop()
      .into(sourceView)
  }

  fun setTargetView(messageRecord: MmsMessageRecord): Boolean {
    val thumbUri = messageRecord.slideDeck.thumbnailSlide?.uri
    when {
      messageRecord.storyType.isTextStory -> setTargetView(StoryTextPostModel.parseFrom(messageRecord))
      thumbUri != null -> setTargetView(thumbUri)
      else -> return false
    }

    return true
  }

  private fun setTargetView(storyTextPostModel: StoryTextPostModel) {
    if (latestTarget == storyTextPostModel) {
      return
    }

    latestTarget = storyTextPostModel

    GlideApp.with(targetView)
      .load(storyTextPostModel)
      .addListener(
        OnReadyListener {
          isTargetReady = true
          notifyIfReady()
        }
      )
      .dontAnimate()
      .placeholder(storyTextPostModel.getPlaceholder())
      .centerCrop()
      .into(targetView)
  }

  private fun setTargetView(uri: Uri) {
    if (latestTarget == uri) {
      return
    }

    latestTarget = uri

    GlideApp.with(targetView)
      .load(DecryptableStreamUriLoader.DecryptableUri(uri))
      .addListener(
        OnReadyListener {
          isTargetReady = true
          notifyIfReady()
        }
      )
      .dontAnimate()
      .centerCrop()
      .into(targetView)
  }

  private fun notifyIfReady() {
    if (isSourceReady && isTargetReady) {
      callback?.onReadyToAnimate()
    }
  }

  private class OnReadyListener(private val onReady: () -> Unit) : RequestListener<Drawable> {
    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
      onReady()
      return false
    }

    override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
      onReady()
      return false
    }
  }

  override fun onCrossfadeAnimationUpdated(progress: Float, reverse: Boolean) {
    if (reverse) {
      sourceView.alpha = progress
      targetView.alpha = 1f - progress
    } else {
      sourceView.alpha = 1f - progress
      targetView.alpha = progress
    }
  }

  override fun onCrossfadeStarted(reverse: Boolean) {
    alpha = 1f

    sourceView.alpha = if (reverse) 0f else 1f
    targetView.alpha = if (reverse) 1f else 0f

    callback?.onAnimationStarted()
  }

  override fun onCrossfadeFinished(reverse: Boolean) {
    if (reverse) {
      return
    }

    animate().alpha(0f)

    callback?.onAnimationFinished()
  }

  interface Callback {
    fun onReadyToAnimate()
    fun onAnimationStarted()
    fun onAnimationFinished()
  }
}
