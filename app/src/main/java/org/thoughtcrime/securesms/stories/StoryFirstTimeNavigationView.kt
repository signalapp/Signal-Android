package org.thoughtcrime.securesms.stories

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.blurhash.BlurHash
import org.thoughtcrime.securesms.components.CornerMask
import org.thoughtcrime.securesms.mms.GlideApp
import org.thoughtcrime.securesms.util.visible

class StoryFirstTimeNavigationView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

  companion object {
    private const val BLUR_ALPHA = 0x3D
    private const val NO_BLUR_ALPHA = 0xB3
  }

  init {
    inflate(context, R.layout.story_first_time_navigation_view, this)
  }

  private val blurHashView: ImageView = findViewById(R.id.edu_blur_hash)
  private val overlayView: ImageView = findViewById(R.id.edu_overlay)
  private val gotIt: View = findViewById(R.id.edu_got_it)

  private val cornerMask = CornerMask(this).apply {
    setRadius(DimensionUnit.DP.toPixels(18f).toInt())
  }

  var callback: Callback? = null

  init {
    if (isRenderEffectSupported()) {
      blurHashView.visible = false
      overlayView.visible = true
      overlayView.setImageDrawable(ColorDrawable(Color.argb(BLUR_ALPHA, 0, 0, 0)))
    }

    gotIt.setOnClickListener {
      callback?.onGotItClicked()
      GlideApp.with(this).clear(blurHashView)
      blurHashView.setImageDrawable(null)
      hide()
    }

    setOnClickListener { }
  }

  override fun dispatchDraw(canvas: Canvas) {
    super.dispatchDraw(canvas)
    cornerMask.mask(canvas)
  }

  fun setBlurHash(blurHash: BlurHash?) {
    if (isRenderEffectSupported() || callback?.userHasSeenFirstNavigationView() == true) {
      return
    }

    if (blurHash == null) {
      blurHashView.visible = false
      overlayView.visible = true
      overlayView.setImageDrawable(ColorDrawable(Color.argb(NO_BLUR_ALPHA, 0, 0, 0)))
      GlideApp.with(this).clear(blurHashView)
      return
    } else {
      blurHashView.visible = true
      overlayView.visible = true
      overlayView.setImageDrawable(ColorDrawable(Color.argb(BLUR_ALPHA, 0, 0, 0)))
    }

    GlideApp.with(this)
      .load(blurHash)
      .addListener(object : RequestListener<Drawable> {
        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
          setBlurHash(null)
          return false
        }

        override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
          return false
        }
      })
      .into(blurHashView)
  }

  fun show() {
    if (callback?.userHasSeenFirstNavigationView() == true) {
      return
    }

    visible = true
  }

  fun hide() {
    visible = false
  }

  private fun isRenderEffectSupported(): Boolean {
    return Build.VERSION.SDK_INT >= 31
  }

  interface Callback {
    fun userHasSeenFirstNavigationView(): Boolean
    fun onGotItClicked()
  }
}
