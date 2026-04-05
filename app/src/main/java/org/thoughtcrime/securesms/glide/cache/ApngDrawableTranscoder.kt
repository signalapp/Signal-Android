package org.thoughtcrime.securesms.glide.cache

import android.graphics.drawable.Drawable
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.resource.drawable.DrawableResource
import com.bumptech.glide.load.resource.transcode.ResourceTranscoder
import org.signal.apng.ApngDecoder
import org.signal.apng.ApngDrawable

class ApngDrawableTranscoder : ResourceTranscoder<ApngDecoder, Drawable> {
  override fun transcode(toTranscode: Resource<ApngDecoder>, options: Options): Resource<Drawable> {
    val decoder = toTranscode.get()
    val drawable = ApngDrawable(decoder).apply {
      loopForever = true
    }

    return object : DrawableResource<Drawable>(drawable) {
      override fun getResourceClass(): Class<Drawable> = Drawable::class.java

      override fun getSize(): Int = 0

      override fun recycle() {
        (get() as ApngDrawable).recycle()
      }
    }
  }
}
