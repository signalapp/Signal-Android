package org.thoughtcrime.securesms.glide

import android.graphics.drawable.BitmapDrawable
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoader.LoadData
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import org.session.libsession.avatars.PlaceholderAvatarPhoto

class PlaceholderAvatarLoader(): ModelLoader<PlaceholderAvatarPhoto, BitmapDrawable> {

    override fun buildLoadData(
        model: PlaceholderAvatarPhoto,
        width: Int,
        height: Int,
        options: Options
    ): LoadData<BitmapDrawable> {
        return LoadData(model, PlaceholderAvatarFetcher(model.context, model))
    }

    override fun handles(model: PlaceholderAvatarPhoto): Boolean = true

    class Factory() : ModelLoaderFactory<PlaceholderAvatarPhoto, BitmapDrawable> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<PlaceholderAvatarPhoto, BitmapDrawable> {
            return PlaceholderAvatarLoader()
        }
        override fun teardown() {}
    }
}