package org.thoughtcrime.securesms.glide

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import org.session.libsession.avatars.PlaceholderAvatarPhoto
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.util.AvatarPlaceholderGenerator

class PlaceholderAvatarFetcher(private val context: Context,
                               private val photo: PlaceholderAvatarPhoto): DataFetcher<BitmapDrawable> {

    override fun loadData(priority: Priority,callback: DataFetcher.DataCallback<in BitmapDrawable>) {
        try {
            val avatar = AvatarPlaceholderGenerator.generate(context, 128, photo.hashString, photo.displayName)
            callback.onDataReady(avatar)
        } catch (e: Exception) {
            Log.e("Loki", "Error in fetching avatar")
            callback.onLoadFailed(e)
        }
    }

    override fun cleanup() {}

    override fun cancel() {}

    override fun getDataClass(): Class<BitmapDrawable> {
        return BitmapDrawable::class.java
    }

    override fun getDataSource(): DataSource = DataSource.LOCAL
}