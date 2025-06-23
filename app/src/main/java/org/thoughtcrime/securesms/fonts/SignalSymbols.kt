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
import androidx.annotation.ColorRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.core.content.ContextCompat
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.ViewUtil

/**
 * Helper object for working with the SignalSymbols font
 */
object SignalSymbols {

  enum class Glyph(val unicode: Char) {
    LOGO('\uE000'),
    ALBUM('\uE001'),
    APPEARANCE('\uE031'),
    ARROW_LEFT('\u2190'),
    ARROW_RIGHT('\u2192'),
    ARROW_UP('\u2191'),
    ARROW_DOWN('\u2193'),
    ARROW_UP_LEFT('\u2196'),
    ARROW_UP_RIGHT('\u2197'),
    ARROW_DOWN_LEFT('\u2199'),
    ARROW_DOWN_RIGHT('\u2198'),
    ARROW_CIRCLE_LEFT('\uE00B'),
    ARROW_CIRCLE_RIGHT('\uE00C'),
    ARROW_CIRCLE_UP('\uE00D'),
    ARROW_CIRCLE_DOWN('\uE00E'),
    ARROW_CIRCLE_UP_LEFT('\uE00F'),
    ARROW_CIRCLE_UP_RIGHT('\uE010'),
    ARROW_CIRCLE_DOWN_LEFT('\uE011'),
    ARROW_CIRCLE_DOWN_RIGHT('\uE012'),
    ARROW_SQUARE_LEFT('\uE013'),
    ARROW_SQUARE_RIGHT('\uE014'),
    ARROW_SQUARE_UP('\uE015'),
    ARROW_SQUARE_DOWN('\uE016'),
    ARROW_SQUARE_UP_LEFT('\uE017'),
    ARROW_SQUARE_UP_RIGHT('\uE018'),
    ARROW_SQUARE_DOWN_LEFT('\uE019'),
    ARROW_SQUARE_DOWN_RIGHT('\uE01A'),
    ARROW_DASH_DOWN('\uE021'),
    ARROW_CIRCLE_LEFT_FILL('\uE003'),
    ARROW_CIRCLE_RIGHT_FILL('\uE004'),
    ARROW_CIRCLE_UP_FILL('\uE005'),
    ARROW_CIRCLE_DOWN_FILL('\uE006'),
    ARROW_CIRCLE_UP_LEFT_FILL('\uE007'),
    ARROW_CIRCLE_UP_RIGHT_FILL('\uE008'),
    ARROW_CIRCLE_DOWN_LEFT_FILL('\uE009'),
    ARROW_CIRCLE_DOWN_RIGHT_FILL('\uE00A'),
    ARROW_SQUARE_LEFT_FILL('\uE08A'),
    ARROW_SQUARE_RIGHT_FILL('\uE08B'),
    ARROW_SQUARE_UP_FILL('\uE08C'),
    ARROW_SQUARE_DOWN_FILL('\uE08D'),
    ARROW_SQUARE_UP_LEFT_FILL('\uE08E'),
    ARROW_SQUARE_UP_RIGHT_FILL('\uE08F'),
    ARROW_SQUARE_DOWN_LEFT_FILL('\uE090'),
    ARROW_SQUARE_DOWN_RIGHT_FILL('\uE091'),
    AT('\uE01B'),
    ATTACH('\uE058'),
    AUDIO('\uE01C'),
    AUDIO_RECTANGLE('\uE01D'),
    BADGE('\uE099'),
    BADGE_FILL('\uE09A'),
    BELL('\uE01E'),
    BELL_SLASH('\uE01F'),
    BELL_RING('\uE020'),
    BLOCK('\uE002'),
    CHECK('\u2713'),
    CHECK_CIRCLE('\uE022'),
    CHECK_SQUARE('\uE023'),
    CHEVRON_LEFT('\uE024'),
    CHEVRON_RIGHT('\uE025'),
    CHEVRON_UP('\uE026'),
    CHEVRON_DOWN('\uE027'),
    CHEVRON_CIRCLE_LEFT('\uE028'),
    CHEVRON_CIRCLE_RIGHT('\uE029'),
    CHEVRON_CIRCLE_UP('\uE02A'),
    CHEVRON_CIRCLE_DOWN('\uE02B'),
    CHEVRON_SQUARE_LEFT('\uE02C'),
    CHEVRON_SQUARE_RIGHT('\uE02D'),
    CHEVRON_SQUARE_UP('\uE02E'),
    CHEVRON_SQUARE_DOWN('\uE02F'),
    DROPDOWN_DOWN('\uE07F'),
    DROPDOWN_UP('\uE080'),
    DROPDOWN_TRIANGLE_DOWN('\uE082'),
    EDIT('\uE030'),
    EMOJI('\u263A'),
    ERROR('\uE032'),
    ERROR_TRIANGLE('\uE092'),
    ERROR_FILL('\uE093'),
    ERROR_TRIANGLE_FILL('\uE094'),
    FILE('\uE034'),
    FORWARD('\uE035'),
    FORWARD_FILL('\uE036'),
    GIF('\uE037'),
    GIF_RECTANGLE('\uE097'),
    GROUP('\uE038'),
    HEART('\uE039'),
    INCOMING('\uE03A'),
    INFO('\uE03B'),
    LEAVE('\uE03C'),
    LEAVE_RTL('\uE03D'),
    LINK('\uE03E'),
    LINK_ANDROID('\uE03F'),
    LINK_BROKEN('\uE057'),
    LINK_SLASH('\uE040'),
    LOCK('\uE041'),
    LOCK_OPEN('\uE07D'),
    MEGAPHONE('\uE042'),
    MERGE('\uE043'),
    MESSAGE_STATUS_SENDING('\uE044'),
    MESSAGE_STATUS_SENT('\uE045'),
    MESSAGE_STATUS_READ('\uE046'),
    MESSAGE_STATUS_DELIVERED('\uE047'),
    MESSAGE_TIMER_00('\uE048'),
    MESSAGE_TIMER_05('\uE049'),
    MESSAGE_TIMER_10('\uE04A'),
    MESSAGE_TIMER_15('\uE04B'),
    MESSAGE_TIMER_20('\uE04C'),
    MESSAGE_TIMER_25('\uE04D'),
    MESSAGE_TIMER_30('\uE04E'),
    MESSAGE_TIMER_35('\uE04F'),
    MESSAGE_TIMER_40('\uE050'),
    MESSAGE_TIMER_45('\uE051'),
    MESSAGE_TIMER_50('\uE052'),
    MESSAGE_TIMER_55('\uE053'),
    MESSAGE_TIMER_60('\uE054'),
    MIC('\uE055'),
    MIC_SLASH('\uE056'),
    MINUS('\u2212'),
    MINUS_CIRCLE('\u2296'),
    MINUS_SQUARE('\uE059'),
    MISSED_INCOMING('\uE05A'),
    MISSED_OUTGOING('\uE05B'),
    NOTE('\uE095'),
    NOTE_RTL('\uE096'),
    OFFICIAL_BADGE('\uE086'),
    OFFICIAL_BADGE_FILL('\uE087'),
    OUTGOING('\uE05C'),
    PERSON('\uE05D'),
    PERSON_CIRCLE('\uE05E'),
    PERSON_CHECK('\uE05F'),
    PERSON_X('\uE060'),
    PERSON_PLUS('\uE061'),
    PERSON_MINUS('\uE062'),
    PHONE('\uE063'),
    PHONE_FILL('\uE064'),
    PHOTO('\uE065'),
    PLAY('\uE067'),
    PLUS('\u002B'),
    PLUS_CIRCLE('\u2295'),
    PLUS_SQUARE('\uE06C'),
    RAISE_HAND('\uE07E'),
    RAISE_HAND_FILL('\uE084'),
    REPLY('\uE06D'),
    REPLY_FILL('\uE06E'),
    SAFETY_NUMBER('\uE06F'),
    SPAM('\uE033'),
    STICKER('\uE070'),
    THREAD('\uE071'),
    THREAD_FILL('\uE072'),
    TIMER('\uE073'),
    TIMER_SLASH('\uE074'),
    VIDEO_CAMERA('\uE075'),
    VIDEO_CAMERA_SLASH('\uE076'),
    VIDEO_CAMERA_FILL('\uE077'),
    VIDEO('\uE088'),
    VIEW_ONCE('\uE078'),
    VIEW_ONCE_DASH('\uE079'),
    VIEW_ONCE_VIEWED('\uE07A'),
    X('\u00D7'),
    X_CIRCLE('\u2297'),
    X_SQUARE('\u2327'),

