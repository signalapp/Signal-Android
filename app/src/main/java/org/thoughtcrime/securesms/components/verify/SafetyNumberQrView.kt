/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.verify

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.PorterDuff
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.Animation
import android.view.animation.AnticipateInterpolator
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.TextSwitcher
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.widget.ImageViewCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import org.signal.core.util.dp
import org.signal.libsignal.protocol.fingerprint.Fingerprint
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.qr.QrCodeUtil
import org.thoughtcrime.securesms.util.ContextUtil
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.visible
import java.nio.charset.Charset
import java.util.Locale

class SafetyNumberQrView : ConstraintLayout {

  companion object {
    private const val NUMBER_OF_SEGMENTS = 12

    @JvmStatic
    fun getSegments(fingerprint: Fingerprint): Array<String> {
      val segments = arrayOfNulls<String>(NUMBER_OF_SEGMENTS)
      val digits = fingerprint.displayableFingerprint.displayText
      val partSize = digits.length / NUMBER_OF_SEGMENTS
      for (i in 0 until NUMBER_OF_SEGMENTS) {
        segments[i] = digits.substring(i * partSize, i * partSize + partSize)
      }
      return (0 until NUMBER_OF_SEGMENTS).map { digits.substring(it * partSize, it * partSize + partSize) }.toTypedArray()
    }
  }

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet, defaultStyle: Int) : super(context, attrs, defaultStyle)

  private val codes: Array<TextView>

  val numbersContainer: View
  val qrCodeContainer: View

  val shareButton: ImageView

  private val loading: View
  private val qrCode: ImageView
  private val qrVerified: ImageView
  private val tapLabel: TextSwitcher

  init {
    inflate(context, R.layout.safety_number_qr_view, this)

    numbersContainer = findViewById(R.id.number_table)
    loading = findViewById(R.id.loading)
    qrCodeContainer = findViewById(R.id.qr_code_container)
    qrCode = findViewById(R.id.qr_code)
    qrVerified = findViewById(R.id.qr_verified)
    tapLabel = findViewById(R.id.tap_label)

    codes = arrayOf(
      findViewById(R.id.code_first),
      findViewById(R.id.code_second),
      findViewById(R.id.code_third),
      findViewById(R.id.code_fourth),
      findViewById(R.id.code_fifth),
      findViewById(R.id.code_sixth),
      findViewById(R.id.code_seventh),
      findViewById(R.id.code_eighth),
      findViewById(R.id.code_ninth),
      findViewById(R.id.code_tenth),
      findViewById(R.id.code_eleventh),
      findViewById(R.id.code_twelth)
    )

    shareButton = findViewById(R.id.share)

    outlineProvider = object : ViewOutlineProvider() {
      override fun getOutline(view: View, outline: Outline) {
        outline.setRoundRect(0, 0, view.width, view.height, 24.dp.toFloat())
      }
    }

    clipToOutline = true
    setSafetyNumberType(false)
  }

  fun setFingerprintViews(fingerprint: Fingerprint, animate: Boolean) {
    val segments: Array<String> = getSegments(fingerprint)
    for (i in codes.indices) {
      if (animate) setCodeSegment(codes[i], segments[i]) else codes[i].text = segments[i]
    }

    val qrCodeData = fingerprint.scannableFingerprint.serialized
    val qrCodeString = String(qrCodeData, Charset.forName("ISO-8859-1"))
    val qrCodeBitmap = QrCodeUtil.createNoPadding(qrCodeString)

    qrCode.setImageBitmap(qrCodeBitmap)
    shareButton.visible = true

    if (animate) {
      ViewUtil.fadeIn(qrCode, 1000)
      ViewUtil.fadeIn(tapLabel, 1000)
      ViewUtil.fadeOut(loading, 300, GONE)
    } else {
      qrCode.visibility = VISIBLE
      tapLabel.visibility = VISIBLE
      loading.visibility = GONE
    }
  }

  fun setSafetyNumberType(newType: Boolean) {
    if (newType) {
      ImageViewCompat.setImageTintList(shareButton, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.signal_dark_colorOnSurface)))
      setBackgroundColor(ContextCompat.getColor(context, R.color.safety_number_card_blue))
      codes.forEach {
        it.setTextColor(ContextCompat.getColor(context, R.color.signal_light_colorOnPrimary))
      }
    } else {
      ImageViewCompat.setImageTintList(shareButton, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.signal_light_colorOnSurface)))
      setBackgroundColor(ContextCompat.getColor(context, R.color.safety_number_card_grey))
      codes.forEach {
        it.setTextColor(ContextCompat.getColor(context, R.color.signal_light_colorOnSurfaceVariant))
      }
    }
  }

  fun animateVerifiedSuccess() {
    val qrBitmap = (qrCode.drawable as BitmapDrawable).bitmap
    val qrSuccess: Bitmap = createVerifiedBitmap(qrBitmap.width, qrBitmap.height, R.drawable.symbol_check_white_48)
    qrVerified.setImageBitmap(qrSuccess)
    qrVerified.background.setColorFilter(resources.getColor(R.color.green_500), PorterDuff.Mode.MULTIPLY)
    tapLabel.setText(context.getString(R.string.verify_display_fragment__successful_match))
    animateVerified()
  }

  fun animateVerifiedFailure() {
    val qrBitmap = (qrCode.drawable as BitmapDrawable).bitmap
    val qrSuccess: Bitmap = createVerifiedBitmap(qrBitmap.width, qrBitmap.height, R.drawable.symbol_x_white_48)
    qrVerified.setImageBitmap(qrSuccess)
    qrVerified.background.setColorFilter(resources.getColor(R.color.red_500), PorterDuff.Mode.MULTIPLY)
    tapLabel.setText(context.getString(R.string.verify_display_fragment__failed_to_verify_safety_number))
    animateVerified()
  }

  private fun animateVerified() {
    val scaleAnimation = ScaleAnimation(
      0f,
      1f,
      0f,
      1f,
      ScaleAnimation.RELATIVE_TO_SELF,
      0.5f,
      ScaleAnimation.RELATIVE_TO_SELF,
      0.5f
    )
    scaleAnimation.interpolator = FastOutSlowInInterpolator()
    scaleAnimation.duration = 800
    scaleAnimation.setAnimationListener(object : Animation.AnimationListener {
      override fun onAnimationStart(animation: Animation) {}
      override fun onAnimationEnd(animation: Animation) {
        qrVerified.postDelayed({
          val scaleAnimation = ScaleAnimation(
            1f,
            0f,
            1f,
            0f,
            ScaleAnimation.RELATIVE_TO_SELF,
            0.5f,
            ScaleAnimation.RELATIVE_TO_SELF,
            0.5f
          )
          scaleAnimation.interpolator = AnticipateInterpolator()
          scaleAnimation.duration = 500
          ViewUtil.animateOut(qrVerified, scaleAnimation, GONE)
          ViewUtil.fadeIn(qrCode, 800)
          qrCodeContainer.isEnabled = true
          tapLabel.setText(context.getString(R.string.verify_display_fragment__tap_to_scan))
        }, 2000)
      }

      override fun onAnimationRepeat(animation: Animation) {}
    })
    ViewUtil.fadeOut(qrCode, 200, INVISIBLE)
    ViewUtil.animateIn(qrVerified, scaleAnimation)
    qrCodeContainer.isEnabled = false
  }

  private fun createVerifiedBitmap(width: Int, height: Int, @DrawableRes id: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val check = ContextUtil.requireDrawable(context, id).toBitmap()
    val offset = ((width - check.width) / 2).toFloat()
    canvas.drawBitmap(check, offset, offset, null)
    return bitmap
  }

  private fun setCodeSegment(codeView: TextView, segment: String) {
    val valueAnimator = ValueAnimator.ofInt(0, segment.toInt())
    valueAnimator.addUpdateListener { animation: ValueAnimator ->
      val value = animation.animatedValue as Int
      codeView.text = String.format(Locale.getDefault(), "%05d", value)
    }
    valueAnimator.duration = 1000
    valueAnimator.start()
  }
}
