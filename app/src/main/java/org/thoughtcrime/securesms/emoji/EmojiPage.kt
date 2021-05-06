package org.thoughtcrime.securesms.emoji

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Key
import com.bumptech.glide.load.Option
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import org.thoughtcrime.securesms.mms.PartAuthority
import java.io.InputStream
import java.security.MessageDigest

typealias EmojiPageFactory = (Uri) -> EmojiPage

sealed class EmojiPage(private val uri: Uri) : Key {
  override fun updateDiskCacheKey(messageDigest: MessageDigest) {
    messageDigest.update("EmojiPage".encodeToByteArray())
    messageDigest.update(uri.toString().encodeToByteArray())
  }

  data class Asset(private val uri: Uri) : EmojiPage(uri)
  data class Disk(private val uri: Uri) : EmojiPage(uri)

  class Loader(private val context: Context) : ModelLoader<EmojiPage, Bitmap> {
    override fun buildLoadData(
      model: EmojiPage,
      width: Int,
      height: Int,
      options: Options
    ): ModelLoader.LoadData<Bitmap> {
      return ModelLoader.LoadData(model, Fetcher(context, model, options.get(IN_SAMPLE_SIZE) ?: 1))
    }

    override fun handles(model: EmojiPage): Boolean = true

    class Factory(private val context: Context) : ModelLoaderFactory<EmojiPage, Bitmap> {
      override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<EmojiPage, Bitmap> {
        return Loader(context)
      }

      override fun teardown() = Unit
    }
  }

  class Fetcher(private val context: Context, private val model: EmojiPage, private val inSampleSize: Int) : DataFetcher<Bitmap> {
    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
      try {
        val inputStream: InputStream = when (model) {
          is Asset -> context.assets.open(model.uri.toString().replace("file:///android_asset/", ""))
          is Disk -> EmojiFiles.openForReading(context, PartAuthority.getEmojiFilename(model.uri))
        }

        val bitmapOptions = BitmapFactory.Options()
        bitmapOptions.inSampleSize = inSampleSize

        callback.onDataReady(BitmapFactory.decodeStream(inputStream, null, bitmapOptions))
      } catch (e: Exception) {
        callback.onLoadFailed(e)
      }
    }

    override fun cleanup() = Unit
    override fun cancel() = Unit

    override fun getDataClass(): Class<Bitmap> {
      return Bitmap::class.java
    }

    override fun getDataSource(): DataSource {
      return DataSource.LOCAL
    }
  }

  companion object {
    @JvmField
    val IN_SAMPLE_SIZE: Option<Int> = Option.memory("emoji_page_in_sample_size", 1)
  }
}
