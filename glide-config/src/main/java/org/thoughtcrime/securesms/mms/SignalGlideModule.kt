package org.thoughtcrime.securesms.mms

import android.content.Context
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule

/**
 * A [GlideModule] to configure Glide for the app. This class is discovered by Glide's annotation
 * processor, and delegates its logic to a [RegisterGlideComponents]. It exists outside of the main
 * Gradle module to reduce the scope of classes that KAPT needs to look at.
 */
@GlideModule
class SignalGlideModule : AppGlideModule() {

  override fun isManifestParsingEnabled(): Boolean {
    return false
  }

  override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
    registerGlideComponents.registerComponents(context, glide, registry)
  }

  override fun applyOptions(context: Context, builder: GlideBuilder) {
    builder.setLogLevel(Log.ERROR)
  }

  companion object {
    @JvmStatic
    lateinit var registerGlideComponents: RegisterGlideComponents
  }
}

interface RegisterGlideComponents {

  fun registerComponents(context: Context, glide: Glide, registry: Registry)
}
