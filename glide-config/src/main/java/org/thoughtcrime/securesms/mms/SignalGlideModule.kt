package org.thoughtcrime.securesms.mms

import android.content.Context
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator
import com.bumptech.glide.module.AppGlideModule

/**
 * A [GlideModule] to configure Glide for the app. This class is discovered by Glide's annotation
 * processor, and delegates its logic to a [RegisterGlideComponents]. It exists outside of the main
 * Gradle module to reduce the scope of classes that KAPT needs to look at.
 */
@GlideModule
class SignalGlideModule : AppGlideModule() {

  companion object {
    private const val MEMORY_CACHE_SAFETY_FACTOR = 0.1f

    @JvmStatic
    lateinit var registerGlideComponents: RegisterGlideComponents
  }

  override fun applyOptions(context: Context, builder: GlideBuilder) {
    builder.setLogLevel(Log.ERROR)

    val calculator = MemorySizeCalculator.Builder(context).build()
    val defaultMemoryCacheSize = calculator.memoryCacheSize.toLong()
    val defaultBitmapPoolSize = calculator.bitmapPoolSize.toLong()

    val customMemoryCacheSize = (defaultMemoryCacheSize * MEMORY_CACHE_SAFETY_FACTOR).toLong()
    val customBitmapPoolSize = (defaultBitmapPoolSize * MEMORY_CACHE_SAFETY_FACTOR).toLong()

    builder.setMemoryCache(LruResourceCache(customMemoryCacheSize))
    builder.setBitmapPool(LruBitmapPool(customBitmapPoolSize))
  }

  override fun isManifestParsingEnabled(): Boolean {
    return false
  }

  override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
    registerGlideComponents.registerComponents(context, glide, registry)
  }
}

interface RegisterGlideComponents {

  fun registerComponents(context: Context, glide: Glide, registry: Registry)
}
