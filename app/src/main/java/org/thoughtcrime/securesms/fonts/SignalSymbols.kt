/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.fonts

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle

/**
 * Helper object for working with the SignalSymbols font
 */
object SignalSymbols {

  enum class Glyph(val unicode: Char) {
    CHECKMARK('\u2713'),
    CHEVRON_RIGHT('\uE025'),
    PERSON_CIRCLE('\uE05E')
  }

  enum class Weight {
    BOLD,
    REGULAR
  }

  private val cache = mutableMapOf<Weight, Typeface>()

  @JvmStatic
  fun getSpannedString(
    context: Context,
    weight: Weight,
    glyph: Glyph
  ): CharSequence {
    val typeface = getTypeface(context, weight)
    val span = CustomTypefaceSpan(typeface)

    val text = SpannableStringBuilder(glyph.unicode.toString())
    text.setSpan(span, 0, text.length, 0)

    return text
  }

  @Composable
  fun AnnotatedString.Builder.SignalSymbol(weight: Weight, glyph: Glyph) {
    withStyle(
      SpanStyle(
        fontFamily = FontFamily(getTypeface(LocalContext.current, weight))
      )
    ) {
      append(glyph.unicode.toString())
    }
  }

  private fun getTypeface(context: Context, weight: Weight): Typeface {
    return when (weight) {
      Weight.BOLD -> getBoldWeightedFont(context)
      Weight.REGULAR -> getRegularWeightedFont(context)
      else -> error("Unsupported weight: $weight")
    }
  }

  private fun getBoldWeightedFont(context: Context): Typeface {
    return cache.getOrPut(
      Weight.BOLD
    ) {
      Typeface.createFromAsset(
        context.assets,
        "fonts/SignalSymbols-Bold.otf"
      )
    }
  }

  private fun getRegularWeightedFont(context: Context): Typeface {
    return cache.getOrPut(
      Weight.REGULAR
    ) {
      Typeface.createFromAsset(
        context.assets,
        "fonts/SignalSymbols-Regular.otf"
      )
    }
  }

  /**
   * Custom TypefaceSpan to support TypefaceSpan in API<28
   *
   * Source: https://www.youtube.com/watch?v=x-FcOX6ErdI&t=486s
   */
  private class CustomTypefaceSpan(val font: Typeface?) : MetricAffectingSpan() {
    override fun updateMeasureState(textPaint: TextPaint) = update(textPaint)
    override fun updateDrawState(textPaint: TextPaint?) = update(textPaint)

    private fun update(tp: TextPaint?) {
      tp.apply {
        val old = this!!.typeface
        val oldStyle = old?.style ?: 0
        val font = Typeface.create(font, oldStyle)
        typeface = font
      }
    }
  }
}
