package org.thoughtcrime.securesms.fonts

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import org.signal.imageeditor.core.Renderer
import org.signal.imageeditor.core.RendererContext
import java.util.Locale

/**
 * RenderContext TypefaceProvider that provides typefaces using TextFont.
 */
class FontTypefaceProvider : RendererContext.TypefaceProvider {

  private var cachedTypeface: Typeface? = null
  private var cachedLocale: Locale? = null

  override fun getSelectedTypeface(context: Context, renderer: Renderer, invalidate: RendererContext.Invalidate): Typeface {
    return getTypeface()
    // TODO [cody] Need to rework Fonts.kt to not hit network on main, reverting to old typeface for now
//    val typeface = cachedTypeface
//    if (typeface != null && cachedLocale == LocaleUtil.getFirstLocale()) {
//      return typeface
//    }
//
//    return when (val fontResult = Fonts.resolveFont(context, TextFont.BOLD)) {
//      is Fonts.FontResult.Immediate -> {
//        cachedTypeface = fontResult.typeface
//        cachedLocale = LocaleUtil.getFirstLocale()
//        fontResult.typeface
//      }
//      is Fonts.FontResult.Async -> {
//        fontResult.future.addListener(object : FutureTaskListener<Typeface> {
//          override fun onSuccess(result: Typeface?) {
//            invalidate.onInvalidate(renderer)
//          }
//
//          override fun onFailure(exception: ExecutionException?) = Unit
//        })
//
//        fontResult.placeholder
//      }
//    }
  }

  private fun getTypeface(): Typeface {
    return if (Build.VERSION.SDK_INT < 26) {
      Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    } else {
      Typeface.Builder("")
        .setFallback("sans-serif")
        .setWeight(900)
        .build()
    }
  }
}