    REFRESH('\uE000'),
    ACTIVATE_PAYMENTS('\uE000'),
    CALENDAR('\uE0A2')
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
    glyph: Glyph,
    @ColorRes colorRes: Int = -1
  ): CharSequence {
    val typeface = getTypeface(context, weight)
    val span = CustomTypefaceSpan(typeface)

    val text = SpannableStringBuilder(glyph.unicode.toString())
    text.setSpan(span, 0, text.length, 0)

    return if (colorRes != -1) {
      SpanUtil.color(ContextCompat.getColor(context, colorRes), text)
    } else {
      text
    }
  }

  @JvmStatic
  fun getSignalSymbolText(
    context: Context,
    text: String,
    glyphStart: Glyph? = null,
    glyphEnd: Glyph? = null,
    glyphStartWeight: Weight = Weight.REGULAR,
    glyphEndWeight: Weight = Weight.REGULAR,
    glyphStartSizeSp: Int = 16,
    glyphEndSizeSp: Int = 16
  ): AnnotatedString {
    val isLtr = ViewUtil.isLtr(context)
    val leftGlyph = if (isLtr) glyphStart else glyphEnd
    val leftGlyphWeight = if (isLtr) glyphStartWeight else glyphEndWeight
    val leftGlyphSizeSp = if (isLtr) glyphStartSizeSp else glyphEndSizeSp
    val rightGlyph = if (isLtr) glyphEnd else glyphStart
    val rightGlyphWeight = if (isLtr) glyphEndWeight else glyphStartWeight
    val rightGlyphSizeSp = if (isLtr) glyphEndSizeSp else glyphStartSizeSp

    return buildAnnotatedString {
      if (leftGlyph != null) {
        val symbol = SpanUtil.ofSize(
          getSpannedString(context, leftGlyphWeight, leftGlyph),
          leftGlyphSizeSp
        )
        append(symbol)
        append(" ")
      }
      append(text)
      if (rightGlyph != null) {
        val symbol = SpanUtil.ofSize(
          getSpannedString(context, rightGlyphWeight, rightGlyph),
          rightGlyphSizeSp
        )
        append(" ")
        append(symbol)
      }
    }
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

  @Composable
  fun signalSymbolText(
    text: String,
    glyphStart: Glyph? = null,
    glyphEnd: Glyph? = null,
    glyphStartWeight: Weight = Weight.REGULAR,
    glyphEndWeight: Weight = Weight.REGULAR,
    glyphStartSizeSp: Int = 16,
    glyphEndSizeSp: Int = 16
  ): AnnotatedString {
    return getSignalSymbolText(
      context = LocalContext.current,
      text = text,
      glyphStart = glyphStart,
      glyphEnd = glyphEnd,
      glyphStartWeight = glyphStartWeight,
      glyphEndWeight = glyphEndWeight,
      glyphStartSizeSp = glyphStartSizeSp,
      glyphEndSizeSp = glyphEndSizeSp
    )
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
        val oldStyle = old?.style ?: Typeface.NORMAL
        val font = Typeface.create(font, oldStyle)
        typeface = font
      }
    }
  }
}
