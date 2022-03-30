package org.thoughtcrime.securesms.fonts

import android.content.Context
import android.graphics.Typeface
import org.signal.imageeditor.core.Renderer
import org.signal.imageeditor.core.RendererContext
import org.thoughtcrime.securesms.util.FutureTaskListener
import org.thoughtcrime.securesms.util.LocaleUtil
import java.util.Locale
import java.util.concurrent.ExecutionException

/**
 * RenderContext TypefaceProvider that provides typefaces using TextFont.
 */
class FontTypefaceProvider : RendererContext.TypefaceProvider {

  private var cachedTypeface: Typeface? = null
  private var cachedLocale: Locale? = null

  override fun getSelectedTypeface(context: Context, renderer: Renderer, invalidate: RendererContext.Invalidate): Typeface {
    val typeface = cachedTypeface
    if (typeface != null && cachedLocale == LocaleUtil.getFirstLocale()) {
      return typeface
    }

    return when (val fontResult = Fonts.resolveFont(context, TextFont.BOLD)) {
      is Fonts.FontResult.Immediate -> {
        cachedTypeface = fontResult.typeface
        cachedLocale = LocaleUtil.getFirstLocale()
        fontResult.typeface
      }
      is Fonts.FontResult.Async -> {
        fontResult.future.addListener(object : FutureTaskListener<Typeface> {
          override fun onSuccess(result: Typeface?) {
            invalidate.onInvalidate(renderer)
          }

          override fun onFailure(exception: ExecutionException?) = Unit
        })

        fontResult.placeholder
      }
    }
  }
}
