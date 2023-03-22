package org.thoughtcrime.securesms.stories.viewer.reply

import android.animation.FloatEvaluator
import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.widget.ImageView
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.card.MaterialCardView
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.animation.transitions.CrossfaderTransition
import org.thoughtcrime.securesms.blurhash.BlurHash
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.mms.DecryptableStreamUriLoader
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.stories.StoryTextPostModel
import kotlin.reflect.KProperty

class StoriesSharedElementCrossFaderView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : MaterialCardView(context, attrs), CrossfaderTransition.Crossfadeable {

  companion object {
    val CORNER_RADIUS_START = DimensionUnit.DP.toPixels(12f)
    val CORNER_RADIUS_END = DimensionUnit.DP.toPixels(18f)
    val CORNER_RADIUS_EVALUATOR = FloatEvaluator()
  }

  init {
    inflate(context, R.layout.stories_shared_element_crossfader, this)
  }

  private val sourceView: ImageView = findViewById(R.id.source_image)
  private val sourceBlurView: ImageView = findViewById(R.id.source_image_blur)
  private val targetView: ImageView = findViewById(R.id.target_image)
  private val targetBlurView: ImageView = findViewById(R.id.target_image_blur)

  private var isSourceReady: Boolean by NotifyIfReadyDelegate(false)
  private var isSourceBlurReady: Boolean by NotifyIfReadyDelegate(false)
  private var isTargetReady: Boolean by NotifyIfReadyDelegate(false)
  private var isTargetBlurReady: Boolean by NotifyIfReadyDelegate(false)

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
        }
      )
      .placeholder(storyTextPostModel.getPlaceholder())
      .dontAnimate()
      .centerCrop()
      .into(sourceView)

    GlideApp.with(sourceBlurView).clear(sourceBlurView)
    isSourceBlurReady = true
  }

  fun setSourceView(uri: Uri, blur: BlurHash?) {
    if (latestSource == uri) {
      return
    }

    latestSource = uri

    GlideApp.with(sourceView)
      .load(DecryptableStreamUriLoader.DecryptableUri(uri))
      .addListener(
        OnReadyListener {
          isSourceReady = true
        }
      )
      .dontAnimate()
      .centerCrop()
      .into(sourceView)

    if (blur == null) {
      GlideApp.with(sourceBlurView).clear(sourceBlurView)
      isSourceBlurReady = true
    } else {
      GlideApp.with(sourceBlurView)
        .load(blur)
        .addListener(
          OnReadyListener {
            isSourceBlurReady = true
          }
        )
        .dontAnimate()
        .centerCrop()
        .into(sourceBlurView)
    }
  }

  fun setTargetView(messageRecord: MmsMessageRecord): Boolean {
    val thumbUri = messageRecord.slideDeck.thumbnailSlide?.uri
    val thumbBlur: BlurHash? = messageRecord.slideDeck.thumbnailSlide?.placeholderBlur
    when {
      messageRecord.storyType.isTextStory -> setTargetView(StoryTextPostModel.parseFrom(messageRecord))
      thumbUri != null -> setTargetView(thumbUri, thumbBlur)
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
        }
      )
      .dontAnimate()
      .placeholder(storyTextPostModel.getPlaceholder())
      .centerCrop()
      .into(targetView)

    GlideApp.with(targetBlurView).clear(targetBlurView)
    isTargetBlurReady = true
  }

  private fun setTargetView(uri: Uri, blur: BlurHash?) {
    if (latestTarget == uri) {
      return
    }

    latestTarget = uri

    GlideApp.with(targetView)
      .load(DecryptableStreamUriLoader.DecryptableUri(uri))
      .addListener(
        OnReadyListener {
          isTargetReady = true
        }
      )
      .dontAnimate()
      .fitCenter()
      .into(targetView)

    if (blur == null) {
      GlideApp.with(targetBlurView).clear(targetBlurView)
      isTargetBlurReady = true
    } else {
      GlideApp.with(targetBlurView)
        .load(blur)
        .addListener(
          OnReadyListener {
            isTargetBlurReady = true
          }
        )
        .dontAnimate()
        .centerCrop()
        .into(targetBlurView)
    }
  }

  private fun notifyIfReady() {
    if (isSourceReady && isTargetReady && isSourceBlurReady && isTargetBlurReady) {
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
      sourceBlurView.alpha = progress
      targetView.alpha = 1f - progress
      targetBlurView.alpha = 1f - progress
      radius = CORNER_RADIUS_EVALUATOR.evaluate(progress, CORNER_RADIUS_END, CORNER_RADIUS_START)
    } else {
      sourceView.alpha = 1f - progress
      sourceBlurView.alpha = 1f - progress
      targetView.alpha = progress
      targetBlurView.alpha = progress
      radius = CORNER_RADIUS_EVALUATOR.evaluate(progress, CORNER_RADIUS_START, CORNER_RADIUS_END)
    }
  }

  override fun onCrossfadeStarted(reverse: Boolean) {
    alpha = 1f

    sourceView.alpha = if (reverse) 0f else 1f
    sourceBlurView.alpha = if (reverse) 0f else 1f
    targetView.alpha = if (reverse) 1f else 0f
    targetBlurView.alpha = if (reverse) 1f else 0f

    radius = if (reverse) CORNER_RADIUS_END else CORNER_RADIUS_START

    callback?.onAnimationStarted()
  }

  override fun onCrossfadeFinished(reverse: Boolean) {
    if (reverse) {
      return
    }

    callback?.onAnimationFinished()
  }

  private inner class NotifyIfReadyDelegate(var value: Boolean) {
    operator fun getValue(storiesSharedElementCrossFaderView: StoriesSharedElementCrossFaderView, property: KProperty<*>): Boolean {
      return value
    }

    operator fun setValue(storiesSharedElementCrossFaderView: StoriesSharedElementCrossFaderView, property: KProperty<*>, b: Boolean) {
      value = b
      notifyIfReady()
    }
  }

  interface Callback {
    fun onReadyToAnimate()
    fun onAnimationStarted()
    fun onAnimationFinished()
  }
}
