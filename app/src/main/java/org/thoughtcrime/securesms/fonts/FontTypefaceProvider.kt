package org.thoughtcrime.securesms.fonts

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import org.signal.imageeditor.core.Renderer
import org.signal.imageeditor.core.RendererContext

/**
 * RenderContext TypefaceProvider that provides typefaces using TextFont.
 */
object FontTypefaceProvider : RendererContext.TypefaceProvider {
  override fun getSelectedTypeface(context: Context, renderer: Renderer, invalidate: RendererContext.Invalidate): Typeface {
    return getTypeface()
    // TODO [cody] Need to rework Fonts.kt to not hit network on main, reverting to old typeface for now
//    return when (val fontResult = Fonts.resolveFont(context, Locale.getDefault(), TextFont.BOLD)) {
//      is Fonts.FontResult.Immediate -> fontResult.typeface
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
