package org.thoughtcrime.securesms.fonts

import android.content.Context
import android.graphics.Typeface
import org.signal.imageeditor.core.Renderer
import org.signal.imageeditor.core.RendererContext
import org.thoughtcrime.securesms.util.FutureTaskListener
import java.util.Locale
import java.util.concurrent.ExecutionException

/**
 * RenderContext TypefaceProvider that provides typefaces using TextFont.
 */
object FontTypefaceProvider : RendererContext.TypefaceProvider {
  override fun getSelectedTypeface(context: Context, renderer: Renderer, invalidate: RendererContext.Invalidate): Typeface {
    return when (val fontResult = Fonts.resolveFont(context, Locale.getDefault(), TextFont.BOLD)) {
      is Fonts.FontResult.Immediate -> fontResult.typeface
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
