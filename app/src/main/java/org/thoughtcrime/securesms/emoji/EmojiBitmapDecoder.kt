package org.thoughtcrime.securesms.emoji

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.bumptech.glide.load.Option
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapResource
import java.io.InputStream

/**
 * Allows fine grain control over how we decode Emoji pages via a scale factor.
 *
 * This can be set via RequestOptions on a Glide request:
 *
 * ```
 * .apply(RequestOptions().set(EmojiBitmapDecoder.OPTION, inSampleSize)
 * ```
 */
class EmojiBitmapDecoder(private val bitmapPool: BitmapPool) : ResourceDecoder<InputStream, Bitmap> {

  override fun handles(source: InputStream, options: Options): Boolean {
    return options.get(OPTION)?.let { it > 1 } ?: false
  }

  override fun decode(source: InputStream, width: Int, height: Int, options: Options): Resource<Bitmap>? {
    val bitmapOptions = BitmapFactory.Options()

    bitmapOptions.inSampleSize = requireNotNull(options.get(OPTION))

    return BitmapResource.obtain(BitmapFactory.decodeStream(source, null, bitmapOptions), bitmapPool)
  }

  companion object {
    @JvmField
    val OPTION: Option<Int> = Option.memory("emoji_sample_size", 1)
  }
}
